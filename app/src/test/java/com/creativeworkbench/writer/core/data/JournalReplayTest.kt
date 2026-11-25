package com.creativeworkbench.writer.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * journal 回放：崩溃恢复唯一的正确性保障。
 * 以前的实现只按 id 覆盖文本，删除的段会复活、新段会被甩到章末；这组用例就是钉住这两个坑。
 */
class JournalReplayTest {

    private val base = listOf(Block("a", "第一段"), Block("b", "第二段"), Block("c", "第三段"))

    private fun layout(seq: Long, vararg ids: String) =
        JournalRecord(seq = seq, kind = JournalRecord.KIND_LAYOUT, order = ids.toList())

    private fun block(seq: Long, id: String, text: String) =
        JournalRecord(seq = seq, kind = JournalRecord.KIND_BLOCK, blockId = id, text = text)

    @Test
    fun deletedBlockStaysDeleted() {
        // 删掉 b：layout 里没有 b
        val records = listOf(layout(1, "a", "c"), block(2, "a", "第一段"), block(3, "c", "第三段"))
        val result = JournalReplay.apply(base, records)
        assertEquals(listOf("a", "c"), result.map { it.id })
        assertEquals("已删的段不能被 journal 复活", 2, result.size)
    }

    @Test
    fun insertedBlockKeepsItsPosition() {
        // 在 a 之后插入 x
        val records = listOf(
            layout(1, "a", "x", "b", "c"),
            block(2, "x", "新插的段")
        )
        val result = JournalReplay.apply(base, records)
        assertEquals(listOf("a", "x", "b", "c"), result.map { it.id })
        assertEquals("新插的段", result[1].text)
    }

    @Test
    fun laterLayoutWins() {
        val records = listOf(
            layout(1, "a", "b", "c"),
            layout(2, "c", "b", "a"),
            block(3, "a", "改过的第一段")
        )
        val result = JournalReplay.apply(base, records)
        assertEquals(listOf("c", "b", "a"), result.map { it.id })
        assertEquals("改过的第一段", result[2].text)
    }

    @Test
    fun textUpdatesApplyWithoutTouchingOrder() {
        val records = listOf(block(1, "b", "第二段（改）"))
        val result = JournalReplay.apply(base, records)
        assertEquals(listOf("a", "b", "c"), result.map { it.id })
        assertEquals("第二段（改）", result[1].text)
    }

    @Test
    fun legacyJournalWithoutLayoutStillReadable() {
        // 老格式：没有 layout，只有 block 记录；新 id 只能追加到末尾
        val records = listOf(block(1, "b", "第二段（改）"), block(2, "d", "旧 journal 里的新段"))
        val result = JournalReplay.apply(base, records)
        assertEquals(listOf("a", "b", "c", "d"), result.map { it.id })
        assertEquals("第二段（改）", result[1].text)
    }

    @Test
    fun emptyRecordsReturnBaseUntouched() {
        assertEquals(base, JournalReplay.apply(base, emptyList()))
    }

    @Test
    fun layoutDroppingEverythingFallsBackToSingleEmptyBlock() {
        val result = JournalReplay.apply(base, listOf(layout(1)))
        assertEquals(1, result.size)
        assertEquals("", result[0].text)
    }
}
