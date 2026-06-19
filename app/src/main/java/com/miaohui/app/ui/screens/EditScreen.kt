package com.miaohui.app.ui.screens

import android.content.Intent
import androidx.core.content.FileProvider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.miaohui.app.viewmodel.MainViewModel
import com.miaohui.app.ui.theme.BrandGradient
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

    // Track elapsed time during loading
    var elapsedSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(state.isLoading) {
        if (state.isLoading) {
            elapsedSeconds = 0
            while (true) {
                kotlinx.coroutines.delay(1000)
                elapsedSeconds++
            }
        }
    }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Source image
            Text("原图", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                AsyncImage(
                    model = File(record.imageFilePath),
                    contentDescription = record.prompt,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                record.prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            // Edit prompt
            Text("修改描述", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val suggestions = listOf(
                "把背景换成日落黄昏" to "🌅 日落",
                "增加更多细节" to "🔍 细节",
                "改为水彩画风格" to "🎨 水彩",
                "添加柔光效果" to "✨ 柔光"
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
                maxLines = 4,
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(12.dp))

            // Size & Quality
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("1024x1024" to "方形", "1536x1024" to "横图", "1024x1536" to "竖图").forEach { (s, label) ->
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
                listOf("low" to "标准", "medium" to "精细", "high" to "极致").forEach { (q, label) ->
                    FilterChip(
                        selected = state.quality == q,
                        onClick = { viewModel.updateEditQuality(q) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Edit button — gradient
            Surface(
                onClick = { viewModel.editImage(record) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                enabled = !state.isLoading && state.prompt.isNotBlank()
            ) {
                Box(
                    modifier = Modifier.background(
                        if (!state.isLoading && state.prompt.isNotBlank())
                            Brush.horizontalGradient(BrandGradient)
                        else
                            Brush.horizontalGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.5f)))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            val hint = when {
                                elapsedSeconds < 30 -> "正在修改中..."
                                elapsedSeconds < 60 -> "AI 正在处理图片..."
                                elapsedSeconds < 120 -> "马上就好，请耐心等待..."
                                else -> "仍在处理（已 ${elapsedSeconds}s），请稍候..."
                            }
                            Text(hint, color = Color.White)
                        } else {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("基于原图修改", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Result
            state.result?.let { resultRecord ->
                Text("修改结果", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        AsyncImage(
                            model = File(resultRecord.imageFilePath),
                            contentDescription = resultRecord.prompt,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(resultRecord.prompt, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        viewModel.saveToGallery(resultRecord) { success, msg ->
                                            scope.launch { snackbarHostState.showSnackbar(if (success) "✅ $msg" else "❌ $msg") }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("保存")
                            }
                            OutlinedButton(
                                onClick = {
                                    val file = File(resultRecord.imageFilePath)
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "分享图片"))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("分享")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
