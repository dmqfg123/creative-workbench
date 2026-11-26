package com.creativeworkbench.writer.core.ai

import com.creativeworkbench.writer.core.data.NoteEntity
import com.creativeworkbench.writer.core.data.NoteKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingInjectorTest {

    private fun note(
        id: String,
        title: String,
        content: String,
        kind: String = NoteKind.CHARACTER,
        aliases: String = "",
        pinned: Boolean = false
    ) = NoteEntity(
        id = id,
        workId = "w1",
        kind = kind,
        title = title,
        aliases = aliases,
        content = content,
        tags = "",
        aiReference = false,
        pinned = pinned,
        createdAtMs = 1L,
        updatedAtMs = 1L,
        sourceNoteId = null
    )

    @Test
    fun hitEntryComesBeforeNonHitEvenIfIdIsLater() {
        val a = note("a", "甲", "一号人物")
        val b = note("b", "乙", "二号人物")
        val result = SettingInjector.inject(listOf(a, b), "乙走进钟楼。", 10_000)
        assertEquals(listOf("乙", "甲"), result.kept.map { it.note.title })
        assertEquals(1, result.hitCount)
    }

    @Test
    fun moreHitsRankHigher() {
        val a = note("a", "甲", "x")
        val b = note("b", "乙", "y")
        val result = SettingInjector.inject(listOf(a, b), "乙 乙 乙，甲 一次", 10_000)
        assertEquals("乙", result.kept.first().note.title)
    }

    @Test
    fun sameScoreUsesIdAscendingForStableOrder() {
        val a = note("a", "甲", "x")
        val b = note("b", "乙", "y")
        val first = SettingInjector.inject(listOf(b, a), "无关文本", 10_000)
        val second = SettingInjector.inject(listOf(a, b), "无关文本", 10_000)
        assertEquals(listOf("a", "b"), first.kept.map { it.note.id })
        assertEquals(listOf("a", "b"), second.kept.map { it.note.id })
    }

    @Test
    fun pinnedWinsTieBreak() {
        val a = note("a", "甲", "x")
        val b = note("b", "乙", "y", pinned = true)
        val result = SettingInjector.inject(listOf(a, b), "无关文本", 10_000)
        assertEquals("b", result.kept.first().note.id)
    }

    @Test
    fun singleCharChineseAliasIsIgnored() {
        val a = note("a", "林先生", "x", aliases = "雨,林")
        val result = SettingInjector.inject(listOf(a), "下雨了，林先生在场。", 10_000)
        // "林" 单字不参与，但标题 "林先生" 命中一次
        assertEquals(1, result.hitCount)
    }

    @Test
    fun englishAliasMatchesWholeWordOnly() {
        val a = note("a", "Rain", "x", aliases = "Rain")
        assertEquals(0, SettingInjector.countHits(a, "It is raining hard."))
        assertEquals(1, SettingInjector.countHits(a, "Rain fell on the tower."))
    }

    @Test
    fun budgetTrimsAndReportsOmitted() {
        val big = note("a", "甲", "很长的设定".repeat(400))
        val small = note("b", "乙", "短设定")
        val result = SettingInjector.inject(listOf(big, small), "乙", 600)
        assertEquals(listOf("乙"), result.kept.map { it.note.title })
        assertTrue(result.omitted.map { it.note.title }.contains("甲"))
        assertTrue(result.usedTokens <= 600)
    }

    @Test
    fun hitEntryOverCapIsStillKept() {
        val huge = note("a", "甲", "命中但很长".repeat(2000))
        val result = SettingInjector.inject(listOf(huge), "甲", 100)
        assertEquals(1, result.kept.size)
        assertTrue(result.overflowed)
    }

    @Test
    fun emptyNotesProduceEmptyResult() {
        val result = SettingInjector.inject(emptyList(), "正文", 1000)
        assertTrue(result.kept.isEmpty())
        assertTrue(result.omitted.isEmpty())
        assertEquals(0, result.usedTokens)
    }

    @Test
    fun aliasesSeparatedByChineseCommaMatch() {
        val a = note("a", "钟楼", "x", aliases = "旧钟楼，钟塔")
        assertTrue(SettingInjector.countHits(a, "他走进钟塔。") >= 1)
    }
}
