package com.creativeworkbench.writer.ui.outline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.data.ChapterEntity
import com.creativeworkbench.writer.core.data.ForeshadowingEntity
import com.creativeworkbench.writer.core.data.OutlineNodeEntity
import com.creativeworkbench.writer.core.data.OutlineTemplates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OutlineUiState(
    val workTitle: String = "",
    val chapters: List<ChapterEntity> = emptyList(),
    val nodes: List<OutlineNodeEntity> = emptyList(),
    /** 伏笔账本（含已回收，界面按状态分区显示）。 */
    val foreshadowing: List<ForeshadowingEntity> = emptyList(),
    val loading: Boolean = true
) {
    /** 未回收：写正文时要带上，也是大纲页要提示的数字。 */
    val openForeshadow: List<ForeshadowingEntity>
        get() = foreshadowing.filter { it.status != "paid" && it.status != "abandoned" }
}

class OutlineViewModel(
    application: Application,
    private val workId: String
) : AndroidViewModel(application) {
    private val container = (application as WorkbenchApp).container
    private val repository = container.outline
    private val foreshadowingRepository = container.foreshadowing
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
        viewModelScope.launch {
            foreshadowingRepository.observeByWork(workId).collect { items ->
                _state.update { it.copy(foreshadowing = items) }
            }
        }
    }

    fun create(title: String, content: String, chapterId: String?, targetWords: Int?) {
        viewModelScope.launch {
            runCatching { repository.create(workId, chapterId, title, content, targetWords = targetWords) }
        }
    }

    fun update(id: String, title: String, content: String, chapterId: String?, targetWords: Int?) {
        viewModelScope.launch {
            runCatching { repository.update(id, title, content, chapterId, targetWords = targetWords) }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { runCatching { repository.delete(id) } }
    }

    fun move(id: String, delta: Int) {
        viewModelScope.launch { runCatching { repository.move(workId, id, delta) } }
    }

    /** 套模板：按章节顺序铺 beat，不够就落全书级。 */
    fun applyTemplate(template: OutlineTemplates.Template) {
        viewModelScope.launch {
            runCatching {
                repository.applyTemplate(
                    workId = workId,
                    chapterIds = _state.value.chapters.map { it.id },
                    beats = template.beats
                )
            }
        }
    }

    // ---- 伏笔账本 ----

    fun addForeshadow(title: String, detail: String, plantedChapterId: String?, dueChapterId: String?) {
        viewModelScope.launch {
            runCatching { foreshadowingRepository.create(workId, title, detail, plantedChapterId, dueChapterId) }
        }
    }

    fun updateForeshadow(item: ForeshadowingEntity) {
        viewModelScope.launch { runCatching { foreshadowingRepository.update(item) } }
    }

    fun markForeshadowPaid(item: ForeshadowingEntity, paidChapterId: String?) {
        viewModelScope.launch { runCatching { foreshadowingRepository.markPaid(item.id, paidChapterId) } }
    }

    fun deleteForeshadow(id: String) {
        viewModelScope.launch { runCatching { foreshadowingRepository.delete(id) } }
    }

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
