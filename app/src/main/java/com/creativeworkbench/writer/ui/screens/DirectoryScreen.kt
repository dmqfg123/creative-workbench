package com.creativeworkbench.writer.ui.screens

import android.app.Application
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.creativeworkbench.writer.core.data.ChapterEntity
import com.creativeworkbench.writer.ui.directory.DirectoryViewModel
import com.creativeworkbench.writer.ui.directory.DirectoryViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryScreen(
    workId: String,
    currentChapterId: String?,
    onOpenChapter: (String) -> Unit,
    onBack: () -> Unit
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: DirectoryViewModel = viewModel(
        key = "directory-" + workId,
        factory = DirectoryViewModelFactory(application, workId)
    )
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val work by viewModel.work.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var renameChapter by remember { mutableStateOf<ChapterEntity?>(null) }
    var summaryChapter by remember { mutableStateOf<ChapterEntity?>(null) }
    var deleteChapter by remember { mutableStateOf<ChapterEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    if (showAdd) {
        NameDialog(
            title = "新建章节",
            initial = "第" + (chapters.size + 1) + "章",
            onDismiss = { showAdd = false },
            onConfirm = {
                viewModel.addChapter(it)
                showAdd = false
            }
        )
    }
    renameChapter?.let { chapter ->
        NameDialog(
            title = "章节改名",
            initial = chapter.title,
            onDismiss = { renameChapter = null },
            onConfirm = {
                viewModel.renameChapter(chapter.id, it)
                renameChapter = null
            }
        )
    }
    summaryChapter?.let { chapter ->
        SummaryDialog(
            chapter = chapter,
            onDismiss = { summaryChapter = null },
            onConfirm = {
                viewModel.updateSummary(chapter.id, it)
                summaryChapter = null
            }
        )
    }
    deleteChapter?.let { chapter ->
        AlertDialog(
            onDismissRequest = { deleteChapter = null },
            title = { Text("删除章节？") },
            text = { Text("将删除「" + chapter.title + "」的正文与日志。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChapter(chapter.id)
                    deleteChapter = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteChapter = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(work?.title ?: "作品目录") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                }
            )
        }
    ) { padding ->
        val continueChapter = chapters.firstOrNull { it.id == currentChapterId } ?: chapters.lastOrNull()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            work?.title ?: "作品目录",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            chapters.size.toString() + " 章 · " + (work?.wordCount ?: 0L) + " 字",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                enabled = continueChapter != null,
                                onClick = { continueChapter?.let { onOpenChapter(it.id) } }
                            ) { Text("继续写作") }
                            TextButton(onClick = { showAdd = true }) { Text("新建章节") }
                        }
                    }
                }
            }
            if (chapters.isEmpty()) {
                item {
                    Text(
                        "还没有章节，点「新建章节」开始。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            } else {
                itemsIndexed(chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                    ChapterRow(
                        index = index,
                        chapter = chapter,
                        selected = chapter.id == currentChapterId,
                        onClick = { onOpenChapter(chapter.id) },
                        onRename = { renameChapter = chapter },
                        onSummary = { summaryChapter = chapter },
                        onDelete = { deleteChapter = chapter }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(
    index: Int,
    chapter: ChapterEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onSummary: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (index + 1).toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(30.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    chapter.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    chapter.wordCount.toString() + " 字",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                chapter.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Box {
                TextButton(onClick = { menu = true }) { Text("…") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("改名") },
                        onClick = { menu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("编辑摘要") },
                        onClick = { menu = false; onSummary() }
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryDialog(
    chapter: ChapterEntity,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(chapter.summary.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("章节摘要") },
        text = {
            Column {
                Text(
                    "100–200 字，用于 AI 最近 10 章上下文；不写也能用。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(200) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(value.length.toString() + "/200") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("标题") }
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
