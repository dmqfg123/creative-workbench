package com.creativeworkbench.writer.ui.directory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.data.ChapterEntity
import com.creativeworkbench.writer.core.data.WorkEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DirectoryViewModel(
    application: Application,
    private val workId: String
) : AndroidViewModel(application) {
    private val repository = (application as WorkbenchApp).container.chapters

    val chapters: StateFlow<List<ChapterEntity>> = repository.observeChapters(workId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _work = MutableStateFlow<WorkEntity?>(null)
    val work: StateFlow<WorkEntity?> = _work.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch { _work.value = repository.work(workId) }
    }

    fun addChapter(title: String) {
        viewModelScope.launch {
            val chapter = repository.createChapter(workId, title)
            if (chapter == null) {
                _messages.emit("创建章节失败")
            } else {
                _messages.emit("已创建「" + chapter.title + "」")
                _work.value = repository.work(workId)
            }
        }
    }

    fun renameChapter(chapterId: String, title: String) {
        viewModelScope.launch {
            repository.renameChapter(chapterId, title)
            _messages.emit("已改名")
        }
    }

    fun deleteChapter(chapterId: String) {
        viewModelScope.launch {
            repository.deleteChapter(chapterId)
            _work.value = repository.work(workId)
            _messages.emit("章节已删除")
        }
    }
}

class DirectoryViewModelFactory(
    private val application: Application,
    private val workId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DirectoryViewModel(application, workId) as T
    }
}
