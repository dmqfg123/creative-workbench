package com.creativeworkbench.writer.ui.outline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.data.ChapterEntity
import com.creativeworkbench.writer.core.data.OutlineNodeEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OutlineUiState(
    val workTitle: String = "",
    val chapters: List<ChapterEntity> = emptyList(),
    val nodes: List<OutlineNodeEntity> = emptyList(),
    val loading: Boolean = true
)

class OutlineViewModel(
    application: Application,
    private val workId: String
) : AndroidViewModel(application) {
    private val container = (application as WorkbenchApp).container
    private val repository = container.outline
    private val chapterRepository = container.chapters
    private val _state = MutableStateFlow(OutlineUiState())
    val state: StateFlow<OutlineUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val work = runCatching { chapterRepository.work(workId) }.getOrNull()
            val chapters = runCatching { chapterRepository.chapters(workId) }.getOrDefault(emptyList())
            _state.update {
                it.copy(workTitle = work?.title.orEmpty(), chapters = chapters, loading = false)
            }
        }
        viewModelScope.launch {
            repository.observeByWork(workId).collect { nodes ->
                _state.update { it.copy(nodes = nodes, loading = false) }
            }
        }
    }

    fun create(title: String, content: String, chapterId: String?) {
        viewModelScope.launch {
            runCatching { repository.create(workId, chapterId, title, content) }
        }
    }

    fun update(id: String, title: String, content: String, chapterId: String?) {
        viewModelScope.launch {
            runCatching { repository.update(id, title, content, chapterId) }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { runCatching { repository.delete(id) } }
    }

    fun move(id: String, delta: Int) {
        viewModelScope.launch { runCatching { repository.move(workId, id, delta) } }
    }

    /** 给「新建下一章」用：把新章的直接绑定到该章。 */
    fun chapterTitle(chapterId: String?): String? =
        _state.value.chapters.firstOrNull { it.id == chapterId }?.title
}

class OutlineViewModelFactory(
    private val application: Application,
    private val workId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return OutlineViewModel(application, workId) as T
    }
}
