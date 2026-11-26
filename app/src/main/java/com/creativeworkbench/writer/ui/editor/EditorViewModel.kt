package com.creativeworkbench.writer.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.common.Breadcrumbs
import com.creativeworkbench.writer.core.data.Block
import com.creativeworkbench.writer.core.data.ChapterEntity
import com.creativeworkbench.writer.core.data.OpenChapterResult
import com.creativeworkbench.writer.core.data.SaveResult
import com.creativeworkbench.writer.core.data.UpdateSource
import com.creativeworkbench.writer.core.ai.SelectionInfo
import com.creativeworkbench.writer.core.editor.EditCommand
import com.creativeworkbench.writer.core.editor.EditEngine
import com.creativeworkbench.writer.core.editor.UndoEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 段首退格合并的结果：上一段的文字。
 * 只带这个前缀，让 EditText 用 insert(0, prefix) 增量改文本——
 * 整段 setText 会把输入法的组合状态一起打掉，长按退格可能就此中断。
 */
data class MergedBlock(val prefix: String) {
    val caret: Int get() = prefix.length
}

data class EditorUiState(
    val workId: String = "",
    val workTitle: String = "",
    val chapterTitle: String = "",
    /** 同一部作品的章节列表：编辑器里直接换章，不用退回目录。 */
    val chapters: List<ChapterEntity> = emptyList(),
    val chapterNote: String = "",
    /** 章节摘要（最近 10 章摘要进 AI 上下文的来源）。 */
    val chapterSummary: String? = null,
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
    val redoAvailable: Boolean = false,
    /** 光标所在的段落下标（单一编辑面里由光标位置推出来）。 */
    val caretBlockIndex: Int = 0,
    /** 光标所在段落的字数（底栏「第 N / M 段 · X 字」用）。 */
    val caretBlockChars: Int = 0,
    /** 撤销/段落操作后要求把光标放到这里（界面消费后清空）。 */
    val pendingCaret: Int? = null
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

    private val openedAtMs = android.os.SystemClock.elapsedRealtime()

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
                    if (com.creativeworkbench.writer.BuildConfig.DEBUG) {
                        android.util.Log.i(
                            "WB_PERF",
                            "load_ms=" + (android.os.SystemClock.elapsedRealtime() - openedAtMs) +
                                " blocks=" + opened.content.blocks.size
                        )
                    }
                    _state.value = EditorUiState(
                        workId = opened.chapter.workId,
                        workTitle = repository.work(opened.chapter.workId)?.title.orEmpty(),
                        chapters = runCatching { repository.chapters(opened.chapter.workId) }
                            .getOrDefault(emptyList()),
                        chapterTitle = opened.chapter.title,
                        chapterNote = opened.chapter.note,
                        chapterSummary = opened.chapter.summary,
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

    /** 写入章节摘要（AI 生成后回填）：这是「最近章节摘要」上下文的数据源。 */
    fun setChapterSummary(summary: String) {
        val trimmed = summary.trim()
        val newSummary = trimmed.takeIf { it.isNotEmpty() }
        _state.update { it.copy(chapterSummary = newSummary) }
        viewModelScope.launch { runCatching { repository.updateSummary(chapterId, newSummary) } }
    }

    fun persistChapterNote() {
        viewModelScope.launch { runCatching { persistNoteNow() } }
    }

    private suspend fun persistNoteNow() {
        runCatching { repository.updateChapterNote(chapterId, _state.value.chapterNote) }
    }

    /**
     * 插入笔记：按空行拆段，接在光标所在段之后；整批算一步撤销。
     */
    fun insertNote(content: String) {
        val paragraphs = content.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        insertParagraphs(paragraphs)
    }

    /**
     * 统一插入规则（PRD §5.3）：一律插在光标所在段之后；
     * 光标停在段中时，先在该处把当前段拆成两段再插入。
     * 拆段 + 插入整批算一步撤销。
     */
    private fun insertParagraphs(paragraphs: List<String>) {
        if (paragraphs.isEmpty()) return
        val before = _state.value.blocks
        val activeId = _state.value.activeBlockId
        val active = before.firstOrNull { it.id == activeId }
        val cursor = _state.value.cursorOffset.coerceIn(0, active?.text?.length ?: 0)
        val inserted = paragraphs.map { Block(UUID.randomUUID().toString(), it) }
        val after = ArrayList<Block>(before.size + paragraphs.size + 1)
        var placed = false
        before.forEach { block ->
            if (!placed && active != null && block.id == active.id) {
                placed = true
                if (cursor in 1 until block.text.length) {
                    after.add(block.copy(text = block.text.substring(0, cursor)))
                    after.addAll(inserted)
                    after.add(Block(UUID.randomUUID().toString(), block.text.substring(cursor)))
                } else {
                    after.add(block)
                    after.addAll(inserted)
                }
            } else {
                after.add(block)
            }
        }
        if (!placed) after.addAll(inserted)
        val result = after.toList()
        pushUndo(before.joinToString("\n") { it.text }, _state.value.cursorOffset, coalesce = false)
        _state.update {
            it.copy(
                blocks = result,
                wordCount = refreshWordCounts(result),
                dirty = true,
                activeBlockId = null,
                cursorOffset = 0,
                undoAvailable = true,
                redoAvailable = false,
                error = null
            )
        }
        editVersion++
        markDirty(result)
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
        pushUndo(before.joinToString("\n") { it.text }, _state.value.cursorOffset, coalesce = false)
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
        // 撤销栈只能有一套：以前压进 undoStack（没人读），撤销时反而吃掉用户刚打的字
        pushUndo(before.joinToString("\n") { it.text }, _state.value.cursorOffset, coalesce = false)
        // 段内偏移换算成全章偏移，否则改完立刻「删段/插段」会作用到别的段落
        val globalCaret = before.take(index).sumOf { it.text.length + 1 } +
            (start + replacement.length).coerceAtMost(newText.length)
        _state.update {
            it.copy(
                blocks = after,
                wordCount = refreshWordCounts(after),
                dirty = true,
                undoAvailable = true,
                redoAvailable = false,
                activeBlockId = blockId,
                cursorOffset = globalCaret,
                pendingCaret = globalCaret,
                error = null
            )
        }
        editVersion++
        markDirty(after)
        return true
    }

    // ================= 单一编辑面（0.17.0） =================

    /**
     * 整篇正文：段落之间用换行连接。
     *
     * 必须带缓存：这个函数在「每次按键 → 状态更新 → Compose 重组 → BodyEditor 对比」这条链上
     * 会被反复调用，原来每次都把整章 join 一遍（5 万字就是每次按键复制 5 万字），
     * 在 60Hz 手机上直接变成掉帧。blocks 是不可变列表，变了就是新实例，用引用相等判断即可。
     */
    private var bodyCacheKey: List<Block>? = null
    private var bodyCacheValue: String = ""

    fun bodyText(): String {
        val blocks = _state.value.blocks
        if (bodyCacheKey !== blocks) {
            bodyCacheKey = blocks
            bodyCacheValue = blocks.joinToString("\n") { it.text }
        }
        return bodyCacheValue
    }

    /**
     * 单一编辑面里的一次文本变化。
     * 只重切段落，不动 EditText：这样输入法的组合态不会被 setText 打断。
     */
    fun onBodyEdited(newText: String) {
        val before = bodyText()
        if (before == newText) return
        pushUndo(before, _state.value.cursorOffset)
        val blocks = reblock(newText, _state.value.blocks)
        recomputeCounts(blocks)
        _state.update {
            it.copy(
                blocks = blocks,
                wordCount = blockWordCounts.values.sum(),
                dirty = true,
                error = null,
                caretBlockIndex = _state.value.caretBlockIndex.coerceIn(0, (blocks.size - 1).coerceAtLeast(0)),
                caretBlockChars = blocks.getOrNull(_state.value.caretBlockIndex)?.text?.length ?: 0,
                undoAvailable = textUndo.isNotEmpty()
            )
        }
        editVersion++
        markDirty(blocks)
    }

    /** 光标移动：更新 AI 用的锚点段落与底栏段号。 */
    fun onCaretMoved(offset: Int) {
        val text = bodyText()
        val caret = offset.coerceIn(0, text.length)
        val index = text.take(caret).count { it == '\n' }
        val blocks = _state.value.blocks
        val block = blocks.getOrNull(index)
        _state.update {
            it.copy(
                cursorOffset = caret,
                caretBlockIndex = index,
                caretBlockChars = block?.text?.length ?: 0,
                activeBlockId = block?.id
            )
        }
    }

    /** 选区 → AI 用的 SelectionInfo（blockId + 段内偏移）。 */
    fun selectionInfo(start: Int, end: Int): SelectionInfo? {
        val text = bodyText()
        if (start < 0 || end > text.length || start >= end) return null
        val index = text.take(start).count { it == '\n' }
        val block = _state.value.blocks.getOrNull(index) ?: return null
        val lineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val localStart = (start - lineStart).coerceIn(0, block.text.length)
        val localEnd = (localStart + (end - start)).coerceIn(0, block.text.length)
        return SelectionInfo(block.id, localStart, localEnd, text.substring(start, end))
    }

    /** 在光标处插入一个段（工具栏「新段」）。 */
    fun insertParagraphAtCaret() {
        val text = bodyText()
        val caret = _state.value.cursorOffset.coerceIn(0, text.length)
        applyTextEdit(text.substring(0, caret) + "\n" + text.substring(caret), caret + 1)
    }

    /** 删除光标所在段（工具栏「删段」）：连同它后面的换行一起删。 */
    fun deleteParagraphAtCaret() {
        val text = bodyText()
        val caret = _state.value.cursorOffset.coerceIn(0, text.length)
        val start = paragraphStart(text, caret)
        val end = paragraphEnd(text, caret)
        val newText = when {
            end < text.length -> text.removeRange(start, end + 1)
            start > 0 -> text.removeRange(start - 1, end)
            else -> ""
        }
        val newCaret = start.coerceIn(0, newText.length)
        applyTextEdit(newText, newCaret)
    }

    /** 上移/下移光标所在段。 */
    fun moveParagraphAtCaret(delta: Int) {
        val text = bodyText()
        val caret = _state.value.cursorOffset.coerceIn(0, text.length)
        val lines = text.split('\n').toMutableList()
        val index = text.take(caret).count { it == '\n' }
        val target = index + delta
        if (index !in lines.indices || target !in lines.indices) return
        val moved = lines.removeAt(index)
        lines.add(target, moved)
        val newText = lines.joinToString("\n")
        val newCaret = lines.take(target).sumOf { it.length + 1 }
        applyTextEdit(newText, newCaret)
    }

    /** 界面消费完 pendingCaret 后清掉，避免重复定位。 */
    fun clearPendingCaret() {
        if (_state.value.pendingCaret != null) _state.update { it.copy(pendingCaret = null) }
    }

    private fun paragraphStart(text: String, caret: Int): Int =
        text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }

    private fun paragraphEnd(text: String, caret: Int): Int =
        text.indexOf('\n', caret).let { if (it < 0) text.length else it }

    /** 统一入口：改全文 + 落段 + 定位光标；推撤销。 */
    private fun applyTextEdit(newText: String, caret: Int) {
        val before = bodyText()
        if (before == newText) return
        pushUndo(before, _state.value.cursorOffset, coalesce = false)
        val blocks = reblock(newText, _state.value.blocks)
        recomputeCounts(blocks)
        val index = newText.take(caret.coerceIn(0, newText.length)).count { it == '\n' }
        _state.update {
            it.copy(
                blocks = blocks,
                wordCount = blockWordCounts.values.sum(),
                dirty = true,
                error = null,
                cursorOffset = caret,
                caretBlockIndex = index.coerceIn(0, (blocks.size - 1).coerceAtLeast(0)),
                caretBlockChars = blocks.getOrNull(index)?.text?.length ?: 0,
                pendingCaret = caret,
                undoAvailable = true,
                redoAvailable = false
            )
        }
        editVersion++
        markDirty(blocks)
    }

    /** 按换行切段：同一位置文本没变就沿用旧 block id（快照/journal 都按 id 记）。 */
    private fun reblock(text: String, old: List<Block>): List<Block> {
        val parts = text.split('\n')
        val used = HashSet<String>()
        return parts.mapIndexed { index, part ->
            val candidate = old.getOrNull(index)
            val id = if (candidate != null && candidate.text == part && used.add(candidate.id)) {
                candidate.id
            } else {
                UUID.randomUUID().toString()
            }
            Block(id, part)
        }
    }

    /** 只重算文本真的变了的那些段，避免每敲一个字就全篇数一遍。 */
    private fun recomputeCounts(blocks: List<Block>) {
        val alive = blocks.map { it.id }.toSet()
        blockWordCounts.keys.retainAll(alive)
        blocks.forEach { block ->
            if (!blockWordCounts.containsKey(block.id)) {
                blockWordCounts[block.id] = repository.countWords(listOf(block))
            }
        }
    }

    fun contentHash(): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        _state.value.blocks.forEach { block ->
            digest.update(block.id.toByteArray())
            digest.update(block.text.toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(32)
    }

    /** 从 AI「检查结果」跳到某一段：把光标定位到段首并让编辑器滚过去。 */
    fun jumpToBlock(blockId: String) {
        val blocks = _state.value.blocks
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val offset = blocks.take(index).sumOf { it.text.length + 1 }
        _state.update {
            it.copy(
                activeBlockId = blockId,
                caretBlockIndex = index,
                cursorOffset = offset,
                pendingCaret = offset
            )
        }
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

    /** 保存失败后手动重试。 */
    fun retrySave() {
        viewModelScope.launch { saveNow() }
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

    /**
     * 段首退格：把上一段并进当前段，**保留当前段的 block id**。
     * 关键在「id 不变」——EditorScreen 里 EditText 是按 block.id 记住的，
     * 换成上一段的 id 会重建 EditText、丢掉输入法连接，长按退格删到段界就断。
     * 返回上一段的文字，让 EditText 当场 insert 到段首，不用等重组。
     */
    fun mergePreviousInto(blockId: String): MergedBlock? {
        val blocks = _state.value.blocks
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index <= 0) return null
        val upper = blocks[index - 1]
        val current = blocks[index]
        val merged = upper.text + current.text
        val caret = upper.text.length
        val after = blocks.toMutableList().apply {
            this[index - 1] = current.copy(text = merged)
            removeAt(index)
        }
        undoStack.addLast(UndoEntry(blocks, after))
        redoStack.clear()
        _state.update {
            it.copy(
                blocks = after,
                wordCount = refreshWordCounts(after),
                dirty = true,
                activeBlockId = blockId,
                cursorOffset = caret,
                undoAvailable = true,
                redoAvailable = false,
                error = null
            )
        }
        editVersion++
        markDirty(after)
        return MergedBlock(upper.text)
    }

    /** 当前章之后的那一章；没有就返回 null（章节面板据此显示「新建下一章」）。 */
    fun nextChapter(): ChapterEntity? {
        val current = _state.value.chapters.firstOrNull { it.id == chapterId } ?: return null
        return _state.value.chapters
            .filter { it.orderIndex > current.orderIndex }
            .minByOrNull { it.orderIndex }
    }

    /**
     * 新建下一章并回调新 id：章节面板的「新建下一章」。
     * 先把当前章落盘，再建章，避免新章抢在当前章保存之前。
     */
    fun createNextChapter(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.appendJournalBlocks(chapterId, _state.value.blocks) }
            withTimeoutOrNull(900) { runCatching { persistNoteNow(); saveNow() } }
            val workId = _state.value.workId.ifBlank {
                _state.value.chapters.firstOrNull { it.id == chapterId }?.workId.orEmpty()
            }
            if (workId.isBlank()) return@launch
            val title = com.creativeworkbench.writer.core.data.ChapterNaming.next(_state.value.chapters.map { it.title })
            val created = runCatching { repository.createChapter(workId, title) }.getOrNull() ?: return@launch
            _state.update {
                it.copy(chapters = runCatching { repository.chapters(workId) }.getOrDefault(it.chapters))
            }
            onCreated(created.id)
        }
    }

    /** 编辑器内换章前先落盘：备注与正文都存住再跳。 */
    fun flushThen(action: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.appendJournalBlocks(chapterId, _state.value.blocks) }
            withTimeoutOrNull(900) {
                runCatching {
                    persistNoteNow()
                    saveNow()
                }
            }
            action()
        }
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
        // 光标就在这一段时走统一插入规则（段中先拆段）；否则插在这段之后。
        if (_state.value.activeBlockId == blockId) {
            insertParagraphs(listOf(""))
            return
        }
        val index = _state.value.blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val newBlock = Block(UUID.randomUUID().toString(), "")
        apply(EditCommand.Insert(index + 1, newBlock))
        _state.update { it.copy(activeBlockId = newBlock.id, cursorOffset = 0) }
    }

    // ---- 文本级撤销（0.17.0）：单一编辑面下，「输入的字也能撤销」 ----

    private data class TextSnapshot(val text: String, val caret: Int)

    private val textUndo = ArrayDeque<TextSnapshot>()
    private val textRedo = ArrayDeque<TextSnapshot>()
    private var lastUndoAt = 0L

    /**
     * 压一步撤销。连续输入按 700ms 合并：否则打十个字要按十次撤销。
     * coalesce=false 用于「段落操作」这类一次就要能整体回退的动作。
     */
    private fun pushUndo(before: String, caret: Int, coalesce: Boolean = true) {
        val now = System.currentTimeMillis()
        if (coalesce && now - lastUndoAt < 700 && textUndo.isNotEmpty()) {
            lastUndoAt = now
            return
        }
        textUndo.addLast(TextSnapshot(before, caret))
        while (textUndo.size > 120) textUndo.removeFirst()
        textRedo.clear()
        lastUndoAt = now
    }

    fun undo() {
        val snapshot = textUndo.removeLastOrNull() ?: return
        textRedo.addLast(TextSnapshot(bodyText(), _state.value.cursorOffset))
        applyTextSnapshot(snapshot)
        _state.update { it.copy(undoAvailable = textUndo.isNotEmpty(), redoAvailable = true) }
    }

    fun redo() {
        val snapshot = textRedo.removeLastOrNull() ?: return
        textUndo.addLast(TextSnapshot(bodyText(), _state.value.cursorOffset))
        applyTextSnapshot(snapshot)
        _state.update { it.copy(undoAvailable = true, redoAvailable = textRedo.isNotEmpty()) }
    }

    private fun applyTextSnapshot(snapshot: TextSnapshot) {
        val blocks = reblock(snapshot.text, _state.value.blocks)
        recomputeCounts(blocks)
        val index = snapshot.text.take(snapshot.caret.coerceIn(0, snapshot.text.length)).count { it == '\n' }
        _state.update {
            it.copy(
                blocks = blocks,
                wordCount = blockWordCounts.values.sum(),
                dirty = true,
                cursorOffset = snapshot.caret.coerceIn(0, snapshot.text.length),
                caretBlockIndex = index.coerceIn(0, (blocks.size - 1).coerceAtLeast(0)),
                caretBlockChars = blocks.getOrNull(index)?.text?.length ?: 0,
                pendingCaret = snapshot.caret.coerceIn(0, snapshot.text.length),
                undoAvailable = textUndo.isNotEmpty(),
                redoAvailable = textRedo.isNotEmpty()
            )
        }
        lastUndoAt = 0L
        editVersion++
        markDirty(blocks)
    }

    fun flush() {
        viewModelScope.launch { saveNow() }
    }

    /**
     * 离开编辑器：先尽量存好，但最多等 900ms，存不完也放人走。
     * 旧实现只在「保存成功」时才回调页面，一旦碰到保存冲突，返回键就成了死键——
     * 正文还有 journal 兜底，不能拿「可能丢掉最后一句」换「页面出不去」。
     */
    fun flushAndBack(onDone: () -> Unit) {
        viewModelScope.launch {
            // 先把正文塞进 journal：即使 saveChapter 被超时打断，重开也能从 journal 回放。
            runCatching { repository.appendJournalBlocks(chapterId, _state.value.blocks) }
            withTimeoutOrNull(900) {
                runCatching {
                    persistNoteNow()
                    saveNow()
                }
            }
            onDone()
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

    private val saveMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun saveNow() {
        // 用锁排队，而不是「正在存就直接返回」：
        // 退出时的 flush 不能因为恰好有个 1.5 秒自动保存在跑，就被静默跳过。
        saveMutex.withLock { saveNowLocked() }
    }

    private suspend fun saveNowLocked(attempt: Int = 0) {
        val snapshot = _state.value
        if (!snapshot.dirty) return
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
                // 磁盘上的 revision 比内存新（另一处写过、或上次导入换过文件）。
                // 单用户写作场景下内存内容就是作者正在写的东西，取磁盘 revision 补存一次；
                // 不这么做，这一章在本次会话里的自动保存会永久失效，只剩 journal 兜底。
                val diskRevision = runCatching { repository.currentRevision(chapterId) }.getOrNull()
                // 0.19.x 以前这里会「拿磁盘 revision 把内存内容再存一遍」——那会把
                // AI 刚追加到正文的段落整段覆盖掉（工具写入用的也是 saveChapter）。
                // 现在：先留一份本章快照，再报错让用户重开这一章，绝不盲写。
                runCatching { repository.saveSnapshot(chapterId, snapshot.blocks) }
                _state.update {
                    it.copy(
                        saving = false,
                        error = "这一章在别处被改过（可能是 AI 写入或恢复）。已为你留了一份本章快照；重开这一章可看到最新内容。"
                    )
                }
                Breadcrumbs.record("保存冲突：磁盘 revision 已变，保留快照并停止自动保存")
                _state.update { it.copy(saving = false, error = "内容已变化，保存被拒绝（重开这一章可恢复）") }
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