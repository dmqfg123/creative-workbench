package com.creativeworkbench.writer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creativeworkbench.writer.core.data.WorkEntity
import com.creativeworkbench.writer.ui.components.ActionSheet
import com.creativeworkbench.writer.ui.components.SheetAction
import com.creativeworkbench.writer.ui.components.pressScale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val coverColors = listOf(
    Color(0xFF70816C),
    Color(0xFF9E7164),
    Color(0xFF7D728E),
    Color(0xFFB08A55),
    Color(0xFF647D86)
)

/**
 * 书架：M3 常见布局——大标题在列表里随滚动走，新建入口固定在右下角悬浮按钮，
 * 单手拿 6.6 寸手机时拇指够得到（番茄作家助手把新建放在右上角，需要够屏幕顶）。
 */
@Composable
fun LibraryScreen(
    works: List<WorkEntity>,
    onCreateWork: (String) -> Unit,
    onOpenWork: (String) -> Unit,
    onOpenDirectory: (String) -> Unit,
    onDeleteWork: (String) -> Unit
) {
    var showNewWork by remember { mutableStateOf(false) }
    var menuWork by remember { mutableStateOf<WorkEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkEntity?>(null) }
    val recent = works.firstOrNull()

    if (showNewWork) {
        NewWorkDialog(
            onDismiss = { showNewWork = false },
            onCreate = {
                onCreateWork(it)
                showNewWork = false
            }
        )
    }

    deleteTarget?.let { work ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除作品？") },
            text = { Text("将删除《" + work.title + "》的全部章节；建议先导出备份。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        onDeleteWork(work.id)
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

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LibraryHeader(count = works.size)
            }
            if (recent != null) {
                item {
                    ContinueCard(
                        work = recent,
                        onClick = { onOpenWork(recent.id) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
            if (works.isEmpty()) {
                item {
                    EmptyLibrary(onCreate = { showNewWork = true })
                }
            } else {
                item {
                    Text(
                        "全部作品",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                    )
                }
                items(works, key = { it.id }) { work ->
                    WorkCard(
                        work = work,
                        onClick = { onOpenWork(work.id) },
                        onMenu = { menuWork = work },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        // 空态里已经有主按钮，再放 FAB 就是两个入口抢同一件事，所以空态不显示 FAB。
        if (works.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { showNewWork = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新作品", fontWeight = FontWeight.Medium) }
            )
        }
    }

    menuWork?.let { work ->
        ActionSheet(
            title = work.title,
            subtitle = formatWords(work.wordCount) + " · " + work.chapterCount + " 章",
            actions = listOf(
                SheetAction(
                    label = "查看目录",
                    icon = Icons.Default.List,
                    onClick = { onOpenDirectory(work.id) }
                ),
                SheetAction(
                    label = "删除作品",
                    icon = Icons.Default.Delete,
                    destructive = true,
                    onClick = { deleteTarget = work }
                )
            ),
            onDismiss = { menuWork = null }
        )
    }
}

@Composable
private fun LibraryHeader(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text("书架", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                count.toString() + " 部作品 · 只存本机",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContinueCard(work: WorkEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().pressScale(interaction, 0.98f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "继续创作",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                work.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                work.chapterCount.toString() + " 章 · " + formatWords(work.wordCount) + " · " + formatTime(work.lastOpenedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "接着写",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(2.dp))
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
private fun WorkCard(
    work: WorkEntity,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().pressScale(interaction, 0.985f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 50.dp, height = 66.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(coverBrush(work.id)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = work.title.take(1),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFFFFAF3)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = work.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = formatWords(work.wordCount) + " · " + work.chapterCount + " 章",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatTime(work.lastOpenedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMenu, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onCreate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text("还没有作品", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "写点什么吧。所有数据只存本机，AI 可选。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Button(onClick = onCreate) { Text("新建第一部作品") }
        }
    }
}

@Composable
private fun NewWorkDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新作品") },
        text = {
            Column {
                Text(
                    "先起个书名，之后可以在目录里新建章节。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("书名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onCreate(title.trim()) }
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun coverBrush(id: String) = Brush.linearGradient(
    listOf(
        coverColors[(id.hashCode() and Int.MAX_VALUE) % coverColors.size],
        coverColors[(id.hashCode() and Int.MAX_VALUE) % coverColors.size].copy(alpha = 0.78f)
    )
)

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
