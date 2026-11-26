package com.creativeworkbench.writer.core.ai

import kotlin.math.min
import kotlin.math.pow

/**
 * 请求重试策略。
 *
 * 数值直接对齐 DSH（deepseek-harness）的 provider 默认值：
 * `dsh-llm/src/retry-policy.ts` 里 DEFAULT_MAX_RETRIES=5、
 * DEFAULT_INITIAL_DELAY_MS=500、DEFAULT_MAX_DELAY_MS=10_000、DEFAULT_JITTER_RATIO=0.1，
 * 可重试码是 EMPTY_RESPONSE / RATE_LIMIT / SERVER / TIMEOUT / TRANSPORT
 * （`dsh-llm-retry` 负责执行，本地退避是「有界指数 + 对称抖动」）。
 *
 * 之前这个 App 一次失败就直接报错：429 限流、502 网关抖动、超时都要人手动点重试，
 * 长文生成时尤其烦。这里照抄同一套退避。
 */
data class RetryPolicy(
    val maxRetries: Int = 5,
    val initialDelayMs: Long = 500L,
    val maxDelayMs: Long = 10_000L,
    val jitterRatio: Double = 0.1
) {
    /**
     * 第 n 次重试前的等待时间（n 从 1 起）。
     * 指数部分按 DSH 的 min(initial * 2^(n-1), max)，再乘对称抖动系数 [1-j, 1+j]，最后再夹一次上限。
     * random 只在测试里传固定值，生产用 Math.random()。
     */
    fun delayMs(retry: Int, random: Double = Math.random()): Long {
        val exponent = (retry - 1).coerceIn(0, 16)
        val exponential = min(initialDelayMs * 2.0.pow(exponent), maxDelayMs.toDouble())
        val jitter = 1 - jitterRatio + 2 * jitterRatio * random
        return min(exponential * jitter, maxDelayMs.toDouble()).toLong().coerceAtLeast(1L)
    }
}

/**
 * 哪些失败值得重试。分成三类，和 DSH 的 BuiltinLlmFailureCode 一一对应：
 * - HTTP 408/429/5xx → TIMEOUT / RATE_LIMIT / SERVER
 * - 网络异常（连接重置、DNS、超时）→ TRANSPORT / TIMEOUT
 * - 模型返回空内容且不是被截断 → EMPTY_RESPONSE
 *
 * 不重试：400（请求格式）、401/403（Key/权限）、402（余额）、422（参数）——
 * 这些重试一百次也一样，只会让用户多等。
 */
object AiFailure {
    fun isRetryableStatus(code: Int): Boolean =
        code == 408 || code == 429 || code in 500..599

    fun isRetryable(error: Throwable): Boolean {
        val message = (error.message ?: "").lowercase()
        // 主动取消不是失败，绝不能重试：否则用户点了「停止」它又发一遍
        if (message.contains("canceled") || message.contains("cancelled")) return false
        return true
    }

    /** Retry-After 只认秒数（HTTP 日期格式在移动端几乎不会出现，真出现就退回本地退避）。 */
    fun retryAfterMs(header: String?): Long? =
        header?.trim()?.toLongOrNull()?.takeIf { it in 0..120 }?.times(1000L)
}
