package com.creativeworkbench.writer.core.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 一条消息。带 toolCalls 的是 assistant 的「我要调用工具」；
 * toolCallId 非空的是 role=tool 的执行结果。其它情况两个字段都空。
 */
data class ChatMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null
)

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
        /** "stop" 正常结束；"length" 撞到 max_tokens 被截断（要续写拼接）；"tool_calls" 要执行工具。 */
        val finishReason: String? = null,
        /** 模型要求调用的工具（可能为空）。 */
        val toolCalls: List<ToolCall> = emptyList()
    ) : AiCallResult {
        val truncated: Boolean get() = finishReason == "length"
    }

    data class Error(val message: String) : AiCallResult
}

/**
 * 流式回调：正文增量与「思考中」增量分开给；
 * onRetry 让界面能显示「网络抖动，正在第 2 次重试…」，而不是干等。
 */
data class AiStreamHooks(
    val onDelta: (String) -> Unit = {},
    val onReasoning: (String) -> Unit = {},
    val onRetry: (attempt: Int, delayMs: Long, reason: String) -> Unit = { _, _, _ -> }
)

/**
 * OpenAI 兼容的 chat/completions 客户端。
 *
 * 针对 DeepSeek 的三处设计（都来自 DSH 源码，见 参考/DSH源码借鉴_20261126.md）：
 * 1) **空闲超时而不是总超时**：DSH 用 streamIdleTimeoutMs（默认 5 分钟）盯「多久没收到字节」，
 *    不设请求总时长。OkHttp 的 callTimeout 是总时长，写长文时会把还在正常输出的流掐掉——
 *    这里只用 readTimeout 做空闲判据。
 * 2) **有界指数退避 + 抖动重试**：429/5xx/超时/网络抖动/空响应都自动重试，
 *    并且尊重 Retry-After；401/402/400 这类不重试。
 * 3) **流内错误要当错误**：SSE 中间可能来一条 error 载荷，当成正常结束会让界面显示半截。
 */
