package com.creativeworkbench.writer.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.ai.ActionTuning
import com.creativeworkbench.writer.core.ai.AiCallResult
import com.creativeworkbench.writer.core.ai.AiClient
import com.creativeworkbench.writer.core.ai.AiStreamHooks
import com.creativeworkbench.writer.core.ai.AiUsage
import com.creativeworkbench.writer.core.ai.ChatMessage
import com.creativeworkbench.writer.core.ai.WebSearchClient
import com.creativeworkbench.writer.core.ai.WebSearchHit
import com.creativeworkbench.writer.core.ai.WebSearchOutcome
import com.creativeworkbench.writer.core.data.NoteEntity
import com.creativeworkbench.writer.core.data.NoteKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatTurn(
    val id: String,
    val role: String,
    val text: String,
    val hits: List<WebSearchHit> = emptyList(),
    val streaming: Boolean = false
)

data class AiChatUiState(
    val turns: List<ChatTurn> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
    /** 联网检索开关。 */
    val webEnabled: Boolean = false,
    /** 对话时是否带上当前作品的大纲 / 笔记。 */
    val includeOutline: Boolean = true,
    val includeNotes: Boolean = true,
    val lastSearchLabel: String = "",
    val usage: AiUsage? = null,
    val configured: Boolean = true
)

/**
 * 专门的 AI 对话窗口：多轮聊天 + 可选联网检索 + 结论一键存成笔记。
 *
 * 和编辑器里的「续写/改写」分开：那边是一次性动作、写完就进正文；
 * 这里是「问清楚再写」，结论落到笔记板块，不直接改正文。
 */
