package com.miaohui.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
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
fun HistoryScreen(
    viewModel: MainViewModel,
    onNavigateToDetail: (Long) -> Unit
) {
    val records by viewModel.repository.getAllRecords().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf<ImageRecord?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("历史作品") }) }
    ) { padding ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.ImageNotSupported,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("还没有作品", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("去创作页面生成你的第一张 AI 图片吧！", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    HistoryGridItem(
                        record = record,
                        onClick = { onNavigateToDetail(record.id) },
                        onLongClick = { showDeleteDialog = record }
                    )
                }
            }
        }
    }

    showDeleteDialog?.let { record ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除作品") },
            text = { Text("确定要删除这张图片吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { viewModel.repository.deleteRecord(record) }
                        showDeleteDialog = null
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryGridItem(
    record: ImageRecord,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column {
            AsyncImage(
                model = File(record.imageFilePath),
                contentDescription = record.prompt,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.padding(8.dp)) {
                Text(
                    record.prompt,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val typeIcon = if (record.type == "edit") "✏️" else "🎨"
                Text(
                    "$typeIcon ${dateFormat.format(Date(record.createdAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
