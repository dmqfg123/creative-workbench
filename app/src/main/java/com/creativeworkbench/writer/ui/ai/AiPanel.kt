package com.creativeworkbench.writer.ui.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.creativeworkbench.writer.core.ai.AiActions
import com.creativeworkbench.writer.core.ai.AutoNotePick
import com.creativeworkbench.writer.core.ai.BodyRange
import com.creativeworkbench.writer.core.ai.NoteCandidate
import com.creativeworkbench.writer.core.ai.NoteContextMode
import com.creativeworkbench.writer.core.ai.SelectionInfo
import com.creativeworkbench.writer.core.data.NoteEntity
import com.creativeworkbench.writer.core.data.NoteKind
import com.creativeworkbench.writer.ui.design.GhostButton
import com.creativeworkbench.writer.ui.design.PillButton
import com.creativeworkbench.writer.ui.design.Tag
import com.creativeworkbench.writer.ui.theme.Motion

/**
 * AI 面板：动作 → 上下文预览 → 生成 → 结果 → 确认写入。
 * 写入必须二次确认；检查结果不写正文。
 *
 * 0.13.0：
 * - 删掉原来那两行功能说明小字（它们和生成结果挤在一起）；
 * - 上下文来源（正文/大纲/笔记）与正文范围改成可点的 chip；
 * - 引用笔记支持全选/清空；
 * - 生成过程显示实时字数、思考字数、自动续写次数。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPanel(
    state: AiUiState,
    stale: Boolean,
    selection: SelectionInfo?,
    hasContent: Boolean,
    hasActiveBlock: Boolean,
    onAction: (String) -> Unit,
    onGenerate: () -> Unit,
    onOverflowRetry: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onWrite: (String) -> Unit,
    onJumpToBlock: (String) -> Unit,
    /** 续写字数（300/600/1000）。 */
    onLength: (Int) -> Unit = {},
    /** 结果区直接编辑（改完再写入）。 */
    onResultEdit: (String) -> Unit = {},
    /** 可以手动取消引用的设定/素材。 */
    contextNotes: List<NoteEntity> = emptyList(),
    excludedNoteIds: Set<String> = emptySet(),
    onToggleNote: (String) -> Unit = {},
    onBodyRange: (BodyRange) -> Unit = {},
    onToggleBody: (Boolean) -> Unit = {},
    onToggleOutline: (Boolean) -> Unit = {},
    onToggleNotes: (Boolean) -> Unit = {},
    onToggleAllNotes: (Boolean) -> Unit = {},
    /** 设定挑选模式：自动 / 全部 / 手动 / 关。 */
    onNoteMode: (NoteContextMode) -> Unit = {},
    /** 「本章摘要」写入 chapter.summary（不是正文）。 */
    onWriteSummary: (String) -> Unit = {},
    /** 大纲为空时点「大纲 0」直接去大纲页（否则那个 chip 只是个没用的标签）。 */
    onOpenOutline: () -> Unit = {},
    /** 把摘要存成本章的大纲节点（反向大纲）。 */
    onSaveAsOutlineNode: () -> Unit = {},
    onToggleFollowOutline: (Boolean) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val contentScroll = rememberScrollState()
    val clipboard = LocalClipboardManager.current
    // 生成结束后把结果滚进视野（结果在面板下方，不滚就得手动找）
    LaunchedEffect(state.generating, state.resultText.isNotBlank()) {
        if (!state.generating && state.resultText.isNotBlank()) {
            kotlinx.coroutines.delay(150)
            contentScroll.animateScrollTo(contentScroll.maxValue)
        }
    }
    var confirmWrite by remember { mutableStateOf(false) }
    var confirmSummary by remember { mutableStateOf(false) }
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
                .verticalScroll(contentScroll)
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
                    // 改写：没选中文字、也没点进任何段落时，这个动作做不了——置灰，别让人点了没反应。
                    val needsSelection = item.id == AiActions.REWRITE || item.id == AiActions.EXPAND
                    val usable = !needsSelection || selection != null || hasActiveBlock
                    Tag(
                        item.name,
                        active = action?.id == item.id,
                        enabled = usable,
                        onClick = { onAction(item.id) }
                    )
                }
            }

            if (action?.id == AiActions.CONTINUE || action?.id == AiActions.EXPAND) {
                Spacer(Modifier.height(10.dp))
                ChipLine(if (action?.id == AiActions.EXPAND) "扩写到" else "续写长度") {
                    listOf(300, 600, 1000).forEach { len ->
                        Tag(
                            text = len.toString() + " 字",
                            active = state.lengthChars == len,
                            onClick = { onLength(len) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            // 来源 chip 带条数：勾了「大纲」但大纲是空的，以前是静默失效（模型收到的就是空），
            // 现在会直接显示「大纲 0」，用户一眼知道该去建大纲。
            val outlineTokens = state.prepared?.preview?.blocks
                ?.firstOrNull { it.label == "大纲" }?.tokens ?: 0
            val noteCount = contextNotes.count { it.id !in excludedNoteIds }
            ChipLine("参考来源") {
                Tag(
                    text = "正文",
                    active = state.includeBody,
                    onClick = { onToggleBody(!state.includeBody) }
                )
                Tag(
                    text = if (state.includeOutline) "大纲 " + (if (outlineTokens > 0) "有" else "0") else "大纲 关",
                    active = state.includeOutline && outlineTokens > 0,
                    onClick = {
                        // 还没写大纲：别只是显示 0，点一下直接去建
                        if (state.includeOutline && outlineTokens == 0) onOpenOutline()
                        else onToggleOutline(!state.includeOutline)
                    }
                )
            }

            // 设定怎么带：默认「自动」——AI 按这一章要写的东西自己挑。
            // 以前是「全部默认勾上、用户逐条取消」，设定一多每次都要先做一遍减法。
            Spacer(Modifier.height(8.dp))
            ChipLine("设定") {
                NoteContextMode.entries.forEach { mode ->
                    Tag(
                        text = if (mode == NoteContextMode.Manual) mode.label + " " + noteCount else mode.label,
                        active = state.noteMode == mode,
                        onClick = { onNoteMode(mode) }
                    )
                }
            }

            // 有选区时「正文范围」没有意义（改的就是选中的那段）——只隐藏无用项，不加任何说明文字
            val selectionMode = (action?.id == AiActions.REWRITE || action?.id == AiActions.EXPAND) &&
                state.selectionChars > 0
            if (!selectionMode && action?.id != AiActions.CHECK &&
                action?.id != AiActions.SUMMARY && state.includeBody
            ) {
                Spacer(Modifier.height(8.dp))
                ChipLine("正文范围") {
                    BodyRange.entries.forEach { range ->
                        Tag(
                            text = range.shortLabel,
                            active = state.bodyRange == range,
                            onClick = { onBodyRange(range) }
                        )
                    }
                }
            }

            if (action?.id == AiActions.CONTINUE || action?.id == AiActions.EXPAND) {
                Spacer(Modifier.height(8.dp))
                ChipLine("边界") {
                    Tag(
                        text = "按大纲推进",
                        active = state.followOutline,
                        enabled = state.includeOutline,
                        onClick = { onToggleFollowOutline(!state.followOutline) }
                    )
                }
            }

            if (state.noteMode == NoteContextMode.Auto && state.includeNotes) {
                Spacer(Modifier.height(6.dp))
                AutoNoteSummary(
                    pick = state.notePick,
                    selecting = state.noteSelecting,
                    poolSize = contextNotes.size,
                    excluded = excludedNoteIds,
                    onToggle = onToggleNote
                )
            } else if (state.noteMode == NoteContextMode.Manual && contextNotes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ContextNotePicker(
                    notes = contextNotes,
                    excluded = excludedNoteIds,
                    onToggle = onToggleNote,
                    onToggleAll = onToggleAllNotes
                )
            } else if (state.noteMode == NoteContextMode.All && contextNotes.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "全部设定 " + contextNotes.size + " 条（超预算的部分会在下面「将发送」里列出）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (preview.sources.isEmpty()) {
                                Tag("只有任务描述", active = true)
                            }
                            preview.sources.forEach { source -> Tag(source, active = true) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(preview.summary, style = MaterialTheme.typography.bodySmall)
                        preview.blocks.filter { it.tokens > 0 }.forEach { block ->
                            val ratio = if (block.cap > 0) block.tokens.toFloat() / block.cap else 0f
                            Text(
                                "· " + block.label + " " + block.tokens + " / " + block.cap + " tokens",
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    ratio >= 1f -> MaterialTheme.colorScheme.error
                                    ratio >= 0.8f -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (preview.budget > 0) {
                            Text(
                                "预算 " + preview.budget + " · 已用 " + preview.used + " · 余 " +
                                    (preview.budget - preview.used).coerceAtLeast(0),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (preview.excluded.isNotEmpty()) {
                            preview.excluded.groupBy { it.kind }.forEach { (kind, items) ->
                                Text(
                                    "· 未发送 " + kind + " " + items.size + " 条（超出预算）：" +
                                        items.map { it.label }.take(6).joinToString("、"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                    Text(
                        buildString {
                            append(if (state.thinkingChars > 0) "正在思考… " else "生成中… ")
                            if (state.resultText.isNotBlank()) append(state.resultText.length).append(" 字")
                            if (state.continuedRounds > 0) {
                                append(" · 已自动续写 ").append(state.continuedRounds).append(" 次")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    TextButton(onClick = onCancel) { Text("停止") }
                }
            }

            if (state.retryNotice.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Tag(state.retryNotice, active = true)
            }

            if (state.continuedRounds > 0 && !state.generating) {
                Spacer(Modifier.height(8.dp))
                Tag("已自动续写 " + state.continuedRounds + " 次（防止截断）", active = true)
            }

            if (state.partialFailure && state.resultText.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        "生成中途失败了：下面只是半截内容，可以复制，不能写入正文。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (stale && state.resultText.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        "正文已改过：只能复制，重新生成后可写入。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = state.resultText.isNotBlank(),
                enter = fadeIn(tween(Motion.Medium)) +
                    slideInVertically(tween(Motion.Medium, easing = Motion.EmphasizedDecelerate)) { it / 10 },
                exit = fadeOut(tween(Motion.Fast))
            ) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    if ((action?.id == AiActions.REWRITE || action?.id == AiActions.CHECK) && selection != null) {
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
                    Text(
                        (if (action?.id == AiActions.CHECK) "检查结果" else "生成结果") +
                            " · 已 " + state.resultText.length + " 字",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        // 直接在结果区改：想删一句、改个词，不用先写入正文再回头改。
                        androidx.compose.foundation.text.BasicTextField(
                            value = state.resultText,
                            onValueChange = onResultEdit,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            // 不再给结果框设 320dp 上限 + 内部滚动：
                            // 那会让长结果在框底被切一刀，看起来就像「生成被截断了」。
                            // 整块内容交给外层弹层滚动，一次能看完整段。
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
            }

            state.usage?.let { usage ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "本次 tokens：prompt " + usage.promptTokens +
                        (if (usage.hasCacheInfo) "（缓存命中 " + usage.cacheHitTokens +
                            "，" + (usage.cacheHitRate * 100).toInt() + "%）" else "") +
                        " · 输出 " + usage.completionTokens +
                        " · 约 ¥" + String.format(java.util.Locale.US, "%.4f", usage.estimatedCostCny()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            // 可横滚：按钮多了（重新生成/复制/写入摘要/存成大纲）会被屏幕裁掉最后一个
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                    // 摘要不是写正文，而是回填到「章节摘要」——下一章 AI 靠它知道前面发生了什么
                    if (action?.id == AiActions.SUMMARY) {
                        PillButton(
                            text = "写入本章摘要",
                            onClick = { confirmSummary = true },
                            enabled = !stale
                        )
                        // 反向大纲：把刚生成的摘要直接存成这一章的大纲节点，
                        // 写完一章就有一条「实际写了什么」，下一章生成时能对上计划
                        GhostButton(text = "存成大纲", onClick = onSaveAsOutlineNode)
                    }
                    val write = action?.write
                    if (write != null && write != com.creativeworkbench.writer.core.ai.WriteTarget.None) {
                        val replaces = write == com.creativeworkbench.writer.core.ai.WriteTarget.BodyReplaceSelection
                        // 续写写入不要求「当前有活动段落」：没有活动段落就接在最后一段之后。
                        // 之前这里要求 hasActiveBlock，用户没点进任何段落时按钮是灰的、点了没反应，
                        // 看起来就是「生成好了却写不进去」。
                        PillButton(
                            text = when {
                                state.partialFailure -> "生成中断，只能复制"
                                replaces -> "替换选区"
                                hasActiveBlock -> "写入正文"
                                else -> "写入正文（接在最后）"
                            },
                            onClick = { confirmWrite = true },
                            // 生成中途失败时只有半句话，绝不能写进正文
                            enabled = !stale && !state.partialFailure &&
                                if (replaces) selection != null else true
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmSummary) {
        AlertDialog(
            onDismissRequest = { confirmSummary = false },
            title = { Text("写入本章摘要？") },
            text = { Text("会覆盖这一章原有的摘要，写正文不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSummary = false
                    onWriteSummary(state.resultText)
                }) { Text("写入") }
            },
            dismissButton = { TextButton(onClick = { confirmSummary = false }) { Text("取消") } },
            shape = MaterialTheme.shapes.large
        )
    }

    if (confirmWrite) {
        AlertDialog(
            onDismissRequest = { confirmWrite = false },
            title = { Text("确认写入？") },
            text = {
                Text(
                    if (action?.id == AiActions.REWRITE || action?.id == AiActions.EXPAND) {
                        "替换选中文字，可撤销。"
                    } else if (hasActiveBlock) {
                        "接在当前段之后，可撤销。"
                    } else {
                        "接在最后一段之后，可撤销。"
                    }
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

/** 一行「标题 + chips」：横向可滚，标题不换行。 */
@Composable
private fun ChipLine(label: String, chips: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            chips()
        }
    }
}

/**
 * 自动模式的挑选结果：选了哪几条、为什么选、多少 token。
 * 点「调整」可以看整个候选池并临时取消某条（会话内有效，不改模式、不落盘）。
 */
@Composable
private fun AutoNoteSummary(
    pick: AutoNotePick?,
    selecting: Boolean,
    poolSize: Int,
    excluded: Set<String>,
    onToggle: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val chosen: List<NoteCandidate> = pick?.chosen.orEmpty()
    val active = chosen.filter { it.note.id !in excluded }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    selecting -> "正在按这一章挑设定…"
                    pick == null -> "选好动作后自动挑"
                    poolSize == 0 -> "还没有可用的设定"
                    else -> pick.message + " · 约 " + pick.usedTokens + " tokens"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (chosen.isNotEmpty()) {
                Text(
                    if (open) "收起" else "调整",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { open = !open }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        if (!open) {
            if (active.isNotEmpty()) {
                Text(
                    active.joinToString("、") { it.note.title },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        } else {
            Text(
                "点一条可以临时取消（只影响这一次，不改模式）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            chosen.forEach { candidate ->
                val checked = candidate.note.id !in excluded
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(candidate.note.id) }
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = { onToggle(candidate.note.id) })
                    Column(Modifier.weight(1f)) {
                        Text(
                            NoteKind.label(candidate.note.kind) + " · " + candidate.note.title,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            candidate.reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 引用笔记：默认带全部人物/设定，可以逐条勾掉，也可以整批全选/清空。
 * 笔记一多，逐条点就是折磨——这就是用户说的「没有全选、管理混乱」。
 */
@Composable
private fun ContextNotePicker(
    notes: List<NoteEntity>,
    excluded: Set<String>,
    onToggle: (String) -> Unit,
    onToggleAll: (Boolean) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val included = notes.count { it.id !in excluded }
    val allIncluded = included == notes.size
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "引用笔记 " + included + "/" + notes.size + " 条",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .weight(1f)
                    .clickable { open = !open }
            )
            Text(
                if (allIncluded) "全部取消" else "全选",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onToggleAll(!allIncluded) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Text(
                if (open) "收起" else "调整",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { open = !open }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (open) {
            notes.forEach { note ->
                val checked = note.id !in excluded
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(note.id) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = { onToggle(note.id) })
                    Column(Modifier.weight(1f)) {
                        Text(
                            NoteKind.label(note.kind) + " · " + note.title,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (note.content.isNotBlank()) {
                            Text(
                                note.content.replace('\n', ' ').take(40),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
