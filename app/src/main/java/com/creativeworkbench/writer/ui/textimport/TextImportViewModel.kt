package com.creativeworkbench.writer.ui.textimport

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.ai.AiClient
import com.creativeworkbench.writer.core.ai.AiUsage
import com.creativeworkbench.writer.core.ai.StructuredItem
import com.creativeworkbench.writer.core.ai.TextStructurer
import com.creativeworkbench.writer.core.data.NoteEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class TextImportUiState(
    val text: String = "",
    val running: Boolean = false,
    val progress: String = "",
    val items: List<StructuredItem> = emptyList(),
    val error: String? = null,
    val notice: String = "",
    val usage: AiUsage? = null
) {
    val selectedCount: Int get() = items.count { it.selected }
}

/**
 * 「导入文本」的流程：粘贴/选文件 → 本地分章 → 逐章 AI 抽取 → 勾选 → 写入笔记 / 大纲 / 伏笔。
 * 只到「勾选」为止都不写库：识别结果先给用户看，确认后才落。
 */
class TextImportViewModel(
    application: Application,
    private val workId: String?
) : AndroidViewModel(application) {
    private val container = (application as WorkbenchApp).container
    private val configStore = container.aiConfig
    private val structurer = TextStructurer(AiClient())
    private val _state = MutableStateFlow(TextImportUiState())
    val state: StateFlow<TextImportUiState> = _state.asStateFlow()
    private var job: Job? = null
    /** 导入进行中：双击「导入」会重复落库。 */
    private var importing = false

    fun setError(message: String) {
        _state.update { it.copy(error = message) }
    }

    /** 关掉弹层就停掉识别：否则后台继续按章调 API（继续扣费）。 */
    fun cancel() {
        job?.cancel()
        _state.update { it.copy(running = false, progress = "") }
    }

    fun setText(text: String) {
        _state.update { it.copy(text = text, error = null, items = emptyList(), notice = "") }
    }

    fun run() {
        val text = _state.value.text.trim()
        if (text.length < 20) {
            _state.update { it.copy(error = "文字太短，至少贴一段正文") }
            return
        }
        if (!configStore.isConfigured()) {
            _state.update { it.copy(error = "还没配置 AI（更多 → AI 助手）。") }
            return
        }
        if (_state.value.running) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(running = true, error = null, notice = "", items = emptyList()) }
            val outcome = structurer.extract(
                text = text,
                config = configStore.config(),
                apiKey = configStore.apiKey()
            ) { done, total, label ->
                _state.update {
                    it.copy(progress = "正在识别 " + (done + 1).coerceAtMost(total) + "/" + total + "：" + label)
                }
            }
            _state.update {
                it.copy(
                    running = false,
                    progress = "",
                    items = outcome.items,
                    error = outcome.error,
                    usage = outcome.usage,
                    notice = when {
                    outcome.items.isEmpty() -> ""
                    outcome.truncated -> "识别出 " + outcome.chapterCount + " 章（只处理前 " +
                        com.creativeworkbench.writer.core.ai.ChapterSplitter.MAX_CHAPTERS +
                        " 章，后面的没抽）· " + outcome.items.size + " 条，勾选后导入"
                    else -> "识别出 " + outcome.chapterCount + " 章 · " + outcome.items.size + " 条，勾选后导入"
                }
                )
            }
        }
    }

    fun toggle(id: String) {
        _state.update { state ->
            state.copy(items = state.items.map { if (it.id == id) it.copy(selected = !it.selected) else it })
        }
    }

    fun toggleBucket(bucket: String, selected: Boolean) {
        _state.update { state ->
            state.copy(
                items = state.items.map {
                    if (it.bucket == bucket) it.copy(selected = selected) else it
                }
            )
        }
    }

    fun importSelected() {
        val chosen = _state.value.items.filter { it.selected }
        if (chosen.isEmpty()) return
        if (importing) return
        importing = true
        if (workId == null) {
            _state.update { it.copy(error = "还没选作品：大纲和伏笔需要挂在一部作品上") }
            return
        }
        viewModelScope.launch {
            var noteCount = 0
            var outlineCount = 0
            var foreshadowCount = 0
            val failures = mutableListOf<String>()
            val chapterList = runCatching { container.chapters.chapters(workId) }.getOrDefault(emptyList())
            chosen.forEach { item ->
                runCatching {
                    when (item.bucket) {
                        StructuredItem.NOTE -> {
                            val now = System.currentTimeMillis()
                            container.notes.upsert(
                                NoteEntity(
                                    id = UUID.randomUUID().toString(),
                                    workId = workId,
                                    kind = item.kind,
                                    title = item.title,
                                    content = item.content,
                                    aiReference = true,
                                    createdAtMs = now,
                                    updatedAtMs = now
                                )
                            )
                            noteCount++
                        }
                        StructuredItem.OUTLINE -> {
                            container.outline.create(
                                workId = workId,
                                chapterId = chapterList.getOrNull((item.chapterIndex ?: 1) - 1)?.id,
                                title = item.title,
                                content = item.content
                            )
                            outlineCount++
                        }
                        StructuredItem.FORESHADOW -> {
                            container.foreshadowing.create(
                                workId = workId,
                                title = item.title,
                                detail = item.content,
                                plantedChapterId = chapterList.getOrNull((item.chapterIndex ?: 1) - 1)?.id,
                                dueChapterId = null
                            )
                            foreshadowCount++
                        }
                    }
                }.onFailure { failures.add(item.title) }
            }
            importing = false
            val importedIds = chosen.map { it.id }.toSet()
            _state.update { state ->
                state.copy(
                    items = state.items.filterNot { it.id in importedIds },
                    notice = "已导入：笔记 " + noteCount + " 条、大纲 " + outlineCount +
                        " 条、伏笔 " + foreshadowCount + " 条" +
                        (if (failures.isEmpty()) "" else "；失败 " + failures.size + " 条"),
                    error = null
                )
            }
        }
    }
}

class TextImportViewModelFactory(
    private val application: Application,
    private val workId: String?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TextImportViewModel(application, workId) as T
    }
}
