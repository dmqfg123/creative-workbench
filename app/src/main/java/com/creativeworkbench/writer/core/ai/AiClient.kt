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
     * 按 DeepSeek 官方价目（deepseek-flash，高峰价，2026-11 口径）估算本次花费（元）：
     * 输入命中 $0.006/M、未命中 $0.30/M、输出 $1.20/M，按 1 美元 ≈ 7.1 元折算；
     * 低谷价是高峰的一半，deepseek-v4-pro 约为 3–4 倍。价格会变，只当量级参考。
     */
    fun estimatedCostCny(): Double =
        (cacheMissTokens * 2.13 + cacheHitTokens * 0.043 + completionTokens * 8.52) / 1_000_000.0
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
        val profile = config.profile
        val payload = JSONObject().apply {
            put("model", config.model)
            // 参数全部来自模型表：思考模式不发 temperature，省得服务端忽略还让人以为设了有用。
            profile.temperature?.let { put("temperature", it) }
            put("max_tokens", profile.requestMaxTokens)
            if (profile.supportsThinking) {
                put("thinking", JSONObject().put("type", if (config.deepThinking) "enabled" else "disabled"))
            }
            put("stream", false)
            put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().put("role", message.role).put("content", message.content))
                }
            })
        }

        val timeout = profile.timeoutSeconds.toLong()
        val client = OkHttpClient.Builder()
            .connectTimeout(20L, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .callTimeout(timeout, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        // 取消协程时要真的把 HTTP 调用掐掉：execute() 是阻塞的，光 cancel 协程调用还在跑、还计费。
        val call = client.newCall(request)
        val cancelHandle = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
            ?.invokeOnCompletion { call.cancel() }
        try {
        runCatching {
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext AiCallResult.Error(
                        "请求失败（" + response.code + "）：" + body.take(300)
                    )
                }
                val parsed = JSONObject(body)
                val choice = parsed.optJSONArray("choices")?.optJSONObject(0)
                val content = choice?.optJSONObject("message")?.optString("content").orEmpty()
                if (content.isBlank()) {
                    if (choice?.optString("finish_reason") == "length") {
                        AiCallResult.Error("输出被截断：这次思考把额度用完了。重试一次，或先关掉「深度思考」。")
                    } else {
                        AiCallResult.Error("模型返回为空")
                    }
                } else {
                    AiCallResult.Ok(content.trim(), parseUsage(parsed.optJSONObject("usage")))
                }
            }
        }.getOrElse { error ->
            AiCallResult.Error("网络错误：" + (error.message ?: error.javaClass.simpleName))
        }
        } finally {
            cancelHandle?.dispose()
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