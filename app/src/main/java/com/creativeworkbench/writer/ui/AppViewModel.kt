package com.creativeworkbench.writer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.creativeworkbench.writer.WorkbenchApp
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

    val works: StateFlow<List<WorkEntity>> = chapterRepository.observeWorks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings = settingsRepository.settings

    private val _appState = MutableStateFlow(AppState(notesEnabled = settings.value.notesEnabled))
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    init {
        assembleModules()
    }

    fun assembleModules() {
        val enabled = if (settings.value.notesEnabled) setOf("notes") else emptySet()
        container.moduleRuntime.assemble(enabled)
        _appState.value = _appState.value.copy(notesEnabled = settings.value.notesEnabled)
    }

    fun setNotesEnabled(enabled: Boolean) {
        settingsRepository.setNotesEnabled(enabled)
        assembleModules()
    }

    fun setTheme(mode: ThemeMode) = settingsRepository.setTheme(mode)

    fun setFontSize(preset: FontSizePreset) = settingsRepository.setFontSize(preset)

    fun setAllowCleartextHttp(enabled: Boolean) = settingsRepository.setAllowCleartextHttp(enabled)

    fun setFpsOverlay(enabled: Boolean) = settingsRepository.setFpsOverlay(enabled)

    fun createWork(title: String) {
        viewModelScope.launch {
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
        }
    }

    fun openWork(workId: String) {
        viewModelScope.launch {
            val chapter = chapterRepository.ensureFirstChapter(workId)
            if (chapter == null) {
                _events.emit(AppEvent.Toast("作品不存在"))
                return@launch
            }
            chapterRepository.touchWork(workId)
            _appState.value = _appState.value.copy(currentWorkId = workId, currentChapterId = chapter.id)
            _events.emit(AppEvent.OpenEditor(workId, chapter.id))
        }
    }

    fun deleteWork(workId: String) {
        viewModelScope.launch {
            chapterRepository.deleteWork(workId)
            if (_appState.value.currentWorkId == workId) {
                _appState.value = _appState.value.copy(currentWorkId = null, currentChapterId = null)
            }
            _events.emit(AppEvent.Toast("作品已删除"))
        }
    }

    suspend fun loadChapter(chapterId: String): OpenedChapter? = chapterRepository.openChapter(chapterId)

    suspend fun saveChapter(chapterId: String, blocks: List<com.creativeworkbench.writer.core.data.Block>, revision: Long): SaveResult =
        chapterRepository.saveChapter(chapterId, blocks, revision, UpdateSource.UserInput)

    suspend fun appendJournal(chapterId: String, blockId: String, text: String) {
        chapterRepository.appendJournal(chapterId, blockId, text)
    }
}
