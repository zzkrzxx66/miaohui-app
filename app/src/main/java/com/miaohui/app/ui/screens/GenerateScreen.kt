package com.miaohui.app.ui.screens

import android.content.Intent
import android.activity.compose.rememberLauncherForActivityResult
import android.activity.result.PickVisualMediaRequest
import android.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.miaohui.app.data.ImageRecord
import com.miaohui.app.ui.theme.BrandGradient
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

    // Photo picker for reference image
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // Copy Uri to cache file
            val cacheFile = File(context.cacheDir, "ref_${System.currentTimeMillis()}.png")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
                viewModel.updateReferenceImage(cacheFile.absolutePath)
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("读取图片失败") }
            }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
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
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "妙绘 · AI 创作",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "输入想象，AI 为你描绘 ✨",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            // ===== Content =====
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(20.dp))

                // ===== Reference Image (图生图) =====
                if (state.referenceImagePath != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        AsyncImage(
                            model = state.referenceImagePath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // Overlay remove button
                        SmallFloatingActionButton(
                            onClick = { viewModel.updateReferenceImage(null) },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            containerColor = Color.Black.copy(alpha = 0.6f)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "移除", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "📝 以图生图模式：基于参考图生成新图片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Surface(
                        onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("📷 上传参考图（以图生图）", style = MaterialTheme.typography.bodyMedium)
                            }
                            Icon(
                                Icons.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Prompt input
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = { viewModel.updatePrompt(it) },
                    label = { Text("描述你想要的图片") },
                    placeholder = { Text("例如：一只在月光下的猫咪，水彩画风格") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(Modifier.height(16.dp))

                // Templates
                Text(
                    "快速模板",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(viewModel.templates) { (prompt, label) ->
                        AssistChip(
                            onClick = { viewModel.updatePrompt(prompt) },
                            label = { Text(label, maxLines = 1) }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Size selector
                SectionTitle("尺寸比例")
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sizes = listOf(
                        Triple("1024x1024", "1:1", "1024×1024"),
                        Triple("1536x1024", "3:2", "1536×1024"),
                        Triple("1024x1536", "2:3", "1024×1536"),
                        Triple("3840x2160", "16:9", "3840×2160\n4K 宽屏"),
                        Triple("2160x3840", "9:16", "2160×3840\n4K 竖屏")
                    )
                    items(sizes) { (size, ratio, res) ->
                        FilterChip(
                            selected = state.size == size,
                            onClick = { viewModel.updateSize(size) },
                            label = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(ratio, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(res, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, maxLines = 2)
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Quality selector
                SectionTitle("质量")
                Spacer(Modifier.height(8.dp))
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

                Spacer(Modifier.height(24.dp))

                // Generate button — gradient
                Surface(
                    onClick = { viewModel.generate() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    enabled = !state.isLoading
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (!state.isLoading)
                                    Brush.horizontalGradient(BrandGradient)
                                else
                                    Brush.horizontalGradient(
                                        listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.5f))
                                    )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("正在生成中，请稍候...", color = Color.White, fontWeight = FontWeight.Medium)
                            } else {
                                Icon(
                                    if (state.referenceImagePath != null) Icons.Filled.Image
                                    else Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (state.referenceImagePath != null) "以图生图" else "生成图片",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }

                if (state.isLoading) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "高质量图片生成可能需要 30-120 秒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Result
                state.result?.let { record ->
                    ResultImageCard(
                        record = record,
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
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

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
fun ResultImageCard(
    record: ImageRecord,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onNew: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            AsyncImage(
                model = File(record.imageFilePath),
                contentDescription = record.prompt,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(12.dp))
            Text(
                record.prompt,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${record.size} · ${record.quality} · ${record.model}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("修改")
                }
                OutlinedButton(
                    onClick = {
                        if (!saving) {
                            saving = true
                            scope.launch {
                                viewModel.saveToGallery(record) { success, msg ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(if (success) "✅ $msg" else "❌ $msg")
                                        saving = false
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("保存中")
                    } else {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("保存")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("分享")
                }
                OutlinedButton(
                    onClick = onNew,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新的")
                }
            }
        }
    }
}
