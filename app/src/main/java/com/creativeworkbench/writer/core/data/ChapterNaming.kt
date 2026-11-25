package com.creativeworkbench.writer.core.data

/**
 * 新章节的默认名：从「第 N 章」往后找第一个没被占用的。
 * 老实现直接用「章数 + 1」，删过章之后会和现存章重名（删掉第 2 章后新建，又叫第 3 章）。
 */
object ChapterNaming {
    fun next(existingTitles: List<String>): String {
        val used = existingTitles.toSet()
        var n = existingTitles.size + 1
        while (used.contains("第" + n + "章")) n++
        return "第" + n + "章"
    }
}
