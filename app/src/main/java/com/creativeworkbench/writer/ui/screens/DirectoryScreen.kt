package com.creativeworkbench.writer.ui.screens

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.creativeworkbench.writer.core.data.ChapterEntity
import com.creativeworkbench.writer.ui.components.ActionSheet
import com.creativeworkbench.writer.ui.components.SheetAction
import com.creativeworkbench.writer.ui.components.pressScale
import com.creativeworkbench.writer.ui.design.EmptyState
import com.creativeworkbench.writer.ui.design.IconAction
import com.creativeworkbench.writer.ui.design.PillButton
import com.creativeworkbench.writer.ui.design.SearchField
import com.creativeworkbench.writer.ui.design.SectionLabel
import com.creativeworkbench.writer.ui.design.WbCard
import com.creativeworkbench.writer.ui.theme.Motion
import com.creativeworkbench.writer.ui.directory.DirectoryViewModel
import com.creativeworkbench.writer.ui.directory.DirectoryViewModelFactory

/**
 * 作品目录：只做「作品 → 章」两层。
 * 当前章用文字 + 左侧短线标记（不用圆点，避免被当成未读红点）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var renameChapter by remember { mutableStateOf<ChapterEntity?>(null) }
    var summaryChapter by remember { mutableStateOf<ChapterEntity?>(null) }
    var noteChapter by remember { mutableStateOf<ChapterEntity?>(null) }
    var deleteChapter by remember { mutableStateOf<ChapterEntity?>(null) }
    var menuChapter by remember { mutableStateOf<ChapterEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    if (showAdd) {
        NameDialog(
            title = "新建章节",
            initial = com.creativeworkbench.writer.core.data.ChapterNaming.next(chapters.map { it.title }),
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
        TextDialog(
            title = "章节摘要",
            hint = "100–200 字，给 AI 当最近 10 章的上下文；不写也能用。",
            initial = chapter.summary.orEmpty(),
            limit = 200,
            onDismiss = { summaryChapter = null },
            onConfirm = {
                viewModel.updateSummary(chapter.id, it)
                summaryChapter = null
            }
        )
    }
    noteChapter?.let { chapter ->
        TextDialog(
            title = "本章备注",
            hint = "给自己看的提醒，不进正文、不计字数。",
            initial = chapter.note,
            limit = 500,
            onDismiss = { noteChapter = null },
            onConfirm = {
                viewModel.updateNote(chapter.id, it)
                noteChapter = null
            }
        )
    }
    deleteChapter?.let { chapter ->
        AlertDialog(
            onDismissRequest = { deleteChapter = null },
            title = { Text("删除「" + chapter.title + "」？") },
            text = { Text("会删除正文与日志，不能撤销。建议先导出备份。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChapter(chapter.id)
                        deleteChapter = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteChapter = null }) { Text("取消") } },
            shape = MaterialTheme.shapes.large
        )
    }

    val filtered = chapters.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
    val continueChapter = chapters.firstOrNull { it.id == currentChapterId } ?: chapters.lastOrNull()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconAction(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            onClick = onBack
                        )
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                work?.title ?: "作品目录",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                chapters.size.toString() + " 章 · " + chapters.sumOf { it.wordCount } + " 字",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconAction(
                            icon = Icons.Default.Search,
                            contentDescription = "搜索章节",
                            tint = if (searching) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            onClick = {
                                searching = !searching
                                if (!searching) query = ""
                            }
                        )
                    }
                }
                if (searching) {
                    item {
                        SearchField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "搜索章节标题",
                            onClose = {
                                searching = false
                                query = ""
                            }
                        )
                    }
                }
                if (continueChapter != null && query.isBlank()) {
                    item {
                        ContinueChapterCard(
                            chapter = continueChapter,
                            onClick = { onOpenChapter(continueChapter.id) }
                        )
                    }
                }
                if (chapters.isEmpty()) {
                    item {
                        EmptyState(
                            title = "还没有章节",
                            message = "点右下角「新章节」写下第一章。",
                            actionLabel = "新建章节",
                            onAction = { showAdd = true }
                        )
                    }
                } else {
                    item { SectionLabel(if (query.isBlank()) "全部章节" else "搜索结果 · " + filtered.size) }
                    itemsIndexed(filtered, key = { _, chapter -> chapter.id }) { index, chapter ->
                        ChapterRow(
                            chapter = chapter,
                            selected = chapter.id == currentChapterId,
                            onClick = { onOpenChapter(chapter.id) },
                            onMenu = { menuChapter = chapter },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            if (chapters.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 20.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("新章节", fontWeight = FontWeight.Medium) }
                )
            }

            // 提示条抬到 FAB 上方：既不挡按钮，也不挡章节列表最后一项
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)
            )
        }
    }

    menuChapter?.let { chapter ->
        val index = chapters.indexOfFirst { it.id == chapter.id }
        ActionSheet(
            title = chapter.title,
            subtitle = chapter.wordCount.toString() + " 字",
            actions = buildList {
                add(
                    SheetAction(
                        label = "改名",
                        icon = Icons.Default.Edit,
                        onClick = { renameChapter = chapter }
                    )
                )
                add(
                    SheetAction(
                        label = "本章备注",
                        icon = Icons.Default.Edit,
                        description = "给自己看的提醒，不进正文",
                        onClick = { noteChapter = chapter }
                    )
                )
                add(
                    SheetAction(
                        label = if (chapter.summary.isNullOrBlank()) "编辑摘要" else "编辑摘要（已有）",
                        icon = Icons.Default.Edit,
                        description = "给 AI 用的最近剧情摘要",
                        onClick = { summaryChapter = chapter }
                    )
                )
                if (index > 0) {
                    add(
                        SheetAction(
                            label = "上移",
                            icon = Icons.Default.KeyboardArrowUp,
                            onClick = { viewModel.moveChapter(chapter.id, -1) }
                        )
                    )
                }
                if (index >= 0 && index < chapters.lastIndex) {
                    add(
                        SheetAction(
                            label = "下移",
                            icon = Icons.Default.KeyboardArrowDown,
                            onClick = { viewModel.moveChapter(chapter.id, 1) }
                        )
                    )
                }
                add(
                    SheetAction(
                        label = "删除章节",
                        icon = Icons.Default.Delete,
                        destructive = true,
                        onClick = { deleteChapter = chapter }
                    )
                )
            },
            onDismiss = { menuChapter = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(
    chapter: ChapterEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val background by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        androidx.compose.animation.core.tween(Motion.Medium),
        label = "chapterBackground"
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outline,
        androidx.compose.animation.core.tween(Motion.Medium),
        label = "chapterBorder"
    )
    val barHeight by androidx.compose.animation.core.animateDpAsState(
        if (chapter.summary.isNullOrBlank()) 28.dp else 40.dp,
        androidx.compose.animation.core.tween(Motion.Medium),
        label = "chapterBarHeight"
    )
    val barColor by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        androidx.compose.animation.core.tween(Motion.Medium),
        label = "chapterBarColor"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.99f)
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick,
                onLongClick = onMenu
            ),
        shape = MaterialTheme.shapes.medium,
        color = background,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .background(color = barColor, shape = RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    chapter.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    chapter.wordCount.toString() + " 字" +
                        (if (chapter.note.isNotBlank()) " · 有备注" else "") +
                        (if (chapter.summary.isNullOrBlank()) "" else " · 有摘要"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                chapter.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconAction(
                icon = Icons.Default.MoreVert,
                contentDescription = "章节操作",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onMenu
            )
        }
    }
}

@Composable
private fun ContinueChapterCard(chapter: ChapterEntity, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "继续写作",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                chapter.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                chapter.wordCount.toString() + " 字" + (if (chapter.summary.isNullOrBlank()) "" else " · 有摘要"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(12.dp))
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
private fun TextDialog(
    title: String,
    hint: String,
    initial: String,
    limit: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(limit) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    supportingText = { Text(value.length.toString() + "/" + limit) }
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = MaterialTheme.shapes.large
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
                label = { Text("标题") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) {
                Text("确定")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = MaterialTheme.shapes.large
    )
}