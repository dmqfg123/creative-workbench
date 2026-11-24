package com.creativeworkbench.writer.core.ai

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

    /** 别名分隔符：中英文逗号、顿号都认。 */
    private val aliasSeparators = Regex("[,，、;；]")

    fun inject(notes: List<NoteEntity>, scanText: String, cap: Int): InjectionResult {
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
        val sorted = scored.sortedWith(
            compareByDescending<SettingHit> { it.hits > 0 }
                .thenByDescending { it.hits }
                .thenByDescending { it.pinned }
                .thenBy { it.note.id }
        )
        val kept = mutableListOf<SettingHit>()
        val omitted = mutableListOf<SettingHit>()
        var used = 0
        var overflowed = false
        sorted.forEach { hit ->
            when {
                used + hit.tokens <= cap -> {
                    kept.add(hit)
                    used += hit.tokens
                }
                // 命中的条目即使单条超预算也要留住，否则「出场即注入」形同虚设
                hit.hits > 0 && kept.isEmpty() -> {
                    kept.add(hit)
                    used += hit.tokens
                    overflowed = true
                }
                else -> omitted.add(hit)
            }
        }
        return InjectionResult(kept, omitted, used, cap, overflowed)
    }

    /** 标题 + 别名在正文里的出现次数；单字中文别名太吵，不参与匹配。 */
    fun countHits(note: NoteEntity, text: String): Int {
        if (text.isBlank()) return 0
        var total = countName(note.title, text, allowSingleChar = true)
        val seen = mutableSetOf(note.title)
        note.aliases.split(aliasSeparators)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { alias ->
                if (seen.add(alias)) total += countName(alias, text, allowSingleChar = false)
            }
        return total
    }

    private fun countName(name: String, text: String, allowSingleChar: Boolean): Int {
        val codePoints = name.codePointCount(0, name.length)
        val asciiOnly = name.all { it.code < 128 }
        if (!allowSingleChar && !asciiOnly && codePoints < 2) return 0
        if (name.isBlank()) return 0
        return if (asciiOnly) {
            // 英文按整词算，避免 "Rain" 命中 "raining"
            val regex = Regex(
                "(?<![A-Za-z0-9])" + Regex.escape(name) + "(?![A-Za-z0-9])",
                RegexOption.IGNORE_CASE
            )
            regex.findAll(text).count()
        } else {
            var index = text.indexOf(name, ignoreCase = true)
            var count = 0
            while (index >= 0) {
                count++
                index = text.indexOf(name, index + name.length.coerceAtLeast(1), ignoreCase = true)
            }
            count.coerceAtMost(20)
        }
    }

    /** 条目渲染成提示词里的样子；设定与素材共用。 */
    fun render(note: NoteEntity): String {
        val aliases = note.aliases.split(aliasSeparators).map { it.trim() }.filter { it.isNotEmpty() }
        val aliasText = if (aliases.isEmpty()) "" else "（别名：" + aliases.joinToString("、") + "）"
        return "- [" + NoteKind.label(note.kind) + "] " + note.title + aliasText + "：" + note.content
    }
}