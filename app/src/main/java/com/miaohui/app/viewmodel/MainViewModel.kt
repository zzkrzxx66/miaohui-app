package com.miaohui.app.viewmodel

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miaohui.app.data.ImageRecord
import com.miaohui.app.data.SettingsManager
import com.miaohui.app.network.ChatGpt2ApiService
import com.miaohui.app.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

data class GenerateUiState(
    val isLoading: Boolean = false,
    val results: List<ImageRecord> = emptyList(),
    val error: String? = null,
    val prompt: String = "",
    val size: String = "1024x1024",
    val quality: String = "high",
    val referenceImagePath: String? = null,
    val batchCount: Int = 1
)

data class EditUiState(
    val isLoading: Boolean = false,
    val result: ImageRecord? = null,
    val error: String? = null,
    val prompt: String = "",
    val size: String = "1024x1024",
    val quality: String = "high",
    val maskPath: String? = null,
    val extraImagePaths: List<String> = emptyList()
)

data class HealthUiState(
    val isLoading: Boolean = false,
    val info: ChatGpt2ApiService.HealthInfo? = null,
    val error: String? = null
)

data class PptPsdUiState(
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val status: String = "",
    val downloadUrl: String? = null,
    val error: String? = null,
    val fileType: String = "ppt"
)

data class ModelListUiState(
    val isLoading: Boolean = false,
    val models: List<ChatGpt2ApiService.ModelInfo> = emptyList(),
    val error: String? = null
)

