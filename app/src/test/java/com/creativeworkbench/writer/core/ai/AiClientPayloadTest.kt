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
        assertEquals(8192, json.getInt("max_tokens"))
        assertFalse(json.getBoolean("stream"))
    }

    @Test
    fun deepSeekThinkingModeOmitsTemperatureAndRaisesMaxTokens() {
        val json = payload(AiConfig(model = "deepseek-flash", deepThinking = true))
        assertFalse("思考模式不该发 temperature", json.has("temperature"))
        assertEquals("enabled", json.getJSONObject("thinking").getString("type"))
        assertEquals(32768, json.getInt("max_tokens"))
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
    fun toolsAreDeclaredAsOpenAiFunctions() {
        val json = AiClient().buildPayload(
            config = AiConfig(model = "deepseek-flash"),
            tuning = ActionTuning(),
            messages = messages,
            tools = listOf(
                AiToolSpec(
                    name = "create_note",
                    description = "新建笔记",
                    parameters = ToolSchema.obj(listOf("title"), "title" to ToolSchema.str("标题")),
                    mutating = true
                )
            )
        )
        assertEquals("auto", json.getString("tool_choice"))
        val tool = json.getJSONArray("tools").getJSONObject(0)
        assertEquals("function", tool.getString("type"))
        val function = tool.getJSONObject("function")
        assertEquals("create_note", function.getString("name"))
        assertEquals(
            1,
            function.getJSONObject("parameters").getJSONArray("required").length()
        )
    }

    @Test
    fun assistantToolCallsSurviveRoundTrip() {
        val calls = listOf(ToolCall("call_1", "search_notes", "{\"query\":\"林\"}"))
        val json = AiClient().buildPayload(
            config = AiConfig(model = "deepseek-flash"),
            tuning = ActionTuning(),
            messages = listOf(
                ChatMessage("user", "查一下"),
                ChatMessage("assistant", "", calls),
                ChatMessage("tool", "没找到", toolCallId = "call_1")
            )
        )
        val list = json.getJSONArray("messages")
        val assistant = list.getJSONObject(1)
        assertEquals("tool_calls 必须原样带回", true, assistant.has("tool_calls"))
        assertEquals(
            "call_1",
            assistant.getJSONArray("tool_calls").getJSONObject(0).getString("id")
        )
        assertEquals("call_1", list.getJSONObject(2).getString("tool_call_id"))
    }

    @Test
    fun jsonModeOnlyForDeepSeek() {
        val deepSeek = AiClient().buildPayload(
            config = AiConfig(model = "deepseek-flash"),
            tuning = ActionTuning(),
            messages = messages,
            jsonMode = true
        )
        assertEquals("json_object", deepSeek.getJSONObject("response_format").getString("type"))

        val generic = AiClient().buildPayload(
            config = AiConfig(baseUrl = "https://example.com/v1", model = "gpt-4o-mini"),
            tuning = ActionTuning(),
            messages = messages,
            jsonMode = true
        )
        assertEquals("通用服务不该收到 response_format", false, generic.has("response_format"))
    }

    @Test
    fun genericCompatibleServerNeverGetsDeepSeekFields() {
        val json = payload(AiConfig(baseUrl = "https://example.com/v1", model = "gpt-4o-mini"))
        assertFalse("通用服务不该收到 thinking", json.has("thinking"))
        assertEquals(0.8, json.getDouble("temperature"), 0.0001)
        assertEquals(2048, json.getInt("max_tokens"))
    }
}
