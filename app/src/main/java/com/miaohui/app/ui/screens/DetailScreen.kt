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
import com.miaohui.app.data.ImageRecord
import com.miaohui.app.viewmodel.MainViewModel
import com.miaohui.app.ui.theme.BrandGradient
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: MainViewModel,
    recordId: Long,
    onBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var record by remember { mutableStateOf<ImageRecord?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val pptPsdState by viewModel.pptPsdState.collectAsState()

    val children by remember(recordId) {
        viewModel.repository.getChildRecords(recordId)
    }.collectAsState(initial = emptyList())

    LaunchedEffect(recordId) {
        record = viewModel.repository.getRecordById(recordId)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("作品详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        val r = record
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Main image
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                AsyncImage(
                    model = File(r.imageFilePath),
                    contentDescription = r.prompt,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(16.dp))

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("描述", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(r.prompt, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("类型", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (r.type == "edit") "✏️ 编辑" else "🎨 生成", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("尺寸", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(r.size, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("质量", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(r.quality, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("模型", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(r.model, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("时间", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(dateFormat.format(Date(r.createdAt)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Edit button — gradient
                Surface(
                    onClick = { onNavigateToEdit(r.id) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier.background(Brush.horizontalGradient(BrandGradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("修改图片", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            viewModel.saveToGallery(r) { success, msg ->
                                scope.launch { snackbarHostState.showSnackbar(if (success) "✅ $msg" else "❌ $msg") }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("保存到相册")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val file = File(r.imageFilePath)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "分享图片"))
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("分享图片")
            }

            // PPT / PSD generation
            Spacer(Modifier.height(20.dp))
            Text("可编辑文件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.generatePpt(r) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !pptPsdState.isLoading
                ) {
                    Icon(Icons.Filled.Slideshow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("生成PPT")
                }
                OutlinedButton(
                    onClick = { viewModel.generatePsd(r) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !pptPsdState.isLoading
                ) {
                    Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("生成PSD")
                }
            }

            // PPT/PSD progress
            if (pptPsdState.isLoading) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "正在生成${if (pptPsdState.fileType == "ppt") "PPT" else "PSD"}文件... ${pptPsdState.status}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (pptPsdState.progress > 0) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { pptPsdState.progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // PPT/PSD error
            pptPsdState.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text("❌ $err", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            // PPT/PSD download
            pptPsdState.downloadUrl?.let { url ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier.background(Brush.horizontalGradient(BrandGradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("下载${if (pptPsdState.fileType == "ppt") "PPT" else "PSD"}文件", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.clearPptPsdState() },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("清除", style = MaterialTheme.typography.labelMedium) }
            }

            if (children.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("修改历史", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                children.forEach { child ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = File(child.imageFilePath),
                                contentDescription = child.prompt,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(child.prompt, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                Text(
                                    dateFormat.format(Date(child.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除作品") },
            text = { Text("确定要删除这张图片吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        record?.let { scope.launch { viewModel.repository.deleteRecord(it) } }
                        showDeleteDialog = false
                        onBack()
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }
}
