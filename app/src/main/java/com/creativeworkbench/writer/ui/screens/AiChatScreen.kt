package com.creativeworkbench.writer.ui.screens

import android.app.Application
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.creativeworkbench.writer.core.ai.ToolProposal
import com.creativeworkbench.writer.core.data.NoteKind
import com.creativeworkbench.writer.ui.chat.AiChatViewModel
import com.creativeworkbench.writer.ui.chat.AiChatViewModelFactory
import com.creativeworkbench.writer.ui.chat.ChatTurn
import com.creativeworkbench.writer.ui.design.SearchField
import com.creativeworkbench.writer.ui.design.Tag

/**
 * AI 对话窗口：多轮讨论 + 可选联网检索 + 结论一键存成笔记。
 * 和编辑器里的续写/改写分开：这里不改正文，只产出「可以变成笔记的判断」。
 */
@Composable
fun AiChatScreen(
    workId: String?,
    workTitle: String
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: AiChatViewModel = viewModel(
        key = "chat-" + (workId ?: "none"),
        factory = AiChatViewModelFactory(application, workId)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var importOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    // 有新内容就滚到底（流式时跟着走）
    LaunchedEffect(state.turns.size, state.turns.lastOrNull()?.text?.length) {
        if (state.turns.isNotEmpty()) {
            listState.animateScrollToItem(state.turns.lastIndex)
        }
    }

    if (importOpen) {
        com.creativeworkbench.writer.ui.textimport.TextImportSheet(
            workId = workId,
            onDismiss = { importOpen = false }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("AI 对话", style = MaterialTheme.typography.displaySmall)
                val subtitle = buildString {
                    append(if (workTitle.isBlank()) "未选作品：只聊天，不带资料" else workTitle + " · 讨论")
                    if (state.lastSearchLabel.isNotBlank()) append(" · ").append(state.lastSearchLabel)
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { viewModel.clear() },
                enabled = state.turns.isNotEmpty(),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "清空对话", modifier = Modifier.size(20.dp))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.turns.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 28.dp, start = 8.dp, end = 8.dp)) {
                        Text("想问什么就问", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "「联网」查资料，「大纲 / 笔记」带上作品资料。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(state.turns, key = { it.id }) { turn ->
                ChatBubble(
                    turn = turn,
                    onCopy = {
                        clipboard.setText(
                            AnnotatedString(com.creativeworkbench.writer.core.ai.PlainText.of(turn.text))
                        )
                    },
                    onSave = { viewModel.saveAsNote(turn, NoteKind.FRAGMENT) }
                )
            }
        }

        if (state.notice.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    state.notice,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (state.error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    state.error ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // 待确认的写入固定在输入框上方：夹在对话流里会被挤到看不见的地方，
            // 用户就不知道「它说要记下来」其实还没落库。
            if (state.proposals.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "待确认的写入 " + state.proposals.count { !it.applied } + " 条",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.proposals.count { !it.applied } > 1) {
                            TextButton(onClick = { viewModel.applyAllProposals() }) { Text("全部应用") }
                        }
                    }
                    state.proposals.forEach { proposal ->
                        ProposalCard(
                            proposal = proposal,
                            onApply = { viewModel.applyProposal(proposal.id) },
                            onDismiss = { viewModel.dismissProposal(proposal.id) }
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Tag(
                    text = "联网",
                    active = state.webEnabled,
                    onClick = { viewModel.setWebEnabled(!state.webEnabled) }
                )
                Tag(
                    text = "大纲",
                    active = state.includeOutline,
                    enabled = workId != null,
                    onClick = { viewModel.setIncludeOutline(!state.includeOutline) }
                )
                Tag(
                    text = "笔记",
                    active = state.includeNotes,
                    enabled = workId != null,
                    onClick = { viewModel.setIncludeNotes(!state.includeNotes) }
                )
                // 读写项目：勾上后 AI 能查章节/搜设定，并把讨论结果写成待确认的提案
                Tag(
                    text = "读写项目",
                    active = state.toolsEnabled && workId != null,
                    enabled = workId != null,
                    onClick = { viewModel.setToolsEnabled(!state.toolsEnabled) }
                )
                // 手上有一大段现成文字时，逐段粘进对话既贵又乱：直接走结构化导入
                Tag(
                    text = "整理文本",
                    active = false,
                    enabled = workId != null,
                    onClick = { importOpen = true }
                )
                if (state.sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
                if (state.retryNotice.isNotBlank()) {
                    Tag(state.retryNotice, active = true)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        if (draft.isEmpty()) {
                            Text(
                                "问点什么…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (draft.isNotBlank()) {
                                    viewModel.send(draft)
                                    draft = ""
                                }
                            }),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp, max = 120.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = {
                        if (draft.isNotBlank()) {
                            viewModel.send(draft)
                            draft = ""
                        }
                    },
                    shape = CircleShape,
                    color = if (draft.isBlank() || state.sending) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (draft.isBlank() || state.sending) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 一条写入提案：看得见要写什么，点「应用」才落库。 */
@Composable
private fun ProposalCard(
    proposal: ToolProposal,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    proposal.label + " · " + proposal.target,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (proposal.applied) "已应用" else "待确认",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (proposal.applied) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Text(proposal.title, style = MaterialTheme.typography.bodyMedium)
            if (proposal.preview.isNotBlank()) {
                Text(
                    proposal.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6
                )
            }
            proposal.applyError?.let { message ->
                Text(
                    "写入失败：" + message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (!proposal.applied) {
                Row {
                    TextButton(onClick = onDismiss) { Text("忽略") }
                    TextButton(onClick = onApply) { Text("应用") }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    turn: ChatTurn,
    onCopy: () -> Unit,
    onSave: () -> Unit
) {
    val isUser = turn.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.width(
                if (isUser) 300.dp else 340.dp
            )
        ) {
            Column(Modifier.padding(12.dp)) {
                if (turn.text.isBlank() && turn.streaming) {
                    Text(
                        "…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // 显示层去装饰（###、**、表格竖线），不改模型收到的内容
                    Text(
                        com.creativeworkbench.writer.core.ai.PlainText.of(turn.text),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (turn.hits.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "参考来源 " + turn.hits.size + " 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    turn.hits.take(5).forEachIndexed { index, hit ->
                        Text(
                            (index + 1).toString() + ". " + hit.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                // 工具执行记录：让用户看见「它到底查了什么、改了什么」，而不是凭空自信
                if (turn.toolRuns.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "工具 " + turn.toolRuns.size + " 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    turn.toolRuns.take(8).forEach { run ->
                        Text(
                            (if (run.ok) "· " else "× ") + run.label + "：" + run.summary,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (run.ok) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            maxLines = 2
                        )
                    }
                }
                if (!isUser && !turn.streaming && turn.text.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onSave) { Text("存为笔记") }
                        TextButton(onClick = onCopy) { Text("复制") }
                    }
                }
            }
        }
    }
}
