package com.creativeworkbench.writer.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 新章默认名：删过章之后不能再和现存章重名。 */
class ChapterNamingTest {
    @Test
    fun emptyLibraryStartsAtOne() {
        assertEquals("第1章", ChapterNaming.next(emptyList()))
    }

    @Test
    fun sequentialTitlesContinue() {
        assertEquals("第2章", ChapterNaming.next(listOf("第1章")))
        assertEquals("第3章", ChapterNaming.next(listOf("第1章", "第2章")))
    }

    @Test
    fun deletedChapterDoesNotCauseDuplicate() {
        // 删掉第 2 章后剩两章：老逻辑会再叫「第3章」，和新章撞名
        assertEquals("第4章", ChapterNaming.next(listOf("第1章", "第3章")))
    }

    @Test
    fun customTitlesStillGetFreeNumber() {
        assertEquals("第3章", ChapterNaming.next(listOf("序章", "尾声")))
    }
}
