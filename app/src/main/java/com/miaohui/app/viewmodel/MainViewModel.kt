package com.miaohui.app.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miaohui.app.data.ImageRecord
import com.miaohui.app.repository.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class GenerateUiState(
    val isLoading: Boolean = false,
    val result: ImageRecord? = null,
    val error: String? = null,
    val prompt: String = "",
    val size: String = "1024x1024",
    val quality: String = "high"
)

data class EditUiState(
    val isLoading: Boolean = false,
    val result: ImageRecord? = null,
    val error: String? = null,
    val prompt: String = "",
    val size: String = "1024x1024",
    val quality: String = "high"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = ImageRepository(application)

    // Generate state
    private val _generateState = MutableStateFlow(GenerateUiState())
    val generateState: StateFlow<GenerateUiState> = _generateState.asStateFlow()

    // Edit state
    private val _editState = MutableStateFlow(EditUiState())
    val editState: StateFlow<EditUiState> = _editState.asStateFlow()

    // Templates
    val templates = listOf(
        "🎨 赛博朋克风格，霓虹灯光下的未来城市" to "赛博朋克",
        "🌸 日式动漫风格，樱花树下的少女" to "动漫风",
        "🏔️ 中国水墨画风格，山水意境" to "水墨画",
        "🦁 写实摄影风格，野生动物特写" to "写实摄影",
        "🌌 梦幻星空，宇宙星云" to "梦幻星空",
        "🎭 像素艺术风格，复古游戏画面" to "像素艺术",
        "🏰 奇幻插画，中世纪城堡" to "奇幻插画",
        "🌊 印象派油画风格，海边日出" to "印象派"
    )

    fun updatePrompt(prompt: String) {
        _generateState.value = _generateState.value.copy(prompt = prompt)
    }

    fun updateSize(size: String) {
        _generateState.value = _generateState.value.copy(size = size)
    }

    fun updateQuality(quality: String) {
        _generateState.value = _generateState.value.copy(quality = quality)
    }

    fun generate() {
        val state = _generateState.value
        if (state.prompt.isBlank()) {
            _generateState.value = state.copy(error = "请输入描述文本")
            return
        }
        if (!repository.isConfigured()) {
            _generateState.value = state.copy(error = "请先在设置中配置 API")
            return
        }

        viewModelScope.launch {
            _generateState.value = state.copy(isLoading = true, error = null)
            val result = repository.generateImage(state.prompt, state.size, state.quality)
            result.fold(
                onSuccess = { record ->
                    _generateState.value = _generateState.value.copy(
                        isLoading = false,
                        result = record,
                        error = null
                    )
                },
                onFailure = { e ->
                    _generateState.value = _generateState.value.copy(
                        isLoading = false,
                        error = e.message ?: "生成失败"
                    )
                }
            )
        }
    }

    fun editImage(sourceRecord: ImageRecord) {
        val state = _editState.value
        if (state.prompt.isBlank()) {
            _editState.value = state.copy(error = "请输入修改描述")
            return
        }
        if (!repository.isConfigured()) {
            _editState.value = state.copy(error = "请先在设置中配置 API")
            return
        }

        viewModelScope.launch {
            _editState.value = state.copy(isLoading = true, error = null)
            val result = repository.editImage(sourceRecord, state.prompt, state.size, state.quality)
            result.fold(
                onSuccess = { record ->
                    _editState.value = _editState.value.copy(
                        isLoading = false,
                        result = record,
                        error = null
                    )
                },
                onFailure = { e ->
                    _editState.value = _editState.value.copy(
                        isLoading = false,
                        error = e.message ?: "编辑失败"
                    )
                }
            )
        }
    }

    fun updateEditPrompt(prompt: String) {
        _editState.value = _editState.value.copy(prompt = prompt)
    }

    fun updateEditSize(size: String) {
        _editState.value = _editState.value.copy(size = size)
    }

    fun updateEditQuality(quality: String) {
        _editState.value = _editState.value.copy(quality = quality)
    }

    fun clearEditState() {
        _editState.value = EditUiState()
    }

    fun clearGenerateResult() {
        _generateState.value = _generateState.value.copy(result = null, error = null)
    }

    fun clearError() {
        _generateState.value = _generateState.value.copy(error = null)
        _editState.value = _editState.value.copy(error = null)
    }

    /**
     * Save image to gallery (MediaStore)
     */
    fun saveToGallery(record: ImageRecord, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val sourceFile = File(record.imageFilePath)
                if (!sourceFile.exists()) {
                    onResult(false, "图片文件不存在")
                    return@launch
                }

                val context = getApplication<Application>()
                val fileName = "miaohui_${System.currentTimeMillis()}.png"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MiaoHui")
                    }
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            sourceFile.inputStream().use { it.copyTo(out) }
                        }
                        onResult(true, "已保存到相册 Pictures/MiaoHui/$fileName")
                    } else {
                        onResult(false, "保存失败")
                    }
                } else {
                    val picturesDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        "MiaoHui"
                    )
                    picturesDir.mkdirs()
                    val destFile = File(picturesDir, fileName)
                    sourceFile.copyTo(destFile)
                    onResult(true, "已保存到 ${destFile.absolutePath}")
                }
            } catch (e: Exception) {
                onResult(false, "保存失败: ${e.message}")
            }
        }
    }
}
