package com.creativeworkbench.writer.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 官方错误码 → 人话。文案错了比报错还糟：402 说成"格式不对"会把用户带去改参数。 */
class AiErrorMappingTest {
    private fun msg(code: Int, body: String = "") = AiClient().describeHttpError(code, body)

    @Test
    fun balanceAndRateLimitAreActionable() {
        assertTrue(msg(402, "Insufficient Balance").contains("余额"))
        assertTrue(msg(429, "Rate Limit Reached").contains("频繁"))
        assertTrue(msg(401, "Authentication Fails").contains("API Key"))
    }

    @Test
    fun contextOverflowIsCalledOutSpecifically() {
        val text = msg(400, "This model's maximum context length is 1048576 tokens")
        assertTrue("超长要提示精简上下文，实际：" + text, text.contains("上下文"))
        assertTrue(msg(400, "invalid json body").contains("格式"))
    }

    @Test
    fun serverErrorsAreRetryableCopy() {
        assertEquals("DeepSeek 服务端出错（500），过一会儿重试。", msg(500, ""))
        assertTrue(msg(503, "").contains("重试"))
    }

    @Test
    fun bodyExcerptIsTruncated() {
        val text = msg(422, "x".repeat(500))
        assertTrue("原始 body 不该整段塞给用户", text.length < 240)
    }
}
