package com.creativeworkbench.writer.ui.screens

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.creativeworkbench.writer.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.data.NoteKind
import com.creativeworkbench.writer.core.data.WorkEntity
import com.creativeworkbench.writer.ui.components.ActionSheet
import com.creativeworkbench.writer.ui.components.SheetAction
import com.creativeworkbench.writer.ui.design.ChipRow
import com.creativeworkbench.writer.ui.design.IconAction
import com.creativeworkbench.writer.ui.design.PillButton
import com.creativeworkbench.writer.ui.design.Tag
import com.creativeworkbench.writer.ui.notes.NoteDetailViewModel
import com.creativeworkbench.writer.ui.notes.NoteDetailViewModelFactory
import com.creativeworkbench.writer.ui.notes.newNoteId

/**
 * 笔记详情：一个页面装六种 kind。
 * 标题/正文/别名/标签都是自由文本，不做专用字段表单。
 */
@Composable
fun NoteDetailScreen(
    noteId: String,
    workId: String?,
    kind: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    // rememberSaveable：进程被杀后回到 note/new 时不能换一个新 UUID，
    // 否则刚写进去的笔记成孤儿，页面上是空白新笔记。
    val actualId = androidx.compose.runtime.saveable.rememberSaveable(noteId) {
        if (noteId == "new") newNoteId() else noteId
    }
    val viewModel: NoteDetailViewModel = viewModel(
        key = "note-" + actualId,
        factory = NoteDetailViewModelFactory(application, actualId, workId, kind)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var works by remember { mutableStateOf<List<WorkEntity>>(emptyList()) }
    var showCopyTo by remember { mutableStateOf(false) }
    val note = state.note

    LaunchedEffect(Unit) {
        works = (application as WorkbenchApp).container.database.workDao().observeAll().first()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.flush() }
    }

    // 退后台也存一次：只有 700ms 防抖的话，切走再被系统杀掉会丢最后一段输入
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) viewModel.flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showCopyTo) {
        ActionSheet(
            title = "复制到哪部作品",
            subtitle = "会生成独立副本，改副本不影响原设定",
            actions = works.map { work ->
                SheetAction(
                    label = work.title,
                    description = if (work.id == note?.workId) "当前作品" else "",
                    onClick = {
                        viewModel.duplicateTo(work.id) { ok ->
                            Toast.makeText(
                                context,
                                if (ok) "已复制到《" + work.title + "》" else "复制失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        showCopyTo = false
                    }
                )
            },
            onDismiss = { showCopyTo = false }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条笔记？") },
            text = { Text("不能恢复（备份里可能还有）。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(onBack)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
            shape = MaterialTheme.shapes.large
        )
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = {
                    viewModel.flush()
                    onBack()
                }
            )
            Text(
                if (note?.createdAtMs == note?.updatedAtMs) "新建笔记" else "笔记",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.IconButton(
                onClick = { viewModel.togglePinned() },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_pin),
                    contentDescription = "置顶",
                    tint = if (note?.pinned == true) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            androidx.compose.material3.IconButton(
                onClick = { showCopyTo = true },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_copy),
                    contentDescription = "复制到其他作品",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconAction(
                icon = Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { confirmDelete = true }
            )
        }

        if (note == null || state.loading) {
            Text(
                "正在打开…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            ChipRow {
                NoteKind.all.forEach { option ->
                    Tag(
                        text = NoteKind.label(option),
                        active = note.kind == option,
                        onClick = { viewModel.setKind(option) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    NoteKind.setting.contains(note.kind) -> "人物/设定固定进上下文。"
                    note.kind == NoteKind.MEMO -> "备忘不进上下文。"
                    else -> "素材要开开关才进。"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = note.title,
                onValueChange = { viewModel.edit { n -> n.copy(title = it.replace("\n", "")) } },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (note.title.isEmpty()) {
                        Text(
                            "标题",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    inner()
                }
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            FieldLine(
                label = "别名",
                value = note.aliases,
                placeholder = "别名，逗号分隔",
                onChange = { value -> viewModel.edit { it.copy(aliases = value) } }
            )
            FieldLine(
                label = "标签",
                value = note.tags,
                placeholder = "标签，用逗号分隔",
                onChange = { value -> viewModel.edit { it.copy(tags = value) } }
            )

            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = note.content,
                onValueChange = { viewModel.edit { n -> n.copy(content = it) } },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().height(300.dp),
                decorationBox = { inner ->
                    if (note.content.isEmpty()) {
                        Text(
                            "正文。",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    inner()
                }
            )

            // 只有素材类（灵感/片段/对白）有得选，才给开关。
            // 人物/设定固定参与、备忘不参与——放个点不动的开关只会骗人。
            if (NoteKind.material.contains(note.kind)) {
                Spacer(Modifier.height(18.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("参与 AI 上下文", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "进 AI 上下文",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = note.aiReference, onCheckedChange = { viewModel.setAiReference(it) })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    state.saving -> "保存中…"
                    state.savedAt != null -> "已保存"
                    else -> "改动会自动保存"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun FieldLine(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                inner()
            }
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}
