package com.creativeworkbench.writer.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenEstimatorTest {
    @Test
    fun chineseCountsAsOneTokenPerCharWithOverhead() {
        val estimate = TokenEstimator.estimate("你好世界")
        assertTrue(estimate >= 4)
        assertTrue(estimate <= 6)
    }

    @Test
    fun asciiCountsCheaper() {
        val estimate = TokenEstimator.estimate("hello")
        assertTrue(estimate in 1..4)
    }

    @Test
    fun budgetSplitSumsToTotal() {
        val budget = ContextBudget(windowTokens = 32768, reservedOutputTokens = 2048)
        assertEquals((32768 * 0.6).toInt() - 2048, budget.budgetTokens)
        assertEquals((budget.budgetTokens * 0.40).toInt(), budget.settingCap)
        assertEquals((budget.budgetTokens * 0.40).toInt(), budget.chapterCap)
    }
}
