package com.creativeworkbench.writer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 40.dp)
    ) {
        Text("更多", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "模块、外观与数据",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        SettingsGroup(title = "模块") {
            SwitchRow(
                title = "设定笔记",
                desc = "唯一可关模块；关闭后导航、笔记抽屉和 AI 上下文里的笔记一起消失，数据保留。",
                checked = settings.notesEnabled,
                onChange = viewModel::setNotesEnabled
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(
                "导入动作包：S4 实现。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingsGroup(title = "外观") {
            Text(
                "主题",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
            )
            SegmentedRow(
                options = listOf("跟随系统", "浅色", "深色"),
                selected = when (settings.theme) {
                    ThemeMode.System -> 0
                    ThemeMode.Light -> 1
                    ThemeMode.Dark -> 2
                },
                onSelect = {
                    viewModel.setTheme(
                        when (it) {
                            0 -> ThemeMode.System
                            1 -> ThemeMode.Light
                            else -> ThemeMode.Dark
                        }
                    )
                }
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "正文字号",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SegmentedRow(
                options = listOf("小", "标准", "大"),
                selected = when (settings.fontSize) {
                    FontSizePreset.Small -> 0
                    FontSizePreset.Standard -> 1
                    FontSizePreset.Large -> 2
                },
                onSelect = {
                    viewModel.setFontSize(
                        when (it) {
                            0 -> FontSizePreset.Small
                            1 -> FontSizePreset.Standard
                            else -> FontSizePreset.Large
                        }
                    )
                }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "正文 " + bodySizeLabel(settings.fontSize) + " · 行高随字号一起放大",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingsGroup(title = "数据与备份") {
            Text(
                "自动备份、导出/导入、清空：S5 实现。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SwitchRow(
                title = "允许明文 HTTP",
                desc = "默认只允许 https；本地 Ollama/LM Studio 需要 http 时才打开。",
                checked = settings.allowCleartextHttp,
                onChange = viewModel::setAllowCleartextHttp
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SwitchRow(
                title = "显示帧率",
                desc = "只在编辑器生效，默认关闭。",
                checked = settings.fpsOverlay,
                onChange = viewModel::setFpsOverlay
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingsGroup(title = "关于") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("创作工作台", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        BuildConfig.VERSION_NAME + " · 单机 · GPL-3.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "在用户确认能日常写之前，这只是原型，不是通过。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 12.dp),
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
        Spacer(Modifier.padding(horizontal = 6.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentedRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selected == index,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label, fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal) }
            )
        }
    }
}

private fun bodySizeLabel(preset: FontSizePreset): String = when (preset) {
    FontSizePreset.Small -> "15sp"
    FontSizePreset.Standard -> "17sp"
    FontSizePreset.Large -> "19sp"
}
