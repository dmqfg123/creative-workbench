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

    operator fun plus(other: AiUsage?): AiUsage {
        if (other == null) return this
        return AiUsage(
            promptTokens = promptTokens + other.promptTokens,
            completionTokens = completionTokens + other.completionTokens,
            cacheHitTokens = cacheHitTokens + other.cacheHitTokens,
            cacheMissTokens = cacheMissTokens + other.cacheMissTokens
        )
    }
}

sealed interface AiCallResult {
    data class Ok(
        val content: String,
        val usage: AiUsage? = null,
        /** "stop" 正常结束；"length" 撞到 max_tokens 被截断（要续写拼接）。 */
        val finishReason: String? = null
    ) : AiCallResult {
        val truncated: Boolean get() = finishReason == "length"
    }

    data class Error(val message: String) : AiCallResult
}

/**
 * 流式回调：正文增量与「思考中」增量分开给。
 * 思考模式下前端等十几秒没动静是最伤的体验，至少要能显示「正在思考…」。
 */
data class AiStreamHooks(
    val onDelta: (String) -> Unit = {},
    val onReasoning: (String) -> Unit = {}
)

/**
 * OpenAI 兼容的 chat/completions 客户端。
 * 默认流式（SSE）：首字 1–2 秒就出来，用户不用盯十几秒的「生成中…」。
 * 取消协程会真的 cancel OkHttp Call，连接断掉、不再计费。
 */
