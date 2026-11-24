package com.creativeworkbench.writer.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.data.Block
import com.creativeworkbench.writer.core.data.OpenChapterResult
import com.creativeworkbench.writer.core.data.SaveResult
import com.creativeworkbench.writer.core.data.UpdateSource
import com.creativeworkbench.writer.core.editor.EditCommand
import com.creativeworkbench.writer.core.editor.EditEngine
import com.creativeworkbench.writer.core.editor.UndoEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class EditorUiState(
    val workTitle: String = "",
    val chapterTitle: String = "",
    val chapterNote: String = "",
    val blocks: List<Block> = emptyList(),
    val revision: Long = 0L,
    val activeBlockId: String? = null,
    val cursorOffset: Int = 0,
    val wordCount: Long = 0L,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val loading: Boolean = true,
    val undoAvailable: Boolean = false,
    val redoAvailable: Boolean = false
)

class EditorViewModel(
    application: Application,
    private val chapterId: String
) : AndroidViewModel(application) {
    private val repository = (application as WorkbenchApp).container.chapters
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<UndoEntry>()
    private val redoStack = ArrayDeque<UndoEntry>()
    private val blockWordCounts = HashMap<String, Long>()
    private var saveJob: Job? = null
    private var maxWaitJob: Job? = null
    private var journalJob: Job? = null
    private var editVersion = 0L
    private var dirtySince = 0L

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val loaded = runCatching { repository.openChapterResult(chapterId) }.getOrElse { error ->
                _state.value = EditorUiState(
                    loading = false,
                    error = "打开章节失败：" + (error.message ?: error.javaClass.simpleName)
                )
                return@launch
            }
            when (loaded) {
                is com.creativeworkbench.writer.core.data.OpenChapterResult.Ok -> {
                    val opened = loaded.opened
                    val total = refreshWordCounts(opened.content.blocks)
                    _state.value = EditorUiState(
                        workTitle = repository.work(opened.chapter.workId)?.title.orEmpty(),
                        chapterTitle = opened.chapter.title,
                        chapterNote = opened.chapter.note,
                        blocks = opened.content.blocks,
                        revision = opened.chapter.revision,
                        wordCount = total,
                        loading = false
                    )
                }
                com.creativeworkbench.writer.core.data.OpenChapterResult.Missing -> {
                    _state.value = EditorUiState(loading = false, error = "章节不存在")
                }
                is com.creativeworkbench.writer.core.data.OpenChapterResult.Corrupt -> {
                    _state.value = EditorUiState(
                        loading = false,
                        error = "章节文件损坏，已停止编辑；请从备份恢复。"
                    )
                }
            }
        }
    }

    /**
     * 回到这个编辑器页面时（例如从作品目录返回），如果磁盘上的章节比内存里的新，
     * 就把内容换成磁盘版本，避免同一个章节在返回栈里留下两份旧内容。
     * 只在没有未保存修改时刷新，正在编辑的内容不会被覆盖。
     */
    fun refreshIfClean() {
        val current = _state.value
        if (current.loading || current.dirty || current.saving) return
        viewModelScope.launch {
            val loaded = runCatching { repository.openChapterResult(chapterId) }.getOrNull() ?: return@launch
            if (loaded !is OpenChapterResult.Ok) return@launch
            val opened = loaded.opened
            if (opened.chapter.revision <= _state.value.revision) return@launch
            val total = refreshWordCounts(opened.content.blocks)
            _state.update {
                it.copy(
                    blocks = opened.content.blocks,
                    revision = opened.chapter.revision,
                    chapterTitle = opened.chapter.title,
                    wordCount = total,
                    activeBlockId = null,
                    cursorOffset = 0,
                    undoAvailable = false,
                    redoAvailable = false
                )
            }
            undoStack.clear()
            redoStack.clear()
        }
    }

    /** 章节备注：不进正文，不计字数。 */
    fun setChapterNote(note: String) {
        _state.update { it.copy(chapterNote = note) }
    }

    fun persistChapterNote() {
        val note = _state.value.chapterNote
        viewModelScope.launch {
            runCatching { repository.updateChapterNote(chapterId, note) }
        }
    }

    /**
     * 插入笔记：按空行拆段，接在当前段之后；整批算一步撤销。
     */
    fun insertNote(content: String) {
        val paragraphs = content.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (paragraphs.isEmpty()) return
        val before = _state.value.blocks
        val anchor = _state.value.activeBlockId
            ?.let { id -> before.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 } ?: (before.size - 1)
        val inserted = paragraphs.map { Block(UUID.randomUUID().toString(), it) }
        val after = before.toMutableList().apply { addAll(anchor + 1, inserted) }
        undoStack.addLast(UndoEntry(before, after))
        redoStack.clear()
        _state.update {
            it.copy(
                blocks = after,
                wordCount = refreshWordCounts(after),
                dirty = true,
                activeBlockId = null,
                undoAvailable = true,
                redoAvailable = false,
                error = null
            )
        }
        editVersion++
        markDirty(after)
    }

    /** AI 续写写入：按空行拆段，接在当前段之后；整批一步撤销。返回插入的 block id。 */
    fun applyAiContinue(text: String): List<String> {
        val snapshotBlocks = _state.value.blocks
        viewModelScope.launch { runCatching { repository.saveSnapshot(chapterId, snapshotBlocks) } }
        val paragraphs = text.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (paragraphs.isEmpty()) return emptyList()
        val before = _state.value.blocks
        val anchor = _state.value.activeBlockId
            ?.let { id -> before.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 } ?: (before.size - 1)
        val inserted = paragraphs.map { Block(UUID.randomUUID().toString(), it) }
        val after = before.toMutableList().apply { addAll(anchor + 1, inserted) }
        undoStack.addLast(UndoEntry(before, after))
        redoStack.clear()
        _state.update {
            it.copy(
                blocks = after,
                wordCount = refreshWordCounts(after),
                dirty = true,
                activeBlockId = null,
                undoAvailable = true,
                redoAvailable = false,
                error = null
            )
        }
        editVersion++
        markDirty(after)
        return inserted.map { it.id }
    }

    /** AI 改写写入：只替换当前段内的选区；一步撤销。 */
    fun applyAiRewrite(blockId: String, start: Int, end: Int, replacement: String): Boolean {
        val before = _state.value.blocks
        viewModelScope.launch { runCatching { repository.saveSnapshot(chapterId, before) } }
        val index = before.indexOfFirst { it.id == blockId }
        if (index < 0) return false
        val text = before[index].text
        if (start < 0 || end > text.length || start > end) return false
        val newText = text.replaceRange(start, end, replacement)
        val after = before.toMutableList().apply { this[index] = before[index].copy(text = newText) }
        undoStack.addLast(UndoEntry(before, after))
        redoStack.clear()
        _state.update {
            it.copy(
                blocks = after,
                wordCount = refreshWordCounts(after),
                dirty = true,
                undoAvailable = true,
                redoAvailable = false,
                activeBlockId = blockId,
                cursorOffset = (start + replacement.length).coerceAtMost(newText.length),
                error = null
            )
        }
        editVersion++
        markDirty(after)
        return true
    }

    fun contentHash(): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        _state.value.blocks.forEach { block ->
            digest.update(block.id.toByteArray())
            digest.update(block.text.toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(32)
    }

    fun activate(blockId: String, cursorOffset: Int = 0) {
        _state.update {
            it.copy(
                activeBlockId = blockId,
                cursorOffset = cursorOffset.coerceAtLeast(0)
            )
        }
    }

    fun deactivate() {
        _state.update { it.copy(activeBlockId = null) }
    }

    fun onTextChanged(blockId: String, text: String) {
        val oldBlock = _state.value.blocks.firstOrNull { it.id == blockId }
        val oldCount = blockWordCounts[blockId] ?: oldBlock?.let { repository.countWords(listOf(it)) } ?: 0L
        val newCount = repository.countWords(listOf(Block(blockId, text)))
        blockWordCounts[blockId] = newCount
        val blocks = _state.value.blocks.map { if (it.id == blockId) it.copy(text = text) else it }
        val total = (_state.value.wordCount - oldCount + newCount).coerceAtLeast(0L)
        _state.update {
            it.copy(
                blocks = blocks,
                wordCount = total,
                dirty = true,
                error = null
            )
        }
        editVersion++
        val updated = blocks.firstOrNull { it.id == blockId }
        if (updated != null) markDirty(listOf(updated))
    }

    fun splitBlock(blockId: String, charOffset: Int) {
        val block = _state.value.blocks.firstOrNull { it.id == blockId } ?: return
        val codePointOffset = block.text.codePointCount(0, charOffset.coerceIn(0, block.text.length))
        val newId = UUID.randomUUID().toString()
        apply(EditCommand.Split(blockId, codePointOffset, newId))
        _state.update { it.copy(activeBlockId = newId, cursorOffset = 0) }
    }

    fun mergeWithPrevious(blockId: String) {
        val index = _state.value.blocks.indexOfFirst { it.id == blockId }
        if (index <= 0) return
        val upper = _state.value.blocks[index - 1]
        val joinOffset = upper.text.length
        apply(EditCommand.Merge(upper.id, blockId))
        _state.update { it.copy(activeBlockId = upper.id, cursorOffset = joinOffset) }
    }

    fun move(blockId: String, delta: Int) {
        val index = _state.value.blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        apply(EditCommand.Move(blockId, index + delta))
    }

    fun remove(blockId: String) {
        val blocks = _state.value.blocks
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val nextActive = blocks.getOrNull(index + 1)?.id ?: blocks.getOrNull(index - 1)?.id
        apply(EditCommand.Remove(blockId))
        _state.update { it.copy(activeBlockId = nextActive, cursorOffset = 0) }
    }

    fun insertAfter(blockId: String) {
        val index = _state.value.blocks.indexOfFirst { it.id == blockId }
        val newBlock = Block(UUID.randomUUID().toString(), "")
        apply(EditCommand.Insert(index + 1, newBlock))
        _state.update { it.copy(activeBlockId = newBlock.id, cursorOffset = 0) }
    }

    fun undo() {
        val entry = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(entry)
        _state.update {
            it.copy(
                blocks = entry.before,
                wordCount = refreshWordCounts(entry.before),
                dirty = true,
                activeBlockId = entry.before.firstOrNull { block -> block.id == it.activeBlockId }?.id
                    ?: entry.before.firstOrNull()?.id,
                cursorOffset = 0,
                undoAvailable = undoStack.isNotEmpty(),
                redoAvailable = true
            )
        }
        editVersion++
        markDirty(entry.before)
    }

    fun redo() {
        val entry = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(entry)
        _state.update {
            it.copy(
                blocks = entry.after,
                wordCount = refreshWordCounts(entry.after),
                dirty = true,
                activeBlockId = entry.after.firstOrNull { block -> block.id == it.activeBlockId }?.id
                    ?: entry.after.firstOrNull()?.id,
                cursorOffset = 0,
                undoAvailable = true,
                redoAvailable = redoStack.isNotEmpty()
            )
        }
        editVersion++
        markDirty(entry.after)
    }

    fun flush() {
        viewModelScope.launch { saveNow() }
    }

    fun flushAndBack(onDone: () -> Unit) {
        viewModelScope.launch {
            saveNow()
            if (_state.value.error == null) onDone()
        }
    }

    private fun refreshWordCounts(blocks: List<Block>): Long {
        blockWordCounts.clear()
        var total = 0L
        blocks.forEach { block ->
            val count = repository.countWords(listOf(block))
            blockWordCounts[block.id] = count
            total += count
        }
        return total
    }

    private fun apply(command: EditCommand) {
        val entry = EditEngine.apply(_state.value.blocks, command) ?: return
        undoStack.addLast(entry)
        redoStack.clear()
        _state.update {
            it.copy(
                blocks = entry.after,
                wordCount = refreshWordCounts(entry.after),
                dirty = true,
                undoAvailable = true,
                redoAvailable = false,
                error = null
            )
        }
        editVersion++
        markDirty(entry.after)
    }

    private fun markDirty(blocks: List<Block>) {
        if (dirtySince == 0L) dirtySince = System.currentTimeMillis()
        journalJob?.cancel()
        journalJob = viewModelScope.launch {
            delay(250)
            repository.appendJournalBlocks(chapterId, blocks)
        }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1500)
            saveNow()
        }
        if (maxWaitJob == null) {
            maxWaitJob = viewModelScope.launch {
                delay(10000)
                if (_state.value.dirty) saveNow()
            }
        }
    }

    private suspend fun saveNow() {
        val snapshot = _state.value
        if (!snapshot.dirty || snapshot.saving) return
        val version = editVersion
        _state.update { it.copy(saving = true) }
        val saved = runCatching {
            repository.saveChapter(chapterId, snapshot.blocks, snapshot.revision, UpdateSource.UserInput)
        }.getOrElse { error ->
            _state.update {
                it.copy(
                    saving = false,
                    error = "保存失败：" + (error.message ?: error.javaClass.simpleName)
                )
            }
            return
        }
        when (saved) {
            is SaveResult.Saved -> {
                val stillDirty = editVersion != version
                _state.update {
                    it.copy(
                        revision = saved.revision,
                        wordCount = saved.wordCount,
                        saving = false,
                        dirty = stillDirty
                    )
                }
                if (!stillDirty) {
                    dirtySince = 0L
                    maxWaitJob = null
                } else {
                    saveJob = viewModelScope.launch {
                        delay(500)
                        saveNow()
                    }
                }
            }
            is SaveResult.Conflict -> {
                _state.update { it.copy(saving = false, error = "内容已变化，保存被拒绝") }
            }
            SaveResult.NotFound -> {
                _state.update { it.copy(saving = false, error = "章节不存在") }
            }
        }
    }
}

class EditorViewModelFactory(
    private val application: Application,
    private val chapterId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return EditorViewModel(application, chapterId) as T
    }
}
