package com.creativeworkbench.writer.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** 一条检索结果：标题 + 链接 + 摘要。摘要直接塞进提示词，链接给用户核对。 */
data class WebSearchHit(val title: String, val url: String, val snippet: String)

sealed interface WebSearchOutcome {
    data class Ok(val hits: List<WebSearchHit>, val provider: String) : WebSearchOutcome
    data class Error(val message: String) : WebSearchOutcome
}

/** 搜索服务商。默认推荐博查（国内直连、中文结果好、有免费额度）。 */
enum class SearchProvider(val id: String, val label: String, val needsKey: Boolean, val note: String) {
    /**
     * 首选：DeepSeek 自己的服务端搜索工具（deepseek_web_search / deepseek_read_url）。
     * 不用额外申请搜索 API，模型自己决定搜不搜，搜索在服务端跑、正文直接带引用。
     * 文档：https://api-docs.deepseek.com/guides/web_tools/
     */
    DEEPSEEK("deepseek", "DeepSeek 内置", false, "不用额外 Key：模型自己决定搜不搜，搜索在服务端跑，答案自带引用"),
    BOCHA("bocha", "博查", true, "国内可直连，中文网页结果好；注册送免费额度"),
    TAVILY("tavily", "Tavily", true, "面向 AI 的检索 API，返回已清洗的摘要"),
    SERPER("serper", "Serper", true, "Google 结果代理，质量高但要能连 google.serper.dev"),
    BRAVE("brave", "Brave", true, "Brave 搜索 API，免费额度 2000 次/月"),
    CUSTOM("custom", "自定义", true, "自己搭的检索服务：POST JSON {query}，返回里找 results/data/items");

    companion object {
        fun byId(id: String): SearchProvider = entries.firstOrNull { it.id == id } ?: DEEPSEEK
    }
}

/**
 * 联网检索。只做「把问题发给搜索 API、取回前 5 条摘要」这一件事：
 * 模型本身不联网，联网是本地先搜、再把结果塞进上下文（和 SillyTavern 的
 * 外挂检索思路一致，也比依赖模型厂商的 function calling 可控）。
 */
