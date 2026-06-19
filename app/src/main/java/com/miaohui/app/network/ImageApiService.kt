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
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
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
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("User-Agent", "MiaoHui/1.0")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeRequest(request)
    }

    /**
     * Edit existing image with a text prompt
     * @param imageBytes the source image bytes (PNG)
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

        val imagePart = MultipartBody.Part.createFormData(
            "image", "source.png",
            imageBytes.toRequestBody("image/png".toMediaType())
        )

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("size", size)
            .addFormDataPart("quality", quality)
            .addFormDataPart("n", "1")
            .addPart(imagePart)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", "MiaoHui/1.0")
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
                        JSONObject(body).optString("error", body)
                    } catch (e: Exception) {
                        "HTTP ${response.code}: ${body.take(200)}"
                    }
                    Log.e("ImageApiService", "API error: $errorMsg")
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
        } catch (e: Exception) {
            Log.e("ImageApiService", "Request failed", e)
            Result.failure(e)
        }
    }
}
