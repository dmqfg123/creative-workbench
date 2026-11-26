package com.creativeworkbench.writer.core.ai

import com.creativeworkbench.writer.core.data.NoteKind
import org.json.JSONObject
import java.util.UUID

/** 从一段文本里识别出来的一条可导入项。 */
data class StructuredItem(
    val id: String = UUID.randomUUID().toString(),
    /** NOTE / OUTLINE / FORESHADOW */
    val bucket: String,
    /** 笔记类型（bucket=NOTE 时有效）。 */
    val kind: String,
    val title: String,
    val content: String,
    /** 大纲绑定第几章（1-based，可空=全书级）。 */
    val chapterIndex: Int? = null,
    val selected: Boolean = true
) {
    val bucketLabel: String
        get() = when (bucket) {
            OUTLINE -> "大纲"
            FORESHADOW -> "伏笔"
            else -> NoteKind.label(kind)
        }

    companion object {
        const val NOTE = "note"
        const val OUTLINE = "outline"
        const val FORESHADOW = "foreshadow"
    }
}

data class ChapterSlice(val index: Int, val title: String, val body: String)

/**
 * 本地分章：不花钱、毫秒级，先把「第 N 章 / 序章 / 楔子」这类标题找出来。
 * 识别不到两章就整段当一个切片——分不出章也别让用户先手动排版。
 */
object ChapterSplitter {

    private val HEAD = Regex(
        "^\\s*(第\\s*[0-9０-９一二三四五六七八九十百千零〇两]+\\s*[章节回卷篇]|序章|楔子|引子|尾声|后记|番外)" +
            "\\s*[：:.、—\\-]?\\s*(.*)$"
    )

    const val MAX_CHAPTERS = 40

    fun split(text: String): List<ChapterSlice> {
        val lines = text.lines()
        val heads = mutableListOf<Pair<Int, String>>()
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && trimmed.length <= 40) {
                val match = HEAD.find(trimmed)
                if (match != null) {
                    val title = (match.groupValues[1] + " " + match.groupValues[2]).trim()
                    heads.add(index to title.ifBlank { trimmed })
                }
            }
        }
        if (heads.size < 2) return listOf(ChapterSlice(1, "全文", text.trim()))
        return heads.mapIndexed { i, (lineIndex, title) ->
            val end = heads.getOrNull(i + 1)?.first ?: lines.size
            val body = lines.subList(lineIndex + 1, end).joinToString("\n").trim()
            ChapterSlice(0, title, body)
        }
            .filter { it.body.isNotBlank() }
            .take(MAX_CHAPTERS)
            .mapIndexed { i, slice -> slice.copy(index = i + 1) }
    }
}

data class StructureOutcome(
    val items: List<StructuredItem>,
    val chapterCount: Int,
    val usage: AiUsage? = null,
    val error: String? = null
)

/**
 * 文本 → 结构化资料：每章一次调用，抽人物/设定/灵感/大纲要点/伏笔。
 *
 * 为什么按章切而不整本喂：一次调用处理一整本必然截断、也必然贵；
 * 按章切还能顺手把大纲节点绑到对应章节上（这是「导入后大纲立刻可用」的关键）。
 * 输出用严格 JSON + JsonSalvage 容错，解析不了的章节跳过并记一条说明，不中断整个导入。
 */
class TextStructurer(private val client: AiClient) {