class AiClient {
    suspend fun chat(
        config: AiConfig,
        apiKey: String,
        messages: List<ChatMessage>,
        tuning: ActionTuning = ActionTuning(),
        hooks: AiStreamHooks? = null,
        /** 声明 DeepSeek 服务端联网工具：模型自己决定搜不搜，搜索在服务端跑。 */
        webSearch: Boolean = false
    ): AiCallResult = withContext(Dispatchers.IO) {
        val endpoint = endpointFor(config)
            ?: return@withContext AiCallResult.Error("Base URL 不合法：只支持 https；打开「允许明文 HTTP」后才允许 http")
        val params = config.profile.request(tuning, config.deepThinking)
        val stream = hooks != null
        val payload = buildPayload(config, tuning, messages, stream, webSearch)

        val timeout = params.timeoutSeconds.toLong()
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
                    if (!response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        return@withContext AiCallResult.Error(describeHttpError(response.code, body))
                    }
                    if (stream) {
                        readStream(response, hooks!!)
                    } else {
                        readOnce(response)
                    }
                }
            }.getOrElse { error ->
                AiCallResult.Error("网络错误：" + (error.message ?: error.javaClass.simpleName))
            }
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun readOnce(response: okhttp3.Response): AiCallResult {
        val body = response.body?.string().orEmpty()
        val parsed = runCatching { JSONObject(body) }.getOrNull()
            ?: return AiCallResult.Error("返回内容不是合法 JSON（前 120 字）：" + body.take(120))
        val choice = parsed.optJSONArray("choices")?.optJSONObject(0)
        val content = choice?.optJSONObject("message")?.optString("content").orEmpty()
        val finish = choice?.optString("finish_reason")?.takeIf { it.isNotEmpty() }
        if (content.isBlank()) {
            return if (finish == "length") {
                AiCallResult.Error("输出被截断：这次思考把额度用完了。重试一次，或先关掉「深度思考」。")
            } else {
                AiCallResult.Error("模型返回为空")
            }
        }
        return AiCallResult.Ok(content.trim(), parseUsage(parsed.optJSONObject("usage")), finish)
    }

    /** SSE：逐行读 data: {...}，把 delta.content 增量吐出去。 */
    private fun readStream(response: okhttp3.Response, hooks: AiStreamHooks): AiCallResult {
        val source = response.body?.source()
            ?: return AiCallResult.Error("流式响应为空")
        val builder = StringBuilder()
        var finish: String? = null
        var usage: AiUsage? = null
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty()) continue
            if (payload == "[DONE]") break
            val chunk = runCatching { JSONObject(payload) }.getOrNull() ?: continue
            chunk.optJSONObject("usage")?.let { usage = (usage ?: AiUsage()) + parseUsage(it) }
            val choice = chunk.optJSONArray("choices")?.optJSONObject(0) ?: continue
            choice.optString("finish_reason").takeIf { it.isNotEmpty() }?.let { finish = it }
            val delta = choice.optJSONObject("delta") ?: continue
            val reasoning = delta.optString("reasoning_content")
            if (reasoning.isNotEmpty()) hooks.onReasoning(reasoning)
            val text = delta.optString("content")
            if (text.isNotEmpty()) {
                builder.append(text)
                hooks.onDelta(text)
            }
        }
        val content = builder.toString()
        if (content.isBlank()) {
            return if (finish == "length") {
                AiCallResult.Error("输出被截断：这次思考把额度用完了。重试一次，或先关掉「深度思考」。")
            } else {
                AiCallResult.Error("模型返回为空")
            }
        }
        return AiCallResult.Ok(content.trim(), usage, finish)
    }

    /**
     * 请求体：DeepSeek 专用的 thinking 只发给 DeepSeek，通用兼容服务一个字段都不多给，
     * 免得远端把不认识的字段当 400。
     */
    internal fun buildPayload(
        config: AiConfig,
        tuning: ActionTuning,
        messages: List<ChatMessage>,
        stream: Boolean = false,
        webSearch: Boolean = false
    ): JSONObject {
        val profile = config.profile
        val params = profile.request(tuning, config.deepThinking)
        return JSONObject().apply {
            put("model", config.model)
            // 参数：动作策略覆盖模型默认；思考模式不发 temperature，省得服务端忽略还让人以为设了有用。
            params.temperature?.let { put("temperature", it) }
            put("max_tokens", params.maxTokens)
            if (profile.supportsThinking) {
                put("thinking", JSONObject().put("type", if (params.thinking) "enabled" else "disabled"))
            }
            put("stream", stream)
            // 流式默认不带 usage；DeepSeek 支持 include_usage，带上才能显示缓存命中与花费。
            if (stream && profile.supportsThinking) {
                put("stream_options", JSONObject().put("include_usage", true))
            }
            // 服务端联网：只声明工具，客户端不执行任何东西；模型自己决定搜不搜，
            // 搜索和抓取都在 DeepSeek 服务端跑，结果以引用形式回到正文里。
            // 注意 tool_choice 不能点名这两个工具，否则 400。
            if (webSearch && profile.supportsThinking) {
                put(
                    "tools",
                    JSONArray().apply {
                        put(JSONObject().put("type", "deepseek_web_search"))
                        put(JSONObject().put("type", "deepseek_read_url"))
                    }
                )
            }
            put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().put("role", message.role).put("content", message.content))
                }
            })
        }
    }

    /**
     * 把官方错误码翻译成人话（https://api-docs.deepseek.com/quick_start/error_codes）。
     * 原始 body 只保留 160 字：402/429 这类错误 body 很长，塞进面板没人看。
     */
    internal fun describeHttpError(code: Int, body: String): String {
        val detail = body.take(160).replace("\n", " ")
        return when (code) {
            400 -> if (detail.contains("context", ignoreCase = true) ||
                detail.contains("too long", ignoreCase = true) ||
                detail.contains("maximum", ignoreCase = true)
            ) {
                "这次带的上下文超过了模型上限。把设定精简一点，或检查是不是把整本书都带上了。"
            } else {
                "请求格式不对（400）：" + detail
            }
            401 -> "API Key 不对或已失效，去「AI 助手」重新填一次。"
            402 -> "DeepSeek 账户余额不足，充值后再试。"
            403 -> "这个 Key 没有权限（403）：" + detail
            408 -> "请求超时，网络或服务端太慢，重试一次。"
            413 -> "请求体太大（413）：图片或上下文超出限制。"
            422 -> "参数不合法（422）：" + detail
            429 -> "请求太频繁或并发超限（429），等几十秒再试。"
            500, 502, 503, 504 -> "DeepSeek 服务端出错（" + code + "），过一会儿重试。"
            else -> "请求失败（" + code + "）：" + detail
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