class AiClient {
    suspend fun chat(
        config: AiConfig,
        apiKey: String,
        messages: List<ChatMessage>,
        tuning: ActionTuning = ActionTuning(),
        retryPolicy: RetryPolicy = RetryPolicy(),
        hooks: AiStreamHooks? = null,
        /** 声明 DeepSeek 服务端联网工具：模型自己决定搜不搜，搜索在服务端跑。 */
        webSearch: Boolean = false,
        /** 本地工具（读写项目）。模型自己决定调不调；执行在客户端。 */
        tools: List<AiToolSpec>? = null,
        /**
         * 要模型输出 JSON 时打开：DeepSeek 的 response_format=json_object 能显著减少
         * 代码围栏和多余解释（客户端仍要能容错，见 JsonSalvage）。只对 DeepSeek 发，
         * 通用兼容服务的支持参差不齐，发了可能 400。
         */
        jsonMode: Boolean = false
    ): AiCallResult = withContext(Dispatchers.IO) {
        val endpoint = endpointFor(config)
            ?: return@withContext AiCallResult.Error("Base URL 不合法：只支持 https；打开「允许明文 HTTP」后才允许 http")
        val params = config.profile.request(tuning, config.deepThinking)
        val stream = hooks != null
        val payload = buildPayload(config, tuning, messages, stream, webSearch, tools, jsonMode)

        val client = OkHttpClient.Builder()
            .connectTimeout(20L, TimeUnit.SECONDS)
            // 空闲判据：DSH 的 streamIdleTimeoutMs = 300_000。故意不设 callTimeout。
            .readTimeout(IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(60L, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        var attempt = 0
        var lastMessage = "请求失败"
        while (true) {
            attempt++
            val call = client.newCall(request)
            val cancelHandle = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                ?.invokeOnCompletion { call.cancel() }
            var emitted = 0
            val outcome: Attempt = try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        Attempt.Fail(
                            message = describeHttpError(response.code, body),
                            retryable = AiFailure.isRetryableStatus(response.code),
                            retryAfter = AiFailure.retryAfterMs(response.header("Retry-After"))
                        )
                    } else if (stream) {
                        readStream(response, hooks!!) { emitted += it }
                    } else {
                        readOnce(response)
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                Attempt.Transport(error)
            } finally {
                cancelHandle?.dispose()
            }

            when (outcome) {
                is Attempt.Ok -> return@withContext outcome.result
                is Attempt.Fail -> {
                    lastMessage = outcome.message
                    // 已经吐了字就不能重试：重试会把同一段再吐一遍
                    if (!outcome.retryable || emitted > 0 || attempt > retryPolicy.maxRetries) {
                        return@withContext AiCallResult.Error(outcome.message)
                    }
                    val wait = outcome.retryAfter ?: retryPolicy.delayMs(attempt)
                    hooks?.onRetry(attempt, wait, outcome.message)
                    delay(wait)
                }
                is Attempt.Transport -> {
                    if (!AiFailure.isRetryable(outcome.error) || emitted > 0 || attempt > retryPolicy.maxRetries) {
                        return@withContext AiCallResult.Error(
                            "网络错误：" + (outcome.error.message ?: outcome.error.javaClass.simpleName)
                        )
                    }
                    val wait = retryPolicy.delayMs(attempt)
                    hooks?.onRetry(attempt, wait, "网络抖动")
                    delay(wait)
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        AiCallResult.Error(lastMessage)
    }

    private sealed interface Attempt {
        data class Ok(val result: AiCallResult.Ok) : Attempt
        data class Fail(val message: String, val retryable: Boolean, val retryAfter: Long?) : Attempt
        data class Transport(val error: Throwable) : Attempt
    }

    private fun readOnce(response: okhttp3.Response): Attempt {
        val body = response.body?.string().orEmpty()
        val parsed = runCatching { JSONObject(body) }.getOrNull()
            ?: return Attempt.Fail("返回内容不是合法 JSON（前 120 字）：" + body.take(120), false, null)
        parsed.optJSONObject("error")?.let { error ->
            return Attempt.Fail(error.optString("message").ifBlank { "服务端返回错误" }, true, null)
        }
        val choice = parsed.optJSONArray("choices")?.optJSONObject(0)
        val message = choice?.optJSONObject("message")
        val content = message?.optString("content").orEmpty()
        val finish = choice?.optString("finish_reason")?.takeIf { it.isNotEmpty() }
        val toolCalls = parseToolCalls(message?.optJSONArray("tool_calls"))
        if (content.isBlank() && toolCalls.isEmpty()) {
            return if (finish == "length") {
                Attempt.Fail("输出被截断：这次思考把额度用完了。重试一次，或先关掉「深度思考」。", false, null)
            } else {
                // DSH 把「空响应」当成可重试的 EMPTY_RESPONSE：服务端偶发返回空并不代表请求错了
                Attempt.Fail("模型返回为空", true, null)
            }
        }
        return Attempt.Ok(
            AiCallResult.Ok(content.trim(), parseUsage(parsed.optJSONObject("usage")), finish, toolCalls)
        )
    }

    /** SSE：逐行读 data: {...}，把 delta.content 增量吐出去；中途的 error 事件当失败。 */
    private fun readStream(
        response: okhttp3.Response,
        hooks: AiStreamHooks,
        onEmitted: (Int) -> Unit
    ): Attempt {
        val source = response.body?.source()
            ?: return Attempt.Fail("流式响应为空", true, null)
        val builder = StringBuilder()
        var finish: String? = null
        var usage: AiUsage? = null
        // 流式下 tool_calls 是分片到达的：id/name 可能只在第一片，arguments 一片一片拼。
        val toolAccumulator = LinkedHashMap<Int, MutableToolCall>()
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (line.isBlank() || line.startsWith(":")) continue // 心跳注释
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty()) continue
            if (payload == "[DONE]") break
            val chunk = runCatching { JSONObject(payload) }.getOrNull() ?: continue
            chunk.optJSONObject("error")?.let { error ->
                return Attempt.Fail(
                    error.optString("message").ifBlank { "服务端在流里返回错误" },
                    true,
                    null
                )
            }
            chunk.optJSONObject("usage")?.let { usage = (usage ?: AiUsage()) + parseUsage(it) }
            val choice = chunk.optJSONArray("choices")?.optJSONObject(0) ?: continue
            choice.optString("finish_reason").takeIf { it.isNotEmpty() }?.let { finish = it }
            val delta = choice.optJSONObject("delta") ?: continue
            val reasoning = delta.optString("reasoning_content")
            if (reasoning.isNotEmpty()) hooks.onReasoning(reasoning)
            delta.optJSONArray("tool_calls")?.let { fragments ->
                for (index in 0 until fragments.length()) {
                    val fragment = fragments.optJSONObject(index) ?: continue
                    val slot = fragment.optInt("index", index)
                    val slotData = toolAccumulator.getOrPut(slot) { MutableToolCall() }
                    fragment.optString("id").takeIf { it.isNotEmpty() }?.let { slotData.id = it }
                    val function = fragment.optJSONObject("function") ?: continue
                    function.optString("name").takeIf { it.isNotEmpty() }?.let { slotData.name = it }
                    function.optString("arguments").takeIf { it.isNotEmpty() }?.let {
                        slotData.arguments.append(it)
                    }
                }
            }
            val text = delta.optString("content")
            if (text.isNotEmpty()) {
                builder.append(text)
                onEmitted(text.length)
                hooks.onDelta(text)
            }
        }
        val content = builder.toString()
        val toolCalls = toolAccumulator.values
            .filter { it.name.isNotBlank() }
            .map { ToolCall(id = it.id.ifBlank { "call_" + it.name }, name = it.name, arguments = it.arguments.toString()) }
        if (content.isBlank() && toolCalls.isEmpty()) {
            return if (finish == "length") {
                Attempt.Fail("输出被截断：这次思考把额度用完了。重试一次，或先关掉「深度思考」。", false, null)
            } else {
                Attempt.Fail("模型返回为空", true, null)
            }
        }
        return Attempt.Ok(AiCallResult.Ok(content.trim(), usage, finish, toolCalls))
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
        webSearch: Boolean = false,
        tools: List<AiToolSpec>? = null,
        jsonMode: Boolean = false
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
            // 结构化输出：DeepSeek 只认 "json_object"，且要求提示词里出现 json 字样（我们的提示词都有）。
            if (jsonMode && profile.supportsThinking) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
            // 流式默认不带 usage；DeepSeek 支持 include_usage，带上才能显示缓存命中与花费。
            if (stream && profile.supportsThinking) {
                put("stream_options", JSONObject().put("include_usage", true))
            }
            // 服务端联网：只声明工具，客户端不执行任何东西；模型自己决定搜不搜。
            // 注意 tool_choice 不能点名这两个工具，否则 400。
            // 服务端工具与本地函数工具共用一个 tools 数组——分两次 put 会互相覆盖。
            val serverTools = if (webSearch && profile.supportsThinking) {
                JSONArray().apply {
                    put(JSONObject().put("type", "deepseek_web_search"))
                    put(JSONObject().put("type", "deepseek_read_url"))
                }
            } else {
                null
            }
            val localTools = if (!tools.isNullOrEmpty()) ToolSchema.arrayOf(tools) else null
            if (serverTools != null || localTools != null) {
                put(
                    "tools",
                    JSONArray().apply {
                        serverTools?.let { array ->
                            for (index in 0 until array.length()) put(array.get(index))
                        }
                        localTools?.let { array ->
                            for (index in 0 until array.length()) put(array.get(index))
                        }
                    }
                )
            }
            // 本地函数工具才需要 tool_choice；服务端工具点名会 400。
            if (localTools != null) {
                put("tool_choice", "auto")
            }
            put("messages", JSONArray().apply {
                messages.forEach { message -> put(serializeMessage(message)) }
            })
        }
    }

    /**
     * 一条消息的线上形态：
     * - assistant 带工具调用时 content 可以是空串，tool_calls 必须原样带上，
     *   否则下一轮模型看不到自己请求过什么，会重复调用同一个工具；
     * - role=tool 的消息用 tool_call_id 对上号。
     */
    private fun serializeMessage(message: ChatMessage): JSONObject {
        val json = JSONObject().put("role", message.role)
        if (message.role == "assistant" && message.toolCalls.isNotEmpty()) {
            if (message.content.isNotBlank()) json.put("content", message.content) else json.put("content", JSONObject.NULL)
            json.put("tool_calls", JSONArray().apply {
                message.toolCalls.forEach { call ->
                    put(
                        JSONObject()
                            .put("id", call.id)
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject().put("name", call.name).put("arguments", call.arguments)
                            )
                    )
                }
            })
            return json
        }
        json.put("content", message.content)
        message.toolCallId?.let { json.put("tool_call_id", it) }
        return json
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

    /** 流式 tool_calls 的分片暂存。 */
    private class MutableToolCall {
        var id: String = ""
        var name: String = ""
        val arguments: StringBuilder = StringBuilder()
    }

    /** 非流式响应里的 tool_calls（结构完整，直接解析）。 */
    private fun parseToolCalls(array: JSONArray?): List<ToolCall> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val function = item.optJSONObject("function") ?: return@mapNotNull null
            val name = function.optString("name")
            if (name.isBlank()) return@mapNotNull null
            ToolCall(
                id = item.optString("id").ifBlank { "call_" + name },
                name = name,
                arguments = function.optString("arguments")
            )
        }
    }

    private companion object {
        /** DSH 的 DEFAULT_STREAM_IDLE_TIMEOUT_MS：5 分钟没收到字节才算卡死。 */
        const val IDLE_TIMEOUT_SECONDS = 300L
    }
}