    suspend fun extract(
        text: String,
        config: AiConfig,
        apiKey: String,
        onProgress: (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): StructureOutcome {
        val chapters = ChapterSplitter.split(text)
        if (chapters.isEmpty()) return StructureOutcome(emptyList(), 0, error = "没有可识别的文本")
        var usage: AiUsage? = null
        val items = mutableListOf<StructuredItem>()
        var firstError: String? = null
        chapters.forEachIndexed { index, slice ->
            onProgress(index, chapters.size, slice.title)
            val body = slice.body.take(MAX_CHARS_PER_CALL)
            val result = client.chat(
                config = config,
                apiKey = apiKey,
                messages = listOf(
                    ChatMessage("system", SYSTEM),
                    ChatMessage("user", buildPrompt(slice, body))
                ),
                tuning = ActionTuning(maxTokens = 2048, thinking = false, temperature = 0.2),
                jsonMode = true
            )
            when (result) {
                is AiCallResult.Error -> {
                    if (firstError == null) firstError = result.message
                }
                is AiCallResult.Ok -> {
                    usage = (usage ?: AiUsage()) + result.usage
                    items.addAll(parse(result.content, slice.index))
                }
            }
        }
        onProgress(chapters.size, chapters.size, "完成")
        return StructureOutcome(
            items = dedupe(items),
            chapterCount = chapters.size,
            usage = usage,
            error = if (items.isEmpty()) firstError ?: "没识别出可导入的内容" else null
        )
    }

    private fun buildPrompt(slice: ChapterSlice, body: String): String = buildString {
        appendLine("【章节】" + slice.title + "（第 " + slice.index + " 章）")
        appendLine("【正文】")
        appendLine(body)
        appendLine("【要求】从上面的正文里抽取结构化资料，只输出 JSON，不要解释：")
        appendLine(
            "{\"notes\":[{\"kind\":\"character|world|idea|fragment|dialogue\",\"title\":\"\",\"content\":\"\",\"aliases\":\"\"}]," +
                "\"outline\":[{\"title\":\"\",\"content\":\"\"}]," +
                "\"foreshadowing\":[{\"title\":\"\",\"detail\":\"\"}]}"
        )
        appendLine("规则：")
        appendLine("- 人物：有名字、有身份/性格/关系才抽；重名不要重复。")
        appendLine("- 设定：世界观、地点、组织、规则、重要物品。")
        appendLine("- 灵感/片段：值得留作素材的句子或点子。")
        appendLine("- 大纲：这一章实际发生了什么，2-5 条，写具体事件。")
        appendLine("- 伏笔：明确埋下、后面需要回收的线索；没有就空数组。")
        appendLine("- 每条 content 用 1-3 句话，不要照抄大段原文。没有的数组写 []。")
    }

    private fun parse(raw: String, chapterIndex: Int): List<StructuredItem> {
        val json = JsonSalvage.extractObject(raw) ?: return emptyList()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val items = mutableListOf<StructuredItem>()
        root.optJSONArray("notes")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                val content = item.optString("content").trim()
                if (title.isBlank() || content.isBlank()) continue
                items.add(
                    StructuredItem(
                        bucket = StructuredItem.NOTE,
                        kind = normalizeKind(item.optString("kind")),
                        title = title.take(60),
                        content = content.take(2000),
                        chapterIndex = chapterIndex
                    )
                )
            }
        }
        root.optJSONArray("outline")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                val content = item.optString("content").trim()
                if (title.isBlank() && content.isBlank()) continue
                items.add(
                    StructuredItem(
                        bucket = StructuredItem.OUTLINE,
                        kind = "",
                        title = title.ifBlank { content.take(20) }.take(60),
                        content = content.take(600),
                        chapterIndex = chapterIndex
                    )
                )
            }
        }
        root.optJSONArray("foreshadowing")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                if (title.isBlank()) continue
                items.add(
                    StructuredItem(
                        bucket = StructuredItem.FORESHADOW,
                        kind = "",
                        title = title.take(60),
                        content = item.optString("detail").trim().take(600),
                        chapterIndex = chapterIndex
                    )
                )
            }
        }
        return items
    }

    /** 模型经常把 kind 写成中文或大写，统一成内部值。 */
    private fun normalizeKind(raw: String): String {
        val value = raw.trim().lowercase()
        if (value in NoteKind.all) return value
        return when {
            value.contains("人物") || value.contains("角色") || value.contains("char") -> NoteKind.CHARACTER
            value.contains("设定") || value.contains("世界") || value.contains("世界") || value.contains("world") ->
                NoteKind.WORLD
            value.contains("灵感") || value.contains("idea") -> NoteKind.IDEA
            value.contains("对白") || value.contains("对话") || value.contains("dialog") -> NoteKind.DIALOGUE
            else -> NoteKind.FRAGMENT
        }
    }

    /** 同名笔记只留一条（合并更长的内容），避免同一人物在每章各抽一遍。 */
    private fun dedupe(items: List<StructuredItem>): List<StructuredItem> {
        val notes = LinkedHashMap<String, StructuredItem>()
        val others = mutableListOf<StructuredItem>()
        items.forEach { item ->
            if (item.bucket != StructuredItem.NOTE) {
                others.add(item)
                return@forEach
            }
            val key = item.kind + "|" + item.title
            val existing = notes[key]
            if (existing == null) {
                notes[key] = item
            } else if (item.content.length > existing.content.length) {
                // 保留内容更长的那条，但章节归属用最早出现的
                notes[key] = existing.copy(content = item.content)
            }
        }
        return (notes.values + others).take(MAX_ITEMS)
    }

    private companion object {
        const val MAX_CHARS_PER_CALL = 6000
        const val MAX_ITEMS = 240
        const val SYSTEM = "你是中文小说资料整理助手。只从给定正文里抽取，不要编造。" +
            "只输出 JSON，不要代码围栏、不要解释。"
    }
}
