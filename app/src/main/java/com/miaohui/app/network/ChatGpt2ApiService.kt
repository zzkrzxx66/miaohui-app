package com.miaohui.app.network

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service for chatgpt2api extended endpoints:
 * - GET /v1/models (dynamic model list)
 * - GET /health?format=json (account pool health)
 * - POST /api/image-tasks/generations + GET /api/image-tasks (async generation)
 * - POST /v1/ppt/generations + GET /api/editable-file-tasks (PPT generation)
 * - POST /v1/psd/generations (PSD generation)
 * - GET /api/images (server cached images)
 */
object ChatGpt2ApiService {

    private const val TAG = "ChatGpt2ApiService"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trimEnd('/')

    private fun authHeaders(baseUrl: String, apiKey: String): Request.Builder {
        return Request.Builder()
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", "MiaoHui/2.0")
    }

    // ===== Model List =====

    data class ModelInfo(
        val id: String,
        val ownedBy: String = "",
        val isImage: Boolean = false
    )

    fun fetchModels(baseUrl: String, apiKey: String): Result<List<ModelInfo>> {
        val url = "${normalizeBaseUrl(baseUrl)}/models"
        val request = authHeaders(baseUrl, apiKey)
            .url(url)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(Exception("HTTP ${response.code}: ${body.take(300)}"))
                } else {
                    val json = JSONObject(body)
                    val data = json.optJSONArray("data") ?: JSONArray()
                    val models = mutableListOf<ModelInfo>()
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val id = item.optString("id", "")
                        if (id.isNotEmpty()) {
                            val ownedBy = item.optString("owned_by", "")
                            val isImage = id.contains("image", ignoreCase = true) ||
                                id.contains("dall", ignoreCase = true) ||
                                id == "auto"
                            models.add(ModelInfo(id, ownedBy, isImage))
                        }
                    }
                    if (models.isEmpty()) {
                        // Fallback defaults
                        models.add(ModelInfo("gpt-image-2", "openai", true))
                        models.add(ModelInfo("auto", "openai", true))
                    }
                    Result.success(models)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchModels failed", e)
            Result.failure(Exception("获取模型列表失败: ${e.message}"))
        }
    }

    // ===== Health Check =====

    data class HealthInfo(
        val totalAccounts: Int = 0,
        val availableAccounts: Int = 0,
        val rateLimited: Int = 0,
        val quotaLow: Int = 0,
        val status: String = "",
        val details: List<String> = emptyList()
    )

    fun fetchHealth(baseUrl: String, apiKey: String): Result<HealthInfo> {
        val url = "${normalizeBaseUrl(baseUrl)}/health?format=json"
        val request = authHeaders(baseUrl, apiKey)
            .url(url)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(Exception("HTTP ${response.code}"))
                } else {
                    val json = JSONObject(body)
                    val info = HealthInfo(
                        totalAccounts = json.optInt("total_accounts", json.optInt("total", 0)),
                        availableAccounts = json.optInt("available_accounts", json.optInt("available", 0)),
                        rateLimited = json.optInt("rate_limited", json.optInt("limited", 0)),
                        quotaLow = json.optInt("quota_low", json.optInt("low_quota", 0)),
                        status = json.optString("status", if (response.isSuccessful) "ok" else "unknown")
                    )
                    Result.success(info)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchHealth failed", e)
            Result.failure(Exception("获取号池状态失败: ${e.message}"))
        }
    }

    // ===== Async Image Tasks =====

    data class ImageTask(
        val taskId: String,
        val status: String, // "pending", "processing", "completed", "failed"
        val progress: Int = 0,
        val imageUrl: String? = null,
        val error: String? = null
    )

    fun createGenerationTask(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        size: String,
        quality: String,
        n: Int = 1
    ): Result<ImageTask> {
        val url = "${normalizeBaseUrl(baseUrl)}/api/image-tasks/generations"
        val jsonBody = JSONObject().apply {
            put("model", model)
            put("prompt", prompt)
            put("size", size)
            put("quality", quality)
            put("n", n)
            put("response_format", "b64_json")
        }

        val request = authHeaders(baseUrl, apiKey)
            .url(url)
            .header("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(Exception("HTTP ${response.code}: ${body.take(300)}"))
                } else {
                    val json = JSONObject(body)
                    val taskId = json.optString("task_id", json.optString("id", ""))
                    if (taskId.isEmpty()) {
                        // If no task_id, maybe synchronous response
                        val dataArray = json.optJSONArray("data")
                        if (dataArray != null && dataArray.length() > 0) {
                            val firstItem = dataArray.getJSONObject(0)
                            val b64 = firstItem.optString("b64_json", "")
                            val imgUrl = firstItem.optString("url", "")
                            return Result.success(ImageTask(
                                taskId = "",
                                status = "completed",
                                imageUrl = if (b64.isNotEmpty()) "b64:$b64" else imgUrl
                            ))
                        }
                        Result.failure(Exception("无法创建异步任务"))
                    } else {
                        Result.success(ImageTask(
                            taskId = taskId,
                            status = json.optString("status", "pending"),
                            progress = json.optInt("progress", 0)
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "createGenerationTask failed", e)
            Result.failure(Exception("创建异步生图任务失败: ${e.message}"))
        }
    }

    fun pollTask(baseUrl: String, apiKey: String, taskId: String): Result<ImageTask> {
        val url = "${normalizeBaseUrl(baseUrl)}/api/image-tasks/$taskId"
        val request = authHeaders(baseUrl, apiKey)
            .url(url)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(Exception("HTTP ${response.code}"))
                } else {
                    val json = JSONObject(body)
                    val status = json.optString("status", "pending")
                    val progress = json.optInt("progress", 0)
                    var imageUrl: String? = null
                    var error: String? = null

                    if (status == "completed") {
                        val dataArray = json.optJSONArray("data")
                        if (dataArray != null && dataArray.length() > 0) {
                            val firstItem = dataArray.getJSONObject(0)
                            val b64 = firstItem.optString("b64_json", "")
                            val imgUrl = firstItem.optString("url", "")
                            imageUrl = if (b64.isNotEmpty()) "b64:$b64" else imgUrl
                        }
                        if (imageUrl == null) {
                            imageUrl = json.optString("image_url", json.optString("url", ""))
                        }
                    } else if (status == "failed") {
                        error = json.optString("error", "任务失败")
                    }

                    Result.success(ImageTask(taskId, status, progress, imageUrl, error))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "pollTask failed", e)
            Result.failure(Exception("轮询任务状态失败: ${e.message}"))
        }
    }

    // ===== PPT / PSD Generation =====

    data class EditableFileTask(
        val taskId: String,
        val status: String,
        val progress: Int = 0,
        val downloadUrl: String? = null,
        val fileType: String = "ppt",
        val error: String? = null
    )

    fun generatePpt(
        baseUrl: String,
        apiKey: String,
        prompt: String,
        imageBase64: String? = null
    ): Result<EditableFileTask> {
        val url = "${normalizeBaseUrl(baseUrl)}/v1/ppt/generations"
        val jsonBody = JSONObject().apply {
            put("prompt", prompt)
            if (imageBase64 != null) put("image", imageBase64)
        }

        val request = authHeaders(baseUrl, apiKey)
            .url(url)
            .header("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(Exception("HTTP ${response.code}: ${body.take(300)}"))
                } else {
                    val json = JSONObject(body)
                    val taskId = json.optString("task_id", json.optString("id", ""))
                    val downloadUrl = json.optString("download_url", json.optString("url", ""))
                    if (taskId.isNotEmpty()) {
                        Result.success(EditableFileTask(taskId, "pending", 0, null, "ppt"))
                    } else if (downloadUrl.isNotEmpty()) {
                        Result.success(EditableFileTask("", "completed", 100, downloadUrl, "ppt"))
                    } else {
                        Result.failure(Exception("PPT 生成请求失败"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "generatePpt failed", e)
            Result.failure(Exception("PPT 生成失败: ${e.message}"))
        }
    }

    fun generatePsd(
        baseUrl: String,
        apiKey: String,
        prompt: String,
        imageBase64: String? = null
    ): Result<EditableFileTask> {
        val url = "${normalizeBaseUrl(baseUrl)}/v1/psd/generations"
        val jsonBody = JSONObject().apply {
            put("prompt", prompt)
            if (imageBase64 != null) put("image", imageBase64)
        }

        val request = authHeaders(baseUrl, apiKey)
            .url(url)
            .header("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(Exception("HTTP ${response.code}: ${body.take(300)}"))
                } else {
                    val json = JSONObject(body)
                    val taskId = json.optString("task_id", json.optString("id", ""))
                    val downloadUrl = json.optString("download_url", json.optString("url", ""))
                    if (taskId.isNotEmpty()) {
                        Result.success(EditableFileTask(taskId, "pending", 0, null, "psd"))
                    } else if (downloadUrl.isNotEmpty()) {
                        Result.success(EditableFileTask("", "completed", 100, downloadUrl, "psd"))
                    } else {
                        Result.failure(Exception("PSD 生成请求失败"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "generatePsd failed", e)
            Result.failure(Exception("PSD 生成失败: ${e.message}"))
        }
    }

    fun pollEditableFileTask(
        baseUrl: String,
        apiKey: String,
        taskId: String,
        fileType: String
    ): Result<EditableFileTask> {
        val url = "${normalizeBaseUrl(baseUrl)}/api/editable-file-tasks/$taskId"
        val request = authHeaders(baseUrl, apiKey)
            .url(url)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(Exception("HTTP ${response.code}"))
                } else {
                    val json = JSONObject(body)
                    val status = json.optString("status", "pending")
                    val progress = json.optInt("progress", 0)
                    val downloadUrl = if (status == "completed") {
                        json.optString("download_url", json.optString("url", "")).ifEmpty { null }
                    } else null
                    val error = if (status == "failed") json.optString("error", "任务失败") else null

                    Result.success(EditableFileTask(taskId, status, progress, downloadUrl, fileType, error))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "pollEditableFileTask failed", e)
            Result.failure(Exception("轮询任务状态失败: ${e.message}"))
        }
    }

    // ===== Server Images =====

    data class ServerImage(
        val id: String,
        val url: String,
        val prompt: String = "",
        val model: String = "",
        val createdAt: String = ""
    )

    fun fetchServerImages(
        baseUrl: String,
        apiKey: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<ServerImage>> {
        val url = "${normalizeBaseUrl(baseUrl)}/api/images?page=$page&page_size=$pageSize"
        val request = authHeaders(baseUrl, apiKey)
            .url(url)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Result.failure(Exception("HTTP ${response.code}"))
                } else {
                    val json = JSONObject(body)
                    val data = json.optJSONArray("data") ?: json.optJSONArray("images") ?: JSONArray()
                    val images = mutableListOf<ServerImage>()
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val id = item.optString("id", i.toString())
                        val imgUrl = item.optString("url", item.optString("image_url", ""))
                        if (imgUrl.isNotEmpty()) {
                            // Make URL absolute if relative
                            val fullUrl = if (imgUrl.startsWith("http")) imgUrl
                                else "${normalizeBaseUrl(baseUrl)}$imgUrl"
                            images.add(ServerImage(
                                id = id,
                                url = fullUrl,
                                prompt = item.optString("prompt", ""),
                                model = item.optString("model", ""),
                                createdAt = item.optString("created_at", item.optString("createdAt", ""))
                            ))
                        }
                    }
                    Result.success(images)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchServerImages failed", e)
            Result.failure(Exception("获取服务端图片失败: ${e.message}"))
        }
    }
}
