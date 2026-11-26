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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.creativeworkbench.writer.core.data.ForeshadowingEntity
import com.creativeworkbench.writer.core.data.OutlineNodeEntity
import com.creativeworkbench.writer.core.data.OutlineTemplates
import com.creativeworkbench.writer.ui.design.GhostButton
import com.creativeworkbench.writer.ui.design.IconAction
import com.creativeworkbench.writer.ui.design.PillButton
import com.creativeworkbench.writer.ui.design.Tag
import com.creativeworkbench.writer.ui.design.WbCard
import com.creativeworkbench.writer.ui.outline.OutlineViewModel
import com.creativeworkbench.writer.ui.outline.OutlineViewModelFactory

/**
 * 大纲：一部作品一条有序列表，节点可绑定章节、可设目标字数。
 * AI 生成正文时只喂「本章 + 前后各一章 + 全书级」节点；未回收伏笔强制带上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineScreen(
    workId: String,
    highlightChapterId: String?,
    onBack: () -> Unit,
    onOpenChapter: (String) -> Unit = {}
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
    var showTemplates by remember { mutableStateOf(false) }
    var showForeshadow by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    // 待删除的伏笔 id（ForeshadowSheet 的回调只给 id）
    var pendingForeshadowDelete by remember { mutableStateOf<String?>(null) }
    val outlineListState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val (index, offset) = com.creativeworkbench.writer.ui.common.ScrollMemory.get("outline-" + workId)
        if (index > 0) outlineListState.scrollToItem(index, offset)
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            com.creativeworkbench.writer.ui.common.ScrollMemory.save(
                "outline-" + workId,
                outlineListState.firstVisibleItemIndex,
                outlineListState.firstVisibleItemScrollOffset
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp),
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
                icon = Icons.Default.List,
                contentDescription = "套用模板",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { showTemplates = true }
            )
            IconAction(
                icon = Icons.Default.Add,
                contentDescription = "新建大纲节点",
                tint = MaterialTheme.colorScheme.primary,
                onClick = { creating = true }
            )
        }

        LazyColumn(
            state = outlineListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.nodes.isNotEmpty() || state.foreshadowing.isNotEmpty()) {
                item {
                    val noOutline = state.chapters.count { chapter ->
                        state.nodes.none { it.chapterId == chapter.id }
                    }
                    val noContent = state.nodes.count { it.content.isBlank() }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Tag(state.chapters.size.toString() + " 章", active = false)
                        Tag(state.nodes.size.toString() + " 条大纲", active = true)
                        if (noOutline > 0) Tag(noOutline.toString() + " 章还没大纲", active = true)
                        if (noContent > 0) Tag(noContent.toString() + " 条没写要点", active = true)
                        Tag(
                            text = if (state.openForeshadow.isEmpty()) "伏笔都收了"
                            else "未回收伏笔 " + state.openForeshadow.size,
                            active = state.openForeshadow.isNotEmpty(),
                            onClick = { showForeshadow = true }
                        )
                        Tag(
                            text = "从文本导入",
                            active = false,
                            onClick = { showImport = true }
                        )
                    }
                }
            }
            if (state.nodes.isEmpty() && !state.loading) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 32.dp)) {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton(text = "新建第一条", icon = Icons.Default.Add, onClick = { creating = true })
                            GhostButton(text = "套模板", onClick = { showTemplates = true })
                            GhostButton(text = "从文本导入", onClick = { showImport = true })
                        }
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
                        if (node.content.isBlank()) {
                            Tag("缺要点", active = false)
                            Spacer(Modifier.width(6.dp))
                        }
                        if (node.chapterId != null) {
                            Tag(
                                text = chapterLabel ?: "第 N 章",
                                active = true,
                                onClick = { onOpenChapter(node.chapterId) }
                            )
                        } else {
                            Tag("全书", active = false)
                        }
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
                    // 这条大纲涉及哪些人物/设定：同一个 MentionIndex，和正文命中口径一致
                    val involved = remember(node.id, node.content, state.notes) {
                        com.creativeworkbench.writer.core.data.MentionIndex.scan(
                            state.notes,
                            node.title + "\n" + node.content
                        )
                    }
                    if (involved.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            involved.take(5).forEach { hit ->
                                Tag(hit.note.title, active = true)
                            }
                        }
                    }
                    val chapter = node.chapterId?.let { id -> state.chapters.firstOrNull { it.id == id } }
                    val target = node.targetWords ?: 0
                    if (chapter != null || target > 0) {
                        Spacer(Modifier.height(4.dp))
                        val written = chapter?.wordCount ?: 0L
                        val over = target > 0 && written > target
                        Text(
                            buildString {
                                if (chapter != null) append("正文 ").append(written).append(" 字")
                                if (target > 0) {
                                    if (chapter != null) append(" · ")
                                    append("目标 ").append(target).append(" 字")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                over -> MaterialTheme.colorScheme.primary
                                written == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
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
            onSave = { title, content, chapterId, targetWords ->
                if (node != null) {
                    viewModel.update(node.id, title, content, chapterId, targetWords)
                } else {
                    viewModel.create(title, content, chapterId, targetWords)
                }
                creating = false
                editing = null
            }
        )
    }

    if (showTemplates) {
        TemplateSheet(
            onPick = { template ->
                viewModel.applyTemplate(template)
                showTemplates = false
            },
            onDismiss = { showTemplates = false }
        )
    }

    if (showForeshadow) {
        ForeshadowSheet(
            items = state.foreshadowing,
            chapters = state.chapters,
            onCreate = { title, detail, planted, due ->
                viewModel.addForeshadow(title, detail, planted, due)
            },
            onMarkPaid = { item, chapterId -> viewModel.markForeshadowPaid(item, chapterId) },
            onDelete = { id ->
                // 伏笔删除以前是一点就没、没有确认也没有撤销
                pendingForeshadowDelete = id
            },
            onDismiss = { showForeshadow = false }
        )
    }

    pendingForeshadowDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingForeshadowDelete = null },
            title = { Text("删除这条伏笔？") },
            text = { Text("删除后不可恢复（本机备份里可能还有）。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteForeshadow(id)
                    pendingForeshadowDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingForeshadowDelete = null }) { Text("取消") }
            },
            shape = MaterialTheme.shapes.large
        )
    }

    if (showImport) {
        com.creativeworkbench.writer.ui.textimport.TextImportSheet(
            workId = workId,
            onDismiss = { showImport = false }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateSheet(
    onPick: (OutlineTemplates.Template) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("套用模板", style = MaterialTheme.typography.titleMedium)
            Text(
                "会按章节顺序新建节点，不覆盖已有大纲。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlineTemplates.all.forEach { template ->
                WbCard(
                    modifier = Modifier.padding(vertical = 4.dp),
                    onClick = { onPick(template) },
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Text(template.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        template.note + " · " + template.beats.size + " 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForeshadowSheet(
    items: List<ForeshadowingEntity>,
    chapters: List<com.creativeworkbench.writer.core.data.ChapterEntity>,
    onCreate: (title: String, detail: String, plantedChapterId: String?, dueChapterId: String?) -> Unit,
    onMarkPaid: (ForeshadowingEntity, String?) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var adding by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("伏笔", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "未回收 " + items.count { it.status != "paid" && it.status != "abandoned" } + " 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { adding = true }) { Text("加一条") }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(items, key = { it.id }) { item ->
                    val planted = chapters.indexOfFirst { it.id == item.plantedChapterId }
                    val due = chapters.indexOfFirst { it.id == item.dueChapterId }
                    WbCard(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Tag(
                                text = when (item.status) {
                                    "paid" -> "已回收"
                                    "reinforced" -> "强化中"
                                    "abandoned" -> "弃用"
                                    else -> "待回收"
                                },
                                active = item.status != "paid" && item.status != "abandoned"
                            )
                        }
                        if (item.detail.isNotBlank()) {
                            Text(
                                item.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            buildString {
                                if (planted >= 0) append("埋在第 ").append(planted + 1).append(" 章")
                                if (due >= 0) {
                                    if (planted >= 0) append(" · ")
                                    append("计划第 ").append(due + 1).append(" 章回收")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.status != "paid") {
                                TextButton(onClick = { onMarkPaid(item, null) }) { Text("标记已回收") }
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { onDelete(item.id) }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除伏笔",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (adding) {
        ForeshadowDialog(
            chapters = chapters,
            onDismiss = { adding = false },
            onSave = { title, detail, planted, due ->
                onCreate(title, detail, planted, due)
                adding = false
            }
        )
    }
}

@Composable
private fun ForeshadowDialog(
    chapters: List<com.creativeworkbench.writer.core.data.ChapterEntity>,
    onDismiss: () -> Unit,
    onSave: (title: String, detail: String, plantedChapterId: String?, dueChapterId: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var planted by remember { mutableStateOf<String?>(null) }
    var due by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新埋伏笔") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("伏笔") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("细节（谁看到的、什么东西）") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 140.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("埋在哪章", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    chapters.take(20).forEachIndexed { index, chapter ->
                        Tag(
                            (index + 1).toString() + " 章",
                            active = planted == chapter.id,
                            onClick = { planted = chapter.id }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("计划哪章回收", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    chapters.take(20).forEachIndexed { index, chapter ->
                        Tag(
                            (index + 1).toString() + " 章",
                            active = due == chapter.id,
                            onClick = { due = chapter.id }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, detail, planted, due) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = MaterialTheme.shapes.large
    )
}

@Composable
private fun OutlineEditorDialog(
    node: OutlineNodeEntity?,
    chapters: List<com.creativeworkbench.writer.core.data.ChapterEntity>,
    defaultChapterId: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, chapterId: String?, targetWords: Int?) -> Unit
) {
    var title by remember { mutableStateOf(node?.title.orEmpty()) }
    var content by remember { mutableStateOf(node?.content.orEmpty()) }
    var chapterId by remember { mutableStateOf(node?.chapterId ?: defaultChapterId) }
    var targetWords by remember { mutableStateOf(node?.targetWords ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (node == null) "新建大纲节点" else "编辑大纲节点") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("要点") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 180.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("目标字数", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 500, 1000, 2000, 3000).forEach { value ->
                        Tag(
                            text = if (value == 0) "不设" else value.toString(),
                            active = targetWords == value,
                            onClick = { targetWords = value }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("绑定章节", style = MaterialTheme.typography.labelSmall)
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
            TextButton(onClick = {
                onSave(title, content, chapterId, targetWords.takeIf { it > 0 })
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = MaterialTheme.shapes.large
    )
}
