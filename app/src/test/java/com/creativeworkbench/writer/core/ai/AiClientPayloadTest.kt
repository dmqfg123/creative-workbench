package com.creativeworkbench.writer.core.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 请求体契约：这几个字段错了，界面看不出来，只会被服务端静默忽略或 400。
 * 直接断言 JSON，不联网。
 */
class AiClientPayloadTest {

    private val messages = listOf(ChatMessage("system", "你是助手"), ChatMessage("user", "续写"))

    private fun payload(config: AiConfig, tuning: ActionTuning = ActionTuning()): JSONObject =
        AiClient().buildPayload(config, tuning, messages)

    @Test
    fun deepSeekFastModeSendsTemperatureAndDisablesThinking() {
        val json = payload(AiConfig(model = "deepseek-flash", deepThinking = false))
        assertEquals(1.2, json.getDouble("temperature"), 0.0001)
        assertEquals("disabled", json.getJSONObject("thinking").getString("type"))
        assertEquals(4096, json.getInt("max_tokens"))
        assertFalse(json.getBoolean("stream"))
    }

    @Test
    fun deepSeekThinkingModeOmitsTemperatureAndRaisesMaxTokens() {
        val json = payload(AiConfig(model = "deepseek-flash", deepThinking = true))
        assertFalse("思考模式不该发 temperature", json.has("temperature"))
        assertEquals("enabled", json.getJSONObject("thinking").getString("type"))
        assertEquals(16384, json.getInt("max_tokens"))
    }

    @Test
    fun actionTuningOverridesMaxTokensButKeepsMode() {
        val json = payload(
            AiConfig(model = "deepseek-flash", deepThinking = false),
            ActionTuning(maxTokens = 6144)
        )
        assertEquals(6144, json.getInt("max_tokens"))
        assertEquals(1.2, json.getDouble("temperature"), 0.0001)
    }

    @Test
    fun genericCompatibleServerNeverGetsDeepSeekFields() {
        val json = payload(AiConfig(baseUrl = "https://example.com/v1", model = "gpt-4o-mini"))
        assertFalse("通用服务不该收到 thinking", json.has("thinking"))
        assertEquals(0.8, json.getDouble("temperature"), 0.0001)
        assertEquals(2048, json.getInt("max_tokens"))
    }
}
