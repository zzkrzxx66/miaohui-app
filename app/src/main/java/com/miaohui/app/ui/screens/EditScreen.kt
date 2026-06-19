package com.miaohui.app.ui.screens

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.miaohui.app.viewmodel.MainViewModel
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    viewModel: MainViewModel,
    recordId: Long,
    onBack: () -> Unit
) {
    val state by viewModel.editState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var sourceRecord by remember { mutableStateOf<com.miaohui.app.data.ImageRecord?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(recordId) {
        if (!loaded) {
            viewModel.clearEditState()
            sourceRecord = viewModel.repository.getRecordById(recordId)
            loaded = true
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("修改图片") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (sourceRecord == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val record = sourceRecord!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            // Source image
            Text("🖼️ 原图", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                AsyncImage(
                    model = File(record.imageFilePath),
                    contentDescription = record.prompt,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "📝 ${record.prompt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            // Edit prompt
            Text("✏️ 修改描述", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val suggestions = listOf(
                "把背景换成日落黄昏" to "🌅 日落背景",
                "增加更多细节" to "🔍 更多细节",
                "改为水彩画风格" to "🎨 水彩风格",
                "添加柔光效果" to "✨ 柔光效果"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                suggestions.forEach { (text, label) ->
                    AssistChip(
                        onClick = { viewModel.updateEditPrompt(text) },
                        label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.prompt,
                onValueChange = { viewModel.updateEditPrompt(it) },
                label = { Text("描述你想要的修改") },
                placeholder = { Text("例如：把天空改为星空，添加流星") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            Spacer(Modifier.height(12.dp))

            // Size & Quality
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sizes = listOf("1024x1024" to "方形", "1536x1024" to "横图", "1024x1536" to "竖图")
                sizes.forEach { (s, label) ->
                    FilterChip(
                        selected = state.size == s,
                        onClick = { viewModel.updateEditSize(s) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val qualities = listOf("low" to "标准", "medium" to "精细", "high" to "极致")
                qualities.forEach { (q, label) ->
                    FilterChip(
                        selected = state.quality == q,
                        onClick = { viewModel.updateEditQuality(q) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Edit button
            Button(
                onClick = { viewModel.editImage(record) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !state.isLoading && state.prompt.isNotBlank(),
                shape = MaterialTheme.shapes.large
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("正在修改中...")
                } else {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("基于原图修改", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Result
            state.result?.let { resultRecord ->
                Text("🎨 修改结果", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(Modifier.padding(12.dp)) {
                        AsyncImage(
                            model = File(resultRecord.imageFilePath),
                            contentDescription = resultRecord.prompt,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "📝 ${resultRecord.prompt}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    // Chain edit: use result as new source
                                    sourceRecord = resultRecord
                                    viewModel.clearEditState()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("继续修改")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        viewModel.saveToGallery(resultRecord) { success, msg ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(if (success) "✅ $msg" else "❌ $msg")
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("保存")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val file = File(resultRecord.imageFilePath)
                                val uri = FileProvider.getUriForFile(
                                    context, "${context.packageName}.fileprovider", file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "分享图片"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("分享")
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
