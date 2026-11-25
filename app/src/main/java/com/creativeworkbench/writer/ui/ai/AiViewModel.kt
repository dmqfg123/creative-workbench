package com.creativeworkbench.writer.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.ai.ActionTuning
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
import com.creativeworkbench.writer.core.ai.AiStreamHooks
import com.creativeworkbench.writer.core.ai.AiUsage
import com.creativeworkbench.writer.core.ai.BodyRange
import com.creativeworkbench.writer.core.ai.ChatMessage
import com.creativeworkbench.writer.core.ai.ContextOptions
import com.creativeworkbench.writer.core.ai.PreparedContext
import com.creativeworkbench.writer.core.ai.SelectionInfo
import com.creativeworkbench.writer.core.ai.SettingOverflow
import com.creativeworkbench.writer.core.data.Block
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val usage: AiUsage? = null,
    /** 续写目标字数（面板里选）。 */
    val lengthChars: Int = 600,
    /** 用户手动勾掉/勾上的设定 id；null = 自动（命中优先全带）。 */
    val excludedNoteIds: Set<String> = emptySet(),
    /** 上下文来源开关：正文 / 大纲 / 笔记，用户可以只留其中一个。 */
    val includeBody: Boolean = true,
    val includeOutline: Boolean = true,
    val includeNotes: Boolean = true,
    /** 正文带多少：整章或光标前后 N 段。 */
    val bodyRange: BodyRange = BodyRange.Whole,
    /** 已经因为撞 max_tokens 自动续写过几次。 */
    val continuedRounds: Int = 0,
    /** 思考模式的思维链字数（只显示「正在思考…」用，不写进结果）。 */
    val thinkingChars: Int = 0,
    /** 自动重试提示（429/5xx/网络抖动时显示「第 N 次重试…」）。 */
    val retryNotice: String = ""
)

