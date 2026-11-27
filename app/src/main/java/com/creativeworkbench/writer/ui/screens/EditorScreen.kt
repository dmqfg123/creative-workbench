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
import com.creativeworkbench.writer.ui.editor.BodyEditor
import com.creativeworkbench.writer.ui.editor.BodyEditorController
import com.creativeworkbench.writer.ui.editor.EditorViewModel
import com.creativeworkbench.writer.ui.editor.EditorViewModelFactory
import com.creativeworkbench.writer.ui.editor.MergedBlock
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
    // 单一编辑面的控制器：外部改文本/定位光标/滚动都由它做，绝不 setText
    val bodyController = remember { BodyEditorController() }
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val bottomMarginPx = with(density) { 12.dp.roundToPx() }
    var notesOpen by remember { mutableStateOf(false) }
    // 删段落后的撤销条：内联在工具条上方，不再用 Snackbar（会压在键盘/工具条上）
    var undoBarVisible by remember { mutableStateOf(false) }
    var undoBarText by remember { mutableStateOf("") }
    var chapterSheetOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var checkSheetOpen by remember { mutableStateOf(false) }
    // AI 检查结果落到编辑器状态：关掉面板、跳去改字之后回来还在，不用重跑（重跑要花钱）
    LaunchedEffect(aiState.issues, aiState.action?.id) {
        if (aiState.action?.id == AiActions.CHECK && aiState.issues.isNotEmpty()) {
            viewModel.setCheckIssues(aiState.issues)
        }
    }
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
    // 继续上次位置：记「光标字符偏移 + 滚动比例」。
    // 存比例而不是像素——换字号/换屏宽后，像素值会把用户丢到别的地方。
    val savePosition: () -> Unit = {
        runCatching {
            app.container.positions.save(
                chapterId = chapterId,
                caret = bodyController.caretForSave(),
                scrollRatio = bodyController.ratioForSave(),
                topChar = bodyController.topCharForSave()
            )
        }
    }
    LaunchedEffect(chapterId) {
        // 书架点进来时优先回到这一章：记的是「上次在编辑哪一章」，不是「最近保存过哪一章」
        app.container.positions.setLastChapter(workId, chapterId)
    }
    // 位置随滚动/移光标持续落盘（控制器内部 500ms 节流）：
    // 不等退出那一刻——返回、被杀、切后台都可能来不及走保存分支。
    DisposableEffect(bodyController, chapterId) {
        bodyController.onPositionChanged = { caret, ratio, topChar ->
            app.container.positions.save(chapterId, caret, ratio, topChar)
        }
        onDispose { bodyController.onPositionChanged = null }
    }
    /**
     * 恢复到上次位置（每章只做一次）。
     *
     * 旧实现的坑：章节加载完成的那一刻，BodyEditor 这个 AndroidView 往往还没挂上、
     * 更没有完成排版，moveCaret/scrollTo 全落在 null 上；然后 restoredPosition 直接置 true，
     * 再也不重试——表现就是「每次从首页进来都回到开头」。
     * 现在：等挂载 → 等布局出高度 → 按比例滚（restoreScroll 内部还有布局重试）。
     */
    LaunchedEffect(state.loading) {
        if (state.loading || restoredPosition) return@LaunchedEffect
        restoredPosition = true
        val position = app.container.positions.load(chapterId) ?: return@LaunchedEffect
        var waited = 0
        while (!bodyController.isAttached && waited < 1200) {
            delay(32)
            waited += 32
        }
        if (!bodyController.isAttached) return@LaunchedEffect
        // setCaret 只放光标不滚动；滚动交给 restoreScroll 按比例做
        android.util.Log.i(
            "WB_POS",
            "restore-chapter " + chapterId.take(8) + " caret=" + position.caret +
                " ratio=" + String.format(java.util.Locale.US, "%.3f", position.scrollRatio)
        )
        if (position.caret > 0) bodyController.setCaret(position.caret)
        delay(60)
        // 主锚点：字符偏移（换字号/换屏宽也落在同一句）；没有偏移才退回比例/光标
        when {
            position.topChar > 0 -> bodyController.restoreToChar(position.topChar, position.scrollRatio)
            position.scrollRatio > 0.001f -> bodyController.restoreScroll(position.scrollRatio)
            position.caret > 0 -> bodyController.scrollCaretIntoView()
        }
    }

    // 只在切换段落 / 键盘弹出或收起时对齐几次（等键盘动画落定），**不监听滚动**：
    // 之前盯着视口/可见项变化，用户一上下滑就被拽回光标位置，根本没法看全文。
    // 0.13：把「键盘是否弹出」也作为触发条件——之前只在切段时对齐一次，
    // 搜狗键盘弹得慢（>600ms）时视口还没缩完就对齐完了，光标又被挡在键盘后面，
    // 用户看到的就是「自动顶到键盘上面这个功能时灵时不灵」。
    // 键盘弹出/候选条高度变化后，把光标行顶进可视区。
    // 单一编辑面下这是 EditText 自己的 requestRectangleOnScreen，不再靠 LazyColumn 算偏移，
    // 所以「长段落里的光标」也能保证可见（旧的分块设计这里必然失败）。
    val imePx = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imePx, state.activeBlockId) {
        if (imePx <= 0) return@LaunchedEffect
        listOf(120L, 360L, 700L).forEach { wait ->
            delay(wait)
            bodyController.scrollCaretIntoView()
        }
    }
    // 段落操作/撤销后由 VM 指定光标位置
    LaunchedEffect(state.pendingCaret) {
        val target = state.pendingCaret ?: return@LaunchedEffect
        bodyController.setTextExternally(viewModel.bodyText(), target)
        // 只 setSelection 不一定滚：从 AI 检查结果跳段、撤销后回填都要主动把光标行顶进可视区
        delay(30)
        bodyController.scrollCaretIntoView()
        viewModel.clearPendingCaret()
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
        // 返回这一刻视图还在，可以把最后 300ms 内的滚动补进缓存
        bodyController.notifyPosition(force = true)
        viewModel.flushAndBack(onBack)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshIfClean()
            if (event == Lifecycle.Event.ON_PAUSE) {
                // 只写缓存：此时视图可能已经被导航摘掉，重算会得到 0
                savePosition()
                viewModel.flush()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            // 返回时界面先于 viewmodel 销毁：这里再存一次（此时控制器已缓存最后位置）
            savePosition()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)) {
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
                        state.caretBlockChars > 2000 -> "本段较长，建议按情节分段"
                        else -> null
                    },
                    onStartWriting = { bodyController.focusAndShowKeyboard() },
                    onRetrySave = { viewModel.retrySave() },
                    onExportBackup = { backupExport.launch("雨刮模拟器-紧急备份.json.gz") }
                )
                // 正文：一整章一个 EditText（见 BodyEditor 的注释：为什么不能一段一个）
                BodyEditor(
                    controller = bodyController,
                    text = viewModel.bodyText(),
                    textSizeSp = bodySize,
                    lineHeightSp = lineHeight,
                    onTextChange = { viewModel.onBodyEdited(it) },
                    onSelectionChange = { viewModel.onCaretMoved(it) },
                    onAiRewriteSelection = { start, end, selected ->
                        val info = viewModel.selectionInfo(start, end)
                        if (info != null) {
                            pendingSelection = info
                            keyboard?.hide()
                            aiOpen = true
                            aiViewModel.prepare(AiActions.REWRITE, state.blocks, info.blockId, info)
                        } else {
                            // 跨段选中、或选区在点菜单前被输入法改短了：给个明确提示，别静默无反应
                            android.widget.Toast.makeText(
                                context,
                                "改写只支持单段选中：请只选一段文字（跨段请分段改）",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)
                )
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
                    val activeIndex = state.caretBlockIndex
                    if (state.blocks.isNotEmpty()) {
                        Text(
                            "第 " + (activeIndex + 1).coerceAtMost(state.blocks.size) + " / " +
                                state.blocks.size + " 段 · " + state.caretBlockChars + " 字",
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
                    androidx.compose.animation.AnimatedVisibility(visible = state.checkIssues.isNotEmpty()) {
                        CheckResultsBar(
                            count = state.checkIssues.size,
                            onClick = { checkSheetOpen = true },
                            onClear = { viewModel.clearCheckIssues() }
                        )
                    }
                    EditorToolbar(
                        undoAvailable = state.undoAvailable,
                        redoAvailable = state.redoAvailable,
                        hasActiveBlock = state.blocks.isNotEmpty(),
                        canMoveUp = state.caretBlockIndex > 0,
                        canMoveDown = state.caretBlockIndex < state.blocks.lastIndex,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                        onMoveUp = { viewModel.moveParagraphAtCaret(-1) },
                        onMoveDown = { viewModel.moveParagraphAtCaret(1) },
                        onAdd = {
                            viewModel.insertParagraphAtCaret()
                            bodyController.focusAndShowKeyboard()
                        },
                        onDelete = {
                            val hadText = state.caretBlockChars > 0
                            viewModel.deleteParagraphAtCaret()
                            if (hadText) {
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
            onOpenHistory = {
                chapterSheetOpen = false
                keyboard?.hide()
                viewModel.loadHistory()
                historyOpen = true
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
            if (actionId == AiActions.REWRITE || actionId == AiActions.EXPAND) pendingSelection else null
        )
    }

    if (checkSheetOpen) {
        CheckSheet(
            issues = state.checkIssues,
            onJump = { issue ->
                val range = viewModel.issueRange(issue)
                if (range == null) {
                    // 模型有时会引用正文里并不存在的片段：别静默无反应，直说
                    android.widget.Toast.makeText(
                        context,
                        "这段原文不在正文里了（可能已被改过），可以重新检查一次",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    bodyController.selectRange(range.first, range.second)
                    checkSheetOpen = false
                }
            },
            onApply = { issue ->
                viewModel.applyIssueSuggestion(issue)
                if (state.checkIssues.size <= 1) checkSheetOpen = false
            },
            onDismiss = { checkSheetOpen = false }
        )
    }

    if (historyOpen) {
        HistorySheet(
            items = state.history,
            onRestore = { id ->
                viewModel.restoreSnapshot(id)
                historyOpen = false
            },
            onDismiss = { historyOpen = false }
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
                // 面板里的问题点一下：优先选中它的「原文片段」，落到字上才能直接改
                aiOpen = false
                val issue = aiState.issues.firstOrNull { it.blockId == blockId }
                val range = issue?.let { viewModel.issueRange(it) }
                if (range != null) {
                    bodyController.selectRange(range.first, range.second)
                } else {
                    viewModel.jumpToBlock(blockId)
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
            onNoteMode = { mode ->
                aiViewModel.setNoteMode(mode)
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
/** 本章历史：每 5 分钟自动留一份，误删/误操作后能回滚（恢复本身也能再撤销）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    items: List<com.creativeworkbench.writer.core.data.SnapshotEntity>,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val format = remember { java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()) }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            Text("本章历史", style = MaterialTheme.typography.titleLarge)
            Text(
                "保存时每 5 分钟自动留一份；点一条恢复，恢复本身也能撤销。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                Text(
                    "还没有历史快照（编辑几分钟并保存后会出现）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(items, key = { it.id }) { snapshot ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRestore(snapshot.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                format.format(java.util.Date(snapshot.createdAtMs)),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text("恢复", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/** 检查结果条：只有「N 条」和一个关闭，不加任何说明文字。 */
@Composable
private fun CheckResultsBar(count: Int, onClick: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "检查 " + count + " 条",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, contentDescription = "清除检查结果", modifier = Modifier.size(18.dp))
        }
    }
}

/** 检查结果列表：一行一条，点一下选中该条原文；有建议的可以一键替换。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckSheet(
    issues: List<com.creativeworkbench.writer.core.ai.AiIssue>,
    onJump: (com.creativeworkbench.writer.core.ai.AiIssue) -> Unit,
    onApply: (com.creativeworkbench.writer.core.ai.AiIssue) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            Text("检查结果", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(issues, key = { it.blockId + it.excerpt }) { issue ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onJump(issue) }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(issue.excerpt, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            issue.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (issue.suggestion.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    issue.suggestion,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onApply(issue) }) { Text("替换") }
                            }
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

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
    onOpenHistory: () -> Unit,
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
                GhostButton(text = "本章历史", onClick = onOpenHistory)
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
