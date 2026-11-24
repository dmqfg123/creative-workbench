package com.creativeworkbench.writer.ui.screens

import android.app.Application
import android.content.Context
import android.graphics.Color as AndroidColor
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.creativeworkbench.writer.R
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.data.Block
import com.creativeworkbench.writer.core.settings.FontSizePreset
import com.creativeworkbench.writer.ui.components.ActionSheet
import com.creativeworkbench.writer.ui.components.SheetAction
import com.creativeworkbench.writer.ui.theme.Motion
import com.creativeworkbench.writer.ui.editor.EditorViewModel
import com.creativeworkbench.writer.ui.editor.EditorViewModelFactory
import com.creativeworkbench.writer.ui.editor.FocusScrollTarget
import com.creativeworkbench.writer.ui.editor.VisibleBlockBounds
import com.creativeworkbench.writer.ui.editor.focusedBlockScrollTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    workId: String,
    chapterId: String,
    onBack: () -> Unit,
    onOpenDirectory: () -> Unit
) {
    val application = LocalContext.current.applicationContext as Application
    val app = application as WorkbenchApp
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
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val bottomMarginPx = with(density) { 12.dp.roundToPx() }
    var menuOpen by remember { mutableStateOf(false) }
    var blockMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.activeBlockId) {
        val activeId = state.activeBlockId ?: return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.viewportEndOffset }
            .distinctUntilChanged()
            .collectLatest {
                val index = state.blocks.indexOfFirst { block -> block.id == activeId }
                if (index < 0) return@collectLatest
                withFrameNanos { }
                val info = listState.layoutInfo
                if (info.visibleItemsInfo.isEmpty()) return@collectLatest
                val target = focusedBlockScrollTarget(
                    focusedIndex = index,
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
    }

    BackHandler {
        keyboard?.hide()
        viewModel.flushAndBack(onBack)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshIfClean()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.workTitle.ifBlank { state.chapterTitle.ifBlank { "编辑器" } },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            keyboard?.hide()
                            viewModel.flushAndBack(onBack)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            keyboard?.hide()
                            menuOpen = true
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在打开章节…")
                }
            } else if (state.error != null && state.blocks.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        state.error ?: "章节无法打开",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
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
                            onStartWriting = {
                                state.blocks.firstOrNull()?.let { block ->
                                    viewModel.activate(block.id, 0)
                                }
                            }
                        )
                    }
                    itemsIndexed(state.blocks, key = { _, block -> block.id }) { _, block ->
                        if (state.activeBlockId == block.id) {
                            ActiveBlockEditor(
                                block = block,
                                cursorOffset = state.cursorOffset,
                                onTextChange = { viewModel.onTextChanged(block.id, it) },
                                onSplitAt = { viewModel.splitBlock(block.id, it) },
                                onMergePrevious = { viewModel.mergeWithPrevious(block.id) },
                                textSizeSp = bodySize,
                                lineHeightSp = lineHeight,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            InactiveBlock(
                                block = block,
                                onActivate = { offset -> viewModel.activate(block.id, offset) }
                            )
                        }
                    }
                    item {
                        TextButton(
                            onClick = { viewModel.insertAfter(state.blocks.lastOrNull()?.id.orEmpty()) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("继续写下一段")
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !state.loading,
                    enter = slideInVertically(
                        animationSpec = tween(Motion.Medium, easing = Motion.EmphasizedDecelerate)
                    ) { fullHeight -> fullHeight } + fadeIn(tween(Motion.Fast)),
                    exit = slideOutVertically(
                        animationSpec = tween(Motion.Fast, easing = Motion.EmphasizedAccelerate)
                    ) { fullHeight -> fullHeight } + fadeOut(tween(Motion.Fast))
                ) {
                    EditorToolbar(
                        undoAvailable = state.undoAvailable,
                        redoAvailable = state.redoAvailable,
                        hasActiveBlock = state.activeBlockId != null,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                        onAdd = {
                            val id = state.activeBlockId ?: state.blocks.lastOrNull()?.id
                            if (id != null) viewModel.insertAfter(id)
                        },
                        onParagraph = {
                            keyboard?.hide()
                            blockMenuOpen = true
                        },
                        onHideKeyboard = { keyboard?.hide() }
                    )
                }
            }
        }
    }

    if (menuOpen) {
        ActionSheet(
            title = state.chapterTitle.ifBlank { "章节" },
            subtitle = state.wordCount.toString() + " 字",
            actions = listOf(
                SheetAction(
                    label = "作品目录",
                    icon = Icons.Default.List,
                    onClick = {
                        keyboard?.hide()
                        viewModel.flushAndBack(onOpenDirectory)
                    }
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

    if (blockMenuOpen) {
        ActionSheet(
            title = "本段操作",
            actions = listOf(
                SheetAction(
                    label = "上移一段",
                    icon = Icons.Default.KeyboardArrowUp,
                    onClick = { state.activeBlockId?.let { viewModel.move(it, -1) } }
                ),
                SheetAction(
                    label = "下移一段",
                    icon = Icons.Default.KeyboardArrowDown,
                    onClick = { state.activeBlockId?.let { viewModel.move(it, 1) } }
                ),
                SheetAction(
                    label = "删除本段",
                    icon = Icons.Default.Delete,
                    destructive = true,
                    onClick = { state.activeBlockId?.let { viewModel.remove(it) } }
                )
            ),
            onDismiss = { blockMenuOpen = false }
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
    onStartWriting: () -> Unit
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
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(
            chapterTitle.ifBlank { "未命名章节" },
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                animatedWordCount.toString() + " 字",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
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
        }
        if (empty) {
            Spacer(Modifier.height(20.dp))
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
    }
}

@Composable
private fun InactiveBlock(block: Block, onActivate: (Int) -> Unit) {
    var layout by remember(block.id) { mutableStateOf<TextLayoutResult?>(null) }
    val interaction = remember { MutableInteractionSource() }
    val text = block.text.ifBlank { " " }
    Text(
        text = text,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .indication(interaction, ripple())
            .pointerInput(block.id) {
                detectTapGestures(
                    onPress = { offset ->
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
            .padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = if (block.text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.onSurface
    )
}

/**
 * 键盘上方的浮动工具条（纯纯写作的快捷输入栏位置）。
 * 贴住输入法上沿，五个等宽触控块，手指不用离开键盘区太远。
 */
@Composable
private fun EditorToolbar(
    undoAvailable: Boolean,
    redoAvailable: Boolean,
    hasActiveBlock: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAdd: () -> Unit,
    onParagraph: () -> Unit,
    onHideKeyboard: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarAction(painterResource(R.drawable.ic_undo), "撤销", undoAvailable, onUndo, Modifier.weight(1f))
            ToolbarAction(painterResource(R.drawable.ic_redo), "重做", redoAvailable, onRedo, Modifier.weight(1f))
            ToolbarAction(Icons.Default.Add, "新段", hasActiveBlock, onAdd, Modifier.weight(1f))
            ToolbarAction(Icons.Default.List, "段落", hasActiveBlock, onParagraph, Modifier.weight(1f))
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
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
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
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
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
                indication = ripple(),
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

@Composable
private fun ActiveBlockEditor(
    block: Block,
    cursorOffset: Int,
    onTextChange: (String) -> Unit,
    onSplitAt: (Int) -> Unit,
    onMergePrevious: () -> Unit,
    textSizeSp: Float,
    lineHeightSp: Float,
    modifier: Modifier
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    val currentOnSplitAt by rememberUpdatedState(onSplitAt)
    val currentOnMergePrevious by rememberUpdatedState(onMergePrevious)
    val suppress = remember { AtomicBoolean(false) }

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
        }
    }

    DisposableEffect(editText) {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (suppress.get()) return
                val text = s?.toString().orEmpty()
                val newline = text.indexOf('\n')
                if (newline >= 0) {
                    suppress.set(true)
                    s?.delete(newline, newline + 1)
                    suppress.set(false)
                    currentOnSplitAt(newline)
                } else {
                    currentOnTextChange(text)
                }
            }
        }
        editText.addTextChangedListener(watcher)
        editText.onBackspaceAtStart = { currentOnMergePrevious() }
        editText.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                false
            } else if (keyCode == KeyEvent.KEYCODE_ENTER && !event.isShiftPressed) {
                currentOnSplitAt(editText.selectionStart.coerceAtLeast(0))
                true
            } else if (keyCode == KeyEvent.KEYCODE_DEL &&
                editText.selectionStart == 0 &&
                editText.selectionEnd == 0
            ) {
                currentOnMergePrevious()
                true
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

    LaunchedEffect(block.id, cursorOffset) {
        delay(60)
        editText.requestFocus()
        editText.setSelection(cursorOffset.coerceIn(0, editText.text.length))
    }

    AndroidView(
        factory = { editText },
        modifier = modifier.fillMaxWidth(),
        update = { view ->
            if (view.text.toString() != block.text) {
                val selection = view.selectionStart.coerceAtLeast(0)
                suppress.set(true)
                view.setText(block.text)
                view.setSelection(selection.coerceAtMost(view.text.length))
                suppress.set(false)
            }
            view.setTextColor(textColor)
        }
    )
}

/**
 * 软键盘的退格在段首不会触发 OnKeyListener；用 InputConnectionWrapper
 * 拦截 deleteSurroundingText 作为补充。剩下少数输入法仍可能不发删除事件，
 * 所以段落菜单始终保留「合并到上一段」兜底。
 */
private class BlockEditText(context: Context) : EditText(context) {
    var onBackspaceAtStart: (() -> Unit)? = null

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
                if (beforeLength == 1 && afterLength == 0 &&
                    selectionStart == 0 && selectionEnd == 0
                ) {
                    onBackspaceAtStart?.invoke()
                    return true
                }
                return false
            }
        }
    }
}
