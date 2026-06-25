package com.miaohui.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.miaohui.app.data.SettingsManager
import com.miaohui.app.network.ChatGpt2ApiService
import com.miaohui.app.ui.theme.BrandGradient
import com.miaohui.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var apiUrl by remember { mutableStateOf(SettingsManager.getApiBaseUrl(context)) }
    var apiKey by remember { mutableStateOf(SettingsManager.getApiKey(context)) }
    var modelName by remember { mutableStateOf(SettingsManager.getModelName(context)) }
    var showPassword by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val modelListState by viewModel.modelListState.collectAsState()
    val healthState by viewModel.healthState.collectAsState()

    var modelMenuExpanded by remember { mutableStateOf(false) }

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

    // Auto-fetch models when API is configured
    LaunchedEffect(apiUrl, apiKey) {
        if (apiUrl.isNotEmpty() && apiKey.isNotEmpty()) {
            viewModel.fetchModels()
            viewModel.fetchHealth()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ===== Gradient Header =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(BrandGradient),
                        RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("设置", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("配置你的 API 信息", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(20.dp))

                // API Config Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("API 配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "配置 chatgpt2api 兼容的图片生成 API",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = apiUrl,
                            onValueChange = { apiUrl = it },
                            label = { Text("API Base URL") },
                            placeholder = { Text("https://your-vps.com/v1") },
                            supportingText = { Text("不含末尾斜杠") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(Modifier.height(12.dp))

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
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        // Model dropdown or manual input
                        if (modelListState.models.isNotEmpty()) {
                            ExposedDropdownMenuBox(
                                expanded = modelMenuExpanded,
                                onExpandedChange = { modelMenuExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = modelName,
                                    onValueChange = { },
                                    label = { Text("模型") },
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = modelMenuExpanded,
                                    onDismissRequest = { modelMenuExpanded = false }
                                ) {
                                    modelListState.models.forEach { model ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(model.id, fontWeight = FontWeight.Medium)
                                                    if (model.ownedBy.isNotEmpty()) {
                                                        Text(model.ownedBy, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            },
                                            onClick = {
                                                modelName = model.id
                                                modelMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = modelName,
                                onValueChange = { modelName = it },
                                label = { Text("模型名称") },
                                placeholder = { Text("gpt-image-2") },
                                supportingText = {
                                    Text(if (modelListState.isLoading) "正在获取模型列表..." else "默认 gpt-image-2")
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.fetchModels() }) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "刷新模型列表")
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ===== Health Monitor Card =====
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("号池监控", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { viewModel.fetchHealth() }, enabled = !healthState.isLoading) {
                                if (healthState.isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        val info = healthState.info
                        if (info != null) {
                            Row(Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${info.totalAccounts}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("总账号", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${info.availableAccounts}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                    Text("可用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${info.rateLimited}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    Text("限流", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${info.quotaLow}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                    Text("低额度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            val availablePct = if (info.totalAccounts > 0) info.availableAccounts * 100 / info.totalAccounts else 0
                            LinearProgressIndicator(
                                progress = { availablePct / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (availablePct > 50) MaterialTheme.colorScheme.tertiary
                                    else if (availablePct > 20) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "可用率 $availablePct% · ${info.status}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (healthState.error != null) {
                            Text(
                                "⚠️ ${healthState.error}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (healthState.isLoading) {
                            Text("正在获取号池状态...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("点击刷新按钮查看号池状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Save button
                Surface(
                    onClick = { save(); saved = true },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier.background(Brush.horizontalGradient(BrandGradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("保存设置", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Help section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("📖 使用帮助", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        HelpItem("1", "配置 API", "填写 VPS 上 chatgpt2api 的地址和 Key")
                        Spacer(Modifier.height(10.dp))
                        HelpItem("2", "选择模型", "自动获取可用模型列表或手动输入")
                        Spacer(Modifier.height(10.dp))
                        HelpItem("3", "批量生成", "选择 1-4 张批量出图，挑选满意的保存")
                        Spacer(Modifier.height(10.dp))
                        HelpItem("4", "蒙版编辑", "上传蒙版图，只修改白色区域")
                        Spacer(Modifier.height(10.dp))
                        HelpItem("5", "PPT/PSD", "在详情页一键生成可编辑 PPT/PSD 文件")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // FAQ Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("⚠️ 常见问题", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "如果生成或编辑图片时提示 504 超时错误：\n\n" +
                                "• 这是 API 服务端的网关超时限制\n" +
                                "• 不是您的手机或网络问题\n" +
                                "• App 会自动重试 3 次，请耐心等待\n" +
                                "• 建议换个时间再试，或更换 API 地址",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    "妙绘 v2.0 · AI 图片生成与编辑 + chatgpt2api",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HelpItem(num: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(num, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
