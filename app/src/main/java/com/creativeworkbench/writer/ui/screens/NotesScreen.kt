package com.creativeworkbench.writer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creativeworkbench.writer.core.data.NoteEntity
import com.creativeworkbench.writer.core.data.NoteKind
import com.creativeworkbench.writer.ui.textimport.TextImportSheet
import com.creativeworkbench.writer.core.data.WorkEntity
import com.creativeworkbench.writer.ui.components.ActionSheet
import com.creativeworkbench.writer.ui.components.SheetAction
import com.creativeworkbench.writer.ui.design.ChipRow
import com.creativeworkbench.writer.ui.design.EmptyState
import com.creativeworkbench.writer.ui.design.IconAction
import com.creativeworkbench.writer.ui.design.PageHeader
import com.creativeworkbench.writer.ui.design.SearchField
import com.creativeworkbench.writer.ui.design.Tag
import com.creativeworkbench.writer.ui.design.WbCard
import java.util.Calendar

/** 分组口径：按创建日期（用户要的「不同日期分别管理」）/ 按最近编辑。 */
private const val GROUP_DATE = "date"
private const val GROUP_RECENT = "recent"

private data class NoteGroup(
    val key: String,
    val label: String,
    val notes: List<NoteEntity>
)

/**
 * 笔记列表：六种 kind 都是同一张笔记表的视图。
 * 0.13.0 起：支持按创建日期分组 + 多选/全选批量删除。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    notes: List<NoteEntity>,
    works: List<WorkEntity>,
    currentWorkId: String?,
    pendingFilter: String?,
    onFilterConsumed: () -> Unit,
    onOpenNote: (String) -> Unit,
    onNewNote: (kind: String) -> Unit,
    onOpenLibrary: () -> Unit,
    onDeleteNotes: (List<String>) -> Unit = {}
) {
    var workFilter by remember { mutableStateOf<String?>(null) }
    var kindFilter by remember { mutableStateOf("all") }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    var grouping by remember { mutableStateOf(GROUP_DATE) }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
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
    // 删掉/导入后把失效的选中项摘掉，避免「已选 3 条」但只剩 1 条
    LaunchedEffect(notes) {
        val alive = notes.map { it.id }.toSet()
        if (selected.any { it !in alive }) selected = selected.intersect(alive)
        if (selectMode && selected.isEmpty() && alive.isEmpty()) selectMode = false
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

    val filteredIds = filtered.map { it.id }
    val allSelected = filteredIds.isNotEmpty() && filteredIds.all { it in selected }
    val groups = remember(filtered, grouping) { groupNotes(filtered, grouping) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                if (selectMode) {
                    SelectionHeader(
                        count = selected.size,
                        allSelected = allSelected,
                        onClose = {
                            selectMode = false
                            selected = emptySet()
                        },
                        onToggleAll = {
                            selected = if (allSelected) emptySet() else filteredIds.toSet()
                        },
                        onDelete = { if (selected.isNotEmpty()) confirmDelete = true }
                    )
                } else {
                    PageHeader(
                        title = "笔记",
                        subtitle = filtered.size.toString() + " 条 · " + (activeWork?.title ?: "全部作品")
                    ) {
                        IconAction(
                            icon = Icons.Default.Check,
                            contentDescription = "多选笔记",
                            tint = MaterialTheme.colorScheme.onSurface,
                            onClick = { selectMode = true }
                        )
                        // 导入文本：识别成人物/设定/大纲/伏笔，勾选后才落库
                        TextButton(onClick = { importOpen = true }) { Text("导入") }
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
            }
            if (searching && !selectMode) {
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
            if (works.isNotEmpty() && !selectMode) {
                item {
                    ChipRow {
                        Tag("全部作品", active = workFilter == null, onClick = { workFilter = null })
                        works.forEach { work ->
                            Tag(work.title, active = workFilter == work.id, onClick = { workFilter = work.id })
                        }
                    }
                }
            }
            if (!selectMode) {
                item {
                    ChipRow {
                        Tag("全部", active = kindFilter == "all", onClick = { kindFilter = "all" })
                        Tag("速记", active = kindFilter == "quick", onClick = { kindFilter = "quick" })
                        NoteKind.all.forEach { kind ->
                            Tag(NoteKind.label(kind), active = kindFilter == kind, onClick = { kindFilter = kind })
                        }
                    }
                }
                item {
                    ChipRow {
                        Tag("按创建日期", active = grouping == GROUP_DATE, onClick = { grouping = GROUP_DATE })
                        Tag("按最近编辑", active = grouping == GROUP_RECENT, onClick = { grouping = GROUP_RECENT })
                    }
                }
            }

            val allTags = notes.flatMap { note ->
                note.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            }.distinct().sorted().take(12)
            if (allTags.isNotEmpty() && !selectMode) {
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
                        message = "先建一部作品。",
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
                        message = "人物、设定、素材都放这里。",
                        actionLabel = "新建笔记",
                        onAction = { showNew = true }
                    )
                }
                else -> {
                    groups.forEach { group ->
                        if (grouping == GROUP_DATE) {
                            stickyHeader(key = "h-" + group.key) {
                                GroupHeader(
                                    label = group.label,
                                    count = group.notes.size,
                                    selectedCount = group.notes.count { it.id in selected },
                                    onToggleAll = {
                                        val ids = group.notes.map { it.id }.toSet()
                                        selected = if (ids.all { it in selected }) selected - ids else selected + ids
                                    }
                                )
                            }
                        }
                        items(group.notes, key = { it.id }) { note ->
                            NoteRow(
                                note = note,
                                selectMode = selectMode,
                                checked = note.id in selected,
                                onClick = {
                                    if (selectMode) {
                                        selected = if (note.id in selected) selected - note.id else selected + note.id
                                    } else {
                                        onOpenNote(note.id)
                                    }
                                },
                                onLongClick = {
                                    if (!selectMode) {
                                        selectMode = true
                                        selected = setOf(note.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (works.isNotEmpty() && filtered.isNotEmpty() && !selectMode) {
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

    if (importOpen) {
        TextImportSheet(
            workId = activeWork?.id ?: currentWorkId,
            onDismiss = { importOpen = false }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除 " + selected.size + " 条笔记？") },
            text = { Text("不能撤销（已留本机备份）。") },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selected.toList()
                    confirmDelete = false
                    selectMode = false
                    selected = emptySet()
                    onDeleteNotes(ids)
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
            shape = MaterialTheme.shapes.large
        )
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
private fun SelectionHeader(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.Close, contentDescription = "退出多选")
        }
        Text(
            "已选 " + count + " 条",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onToggleAll) { Text(if (allSelected) "取消全选" else "全选") }
        IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除选中",
                tint = if (count > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

/** 日期分组头：点一下 = 选中/取消整组（配合「按日期删笔记」）。 */
@Composable
private fun GroupHeader(
    label: String,
    count: Int,
    selectedCount: Int,
    onToggleAll: () -> Unit
) {
    Surface(
        onClick = onToggleAll,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(top = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                count.toString() + " 条" + if (selectedCount > 0) " · 已选 " + selectedCount else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (selectedCount > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    note: NoteEntity,
    selectMode: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    WbCard(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectMode) {
                Checkbox(checked = checked, onCheckedChange = { onClick() })
                Spacer(Modifier.width(4.dp))
            }
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

/**
 * 分组。按创建日期用「今天 / 昨天 / 本周 / 更早」+ 具体年月日做标题；
 * 同一组内保持 DAO 的顺序（置顶优先、最近编辑在前），不重排。
 */
private fun groupNotes(notes: List<NoteEntity>, mode: String): List<NoteGroup> {
    if (mode != GROUP_DATE) {
        return listOf(NoteGroup("all", "共 " + notes.size + " 条", notes))
    }
    val byDay = LinkedHashMap<String, MutableList<NoteEntity>>()
    notes.forEach { note ->
        byDay.getOrPut(dayKey(note.createdAtMs)) { mutableListOf() }.add(note)
    }
    val sortedKeys = byDay.keys.sortedDescending()
    return sortedKeys.map { key ->
        val items = byDay.getValue(key)
        NoteGroup(key, dayLabel(key), items)
    }
}

private fun dayKey(ms: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = ms }
    return String.format(
        java.util.Locale.US,
        "%04d-%02d-%02d",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH)
    )
}

private fun dayLabel(key: String): String {
    val parts = key.split('-')
    val year = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val month = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val day = parts.getOrNull(2)?.toIntOrNull() ?: 0
    val now = Calendar.getInstance()
    val today = String.format(
        java.util.Locale.US, "%04d-%02d-%02d",
        now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
    )
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
    val yesterdayKey = String.format(
        java.util.Locale.US, "%04d-%02d-%02d",
        yesterday.get(Calendar.YEAR), yesterday.get(Calendar.MONTH) + 1, yesterday.get(Calendar.DAY_OF_MONTH)
    )
    return when {
        key == today -> "今天 " + month + "月" + day + "日"
        key == yesterdayKey -> "昨天 " + month + "月" + day + "日"
        year == now.get(Calendar.YEAR) -> month.toString() + "月" + day + "日"
        else -> year.toString() + "年" + month + "月" + day + "日"
    }
}
