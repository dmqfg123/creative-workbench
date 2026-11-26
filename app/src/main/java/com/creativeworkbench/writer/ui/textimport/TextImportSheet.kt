package com.creativeworkbench.writer.ui.textimport

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.creativeworkbench.writer.core.ai.StructuredItem
import com.creativeworkbench.writer.ui.design.GhostButton
import com.creativeworkbench.writer.ui.design.PillButton

/**
 * 导入文本 → 识别 → 勾选 → 写入笔记 / 大纲 / 伏笔。
 * 底栏是唯一的操作区：识别完之前不出现导入按钮，避免「点了没反应」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextImportSheet(
    workId: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: TextImportViewModel = viewModel(
        key = "text-import-" + (workId ?: "none"),
        factory = TextImportViewModelFactory(application, workId)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    decodeText(stream.readBytes())
                }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                viewModel.setError("这个文件读不出来（可能是二进制或被加密）")
            } else {
                viewModel.setText(text.take(300_000))
            }
        }
    }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("导入文本", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            Text(
                "贴一整段或整本，先识别成人物/设定/大纲/伏笔，勾选后才写进项目。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.items.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BasicTextField(
                        value = state.text,
                        onValueChange = { viewModel.setText(it) },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 220.dp)
                            .padding(12.dp)
                    )
                }
                if (state.text.isBlank()) {
                    Text(
                        "把 txt 或文档内容粘进来，也可以点下面「选择文件」。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        picker.launch(arrayOf("text/plain", "text/*", "application/octet-stream"))
                    }) { Text("选择文件") }
                    Text(
                        if (state.text.isBlank()) "" else state.text.length.toString() + " 字",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    PillButton(
                        text = if (state.running) "识别中…" else "开始识别",
                        onClick = { viewModel.run() },
                        enabled = !state.running && state.text.trim().length >= 20
                    )
                }
                if (state.running && state.progress.isNotBlank()) {
                    Text(
                        state.progress,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Text(state.notice, style = MaterialTheme.typography.labelMedium)
                state.usage?.let { usage ->
                    Text(
                        "本次消耗：输入 " + usage.promptTokens + " · 输出 " + usage.completionTokens +
                            " tokens · 约 ¥" + String.format(java.util.Locale.US, "%.4f", usage.estimatedCostCny()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                val order = listOf("人物", "设定", "灵感", "片段", "对白", "备忘", "大纲", "伏笔")
                val groups = state.items.groupBy { it.bucketLabel }
                order.forEach { label ->
                    val group = groups[label] ?: return@forEach
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label + " " + group.size + " 条",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        val allOn = group.all { it.selected }
                        Text(
                            if (allOn) "取消" else "全选",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { viewModel.toggleBucket(group.first().bucket, !allOn) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    group.forEach { item ->
                        ImportRow(item = item, onToggle = { viewModel.toggle(item.id) })
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GhostButton(text = "重新识别", onClick = { viewModel.run() })
                    PillButton(
                        text = "导入 " + state.selectedCount + " 条",
                        onClick = { viewModel.importSelected() },
                        enabled = state.selectedCount > 0
                    )
                }
            }

            state.error?.let { message ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            if (state.items.isEmpty() && state.notice.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(state.notice, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ImportRow(item: StructuredItem, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = item.selected, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodySmall)
            if (item.content.isNotBlank()) {
                Text(
                    item.content.replace('\n', ' '),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

/** txt 常见 GBK：先按 UTF-8 解，替换字符太多就换 GBK 再试。 */
private fun decodeText(bytes: ByteArray): String {
    val utf8 = String(bytes, Charsets.UTF_8)
    val bad = utf8.count { it == '\uFFFD' }
    return if (bad <= 3) utf8 else runCatching { String(bytes, charset("GBK")) }.getOrDefault(utf8)
}