class WebSearchClient {
    suspend fun search(
        query: String,
        provider: SearchProvider,
        apiKey: String,
        customUrl: String
    ): WebSearchOutcome = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext WebSearchOutcome.Error("检索词为空")
        // 共享连接池（search 是带 30s 读超时的克隆），不再每次检索都新建 client
        val client = HttpClientProvider.search
        val request = runCatching { buildRequest(provider, query, apiKey, customUrl) }
            .getOrElse { error -> return@withContext WebSearchOutcome.Error(error.message ?: "请求构造失败") }
            ?: return@withContext WebSearchOutcome.Error("这个搜索服务需要先填 API Key（更多 → AI 助手 → 联网搜索）")
        runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext WebSearchOutcome.Error(
                        "搜索失败（" + response.code + "）：" + body.take(160).replace("\n", " ")
                    )
                }
                val hits = parse(provider, body)
                if (hits.isEmpty()) {
                    WebSearchOutcome.Error("搜索没有返回结果（" + provider.label + "）")
                } else {
                    WebSearchOutcome.Ok(hits.take(5), provider.label)
                }
            }
        }.getOrElse { error ->
            WebSearchOutcome.Error("联网失败：" + (error.message ?: error.javaClass.simpleName))
        }
    }

    private fun buildRequest(
        provider: SearchProvider,
        query: String,
        apiKey: String,
        customUrl: String
    ): Request? {
        val json = "application/json".toMediaType()
        fun post(url: String, body: JSONObject, headerName: String? = null): Request {
            val builder = Request.Builder().url(url).post(body.toString().toRequestBody(json))
            if (headerName != null && apiKey.isNotBlank()) builder.addHeader(headerName, apiKey)
            return builder.build()
        }
        return when (provider) {
            // DeepSeek 内置搜索由服务端执行（见 AiClient 的 tools 声明），这里没有本地请求
            SearchProvider.DEEPSEEK -> null
            SearchProvider.BOCHA -> {
                if (apiKey.isBlank()) return null
                post(
                    "https://api.bochaai.com/v1/web-search",
                    JSONObject().put("query", query).put("summary", true).put("count", 5),
                    "Authorization"
                )
            }
            SearchProvider.TAVILY -> {
                if (apiKey.isBlank()) return null
                post(
                    "https://api.tavily.com/search",
                    JSONObject()
                        .put("api_key", apiKey)
                        .put("query", query)
                        .put("max_results", 5)
                        .put("search_depth", "basic")
                )
            }
            SearchProvider.SERPER -> {
                if (apiKey.isBlank()) return null
                post("https://google.serper.dev/search", JSONObject().put("q", query), "X-API-KEY")
            }
            SearchProvider.BRAVE -> {
                if (apiKey.isBlank()) return null
                Request.Builder()
                    .url(
                        "https://api.search.brave.com/res/v1/web/search?q=" +
                            URLEncoder.encode(query, "UTF-8") + "&count=5"
                    )
                    .addHeader("X-Subscription-Token", apiKey)
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()
            }
            SearchProvider.CUSTOM -> {
                val url = customUrl.trim()
                if (url.isEmpty()) return null
                val builder = Request.Builder()
                    .url(url)
                    .post(
                        JSONObject().put("query", query).put("q", query).put("count", 5)
                            .toString().toRequestBody(json)
                    )
                if (apiKey.isNotBlank()) builder.addHeader("Authorization", "Bearer " + apiKey)
                builder.build()
            }
        }
    }

    /** 各家返回结构不一样，逐个兼容；认不出来就用「找第一个带 url+title 的数组」。 */
    private fun parse(provider: SearchProvider, body: String): List<WebSearchHit> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        return when (provider) {
            SearchProvider.DEEPSEEK -> emptyList()
            SearchProvider.BOCHA -> {
                val array = root.optJSONObject("data")
                    ?.optJSONObject("webPages")
                    ?.optJSONArray("value")
                readArray(array) { item ->
                    WebSearchHit(
                        title = item.optString("name"),
                        url = item.optString("url"),
                        snippet = item.optString("summary").ifBlank { item.optString("snippet") }
                    )
                }
            }
            SearchProvider.TAVILY -> readArray(root.optJSONArray("results")) { item ->
                WebSearchHit(
                    title = item.optString("title"),
                    url = item.optString("url"),
                    snippet = item.optString("content")
                )
            }
            SearchProvider.SERPER -> readArray(root.optJSONArray("organic")) { item ->
                WebSearchHit(
                    title = item.optString("title"),
                    url = item.optString("link"),
                    snippet = item.optString("snippet")
                )
            }
            SearchProvider.BRAVE -> readArray(
                root.optJSONObject("web")?.optJSONArray("results")
            ) { item ->
                WebSearchHit(
                    title = item.optString("title"),
                    url = item.optString("url"),
                    snippet = item.optString("description")
                )
            }
            SearchProvider.CUSTOM -> {
                val candidates = listOf("results", "data", "items", "organic", "webPages", "value")
                candidates.firstNotNullOfOrNull { key ->
                    readArray(root.optJSONArray(key)) { item ->
                        WebSearchHit(
                            title = item.optString("title").ifBlank { item.optString("name") },
                            url = item.optString("url").ifBlank { item.optString("link") },
                            snippet = item.optString("content")
                                .ifBlank { item.optString("snippet") }
                                .ifBlank { item.optString("summary") }
                                .ifBlank { item.optString("description") }
                        )
                    }.takeIf { it.isNotEmpty() }
                }.orEmpty()
            }
        }.filter { it.title.isNotBlank() || it.url.isNotBlank() }
    }

    private inline fun readArray(array: JSONArray?, mapper: (JSONObject) -> WebSearchHit): List<WebSearchHit> {
        if (array == null) return emptyList()
        val hits = mutableListOf<WebSearchHit>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            hits.add(mapper(item))
        }
        return hits
    }

    /** 把检索结果渲染成提示词片段。 */
    fun renderHits(hits: List<WebSearchHit>, providerLabel: String): String = buildString {
        appendLine("【联网检索结果（" + providerLabel + "，外部信息，可能过时或不准确）】")
        hits.forEachIndexed { index, hit ->
            appendLine((index + 1).toString() + ". " + hit.title)
            if (hit.snippet.isNotBlank()) appendLine("   " + hit.snippet.replace("\n", " ").take(300))
            if (hit.url.isNotBlank()) appendLine("   来源：" + hit.url)
        }
        appendLine("回答时如果用了上面的信息，请在句末用（来源 1）这样的编号标注。")
    }
}
