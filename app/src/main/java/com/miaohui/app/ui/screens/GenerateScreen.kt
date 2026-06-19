package com.miaohui.app.ui.screens

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.miaohui.app.data.ImageRecord
import com.miaohui.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(
    viewModel: MainViewModel,
    onNavigateToEdit: (Long) -> Unit
) {
    val state by viewModel.generateState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("妙绘 · AI 创作") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            // Prompt input
            OutlinedTextField(
                value = state.prompt,
                onValueChange = { viewModel.updatePrompt(it) },
                label = { Text("描述你想要的图片") },
                placeholder = { Text("例如：一只在月光下的猫咪，水彩画风格") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            Spacer(Modifier.height(12.dp))

            // Templates
            Text("✨ 快速模板", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(viewModel.templates) { (prompt, label) ->
                    AssistChip(
                        onClick = { viewModel.updatePrompt(prompt) },
                        label = { Text(label, maxLines = 1) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Size selector
            Text("📐 尺寸", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val sizes = listOf("1024x1024" to "方形", "1536x1024" to "横图", "1024x1536" to "竖图")
                sizes.forEach { (size, label) ->
                    FilterChip(
                        selected = state.size == size,
                        onClick = { viewModel.updateSize(size) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Quality selector
            Text("⚡ 质量", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val qualities = listOf("low" to "标准", "medium" to "精细", "high" to "极致")
                qualities.forEach { (q, label) ->
                    FilterChip(
                        selected = state.quality == q,
                        onClick = { viewModel.updateQuality(q) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Generate button
            Button(
                onClick = { viewModel.generate() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !state.isLoading,
                shape = MaterialTheme.shapes.large
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("正在生成中，请稍候...", fontWeight = FontWeight.Medium)
                } else {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("生成图片", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
                }
            }

            if (state.isLoading) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "💡 提示：高质量图片生成可能需要 30-120 秒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            // Result
            state.result?.let { record ->
                ResultImageCard(
                    record = record,
                    viewModel = viewModel,
                    onEdit = { onNavigateToEdit(record.id) },
                    onShare = {
                        val file = File(record.imageFilePath)
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
                    onNew = { viewModel.clearGenerateResult() }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ResultImageCard(
    record: ImageRecord,
    viewModel: MainViewModel,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onNew: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var saving by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(12.dp)) {
            AsyncImage(
                model = File(record.imageFilePath),
                contentDescription = record.prompt,
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "📝 ${record.prompt}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${record.size} · ${record.quality} · ${record.model}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    enabled = !saving
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("修改")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            saving = true
                            viewModel.saveToGallery(record) { success, msg ->
                                saving = false
                                scope.launch {
                                    snackbarHostState.showSnackbar(if (success) "✅ $msg" else "❌ $msg")
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !saving
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (saving) "保存中..." else "保存")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("分享")
                }
                OutlinedButton(
                    onClick = onNew,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新作品")
                }
            }
        }
    }
}
