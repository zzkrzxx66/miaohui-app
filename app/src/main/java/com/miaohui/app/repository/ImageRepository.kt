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
        private const val MAX_UPLOAD_DIMENSION = 1024
        private const val JPEG_QUALITY = 80
        private const val MAX_UPLOAD_BYTES = 800_000 // 800KB per image, safe for nginx 1MB limit
        private const val MAX_MASK_DIMENSION = 512

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

    suspend fun generateImage(prompt: String, size: String, quality: String, n: Int = 1): Result<List<ImageRecord>> {
        return withContext(Dispatchers.IO) {
            val baseUrl = SettingsManager.getApiBaseUrl(context)
            val apiKey = SettingsManager.getApiKey(context)
            val model = SettingsManager.getModelName(context)

            val result = ImageApiService.generateImage(baseUrl, apiKey, model, prompt, size, quality, n)

            result.fold(
                onSuccess = { imageDataList ->
                    val records = mutableListOf<ImageRecord>()
                    for (imageData in imageDataList) {
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
                            records.add(record.copy(id = id))
                        }
                    }
                    if (records.isEmpty()) {
                        Result.failure(Exception("图片保存失败"))
                    } else {
                        Result.success(records)
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
        quality: String,
        maskPath: String? = null
    ): Result<ImageRecord> {
        return withContext(Dispatchers.IO) {
            val baseUrl = SettingsManager.getApiBaseUrl(context)
            val apiKey = SettingsManager.getApiKey(context)
            val model = SettingsManager.getModelName(context)

            val sourceFile = File(sourceRecord.imageFilePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("原图文件不存在"))
            }

            val compressedBytes = compressImageForUpload(sourceFile)
            Log.i(TAG, "Image compressed: ${sourceFile.length()} bytes -> ${compressedBytes.size} bytes")

            // Read mask if provided
            val maskBytes = if (maskPath != null) {
                val maskFile = File(maskPath)
                if (maskFile.exists()) compressMaskForUpload(maskFile) else null
            } else null

            val result = ImageApiService.editImage(
                baseUrl, apiKey, model, listOf(compressedBytes), editPrompt, size, quality, maskBytes
            )

            result.fold(
                onSuccess = { imageDataList ->
                    val imageData = imageDataList.firstOrNull() ?: return@fold Result.failure(Exception("无编辑结果"))
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
     * 多图编辑：上传多张参考图 + prompt → 融合生成新图片
     */
    suspend fun editImageMulti(
        imagePaths: List<String>,
        prompt: String,
        size: String,
        quality: String,
        maskPath: String? = null
    ): Result<ImageRecord> {
        return withContext(Dispatchers.IO) {
            val baseUrl = SettingsManager.getApiBaseUrl(context)
            val apiKey = SettingsManager.getApiKey(context)
            val model = SettingsManager.getModelName(context)

            if (imagePaths.isEmpty()) {
                return@withContext Result.failure(Exception("请至少选择一张图片"))
            }

            val compressedList = imagePaths.mapNotNull { path ->
                val file = File(path)
                if (file.exists()) compressImageForUpload(file) else null
            }

            if (compressedList.isEmpty()) {
                return@withContext Result.failure(Exception("无法读取图片"))
            }

            val maskBytes = if (maskPath != null) {
                val maskFile = File(maskPath)
                if (maskFile.exists()) compressMaskForUpload(maskFile) else null
            } else null

            val result = ImageApiService.editImage(
                baseUrl, apiKey, model, compressedList, prompt, size, quality, maskBytes
            )

            result.fold(
                onSuccess = { imageDataList ->
                    val imageData = imageDataList.firstOrNull() ?: return@fold Result.failure(Exception("无编辑结果"))
                    val filePath = saveImageData(imageData)
                    if (filePath != null) {
                        val record = ImageRecord(
                            prompt = prompt,
                            imageFilePath = filePath,
                            size = size,
                            quality = quality,
                            model = model,
                            createdAt = System.currentTimeMillis(),
                            type = "multi_edit"
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

    /**
     * 以图生图：上传参考图 + prompt → 生成新图片
     */
    suspend fun imageToImage(
        imagePath: String,
        prompt: String,
        size: String,
        quality: String
    ): Result<ImageRecord> {
        return withContext(Dispatchers.IO) {
            val baseUrl = SettingsManager.getApiBaseUrl(context)
            val apiKey = SettingsManager.getApiKey(context)
            val model = SettingsManager.getModelName(context)

            val sourceFile = File(imagePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("参考图片不存在"))
            }

            // Copy selected image to app's image directory for record
            val timestamp = System.currentTimeMillis()
            val savedRef = File(imageDir, "ref_$timestamp.png")
            try {
                sourceFile.inputStream().use { input ->
                    savedRef.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("读取参考图片失败"))
            }

            val compressedBytes = compressImageForUpload(savedRef)
            Log.i(TAG, "Image-to-image: ref compressed ${savedRef.length()} -> ${compressedBytes.size} bytes")

            val result = ImageApiService.editImage(
                baseUrl, apiKey, model, listOf(compressedBytes), prompt, size, quality, null
            )

            result.fold(
                onSuccess = { imageDataList ->
                    val imageData = imageDataList.firstOrNull() ?: return@fold Result.failure(Exception("无生成结果"))
                    val filePath = saveImageData(imageData)
                    if (filePath != null) {
                        val record = ImageRecord(
                            prompt = prompt,
                            imageFilePath = filePath,
                            size = size,
                            quality = quality,
                            model = model,
                            createdAt = System.currentTimeMillis(),
                            type = "img2img"
                        )
                        val id = dao.insert(record)
                        Result.success(record.copy(id = id))
                    } else {
                        Result.failure(Exception("生成图片保存失败"))
                    }
                },
                onFailure = { Result.failure(it) }
            )
        }
    }

    /**
     * Compress and resize image for API upload.
     * - Max dimension: 1024px (only shrinks if larger)
     * - Format: JPEG with progressive quality reduction
     * - Auto-reduces quality if result exceeds MAX_UPLOAD_BYTES
     * - Returns ByteArray suitable for multipart upload
     */
    private fun compressImageForUpload(file: File): ByteArray {
        return compressForUpload(file, MAX_UPLOAD_DIMENSION, MAX_UPLOAD_BYTES)
    }

    /**
     * Compress mask image more aggressively:
     * - Max dimension: 512px (mask only needs shape info)
     * - Max size: 200KB
     */
    private fun compressMaskForUpload(file: File): ByteArray {
        return compressForUpload(file, MAX_MASK_DIMENSION, 200_000)
    }

    private fun compressForUpload(file: File, maxDimension: Int, maxBytes: Int): ByteArray {
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
            while (maxDim / sampleSize > maxDimension * 2) {
                sampleSize *= 2
            }

            // Decode at reduced size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            var bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)

            if (bitmap == null) {
                Log.w(TAG, "Bitmap decode failed, sending raw bytes")
                return file.readBytes()
            }

            // Further scale if still too large
            val curMaxDim = maxOf(bitmap.width, bitmap.height)
            if (curMaxDim > maxDimension) {
                val scale = maxDimension.toFloat() / curMaxDim
                val newW = (bitmap.width * scale).toInt()
                val newH = (bitmap.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                }
                bitmap = scaled
            }

            // Progressive compression: try decreasing quality until under size limit
            var quality = JPEG_QUALITY
            var result: ByteArray
            while (true) {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                result = stream.toByteArray()
                if (result.size <= maxBytes || quality <= 30) {
                    break
                }
                quality -= 15
                Log.d(TAG, "Compressed ${result.size} bytes > ${maxBytes}, retrying at quality ${quality}")
            }

            bitmap.recycle()
            Log.i(TAG, "Image compressed: ${file.length()} bytes -> ${result.size} bytes (quality=$quality)")
            result
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
