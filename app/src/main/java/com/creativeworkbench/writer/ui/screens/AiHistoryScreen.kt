package com.creativeworkbench.writer.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.ai.AiHistoryRecord
import com.creativeworkbench.writer.core.data.OpenChapterResult
import com.creativeworkbench.writer.core.data.UpdateSource
import com.creativeworkbench.writer.ui.design.EmptyState
import com.creativeworkbench.writer.ui.design.IconAction
import com.creativeworkbench.writer.ui.design.Tag
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI 调用历史：最近 200 条；只存摘要与预览，不存完整 prompt。
 * 写入过的记录可撤销：改写回填原文，续写删掉新插入的段。
 */
@Composable
fun AiHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WorkbenchApp
    val store = app.container.aiHistory
    val chapters = app.container.chapters
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<AiHistoryRecord>>(emptyList()) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { records = store.list() }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空 AI 历史？") },
            text = { Text("只删除调用记录，不影响正文。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        store.clear()
                        records = emptyList()
                    }
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
            shape = MaterialTheme.shapes.large
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
            Text("AI 历史", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (records.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) { Text("清空") }
            }
        }
        if (records.isEmpty()) {
            EmptyState(
                title = "还没有调用记录",
                message = "AI 调用记录，写入过的可撤销。"
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Tag(record.actionName, active = true)
                                Spacer(Modifier.padding(horizontal = 4.dp))
                                Text(
                                    formatTime(record.createdAtMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                if (record.applied) {
                                    Text(
                                        "已写入",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                record.contextSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                record.resultPreview,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(record.resultText))
                                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                }) { Text("复制") }
                                if (record.applied && record.undoMode != "none") {
                                    TextButton(onClick = {
                                        scope.launch {
                                            val message = undo(chapters, record)
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                            if (message.startsWith("已撤销")) {
                                                records = store.list()
                                            }
                                        }
                                    }) { Text("撤销这次写入") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun undo(
    chapters: com.creativeworkbench.writer.core.data.ChapterRepository,
    record: AiHistoryRecord
): String {
    val chapterId = record.chapterId ?: return "记录没有绑定章节"
    val opened = when (val result = chapters.openChapterResult(chapterId)) {
        is OpenChapterResult.Ok -> result.opened
        else -> return "章节已不存在"
    }
    val blocks = opened.content.blocks.toMutableList()
    when (record.undoMode) {
        "replace" -> {
            val index = blocks.indexOfFirst { it.id == record.undoBlockId }
            if (index < 0) return "段落已不存在"
            val text = blocks[index].text
            val start = record.undoStart
            val end = record.undoEnd
            if (start < 0 || end > text.length || text.substring(start, end) != record.undoAfter) {
                return "内容已变化，不能撤销"
            }
            blocks[index] = blocks[index].copy(
                text = text.replaceRange(start, end, record.undoBefore)
            )
        }
        "insert" -> {
            val ids = record.undoIds.toSet()
            if (ids.isEmpty()) return "这条记录没有可撤销的段落"
            val remaining = blocks.filterNot { ids.contains(it.id) }
            if (remaining.size == blocks.size) return "插入的段落已不存在或已被修改"
            blocks.clear()
            blocks.addAll(remaining.ifEmpty { listOf(com.creativeworkbench.writer.core.data.Block(java.util.UUID.randomUUID().toString(), "")) })
        }
        else -> return "这条记录不可撤销"
    }
    val saved = chapters.saveChapter(chapterId, blocks, opened.chapter.revision, UpdateSource.Restore)
    return if (saved is com.creativeworkbench.writer.core.data.SaveResult.Saved) "已撤销" else "撤销失败：内容已变化"
}

private fun formatTime(time: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(time))
