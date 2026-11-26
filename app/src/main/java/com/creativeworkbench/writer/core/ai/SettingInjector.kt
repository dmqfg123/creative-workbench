package com.creativeworkbench.writer.core.ai

import com.creativeworkbench.writer.core.data.MentionIndex
import com.creativeworkbench.writer.core.data.NoteEntity
import com.creativeworkbench.writer.core.data.NoteKind

/** 一条设定进上下文时的评估结果。 */
data class SettingHit(
    val note: NoteEntity,
    val text: String,
    val tokens: Int,
    val hits: Int,
    val pinned: Boolean
)

data class InjectionResult(
    val kept: List<SettingHit>,
    val omitted: List<SettingHit>,
    val usedTokens: Int,
    val cap: Int,
    /** 命中的单条自身就超过预算，被迫整条保留。 */
    val overflowed: Boolean = false
) {
    val keptText: String
        get() = kept.joinToString("\n") { it.text }
    val hitCount: Int
        get() = kept.count { it.hits > 0 }
}

/**
 * 设定注入：把「人物 / 世界观」装进固定预算。
 *
 * 借鉴 SillyTavern 世界书的「按命中排序 + 逐条装入 + 超预算停止」：
 * - 命中（标题或别名出现在当前章 / 前一章结尾）的排前面，命中次数多更前；
 * - 同分按置顶、再按 id 升序 —— 稳定序，保证同一份数据两次装配结果一致（便于前缀缓存）；
 * - 按顺序装入预算，装不下的记进 omitted，交给预览显示明细。
 *
 * 与 ST 的差异（有意为之）：不做概率激活、冷却、粘性、递归、分组评分。
 */
object SettingInjector {

    /**
     * @param preserveOrder true = 按传入顺序装（调用方已经定好顺序，比如 AI 自选的结果），
     *   不再按命中重排。模型给的顺序本身表达了轻重，重排会把它丢掉。
     */
    fun inject(
        notes: List<NoteEntity>,
        scanText: String,
        cap: Int,
        preserveOrder: Boolean = false
    ): InjectionResult {
        if (notes.isEmpty()) return InjectionResult(emptyList(), emptyList(), 0, cap)
        val scored = notes.map { note ->
            val text = render(note)
            SettingHit(
                note = note,
                text = text,
                tokens = TokenEstimator.estimate(text),
                hits = countHits(note, scanText),
                pinned = note.pinned
            )
        }
        val sorted = if (preserveOrder) {
            scored
        } else {
            scored.sortedWith(
                compareByDescending<SettingHit> { it.hits > 0 }
                    .thenByDescending { it.hits }
                    .thenByDescending { it.pinned }
                    .thenBy { it.note.id }
            )
        }
        val kept = mutableListOf<SettingHit>()
        val omitted = mutableListOf<SettingHit>()
        var used = 0
        var overflowed = false
        // 置顶条目 = SillyTavern 的 constant（常驻）：先装、且不参与预算淘汰。
        // 以前置顶只影响排序，预算满了一样被砍——用户把一条设为「置顶」，结果它没进上下文。
        // 常驻条目通常就几条，硬留的代价可控；真的太多时由调用方在预览里看到「设定超预算」。
        val (always, rest) = if (preserveOrder) listOf<SettingHit>() to sorted else sorted.partition { it.pinned }
        always.forEach { hit ->
            kept.add(hit)
            used += hit.tokens
            if (used > cap) overflowed = true
        }
        rest.forEach { hit ->
            // 溢出用「跳过这一条」而不是「停止装载」：后面可能有更短的条目还装得下。
            if (used + hit.tokens <= cap) {
                kept.add(hit)
                used += hit.tokens
            } else if (hit.hits > 0 && kept.isEmpty()) {
                // 命中的条目即使单条超预算也要留住，否则「出场即注入」形同虚设
                kept.add(hit)
                used += hit.tokens
                overflowed = true
            } else {
                omitted.add(hit)
            }
        }
        return InjectionResult(kept, omitted, used, cap, overflowed)
    }

    /**
     * 标题 + 别名在正文里的出现次数。
     * 直接委托 MentionIndex：界面上的「本章提到谁」和这里注入的命中数必须是同一份规则，
     * 否则用户会看到「显示命中了 3 条，实际只注入了 2 条」这种没法解释的差异。
     */
    fun countHits(note: NoteEntity, text: String): Int = MentionIndex.countIn(note, text)

    /** 条目渲染成提示词里的样子；设定与素材共用。 */
    fun render(note: NoteEntity): String {
        val aliases = MentionIndex.names(note).drop(1)
        val aliasText = if (aliases.isEmpty()) "" else "（别名：" + aliases.joinToString("、") + "）"
        return "- [" + NoteKind.label(note.kind) + "] " + note.title + aliasText + "：" + note.content
    }
}