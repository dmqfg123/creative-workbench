package com.creativeworkbench.writer.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 改写闪退回归：系统选区可能超出当前文本长度，必须夹住而不是直接 substring。 */
class SelectionClampTest {

    @Test
    fun staleEndBeyondLengthIsClamped() {
        // 崩溃现场：begin 228, end 274, length 253
        assertEquals(228..252, clampSelection(253, 228, 274))
    }

    @Test
    fun bothBeyondLengthCollapses() {
        assertNull(clampSelection(253, 300, 400))
    }

    @Test
    fun negativeStartIsClamped() {
        assertEquals(0..9, clampSelection(100, -5, 10))
    }

    @Test
    fun emptySelectionIsNull() {
        assertNull(clampSelection(100, 10, 10))
        assertNull(clampSelection(100, 20, 10))
        assertNull(clampSelection(0, 0, 5))
    }

    @Test
    fun normalSelectionKeepsBounds() {
        assertEquals(5..14, clampSelection(100, 5, 15))
    }
}
