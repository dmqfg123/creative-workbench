package com.creativeworkbench.writer.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.creativeworkbench.writer.core.data.OutlineNodeEntity
import com.creativeworkbench.writer.ui.design.IconAction
import com.creativeworkbench.writer.ui.design.PillButton
import com.creativeworkbench.writer.ui.design.Tag
import com.creativeworkbench.writer.ui.design.WbCard
import com.creativeworkbench.writer.ui.outline.OutlineViewModel
import com.creativeworkbench.writer.ui.outline.OutlineViewModelFactory

/**
 * 大纲：一部作品一条有序列表，节点可以绑定到某一章。
 * AI 生成正文时只喂「本章 + 前后各一章 + 全书级」节点（见 AiContextBuilder.renderOutline），
 * 不会把整本书的大纲都塞进 token。
 */
@Composable
fun OutlineScreen(
    workId: String,
    highlightChapterId: String?,
    onBack: () -> Unit
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: OutlineViewModel = viewModel(
        key = "outline-" + workId,
        factory = OutlineViewModelFactory(application, workId)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<OutlineNodeEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<OutlineNodeEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onBack
            )
            Column(Modifier.weight(1f)) {
                Text("大纲", style = MaterialTheme.typography.titleLarge)
                Text(
                    state.workTitle.ifBlank { "作品" } + " · " + state.nodes.size + " 条",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconAction(
                icon = Icons.Default.Add,
                contentDescription = "新建大纲节点",
                tint = MaterialTheme.colorScheme.primary,
                onClick = { creating = true }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.nodes.isEmpty() && !state.loading) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 40.dp)) {
                        Text(
                            "还没有大纲",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            "一条 = 一个剧情点。AI 只带本章与相邻章节。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        PillButton(text = "新建第一条", icon = Icons.Default.Add, onClick = { creating = true })
                    }
                }
            }
            items(state.nodes, key = { it.id }) { node ->
                val index = state.nodes.indexOfFirst { it.id == node.id }
                val chapterLabel = node.chapterId?.let { id ->
                    val order = state.chapters.indexOfFirst { it.id == id }
                    if (order >= 0) "第 " + (order + 1) + " 章" else null
                }
                WbCard(contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (index + 1).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(26.dp)
                        )
                        Text(
                            node.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Tag(if (chapterLabel != null) chapterLabel else "全书", active = node.chapterId != null)
                    }
                    if (node.content.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            node.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallAction(Icons.Default.KeyboardArrowUp, "上移", index > 0) {
                            viewModel.move(node.id, -1)
                        }
                        SmallAction(Icons.Default.KeyboardArrowDown, "下移", index < state.nodes.lastIndex) {
                            viewModel.move(node.id, 1)
                        }
                        SmallAction(Icons.Default.Delete, "删除", true, destructive = true) {
                            confirmDelete = node
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { editing = node }) { Text("编辑") }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val node = editing
        OutlineEditorDialog(
            node = node,
            chapters = state.chapters,
            defaultChapterId = highlightChapterId,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { title, content, chapterId ->
                if (node != null) {
                    viewModel.update(node.id, title, content, chapterId)
                } else {
                    viewModel.create(title, content, chapterId)
                }
                creating = false
                editing = null
            }
        )
    }

    confirmDelete?.let { node ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除这条大纲？") },
            text = { Text(node.title) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(node.id)
                    confirmDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
            shape = MaterialTheme.shapes.large
        )
    }
}

@Composable
private fun SmallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun OutlineEditorDialog(
    node: OutlineNodeEntity?,
    chapters: List<com.creativeworkbench.writer.core.data.ChapterEntity>,
    defaultChapterId: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, chapterId: String?) -> Unit
) {
    var title by remember { mutableStateOf(node?.title.orEmpty()) }
    var content by remember { mutableStateOf(node?.content.orEmpty()) }
    var chapterId by remember { mutableStateOf(node?.chapterId ?: defaultChapterId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (node == null) "新建大纲节点" else "编辑大纲节点") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题（一句话说清这段写什么）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("要点") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text("绑定章节", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Tag("全书", active = chapterId == null, onClick = { chapterId = null })
                    chapters.take(30).forEachIndexed { index, chapter ->
                        Tag(
                            (index + 1).toString() + " 章",
                            active = chapterId == chapter.id,
                            onClick = { chapterId = chapter.id }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, content, chapterId) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = MaterialTheme.shapes.large
    )
}
