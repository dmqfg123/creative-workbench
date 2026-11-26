package com.creativeworkbench.writer.core.editor

import com.creativeworkbench.writer.core.data.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EditEngineTest {
    private fun blocks(vararg texts: String): List<Block> =
        texts.mapIndexed { index, text -> Block("b" + index, text) }

    @Test
    fun splitMovesTailIntoNewBlock() {
        val before = blocks("你好世界")
        val entry = EditEngine.apply(before, EditCommand.Split("b0", 2, "new"))
        assertNotNull(entry)
        assertEquals("你好", entry!!.after[0].text)
        assertEquals("世界", entry.after[1].text)
        assertEquals(before, entry.before)
    }

    @Test
    fun splitHandlesSurrogatePair() {
        val before = blocks("A😀B")
        val entry = EditEngine.apply(before, EditCommand.Split("b0", 2, "new"))
        assertNotNull(entry)
        assertEquals("A😀", entry!!.after[0].text)
        assertEquals("B", entry.after[1].text)
    }

    @Test
    fun mergeAppendsLowerText() {
        val before = blocks("上", "下")
        val entry = EditEngine.apply(before, EditCommand.Merge("b0", "b1"))
        assertNotNull(entry)
        assertEquals(1, entry!!.after.size)
        assertEquals("上下", entry.after[0].text)
    }

    @Test
    fun moveReorders() {
        val before = blocks("一", "二", "三")
        val entry = EditEngine.apply(before, EditCommand.Move("b0", 2))
        assertNotNull(entry)
        assertEquals(listOf("二", "三", "一"), entry!!.after.map { it.text })
    }

    @Test
    fun removeKeepsAtLeastOneBlock() {
        val entry = EditEngine.apply(blocks("只有一段"), EditCommand.Remove("b0"))
        assertNotNull(entry)
        assertEquals(1, entry!!.after.size)
        assertEquals("", entry.after[0].text)
    }
}
