package com.creativeworkbench.writer.core.ai

import com.creativeworkbench.writer.core.data.MentionIndex
import com.creativeworkbench.writer.core.data.NoteEntity
import com.creativeworkbench.writer.core.data.NoteKind
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 设定（笔记）进上下文的方式。
 *
 * 为什么要有「自动」：以前的实现是「全部设定默认勾上，逐个取消」——
 * 设定一多，每次写正文都得先做一遍减法，而且减掉的还是几章之前的旧设定，
 * 跟这一章根本没有关系。真正该由谁决定？由「这一章要写什么」决定。
 * 所以默认改成 Auto：本地先按命中/置顶/类型排出一批候选，再让模型从候选里挑，
 * 挑不中/调用失败就退回本地打分。用户仍可切回「手动」逐条勾选（选择会被记住）。
 *
 * 参考：
 * - SillyTavern 世界书：关键词命中才注入 + 预算裁剪 + 递归扫描（见 参考/拆解_SillyTavern世界书激活_20261126.md）；
 * - Novelcrafter Codex：AI 只读取「本章场景里被提到/被关联」的条目，而不是整个 Codex；
 * - NovelAI Lorebook：按关键词激活 + 条目优先级。
 */
enum class NoteContextMode(val label: String, val hint: String) {
    /** AI 按这一章的剧情自己挑（默认）。 */
    Auto("自动", "AI 按这一章要写的内容挑，不用手动取消"),
    /** 全部设定都带（旧行为）。 */
    All("全部", "所有设定都带上，怕漏就选这个"),
    /** 用户自己勾。 */
    Manual("手动", "自己勾选，下次打开还是这套"),
    /** 一条都不带。 */
    Off("关", "一条设定都不带");

    companion object {
        fun of(name: String?): NoteContextMode =
            entries.firstOrNull { it.name == name } ?: Auto
    }
}

/** 一条候选设定：为什么进候选、大概多少 token。 */
data class NoteCandidate(
    val note: NoteEntity,
    /** 本地打分（命中次数为主，置顶/类型/别名加成）。 */
    val score: Int,
    /** 在参考文本里被提到几次。 */
    val mentions: Int,
    val tokens: Int,
    /** 给界面看的一句话理由。 */
    val reason: String
)

/** 自动挑选的结果：选了哪些、候选池有多大、是不是模型挑的。 */
data class AutoNotePick(
    val chosen: List<NoteCandidate>,
    val pool: List<NoteCandidate>,
    /** true = 模型挑的；false = 本地打分兜底。 */
    val byAi: Boolean,
    val message: String
) {
    val chosenIds: Set<String> = chosen.map { it.note.id }.toSet()
    val usedTokens: Int = chosen.sumOf { it.tokens }

    companion object {
        val EMPTY = AutoNotePick(emptyList(), emptyList(), false, "没有可用的设定")
    }
}

/**
 * 本地候选打分：不联网、不向量，只做「提到过谁」+ 少量先验。
 * 这里的规则和界面上的「本章提到」完全一致（都走 MentionIndex），
 * 否则用户会看到「显示提到了 3 条、AI 只带了 1 条」这种没法解释的差异。
 */
object NoteRetriever {

    /** 候选池上限：给模型的候选超过这个数，选择质量反而下降，token 也白花。 */
    const val POOL_LIMIT = 36

    fun candidates(
        notes: List<NoteEntity>,
        scanText: String,
        limit: Int = POOL_LIMIT
    ): List<NoteCandidate> {
        if (notes.isEmpty()) return emptyList()
        return notes.map { note ->
            val mentions = MentionIndex.countIn(note, scanText)
            val text = SettingInjector.render(note)
            val kindBonus = when (note.kind) {
                NoteKind.CHARACTER -> 4
                NoteKind.WORLD -> 2
                else -> 0
            }
            val pinnedBonus = if (note.pinned) 8 else 0
            val aliasBonus = if (MentionIndex.names(note).size > 1) 1 else 0
            val score = minOf(mentions, 3) * 10 + kindBonus + pinnedBonus + aliasBonus
            NoteCandidate(
                note = note,
                score = score,
                mentions = mentions,
                tokens = TokenEstimator.estimate(text),
                reason = reasonOf(note, mentions)
            )
        }
            .sortedWith(
                compareByDescending<NoteCandidate> { it.score }
                    .thenByDescending { it.note.pinned }
                    .thenBy { it.note.title }
            )
            .take(limit)
    }

