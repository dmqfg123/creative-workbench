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

sealed interface AiCallResult {
    data class Ok(val content: String) : AiCallResult
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
                val content = JSONObject(body)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                if (content.isBlank()) {
                    AiCallResult.Error("模型返回为空")
                } else {
                    AiCallResult.Ok(content.trim())
                }
            }
        }.getOrElse { error ->
            AiCallResult.Error("网络错误：" + (error.message ?: error.javaClass.simpleName))
        }
    }

    private fun endpointFor(config: AiConfig): String? {
        val base = config.baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return null
        val scheme = base.substringBefore("://", "").lowercase()
        if (scheme != "https" && !(scheme == "http" && config.allowCleartextHttp)) return null
        return if (base.endsWith("/chat/completions")) base else base + "/chat/completions"
    }
}
