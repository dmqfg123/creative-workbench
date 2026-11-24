package com.creativeworkbench.writer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.creativeworkbench.writer.WorkbenchApp
import com.creativeworkbench.writer.core.ai.AiClient
import com.creativeworkbench.writer.core.ai.AiConfig
import com.creativeworkbench.writer.core.ai.AiCallResult
import com.creativeworkbench.writer.core.ai.ChatMessage
import com.creativeworkbench.writer.ui.design.GhostButton
import com.creativeworkbench.writer.ui.design.IconAction
import com.creativeworkbench.writer.ui.design.PillButton
import com.creativeworkbench.writer.ui.design.SectionLabel
import kotlinx.coroutines.launch

/**
 * AI 配置：OpenAI 兼容接口。密钥只在 Android Keystore 里加密保存，界面只显示掩码。
 */
@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WorkbenchApp
    val store = app.container.aiConfig
    val initial = remember { store.config() }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var model by remember { mutableStateOf(initial.model) }
    var apiKey by remember { mutableStateOf("") }
    var temperature by remember { mutableStateOf(String.format(java.util.Locale.US, "%.1f", initial.temperature)) }
    var maxTokens by remember { mutableStateOf(initial.maxTokens.toString()) }
    var timeout by remember { mutableStateOf(initial.timeoutSeconds.toString()) }
    var contextWindow by remember { mutableStateOf(initial.contextWindowTokens.toString()) }
    var reserved by remember { mutableStateOf(initial.reservedOutputTokens.toString()) }
    var allowCleartext by remember { mutableStateOf(initial.allowCleartextHttp) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun currentConfig() = AiConfig(
        baseUrl = baseUrl.trim(),
        model = model.trim(),
        temperature = temperature.toDoubleOrNull() ?: 0.8,
        maxTokens = maxTokens.toIntOrNull() ?: 2048,
        timeoutSeconds = timeout.toIntOrNull() ?: 60,
        contextWindowTokens = contextWindow.toIntOrNull() ?: 32768,
        reservedOutputTokens = reserved.toIntOrNull() ?: 2048,
        allowCleartextHttp = allowCleartext
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
            Text("AI 助手", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text(
                "只在你点「生成」时联网；密钥加密存在本机，不进备份。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text("https://api.deepseek.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("模型") },
                placeholder = { Text("deepseek-chat") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key（留空则不改）") },
                placeholder = { Text("当前：" + store.maskedKey()) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )

            SectionLabel("预算与超时")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it },
                    label = { Text("maxTokens") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                )
                OutlinedTextField(
                    value = timeout,
                    onValueChange = { timeout = it },
                    label = { Text("超时（秒）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = contextWindow,
                    onValueChange = { contextWindow = it },
                    label = { Text("上下文窗口") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                )
                OutlinedTextField(
                    value = reserved,
                    onValueChange = { reserved = it },
                    label = { Text("预留输出") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = temperature,
                onValueChange = { temperature = it },
                label = { Text("temperature（0–2）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )

            Spacer(Modifier.height(12.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("允许明文 HTTP", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "默认只允许 https；本机 Ollama / LM Studio 才需要打开",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = allowCleartext, onCheckedChange = { allowCleartext = it })
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    text = "保存",
                    onClick = {
                        val config = currentConfig()
                        store.save(config)
                        if (apiKey.isNotBlank()) store.saveApiKey(apiKey)
                        status = "已保存"
                    }
                )
                GhostButton(
                    text = "测试连接",
                    onClick = {
                        val config = currentConfig()
                        store.save(config)
                        if (apiKey.isNotBlank()) store.saveApiKey(apiKey)
                        val key = store.apiKey()
                        if (key.isBlank() || config.baseUrl.isBlank()) {
                            status = "先填 Base URL 和 API Key"
                            return@GhostButton
                        }
                        status = "测试中…"
                        scope.launch {
                            val result = AiClient().chat(
                                config,
                                key,
                                listOf(ChatMessage("user", "只回复 OK"))
                            )
                            status = when (result) {
                                is AiCallResult.Ok -> "连接正常：" + result.content.take(40)
                                is AiCallResult.Error -> result.message
                            }
                        }
                    }
                )
            }
            status?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
