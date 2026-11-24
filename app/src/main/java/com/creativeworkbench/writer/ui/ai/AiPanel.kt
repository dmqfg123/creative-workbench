package com.creativeworkbench.writer.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creativeworkbench.writer.core.ai.AiActions
import com.creativeworkbench.writer.core.ai.SelectionInfo
import com.creativeworkbench.writer.ui.design.GhostButton
import com.creativeworkbench.writer.ui.design.PillButton
import com.creativeworkbench.writer.ui.design.Tag

/**
 * AI 面板：动作 → 上下文预览 → 生成 → 结果 → 确认写入。
 * 写入必须二次确认；检查结果不写正文。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPanel(
    state: AiUiState,
    selection: SelectionInfo?,
    hasContent: Boolean,
    hasActiveBlock: Boolean,
    onAction: (String) -> Unit,
    onGenerate: () -> Unit,
    onOverflowRetry: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onWrite: (String) -> Unit,
    onJumpToBlock: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    var confirmWrite by remember { mutableStateOf(false) }
    val action = state.action

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
                Text("AI 助手", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.availableActions.forEach { item ->
                    Tag(
                        item.name,
                        active = action?.id == item.id,
                        onClick = {
                            // 改写没选中时落到「当前段」，由编辑器侧补选区。
                            if (item.id != AiActions.REWRITE || selection != null || hasActiveBlock) onAction(item.id)
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    selection != null -> "已选中 " + selection.text.length + " 字；改写只替换这段"
                    !hasContent -> "正文还是空的：续写会从空段开始，检查没有可检查的内容"
                    else -> "改写：先选中文字，或点正文进入某段后直接改写整段"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (state.overflow != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            state.overflow.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = onOverflowRetry) { Text("仍然发送（会省略部分设定）") }
                    }
                }
            }

            state.prepared?.preview?.let { preview ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("将发送", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(preview.summary, style = MaterialTheme.typography.bodySmall)
                        preview.notes.forEach { note ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "· " + note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (state.generating) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    Text("生成中…", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    TextButton(onClick = onCancel) { Text("取消") }
                }
            }

            if (state.resultText.isNotBlank() && !state.generating) {
                Spacer(Modifier.height(14.dp))
                if (action?.id == AiActions.REWRITE || action?.id == AiActions.CHECK) {
                    if (selection != null) {
                        Text("原文", style = MaterialTheme.typography.labelMedium)
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                selection.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                Text(
                    if (action?.id == AiActions.CHECK) "检查结果" else "生成结果",
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        state.resultText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }
            }

            if (state.issues.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("问题 " + state.issues.size + " 条（点一条跳到对应段落）", style = MaterialTheme.typography.labelMedium)
                state.issues.forEach { issue ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clickable { onJumpToBlock(issue.blockId) },
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                "[" + issue.severity + "] " + issue.message,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (issue.excerpt.isNotBlank()) {
                                Text(
                                    issue.excerpt,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.prepared != null && state.resultText.isBlank() && !state.generating) {
                    PillButton(
                        text = "生成",
                        onClick = onGenerate,
                        enabled = action != null && (action.id != AiActions.REWRITE || selection != null) &&
                            (action.id != AiActions.CHECK || hasContent)
                    )
                }
                if (state.resultText.isNotBlank() && !state.generating) {
                    GhostButton(text = "重新生成", onClick = onGenerate)
                    GhostButton(text = "复制", onClick = { clipboard.setText(AnnotatedString(state.resultText)) })
                    if (action?.id == AiActions.CONTINUE || action?.id == AiActions.REWRITE) {
                        PillButton(
                            text = if (action.id == AiActions.CONTINUE) "写入正文" else "替换选区",
                            onClick = { confirmWrite = true },
                            enabled = if (action.id == AiActions.REWRITE) selection != null else hasActiveBlock
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmWrite) {
        AlertDialog(
            onDismissRequest = { confirmWrite = false },
            title = { Text("确认写入？") },
            text = {
                Text(
                    if (action?.id == AiActions.REWRITE) "会用结果替换当前选中的文字，写入后可以撤销。"
                    else "会把结果作为新段落接在当前段之后，写入后可以撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWrite = false
                    onWrite(state.resultText)
                }) { Text("写入") }
            },
            dismissButton = { TextButton(onClick = { confirmWrite = false }) { Text("取消") } },
            shape = MaterialTheme.shapes.large
        )
    }
}
