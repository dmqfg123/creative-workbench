package com.creativeworkbench.writer.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 退避算法与 DSH（dsh-llm/src/retry-policy.ts）一致：
 * 500ms 起、指数翻倍、最长 10s、对称抖动 ±10%。random 传固定值，结果可复现。
 */
class RetryPolicyTest {
    private val policy = RetryPolicy()

    @Test
    fun doublesUpToTheCap() {
        // random=0.5 → 抖动系数正好 1.0，等价于纯指数
        assertEquals(500L, policy.delayMs(1, 0.5))
        assertEquals(1000L, policy.delayMs(2, 0.5))
        assertEquals(2000L, policy.delayMs(3, 0.5))
        assertEquals(4000L, policy.delayMs(4, 0.5))
        assertEquals(8000L, policy.delayMs(5, 0.5))
        // 第 6 次本来该 16000，被 maxDelayMs 夹到 10000
        assertEquals(10_000L, policy.delayMs(6, 0.5))
    }

    @Test
    fun jitterStaysInsideTenPercent() {
        val low = policy.delayMs(3, 0.0)
        val high = policy.delayMs(3, 1.0)
        assertTrue("低抖动 " + low, low in 1700..1801)
        assertTrue("高抖动 " + high, high in 2199..2201)
    }

    @Test
    fun retryableStatusOnlyCoversTransient() {
        assertTrue(AiFailure.isRetryableStatus(429))
        assertTrue(AiFailure.isRetryableStatus(500))
        assertTrue(AiFailure.isRetryableStatus(502))
        assertTrue(AiFailure.isRetryableStatus(504))
        assertTrue(AiFailure.isRetryableStatus(408))
        // 这些重试也没用，必须立刻失败：Key 错、余额不足、请求格式错
        assertFalse(AiFailure.isRetryableStatus(400))
        assertFalse(AiFailure.isRetryableStatus(401))
        assertFalse(AiFailure.isRetryableStatus(402))
        assertFalse(AiFailure.isRetryableStatus(403))
        assertFalse(AiFailure.isRetryableStatus(422))
    }

    @Test
    fun cancellationIsNeverRetried() {
        assertFalse(AiFailure.isRetryable(java.io.IOException("Canceled")))
        assertFalse(AiFailure.isRetryable(java.io.IOException("socket closed, cancelled")))
        assertTrue(AiFailure.isRetryable(java.io.IOException("unexpected end of stream")))
    }

    @Test
    fun retryAfterIsHonoredButBounded() {
        assertEquals(3000L, AiFailure.retryAfterMs("3"))
        assertEquals(null, AiFailure.retryAfterMs(null))
        assertEquals(null, AiFailure.retryAfterMs("not-a-number"))
        // 超过两分钟的服务端要求不接受，退回本地退避
        assertEquals(null, AiFailure.retryAfterMs("9999"))
    }
}
