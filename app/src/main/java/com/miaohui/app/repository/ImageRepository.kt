package com.miaohui.app.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.miaohui.app.data.AppDatabase
import com.miaohui.app.data.ImageRecord
import com.miaohui.app.data.SettingsManager
import com.miaohui.app.network.ImageApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import android.util.Log

class ImageRepository(private val context: Context) {

    private val dao = AppDatabase.getDatabase(context).imageRecordDao()
    private val imageDir = File(context.filesDir, "images").apply { mkdirs() }

    companion object {
        private const val TAG = "ImageRepository"
        private const val MAX_UPLOAD_DIMENSION = 1536
        private const val JPEG_QUALITY = 95

        fun loadBitmap(filePath: String): Bitmap? {
            return try {
                BitmapFactory.decodeFile(filePath)
            } catch (e: Exception) {
                null
            }
        }

        fun bitmapToBytes(bitmap: Bitmap): ByteArray {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            return stream.toByteArray()
        }
    }

    fun isConfigured(): Boolean = SettingsManager.isConfigured(context)

    suspend fun generateImage(prompt: String, size: String, quality: String): Result<ImageRecord> {
        return withContext(Dispatchers.IO) {
            val baseUrl = SettingsManager.getApiBaseUrl(context)
            val apiKey = SettingsManager.getApiKey(context)
            val model = SettingsManager.getModelName(context)

            val result = ImageApiService.generateImage(baseUrl, apiKey, model, prompt, size, quality)

            result.fold(
                onSuccess = { imageData ->
                    val filePath = saveImageData(imageData)
                    if (filePath != null) {
                        val record = ImageRecord(
                            prompt = prompt,
                            imageFilePath = filePath,
                            size = size,
                            quality = quality,
                            model = model,
                            createdAt = System.currentTimeMillis(),
                            type = "generate"
                        )
                        val id = dao.insert(record)
                        Result.success(record.copy(id = id))
                    } else {
                        Result.failure(Exception("图片保存失败"))
                    }
                },
                onFailure = { Result.failure(it) }
            )
        }
    }

    suspend fun editImage(
        sourceRecord: ImageRecord,
        editPrompt: String,
        size: String,
        quality: String
    ): Result<ImageRecord> {
        return withContext(Dispatchers.IO) {
            val baseUrl = SettingsManager.getApiBaseUrl(context)
            val apiKey = SettingsManager.getApiKey(context)
            val model = SettingsManager.getModelName(context)

            // Read source image bytes
            val sourceFile = File(sourceRecord.imageFilePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("原图文件不存在"))
            }

            // Compress image before upload to avoid timeout
            val compressedBytes = compressImageForUpload(sourceFile)
            Log.i(TAG, "Image compressed: ${sourceFile.length()} bytes -> ${compressedBytes.size} bytes")

            val result = ImageApiService.editImage(
                baseUrl, apiKey, model, compressedBytes, editPrompt, size, quality
            )

            result.fold(
                onSuccess = { imageData ->
                    val filePath = saveImageData(imageData)
                    if (filePath != null) {
                        val record = ImageRecord(
                            prompt = editPrompt,
                            imageFilePath = filePath,
                            size = size,
                            quality = quality,
                            model = model,
                            createdAt = System.currentTimeMillis(),
                            parentId = sourceRecord.id,
                            type = "edit"
                        )
                        val id = dao.insert(record)
                        Result.success(record.copy(id = id))
                    } else {
                        Result.failure(Exception("编辑后的图片保存失败"))
                    }
                },
                onFailure = { Result.failure(it) }
            )
        }
    }

    /**
     * Compress and resize image for API upload.
     * - Max dimension: 1536px (only shrinks if larger)
     * - Format: JPEG at 95% quality (near-lossless)
     * - Returns ByteArray suitable for multipart upload
     */
    private fun compressImageForUpload(file: File): ByteArray {
        return try {
            // Decode with bounds first to check size
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

            val srcW = boundsOptions.outWidth
            val srcH = boundsOptions.outHeight

            // Calculate inSampleSize to reduce memory
            var sampleSize = 1
            val maxDim = maxOf(srcW, srcH)
            while (maxDim / sampleSize > MAX_UPLOAD_DIMENSION * 2) {
                sampleSize *= 2
            }

            // Decode at reduced size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            var bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)

            if (bitmap == null) {
                // If decode fails, return raw bytes
                Log.w(TAG, "Bitmap decode failed, sending raw bytes")
                return file.readBytes()
            }

            // Further scale if still too large
            val curMaxDim = maxOf(bitmap.width, bitmap.height)
            if (curMaxDim > MAX_UPLOAD_DIMENSION) {
                val scale = MAX_UPLOAD_DIMENSION.toFloat() / curMaxDim
                val newW = (bitmap.width * scale).toInt()
                val newH = (bitmap.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                }
                bitmap = scaled
            }

            // Compress to JPEG bytes
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            bitmap.recycle()
            stream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Image compression failed, sending raw bytes", e)
            file.readBytes()
        }
    }

    /**
     * Save base64 or url image data to internal storage
     * Returns file path or null on failure
     */
    private fun saveImageData(imageData: String): String? {
        val timestamp = System.currentTimeMillis()
        val outFile = File(imageDir, "img_$timestamp.png")

        return try {
            if (imageData.startsWith("url:")) {
                // Download from URL
                val url = imageData.removePrefix("url:")
                URL(url).openStream().use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                // Decode base64 and save
                val bytes = Base64.decode(imageData, Base64.DEFAULT)
                outFile.writeBytes(bytes)
            }
            outFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image", e)
            null
        }
    }

    fun getAllRecords() = dao.getAllRecords()
    fun getRootRecords() = dao.getRootRecords()
    fun getChildRecords(parentId: Long) = dao.getChildRecords(parentId)

    suspend fun getRecordById(id: Long) = dao.getRecordById(id)

    suspend fun deleteRecord(record: ImageRecord) {
        withContext(Dispatchers.IO) {
            // Delete file
            File(record.imageFilePath).delete()
            dao.delete(record)
        }
    }

    suspend fun deleteRecordById(id: Long) {
        withContext(Dispatchers.IO) {
            dao.deleteById(id)
        }
    }
}
