package com.creativeworkbench.writer.core.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 拒写识别：宁可漏判（白跑一次），也不要把正常正文当成拒绝丢掉。 */
class RefusalGuardTest {

    @Test
    fun shortRefusalIsDetected() {
        assertTrue(RefusalGuard.looksLikeRefusal("抱歉，我无法协助生成这类内容。"))
        assertTrue(RefusalGuard.looksLikeRefusal("根据平台规则，我不能提供这段文字。"))
    }

    @Test
    fun longProseIsNotRefusal() {
        val prose = "抱歉这两个字在正文里很常见。".repeat(40)
        assertFalse(RefusalGuard.looksLikeRefusal(prose))
    }

    @Test
    fun multiParagraphProseIsNotRefusal() {
        val prose = "他说对不起。\n她没回头。\n雨还在下。\n他把门关上。"
        assertFalse(RefusalGuard.looksLikeRefusal(prose))
    }

    @Test
    fun emptyTextIsNotRefusal() {
        assertFalse(RefusalGuard.looksLikeRefusal("   "))
    }
}
