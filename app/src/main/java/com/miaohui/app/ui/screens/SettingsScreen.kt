package com.miaohui.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.miaohui.app.data.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var apiUrl by remember { mutableStateOf(SettingsManager.getApiBaseUrl(context)) }
    var apiKey by remember { mutableStateOf(SettingsManager.getApiKey(context)) }
    var modelName by remember { mutableStateOf(SettingsManager.getModelName(context)) }
    var showPassword by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun save() {
        SettingsManager.setApiBaseUrl(context, apiUrl)
        SettingsManager.setApiKey(context, apiKey)
        SettingsManager.setModelName(context, modelName)
    }

    LaunchedEffect(saved) {
        if (saved) {
            snackbarHostState.showSnackbar("✅ 设置已保存")
            saved = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            // API Base URL
            Text("🔗 API 配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "配置你的 OpenAI 兼容图片生成 API 地址和密钥",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = { Text("API Base URL") },
                placeholder = { Text("https://api.openai.com/v1") },
                supportingText = { Text("不含末尾斜杠，如 https://your-api.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // API Key
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                placeholder = { Text("sk-xxxxxxxx") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "显示/隐藏"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Model name
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("模型名称") },
                placeholder = { Text("gpt-image-2") },
                singleLine = true,
                supportingText = { Text("默认 gpt-image-2，可按需修改") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    save()
                    saved = true
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("保存设置", fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(24.dp))

            // Help section
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("📖 使用帮助", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("1. 获取 API Key", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "在 OpenAI 或兼容 API 提供商的网站注册并获取 API Key",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("2. 填写 API 地址", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "通常是 https://api.openai.com/v1 或你的代理地址/v1",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("3. 开始创作", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "在创作页面输入描述，选择尺寸和质量，点击生成",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("4. 修改图片", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "生成后点击「修改」，输入修改描述，基于原图重新生成",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // About
            Text(
                "妙绘 v1.0 · AI 图片生成与编辑",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
