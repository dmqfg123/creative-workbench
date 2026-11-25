package com.creativeworkbench.writer.core.data

/**
 * 交叉索引：谁在哪儿被提到。
 *
 * 参考实现：
 * - novelWriter 的 Index/TagsIndex：一份索引同时给出「这段引用了谁」(getReferenceForHeader)
 *   和「谁引用了它」(getBackReferenceList) 与使用次数 (getTagUseCounts)；
 * - Novelcrafter 的 Mentions Tracker：按正名/别名在正文里自动识别条目，可视化「被提及位置」；
 * - SillyTavern 世界书：关键词命中才注入，且按主键/次键/整词匹配。
 *
 * 结论（本项目的实现取向）：**不要为界面再造一套「命中」规则**。
 * AI 注入用的命中判断、编辑器「本章提到谁」、笔记「出现在哪几章」、大纲节点「涉及谁」，
 * 必须共用同一个扫描器，否则界面显示的命中和真正注入的条数会对不上——那是最难查的一类不一致。
 */
object MentionIndex {

    /** 别名分隔符：中英文逗号、顿号。 */
    private val aliasSeparators = Regex("[,，、;；]")

    data class Mention(val note: NoteEntity, val count: Int)

    /** 一条笔记参与匹配的名字：正名 + 别名（去重、去空）。 */
    fun names(note: NoteEntity): List<String> {
        val list = mutableListOf(note.title)
        note.aliases.split(aliasSeparators)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { if (it !in list) list.add(it) }
        return list
    }

    /**
     * 单条笔记在文本里的出现次数。
     * 规则与 SettingInjector 原来的一模一样（搬过来，不是新发明）：
     * - 正名允许单字；别名里单个汉字太吵，不参与；
     * - 英文按整词（避免 Rain 命中 raining），中文按子串；
     * - 忽略大小写。
     */
    fun countIn(note: NoteEntity, text: String): Int {
        if (text.isBlank()) return 0
        // 长名先匹配并占位，短名/别名不许落在已被占用的字符上：
        // 「林小满」命中后，别名「小满」不该在同一个位置再算一次。
        // 不这么做的话，名字越长、别名越多，命中次数就越虚高，排序和界面数字都会失真。
        val all = names(note).mapIndexed { index, name -> name to (index == 0) }
            .sortedByDescending { it.first.length }
        val consumed = BooleanArray(text.length)
        var total = 0
        all.forEach { (name, allowSingle) ->
            total += claimSpans(name, text, allowSingle, consumed)
        }
        return total
    }

    /** 找出 name 在 text 的位置，跳过与已占用字符重叠的那些，返回新计入的次数。 */
    private fun claimSpans(name: String, text: String, allowSingleChar: Boolean, consumed: BooleanArray): Int {
        if (name.isBlank()) return 0
        val codePoints = name.codePointCount(0, name.length)
        val asciiOnly = name.all { it.code < 128 }
        if (!allowSingleChar && !asciiOnly && codePoints < 2) return 0
        var count = 0
        if (asciiOnly) {
            Regex(
                "(?<![A-Za-z0-9])" + Regex.escape(name) + "(?![A-Za-z0-9])",
                RegexOption.IGNORE_CASE
            ).findAll(text).take(20).forEach { match ->
                if (!overlaps(consumed, match.range.first, match.range.last)) {
                    for (i in match.range) consumed[i] = true
                    count++
                }
            }
        } else {
            var index = text.indexOf(name, ignoreCase = true)
            while (index >= 0 && count < 20) {
                val end = index + name.length - 1
                if (!overlaps(consumed, index, end)) {
                    for (i in index..end) consumed[i] = true
                    count++
                }
                index = text.indexOf(name, index + name.length.coerceAtLeast(1), ignoreCase = true)
            }
        }
        return count
    }

    private fun overlaps(consumed: BooleanArray, start: Int, end: Int): Boolean {
        for (i in start..end) {
            if (i in consumed.indices && consumed[i]) return true
        }
        return false
    }

    /** 一段文本里出现了哪些笔记；按次数降序，次数相同按标题。 */
    fun scan(notes: List<NoteEntity>, text: String): List<Mention> {
        if (text.isBlank() || notes.isEmpty()) return emptyList()
        return notes.mapNotNull { note ->
            val count = countIn(note, text)
            if (count > 0) Mention(note, count) else null
        }.sortedWith(compareByDescending<Mention> { it.count }.thenBy { it.note.title })
    }

    /** 把章节正文拼成一段可扫描的文本（块之间换行，避免跨段粘连成全词）。 */
    fun textOf(blocks: List<Block>): String = blocks.joinToString("\n") { it.text }
}
