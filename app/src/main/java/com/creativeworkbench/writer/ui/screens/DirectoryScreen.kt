package com.creativeworkbench.writer.ui.screens

import android.app.Application
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.creativeworkbench.writer.core.data.ChapterEntity
import com.creativeworkbench.writer.ui.components.ActionSheet
import com.creativeworkbench.writer.ui.components.SheetAction
import com.creativeworkbench.writer.ui.components.pressScale
import com.creativeworkbench.writer.ui.directory.DirectoryViewModel
import com.creativeworkbench.writer.ui.directory.DirectoryViewModelFactory
import com.creativeworkbench.writer.ui.theme.Motion

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
    var menuChapter by remember { mutableStateOf<ChapterEntity?>(null) }

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
                TextButton(
                    onClick = {
                        viewModel.deleteChapter(chapter.id)
                        deleteChapter = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
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
                title = {
                    Column {
                        Text(
                            work?.title ?: "作品目录",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            chapters.size.toString() + " 章 · " + (work?.wordCount ?: 0L) + " 字",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
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
        val continueChapter = chapters.firstOrNull { it.id == currentChapterId } ?: chapters.lastOrNull()
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (continueChapter != null) {
                    item {
                        ContinueChapterCard(
                            chapter = continueChapter,
                            onClick = { onOpenChapter(continueChapter.id) }
                        )
                    }
                }
                if (chapters.isEmpty()) {
                    item {
                        Text(
                            "还没有章节，点右下角「新章节」开始。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    }
                } else {
                    item {
                        Text(
                            "全部章节",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
                        )
                    }
                    itemsIndexed(chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                        ChapterRow(
                            index = index,
                            chapter = chapter,
                            selected = chapter.id == currentChapterId,
                            onClick = { onOpenChapter(chapter.id) },
                            onMenu = { menuChapter = chapter },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新章节", fontWeight = FontWeight.Medium) }
            )
        }
    }

    menuChapter?.let { chapter ->
        ActionSheet(
            title = chapter.title,
            subtitle = chapter.wordCount.toString() + " 字",
            actions = listOf(
                SheetAction(
                    label = "改名",
                    icon = Icons.Default.Edit,
                    onClick = { renameChapter = chapter }
                ),
                SheetAction(
                    label = "编辑摘要",
                    icon = Icons.Default.Edit,
                    description = "给 AI 用的最近剧情摘要",
                    onClick = { summaryChapter = chapter }
                ),
                SheetAction(
                    label = "删除章节",
                    icon = Icons.Default.Delete,
                    destructive = true,
                    onClick = { deleteChapter = chapter }
                )
            ),
            onDismiss = { menuChapter = null }
        )
    }
}

@Composable
private fun ContinueChapterCard(chapter: ChapterEntity, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().pressScale(interaction, 0.98f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "继续写作",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                chapter.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                chapter.wordCount.toString() + " 字" + if (chapter.summary.isNullOrBlank()) "" else " · 有摘要",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "接着写",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
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
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(Motion.Medium),
        label = "chapterBg"
    )
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().pressScale(interaction, 0.99f),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(38.dp)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
            Spacer(Modifier.width(10.dp))
            Text(
                (index + 1).toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(26.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    chapter.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
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
            IconButton(onClick = onMenu, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "章节操作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