data class ServerImagesUiState(
    val isLoading: Boolean = false,
    val images: List<ChatGpt2ApiService.ServerImage> = emptyList(),
    val error: String? = null,
    val page: Int = 1
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = ImageRepository(application)

    private val _generateState = MutableStateFlow(GenerateUiState())
    val generateState: StateFlow<GenerateUiState> = _generateState.asStateFlow()

    private val _editState = MutableStateFlow(EditUiState())
    val editState: StateFlow<EditUiState> = _editState.asStateFlow()

    private val _healthState = MutableStateFlow(HealthUiState())
    val healthState: StateFlow<HealthUiState> = _healthState.asStateFlow()

    private val _pptPsdState = MutableStateFlow(PptPsdUiState())
    val pptPsdState: StateFlow<PptPsdUiState> = _pptPsdState.asStateFlow()

    private val _modelListState = MutableStateFlow(ModelListUiState())
    val modelListState: StateFlow<ModelListUiState> = _modelListState.asStateFlow()

    private val _serverImagesState = MutableStateFlow(ServerImagesUiState())
    val serverImagesState: StateFlow<ServerImagesUiState> = _serverImagesState.asStateFlow()

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

    // ===== Generate =====

    fun updatePrompt(prompt: String) {
        _generateState.value = _generateState.value.copy(prompt = prompt)
    }

    fun updateSize(size: String) {
        _generateState.value = _generateState.value.copy(size = size)
    }

    fun updateQuality(quality: String) {
        _generateState.value = _generateState.value.copy(quality = quality)
    }

    fun updateReferenceImage(path: String?) {
        _generateState.value = _generateState.value.copy(referenceImagePath = path)
    }

    fun updateBatchCount(count: Int) {
        _generateState.value = _generateState.value.copy(batchCount = count.coerceIn(1, 4))
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
            _generateState.value = state.copy(isLoading = true, error = null, results = emptyList())

            if (state.referenceImagePath != null) {
                val result = repository.imageToImage(state.referenceImagePath, state.prompt, state.size, state.quality)
                result.fold(
                    onSuccess = { record ->
                        _generateState.value = _generateState.value.copy(
                            isLoading = false, results = listOf(record), error = null
                        )
                    },
                    onFailure = { e ->
                        _generateState.value = _generateState.value.copy(
                            isLoading = false, error = e.message ?: "生成失败"
                        )
                    }
                )
            } else {
                val result = repository.generateImage(state.prompt, state.size, state.quality, state.batchCount)
                result.fold(
                    onSuccess = { records ->
                        _generateState.value = _generateState.value.copy(
                            isLoading = false, results = records, error = null
                        )
                    },
                    onFailure = { e ->
                        _generateState.value = _generateState.value.copy(
                            isLoading = false, error = e.message ?: "生成失败"
                        )
                    }
                )
            }
        }
    }

    // ===== Edit =====

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
            val result = repository.editImage(sourceRecord, state.prompt, state.size, state.quality, state.maskPath)
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

    fun updateMaskPath(path: String?) {
        _editState.value = _editState.value.copy(maskPath = path)
    }

    fun updateExtraImages(paths: List<String>) {
        _editState.value = _editState.value.copy(extraImagePaths = paths)
    }

    fun clearEditState() {
        _editState.value = EditUiState()
    }

    fun clearGenerateResult() {
        _generateState.value = _generateState.value.copy(results = emptyList(), error = null)
    }

    fun clearError() {
        _generateState.value = _generateState.value.copy(error = null)
        _editState.value = _editState.value.copy(error = null)
    }

    // ===== Model List =====

    fun fetchModels() {
        val context = getApplication<Application>()
        val baseUrl = SettingsManager.getApiBaseUrl(context)
        val apiKey = SettingsManager.getApiKey(context)
        if (baseUrl.isEmpty() || apiKey.isEmpty()) return

        _modelListState.value = _modelListState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ChatGpt2ApiService.fetchModels(baseUrl, apiKey)
            }
            result.fold(
                onSuccess = { models ->
                    // Cache model IDs
                    val modelIds = models.joinToString(",") { it.id }
                    SettingsManager.setCachedModels(context, modelIds)
                    _modelListState.value = _modelListState.value.copy(
                        isLoading = false,
                        models = models,
                        error = null
                    )
                },
                onFailure = { e ->
                    _modelListState.value = _modelListState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            )
        }
    }

    // ===== Health Check =====

    fun fetchHealth() {
        val context = getApplication<Application>()
        val baseUrl = SettingsManager.getApiBaseUrl(context)
        val apiKey = SettingsManager.getApiKey(context)
        if (baseUrl.isEmpty() || apiKey.isEmpty()) return

        _healthState.value = _healthState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ChatGpt2ApiService.fetchHealth(baseUrl, apiKey)
            }
            result.fold(
                onSuccess = { info ->
                    _healthState.value = _healthState.value.copy(isLoading = false, info = info, error = null)
                },
                onFailure = { e ->
                    _healthState.value = _healthState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    // ===== PPT / PSD Generation =====

    fun generatePpt(record: ImageRecord) {
        val context = getApplication<Application>()
        val baseUrl = SettingsManager.getApiBaseUrl(context)
        val apiKey = SettingsManager.getApiKey(context)
        if (baseUrl.isEmpty()) return

        _pptPsdState.value = PptPsdUiState(isLoading = true, fileType = "ppt", status = "提交中...")
        viewModelScope.launch {
            // Read image as base64
            val imageBase64 = withContext(Dispatchers.IO) {
                try {
                    val file = File(record.imageFilePath)
                    if (file.exists()) {
                        android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
                    } else null
                } catch (e: Exception) { null }
            }

            val result = withContext(Dispatchers.IO) {
                ChatGpt2ApiService.generatePpt(baseUrl, apiKey, record.prompt, imageBase64)
            }

            result.fold(
                onSuccess = { task ->
                    if (task.taskId.isNotEmpty()) {
                        pollEditableFileTask(task.taskId, "ppt")
                    } else if (task.downloadUrl != null) {
                        _pptPsdState.value = PptPsdUiState(
                            isLoading = false, progress = 100, status = "完成",
                            downloadUrl = task.downloadUrl, fileType = "ppt"
                        )
                    } else {
                        _pptPsdState.value = PptPsdUiState(isLoading = false, error = "PPT 生成失败", fileType = "ppt")
                    }
                },
                onFailure = { e ->
                    _pptPsdState.value = PptPsdUiState(isLoading = false, error = e.message, fileType = "ppt")
                }
            )
        }
    }

    fun generatePsd(record: ImageRecord) {
        val context = getApplication<Application>()
        val baseUrl = SettingsManager.getApiBaseUrl(context)
        val apiKey = SettingsManager.getApiKey(context)
        if (baseUrl.isEmpty()) return

        _pptPsdState.value = PptPsdUiState(isLoading = true, fileType = "psd", status = "提交中...")
        viewModelScope.launch {
            val imageBase64 = withContext(Dispatchers.IO) {
                try {
                    val file = File(record.imageFilePath)
                    if (file.exists()) {
                        android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
                    } else null
                } catch (e: Exception) { null }
            }

            val result = withContext(Dispatchers.IO) {
                ChatGpt2ApiService.generatePsd(baseUrl, apiKey, record.prompt, imageBase64)
            }

            result.fold(
                onSuccess = { task ->
                    if (task.taskId.isNotEmpty()) {
                        pollEditableFileTask(task.taskId, "psd")
                    } else if (task.downloadUrl != null) {
                        _pptPsdState.value = PptPsdUiState(
                            isLoading = false, progress = 100, status = "完成",
                            downloadUrl = task.downloadUrl, fileType = "psd"
                        )
                    } else {
                        _pptPsdState.value = PptPsdUiState(isLoading = false, error = "PSD 生成失败", fileType = "psd")
                    }
                },
                onFailure = { e ->
                    _pptPsdState.value = PptPsdUiState(isLoading = false, error = e.message, fileType = "psd")
                }
            )
        }
    }

    private fun pollEditableFileTask(taskId: String, fileType: String) {
        val context = getApplication<Application>()
        val baseUrl = SettingsManager.getApiBaseUrl(context)
        val apiKey = SettingsManager.getApiKey(context)

        viewModelScope.launch {
            var attempts = 0
            while (attempts < 60) {
                delay(3000)
                attempts++
                val result = withContext(Dispatchers.IO) {
                    ChatGpt2ApiService.pollEditableFileTask(baseUrl, apiKey, taskId, fileType)
                }
                result.fold(
                    onSuccess = { task ->
                        if (task.status == "completed" && task.downloadUrl != null) {
                            _pptPsdState.value = PptPsdUiState(
                                isLoading = false, progress = 100, status = "完成",
                                downloadUrl = task.downloadUrl, fileType = fileType
                            )
                            return@launch
                        } else if (task.status == "failed") {
                            _pptPsdState.value = PptPsdUiState(
                                isLoading = false, error = task.error ?: "任务失败", fileType = fileType
                            )
                            return@launch
                        } else {
                            _pptPsdState.value = PptPsdUiState(
                                isLoading = true, progress = task.progress,
                                status = task.status, fileType = fileType
                            )
                        }
                    },
                    onFailure = { e ->
                        _pptPsdState.value = PptPsdUiState(
                            isLoading = false, error = e.message, fileType = fileType
                        )
                        return@launch
                    }
                )
            }
            _pptPsdState.value = PptPsdUiState(
                isLoading = false, error = "超时，请稍后重试", fileType = fileType
            )
        }
    }

    fun clearPptPsdState() {
        _pptPsdState.value = PptPsdUiState()
    }

    // ===== Server Images =====

    fun fetchServerImages(page: Int = 1) {
        val context = getApplication<Application>()
        val baseUrl = SettingsManager.getApiBaseUrl(context)
        val apiKey = SettingsManager.getApiKey(context)
        if (baseUrl.isEmpty()) return

        _serverImagesState.value = _serverImagesState.value.copy(isLoading = true, error = null, page = page)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ChatGpt2ApiService.fetchServerImages(baseUrl, apiKey, page)
            }
            result.fold(
                onSuccess = { images ->
                    val current = if (page == 1) images else _serverImagesState.value.images + images
                    _serverImagesState.value = _serverImagesState.value.copy(
                        isLoading = false, images = current, error = null, page = page
                    )
                },
                onFailure = { e ->
                    _serverImagesState.value = _serverImagesState.value.copy(
                        isLoading = false, error = e.message
                    )
                }
            )
        }
    }

    // ===== Save to Gallery =====

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
