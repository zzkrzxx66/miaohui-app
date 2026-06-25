package com.miaohui.app.network

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ImageApiService {

    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY_MS = 2000L

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trimEnd('/')

    /**
     * Generate image from text prompt.
     * Auto-retries up to MAX_RETRIES on 504/timeout.
     * @return base64 encoded image string (or "url:..." for URL responses)
     */
    fun generateImage(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        size: String,
        quality: String,
        n: Int = 1
    ): Result<List<String>> {
        val url = "${normalizeBaseUrl(baseUrl)}/images/generations"

        val jsonBody = JSONObject().apply {
            put("model", model)
            put("prompt", prompt)
            put("size", size)
            put("quality", quality)
            put("n", n)
            put("response_format", "b64_json")
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("User-Agent", "MiaoHui/2.0")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeWithRetryMulti(request)
    }

    /**
     * Edit existing image with a text prompt.
     * Auto-retries up to MAX_RETRIES on 504/timeout.
     * @param imageBytes the source image bytes (compressed JPEG)
     * @return base64 encoded edited image string (or "url:..." for URL responses)
     */
    fun editImage(
        baseUrl: String,
        apiKey: String,
        model: String,
        imageBytesList: List<ByteArray>,
        prompt: String,
        size: String,
        quality: String,
        maskBytes: ByteArray? = null
    ): Result<List<String>> {
        val url = "${normalizeBaseUrl(baseUrl)}/images/edits"

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("size", size)
            .addFormDataPart("quality", quality)
            .addFormDataPart("n", "1")
            .addFormDataPart("response_format", "b64_json")

        // Add one or more images
        for ((index, imageBytes) in imageBytesList.withIndex()) {
            val isPng = imageBytes.size > 4 &&
                imageBytes[0] == 0x89.toByte() &&
                imageBytes[1] == 0x50.toByte() &&
                imageBytes[2] == 0x4E.toByte()
            val mimeType = if (isPng) "image/png" else "image/jpeg"
            val fieldName = if (imageBytesList.size == 1) "image" else "image[]"
            val fileName = if (isPng) "source_$index.png" else "source_$index.jpg"
            multipartBuilder.addFormDataPart(
                fieldName, fileName,
                imageBytes.toRequestBody(mimeType.toMediaType())
            )
        }

        // Add mask if provided
        if (maskBytes != null) {
            val maskName = "mask.png"
            multipartBuilder.addFormDataPart(
                "mask", maskName,
                maskBytes.toRequestBody("image/png".toMediaType())
            )
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", "MiaoHui/2.0")
            .post(multipartBuilder.build())
            .build()

        return executeWithRetryMulti(request)
    }

    /**
     * Execute request with automatic retry, returning multiple results.
     */
    private fun executeWithRetryMulti(request: Request): Result<List<String>> {
        var lastError: String = ""
        for (attempt in 1..MAX_RETRIES) {
            Log.d("ImageApiService", "API attempt $attempt/$MAX_RETRIES")
            val result = executeRequestMulti(request)
            if (result.isSuccess) return result

            val error = result.exceptionOrNull()?.message ?: "未知错误"
            lastError = error

            val shouldRetry = error.contains("504") ||
                error.contains("超时") ||
                error.contains("timeout") ||
                error.contains("Gateway") ||
                error.contains("502") ||
                error.contains("503")

            if (!shouldRetry || attempt == MAX_RETRIES) break

            Log.d("ImageApiService", "Will retry in ${RETRY_DELAY_MS}ms (error: $error)")
            Thread.sleep(RETRY_DELAY_MS)
        }

        val finalMsg = if (lastError.contains("504") || lastError.contains("Gateway")) {
            "API 服务器网关超时 (504)。\n\n" +
                "已自动重试 $MAX_RETRIES 次仍失败。\n" +
                "建议换个时间再试，或更换其他 API 地址。"
        } else if (lastError.contains("超时") || lastError.contains("timeout")) {
            "请求超时。已自动重试 $MAX_RETRIES 次。建议稍后重试。"
        } else {
            lastError
        }

        return Result.failure(Exception(finalMsg))
    }

    private fun executeRequestMulti(request: Request): Result<List<String>> {
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""

                if (response.code == 413) {
                    return Result.failure(Exception("上传数据过大 (413)。图片已自动压缩但仍超出服务器限制，建议使用更小尺寸的图片。"))
                }

                if (response.code == 504) {
                    return Result.failure(Exception("504 Gateway Timeout: 服务器网关超时"))
                }

                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val json = JSONObject(body)
                        val errObj = json.opt("error")
                        if (errObj is JSONObject) {
                            errObj.optString("message", body)
                        } else {
                            json.optString("error", body)
                        }
                    } catch (e: Exception) {
                        "HTTP ${response.code}: ${body.take(300)}"
                    }
                    Log.e("ImageApiService", "API error (${'$'}{response.code}): $errorMsg")
                    Result.failure(Exception(errorMsg))
                } else {
                    val json = JSONObject(body)
                    val dataArray: JSONArray = json.optJSONArray("data")
                        ?: return Result.failure(Exception("API 返回数据格式异常: ${body.take(200)}"))

                    if (dataArray.length() == 0) {
                        return Result.failure(Exception("API 返回空数据"))
                    }

                    val results = mutableListOf<String>()
                    for (i in 0 until dataArray.length()) {
                        val item = dataArray.getJSONObject(i)
                        val b64 = item.optString("b64_json", "")
                        if (b64.isNotEmpty()) {
                            results.add(b64)
                        } else {
                            val imgUrl = item.optString("url", "")
                            if (imgUrl.isNotEmpty()) {
                                results.add("url:$imgUrl")
                            }
                        }
                    }

                    if (results.isEmpty()) {
                        Result.failure(Exception("API 返回数据中无图片"))
                    } else {
                        Result.success(results)
                    }
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("ImageApiService", "Timeout", e)
            Result.failure(Exception("服务器响应超时"))
        } catch (e: java.net.UnknownHostException) {
            Log.e("ImageApiService", "Unknown host", e)
            Result.failure(Exception("无法连接服务器，请检查 API 地址和网络"))
        } catch (e: javax.net.ssl.SSLException) {
            Log.e("ImageApiService", "SSL error", e)
            Result.failure(Exception("网络连接异常: " + (e.message ?: "SSL错误")))
        } catch (e: Exception) {
            Log.e("ImageApiService", "Request failed", e)
            Result.failure(Exception("请求失败: " + (e.message ?: "未知错误")))
        }
    }

    /**
     * Execute request with automatic retry on transient failures (504, timeout).
     * Legacy single-result method for backward compatibility.
     */
    private fun executeWithRetry(request: Request): Result<String> {
        val multiResult = executeWithRetryMulti(request)
        return multiResult.fold(
            onSuccess = { list -> if (list.isNotEmpty()) Result.success(list[0]) else Result.failure(Exception("无结果")) },
            onFailure = { Result.failure(it) }
        )
    }

    private fun executeRequest(request: Request): Result<String> {
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""

                // Detect 504 Gateway Timeout (HTML response from nginx)
                if (response.code == 504) {
                    return Result.failure(Exception("504 Gateway Timeout: 服务器网关超时"))
                }

                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val json = JSONObject(body)
                        val errObj = json.opt("error")
                        if (errObj is JSONObject) {
                            errObj.optString("message", body)
                        } else {
                            json.optString("error", body)
                        }
                    } catch (e: Exception) {
                        "HTTP ${response.code}: ${body.take(300)}"
                    }
                    Log.e("ImageApiService", "API error (${'$'}{response.code}): $errorMsg")
                    Result.failure(Exception(errorMsg))
                } else {
                    val json = JSONObject(body)
                    val dataArray: JSONArray = json.optJSONArray("data")
                        ?: return Result.failure(Exception("API 返回数据格式异常: ${body.take(200)}"))

                    if (dataArray.length() == 0) {
                        return Result.failure(Exception("API 返回空数据"))
                    }

                    val firstItem = dataArray.getJSONObject(0)
                    val b64 = firstItem.optString("b64_json", "")
                    if (b64.isNotEmpty()) {
                        Result.success(b64)
                    } else {
                        val imgUrl = firstItem.optString("url", "")
                        if (imgUrl.isNotEmpty()) {
                            Result.success("url:$imgUrl")
                        } else {
                            Result.failure(Exception("API 返回数据中无图片"))
                        }
                    }
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("ImageApiService", "Timeout", e)
            Result.failure(Exception("服务器响应超时"))
        } catch (e: java.net.UnknownHostException) {
            Log.e("ImageApiService", "Unknown host", e)
            Result.failure(Exception("无法连接服务器，请检查 API 地址和网络"))
        } catch (e: javax.net.ssl.SSLException) {
            Log.e("ImageApiService", "SSL error", e)
            Result.failure(Exception("网络连接异常: " + (e.message ?: "SSL错误")))
        } catch (e: Exception) {
            Log.e("ImageApiService", "Request failed", e)
            Result.failure(Exception("请求失败: " + (e.message ?: "未知错误")))
        }
    }
}
