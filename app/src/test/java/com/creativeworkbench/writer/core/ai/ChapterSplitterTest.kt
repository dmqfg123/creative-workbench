package com.creativeworkbench.writer.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterSplitterTest {

    @Test
    fun splitsOnChineseChapterHeadings() {
        val text = """
            第一章 雨夜
            他推开门。
            雨很大。

            第二章 灯
            她还没回来。
        """.trimIndent()
        val slices = ChapterSplitter.split(text)
        assertEquals(2, slices.size)
        assertTrue(slices[0].title.contains("第一章"))
        assertTrue(slices[0].body.contains("他推开门"))
        assertEquals(2, slices[1].index)
    }

    @Test
    fun fallsBackToWholeTextWithoutHeadings() {
        val slices = ChapterSplitter.split("只有一段文字，没有章节标题。\n还有一行。")
        assertEquals(1, slices.size)
        assertEquals("全文", slices[0].title)
    }

    @Test
    fun recognizesPrologueAndArabicNumbers() {
        val text = """
            楔子
            很久以前。

            第2章 出发
            他们走了。
        """.trimIndent()
        val slices = ChapterSplitter.split(text)
        assertEquals(2, slices.size)
        assertEquals("楔子", slices[0].title.substringBefore(" "))
    }

    @Test
    fun ignoresOverlongLinesThatOnlyLookLikeHeadings() {
        val longLine = "第一章 " + "很长的正文".repeat(30)
        val text = longLine + "\n正文一\n\n第二章 短标题\n正文二"
        val slices = ChapterSplitter.split(text)
        assertEquals(1, slices.size)
    }
}
