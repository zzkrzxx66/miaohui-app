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

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trimEnd('/')

    /**
     * Generate image from text prompt
     * @return base64 encoded image string
     */
    fun generateImage(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        size: String,
        quality: String
    ): Result<String> {
        val url = "${normalizeBaseUrl(baseUrl)}/images/generations"

        val jsonBody = JSONObject().apply {
            put("model", model)
            put("prompt", prompt)
            put("size", size)
            put("quality", quality)
            put("n", 1)
            put("response_format", "b64_json")
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("User-Agent", "MiaoHui/1.2")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeRequest(request)
    }

    /**
     * Edit existing image with a text prompt
     * @param imageBytes the source image bytes (compressed PNG/JPEG)
     * @return base64 encoded edited image string
     */
    fun editImage(
        baseUrl: String,
        apiKey: String,
        model: String,
        imageBytes: ByteArray,
        prompt: String,
        size: String,
        quality: String
    ): Result<String> {
        val url = "${normalizeBaseUrl(baseUrl)}/images/edits"

        // Determine MIME type based on image header
        val isPng = imageBytes.size > 4 &&
            imageBytes[0] == 0x89.toByte() &&
            imageBytes[1] == 0x50.toByte() &&
            imageBytes[2] == 0x4E.toByte()
        val mimeType = if (isPng) "image/png" else "image/jpeg"
        val fileName = if (isPng) "source.png" else "source.jpg"

        val imagePart = MultipartBody.Part.createFormData(
            "image", fileName,
            imageBytes.toRequestBody(mimeType.toMediaType())
        )

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("size", size)
            .addFormDataPart("quality", quality)
            .addFormDataPart("n", "1")
            .addFormDataPart("response_format", "b64_json")
            .addPart(imagePart)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", "MiaoHui/1.2")
            .post(multipartBuilder.build())
            .build()

        return executeRequest(request)
    }

    private fun executeRequest(request: Request): Result<String> {
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val json = JSONObject(body)
                        // Try error.message or error object as string
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
                    // Try b64_json first, then url
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
            val isWrite = e.message?.contains("write", ignoreCase = true) == true
            Result.failure(Exception(
                if (isWrite) "上传图片超时，请检查网络后重试"
                else "服务器响应超时，AI 图片处理需要较长时间，请稍后重试"
            ))
        } catch (e: java.net.UnknownHostException) {
            Log.e("ImageApiService", "Unknown host", e)
            Result.failure(Exception("无法连接服务器，请检查 API 地址和网络"))
        } catch (e: javax.net.ssl.SSLException) {
            Log.e("ImageApiService", "SSL error", e)
            Result.failure(Exception("网络连接异常: ${e.message ?: "SSL错误"}"))
        } catch (e: Exception) {
            Log.e("ImageApiService", "Request failed", e)
            Result.failure(Exception("请求失败: ${e.message ?: "未知错误"}"))
        }
    }
}
