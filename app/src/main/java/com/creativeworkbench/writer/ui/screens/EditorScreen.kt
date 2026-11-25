package com.creativeworkbench.writer.ui.screens

import android.app.Application
import android.content.Context
import android.graphics.Color as AndroidColor
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.creativeworkbench.writer.R
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.data.Block
import com.creativeworkbench.writer.core.data.ChapterEntity
import com.creativeworkbench.writer.core.data.NoteEntity
import com.creativeworkbench.writer.core.data.NoteKind
import com.creativeworkbench.writer.core.settings.FontSizePreset
import com.creativeworkbench.writer.core.ai.AiActions
import com.creativeworkbench.writer.core.ai.SelectionInfo
import com.creativeworkbench.writer.core.ai.WriteTarget
import com.creativeworkbench.writer.ui.ai.AiPanel
import com.creativeworkbench.writer.ui.ai.AiViewModel
import com.creativeworkbench.writer.ui.ai.AiViewModelFactory
import com.creativeworkbench.writer.ui.components.ActionSheet
import com.creativeworkbench.writer.ui.components.SheetAction
import com.creativeworkbench.writer.ui.design.GhostButton
import com.creativeworkbench.writer.ui.design.IconAction
import com.creativeworkbench.writer.ui.design.PillButton
import com.creativeworkbench.writer.ui.design.Tag
import com.creativeworkbench.writer.ui.editor.EditorViewModel
import com.creativeworkbench.writer.ui.editor.EditorViewModelFactory
import com.creativeworkbench.writer.ui.editor.FocusScrollTarget
import com.creativeworkbench.writer.ui.editor.MergedBlock
import com.creativeworkbench.writer.ui.editor.VisibleBlockBounds
import com.creativeworkbench.writer.ui.editor.focusedBlockScrollTarget
import com.creativeworkbench.writer.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    workId: String,
    chapterId: String,
    notes: List<NoteEntity>,
    notesEnabled: Boolean,
    onBack: () -> Unit,
    onSwitchChapter: (String) -> Unit,
    onOpenDirectory: () -> Unit,
    onOpenOutline: () -> Unit = {},
    onOpenNote: (String) -> Unit = {}
) {
    val application = LocalContext.current.applicationContext as Application
    val app = application as WorkbenchApp
    val context = LocalContext.current
    val settings by app.container.settings.settings.collectAsStateWithLifecycle()
    val bodySize = when (settings.fontSize) {
        FontSizePreset.Small -> 15f
        FontSizePreset.Standard -> 17f
        FontSizePreset.Large -> 19f
    }
    val lineHeight = when (settings.fontSize) {
        FontSizePreset.Small -> 27f
        FontSizePreset.Standard -> 31f
        FontSizePreset.Large -> 35f
    }
    val viewModel: EditorViewModel = viewModel(
        key = "editor-" + chapterId,
        factory = EditorViewModelFactory(application, chapterId)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val aiViewModel: AiViewModel = viewModel(
        key = "ai-" + chapterId,
        factory = AiViewModelFactory(application, workId, chapterId)
    )
    val aiState by aiViewModel.state.collectAsStateWithLifecycle()
    var aiOpen by remember { mutableStateOf(false) }
    var pendingSelection by remember { mutableStateOf<SelectionInfo?>(null) }
    val composedAtMs = remember { android.os.SystemClock.elapsedRealtime() }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val bottomMarginPx = with(density) { 12.dp.roundToPx() }
    val positionPrefs = remember {
        context.getSharedPreferences("editor_positions", Context.MODE_PRIVATE)
    }
    var notesOpen by remember { mutableStateOf(false) }
    // 删段落后的撤销条：内联在工具条上方，不再用 Snackbar（会压在键盘/工具条上）
    var undoBarVisible by remember { mutableStateOf(false) }
    var undoBarText by remember { mutableStateOf("") }
    var chapterSheetOpen by remember { mutableStateOf(false) }
    var activeBlockMaxHeight by remember { mutableStateOf(320.dp) }

    val imeBottomPx = WindowInsets.ime.getBottom(density)
    var noteExpanded by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var restoredPosition by remember { mutableStateOf(false) }
    // 保存失败时的兜底出口：立刻把全量备份导到用户选的位置
    val backupExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = app.container.backup.export(uri, gzip = true)
                snackbar.showSnackbar(if (result.isSuccess) "已导出备份" else "导出失败")
            }
        }
    }

    // 继续上次位置：记住当前章的第一可见段和偏移。
    LaunchedEffect(state.loading, state.blocks.size) {
        if (!state.loading && !restoredPosition && state.blocks.isNotEmpty()) {
            val saved = positionPrefs.getString(chapterId, null)
            if (saved != null) {
                val parts = saved.split('|')
                val blockId = parts.firstOrNull()
                val offset = parts.getOrNull(1)?.toIntOrNull() ?: 0
                // 列表第 0 项是章节头，正文段要从 item 下标 +1 算
                val index = state.blocks.indexOfFirst { it.id == blockId }
                if (index >= 0) listState.scrollToItem(index + 1, offset)
            }
            restoredPosition = true
        }
    }
    LaunchedEffect(state.loading) {
        if (!state.loading) {
            android.util.Log.i(
                "WB_PERF",
                "content_ms=" + (android.os.SystemClock.elapsedRealtime() - composedAtMs)
            )
        }
    }
    LaunchedEffect(state.chapterNote) {
        if (!state.loading) {
            delay(900)
            viewModel.persistChapterNote()
        }
    }
    LaunchedEffect(chapterId) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (index, offset) ->
                delay(600)
                val blockId = state.blocks.getOrNull(index - 1)?.id ?: return@collectLatest
                positionPrefs.edit().putString(chapterId, blockId + "|" + offset).apply()
            }
    }

    // 只在切换段落 / 键盘弹出或收起时对齐几次（等键盘动画落定），**不监听滚动**：
    // 之前盯着视口/可见项变化，用户一上下滑就被拽回光标位置，根本没法看全文。
    // 0.13：把「键盘是否弹出」也作为触发条件——之前只在切段时对齐一次，
    // 搜狗键盘弹得慢（>600ms）时视口还没缩完就对齐完了，光标又被挡在键盘后面，
    // 用户看到的就是「自动顶到键盘上面这个功能时灵时不灵」。
    val imePx = WindowInsets.ime.getBottom(density)
    val imeVisible = imePx > 0
    // 用「实际 insets 高度」而不是布尔值做 key：26 键/9 键切换、候选条展开都会改变高度，
    // 只按「弹出/收起」触发的话这些情况不会重新对齐。
    LaunchedEffect(state.activeBlockId, imePx) {
        val activeId = state.activeBlockId ?: return@LaunchedEffect
        val lazyIndex = state.blocks.indexOfFirst { block -> block.id == activeId } + 1
        if (lazyIndex <= 0) return@LaunchedEffect
        val waits = if (imeVisible) listOf(120L, 320L, 560L, 820L) else listOf(120L, 260L)
        waits.forEach { wait ->
            delay(wait)
            alignFocusedBlock(listState, lazyIndex, bottomMarginPx)
        }
    }
    // 撤销条 4 秒后自己消失（不阻塞任何操作）
    LaunchedEffect(undoBarVisible) {
        if (undoBarVisible) {
            delay(4000)
            undoBarVisible = false
        }
    }

    BackHandler {
        keyboard?.hide()
        viewModel.flushAndBack(onBack)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshIfClean()
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activeBlockText = state.blocks.firstOrNull { it.id == state.activeBlockId }?.text

    Box(Modifier.fillMaxSize()) {
    if (settings.fpsOverlay) {
        FpsOverlay(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 6.dp)
        )
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = {
                    keyboard?.hide()
                    viewModel.flushAndBack(onBack)
                }
            )
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        keyboard?.hide()
                        chapterSheetOpen = true
                    }
                    .padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
            ) {
                Text(
                    state.workTitle.ifBlank { "作品" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.chapterTitle.ifBlank { "未命名章节" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "换章",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            IconButton(
                onClick = {
                    pendingSelection = null
                    keyboard?.hide()
                    aiOpen = true
                    aiViewModel.prepare(
                        AiActions.CONTINUE,
                        state.blocks,
                        state.activeBlockId,
                        null
                    )
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_sparkle),
                    contentDescription = "AI 助手",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (notesEnabled) {
                IconButton(onClick = { notesOpen = true }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_notes),
                        contentDescription = "笔记抽屉",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("正在打开章节…", style = MaterialTheme.typography.bodyMedium)
            }
            return@Column
        }
        if (state.error != null && state.blocks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    state.error ?: "章节无法打开",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
            return@Column
        }

        Column(Modifier.fillMaxSize().imePadding()) {
          BoxWithConstraints(Modifier.weight(1f)) {
            // 正在写的这一段最多占视口 55%：段落再长也在框内自己滚，
            // 光标永远在键盘上方，不用手指去拉页面。
            val maxBlockHeight = maxHeight * 0.55f
            LaunchedEffect(maxHeight) { activeBlockMaxHeight = maxBlockHeight }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    EditorHeader(
                        chapterTitle = state.chapterTitle,
                        wordCount = state.wordCount,
                        saving = state.saving,
                        dirty = state.dirty,
                        error = state.error,
                        empty = state.blocks.all { it.text.isBlank() },
                        note = state.chapterNote,
                        noteExpanded = noteExpanded,
                        onToggleNote = {
                            if (noteExpanded) viewModel.persistChapterNote()
                            noteExpanded = !noteExpanded
                        },
                        onNoteChange = {
                            viewModel.setChapterNote(it)
                        },
                        warning = when {
                            state.wordCount >= 50_000 -> "单章已超过 5 万字，建议拆分为多章"
                            (activeBlockText?.length ?: 0) > 2000 -> "本段较长，建议按情节分段"
                            else -> null
                        },
                        onStartWriting = {
                            state.blocks.firstOrNull()?.let { block ->
                                viewModel.activate(block.id, 0)
                            }
                        },
                        onRetrySave = { viewModel.retrySave() },
                        onExportBackup = { backupExport.launch("雨刮模拟器-紧急备份.json.gz") }
                    )
                }
                itemsIndexed(state.blocks, key = { _, block -> block.id }) { index, block ->
                    if (state.activeBlockId == block.id) {
                        Column(Modifier.fillMaxWidth()) {
                            ActiveBlockEditor(
                                block = block,
                                cursorOffset = state.cursorOffset,
                                onTextChange = { viewModel.onTextChanged(block.id, it) },
                                onSplitAt = { viewModel.splitBlock(block.id, it) },
                                onMergePrevious = { viewModel.mergePreviousInto(block.id) },
                                onAiRewrite = { selected, start, end ->
                                    val blockId = state.activeBlockId ?: block.id
                                    pendingSelection = SelectionInfo(blockId, start, end, selected)
                                    keyboard?.hide()
                                    aiOpen = true
                                    aiViewModel.prepare(
                                        AiActions.REWRITE,
                                        state.blocks,
                                        blockId,
                                        SelectionInfo(blockId, start, end, selected)
                                    )
                                },
                                textSizeSp = bodySize,
                                lineHeightSp = lineHeight,
                                maxHeight = activeBlockMaxHeight,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        InactiveBlock(
                            block = block,
                            textSizeSp = bodySize,
                            lineHeightSp = lineHeight,
                            onActivate = { offset -> viewModel.activate(block.id, offset) }
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = { viewModel.insertAfter(state.blocks.lastOrNull()?.id.orEmpty()) }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("继续写下一段")
                        }
                    }
                }
            }
          }

            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    animationSpec = tween(Motion.Medium, easing = Motion.EmphasizedDecelerate)
                ) { full -> full } + fadeIn(tween(Motion.Fast)),
                exit = slideOutVertically(
                    animationSpec = tween(Motion.Fast, easing = Motion.EmphasizedAccelerate)
                ) { full -> full } + fadeOut(tween(Motion.Fast))
            ) {
                Column {
                    // 提示条放在底栏内部：既不会被键盘挡住，也不会压在正文上（Snackbar 自己是独立窗口，
                    // 放在外层 Box 里时它只按窗口底部对齐，键盘一弹就钻到键盘后面）。
                    SnackbarHost(hostState = snackbar, modifier = Modifier.fillMaxWidth())
                    // 正在编辑哪一段，说清楚：之前点段落没有任何选中反馈，
                    // 用户不知道「上移/删除」作用在哪一段，只能靠猜。
                    val activeIndex = state.blocks.indexOfFirst { it.id == state.activeBlockId }
                    if (activeIndex >= 0) {
                        Text(
                            "第 " + (activeIndex + 1) + " / " + state.blocks.size + " 段 · " +
                                state.blocks[activeIndex].text.length + " 字",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, top = 2.dp, bottom = 2.dp)
                        )
                    }
                    // 删除段落的撤销条：内联在工具条上方，不弹窗、不盖字、不被键盘挡。
                    androidx.compose.animation.AnimatedVisibility(visible = undoBarVisible) {
                        UndoBar(
                            text = undoBarText,
                            onUndo = {
                                undoBarVisible = false
                                viewModel.undo()
                            },
                            onDismiss = { undoBarVisible = false }
                        )
                    }
                    EditorToolbar(
                        undoAvailable = state.undoAvailable,
                        redoAvailable = state.redoAvailable,
                        hasActiveBlock = state.activeBlockId != null,
                        canMoveUp = state.blocks.indexOfFirst { it.id == state.activeBlockId } > 0,
                        canMoveDown = state.blocks.indexOfFirst { it.id == state.activeBlockId }
                            .let { it in 0 until state.blocks.lastIndex },
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                        onMoveUp = { state.activeBlockId?.let { viewModel.move(it, -1) } },
                        onMoveDown = { state.activeBlockId?.let { viewModel.move(it, 1) } },
                        onAdd = {
                            val id = state.activeBlockId ?: state.blocks.lastOrNull()?.id
                            if (id != null) viewModel.insertAfter(id)
                        },
                        onDelete = {
                            val activeId = state.activeBlockId
                            if (activeId != null) {
                                viewModel.remove(activeId)
                                undoBarText = "已删除这一段"
                                undoBarVisible = true
                            }
                        }
                    )
                }
            }
        }
    }



    // 换章：这是**唯一**入口（点标题/工具条「章节」）。
    // 0.12 还有「右上角三点」「侧滑侧栏」两条路，三条路互相打架、
    // 右侧滑入又和系统返回手势抢边缘手势，全部砍掉，只留底部弹层。
    if (chapterSheetOpen) {
        ChapterSheet(
            workTitle = state.workTitle,
            chapters = state.chapters,
            currentChapterId = chapterId,
            nextChapter = viewModel.nextChapter(),
            mentioned = com.creativeworkbench.writer.core.data.MentionIndex.scan(
                notes.filter { it.workId == null || it.workId == workId },
                com.creativeworkbench.writer.core.data.MentionIndex.textOf(state.blocks)
            ),
            onOpenNote = { noteId -> onOpenNote(noteId) },
            onPick = { target ->
                chapterSheetOpen = false
                keyboard?.hide()
                if (target == chapterId) return@ChapterSheet
                viewModel.flushThen { onSwitchChapter(target) }
            },
            onOpenDirectory = {
                chapterSheetOpen = false
                keyboard?.hide()
                viewModel.flushAndBack(onOpenDirectory)
            },
            onOpenOutline = {
                chapterSheetOpen = false
                keyboard?.hide()
                viewModel.flushAndBack(onOpenOutline)
            },
            onCreateNext = {
                chapterSheetOpen = false
                keyboard?.hide()
                viewModel.createNextChapter { newId -> onSwitchChapter(newId) }
            },
            onDismiss = { chapterSheetOpen = false }
        )
    }
    }


    if (notesOpen) {
        NotesDrawer(
            notes = notes.filter { it.workId == null || it.workId == workId || NoteKind.setting.contains(it.kind) },
            onInsert = { note ->
                keyboard?.hide()
                viewModel.insertNote(note.content)
                notesOpen = false
                scope.launch {
                    val result = snackbar.showSnackbar(
                        message = "已插入笔记，可撤销",
                        actionLabel = "撤销",
                        duration = androidx.compose.material3.SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo()
                }
            },
            onDismiss = { notesOpen = false }
        )
    }

    // 可以手动勾选/取消引用的设定与素材（人物/设定 + 勾过 AI 参考的素材）
    val aiContextNotes = notes.filter {
        (it.workId == null || it.workId == workId) && (NoteKind.setting.contains(it.kind) ||
            (NoteKind.material.contains(it.kind) && it.aiReference))
    }
    val reprepareAi: () -> Unit = {
        val actionId = aiState.action?.id ?: AiActions.CONTINUE
        aiViewModel.prepare(
            actionId,
            state.blocks,
            state.activeBlockId,
            if (actionId == AiActions.REWRITE) pendingSelection else null
        )
    }

    if (aiOpen) {
        // 只看内容哈希：revision 是「写过几次」的计数器，每次自动保存都会 +1，
        // 拿它判「正文改过没有」会把一个字没动的情况也判成过期，写入按钮白白变灰。
        val aiStale = aiState.resultText.isNotBlank() &&
            aiState.contentHash != viewModel.contentHash()
        AiPanel(
            state = aiState,
            stale = aiStale,
            selection = pendingSelection,
            hasContent = state.blocks.any { it.text.isNotBlank() },
            hasActiveBlock = state.activeBlockId != null,
            onAction = { actionId ->
                val replacesSelection =
                    actionId == AiActions.REWRITE || actionId == AiActions.EXPAND
                var selection = if (replacesSelection) pendingSelection else null
                // 没选中文字时，改写/扩写默认作用于当前段：面板里会明确显示「原文」。
                if (replacesSelection && selection == null) {
                    val active = state.blocks.firstOrNull { it.id == state.activeBlockId }
                    if (active != null && active.text.isNotBlank()) {
                        selection = SelectionInfo(active.id, 0, active.text.length, active.text)
                        pendingSelection = selection
                    }
                }
                aiViewModel.prepare(actionId, state.blocks, state.activeBlockId, selection)
            },
            onGenerate = { aiViewModel.generate(state.revision, viewModel.contentHash()) },
            onOverflowRetry = {
                val actionId = aiState.action?.id ?: AiActions.CONTINUE
                val selection = if (actionId == AiActions.REWRITE || actionId == AiActions.EXPAND) {
                    pendingSelection
                } else {
                    null
                }
                aiViewModel.prepare(actionId, state.blocks, state.activeBlockId, selection, allowOverflow = true)
            },
            onCancel = { aiViewModel.cancel() },
            onDismiss = {
                aiOpen = false
                aiViewModel.reset()
            },
            onWrite = { text ->
                val action = aiState.action
                var writeFailed = false
                if (action?.write == WriteTarget.BodyReplaceSelection) {
                    val selection = pendingSelection
                    if (selection == null) {
                        writeFailed = true
                    } else {
                        val ok = viewModel.applyAiRewrite(
                            selection.blockId,
                            selection.start,
                            selection.end,
                            text
                        )
                        if (ok) {
                            aiViewModel.saveUndoInfo(
                                mode = "replace",
                                blockId = selection.blockId,
                                start = selection.start,
                                end = selection.start + text.length,
                                before = selection.text,
                                after = text,
                                insertedIds = emptyList()
                            )
                        } else {
                            writeFailed = true
                        }
                    }
                } else {
                    val ids = viewModel.applyAiContinue(text)
                    aiViewModel.saveUndoInfo(
                        mode = "insert",
                        blockId = null,
                        start = -1,
                        end = -1,
                        before = "",
                        after = text,
                        insertedIds = ids
                    )
                }
                if (writeFailed) {
                    // 写不进去就别说「已写入」：面板留着，结果还能复制走。
                    scope.launch { snackbar.showSnackbar("写入失败：这一段已经变了，请重新生成或手动复制") }
                    return@AiPanel
                }
                aiOpen = false
                aiViewModel.reset()
                pendingSelection = null
                scope.launch {
                    val result = snackbar.showSnackbar(
                        message = "已写入，不满意可以撤销",
                        actionLabel = "撤销",
                        duration = androidx.compose.material3.SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo()
                }
            },
            onJumpToBlock = { blockId ->
                val index = blockId.removePrefix("b").toIntOrNull()
                if (index != null) {
                    // 列表第 0 项是章节头
                    aiOpen = false
                    viewModel.activate(state.blocks.getOrNull(index)?.id ?: "", 0)
                }
            },
            // 改字数/范围/来源/勾选笔记后立刻按新配置重新装配上下文（预览马上跟着变）
            onLength = { len ->
                aiViewModel.setLength(len)
                reprepareAi()
            },
            onResultEdit = { aiViewModel.editResult(it) },
            contextNotes = aiContextNotes,
            excludedNoteIds = aiState.excludedNoteIds,
            onToggleNote = { id ->
                aiViewModel.toggleNote(id)
                reprepareAi()
            },
            onBodyRange = { range ->
                aiViewModel.setBodyRange(range)
                reprepareAi()
            },
            onToggleBody = { on ->
                aiViewModel.setIncludeBody(on)
                reprepareAi()
            },
            onToggleOutline = { on ->
                aiViewModel.setIncludeOutline(on)
                reprepareAi()
            },
            onToggleNotes = { on ->
                aiViewModel.setIncludeNotes(on)
                reprepareAi()
            },
            onToggleFollowOutline = { on ->
                aiViewModel.setFollowOutline(on)
                reprepareAi()
            },
            onToggleAllNotes = { includeAll ->
                aiViewModel.setNotesExcluded(aiContextNotes.map { it.id }, excluded = !includeAll)
                reprepareAi()
            },
            onOpenOutline = {
                aiOpen = false
                aiViewModel.reset()
                viewModel.flushAndBack(onOpenOutline)
            },
            onSaveAsOutlineNode = {
                val text = aiState.resultText.trim()
                if (text.isNotEmpty()) {
                    val title = text.lineSequence().firstOrNull()?.take(24)?.ifBlank { state.chapterTitle }
                        ?: state.chapterTitle
                    aiOpen = false
                    aiViewModel.reset()
                    scope.launch {
                        runCatching {
                            app.container.outline.create(
                                workId = workId,
                                chapterId = chapterId,
                                title = title,
                                content = text
                            )
                        }
                        snackbar.showSnackbar("已存成大纲节点")
                    }
                }
            },
            onWriteSummary = { text ->
                viewModel.setChapterSummary(text)
                aiOpen = false
                aiViewModel.reset()
                scope.launch { snackbar.showSnackbar("已写入本章摘要") }
            }
        )
    }
}

/**
 * 把正在编辑的那一段对齐到可视区。
 * 大跳用 scrollToItem 直接定位段首（delta 累加 + 动画会越滚越远），小位移才做动画。
 */
private suspend fun alignFocusedBlock(
    listState: androidx.compose.foundation.lazy.LazyListState,
    lazyIndex: Int,
    bottomMarginPx: Int
) {
    val info = listState.layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return
    val available = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
    val target = focusedBlockScrollTarget(
        focusedIndex = lazyIndex,
        visibleItems = info.visibleItemsInfo.map { item ->
            VisibleBlockBounds(item.index, item.offset, item.size)
        },
        viewportStartOffset = info.viewportStartOffset,
        viewportEndOffset = info.viewportEndOffset,
        bottomMarginPx = bottomMarginPx
    )
    when (target) {
        is FocusScrollTarget.By ->
            if (kotlin.math.abs(target.deltaPx) > available / 3) {
                listState.scrollToItem(lazyIndex, 0)
            } else {
                listState.animateScrollBy(target.deltaPx.toFloat())
            }
        is FocusScrollTarget.ToItem -> listState.scrollToItem(target.index, target.scrollOffset)
        null -> Unit
    }
}

/** 调试用帧率显示：每 500ms 统计一次实际渲染帧数，默认关。 */
@Composable
private fun FpsOverlay(modifier: Modifier = Modifier) {
    var fps by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        var frames = 0
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                frames++
                val elapsed = now - last
                if (elapsed >= 500_000_000L) {
                    fps = (frames * 1_000_000_000L / elapsed).toInt()
                    frames = 0
                    last = now
                }
            }
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.75f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            fps.toString() + " fps",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EditorHeader(
    chapterTitle: String,
    wordCount: Long,
    saving: Boolean,
    dirty: Boolean,
    error: String?,
    empty: Boolean,
    note: String,
    noteExpanded: Boolean,
    onToggleNote: () -> Unit,
    onNoteChange: (String) -> Unit,
    warning: String?,
    onStartWriting: () -> Unit,
    onRetrySave: () -> Unit,
    onExportBackup: () -> Unit
) {
    val animatedWordCount by animateIntAsState(
        targetValue = wordCount.toInt(),
        animationSpec = tween(420, easing = Motion.Standard),
        label = "wordCount"
    )
    val saveLabel = when {
        error != null -> error
        saving -> "保存中…"
        dirty -> "未保存"
        else -> "已保存"
    }
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(
            chapterTitle.ifBlank { "未命名章节" },
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                animatedWordCount.toString() + " 字",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "  ·  ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Crossfade(
                targetState = saveLabel,
                animationSpec = tween(Motion.Fast),
                label = "saveState"
            ) { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (noteExpanded) "收起备注" else "备注",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleNote)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        AnimatedVisibility(visible = noteExpanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(Modifier.fillMaxWidth().height(96.dp).padding(10.dp)) {
                        if (note.isEmpty()) {
                            Text(
                                "本章备注，不计字数。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = note,
                            onValueChange = onNoteChange,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRetrySave) { Text("重试保存") }
                TextButton(onClick = onExportBackup) { Text("立即导出备份") }
            }
        }
        if (warning != null) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    warning,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        if (empty) {
            Spacer(Modifier.height(26.dp))
            Text(
                "点这里开始写，回车分段。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onStartWriting)
                    .padding(vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun InactiveBlock(
    block: Block,
    textSizeSp: Float,
    lineHeightSp: Float,
    onActivate: (Int) -> Unit
) {
    var layout by remember(block.id) { mutableStateOf<TextLayoutResult?>(null) }
    val interaction = remember { MutableInteractionSource() }
    val text = block.text.ifBlank { " " }
    Text(
        text = text,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .indication(interaction, androidx.compose.material3.ripple())
            .pointerInput(block.id) {
                detectTapGestures(
                    onPress = { offset: androidx.compose.ui.geometry.Offset ->
                        val press = PressInteraction.Press(offset)
                        interaction.emit(press)
                        val released = tryAwaitRelease()
                        interaction.emit(
                            if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press)
                        )
                    }
                ) { position ->
                    onActivate(layout?.getOffsetForPosition(position) ?: 0)
                }
            }
            .padding(vertical = 6.dp),
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = textSizeSp.sp, lineHeight = lineHeightSp.sp),
        color = if (block.text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.onSurface
    )
}

/**
 * 删段落的撤销条。0.12 用的是 Snackbar，压在键盘上沿的工具条和正文之间，
 * 既盖字又和工具条重叠；这里改成工具条正上方的一条常驻内联条，4 秒后自己消失。
 */
@Composable
private fun UndoBar(
    text: String,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onUndo) {
                Text("撤销", color = MaterialTheme.colorScheme.inversePrimary)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 键盘上方的工具条：六个等宽触控块，贴住输入法上沿。
 *
 * 0.13 的改动：
 * - 删掉「收起」按钮：工具条本来就跟着键盘走，键盘一收它自己就没意义了，
 *   那个按钮唯一的作用是让功能消失，用户完全猜不到它要干什么；
 * - 删掉「换章」「笔记」：换章入口收敛到标题（唯一入口），笔记在顶栏；
 * - 加进「上移 / 下移 / 删除」：段落操作条不再插在正文里把正文往上挤，
 *   段落的增删移和文字操作统一在这一条上，正文区域永远是纯文字。
 */
@Composable
private fun EditorToolbar(
    undoAvailable: Boolean,
    redoAvailable: Boolean,
    hasActiveBlock: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            // 不再 navigationBarsPadding：外层 Scaffold 的 innerPadding 已经把导航栏算进去了，
            // 这里再加一次就是双计（工具条会浮在导航栏上方一大截，和键盘一起把正文挤没）。
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarAction(painterResource(R.drawable.ic_undo), "撤销", undoAvailable, onUndo, Modifier.weight(1f))
            ToolbarAction(painterResource(R.drawable.ic_redo), "重做", redoAvailable, onRedo, Modifier.weight(1f))
            ToolbarAction(Icons.Default.KeyboardArrowUp, "上移", canMoveUp, onMoveUp, Modifier.weight(1f))
            ToolbarAction(Icons.Default.KeyboardArrowDown, "下移", canMoveDown, onMoveDown, Modifier.weight(1f))
            ToolbarAction(Icons.Default.Add, "新段", hasActiveBlock, onAdd, Modifier.weight(1f))
            ToolbarAction(Icons.Default.Delete, "删段", hasActiveBlock, onDelete, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ToolbarAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ToolbarActionContent(label, enabled, onClick, modifier) { tint ->
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun ToolbarAction(
    icon: Painter,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ToolbarActionContent(label, enabled, onClick, modifier) { tint ->
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun ToolbarActionContent(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: @Composable (Color) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.3f,
        animationSpec = tween(Motion.Fast),
        label = "toolbarAction"
    )
    Column(
        modifier = modifier
            .alpha(alpha)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon(MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesDrawer(
    notes: List<NoteEntity>,
    onInsert: (NoteEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 4.dp)
        ) {
            Text("笔记", style = MaterialTheme.typography.titleLarge)
            Text(
                "点一条插入到当前段之后",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            if (notes.isEmpty()) {
                Text(
                    "还没有笔记。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(Modifier.height(420.dp)) {
                    itemsIndexed(notes, key = { _, note -> note.id }) { _, note ->
                        Surface(
                            onClick = { onInsert(note) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Tag(NoteKind.label(note.kind), active = NoteKind.setting.contains(note.kind))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        note.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                                if (note.content.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        note.content.replace('\n', ' '),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ActiveBlockEditor(
    block: Block,
    cursorOffset: Int,
    onTextChange: (String) -> Unit,
    onSplitAt: (Int) -> Unit,
    onMergePrevious: () -> MergedBlock?,
    onAiRewrite: (text: String, start: Int, end: Int) -> Unit,
    textSizeSp: Float,
    lineHeightSp: Float,
    maxHeight: Dp,
    modifier: Modifier
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val onTextChangeState = rememberUpdatedState(onTextChange)
    val onSplitAtState = rememberUpdatedState(onSplitAt)
    val onMergeState = rememberUpdatedState(onMergePrevious)
    val onAiRewriteState = rememberUpdatedState(onAiRewrite)

    val editText = remember(block.id, textSizeSp, lineHeightSp) {
        BlockEditText(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            // 主题里正文是 Serif，EditText 默认是 sans：不显式设字体，点进段落的一瞬间字会变体
            typeface = android.graphics.Typeface.SERIF
            setLineSpacing(0f, lineHeightSp / textSizeSp)
            gravity = Gravity.TOP or Gravity.START
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setTextColor(textColor)
            setText(block.text)
            setSelection(text.length)
            // 收起键盘后再点正文（段落还是活动状态）也要能把输入法叫回来
            setOnClickListener { showSoftKeyboard(this) }
            customSelectionActionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    menu?.add(0, MENU_AI_REWRITE, 0, "AI 改写")
                        ?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                    if (item?.itemId != MENU_AI_REWRITE) return false
                    val start = selectionStart
                    val end = selectionEnd
                    if (start in 0 until end && end <= text.length) {
                        onAiRewriteState.value(text.substring(start, end), start, end)
                    }
                    mode?.finish()
                    return true
                }

                override fun onDestroyActionMode(mode: ActionMode?) = Unit
            }
        }
    }

    DisposableEffect(editText) {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (editText.suppress.get()) return
                val inputAtNanos = android.os.SystemClock.elapsedRealtimeNanos()
                // 输入到下一帧的耗时：只在 debug 打点；release 每敲一个字都写 logcat 会拖慢输入。
                if (com.creativeworkbench.writer.BuildConfig.DEBUG) {
                    editText.postOnAnimation {
                        android.util.Log.i(
                            "WB_PERF",
                            "input_ms=" + ((android.os.SystemClock.elapsedRealtimeNanos() - inputAtNanos) / 1_000_000.0)
                        )
                    }
                }
                val text = s?.toString().orEmpty()
                val newline = text.indexOf('\n')
                if (newline >= 0) {
                    editText.suppress.set(true)
                    s?.delete(newline, newline + 1)
                    editText.suppress.set(false)
                    onSplitAtState.value(newline)
                } else {
                    onTextChangeState.value(text)
                }
            }
        }
        editText.addTextChangedListener(watcher)
        editText.onBackspaceAtStart = { onMergeState.value() }
        editText.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                false
            } else if (keyCode == KeyEvent.KEYCODE_ENTER && !event.isShiftPressed) {
                onSplitAtState.value(editText.selectionStart.coerceAtLeast(0))
                true
            } else if (keyCode == KeyEvent.KEYCODE_DEL &&
                editText.selectionStart == 0 &&
                editText.selectionEnd == 0
            ) {
                val merged = onMergeState.value()
                if (merged != null) {
                    editText.applyMerge(merged)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        onDispose {
            editText.removeTextChangedListener(watcher)
            editText.setOnKeyListener(null)
            editText.onBackspaceAtStart = null
        }
    }

    // 点一下段落就直接给焦点 + 弹输入法。只在 Compose 里 requestFocus 是不够的：
    // 焦点有了、键盘不出来，用户就得点第二下——0.10.0 的「要连点两次」就是这么来的。
    LaunchedEffect(block.id) {
        delay(40)
        editText.requestFocus()
        editText.setSelection(cursorOffset.coerceIn(0, editText.text.length))
        // 无条件请求输入法：回车分段会重建 EditText（新段 id 变了），
        // 只 requestFocus 的话输入法可能已经掉了——真机上表现为「回车后打字没反应」。
        editText.postDelayed({ showSoftKeyboard(editText) }, 60)
    }
    LaunchedEffect(cursorOffset) {
        if (editText.selectionStart != cursorOffset) {
            editText.setSelection(cursorOffset.coerceIn(0, editText.text.length))
        }
    }

    AndroidView(
        factory = { editText },
        // 和未激活的 Text 对齐：那边有 6dp 上下内边距，这边不加的话点一下正文会往上跳
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight).padding(vertical = 6.dp),
        update = { view ->
            if (view.text.toString() != block.text) {
                val selection = view.selectionStart.coerceAtLeast(0)
                view.suppress.set(true)
                view.setText(block.text)
                view.setSelection(selection.coerceAtMost(view.text.length))
                view.suppress.set(false)
            }
            view.setTextColor(textColor)
        }
    )
}

/** 输入法弹出的可靠做法：窗口有焦点后 showSoftInput，SHOW_IMPLICIT 不弹候选条也能出来。 */
private fun showSoftKeyboard(view: View) {
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
    if (!view.isFocused) view.requestFocus()
    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

/**
 * 软键盘的退格在段首不会触发 OnKeyListener；用 InputConnectionWrapper
 * 拦截 deleteSurroundingText 作为补充。
 */
private const val MENU_AI_REWRITE = 1001

private class BlockEditText(context: Context) : EditText(context) {
    /** 程序改文本时抬起来，别让 TextWatcher 把它当成用户输入再回写一遍。 */
    val suppress = AtomicBoolean(false)
    var onBackspaceAtStart: (() -> MergedBlock?)? = null

    /**
     * 段首退格跨段的连续删除：把上一段并进来、光标落到接缝处，**同一个 EditText 继续被长按退格**。
     * 旧实现把活动段切到上一段的 id，EditText 被重建、输入法连接断掉，长按就停在那儿了。
     */
    fun applyMerge(merged: MergedBlock) {
        suppress.set(true)
        // 增量插入而不是整段 setText：输入法的组合状态不被打断，长按退格才不会断。
        if (merged.prefix.isNotEmpty()) {
            text?.insert(0, merged.prefix)
        }
        setSelection(merged.caret.coerceIn(0, length()))
        suppress.set(false)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(base, false) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (tryBackspaceAtStart(beforeLength, afterLength)) return true
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                if (tryBackspaceAtStart(beforeLength, afterLength)) return true
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            }

            private fun tryBackspaceAtStart(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength < 1 || afterLength != 0) return false
                if (selectionStart != 0 || selectionEnd != 0) return false
                if (text.isNullOrEmpty()) return false
                val merged = onBackspaceAtStart?.invoke() ?: return false
                applyMerge(merged)
                return true
            }
        }
    }
}

/**
 * 章节面板（唯一入口）：底部弹层。
 *
 * 为什么从「右侧滑出侧栏」换成底部弹层：
 * 1) 右边缘全屏侧栏和 Android 10+ 的全面屏左右边缘返回手势抢同一片热区，
 *    系统手势优先，用户想拉侧栏结果退出编辑器；靠 systemGestureExclusion 也救不回来
 *    （每条边最多 200dp，且系统手势仍然优先）；
 * 2) 底部弹层是纵向手势，和边缘手势零重叠，还能被拖拽关闭；
 * 3) 顺手把「下一章 / 新建下一章」「作品目录」「本章大纲」放进同一个面板，
 *    用户不用先退出编辑器再找目录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterSheet(
    workTitle: String,
    chapters: List<ChapterEntity>,
    currentChapterId: String,
    nextChapter: ChapterEntity?,
    /** 本章正文里提到的笔记（与 AI 注入共用 MentionIndex）。 */
    mentioned: List<com.creativeworkbench.writer.core.data.MentionIndex.Mention>,
    onOpenNote: (String) -> Unit,
    onPick: (String) -> Unit,
    onOpenDirectory: () -> Unit,
    onOpenOutline: () -> Unit,
    onCreateNext: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentIndex = chapters.indexOfFirst { it.id == currentChapterId }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("章节", style = MaterialTheme.typography.titleLarge)
                    Text(
                        workTitle.ifBlank { "作品" } + " · " +
                            (if (currentIndex >= 0) "第 " + (currentIndex + 1) + " 章" else "未定位") +
                            " / 共 " + chapters.size + " 章",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (nextChapter != null) {
                    PillButton(
                        text = "下一章 · " + nextChapter.title,
                        onClick = { onPick(nextChapter.id) }
                    )
                } else {
                    PillButton(
                        text = "新建下一章",
                        icon = Icons.Default.Add,
                        onClick = onCreateNext
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GhostButton(text = "作品目录", onClick = onOpenDirectory)
                GhostButton(text = "本章大纲", onClick = onOpenOutline)
            }
            if (nextChapter != null) {
                TextButton(
                    onClick = onCreateNext,
                    modifier = Modifier.padding(start = 12.dp)
                ) { Text("在末尾新建一章") }
            }
            // 本章提到了哪些人物/设定：点一下进笔记详情。规则和 AI 注入完全相同。
            if (mentioned.isNotEmpty()) {
                Text(
                    "本章提到 " + mentioned.size + " 条笔记",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 2.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    mentioned.take(12).forEach { mention ->
                        Tag(
                            text = mention.note.title + " ×" + mention.count,
                            active = false,
                            onClick = { onOpenNote(mention.note.id) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(chapters, key = { it.id }) { chapter ->
                    val current = chapter.id == currentChapterId
                    Surface(
                        onClick = { if (!current) onPick(chapter.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        shape = MaterialTheme.shapes.small,
                        color = if (current) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                ((chapters.indexOfFirst { it.id == chapter.id }) + 1).toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(28.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    chapter.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    chapter.wordCount.toString() + " 字" + (if (current) " · 正在写" else ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
