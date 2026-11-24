package com.creativeworkbench.writer.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creativeworkbench.writer.core.data.NoteEntity
import com.creativeworkbench.writer.core.data.NoteKind
import com.creativeworkbench.writer.core.data.WorkEntity
import com.creativeworkbench.writer.ui.components.ActionSheet
import com.creativeworkbench.writer.ui.components.SheetAction
import com.creativeworkbench.writer.ui.design.ChipRow
import com.creativeworkbench.writer.ui.design.EmptyState
import com.creativeworkbench.writer.ui.design.IconAction
import com.creativeworkbench.writer.ui.design.PageHeader
import com.creativeworkbench.writer.ui.design.SearchField
import com.creativeworkbench.writer.ui.design.SectionLabel
import com.creativeworkbench.writer.ui.design.Tag
import com.creativeworkbench.writer.ui.design.WbCard

/**
 * 笔记列表：六种 kind 都是同一张笔记表的视图。
 * 顶部作品 chip 只改过滤条件，不改数据；速记筛选把 memo/idea/fragment/dialogue 放一起。
 */
@Composable
fun NotesScreen(
    notes: List<NoteEntity>,
    works: List<WorkEntity>,
    currentWorkId: String?,
    pendingFilter: String?,
    onFilterConsumed: () -> Unit,
    onOpenNote: (String) -> Unit,
    onNewNote: (kind: String) -> Unit,
    onOpenLibrary: () -> Unit
) {
    var workFilter by remember { mutableStateOf<String?>(null) }
    var kindFilter by remember { mutableStateOf("all") }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(pendingFilter) {
        if (pendingFilter != null) {
            kindFilter = if (pendingFilter == "quick" || pendingFilter == "all") "quick" else pendingFilter
            onFilterConsumed()
        }
    }
    LaunchedEffect(currentWorkId) {
        if (workFilter == null && currentWorkId != null) workFilter = currentWorkId
    }

    val activeWork = works.firstOrNull { it.id == workFilter } ?: works.firstOrNull { it.id == currentWorkId }
    val filtered = notes
        .filter { note ->
            (workFilter == null || note.workId == null || note.workId == workFilter)
        }
        .filter { note ->
            when (kindFilter) {
                "all" -> true
                "quick" -> NoteKind.quick.contains(note.kind)
                else -> note.kind == kindFilter
            }
        }
        .filter { note ->
            tagFilter == null || note.tags.split(',').map { it.trim() }.any { it.equals(tagFilter, ignoreCase = true) }
        }
        .filter { note ->
            query.isBlank() ||
                note.title.contains(query.trim(), ignoreCase = true) ||
                note.content.contains(query.trim(), ignoreCase = true) ||
                note.tags.contains(query.trim(), ignoreCase = true)
        }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                PageHeader(
                    title = "笔记",
                    subtitle = filtered.size.toString() + " 条 · " + (activeWork?.title ?: "全部作品")
                ) {
                    IconAction(
                        icon = Icons.Default.Search,
                        contentDescription = "搜索笔记",
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
                        placeholder = "搜索标题、正文、标签",
                        onClose = {
                            searching = false
                            query = ""
                        }
                    )
                }
            }
            if (works.isNotEmpty()) {
                item {
                    ChipRow {
                        Tag("全部作品", active = workFilter == null, onClick = { workFilter = null })
                        works.forEach { work ->
                            Tag(work.title, active = workFilter == work.id, onClick = { workFilter = work.id })
                        }
                    }
                }
            }
            item {
                ChipRow {
                    Tag("全部", active = kindFilter == "all", onClick = { kindFilter = "all" })
                    Tag("速记", active = kindFilter == "quick", onClick = { kindFilter = "quick" })
                    NoteKind.all.forEach { kind ->
                        Tag(NoteKind.label(kind), active = kindFilter == kind, onClick = { kindFilter = kind })
                    }
                }
            }

            val allTags = notes.flatMap { note ->
                note.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            }.distinct().sorted().take(12)
            if (allTags.isNotEmpty()) {
                item {
                    ChipRow {
                        Tag("全部标签", active = tagFilter == null, onClick = { tagFilter = null })
                        allTags.forEach { tag ->
                            Tag(tag, active = tagFilter == tag, onClick = {
                                tagFilter = if (tagFilter == tag) null else tag
                            })
                        }
                    }
                }
            }

            when {
                works.isEmpty() -> item {
                    EmptyState(
                        title = "先建一部作品",
                        message = "人物和设定要挂在作品上。去书架建一部，再回来记设定。",
                        actionLabel = "去书架",
                        onAction = onOpenLibrary
                    )
                }
                filtered.isEmpty() && query.isNotBlank() -> item {
                    EmptyState(
                        title = "没找到「" + query.trim() + "」",
                        message = "换一个关键词，或者清空搜索。",
                        actionLabel = "清空搜索",
                        onAction = { query = "" }
                    )
                }
                filtered.isEmpty() -> item {
                    EmptyState(
                        title = "还没有笔记",
                        message = "人物、设定、灵感、片段、对白、备忘都放这里。写正文时可以随时插入。",
                        actionLabel = "新建笔记",
                        onAction = { showNew = true }
                    )
                }
                else -> {
                    item { SectionLabel("共 " + filtered.size + " 条") }
                    items(filtered, key = { it.id }) { note ->
                        NoteRow(note = note, onClick = { onOpenNote(note.id) })
                    }
                }
            }
        }

        if (works.isNotEmpty() && filtered.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { showNew = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新建笔记", fontWeight = FontWeight.Medium) }
            )
        }
    }

    if (showNew) {
        ActionSheet(
            title = "新建笔记",
            subtitle = "类型决定 AI 是否自动参考",
            actions = NoteKind.all.map { kind ->
                SheetAction(
                    label = NoteKind.label(kind),
                    description = when (kind) {
                        NoteKind.CHARACTER -> "人物卡；每次 AI 调用都会带上"
                        NoteKind.WORLD -> "世界观；每次 AI 调用都会带上"
                        NoteKind.MEMO -> "备忘；不参与 AI 上下文"
                        else -> "素材；勾选后才进 AI 上下文"
                    },
                    onClick = {
                        showNew = false
                        onNewNote(kind)
                    }
                )
            },
            onDismiss = { showNew = false }
        )
    }
}

@Composable
private fun NoteRow(note: NoteEntity, onClick: () -> Unit) {
    WbCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(NoteKind.label(note.kind), active = NoteKind.setting.contains(note.kind))
            Spacer(Modifier.width(10.dp))
            Text(
                note.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (note.pinned) {
                Text("置顶", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (note.content.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                note.content.replace('\n', ' '),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (note.tags.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                note.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(3).forEach { tag ->
                    Tag(tag)
                }
            }
        }
    }
}