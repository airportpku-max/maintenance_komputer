package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class ProgressRequestBody(
    private val contentType: String,
    private val contentBytes: ByteArray,
    private val onProgress: (Float) -> Unit
) : RequestBody() {

    override fun contentType() = contentType.toMediaTypeOrNull()

    override fun contentLength() = contentBytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var uploaded = 0L
        val buffer = ByteArray(2048)
        val inputStream = contentBytes.inputStream()

        try {
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                uploaded += read
                val progress = if (total > 0) uploaded.toFloat() / total.toFloat() else 0f
                onProgress(progress)
            }
        } finally {
            inputStream.close()
        }
    }
}

class CloudStorageManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024 // 5 MB limit
    }

    /**
     * Compress bitmap or URI to JPEG byte array below MAX_FILE_SIZE_BYTES (5 MB).
     * Scales max dimension to 1920px and compresses to 80% JPEG quality.
     */
    suspend fun compressImage(
        context: Context,
        imageUri: Uri,
        maxDimension: Int = 1920,
        quality: Int = 80
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            if (inputStream == null) return@withContext Result.failure(Exception("Cannot open image stream"))

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            val originalBytes = inputStream.readBytes()
            inputStream.close()

            BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, options)

            // Calculate sample size
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, decodeOptions)
                ?: return@withContext Result.failure(Exception("Failed to decode bitmap"))

            var currentQuality = quality
            var outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream)
            var resultBytes = outputStream.toByteArray()

            // If still larger than 5MB, iteratively reduce quality
            while (resultBytes.size > MAX_FILE_SIZE_BYTES && currentQuality > 20) {
                currentQuality -= 15
                outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream)
                resultBytes = outputStream.toByteArray()
            }

            bitmap.recycle()

            if (resultBytes.size > MAX_FILE_SIZE_BYTES) {
                Result.failure(Exception("Ukuran file melebihi batas maksimal 5 MB (${resultBytes.size / (1024 * 1024)} MB)"))
            } else {
                Result.success(resultBytes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Compress bitmap directly
     */
    suspend fun compressBitmap(
        bitmap: Bitmap,
        quality: Int = 80
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            var currentQuality = quality
            var outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream)
            var resultBytes = outputStream.toByteArray()

            while (resultBytes.size > MAX_FILE_SIZE_BYTES && currentQuality > 20) {
                currentQuality -= 15
                outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream)
                resultBytes = outputStream.toByteArray()
            }

            if (resultBytes.size > MAX_FILE_SIZE_BYTES) {
                Result.failure(Exception("Ukuran gambar melebihi batas 5 MB"))
            } else {
                Result.success(resultBytes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload Uri or Local File Path (PDF or Image) to Cloudflare Worker -> R2
     */
    suspend fun uploadUriToR2(
        context: Context,
        workerUrl: String,
        uriString: String,
        fileNameHint: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (uriString.isBlank() || uriString.startsWith("http://") || uriString.startsWith("https://")) {
            return@withContext Result.success(uriString)
        }
        if (workerUrl.isBlank()) {
            return@withContext Result.failure(Exception("URL Cloudflare Worker belum dikonfigurasi!"))
        }

        try {
            val uri = Uri.parse(uriString)
            val contentResolver = context.contentResolver
            val isPdf = uriString.endsWith(".pdf", ignoreCase = true) ||
                    fileNameHint?.endsWith(".pdf", ignoreCase = true) == true ||
                    (contentResolver.getType(uri)?.contains("pdf", ignoreCase = true) == true)

            val mimeType = if (isPdf) "application/pdf" else "image/jpeg"

            val bytes: ByteArray = if (isPdf) {
                val inputStream = if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                    contentResolver.openInputStream(uri)
                } else {
                    java.io.File(uriString).inputStream()
                } ?: return@withContext Result.failure(Exception("Gagal membuka file PDF: $uriString"))
                val raw = inputStream.readBytes()
                inputStream.close()
                if (raw.size > MAX_FILE_SIZE_BYTES) {
                    return@withContext Result.failure(Exception("Ukuran file PDF (${raw.size / (1024 * 1024)} MB) melebihi batas 5 MB"))
                }
                raw
            } else {
                val compRes = compressImage(context, uri)
                if (compRes.isSuccess) {
                    compRes.getOrThrow()
                } else {
                    val inputStream = if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                        contentResolver.openInputStream(uri)
                    } else {
                        java.io.File(uriString).inputStream()
                    } ?: return@withContext Result.failure(Exception("Gagal membaca file gambar: $uriString"))
                    val raw = inputStream.readBytes()
                    inputStream.close()
                    raw
                }
            }

            uploadToR2(
                workerUrl = workerUrl,
                fileBytes = bytes,
                mimeType = mimeType
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload image to Cloudflare Worker -> Cloudflare R2
     * Features:
     * - Unique filename with UUID + Timestamp
     * - Progress callback
     * - Retry logic (up to maxRetries)
     */
    suspend fun uploadToR2(
        workerUrl: String,
        fileBytes: ByteArray,
        mimeType: String = "image/jpeg",
        maxRetries: Int = 3,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        if (workerUrl.isBlank()) {
            return@withContext Result.failure(Exception("URL Cloudflare Worker belum dikonfigurasi!"))
        }

        if (fileBytes.size > MAX_FILE_SIZE_BYTES) {
            return@withContext Result.failure(Exception("Ukuran file (${fileBytes.size / (1024 * 1024)} MB) melebihi batas 5 MB!"))
        }

        // Generate unique filename using UUID + timestamp
        val extension = if (mimeType.contains("pdf")) "pdf" else "jpg"
        val uniqueFileName = "${UUID.randomUUID()}_${System.currentTimeMillis()}.$extension"

        val progressBody = ProgressRequestBody(mimeType, fileBytes, onProgress)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", uniqueFileName, progressBody)
            .addFormDataPart("filename", uniqueFileName)
            .build()

        val cleanUrl = workerUrl.trimEnd('/')
        val uploadUrl = if (cleanUrl.endsWith("/upload")) cleanUrl else "$cleanUrl/upload"

        var attempt = 0
        var lastException: Exception? = null

        while (attempt < maxRetries) {
            attempt++
            try {
                val request = Request.Builder()
                    .url(uploadUrl)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBodyStr = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(responseBodyStr)
                    val rawUrl = if (json.has("url")) json.optString("url", "") else if (json.has("fileUrl")) json.optString("fileUrl", "") else ""
                    val fileKey = json.optString("fileKey", "")
                    
                    var finalUrl = rawUrl
                    if (finalUrl.isBlank() || finalUrl.contains("pub-r2.yourdomain.com") || finalUrl.contains("example.com") || finalUrl.contains("yourdomain.com")) {
                        val keyToUse = if (fileKey.isNotBlank()) fileKey else if (rawUrl.isNotBlank()) rawUrl.substringAfterLast("/") else uniqueFileName
                        val cleanWorker = workerUrl.trimEnd('/').removeSuffix("/upload")
                        finalUrl = "$cleanWorker/$keyToUse"
                    }

                    if (finalUrl.isNotEmpty()) {
                        android.util.Log.d("CloudStorageManager", "Uploaded to R2 successfully: $finalUrl")
                        return@withContext Result.success(finalUrl)
                    } else {
                        return@withContext Result.failure(Exception("Response Cloudflare Worker tidak berisi URL file: $responseBodyStr"))
                    }
                } else {
                    lastException = Exception("Upload gagal (HTTP ${response.code}): $responseBodyStr")
                }
            } catch (e: Exception) {
                lastException = e
            }

            if (attempt < maxRetries) {
                delay(1000L * attempt) // Retry delay
            }
        }

        Result.failure(lastException ?: Exception("Upload ke Cloudflare R2 gagal setelah $maxRetries percobaan"))
    }

    /**
     * Delete file from Cloudflare R2 via Cloudflare Worker API
     */
    suspend fun deleteFromR2(
        workerUrl: String,
        fileUrlOrKey: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (workerUrl.isBlank() || fileUrlOrKey.isBlank()) {
            return@withContext Result.success(true)
        }

        try {
            val cleanUrl = workerUrl.trimEnd('/')
            val deleteEndpoint = if (cleanUrl.endsWith("/delete") || cleanUrl.endsWith("/file")) cleanUrl else "$cleanUrl/delete"

            // Extract filename/key from full URL if full URL is passed
            val fileKey = if (fileUrlOrKey.contains("/")) {
                fileUrlOrKey.substringBefore("?").substringAfterLast("/")
            } else {
                fileUrlOrKey
            }

            android.util.Log.d("CloudStorageManager", "Deleting R2 fileKey: $fileKey via endpoint: $deleteEndpoint")

            val request = Request.Builder()
                .url("$deleteEndpoint?key=$fileKey")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            android.util.Log.d("CloudStorageManager", "Delete R2 response code: ${response.code}, body: $responseBody")

            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("Gagal menghapus file dari R2 (HTTP ${response.code}): $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
