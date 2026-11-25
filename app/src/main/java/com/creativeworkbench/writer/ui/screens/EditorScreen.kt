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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import com.creativeworkbench.writer.ui.design.IconAction
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
    onOpenDirectory: () -> Unit
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
    var menuOpen by remember { mutableStateOf(false) }
    var notesOpen by remember { mutableStateOf(false) }
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

    // 键盘一弹就把视口挤小；这一步负责把正在写的段拉到键盘上沿之上。
    // 只监听 activeBlockId 是不够的——那时机在键盘弹出之前，算出来「已可见」就不动了。
    LaunchedEffect(state.activeBlockId, imeBottomPx, activeBlockMaxHeight) {
        val activeId = state.activeBlockId ?: return@LaunchedEffect
        val index = state.blocks.indexOfFirst { block -> block.id == activeId }
        if (index < 0) return@LaunchedEffect
        delay(if (imeBottomPx > 0) 160 else 60)
        withFrameNanos { }
        val info = listState.layoutInfo
        if (info.visibleItemsInfo.isEmpty()) return@LaunchedEffect
        val target = focusedBlockScrollTarget(
            // LazyColumn 第 0 项是章节头，正文段从 item 下标 +1 算——
            // 老代码直接拿 block 下标去比，永远对不上正确的 item，这就是「不自动对齐」的一半原因。
            focusedIndex = index + 1,
            visibleItems = info.visibleItemsInfo.map { item ->
                VisibleBlockBounds(item.index, item.offset, item.size)
            },
            viewportStartOffset = info.viewportStartOffset,
            viewportEndOffset = info.viewportEndOffset,
            bottomMarginPx = bottomMarginPx
        )
        when (target) {
            is FocusScrollTarget.By -> listState.animateScrollBy(target.deltaPx.toFloat())
            is FocusScrollTarget.ToItem -> listState.animateScrollToItem(target.index, target.scrollOffset)
            null -> Unit
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
    val chapterChars = state.blocks.sumOf { it.text.length }

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
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
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
                            // 段落操作条：固定跟在这一段下面，不再把按钮压在文字上
                            ParagraphActionBar(
                                canMoveUp = index > 0,
                                canMoveDown = index < state.blocks.lastIndex,
                                onMoveUp = { viewModel.move(block.id, -1) },
                                onMoveDown = { viewModel.move(block.id, 1) },
                                onInsertBelow = { viewModel.insertAfter(block.id) },
                                onDelete = {
                                    viewModel.remove(block.id)
                                    scope.launch {
                                        val result = snackbar.showSnackbar(
                                            message = "已删除这一段",
                                            actionLabel = "撤销",
                                            duration = androidx.compose.material3.SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) viewModel.undo()
                                    }
                                }
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
                EditorToolbar(
                    undoAvailable = state.undoAvailable,
                    redoAvailable = state.redoAvailable,
                    hasActiveBlock = state.activeBlockId != null,
                    notesEnabled = notesEnabled,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onAdd = {
                        val id = state.activeBlockId ?: state.blocks.lastOrNull()?.id
                        if (id != null) viewModel.insertAfter(id)
                    },
                    onChapterList = {
                        keyboard?.hide()
                        chapterSheetOpen = true
                    },
                    onNotes = {
                        keyboard?.hide()
                        notesOpen = true
                    },
                    onHideKeyboard = { keyboard?.hide() }
                )
            }
        }
    }

    SnackbarHost(
        hostState = snackbar,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
    )
    }

    if (menuOpen) {
        ActionSheet(
            title = state.chapterTitle.ifBlank { "章节" },
            subtitle = state.wordCount.toString() + " 字" + if (chapterChars >= 50_000) " · 偏长" else "",
            actions = listOf(
                SheetAction(
                    label = "作品目录",
                    icon = Icons.Default.List,
                    onClick = { viewModel.flushAndBack(onOpenDirectory) }
                ),
                SheetAction(
                    label = "立即保存",
                    icon = Icons.Default.Check,
                    onClick = { viewModel.flush() }
                )
            ),
            onDismiss = { menuOpen = false }
        )
    }

    if (chapterSheetOpen) {
        ActionSheet(
            title = "换章",
            subtitle = state.workTitle.ifBlank { "作品" } + " · 共 " + state.chapters.size + " 章",
            actions = state.chapters.map { chapter ->
                SheetAction(
                    label = chapter.title,
                    description = chapter.wordCount.toString() + " 字" +
                        if (chapter.id == chapterId) " · 正在写" else "",
                    onClick = {
                        if (chapter.id != chapterId) {
                            keyboard?.hide()
                            val target = chapter.id
                            viewModel.flushThen { onSwitchChapter(target) }
                        }
                    }
                )
            },
            onDismiss = { chapterSheetOpen = false }
        )
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

    if (aiOpen) {
        // PRD §5.5：结果绑定发起时的 revision 与内容哈希；不一致只能看/复制，不能写。
        val aiStale = aiState.resultText.isNotBlank() &&
            (aiState.chapterRevision != state.revision || aiState.contentHash != viewModel.contentHash())
        AiPanel(
            state = aiState,
            stale = aiStale,
            selection = pendingSelection,
            hasContent = state.blocks.any { it.text.isNotBlank() },
            hasActiveBlock = state.activeBlockId != null,
            onAction = { actionId ->
                var selection = if (actionId == AiActions.REWRITE) pendingSelection else null
                // 没选中文字时，改写默认作用于当前段：面板里会明确显示「原文」。
                if (actionId == AiActions.REWRITE && selection == null) {
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
                val selection = if (actionId == AiActions.REWRITE) pendingSelection else null
                aiViewModel.prepare(actionId, state.blocks, state.activeBlockId, selection, allowOverflow = true)
            },
            onCancel = { aiViewModel.cancel() },
            onDismiss = {
                aiOpen = false
                aiViewModel.reset()
            },
            onWrite = { text ->
                val action = aiState.action
                if (action?.write == WriteTarget.BodyReplaceSelection) {
                    pendingSelection?.let { selection ->
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
            }
        )
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
                                "本章备注：给 AI 的提醒、待改的地方，都写这里；不计字数。",
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
 * 段落操作条：贴着「正在写的这一段」下面，四等分，位置固定。
 * 取代 0.10.0 那个浮在正文左侧、压住前两个字的小圆点——
 * 那个东西既看不见、又点不准、还挡字。
 */
@Composable
private fun ParagraphActionBar(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onInsertBelow: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ParagraphAction(Icons.Default.KeyboardArrowUp, "上移", canMoveUp, onMoveUp)
            ParagraphAction(Icons.Default.KeyboardArrowDown, "下移", canMoveDown, onMoveDown)
            ParagraphAction(Icons.Default.Add, "新段", true, onInsertBelow)
            ParagraphAction(Icons.Default.Delete, "删除", true, onDelete, destructive = true)
        }
    }
}

@Composable
private fun ParagraphAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/**
 * 键盘上方的工具条：六个等宽触控块，贴住输入法上沿。
 */
@Composable
private fun EditorToolbar(
    undoAvailable: Boolean,
    redoAvailable: Boolean,
    hasActiveBlock: Boolean,
    notesEnabled: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAdd: () -> Unit,
    onChapterList: () -> Unit,
    onNotes: () -> Unit,
    onHideKeyboard: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarAction(painterResource(R.drawable.ic_undo), "撤销", undoAvailable, onUndo, Modifier.weight(1f))
            ToolbarAction(painterResource(R.drawable.ic_redo), "重做", redoAvailable, onRedo, Modifier.weight(1f))
            ToolbarAction(Icons.Default.Add, "新段", hasActiveBlock, onAdd, Modifier.weight(1f))
            ToolbarAction(Icons.Default.List, "换章", true, onChapterList, Modifier.weight(1f))
            ToolbarAction(
                painterResource(R.drawable.ic_notes),
                "笔记",
                notesEnabled,
                onNotes,
                Modifier.weight(1f)
            )
            ToolbarAction(Icons.Default.KeyboardArrowDown, "收起", true, onHideKeyboard, Modifier.weight(1f))
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
                "点一条，把正文插入到当前段之后（可撤销）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            if (notes.isEmpty()) {
                Text(
                    "还没有笔记。去「笔记」页建人物和设定，AI 会强制带上；素材类可以在这里插入正文。",
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
            setLineSpacing(0f, lineHeightSp / textSizeSp)
            gravity = Gravity.TOP or Gravity.START
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setTextColor(textColor)
            setText(block.text)
            setSelection(text.length)
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
        editText.postDelayed({ showSoftKeyboard(editText) }, 60)
    }
    LaunchedEffect(cursorOffset) {
        if (editText.selectionStart != cursorOffset) {
            editText.setSelection(cursorOffset.coerceIn(0, editText.text.length))
        }
    }

    AndroidView(
        factory = { editText },
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight),
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
