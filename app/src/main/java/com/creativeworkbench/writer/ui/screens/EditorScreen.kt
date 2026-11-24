package com.creativeworkbench.writer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creativeworkbench.writer.core.data.OpenedChapter
import com.creativeworkbench.writer.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    workId: String,
    chapterId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    var opened by remember(chapterId) { mutableStateOf<OpenedChapter?>(null) }

    LaunchedEffect(chapterId) {
        opened = viewModel.loadChapter(chapterId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(opened?.chapter?.title ?: "编辑器") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp)
        ) {
            Text(
                "S2 编辑器占位",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "作品 " + workId.take(8) + " · 章节 " + chapterId.take(8),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "字数 " + (opened?.chapter?.wordCount ?: 0L) + " · revision " + (opened?.chapter?.revision ?: 0L),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(18.dp))
            val firstText = opened?.content?.blocks?.firstOrNull()?.text.orEmpty()
            Text(
                if (firstText.isBlank()) "正文为空。S2 将在这里实现分块编辑器。" else firstText,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "输入法、自动保存、journal、撤销和 5 万字性能将在 S2 接入。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
