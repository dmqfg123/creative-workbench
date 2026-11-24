package com.creativeworkbench.writer.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.res.painterResource
import com.creativeworkbench.writer.R
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.creativeworkbench.writer.core.data.NoteKind
import com.creativeworkbench.writer.core.data.WorkEntity
import com.creativeworkbench.writer.core.settings.LibrarySort
import com.creativeworkbench.writer.ui.components.ActionSheet
import com.creativeworkbench.writer.ui.components.SheetAction
import com.creativeworkbench.writer.ui.components.pressScale
import com.creativeworkbench.writer.ui.design.EmptyState
import com.creativeworkbench.writer.ui.design.GhostButton
import com.creativeworkbench.writer.ui.design.IconAction
import com.creativeworkbench.writer.ui.design.PageHeader
import com.creativeworkbench.writer.ui.design.PillButton
import com.creativeworkbench.writer.ui.design.SearchField
import com.creativeworkbench.writer.ui.design.SectionLabel
import com.creativeworkbench.writer.ui.design.Tag
import com.creativeworkbench.writer.ui.design.WbCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val coverColors = listOf(
    Color(0xFF8E7B63),
    Color(0xFF9C6B58),
    Color(0xFF7C7F6B),
    Color(0xFFA38A5F),
    Color(0xFF6F7B84),
    Color(0xFF8A6B7A)
)

