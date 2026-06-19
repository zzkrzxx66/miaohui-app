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
import com.miaohui.app.data.ImageRecord
import com.miaohui.app.viewmodel.MainViewModel
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = MaterialTheme.shapes.large
            ) {
                AsyncImage(
                    model = File(r.imageFilePath),
                    contentDescription = r.prompt,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("📝 描述", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(r.prompt, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("📊 参数", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("类型: ${if (r.type == "edit") "✏️ 编辑" else "🎨 生成"}")
                    Text("尺寸: ${r.size}")
                    Text("质量: ${r.quality}")
                    Text("模型: ${r.model}")
                    Text("时间: ${dateFormat.format(Date(r.createdAt))}")
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onNavigateToEdit(r.id) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("修改图片")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            viewModel.saveToGallery(r) { success, msg ->
                                scope.launch { snackbarHostState.showSnackbar(if (success) "✅ $msg" else "❌ $msg") }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large
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
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("分享图片")
            }

            if (children.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("🔄 修改历史", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                children.forEach { child ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = File(child.imageFilePath),
                                contentDescription = child.prompt,
                                modifier = Modifier.size(80.dp),
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
                TextButton(onClick = {
                    scope.launch {
                        record?.let { viewModel.repository.deleteRecord(it) }
                        onBack()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}
