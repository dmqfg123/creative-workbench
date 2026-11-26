package com.creativeworkbench.writer.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 导入分章：中英混合标题都要认出来，否则整本会被当成一章。 */
class ChapterSplitterMultiLanguageTest {

    @Test
    fun recognizesEnglishChapterHeadings() {
        val text = """
            Chapter 1 The Letter
            He opened the door.

            Chapter 2 The Rain
            She waited.
        """.trimIndent()
        val slices = ChapterSplitter.split(text)
        assertEquals(2, slices.size)
        assertTrue(slices[0].title.contains("Chapter 1"))
    }

    @Test
    fun recognizesNumericHeadings() {
        val text = """
            1. 开头
            正文一。

            2. 转折
            正文二。
        """.trimIndent()
        val slices = ChapterSplitter.split(text)
        assertEquals(2, slices.size)
    }

    @Test
    fun recognizesChineseNumeralHeadings() {
        val text = """
            一、开端
            正文一。

            二、发展
            正文二。
        """.trimIndent()
        val slices = ChapterSplitter.split(text)
        assertEquals(2, slices.size)
    }
}
