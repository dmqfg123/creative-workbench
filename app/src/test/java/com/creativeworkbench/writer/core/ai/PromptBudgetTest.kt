package com.creativeworkbench.writer.core.ai

import com.creativeworkbench.writer.core.data.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBudgetTest {

    private fun blocks(count: Int, chars: Int = 40): List<Block> =
        (1..count).map { Block("b$it", "第" + it + "段" + "字".repeat(chars)) }

    @Test
    fun keepsEverythingWhenWithinBudget() {
        val list = blocks(3)
        val fit = PromptBudget.fitBlocks(list, list.indices.toList(), 10_000, 1)
        assertEquals(3L, fit.kept.toLong())
        assertTrue(fit.dropped.isEmpty())
    }

    @Test
    fun dropsWholeBlocksFromFarSideAndKeepsAnchor() {
        val list = blocks(10)
        val cap = TokenEstimator.estimate("[[b0]] " + list[0].text) * 3 + 10
        val fit = PromptBudget.fitBlocks(list, list.indices.toList(), cap, 9)
        assertTrue(fit.tokens <= cap)
        assertTrue(fit.kept < 10)
        assertTrue(fit.text.contains("第10段"))
        assertTrue(fit.dropped.isNotEmpty())
    }

    @Test
    fun neverCutsABlockInHalf() {
        val list = blocks(6)
        val cap = 400
        val fit = PromptBudget.fitBlocks(list, list.indices.toList(), cap, 3)
        list.forEachIndexed { index, block ->
            val mentioned = fit.text.contains("[[b" + index + "]] " + block.text)
            val dropped = fit.dropped.any { it.label == "第 " + (index + 1) + " 段" }
            assertTrue("block $index 要么整段在，要么整段不在", mentioned || dropped)
            if (dropped) assertTrue(!fit.text.contains("[[b" + index + "]]"))
        }
    }

    @Test
    fun keepsSingleOversizedBlock() {
        val list = listOf(Block("b1", "字".repeat(2000)))
        val fit = PromptBudget.fitBlocks(list, listOf(0), 100, 0)
        assertEquals(1L, fit.kept.toLong())
        assertTrue(fit.dropped.isEmpty())
    }

    @Test
    fun entriesDropEarliestFirstWhenKeepTail() {
        val entries = (1..5).map { "第" + it + "章" to "第" + it + "章：" + "字".repeat(60) }
        val fit = PromptBudget.fitEntries(entries, 150, "摘要", keepTail = true)
        assertTrue(fit.text.contains("第5章"))
        assertTrue(fit.dropped.any { it.label == "第1章" })
    }

    @Test
    fun entriesDropLatestFirstWhenNotKeepTail() {
        val entries = (1..5).map { "素材" + it to "素材" + it + "：" + "字".repeat(60) }
        val fit = PromptBudget.fitEntries(entries, 150, "素材", keepTail = false)
        assertTrue(fit.text.contains("素材1"))
        assertTrue(fit.dropped.any { it.label == "素材5" })
    }

    @Test
    fun emptyInputIsEmptyResult() {
        val fit = PromptBudget.fitBlocks(emptyList(), emptyList(), 100, null)
        assertEquals(0L, fit.kept.toLong())
        assertEquals("", fit.text)
    }
}
