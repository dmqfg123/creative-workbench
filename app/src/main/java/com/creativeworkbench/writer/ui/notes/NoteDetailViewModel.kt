package com.creativeworkbench.writer.ui.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.data.NoteEntity
import com.creativeworkbench.writer.core.data.NoteKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class NoteDetailState(
    val note: NoteEntity? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val savedAt: Long? = null,
    /** 同作品章节：设定「从哪一章开始生效」要用。 */
    val chapters: List<com.creativeworkbench.writer.core.data.ChapterEntity> = emptyList(),
    /** 这条笔记出现在哪几章（点了才扫，见 scanAppearances）。 */
    val appearances: List<Pair<com.creativeworkbench.writer.core.data.ChapterEntity, Int>> = emptyList(),
    val scanning: Boolean = false,
    val scanned: Boolean = false
)

class NoteDetailViewModel(
    application: Application,
    private val noteId: String,
    private val initialWorkId: String?,
    private val initialKind: String
) : AndroidViewModel(application) {
    private val repository = (application as WorkbenchApp).container.notes
    private val _state = MutableStateFlow(NoteDetailState())
    val state: StateFlow<NoteDetailState> = _state.asStateFlow()
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val existing = repository.byId(noteId)
            val note = existing ?: NoteEntity(
                id = noteId,
                workId = if (NoteKind.requiresWork(initialKind)) initialWorkId else initialWorkId,
                kind = initialKind,
                title = "",
                createdAtMs = System.currentTimeMillis(),
                updatedAtMs = System.currentTimeMillis()
            )
            val chapters = runCatching {
                note.workId?.let { (application as WorkbenchApp).container.chapters.chapters(it) }
            }.getOrNull().orEmpty()
            _state.value = NoteDetailState(note = note, loading = false, chapters = chapters)
        }
    }

    /**
     * 反向索引：扫全部章节，找出这条笔记被提到的地方。
     * 对应 novelWriter 的 getBackReferenceList / Obsidian 的 backlinks——
     * 不建持久索引（章节文件是真相，索引容易和文件不一致），改成按需扫，
     * 30 章 × 1 万字也只是一次线性扫描。
     */
    fun scanAppearances() {
        val note = _state.value.note ?: return
        val workId = note.workId ?: return
        if (_state.value.scanning) return
        _state.value = _state.value.copy(scanning = true)
        viewModelScope.launch {
            val chapterRepository = (getApplication() as WorkbenchApp).container.chapters
            val chapters = _state.value.chapters
            val found = mutableListOf<Pair<com.creativeworkbench.writer.core.data.ChapterEntity, Int>>()
            chapters.forEach { chapter ->
                val opened = runCatching { chapterRepository.openChapter(chapter.id) }.getOrNull() ?: return@forEach
                val text = com.creativeworkbench.writer.core.data.MentionIndex.textOf(opened.content.blocks)
                val count = com.creativeworkbench.writer.core.data.MentionIndex.countIn(note, text)
                if (count > 0) found.add(chapter to count)
            }
            _state.value = _state.value.copy(appearances = found, scanning = false, scanned = true)
            workId
        }
    }

    fun edit(transform: (NoteEntity) -> NoteEntity) {
        val current = _state.value.note ?: return
        val updated = transform(current).copy(updatedAtMs = System.currentTimeMillis())
        _state.value = _state.value.copy(note = updated)
        scheduleSave(updated)
    }

    fun setKind(kind: String) = edit { note ->
        note.copy(kind = kind, workId = if (NoteKind.requiresWork(kind)) note.workId ?: initialWorkId else note.workId)
    }

    /** 设定从哪一章开始生效（null = 全篇）。 */
    fun setFromChapter(chapterId: String?) = edit { note ->
        note.copy(fromChapterId = chapterId)
    }

    fun setAiReference(value: Boolean) = edit { note ->
        if (NoteKind.setting.contains(note.kind)) note else note.copy(aiReference = value)
    }

    fun togglePinned() = edit { it.copy(pinned = !it.pinned) }

    private fun scheduleSave(note: NoteEntity) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            saveNow(note)
        }
    }

    /**
     * 立即保存。用 NonCancellable：返回键会立刻销毁这个页面的 ViewModel，
     * 普通协程在 upsert 挂起处会被取消 —— 最后 ≤700ms 的输入就丢了。
     */
    fun flush() {
        val note = _state.value.note ?: return
        saveJob?.cancel()
        viewModelScope.launch(kotlinx.coroutines.NonCancellable) { saveNow(note) }
    }

    private suspend fun saveNow(note: NoteEntity) {
        if (note.title.isBlank() && note.content.isBlank()) return
        _state.value = _state.value.copy(saving = true)
        runCatching { repository.upsert(note) }
            .onSuccess {
                _state.value = _state.value.copy(saving = false, savedAt = System.currentTimeMillis())
            }
            .onFailure {
                _state.value = _state.value.copy(saving = false)
            }
    }

    /** 复制一份到另一部作品：副本独立，改一边不影响另一边。 */
    fun duplicateTo(workId: String, onDone: (Boolean) -> Unit) {
        val note = _state.value.note ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val copy = note.copy(
                id = UUID.randomUUID().toString(),
                workId = workId,
                title = note.title + "（副本）",
                createdAtMs = now,
                updatedAtMs = now
            )
            runCatching { repository.upsert(copy) }
                .onSuccess { onDone(true) }
                .onFailure { onDone(false) }
        }
    }

    fun delete(onDone: () -> Unit) {
        // 先掐掉待落库的防抖任务并把内存里的 note 置空：
        // 否则删完 onDispose 还会 flush() 一次，把刚删的笔记 upsert 回来。
        saveJob?.cancel()
        saveJob = null
        _state.value = _state.value.copy(note = null)
        viewModelScope.launch {
            runCatching { repository.delete(noteId) }
            onDone()
        }
    }
}

class NoteDetailViewModelFactory(
    private val application: Application,
    private val noteId: String,
    private val workId: String?,
    private val kind: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return NoteDetailViewModel(application, noteId, workId, kind) as T
    }
}

fun newNoteId(): String = UUID.randomUUID().toString()
