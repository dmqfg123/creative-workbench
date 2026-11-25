package com.creativeworkbench.writer.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 交叉索引的匹配规则。这些规则不是新发明的，是从 SettingInjector 原有的命中逻辑搬过来的
 * （参考 SillyTavern 世界书的整词/别名匹配、novelWriter 的标签索引）：
 * 界面显示「本章提到谁」和 AI 实际注入的条数必须是同一份规则，所以这里要锁死。
 */
class MentionIndexTest {

    private fun note(id: String, title: String, aliases: String = "", kind: String = NoteKind.CHARACTER) =
        NoteEntity(
            id = id,
            workId = "w1",
            kind = kind,
            title = title,
            aliases = aliases,
            content = "",
            createdAtMs = 0,
            updatedAtMs = 0
        )

    @Test
    fun asciiNamesMatchWholeWordsOnly() {
        val rain = note("n1", "Rain")
        assertEquals(0, MentionIndex.countIn(rain, "It is raining outside"))
        assertEquals(1, MentionIndex.countIn(rain, "Rain falls on the dike"))
        // 大小写不敏感
        assertEquals(1, MentionIndex.countIn(rain, "rain falls"))
    }

    @Test
    fun cjkNamesMatchAsSubstring() {
        val lin = note("n2", "林小满")
        assertEquals(2, MentionIndex.countIn(lin, "林小满来了。林小满说完就走。"))
    }

    @Test
    fun aliasesCountButSingleCharCjkAliasIsIgnored() {
        val note1 = note("n3", "林小满", "小满,满姐")
        assertEquals(1 + 1 + 1, MentionIndex.countIn(note1, "林小满说，小满笑，满姐走了"))
        // 单个汉字别名太吵：不参与匹配
        val noisy = note("n4", "张越", "越")
        assertEquals(1, MentionIndex.countIn(noisy, "张越看着她。越说越远。"))
    }

    @Test
    fun scanSortsByCountAndSkipsAbsent() {
        val a = note("a", "刘玉")
        val b2 = note("b", "王欣瑶")
        val c = note("c", "不出现的名字")
        val hits = MentionIndex.scan(listOf(a, b2, c), "刘玉和刘玉，还有王欣瑶")
        assertEquals(2, hits.size)
        assertEquals("刘玉", hits[0].note.title)
        assertEquals(2, hits[0].count)
        assertEquals("王欣瑶", hits[1].note.title)
    }

    @Test
    fun textOfSeparatesBlocksSoWordsDoNotGlueTogether() {
        val blocks = listOf(Block("b1", "Rain"), Block("b2", "fall"))
        assertTrue(MentionIndex.textOf(blocks).contains("\n"))
        val rain = note("n5", "Rainfalls")
        assertEquals(0, MentionIndex.countIn(rain, MentionIndex.textOf(blocks)))
    }
}
