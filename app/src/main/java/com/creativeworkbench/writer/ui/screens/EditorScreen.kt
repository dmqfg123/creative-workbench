package com.creativeworkbench.writer.ui.screens

import android.app.Application
import android.graphics.Color as AndroidColor
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.creativeworkbench.writer.core.data.Block
import com.creativeworkbench.writer.ui.editor.EditorViewModel
import com.creativeworkbench.writer.ui.editor.EditorViewModelFactory
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
    val viewModel: EditorViewModel = viewModel(
        key = "editor-" + chapterId,
        factory = EditorViewModelFactory(application, chapterId)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler { viewModel.flushAndBack(onBack) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.chapterTitle.ifBlank { "编辑器" }) },
                navigationIcon = {
                    TextButton(onClick = { viewModel.flushAndBack(onBack) }) { Text("返回") }
                },
                actions = {
                    TextButton(onClick = { viewModel.flushAndBack(onOpenDirectory) }) { Text("目录") }
                    TextButton(onClick = viewModel::undo, enabled = state.undoAvailable) { Text("撤销") }
                    TextButton(onClick = viewModel::redo, enabled = state.redoAvailable) { Text("重做") }
                    TextButton(onClick = viewModel::flush) { Text("保存") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    state.wordCount.toString() + " 字",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    when {
                        state.error != null -> state.error ?: ""
                        state.saving -> "保存中…"
                        state.dirty -> "未保存"
                        else -> "已保存"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在打开章节…")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(state.blocks, key = { _, block -> block.id }) { index, block ->
                        BlockRow(
                            block = block,
                            index = index,
                            lastIndex = state.blocks.lastIndex,
                            active = state.activeBlockId == block.id,
                            onActivate = { viewModel.activate(block.id) },
                            onTextChange = { viewModel.onTextChanged(block.id, it) },
                            onSplitAt = { viewModel.splitBlock(block.id, it) },
                            onMergePrevious = { viewModel.mergeWithPrevious(block.id) },
                            onMoveUp = { viewModel.move(block.id, -1) },
                            onMoveDown = { viewModel.move(block.id, 1) },
                            onInsertAfter = { viewModel.insertAfter(block.id) },
                            onDelete = { viewModel.remove(block.id) }
                        )
                    }
                    item {
                        TextButton(
                            onClick = { viewModel.insertAfter(state.blocks.lastOrNull()?.id.orEmpty()) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("＋ 段落") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockRow(
    block: Block,
    index: Int,
    lastIndex: Int,
    active: Boolean,
    onActivate: () -> Unit,
    onTextChange: (String) -> Unit,
    onSplitAt: (Int) -> Unit,
    onMergePrevious: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onInsertAfter: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(28.dp).padding(top = 6.dp)) {
            Text(
                "⋮",
                modifier = Modifier.clickable { menuOpen = true }.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("上移") },
                    enabled = index > 0,
                    onClick = { menuOpen = false; onMoveUp() }
                )
                DropdownMenuItem(
                    text = { Text("下移") },
                    enabled = index < lastIndex,
                    onClick = { menuOpen = false; onMoveDown() }
                )
                DropdownMenuItem(
                    text = { Text("在后面插入") },
                    onClick = { menuOpen = false; onInsertAfter() }
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
        if (active) {
            ActiveBlockEditor(
                block = block,
                onTextChange = onTextChange,
                onSplitAt = onSplitAt,
                onMergePrevious = onMergePrevious,
                modifier = Modifier.weight(1f)
            )
        } else {
            val hint = block.text.ifBlank { "点击编辑" }
            Text(
                text = hint,
                modifier = Modifier.weight(1f).clickable(onClick = onActivate).padding(vertical = 4.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (block.text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ActiveBlockEditor(
    block: Block,
    onTextChange: (String) -> Unit,
    onSplitAt: (Int) -> Unit,
    onMergePrevious: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    val currentOnSplitAt by rememberUpdatedState(onSplitAt)
    val currentOnMergePrevious by rememberUpdatedState(onMergePrevious)
    val suppress = remember { AtomicBoolean(false) }

    val editText = remember(block.id) {
        EditText(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setLineSpacing(0f, 1.18f)
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
                if (!suppress.get()) currentOnTextChange(s?.toString().orEmpty())
            }
        }
        editText.addTextChangedListener(watcher)
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
        }
    }

    LaunchedEffect(block.id) {
        kotlinx.coroutines.delay(60)
        editText.requestFocus()
        editText.setSelection(editText.text.length)
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