    private fun reasonOf(note: NoteEntity, mentions: Int): String = when {
        mentions > 0 -> "本章提到 " + mentions + " 次"
        note.pinned -> "置顶常驻"
        else -> "候选"
    }

    /** 兜底：按打分顺序塞进预算，塞不下就停。 */
    fun pickByScore(candidates: List<NoteCandidate>, cap: Int): List<NoteCandidate> {
        val kept = mutableListOf<NoteCandidate>()
        var used = 0
        candidates.forEach { candidate ->
            if (used + candidate.tokens <= cap || kept.isEmpty()) {
                kept.add(candidate)
                used += candidate.tokens
            }
        }
        return kept
    }
}

/**
 * 「AI 自选设定」：一次便宜的非流式调用，从候选里挑这次要用的条目。
 *
 * 成本控制：
 * - 只发候选摘要（标题 + 80 字），不发全文；正文只发末尾 1200 字；
 * - 输出上限 256 tokens、温度 0、关思考；
 * - 同一章、同一动作、同一批候选的结果缓存，反复点 chip 不会重复花钱。
 */
class NoteSelector(private val client: AiClient) {

    private val cache = object : LinkedHashMap<String, AutoNotePick>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AutoNotePick>?) = size > 12
    }

    suspend fun select(
        actionId: String,
        actionName: String,
        notes: List<NoteEntity>,
        scanText: String,
        outlineText: String,
        cap: Int,
        config: AiConfig,
        apiKey: String
    ): AutoNotePick {
        val candidates = NoteRetriever.candidates(notes, scanText + "\n" + outlineText)
        if (candidates.isEmpty()) return AutoNotePick.EMPTY
        val fallback = NoteRetriever.pickByScore(candidates, cap)
        // 候选很少时没必要再花一次调用：全带上就是最优解。
        if (candidates.size <= 5 || apiKey.isBlank()) {
            return AutoNotePick(
                chosen = fallback,
                pool = candidates,
                byAi = false,
                message = "本地挑选 " + fallback.size + "/" + candidates.size + " 条"
            )
        }
        val key = cacheKey(actionId, scanText, candidates, cap, outlineText.hashCode().toString())
        cache[key]?.let { return it }

        val listing = candidates.joinToString("\n") { candidate ->
            candidate.note.id + " | " + NoteKind.label(candidate.note.kind) + " | " +
                candidate.note.title + " | " + candidate.note.content.replace('\n', ' ').take(80)
        }
        val system = "你是写作资料管理员。给你一次写作任务的片段和候选设定，" +
            "挑出这次真正需要的条目（与当下情节有关的、会被写到的），" +
            "不要把所有候选都带上；与本次无关的旧设定不要选。" +
            "只输出 JSON：{\"ids\":[\"条目id\"]}，不要解释。"
        val user = buildString {
            appendLine("【动作】" + actionName)
            if (outlineText.isNotBlank()) {
                appendLine("【这章的大纲要点】")
                appendLine(outlineText.take(800))
            }
            appendLine("【正文末尾】")
            appendLine(scanText.takeLast(1200))
            appendLine("【候选设定（id | 类型 | 标题 | 摘要）】")
            appendLine(listing)
            appendLine("【要求】最多挑 " + MAX_PICKS + " 条，宁少勿多。只输出 JSON。")
        }
        val result = client.chat(
            config = config,
            apiKey = apiKey,
            messages = listOf(ChatMessage("system", system), ChatMessage("user", user)),
            tuning = ActionTuning(maxTokens = 256, thinking = false, temperature = 0.0),
            jsonMode = true
        )
        val pick = when (result) {
            is AiCallResult.Error -> AutoNotePick(
                chosen = fallback,
                pool = candidates,
                byAi = false,
                message = "本地挑选 " + fallback.size + "/" + candidates.size + " 条（AI 挑选失败）"
            )
            is AiCallResult.Ok -> parsePick(result.content, candidates, cap, fallback)
        }
        cache[key] = pick
        return pick
    }

    /** 不调模型，只用本地打分挑一遍（没配 Key、调用失败、用户关掉 AI 挑选时用）。 */
    fun localOnly(
        notes: List<NoteEntity>,
        scanText: String,
        outlineText: String,
        cap: Int
    ): AutoNotePick {
        val candidates = NoteRetriever.candidates(notes, scanText + "\n" + outlineText)
        val chosen = NoteRetriever.pickByScore(candidates, cap).take(MAX_PICKS)
        return AutoNotePick(
            chosen = chosen,
            pool = candidates,
            byAi = false,
            message = "本地挑选 " + chosen.size + "/" + candidates.size + " 条"
        )
    }

    private fun parsePick(
        raw: String,
        candidates: List<NoteCandidate>,
        cap: Int,
        fallback: List<NoteCandidate>
    ): AutoNotePick {
        val json = JsonSalvage.extractObject(raw)
        val ids = json?.let { runCatching { JSONObject(it).optJSONArray("ids") }.getOrNull() }
        if (ids == null) {
            return AutoNotePick(
                chosen = fallback,
                pool = candidates,
                byAi = false,
                message = "本地挑选 " + fallback.size + "/" + candidates.size + " 条（模型输出没解析成功）"
            )
        }
        val wanted = (0 until ids.length()).mapNotNull { ids.optString(it).takeIf { id -> id.isNotBlank() } }
        val byId = candidates.associateBy { it.note.id }
        // 按候选原有顺序输出，保证同一份数据两次装配逐字一致（前缀缓存靠它）。
        val chosen = candidates.filter { it.note.id in wanted }.take(MAX_PICKS)
        if (chosen.isEmpty()) {
            return AutoNotePick(
                chosen = fallback,
                pool = candidates,
                byAi = false,
                message = "本地挑选 " + fallback.size + "/" + candidates.size + " 条（模型一条都没选）"
            )
        }
        // 预算兜底：模型挑多了就按它的顺序裁到装得下。
        val fitted = mutableListOf<NoteCandidate>()
        var used = 0
        chosen.forEach { candidate ->
            if (used + candidate.tokens <= cap || fitted.isEmpty()) {
                fitted.add(candidate)
                used += candidate.tokens
            }
        }
        val dropped = chosen.size - fitted.size
        return AutoNotePick(
            chosen = fitted,
            pool = candidates,
            byAi = true,
            message = "AI 挑了 " + fitted.size + "/" + candidates.size + " 条" +
                (if (dropped > 0) "（超预算省略 " + dropped + " 条）" else "")
        )
    }

    private fun cacheKey(
        actionId: String,
        scanText: String,
        candidates: List<NoteCandidate>,
        cap: Int,
        outlineHash: String
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(actionId.toByteArray())
        digest.update(cap.toString().toByteArray())
        digest.update(scanText.takeLast(2000).toByteArray())
        // 大纲与候选内容也进 key：改了设定/大纲之后不能复用旧挑选结果
        digest.update(outlineHash.toByteArray())
        candidates.forEach { candidate ->
            digest.update(candidate.note.id.toByteArray())
            digest.update(candidate.note.content.hashCode().toString().toByteArray())
            digest.update((if (candidate.note.pinned) "1" else "0").toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(32)
    }

    private companion object {
        /** 一次最多挑几条：设定多了反而让正文失焦，真需要的会命中。 */
        const val MAX_PICKS = 10
    }
}
