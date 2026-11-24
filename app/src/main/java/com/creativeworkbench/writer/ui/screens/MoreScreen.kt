package com.creativeworkbench.writer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creativeworkbench.writer.BuildConfig
import com.creativeworkbench.writer.core.settings.AppSettings
import com.creativeworkbench.writer.core.settings.FontSizePreset
import com.creativeworkbench.writer.core.settings.ThemeMode
import com.creativeworkbench.writer.ui.AppViewModel

@Composable
fun MoreScreen(viewModel: AppViewModel, settings: AppSettings) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Text("更多", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(18.dp))

        Section("模块与扩展")
        SwitchRow(
            title = "设定笔记",
            desc = "唯一可关模块；关闭后导航、笔记抽屉和 AI 上下文里的笔记一起消失，数据保留。",
            checked = settings.notesEnabled,
            onChange = viewModel::setNotesEnabled
        )
        Text(
            "导入动作包：S4 实现。",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))
        Section("主题")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ThemeButton("跟随系统", settings.theme == ThemeMode.System) { viewModel.setTheme(ThemeMode.System) }
            ThemeButton("浅色", settings.theme == ThemeMode.Light) { viewModel.setTheme(ThemeMode.Light) }
            ThemeButton("深色", settings.theme == ThemeMode.Dark) { viewModel.setTheme(ThemeMode.Dark) }
        }

        Spacer(Modifier.height(12.dp))
        Section("正文字号")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ThemeButton("小 15/27", settings.fontSize == FontSizePreset.Small) { viewModel.setFontSize(FontSizePreset.Small) }
            ThemeButton("标准 17/31", settings.fontSize == FontSizePreset.Standard) { viewModel.setFontSize(FontSizePreset.Standard) }
            ThemeButton("大 19/35", settings.fontSize == FontSizePreset.Large) { viewModel.setFontSize(FontSizePreset.Large) }
        }

        Spacer(Modifier.height(18.dp))
        Section("数据与备份")
        Text(
            "自动备份、导出/导入、清空：S5 实现。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = "允许明文 HTTP",
            desc = "默认只允许 https；本地 Ollama/LM Studio 需要 http 时才打开。",
            checked = settings.allowCleartextHttp,
            onChange = viewModel::setAllowCleartextHttp
        )
        SwitchRow(
            title = "显示帧率",
            desc = "只在编辑器生效，默认关闭。",
            checked = settings.fpsOverlay,
            onChange = viewModel::setFpsOverlay
        )

        Spacer(Modifier.height(18.dp))
        Section("关于")
        Text(
            "创作工作台 " + BuildConfig.VERSION_NAME + "（S1 骨架）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                desc,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun ThemeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
