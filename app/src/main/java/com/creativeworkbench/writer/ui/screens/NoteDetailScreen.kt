package com.creativeworkbench.writer.ui.screens

import android.app.Application
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
import com.creativeworkbench.writer.core.data.NoteKind
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
    val application = LocalContext.current.applicationContext as Application
    val actualId = remember(noteId) { if (noteId == "new") newNoteId() else noteId }
    val viewModel: NoteDetailViewModel = viewModel(
        key = "note-" + actualId,
        factory = NoteDetailViewModelFactory(application, actualId, workId, kind)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val note = state.note

    DisposableEffect(Unit) {
        onDispose { viewModel.flush() }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条笔记？") },
            text = { Text("删除后不能恢复；备份里仍可能有旧副本。") },
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
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NoteKind.all.forEach { option ->
                    Tag(
                        text = NoteKind.label(option),
                        active = note.kind == option,
                        onClick = { viewModel.setKind(option) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            BasicTextField(
                value = note.title,
                onValueChange = { viewModel.edit { n -> n.copy(title = it) } },
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
                placeholder = "别名，用逗号分隔；AI 出现即注入会用到",
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
                            "正文。人物、设定、灵感、片段、对白、备忘都写在这里。",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    inner()
                }
            )

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
                        Text(
                            if (note.kind == NoteKind.MEMO) "不参与 AI 上下文" else "参与 AI 上下文",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            if (NoteKind.setting.contains(note.kind)) {
                                "设定类固定参与，不能关闭"
                            } else if (note.kind == NoteKind.MEMO) {
                                "备忘不参与 AI 上下文"
                            } else {
                                "打开后，AI 调用会带上这条素材"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (note.kind == NoteKind.MEMO) {
                        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Switch(
                            checked = NoteKind.setting.contains(note.kind) || note.aiReference,
                            enabled = !NoteKind.setting.contains(note.kind),
                            onCheckedChange = { viewModel.setAiReference(it) }
                        )
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