class AiChatViewModel(
    application: Application,
    private val workId: String?
) : AndroidViewModel(application) {
    private val container = (application as WorkbenchApp).container
    private val configStore = container.aiConfig
    private val client = AiClient()
    private val searchClient = WebSearchClient()
    private val _state = MutableStateFlow(AiChatUiState())
    val state: StateFlow<AiChatUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        _state.update { it.copy(configured = configStore.isConfigured()) }
    }

    fun setWebEnabled(enabled: Boolean) = _state.update { it.copy(webEnabled = enabled) }

    fun setIncludeOutline(enabled: Boolean) = _state.update { it.copy(includeOutline = enabled) }

    fun setIncludeNotes(enabled: Boolean) = _state.update { it.copy(includeNotes = enabled) }

    fun clear() {
        job?.cancel()
        _state.update { it.copy(turns = emptyList(), sending = false, error = null) }
    }

    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty() || _state.value.sending) return
        if (!configStore.isConfigured()) {
            _state.update { it.copy(error = "还没配置 AI。去「更多 → AI 助手」填 Base URL、模型和密钥。") }
            return
        }
        val userTurn = ChatTurn(UUID.randomUUID().toString(), "user", question)
        val assistantTurn = ChatTurn(UUID.randomUUID().toString(), "assistant", "", streaming = true)
        _state.update {
            it.copy(
                turns = it.turns + userTurn + assistantTurn,
                sending = true,
                error = null,
                lastSearchLabel = ""
            )
        }
        job?.cancel()
        job = viewModelScope.launch {
            val config = configStore.config()
            val apiKey = configStore.apiKey()
            var hits: List<WebSearchHit> = emptyList()
            var searchNote = ""
            // DeepSeek 的 deepseek_web_search 是服务端工具：不用本地搜，模型自己决定上不上网，
            // 搜索与抓取在服务端跑，答案直接带引用。其它服务商才走「本地搜 → 塞上下文」。
            var serverSearch = false
            if (_state.value.webEnabled) {
                val provider = configStore.searchProvider()
                if (provider == com.creativeworkbench.writer.core.ai.SearchProvider.DEEPSEEK) {
                    serverSearch = true
                    searchNote = "【已开启服务端联网：需要最新信息时自己上网查，引用用 [n] 标注】"
                    _state.update { it.copy(lastSearchLabel = "DeepSeek 联网") }
                } else {
                    when (
                        val outcome = searchClient.search(
                            query = question,
                            provider = provider,
                            apiKey = configStore.searchKey(),
                            customUrl = configStore.customSearchUrl()
                        )
                    ) {
                        is WebSearchOutcome.Ok -> {
                            hits = outcome.hits
                            searchNote = searchClient.renderHits(outcome.hits, outcome.provider)
                            _state.update {
                                it.copy(lastSearchLabel = outcome.provider + " · " + outcome.hits.size + " 条")
                            }
                        }
                        is WebSearchOutcome.Error -> {
                            searchNote = "【联网检索失败：" + outcome.message + "】请基于已有知识回答，并说明这一点。"
                            _state.update { it.copy(lastSearchLabel = "联网失败") }
                        }
                    }
                }
            }

            val context = buildContext()
            val system = buildString {
                appendLine("你是中文小说写作助手，和作者讨论情节、人物、设定、写法。")
                appendLine("回答用中文，先说结论，不要客套。需要举例时给可直接抄进正文的句子。")
                appendLine("不要输出 markdown 标题层级之外的装饰符号；不要提「作为一个 AI」。")
                if (context.isNotBlank()) {
                    appendLine()
                    appendLine(context)
                }
            }
            val history = _state.value.turns
                .filter { !it.streaming }
                .dropLast(1)
                .takeLast(12)
                .filter { it.role == "user" || it.role == "assistant" }
                .map { ChatMessage(it.role, it.text) }
            val payload = buildString {
                if (searchNote.isNotBlank()) {
                    appendLine(searchNote)
                    appendLine()
                }
                append(question)
            }
            val messages = mutableListOf(ChatMessage("system", system))
            messages.addAll(history)
            messages.add(ChatMessage("user", payload))

            val tuned = ActionTuning(maxTokens = if (config.deepThinking) 4096 else 3072)
            val builder = StringBuilder()
            var lastEmit = 0L
            val result = client.chat(
                config = config,
                apiKey = apiKey,
                messages = messages,
                tuning = tuned,
                hooks = AiStreamHooks(
                    onDelta = { delta ->
                        builder.append(delta)
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastEmit > 80) {
                            lastEmit = now
                            updateAssistant(assistantTurn.id, builder.toString(), false)
                        }
                    }
                ),
                webSearch = serverSearch
            )
            when (result) {
                is AiCallResult.Error -> {
                    _state.update { it.copy(sending = false, error = result.message) }
                    updateAssistant(assistantTurn.id, builder.toString().ifBlank { "（生成失败）" }, false)
                }
                is AiCallResult.Ok -> {
                    updateAssistant(assistantTurn.id, result.content, false, hits)
                    _state.update { it.copy(sending = false, usage = (it.usage ?: AiUsage()) + result.usage) }
                }
            }
        }
    }

    private fun updateAssistant(id: String, text: String, streaming: Boolean, hits: List<WebSearchHit>? = null) {
        _state.update { state ->
            state.copy(
                turns = state.turns.map { turn ->
                    if (turn.id != id) turn
                    else turn.copy(text = text, streaming = streaming, hits = hits ?: turn.hits)
                }
            )
        }
    }

    /** 对话上下文：当前作品的大纲 + 设定笔记。没选作品就只聊天。 */
    private suspend fun buildContext(): String {
        val id = workId ?: return ""
        return buildString {
            appendLine("【当前作品资料】")
            val work = runCatching { container.chapters.work(id) }.getOrNull()
            appendLine("作品名：" + (work?.title ?: ""))
            if (_state.value.includeOutline) {
                val nodes = runCatching { container.outline.list(id) }.getOrDefault(emptyList())
                if (nodes.isNotEmpty()) {
                    appendLine("【大纲】")
                    nodes.take(40).forEach { node ->
                        appendLine("· " + node.title + if (node.content.isNotBlank()) "：" + node.content else "")
                    }
                }
            }
            if (_state.value.includeNotes) {
                val notes = container.notes.allOnce()
                    .filter { (it.workId == null || it.workId == id) && NoteKind.setting.contains(it.kind) }
                    .take(20)
                if (notes.isNotEmpty()) {
                    appendLine("【设定】")
                    notes.forEach { note: NoteEntity ->
                        appendLine("· " + NoteKind.label(note.kind) + " " + note.title +
                            if (note.content.isNotBlank()) "：" + note.content.take(200) else "")
                    }
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { state ->
            state.copy(
                sending = false,
                turns = state.turns.map { if (it.streaming) it.copy(streaming = false) else it }
            )
        }
    }

    /** 把某条回答存进笔记板块（默认「片段」，勾选参与 AI 上下文）。 */
    fun saveAsNote(turn: ChatTurn, kind: String = NoteKind.FRAGMENT) {
        if (turn.text.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                container.notes.upsert(
                    NoteEntity(
                        id = UUID.randomUUID().toString(),
                        workId = workId,
                        kind = kind,
                        title = turn.text.lineSequence().firstOrNull()?.take(24)?.ifBlank { "AI 对话" } ?: "AI 对话",
                        content = turn.text,
                        aiReference = true,
                        createdAtMs = now,
                        updatedAtMs = now
                    )
                )
                _state.update { it.copy(error = null) }
            }.onFailure { error ->
                _state.update { it.copy(error = "存笔记失败：" + (error.message ?: "存储错误")) }
            }
        }
    }
}

class AiChatViewModelFactory(
    private val application: Application,
    private val workId: String?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AiChatViewModel(application, workId) as T
    }
}
