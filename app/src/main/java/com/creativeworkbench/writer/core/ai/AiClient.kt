package com.creativeworkbench.writer.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)

/** 一次调用的 token 账：DeepSeek 会额外给缓存命中/未命中。 */
data class AiUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val cacheHitTokens: Int = 0,
    val cacheMissTokens: Int = 0
) {
    val hasCacheInfo: Boolean get() = cacheHitTokens > 0 || cacheMissTokens > 0
    val totalTokens: Int get() = promptTokens + completionTokens

    /** 缓存命中率（按输入 tokens 算）。 */
    val cacheHitRate: Double
        get() = if (promptTokens <= 0) 0.0 else cacheHitTokens.toDouble() / promptTokens

    /**
     * 按 DeepSeek 公开价目估算本次花费（元）：
     * 缓存命中 0.1 / 未命中 1 / 输出 2 元每百万 tokens。价格会变，只作参考。
     */
    fun estimatedCostCny(): Double =
        (cacheMissTokens * 1.0 + cacheHitTokens * 0.1 + completionTokens * 2.0) / 1_000_000.0
}

sealed interface AiCallResult {
    data class Ok(val content: String, val usage: AiUsage? = null) : AiCallResult
    data class Error(val message: String) : AiCallResult
}

/**
 * OpenAI 兼容的 chat/completions 客户端。
 * 不流式、不重试；超时由配置决定；http 只有在设置里显式允许时才放行。
 */
class AiClient {
    suspend fun chat(
        config: AiConfig,
        apiKey: String,
        messages: List<ChatMessage>
    ): AiCallResult = withContext(Dispatchers.IO) {
        val endpoint = endpointFor(config)
            ?: return@withContext AiCallResult.Error("Base URL 不合法：只支持 https；打开「允许明文 HTTP」后才允许 http")
        val payload = JSONObject().apply {
            put("model", config.model)
            put("temperature", config.temperature)
            put("max_tokens", config.maxTokens)
            put("stream", false)
            put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().put("role", message.role).put("content", message.content))
                }
            })
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext AiCallResult.Error(
                        "请求失败（" + response.code + "）：" + body.take(300)
                    )
                }
                val parsed = JSONObject(body)
                val content = parsed
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                if (content.isBlank()) {
                    AiCallResult.Error("模型返回为空")
                } else {
                    AiCallResult.Ok(content.trim(), parseUsage(parsed.optJSONObject("usage")))
                }
            }
        }.getOrElse { error ->
            AiCallResult.Error("网络错误：" + (error.message ?: error.javaClass.simpleName))
        }
    }

    /** usage 解析：缺失或非法一律当 0，不影响主流程。 */
    private fun parseUsage(json: JSONObject?): AiUsage? {
        if (json == null) return null
        val details = json.optJSONObject("prompt_tokens_details")
        val hit = when {
            json.has("prompt_cache_hit_tokens") -> json.optInt("prompt_cache_hit_tokens", 0)
            details != null && details.has("cached_tokens") -> details.optInt("cached_tokens", 0)
            else -> 0
        }
        val miss = if (json.has("prompt_cache_miss_tokens")) {
            json.optInt("prompt_cache_miss_tokens", 0)
        } else {
            (json.optInt("prompt_tokens", 0) - hit).coerceAtLeast(0)
        }
        return AiUsage(
            promptTokens = json.optInt("prompt_tokens", 0),
            completionTokens = json.optInt("completion_tokens", 0),
            cacheHitTokens = hit,
            cacheMissTokens = miss
        )
    }

    private fun endpointFor(config: AiConfig): String? {
        val base = config.baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return null
        val scheme = base.substringBefore("://", "").lowercase()
        if (scheme != "https" && !(scheme == "http" && config.allowCleartextHttp)) return null
        return if (base.endsWith("/chat/completions")) base else base + "/chat/completions"
    }
}