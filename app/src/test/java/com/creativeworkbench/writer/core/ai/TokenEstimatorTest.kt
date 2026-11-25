package com.creativeworkbench.writer.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenEstimatorTest {
    /** DeepSeek 官方口径：1 汉字 ≈ 0.6 token，估算留 10% 余量。 */
    @Test
    fun chineseCountsSixTenthsTokenPerChar() {
        val estimate = TokenEstimator.estimate("你好世界")
        assertTrue("4 个汉字应在 2-4 token 之间，实际 " + estimate, estimate in 2..4)
    }

    @Test
    fun asciiCountsCheaper() {
        val estimate = TokenEstimator.estimate("hello")
        assertTrue(estimate in 1..4)
    }

    @Test
    fun thousandChineseCharsStayUnderSevenHundredTokens() {
        val estimate = TokenEstimator.estimate("字".repeat(1000))
        assertTrue("1000 汉字应 ≈660 token，实际 " + estimate, estimate in 600..700)
    }

    /**
     * 五个槽（设定/正文/大纲/摘要/素材）加起来必须正好等于预算：
     * 0.13.0 加大纲槽后，比例漂一点就会让「已用 > 预算」，预览直接自相矛盾。
     */
    @Test
    fun budgetSplitSumsToTotal() {
        val budget = ContextBudget(windowTokens = 32768, reservedOutputTokens = 2048)
        assertEquals((32768 * 0.6).toInt() - 2048, budget.budgetTokens)
        assertEquals((budget.budgetTokens * 0.35).toInt(), budget.settingCap)
        assertEquals((budget.budgetTokens * 0.35).toInt(), budget.chapterCap)
        assertEquals((budget.budgetTokens * 0.10).toInt(), budget.outlineCap)
        val sum = budget.settingCap + budget.chapterCap + budget.outlineCap +
            budget.summaryCap + budget.materialCap
        assertTrue("分槽之和 " + sum + " 不应超过预算 " + budget.budgetTokens, sum <= budget.budgetTokens)
        assertTrue("分槽之和 " + sum + " 不应低于预算的 99%", sum >= (budget.budgetTokens * 0.99).toInt())
    }
}
