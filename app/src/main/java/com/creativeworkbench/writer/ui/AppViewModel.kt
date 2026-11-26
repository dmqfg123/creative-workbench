package com.creativeworkbench.writer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.data.NoteEntity
import com.creativeworkbench.writer.core.data.NoteKind
import com.creativeworkbench.writer.core.data.OpenedChapter
import com.creativeworkbench.writer.core.data.SaveResult
import com.creativeworkbench.writer.core.data.UpdateSource
import com.creativeworkbench.writer.core.data.WorkEntity
import com.creativeworkbench.writer.core.settings.FontSizePreset
import com.creativeworkbench.writer.core.settings.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppState(
    val currentWorkId: String? = null,
    val currentChapterId: String? = null,
    val apiConfigured: Boolean = false,
    val lastBackupAt: Long? = null,
    val notesEnabled: Boolean = true
)

sealed interface AppEvent {
    data class OpenEditor(val workId: String, val chapterId: String) : AppEvent
    data class Toast(val text: String) : AppEvent
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as WorkbenchApp).container
    private val chapterRepository = container.chapters
    private val settingsRepository = container.settings

    private val noteRepository = container.notes

    val works: StateFlow<List<WorkEntity>> = chapterRepository.observeWorks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = noteRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 书架「速记 → 查看全部」要用：让笔记页打开时预选筛选。 */
    private val _pendingNotesFilter = MutableStateFlow<String?>(null)
    val pendingNotesFilter: StateFlow<String?> = _pendingNotesFilter.asStateFlow()

    val settings = settingsRepository.settings

    private val _appState = MutableStateFlow(AppState(notesEnabled = settings.value.notesEnabled))
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    init {
        assembleModules()
        // 冷启动先把「当前作品」补上：不然在笔记页新建的人物/设定 workId 为空，
        // 永远进不了 AI 上下文（笔记页却写着「每次 AI 调用都会带上」）。
        viewModelScope.launch {
            if (_appState.value.currentWorkId == null) {
                runCatching { chapterRepository.mostRecentWork() }
                    .getOrNull()
                    ?.let { work ->
                        _appState.value = _appState.value.copy(currentWorkId = work.id)
                    }
            }
        }
        // 启动抽检字数索引；漂移才全量重算，正常是空转。
        viewModelScope.launch {
            // 推迟到首帧之后，别和打开章节抢磁盘
            kotlinx.coroutines.delay(2500)
            runCatching { chapterRepository.auditWordCounts() }
        }
    }

    fun assembleModules() {
        val enabled = if (settings.value.notesEnabled) setOf("notes") else emptySet()
        container.moduleRuntime.assemble(enabled)
        _appState.value = _appState.value.copy(notesEnabled = settings.value.notesEnabled)
    }

    fun setNotesEnabled(enabled: Boolean) {
        com.creativeworkbench.writer.core.common.Breadcrumbs.record(
            if (enabled) "打开设定笔记模块" else "关闭设定笔记模块"
        )
        settingsRepository.setNotesEnabled(enabled)
        assembleModules()
    }

    fun setTheme(mode: ThemeMode) = settingsRepository.setTheme(mode)

    fun setFontSize(preset: FontSizePreset) = settingsRepository.setFontSize(preset)

    fun setAllowCleartextHttp(enabled: Boolean) = settingsRepository.setAllowCleartextHttp(enabled)

    fun setFpsOverlay(enabled: Boolean) = settingsRepository.setFpsOverlay(enabled)

    fun createWork(title: String) {
        viewModelScope.launch {
            try {
                val work = chapterRepository.createWork(title)
                _appState.value = _appState.value.copy(
                    currentWorkId = work.id,
                    currentChapterId = null
                )
                val chapter = chapterRepository.chapters(work.id).firstOrNull()
                if (chapter != null) {
                    _appState.value = _appState.value.copy(currentChapterId = chapter.id)
                    _events.emit(AppEvent.OpenEditor(work.id, chapter.id))
                } else {
                    _events.emit(AppEvent.Toast("作品已创建"))
                }
            } catch (error: Exception) {
                _events.emit(AppEvent.Toast("创建失败：" + (error.message ?: "存储错误")))
            }
        }
    }

    fun requestNotesFilter(filter: String?) {
        _pendingNotesFilter.value = filter
    }

    fun consumeNotesFilter(): String? {
        val value = _pendingNotesFilter.value
        _pendingNotesFilter.value = null
        return value
    }

    fun openWork(workId: String) {
        viewModelScope.launch {
            try {
                // 点卡片回到「上次在编辑的那一章」：优先用编辑位置记下的章节；
                // 没有记录（新装/换设备/那章被删）才退回「最近保存过的一章」。
                // 只用 updatedAt 是不够的：读了一会儿没改字，它仍然是上次保存的那章，
                // 用户看到的却是「怎么又跳到别的章去了」。
                val chapters = chapterRepository.chapters(workId)
                val remembered = container.positions.lastChapter(workId)
                    ?.let { id -> chapters.firstOrNull { it.id == id } }
                val chapter = remembered
                    ?: chapters.maxByOrNull { it.updatedAt }
                    ?: chapterRepository.ensureFirstChapter(workId)
                if (chapter == null) {
                    _events.emit(AppEvent.Toast("作品不存在"))
                    return@launch
                }
                chapterRepository.touchWork(workId)
                _appState.value = _appState.value.copy(currentWorkId = workId, currentChapterId = chapter.id)
                _events.emit(AppEvent.OpenEditor(workId, chapter.id))
            } catch (error: Exception) {
                _events.emit(AppEvent.Toast("打开失败：" + (error.message ?: "存储错误")))
            }
        }
    }

    fun setCurrentChapter(workId: String, chapterId: String) {
        _appState.value = _appState.value.copy(currentWorkId = workId, currentChapterId = chapterId)
    }

    fun renameWork(workId: String, title: String) {
        viewModelScope.launch {
            try {
                chapterRepository.renameWork(workId, title.trim())
            } catch (error: Exception) {
                _events.emit(AppEvent.Toast("改名失败：" + (error.message ?: "存储错误")))
            }
        }
    }

    fun setLibrarySort(sort: com.creativeworkbench.writer.core.settings.LibrarySort) {
        settingsRepository.setLibrarySort(sort)
    }

    /** 速记面板保存：备忘落 memo，灵感类落 idea/fragment/dialogue。 */
    fun saveQuickNote(kind: String, title: String?, content: String, workId: String?) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val text = content.trim()
                if (text.isEmpty()) return@launch
                val note = NoteEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    workId = workId,
                    kind = kind,
                    title = title?.trim()?.takeIf { it.isNotEmpty() }
                        ?: text.lineSequence().first().take(20),
                    content = text,
                    createdAtMs = now,
                    updatedAtMs = now
                )
                noteRepository.upsert(note)
                _events.emit(AppEvent.Toast("已保存到笔记"))
            } catch (error: Exception) {
                _events.emit(AppEvent.Toast("保存失败：" + (error.message ?: "存储错误")))
            }
        }
    }

    /** 笔记批量删除（多选 / 按日期整组）。 */
    fun deleteNotes(ids: Collection<String>) {
        val list = ids.toList()
        if (list.isEmpty()) return
        viewModelScope.launch {
            try {
                // 删笔记前先留一份本机备份：批量删除手一滑就是几十条，5 秒撤销不够兜底。
                runCatching { container.backup.writeLocalBackup() }
                noteRepository.deleteAll(list)
                _events.emit(AppEvent.Toast("已删除 " + list.size + " 条笔记"))
            } catch (error: Exception) {
                _events.emit(AppEvent.Toast("删除失败：" + (error.message ?: "存储错误")))
            }
        }
    }

    /** 把 AI 对话/搜索结论直接存成笔记（素材类，默认参与 AI 上下文）。 */
    fun saveAiNote(kind: String, title: String, content: String, workId: String?, aiReference: Boolean = true) {
        if (content.isBlank()) return
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                noteRepository.upsert(
                    NoteEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        workId = workId,
                        kind = kind,
                        title = title.trim().ifEmpty {
                            content.lineSequence().firstOrNull()?.take(20) ?: "AI 结果"
                        },
                        content = content.trim(),
                        aiReference = aiReference,
                        createdAtMs = now,
                        updatedAtMs = now
                    )
                )
                _events.emit(AppEvent.Toast("已存为笔记"))
            } catch (error: Exception) {
                _events.emit(AppEvent.Toast("保存失败：" + (error.message ?: "存储错误")))
            }
        }
    }

    private val pendingDeletes = mutableMapOf<String, kotlinx.coroutines.Job>()

    /** 5 秒撤销窗口放到 ViewModel：切页面/切 Tab 不会把待删任务取消掉。 */
    fun scheduleDeleteWork(workId: String) {
        pendingDeletes[workId]?.cancel()
        pendingDeletes[workId] = viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            pendingDeletes.remove(workId)
            deleteWork(workId)
        }
    }

    fun cancelScheduledDelete(workId: String) {
        pendingDeletes.remove(workId)?.cancel()
    }

    fun deleteWork(workId: String) {
        viewModelScope.launch {
            try {
                // PRD：删除前先自动备份一份，避免误删后只能靠 5 秒撤销。
                runCatching { container.backup.writeLocalBackup() }
                chapterRepository.deleteWork(workId)
                if (_appState.value.currentWorkId == workId) {
                    _appState.value = _appState.value.copy(currentWorkId = null, currentChapterId = null)
                }
                _events.emit(AppEvent.Toast("作品已删除"))
            } catch (error: Exception) {
                _events.emit(AppEvent.Toast("删除失败：" + (error.message ?: "存储错误")))
            }
        }
    }

    suspend fun loadChapter(chapterId: String): OpenedChapter? = chapterRepository.openChapter(chapterId)

    suspend fun saveChapter(chapterId: String, blocks: List<com.creativeworkbench.writer.core.data.Block>, revision: Long): SaveResult =
        chapterRepository.saveChapter(chapterId, blocks, revision, UpdateSource.UserInput)

    suspend fun appendJournal(chapterId: String, blockId: String, text: String) {
        chapterRepository.appendJournal(chapterId, blockId, text)
    }
}