class AiViewModel(
    application: Application,
    private val workId: String,
    private val chapterId: String
) : AndroidViewModel(application) {
    private val container = (application as WorkbenchApp).container
    private val configStore = container.aiConfig
    private val client = AiClient()
    private val builder = AiContextBuilder(container.chapters, container.notes, container.outline)
    private val historyStore = container.aiHistory
    private val _state = MutableStateFlow(
        AiUiState(
            availableActions = AiActions.builtin + container.plugins.list().flatMap { it.actions }
        )
    )
    val state: StateFlow<AiUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var prepareJob: Job? = null
    private var lastRecord: AiHistoryRecord? = null
    private var lastSelection: SelectionInfo? = null

    private fun options(): ContextOptions = ContextOptions(
        lengthChars = _state.value.lengthChars,
        excludedNoteIds = _state.value.excludedNoteIds,
        includeNoteSources = _state.value.includeNotes,
        includeOutline = _state.value.includeOutline,
        includeBody = _state.value.includeBody,
        bodyRange = _state.value.bodyRange
    )

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
        lastSelection = selection
        val config = configStore.config()
        if (!configStore.isConfigured()) {
            _state.value = AiUiState(action = action, error = "还没配置 AI（更多 → AI 助手）。")
            return
        }
        _state.value = _state.value.copy(
            action = action,
            overflow = null,
            error = null,
            resultText = "",
            issues = emptyList(),
            continuedRounds = 0,
            thinkingChars = 0
        )
        // 连点两个动作时，慢的那次回来会把 prepared 覆盖成旧动作的上下文——直接丢掉前一次
        prepareJob?.cancel()
        prepareJob = viewModelScope.launch {
            val promptOverride = if (action.source == com.creativeworkbench.writer.core.ai.ActionSource.Imported) {
                container.plugins.promptFor(action)
            } else {
                null
            }
            val prepared = runCatching {
                builder.prepare(
                    action, config, workId, chapterId, blocks, activeBlockId, selection, allowOverflow,
                    instructionOverride = promptOverride,
                    options = options()
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

    /**
     * 输出上限：不再「按字数卡死」。
     * 旧实现按 lengthChars×0.6×1.4 给 500–2048 tokens，模型多写半句就撞 max_tokens，
     * 结果就是正文被从中间切断（用户看到的「暴力截断」）。现在给 1.8 倍余量；
     * 万一还是撞上，generate() 会自动续写拼接。
     */
    private fun maxTokensFor(action: AiActionSpec, config: com.creativeworkbench.writer.core.ai.AiConfig): Int {
        val wanted = when (action.id) {
            AiActions.CHECK -> action.tuning.maxTokens ?: 6_144
            AiActions.REWRITE -> (((lastSelection?.text?.length ?: 600) * 1.6).toInt() + 512)
            else -> (_state.value.lengthChars * 1.8).toInt()
        }
        return if (config.deepThinking) {
            // 思考模式的思维链和正文共用 max_tokens：给到 1024 的话，光思考就把额度烧完，
            // 正文为空且 finish_reason=length。思考模式下至少给 8K。
            wanted.coerceIn(8_192, 16_384)
        } else {
            wanted.coerceIn(1_024, 8_192)
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
                resultText = "",
                issues = emptyList(),
                chapterRevision = chapterRevision,
                contentHash = contentHash,
                continuedRounds = 0,
                thinkingChars = 0
            )
            val messages = mutableListOf(
                ChatMessage("system", prepared.system),
                ChatMessage("user", prepared.user)
            )
            val tuned = ActionTuning(maxTokens = maxTokensFor(action, config))
            val collected = StringBuilder()
            var usage: AiUsage? = null
            var finish: String? = null
            var round = 0
            var failure: String? = null
            var lastEmit = 0L
            // 最多 3 轮：撞到 max_tokens 就接着写，别把半句话丢给用户
            while (round < MAX_CONTINUE_ROUNDS) {
                val roundText = StringBuilder()
                val result = client.chat(
                    config = config,
                    apiKey = apiKey,
                    messages = messages,
                    tuning = tuned,
                    hooks = AiStreamHooks(
                        onDelta = { delta ->
                            roundText.append(delta)
                            val now = android.os.SystemClock.elapsedRealtime()
                            // 每个 token 都重组一次会让长文本掉帧：限到 ~12fps
                            if (now - lastEmit > 80) {
                                lastEmit = now
                                val snapshot = collected.toString() + roundText.toString()
                                _state.update { it.copy(resultText = snapshot, thinkingChars = 0) }
                            }
                        },
                        onReasoning = { delta ->
                            _state.update { it.copy(thinkingChars = it.thinkingChars + delta.length) }
                        },
                        onRetry = { attempt, waitMs, reason ->
                            _state.update {
                                it.copy(
                                    retryNotice = "第 " + attempt + " 次重试（" + reason + "），等 " +
                                        (waitMs / 1000.0).let { s -> String.format(java.util.Locale.US, "%.1f", s) } + " 秒"
                                )
                            }
                        }
                    )
                )
                when (result) {
                    is AiCallResult.Error -> {
                        failure = result.message
                        break
                    }
                    is AiCallResult.Ok -> {
                        val piece = result.content
                        val merged = mergeContinuation(collected.toString(), piece)
                        collected.setLength(0)
                        collected.append(merged)
                        usage = (usage ?: AiUsage()) + result.usage
                        finish = result.finishReason
                        round++
                        _state.update { it.copy(resultText = collected.toString()) }
                        if (result.finishReason != "length") break
                        if (action.id == AiActions.CHECK) break
                        if (round >= MAX_CONTINUE_ROUNDS) break
                        // 续写拼接：把已生成的当 assistant 前缀，再要求「从断点继续」
                        messages.add(ChatMessage("assistant", collected.toString()))
                        messages.add(ChatMessage("user", CONTINUE_INSTRUCTION))
                        _state.update { it.copy(continuedRounds = round) }
                    }
                }
            }
            if (failure != null && collected.isEmpty()) {
                _state.update { it.copy(generating = false, error = failure) }
                return@launch
            }
            val cleaned = AiOutputSanitizer.clean(collected.toString())
            if (cleaned.isBlank()) {
                _state.update { it.copy(generating = false, error = failure ?: "模型返回为空") }
                return@launch
            }
            val issues = if (action.result.name == "IssueList") {
                AiIssueParser.parse(cleaned).orEmpty()
            } else {
                emptyList()
            }
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
                promptTokens = usage?.promptTokens ?: 0,
                completionTokens = usage?.completionTokens ?: 0,
                cacheHitTokens = usage?.cacheHitTokens ?: 0
            )
            historyStore.add(record)
            lastRecord = record
            _state.update {
                it.copy(
                    generating = false,
                    resultText = cleaned,
                    issues = issues,
                    historyId = record.id,
                    usage = usage,
                    thinkingChars = 0,
                    retryNotice = "",
                    error = failure
                )
            }
        }
    }

    /** 续写拼接去重：模型常把上一轮最后一句重写一遍，重叠超过 24 字就剪掉。 */
    private fun mergeContinuation(previous: String, next: String): String {
        if (previous.isEmpty()) return next
        if (next.isEmpty()) return previous
        val probeMax = minOf(200, previous.length, next.length)
        for (size in probeMax downTo 24) {
            if (previous.regionMatches(previous.length - size, next, 0, size)) {
                return previous + next.substring(size)
            }
        }
        // 没有长重叠就直接接上；短句重复交给提示词「不要重复」兜
        return previous + next
    }

    fun retryWithOverflow(blocks: List<Block>, activeBlockId: String?, selection: SelectionInfo?) {
        val actionId = _state.value.action?.id ?: return
        prepare(actionId, blocks, activeBlockId, selection, allowOverflow = true)
    }

    fun markApplied(undoRecord: AiHistoryRecord?) {
        val id = _state.value.historyId ?: return
        viewModelScope.launch {
            if (undoRecord != null && undoRecord.id == id) {
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

    /** 面板里改字数：只影响下一次装配的提示词与输出上限。 */
    fun setLength(chars: Int) {
        _state.value = _state.value.copy(lengthChars = chars)
    }

    fun setBodyRange(range: BodyRange) {
        _state.value = _state.value.copy(bodyRange = range)
    }

    fun setIncludeBody(value: Boolean) {
        _state.value = _state.value.copy(includeBody = value)
    }

    fun setIncludeOutline(value: Boolean) {
        _state.value = _state.value.copy(includeOutline = value)
    }

    fun setIncludeNotes(value: Boolean) {
        _state.value = _state.value.copy(includeNotes = value)
    }

    /** 用户在结果区直接改字，写回 state 再写入正文。 */
    fun editResult(text: String) {
        _state.value = _state.value.copy(resultText = text)
    }

    fun toggleNote(noteId: String) {
        val excluded = _state.value.excludedNoteIds.toMutableSet()
        if (!excluded.add(noteId)) excluded.remove(noteId)
        _state.value = _state.value.copy(excludedNoteIds = excluded)
    }

    /** 全选 / 全不选引用笔记（笔记多了逐条点太蠢）。 */
    fun setNotesExcluded(noteIds: Collection<String>, excluded: Boolean) {
        val set = _state.value.excludedNoteIds.toMutableSet()
        if (excluded) set.addAll(noteIds) else set.removeAll(noteIds.toSet())
        _state.value = _state.value.copy(excludedNoteIds = set)
    }

    fun cancel() {
        job?.cancel()
        _state.value = _state.value.copy(generating = false)
    }

    fun reset() {
        job?.cancel()
        prepareJob?.cancel()
        // availableActions 不能跟着复位：导入包的动作会从动作栏里消失，直到重建 ViewModel
        _state.value = AiUiState(availableActions = _state.value.availableActions)
    }

    fun store(): AiHistoryStore = historyStore

    private companion object {
        const val MAX_CONTINUE_ROUNDS = 3
        const val CONTINUE_INSTRUCTION =
            "接着上面最后一句继续往下写，不要重复已写过的句子，不要重新开头，不要解释。只输出新增的正文。"
    }
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
