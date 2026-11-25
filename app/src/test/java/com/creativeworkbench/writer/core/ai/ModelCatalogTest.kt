package com.creativeworkbench.writer.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型参数表：0.11.0 把「上下文窗口 / 输出上限 / 温度 / 超时」从界面搬到了这里，
 * 这张表错了，界面看不出来，只会变成服务端 400 或无声截断。
 */
class ModelCatalogTest {

    @Test
    fun deepSeekOfficialGetsOneMegabyteWindow() {
        val profile = ModelCatalog.profileFor("https://api.deepseek.com", "deepseek-flash")
        assertEquals(1_048_576, profile.contextWindowTokens)
        assertEquals(393_216, profile.maxOutputTokens)
        assertTrue(profile.supportsThinking)
        // 预算窗口只取 1/4，别因为模型有 1M 就一次塞满
        assertEquals(ModelCatalog.CONTEXT_BUDGET_TOKEN_CAP, profile.budgetWindowTokens)
    }

    @Test
    fun thinkingModeDropsTemperature() {
        val profile = ModelCatalog.profileFor("https://api.deepseek.com", "deepseek-flash")
        val fast = profile.request(ActionTuning(), globalThinking = false)
        val thinking = profile.request(ActionTuning(), globalThinking = true)
        assertEquals(1.2, fast.temperature!!, 0.0001)
        assertNull("思考模式不该发 temperature", thinking.temperature)
        assertTrue(thinking.maxTokens > fast.maxTokens)
        assertTrue(thinking.timeoutSeconds > fast.timeoutSeconds)
    }

    @Test
    fun genericProviderFallsBackToSmallWindow() {
        val profile = ModelCatalog.profileFor("http://127.0.0.1:11434/v1", "qwen2.5:7b")
        assertFalse(profile.supportsThinking)
        assertEquals(32_768, profile.contextWindowTokens)
        assertEquals("通用服务的窗口不该被当成 1M", 32_768, profile.budgetWindowTokens)
    }

    @Test
    fun actionTuningOverridesOutputButNeverExceedsModelCap() {
        val profile = ModelCatalog.profileFor("https://api.deepseek.com", "deepseek-flash")
        val bounded = profile.request(ActionTuning(maxTokens = 4096), globalThinking = false)
        assertEquals(4096, bounded.maxTokens)
        val tooBig = profile.request(ActionTuning(maxTokens = 9_999_999), globalThinking = false)
        assertEquals(393_216, tooBig.maxTokens)
    }

    @Test
    fun thinkingOverrideFollowsGlobalUnlessActionSaysOtherwise() {
        val profile = ModelCatalog.profileFor("https://api.deepseek.com", "deepseek-flash")
        assertTrue(profile.request(ActionTuning(), globalThinking = true).thinking)
        assertFalse(profile.request(ActionTuning(thinking = false), globalThinking = true).thinking)
        // 开了思考就不该再带 temperature
        assertNull(profile.request(ActionTuning(), globalThinking = true).temperature)
        assertEquals(1.2, profile.request(ActionTuning(thinking = false), globalThinking = true).temperature!!, 0.0001)
    }
}
