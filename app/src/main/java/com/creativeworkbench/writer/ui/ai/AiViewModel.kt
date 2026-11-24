package com.creativeworkbench.writer.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.ai.AiActionSpec
import com.creativeworkbench.writer.core.ai.AiActions
import com.creativeworkbench.writer.core.ai.AiCallResult
import com.creativeworkbench.writer.core.ai.AiClient
import com.creativeworkbench.writer.core.ai.AiContextBuilder
import com.creativeworkbench.writer.core.ai.AiHistoryRecord
import com.creativeworkbench.writer.core.ai.AiHistoryStore
import com.creativeworkbench.writer.core.ai.AiIssue
import com.creativeworkbench.writer.core.ai.AiIssueParser
import com.creativeworkbench.writer.core.ai.AiOutputSanitizer
import com.creativeworkbench.writer.core.ai.AiUsage
import com.creativeworkbench.writer.core.ai.ChatMessage
import com.creativeworkbench.writer.core.ai.ContextPreview
import com.creativeworkbench.writer.core.ai.PreparedContext
import com.creativeworkbench.writer.core.ai.SelectionInfo
import com.creativeworkbench.writer.core.ai.SettingOverflow
import com.creativeworkbench.writer.core.data.Block
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class AiUiState(
    val action: AiActionSpec? = null,
    val prepared: PreparedContext? = null,
    val overflow: SettingOverflow? = null,
    val resultText: String = "",
    val issues: List<AiIssue> = emptyList(),
    val error: String? = null,
    val generating: Boolean = false,
    val contextSummary: String = "",
    val historyId: String? = null,
    val chapterRevision: Long = 0L,
    val contentHash: String = "",
    val requestCount: Int = 1,
    val availableActions: List<AiActionSpec> = AiActions.builtin,
    /** 最近一次调用的 token 账（含缓存命中）。 */
    val usage: AiUsage? = null
)

class AiViewModel(
    application: Application,
    private val workId: String,
    private val chapterId: String
) : AndroidViewModel(application) {
    private val container = (application as WorkbenchApp).container
    private val configStore = container.aiConfig
    private val client = AiClient()
    private val builder = AiContextBuilder(container.chapters, container.notes)
    private val historyStore = container.aiHistory
    private val _state = MutableStateFlow(
        AiUiState(
            availableActions = AiActions.builtin + container.plugins.list().flatMap { it.actions }
        )
    )
    val state: StateFlow<AiUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var lastRecord: AiHistoryRecord? = null
    private var lastBlocks: List<Block> = emptyList()
    private var lastSelection: SelectionInfo? = null

    fun prepare(
        actionId: String,
        blocks: List<Block>,
        activeBlockId: String?,
        selection: SelectionInfo?,
        allowOverflow: Boolean = false
    ) {
        val action = _state.value.availableActions.firstOrNull { it.id == actionId }
            ?: AiActions.byId(actionId)
            ?: return
        lastBlocks = blocks
        lastSelection = selection
        val config = configStore.config()
        if (!configStore.isConfigured()) {
            _state.value = AiUiState(action = action, error = "还没配置 AI。去「更多 → AI 助手」填 Base URL、模型和密钥。")
            return
        }
        _state.value = _state.value.copy(action = action, overflow = null, error = null, resultText = "", issues = emptyList())
        viewModelScope.launch {
            val promptOverride = if (action.source == com.creativeworkbench.writer.core.ai.ActionSource.Imported) {
                container.plugins.promptFor(action)
            } else {
                null
            }
            val prepared = runCatching {
                builder.prepare(
                    action, config, workId, chapterId, blocks, activeBlockId, selection, allowOverflow,
                    instructionOverride = promptOverride
                )
            }.getOrElse { error ->
                _state.value = _state.value.copy(error = "上下文装配失败：" + (error.message ?: "未知错误"))
                return@launch
            }
            if (prepared.overflow != null) {
                _state.value = _state.value.copy(overflow = prepared.overflow)
            } else {
                _state.value = _state.value.copy(
                    prepared = prepared,
                    contextSummary = prepared.preview?.summary.orEmpty()
                )
            }
        }
    }

    fun generate(chapterRevision: Long, contentHash: String) {
        val action = _state.value.action ?: return
        val prepared = _state.value.prepared ?: return
        val config = configStore.config()
        val apiKey = configStore.apiKey()
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(
                generating = true,
                error = null,
                chapterRevision = chapterRevision,
                contentHash = contentHash
            )
            val messages = listOf(
                ChatMessage("system", prepared.system),
                ChatMessage("user", prepared.user)
            )
            when (val result = client.chat(config, apiKey, messages)) {
                is AiCallResult.Error -> {
                    _state.value = _state.value.copy(generating = false, error = result.message)
                }
                is AiCallResult.Ok -> {
                    val cleaned = AiOutputSanitizer.clean(result.content)
                    val issues = if (action.result.name == "IssueList") AiIssueParser.parse(result.content).orEmpty() else emptyList()
                    val record = AiHistoryRecord(
                        id = UUID.randomUUID().toString(),
                        actionId = action.id,
                        actionName = action.name,
                        workId = workId,
                        chapterId = chapterId,
                        chapterRevision = chapterRevision,
                        contentHash = contentHash,
                        model = config.model,
                        contextSummary = prepared.preview?.summary.orEmpty(),
                        resultKind = action.result.name,
                        resultPreview = cleaned.take(200),
                        resultText = cleaned.take(32 * 1024),
                        createdAtMs = System.currentTimeMillis(),
                        promptTokens = result.usage?.promptTokens ?: 0,
                        completionTokens = result.usage?.completionTokens ?: 0,
                        cacheHitTokens = result.usage?.cacheHitTokens ?: 0
                    )
                    historyStore.add(record)
                    lastRecord = record
                    _state.value = _state.value.copy(
                        generating = false,
                        resultText = cleaned,
                        issues = issues,
                        historyId = record.id,
                        usage = result.usage
                    )
                }
            }
        }
    }

    fun retryWithOverflow(blocks: List<Block>, activeBlockId: String?, selection: SelectionInfo?) {
        val actionId = _state.value.action?.id ?: return
        prepare(actionId, blocks, activeBlockId, selection, allowOverflow = true)
    }

    fun markApplied(undoRecord: AiHistoryRecord?) {
        val id = _state.value.historyId ?: return
        viewModelScope.launch {
            if (undoRecord != null && undoRecord.id == id) {
                // 覆盖写入所需的撤销信息
                historyStore.replace(id, undoRecord)
            }
            historyStore.markApplied(id)
        }
    }

    /** 编辑器写入完成后，把撤销信息补进历史记录。 */
    fun saveUndoInfo(
        mode: String,
        blockId: String?,
        start: Int,
        end: Int,
        before: String,
        after: String,
        insertedIds: List<String>
    ) {
        val record = lastRecord ?: return
        viewModelScope.launch {
            historyStore.replace(
                record.id,
                record.copy(
                    undoMode = mode,
                    undoBlockId = blockId,
                    undoStart = start,
                    undoEnd = end,
                    undoBefore = before,
                    undoAfter = after,
                    undoIds = insertedIds,
                    applied = true
                )
            )
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = _state.value.copy(generating = false)
    }

    fun reset() {
        job?.cancel()
        _state.value = AiUiState()
    }

    fun store(): AiHistoryStore = historyStore
}

class AiViewModelFactory(
    private val application: Application,
    private val workId: String,
    private val chapterId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AiViewModel(application, workId, chapterId) as T
    }
}