/**
 * 书架：作品是唯一入口。个人工具，不做运营位、不做数据看板。
 * 速记固定在右下；新建作品是列表末尾的一张卡，不占悬浮位。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    works: List<WorkEntity>,
    sort: LibrarySort,
    onCreateWork: (String) -> Unit,
    onOpenWork: (String) -> Unit,
    onOpenDirectory: (String) -> Unit,
    onRenameWork: (String, String) -> Unit,
    onDeleteWork: (String) -> Unit,
    onSaveQuickNote: (kind: String, content: String, tag: String?) -> Unit,
    onOpenNotes: (String?) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onSortChange: (LibrarySort) -> Unit
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showSort by remember { mutableStateOf(false) }
    var showNewWork by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<WorkEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkEntity?>(null) }
    var menuWork by remember { mutableStateOf<WorkEntity?>(null) }
    var showQuickNote by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    // 删除 5 秒内可撤销：先隐藏，5 秒后才真正删。
    LaunchedEffect(pendingDeleteId) {
        val id = pendingDeleteId ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = "已删除，5 秒内可撤销",
            actionLabel = "撤销",
            duration = androidx.compose.material3.SnackbarDuration.Long
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            pendingDeleteId = null
        } else {
            onDeleteWork(id)
            pendingDeleteId = null
        }
    }

    val filtered = works
        .filter { pendingDeleteId != it.id }
        .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
    val sorted = when (sort) {
        LibrarySort.Recent -> filtered.sortedByDescending { it.lastOpenedAt }
        LibrarySort.Created -> filtered.sortedByDescending { it.createdAt }
        LibrarySort.Title -> filtered.sortedBy { it.title }
        LibrarySort.Words -> filtered.sortedByDescending { it.wordCount }
    }
    val recent = works.filter { pendingDeleteId != it.id }.maxByOrNull { it.lastOpenedAt }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PageHeader(
                    title = "书架",
                    subtitle = works.count { it.id != pendingDeleteId }.toString() + " 部作品 · 只存本机"
                ) {
                    IconAction(
                        icon = Icons.Default.Search,
                        contentDescription = "搜索作品",
                        tint = if (searching) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            searching = !searching
                            if (!searching) query = ""
                        }
                    )
                    androidx.compose.material3.IconButton(
                        onClick = { showSort = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_sort),
                            contentDescription = "排序",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (searching) {
                item {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "搜索书名",
                        onClose = {
                            searching = false
                            query = ""
                        }
                    )
                }
            }

            if (works.isEmpty()) {
                item {
                    WbCard(contentPadding = PaddingValues(0.dp)) {
                        EmptyState(
                            title = "还没有作品",
                            message = "所有数据只存本机，AI 可选。先建一部作品，写第一章。",
                            actionLabel = "新建第一部作品",
                            onAction = { showNewWork = true }
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 20.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(onClick = onOpenSettings) { Text("去设置 AI") }
                        }
                    }
                }
            } else {
                // 只有一部作品时，列表本身就能继续写；再放一张大卡是重复信息。
                if (recent != null && query.isBlank() && works.size >= 2) {
                    item(key = "continue") {
                        ContinueCard(work = recent, onClick = { onOpenWork(recent.id) })
                    }
                }
                item(key = "label") {
                    SectionLabel(
                        if (query.isBlank()) "全部作品" else "搜索结果 · " + sorted.size
                    )
                }
                items(sorted, key = { it.id }) { work ->
                    WorkRow(
                        work = work,
                        onClick = { onOpenWork(work.id) },
                        onMenu = { menuWork = work }
                    )
                }
                if (query.isBlank()) {
                    item(key = "new") {
                        NewWorkCard(onClick = { showNewWork = true })
                    }
                } else if (sorted.isEmpty()) {
                    item(key = "noresult") {
                        EmptyState(
                            title = "没找到「" + query.trim() + "」",
                            message = "换个关键词，或者清空搜索。",
                            actionLabel = "清空搜索",
                            onAction = { query = "" }
                        )
                    }
                }
            }
        }

        if (works.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { showQuickNote = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                text = { Text("速记", fontWeight = FontWeight.Medium) }
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)
        )
    }

    if (showSort) {
        ActionSheet(
            title = "排序",
            actions = LibrarySort.entries.map { option ->
                SheetAction(
                    label = sortLabel(option),
                    icon = if (option == sort) Icons.Default.Check else null,
                    onClick = { onSortChange(option) }
                )
            },
            onDismiss = { showSort = false }
        )
    }

    if (showNewWork) {
        NameDialog(
            title = "新作品",
            label = "书名",
            hint = "只填书名；创建后自动建第一章。",
            initial = "",
            confirmLabel = "创建",
            onDismiss = { showNewWork = false },
            onConfirm = {
                onCreateWork(it)
                showNewWork = false
            }
        )
    }

    renameTarget?.let { work ->
        NameDialog(
            title = "修改书名",
            label = "书名",
            hint = null,
            initial = work.title,
            confirmLabel = "保存",
            onDismiss = { renameTarget = null },
            onConfirm = {
                onRenameWork(work.id, it)
                renameTarget = null
            }
        )
    }

    menuWork?.let { work ->
        ActionSheet(
            title = work.title,
            subtitle = formatWords(work.wordCount) + " · " + work.chapterCount + " 章",
            actions = listOf(
                SheetAction(
                    label = "作品目录",
                    icon = Icons.Default.List,
                    onClick = { onOpenDirectory(work.id) }
                ),
                SheetAction(
                    label = "修改书名",
                    icon = Icons.Default.Edit,
                    onClick = { renameTarget = work }
                ),
                SheetAction(
                    label = "删除作品",
                    icon = Icons.Default.Delete,
                    destructive = true,
                    description = "5 秒内可撤销；删除前建议先导出备份",
                    onClick = {
                        deleteTarget = work
                    }
                )
            ),
            onDismiss = { menuWork = null }
        )
    }

    deleteTarget?.let { work ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除《" + work.title + "》？") },
            text = { Text("会删除这部作品的 " + work.chapterCount + " 章正文与日志。5 秒内可以撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        pendingDeleteId = work.id
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    if (showQuickNote) {
        QuickNoteSheet(
            onDismiss = { showQuickNote = false },
            onSave = { kind, content, tag ->
                onSaveQuickNote(kind, content, tag)
                showQuickNote = false
            },
            onViewAll = {
                showQuickNote = false
                onOpenNotes("quick")
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkRow(work: WorkEntity, onClick: () -> Unit, onMenu: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.985f)
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick,
                onLongClick = onMenu
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cover(work = work, width = 44.dp, height = 58.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    work.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    formatWords(work.wordCount) + " · " + work.chapterCount + " 章 · " + formatTime(work.lastOpenedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconAction(
                icon = Icons.Default.MoreVert,
                contentDescription = "作品操作",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onMenu
            )
        }
    }
}

@Composable
private fun ContinueCard(work: WorkEntity, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(work = work, width = 52.dp, height = 70.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "继续创作",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    work.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    formatWords(work.wordCount) + " · " + work.chapterCount + " 章 · " + formatTime(work.lastOpenedAt),
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
}

@Composable
private fun NewWorkCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "新作品",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun Cover(work: WorkEntity, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    val color = coverColors[(work.id.hashCode() and Int.MAX_VALUE) % coverColors.size]
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .background(
                Brush.verticalGradient(listOf(color, color.copy(alpha = 0.82f))),
                RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            work.title.take(1),
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFFFFF8EC)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickNoteSheet(
    onDismiss: () -> Unit,
    onSave: (kind: String, content: String, tag: String?) -> Unit,
    onViewAll: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(NoteKind.IDEA) }
    var tag by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("速记", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onViewAll) { Text("查看全部") }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag("备忘", active = tab == 0, onClick = { tab = 0 })
                Tag("灵感", active = tab == 1, onClick = { tab = 1 })
            }
            Spacer(Modifier.height(14.dp))
            if (tab == 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag("灵感", active = kind == NoteKind.IDEA, onClick = { kind = NoteKind.IDEA })
                    Tag("片段", active = kind == NoteKind.FRAGMENT, onClick = { kind = NoteKind.FRAGMENT })
                    Tag("对白", active = kind == NoteKind.DIALOGUE, onClick = { kind = NoteKind.DIALOGUE })
                    Tag("场景", active = tag == "场景", onClick = {
                        kind = NoteKind.IDEA
                        tag = if (tag == "场景") null else "场景"
                    })
                    Tag("资料", active = tag == "资料", onClick = {
                        kind = NoteKind.IDEA
                        tag = if (tag == "资料") null else "资料"
                    })
                }
                Spacer(Modifier.height(12.dp))
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(Modifier.fillMaxWidth().height(150.dp).padding(14.dp)) {
                    if (text.isEmpty()) {
                        Text(
                            if (tab == 0) "记一笔，保存为备忘" else "抓住这个念头，保存为素材",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PillButton(
                    text = "保存",
                    enabled = text.isNotBlank(),
                    onClick = {
                        val saveKind = if (tab == 0) NoteKind.MEMO else kind
                        onSave(saveKind, text, tag)
                        scope.launch { sheetState.hide() }
                    }
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    label: String,
    hint: String?,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (hint != null) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                }
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )
            }
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        shape = MaterialTheme.shapes.large
    )
}

private fun sortLabel(sort: LibrarySort): String = when (sort) {
    LibrarySort.Recent -> "最近编辑"
    LibrarySort.Created -> "创建时间"
    LibrarySort.Title -> "书名"
    LibrarySort.Words -> "字数"
}

private fun formatWords(count: Long): String =
    if (count >= 10_000L) {
        String.format(Locale.CHINA, "%.1f 万字", count / 10_000.0)
    } else {
        count.toString() + " 字"
    }

private fun formatTime(time: Long): String {
    if (time <= 0L) return "未打开"
    return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(time))
}
