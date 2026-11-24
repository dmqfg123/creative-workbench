package com.creativeworkbench.writer.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creativeworkbench.writer.core.data.WorkEntity
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

@Composable
fun LibraryScreen(
    works: List<WorkEntity>,
    onCreateWork: (String) -> Unit,
    onOpenWork: (String) -> Unit,
    onDeleteWork: (String) -> Unit
) {
    var showNewWork by remember { mutableStateOf(false) }

    if (showNewWork) {
        NewWorkDialog(
            onDismiss = { showNewWork = false },
            onCreate = {
                onCreateWork(it)
                showNewWork = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("书架", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = works.size.toString() + " 部作品 · 只存本机",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (works.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("还没有作品", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "所有数据只存本机，AI 可选。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = { showNewWork = true }) {
                            Text("新建第一部作品")
                        }
                    }
                }
            }
        } else {
            items(works, key = { it.id }) { work ->
                WorkCard(
                    work = work,
                    onClick = { onOpenWork(work.id) },
                    onDelete = { onDeleteWork(work.id) }
                )
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showNewWork = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "＋ 新作品",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkCard(work: WorkEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除作品？") },
            text = { Text("将删除《" + work.title + "》的全部章节；建议先导出备份。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = 68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(coverColors[(work.id.hashCode() and Int.MAX_VALUE) % coverColors.size]),
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
            Box {
                TextButton(onClick = { menuOpen = true }) { Text("…") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            menuOpen = false
                            confirmDelete = true
                        }
                    )
                }
            }
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
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("书名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
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
