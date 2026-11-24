package com.creativeworkbench.writer.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusedBlockScrollTest {
    private fun items(vararg bounds: Triple<Int, Int, Int>): List<VisibleBlockBounds> =
        bounds.map { VisibleBlockBounds(it.first, it.second, it.third) }

    @Test
    fun fullyVisibleNeedsNoScroll() {
        val target = focusedBlockScrollTarget(
            focusedIndex = 1,
            visibleItems = items(Triple(0, 0, 100), Triple(1, 100, 100), Triple(2, 200, 100)),
            viewportStartOffset = 0,
            viewportEndOffset = 400,
            bottomMarginPx = 12
        )
        assertNull(target)
    }

    @Test
    fun partiallyClippedBelowScrollsByClippedAmount() {
        val target = focusedBlockScrollTarget(
            focusedIndex = 2,
            visibleItems = items(Triple(0, 0, 100), Triple(1, 100, 100), Triple(2, 200, 100)),
            viewportStartOffset = 0,
            viewportEndOffset = 280,
            bottomMarginPx = 12
        )
        assertTrue(target is FocusScrollTarget.By)
        assertEquals(32, (target as FocusScrollTarget.By).deltaPx)
    }

    @Test
    fun notComposedBelowUsesToItemWithEstimatedOffset() {
        val target = focusedBlockScrollTarget(
            focusedIndex = 5,
            visibleItems = items(Triple(0, 0, 100), Triple(1, 100, 100)),
            viewportStartOffset = 0,
            viewportEndOffset = 300,
            bottomMarginPx = 12
        )
        assertTrue(target is FocusScrollTarget.ToItem)
        assertEquals(5, (target as FocusScrollTarget.ToItem).index)
        assertTrue(target.scrollOffset >= 0)
    }

    @Test
    fun aboveViewportScrollsToTop() {
        val target = focusedBlockScrollTarget(
            focusedIndex = 0,
            visibleItems = items(Triple(2, 0, 100), Triple(3, 100, 100)),
            viewportStartOffset = 0,
            viewportEndOffset = 300,
            bottomMarginPx = 12
        )
        assertTrue(target is FocusScrollTarget.ToItem)
        assertEquals(0, (target as FocusScrollTarget.ToItem).scrollOffset)
    }
}
