package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MaintenanceViewModel(
    private val repository: MaintenanceRepository,
    private val applicationContext: android.content.Context
) : ViewModel() {

    private val syncMutex = Mutex()

    // Exposure of Flows
    val devices: StateFlow<List<Device>> = repository.devices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val technicians: StateFlow<List<Technician>> = repository.technicians
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val troubleTickets: StateFlow<List<TroubleTicket>> = repository.troubleTickets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val maintenanceLogs: StateFlow<List<MaintenanceLog>> = repository.maintenanceLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uploadedFiles: StateFlow<List<UploadedFile>> = repository.uploadedFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cloudflare R2 & Supabase Integration
    val cloudStorageManager = com.example.data.remote.CloudStorageManager()
    val supabaseRepository = com.example.data.remote.SupabaseRepository()

    val uploadProgress = MutableStateFlow(0f)
    val isUploadingFile = MutableStateFlow(false)
    val uploadStatusMessage = MutableStateFlow("")

    suspend fun uploadPhotoToCloudflareR2(
        context: android.content.Context,
        imageUri: android.net.Uri,
        workerUrl: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        isUploadingFile.value = true
        uploadProgress.value = 0f
        uploadStatusMessage.value = "Mengompres gambar..."

        val compressionResult = cloudStorageManager.compressImage(context, imageUri)
        if (compressionResult.isFailure) {
            isUploadingFile.value = false
            return Result.failure(compressionResult.exceptionOrNull() ?: Exception("Gagal kompres gambar"))
        }
        val compressedBytes = compressionResult.getOrThrow()

        uploadStatusMessage.value = "Mengunggah ke Cloudflare R2..."
        val uploadResult = cloudStorageManager.uploadToR2(
            workerUrl = workerUrl,
            fileBytes = compressedBytes,
            mimeType = "image/jpeg",
            maxRetries = 3,
            onProgress = { progress ->
                uploadProgress.value = progress
                onProgress(progress)
            }
        )

        isUploadingFile.value = false
        if (uploadResult.isSuccess) {
            uploadStatusMessage.value = "Upload Berhasil!"
        } else {
            uploadStatusMessage.value = "Upload Gagal: ${uploadResult.exceptionOrNull()?.message}"
        }

        return uploadResult
    }

    suspend fun deletePhotoFromCloudflareR2(workerUrl: String, photoUrl: String): Result<Boolean> {
        return cloudStorageManager.deleteFromR2(workerUrl, photoUrl)
    }

    suspend fun uploadUriToCloudflareR2(
        context: android.content.Context,
        uri: android.net.Uri,
        fileNameHint: String? = null
    ): Result<String> {
        val supabasePrefs = context.getSharedPreferences("supabase_r2_prefs", android.content.Context.MODE_PRIVATE)
        val workerUrl = supabasePrefs.getString("cloudflare_worker_url", "") ?: ""
        if (workerUrl.isBlank()) {
            return Result.failure(Exception("URL Cloudflare Worker R2 belum dikonfigurasi di Pengaturan / Profil!"))
        }
        return cloudStorageManager.uploadUriToR2(
            context = context,
            workerUrl = workerUrl,
            uriString = uri.toString(),
            fileNameHint = fileNameHint
        )
    }

    private suspend fun uploadLogPhotosToR2(log: MaintenanceLog, workerUrl: String): MaintenanceLog {
        if (workerUrl.isBlank()) return log
        suspend fun uploadOne(uri: String?): String? {
            if (uri.isNullOrBlank() || uri.startsWith("http")) return uri
            val res = cloudStorageManager.uploadUriToR2(applicationContext, workerUrl, uri)
            return if (res.isSuccess) res.getOrNull() else uri
        }
        return log.copy(
            healthReportBeforePhoto = uploadOne(log.healthReportBeforePhoto),
            healthReportAfterPhoto = uploadOne(log.healthReportAfterPhoto),
            diskCleanupBeforePhoto = uploadOne(log.diskCleanupBeforePhoto),
            diskCleanupAfterPhoto = uploadOne(log.diskCleanupAfterPhoto),
            hardwareCleanupBeforePhoto = uploadOne(log.hardwareCleanupBeforePhoto),
            hardwareCleanupAfterPhoto = uploadOne(log.hardwareCleanupAfterPhoto),
            checkingDriveErrorBeforePhoto = uploadOne(log.checkingDriveErrorBeforePhoto),
            checkingDriveErrorAfterPhoto = uploadOne(log.checkingDriveErrorAfterPhoto),
            scanningVirusBeforePhoto = uploadOne(log.scanningVirusBeforePhoto),
            scanningVirusAfterPhoto = uploadOne(log.scanningVirusAfterPhoto),
            checkingNetworkBeforePhoto = uploadOne(log.checkingNetworkBeforePhoto),
            checkingNetworkAfterPhoto = uploadOne(log.checkingNetworkAfterPhoto),
            updatingAntivirusBeforePhoto = uploadOne(log.updatingAntivirusBeforePhoto),
            updatingAntivirusAfterPhoto = uploadOne(log.updatingAntivirusAfterPhoto),
            updatingAplikasiBeforePhoto = uploadOne(log.updatingAplikasiBeforePhoto),
            updatingAplikasiAfterPhoto = uploadOne(log.updatingAplikasiAfterPhoto)
        )
    }

    suspend fun syncDatabaseWithSupabase(
        supabaseUrl: String,
        anonKey: String,
        workerUrl: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || anonKey.isBlank()) {
            return@withContext Result.failure(Exception("Supabase URL dan Anon Key belum dikonfigurasi!"))
        }

        syncMutex.withLock {
            try {
                // 1. Fetch current remote lists from Supabase
                val remoteDevsRes = supabaseRepository.getDevices(supabaseUrl, anonKey)
                val remoteTicketsRes = supabaseRepository.getTroubleTickets(supabaseUrl, anonKey)
                val remoteLogsRes = supabaseRepository.getMaintenanceLogs(supabaseUrl, anonKey)
                val remoteFilesRes = supabaseRepository.getUploadedFiles(supabaseUrl, anonKey)

                // Jangan batalkan seluruh sinkronisasi jika salah satu tabel sekunder (seperti uploaded_files) gagal
                if (remoteDevsRes.isFailure && remoteLogsRes.isFailure) {
                    val errorMsg = remoteDevsRes.exceptionOrNull()?.message 
                        ?: remoteLogsRes.exceptionOrNull()?.message
                        ?: "Gagal mengambil data dari Supabase"
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val remoteDevs = remoteDevsRes.getOrDefault(emptyList()).toMutableList()
                val remoteTickets = remoteTicketsRes.getOrDefault(emptyList()).toMutableList()
                val remoteLogs = remoteLogsRes.getOrDefault(emptyList()).toMutableList()
                val remoteFiles = remoteFilesRes.getOrDefault(emptyList()).toMutableList()

                val localDevs = repository.getDevicesList()
                val localTickets = repository.getTroubleTicketsList()
                val localLogs = repository.getMaintenanceLogsList()
                val localFiles = repository.getUploadedFilesList()

                // 2. Identify local items NOT yet present in Supabase, and push them to Supabase

                // A. Maintenance Logs
                // Match by (id > 0 and in remote) OR (deviceName + timestamp)
                val remoteLogKeys = remoteLogs.map { "${it.deviceName}_${it.timestamp}" }.toSet()
                val remoteLogIds = remoteLogs.map { it.id }.filter { it > 0 }.toSet()
                val newLogsToPush = localLogs.filter { log ->
                    !remoteLogIds.contains(log.id) && !remoteLogKeys.contains("${log.deviceName}_${log.timestamp}")
                }

                if (newLogsToPush.isNotEmpty()) {
                    val uploadedLogs = if (workerUrl.isNotBlank()) {
                        newLogsToPush.map { uploadLogPhotosToR2(it, workerUrl) }
                    } else newLogsToPush
                    val pushRes = supabaseRepository.insertNewMaintenanceLogs(supabaseUrl, anonKey, uploadedLogs)
                    if (pushRes.isFailure) {
                        android.util.Log.e("SyncLogs", "Gagal push logs baru ke Supabase: ${pushRes.exceptionOrNull()?.message}")
                    }
                }

                // B. Devices
                // Match by serialNumber (barcode) or id
                val newDevsToPush = mutableListOf<Device>()
                val existingDevsToUpdate = mutableListOf<Device>()

                for (dev in localDevs) {
                    val barcode = dev.serialNumber.trim().lowercase()
                    val matched = remoteDevs.find { 
                        (dev.id > 0 && it.id == dev.id) || 
                        (barcode.isNotBlank() && it.serialNumber.trim().lowercase() == barcode) 
                    }
                    if (matched == null) {
                        newDevsToPush.add(dev)
                    } else if (dev.lastMaintenance > matched.lastMaintenance || dev.condition != matched.condition) {
                        existingDevsToUpdate.add(dev.copy(id = matched.id))
                    }
                }

                for (dev in newDevsToPush) {
                    val res = supabaseRepository.saveOrUpdateSingleDevice(supabaseUrl, anonKey, dev)
                    if (res.isSuccess) {
                        res.getOrNull()?.let { repository.addDevice(it) }
                    } else {
                        android.util.Log.e("SyncDevices", "Gagal push device ${dev.name}: ${res.exceptionOrNull()?.message}")
                    }
                }
                for (dev in existingDevsToUpdate) {
                    val res = supabaseRepository.saveOrUpdateSingleDevice(supabaseUrl, anonKey, dev)
                    if (res.isFailure) {
                        android.util.Log.e("SyncDevices", "Gagal update device ${dev.name}: ${res.exceptionOrNull()?.message}")
                    }
                }

                // C. Trouble Tickets
                val remoteTicketKeys = remoteTickets.map { "${it.deviceName}_${it.timestamp}" }.toSet()
                val remoteTicketIds = remoteTickets.map { it.id }.filter { it > 0 }.toSet()

                val newTicketsToPush = mutableListOf<TroubleTicket>()
                val existingTicketsToUpdate = mutableListOf<TroubleTicket>()

                for (ticket in localTickets) {
                    val key = "${ticket.deviceName}_${ticket.timestamp}"
                    val matched = remoteTickets.find { 
                        (ticket.id > 0 && it.id == ticket.id) || 
                        (ticket.deviceName.isNotBlank() && key == "${it.deviceName}_${it.timestamp}") 
                    }
                    if (matched == null) {
                        newTicketsToPush.add(ticket)
                    } else if (ticket.status != matched.status || ticket.actionTaken != matched.actionTaken) {
                        existingTicketsToUpdate.add(ticket.copy(id = matched.id))
                    }
                }

                if (newTicketsToPush.isNotEmpty()) {
                    supabaseRepository.insertNewTroubleTickets(supabaseUrl, anonKey, newTicketsToPush)
                }
                if (existingTicketsToUpdate.isNotEmpty()) {
                    supabaseRepository.upsertTroubleTickets(supabaseUrl, anonKey, existingTicketsToUpdate)
                }

                // D. Uploaded Files
                val remoteFileNames = remoteFiles.map { it.fileName.trim().lowercase() }.toSet()
                val newFilesToPush = localFiles.filter { !remoteFileNames.contains(it.fileName.trim().lowercase()) }
                if (newFilesToPush.isNotEmpty()) {
                    supabaseRepository.insertNewUploadedFiles(supabaseUrl, anonKey, newFilesToPush)
                }

                // 3. Re-fetch fresh state from Supabase after pushing
                val freshDevs = supabaseRepository.getDevices(supabaseUrl, anonKey).getOrDefault(remoteDevs)
                val freshTickets = supabaseRepository.getTroubleTickets(supabaseUrl, anonKey).getOrDefault(remoteTickets)
                val freshLogs = supabaseRepository.getMaintenanceLogs(supabaseUrl, anonKey).getOrDefault(remoteLogs)
                val freshFiles = supabaseRepository.getUploadedFiles(supabaseUrl, anonKey).getOrDefault(remoteFiles)

                // 4. Merge photo URIs from local if remote photo is blank
                val localDevsMap = localDevs.associateBy { it.id }
                val localTicketsMap = localTickets.associateBy { it.id }
                val localLogsMap = localLogs.associateBy { it.id }

                val mergedDevs = freshDevs.map { r ->
                    val l = localDevsMap[r.id]
                    if (l != null) {
                        r.copy(
                            photoUri = if (r.photoUri.isNullOrBlank()) l.photoUri else r.photoUri,
                            baFileUri = if (r.baFileUri.isNullOrBlank()) l.baFileUri else r.baFileUri,
                            baFileName = if (r.baFileName.isNullOrBlank()) l.baFileName else r.baFileName
                        )
                    } else r
                }

                val mergedTickets = freshTickets.map { r ->
                    val l = localTicketsMap[r.id]
                    if (l != null) {
                        r.copy(
                            photoBefore = if (r.photoBefore.isNullOrBlank()) l.photoBefore else r.photoBefore,
                            photoAfter = if (r.photoAfter.isNullOrBlank()) l.photoAfter else r.photoAfter
                        )
                    } else r
                }

                val mergedLogs = freshLogs.map { r ->
                    val l = localLogsMap[r.id]
                    if (l != null) {
                        r.copy(
                            healthReportBeforePhoto = if (r.healthReportBeforePhoto.isNullOrBlank()) l.healthReportBeforePhoto else r.healthReportBeforePhoto,
                            healthReportAfterPhoto = if (r.healthReportAfterPhoto.isNullOrBlank()) l.healthReportAfterPhoto else r.healthReportAfterPhoto,
                            diskCleanupBeforePhoto = if (r.diskCleanupBeforePhoto.isNullOrBlank()) l.diskCleanupBeforePhoto else r.diskCleanupBeforePhoto,
                            diskCleanupAfterPhoto = if (r.diskCleanupAfterPhoto.isNullOrBlank()) l.diskCleanupAfterPhoto else r.diskCleanupAfterPhoto,
                            hardwareCleanupBeforePhoto = if (r.hardwareCleanupBeforePhoto.isNullOrBlank()) l.hardwareCleanupBeforePhoto else r.hardwareCleanupBeforePhoto,
                            hardwareCleanupAfterPhoto = if (r.hardwareCleanupAfterPhoto.isNullOrBlank()) l.hardwareCleanupAfterPhoto else r.hardwareCleanupAfterPhoto,
                            checkingDriveErrorBeforePhoto = if (r.checkingDriveErrorBeforePhoto.isNullOrBlank()) l.checkingDriveErrorBeforePhoto else r.checkingDriveErrorBeforePhoto,
                            checkingDriveErrorAfterPhoto = if (r.checkingDriveErrorAfterPhoto.isNullOrBlank()) l.checkingDriveErrorAfterPhoto else r.checkingDriveErrorAfterPhoto,
                            scanningVirusBeforePhoto = if (r.scanningVirusBeforePhoto.isNullOrBlank()) l.scanningVirusBeforePhoto else r.scanningVirusBeforePhoto,
                            scanningVirusAfterPhoto = if (r.scanningVirusAfterPhoto.isNullOrBlank()) l.scanningVirusAfterPhoto else r.scanningVirusAfterPhoto,
                            checkingNetworkBeforePhoto = if (r.checkingNetworkBeforePhoto.isNullOrBlank()) l.checkingNetworkBeforePhoto else r.checkingNetworkBeforePhoto,
                            checkingNetworkAfterPhoto = if (r.checkingNetworkAfterPhoto.isNullOrBlank()) l.checkingNetworkAfterPhoto else r.checkingNetworkAfterPhoto,
                            updatingAntivirusBeforePhoto = if (r.updatingAntivirusBeforePhoto.isNullOrBlank()) l.updatingAntivirusBeforePhoto else r.updatingAntivirusBeforePhoto,
                            updatingAntivirusAfterPhoto = if (r.updatingAntivirusAfterPhoto.isNullOrBlank()) l.updatingAntivirusAfterPhoto else r.updatingAntivirusAfterPhoto,
                            updatingAplikasiBeforePhoto = if (r.updatingAplikasiBeforePhoto.isNullOrBlank()) l.updatingAplikasiBeforePhoto else r.updatingAplikasiBeforePhoto,
                            updatingAplikasiAfterPhoto = if (r.updatingAplikasiAfterPhoto.isNullOrBlank()) l.updatingAplikasiAfterPhoto else r.updatingAplikasiAfterPhoto
                        )
                    } else r
                }

                // 5. Overwrite local database directly with exact state from Supabase
                // Safeguard against accidentally clearing data if fresh fetch was empty while local had data
                val finalLogs = if (mergedLogs.isEmpty() && localLogs.isNotEmpty()) localLogs else mergedLogs
                val finalDevs = if (mergedDevs.isEmpty() && localDevs.isNotEmpty()) localDevs else mergedDevs
                val finalTickets = if (mergedTickets.isEmpty() && localTickets.isNotEmpty()) localTickets else mergedTickets

                repository.overwriteDatabaseFromSync(
                    devices = finalDevs,
                    troubleTickets = finalTickets,
                    maintenanceLogs = finalLogs,
                    uploadedFiles = freshFiles
                )

                Result.success("Berhasil sinkronisasi dengan Supabase PostgreSQL & Cloudflare R2!")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun restoreDevicesToSupabase(
        supabaseUrl: String,
        anonKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || anonKey.isBlank()) {
            return@withContext Result.failure(Exception("Supabase URL dan Anon Key belum dikonfigurasi!"))
        }

        var localDevs = repository.getDevicesList()

        // If local devices is empty, check if we can fetch from Google Sheets
        if (localDevs.isEmpty()) {
            val sharedPrefs = applicationContext.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
            val webAppUrl = sharedPrefs.getString("web_app_url", "") ?: ""
            if (webAppUrl.isNotBlank()) {
                val pullResult = performGoogleSheetsPullAll(webAppUrl)
                if (pullResult.first) {
                    localDevs = repository.getDevicesList()
                }
            }
        }

        if (localDevs.isEmpty()) {
            return@withContext Result.failure(Exception("Data perangkat di HP dan Google Sheets masih kosong. Silakan tambahkan perangkat atau hubungkan Google Sheets terlebih dahulu."))
        }

        val restoreRes = supabaseRepository.forceRestoreDevices(supabaseUrl, anonKey, localDevs)
        if (restoreRes.isSuccess) {
            val count = restoreRes.getOrDefault(localDevs.size)
            val freshRemote = supabaseRepository.getDevices(supabaseUrl, anonKey).getOrNull()
            if (!freshRemote.isNullOrEmpty()) {
                repository.overwriteDatabaseFromSync(
                    devices = freshRemote,
                    troubleTickets = repository.getTroubleTicketsList(),
                    maintenanceLogs = repository.getMaintenanceLogsList(),
                    uploadedFiles = repository.getUploadedFilesList()
                )
            }
            Result.success("Berhasil mengembalikan $count data perangkat ke Supabase!")
        } else {
            Result.failure(Exception("Gagal mengembalikan data: ${restoreRes.exceptionOrNull()?.message}"))
        }
    }

    fun deleteMaintenanceLogWithSupabaseAndR2(
        supabaseUrl: String,
        anonKey: String,
        workerUrl: String,
        log: MaintenanceLog
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            supabaseRepository.deleteMaintenanceLogWithPhotos(supabaseUrl, anonKey, workerUrl, log)
            repository.removeMaintenanceLog(log.id)
        }
    }

    fun deleteMaintenanceLog(log: MaintenanceLog) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeMaintenanceLog(log.id)
            val prefs = applicationContext.getSharedPreferences("supabase_r2_prefs", android.content.Context.MODE_PRIVATE)
            val url = prefs.getString("supabase_url", "") ?: ""
            val key = prefs.getString("supabase_anon_key", "") ?: ""
            val workerUrl = prefs.getString("cloudflare_worker_url", "") ?: ""
            if (url.isNotBlank() && key.isNotBlank()) {
                supabaseRepository.deleteMaintenanceLogWithPhotos(url, key, workerUrl, log)
            }
            triggerAutoSync()
        }
    }

    // Centralized Google Sheets Synchronization
    private suspend fun uploadFileToGoogleDriveInternal(
        context: android.content.Context,
        uriString: String,
        mimeType: String,
        originalName: String,
        webAppUrl: String
    ): Triple<String, String, String>? = withContext(Dispatchers.IO) {
        if (uriString.isBlank() || webAppUrl.isBlank()) return@withContext null
        if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
            val fileId = extractFileIdFromUrl(uriString) ?: uriString.hashCode().toString()
            return@withContext Triple(fileId, uriString, originalName)
        }
        try {
            val uri = android.net.Uri.parse(uriString)
            var inputStream: java.io.InputStream? = null
            
            if (uriString.startsWith("/") || uri.scheme.isNullOrBlank()) {
                val file = java.io.File(uriString)
                if (file.exists()) {
                    inputStream = java.io.FileInputStream(file)
                }
            } else if (uri.scheme == "file") {
                val path = uri.path
                if (path != null) {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        inputStream = java.io.FileInputStream(file)
                    }
                }
            } else if (uri.authority == "${context.packageName}.fileprovider") {
                val lastPathSegment = uri.lastPathSegment
                if (lastPathSegment != null) {
                    val cachePath = java.io.File(context.cacheDir, "images")
                    var localFile = java.io.File(cachePath, lastPathSegment)
                    if (!localFile.exists()) {
                        val maintPath = java.io.File(context.filesDir, "maintenance_photos")
                        localFile = java.io.File(maintPath, lastPathSegment)
                    }
                    if (localFile.exists()) {
                        inputStream = java.io.FileInputStream(localFile)
                    }
                }
            }
            
            if (inputStream == null) {
                inputStream = context.contentResolver.openInputStream(uri)
            }
            
            if (inputStream == null) return@withContext null
            val bytes = inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return@withContext null

            val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

            val jsonObject = org.json.JSONObject().apply {
                put("action", "upload_file")
                put("fileName", originalName)
                put("mimeType", mimeType)
                put("fileData", base64Str)
            }

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val body = jsonObject.toString().toRequestBody(mediaType)
            var currentUrl = webAppUrl
            var response: okhttp3.Response? = null
            var redirectCount = 0
            val maxRedirects = 5
            var useGet = false

            while (redirectCount < maxRedirects) {
                val requestBuilder = okhttp3.Request.Builder().url(currentUrl)
                val request = if (useGet) {
                    requestBuilder.get().build()
                } else {
                    requestBuilder.post(body).build()
                }

                response = client.newCall(request).execute()
                val code = response.code
                android.util.Log.d("UploadGDriveRedirect", "URL: $currentUrl, Response Code: $code")
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    val location = response.header("Location")
                    response.close()
                    if (!location.isNullOrBlank()) {
                        currentUrl = location
                        redirectCount++
                        if (code == 301 || code == 302 || code == 303) {
                            useGet = true
                        }
                        continue
                    }
                }
                break
            }

            if (response != null && response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                response.close()
                android.util.Log.d("UploadGDriveInternal", "Response: $bodyStr")
                val resObj = org.json.JSONObject(bodyStr)
                val status = resObj.optString("status", "")
                if (status == "success") {
                    val fileId = resObj.optString("fileId", "")
                    val downloadUrl = resObj.optString("downloadUrl", "")
                    val fileName = resObj.optString("fileName", originalName)
                    return@withContext Triple(fileId, downloadUrl, fileName)
                } else {
                    val errMsg = resObj.optString("message", "Unknown error status")
                    android.util.Log.e("UploadGDriveInternal", "Apps Script error: $errMsg")
                }
            } else {
                val code = response?.code ?: -1
                val errMsg = response?.message ?: "Unknown Error"
                android.util.Log.e("UploadGDriveInternal", "HTTP unsuccessful: $code $errMsg")
                response?.close()
            }
        } catch (e: Exception) {
            android.util.Log.e("UploadGDriveInternal", "Upload failed with exception: ${e.message}", e)
            e.printStackTrace()
        }
        return@withContext null
    }

    private suspend fun performGoogleSheetsSyncAll(
        webAppUrl: String,
        devices: List<Device>,
        technicians: List<Technician>,
        troubleTickets: List<TroubleTicket>,
        logs: List<MaintenanceLog>,
        uploadedFiles: List<UploadedFile>
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // Helper function to check and upload a log photo to Google Drive
            suspend fun uploadLogPhotoIfNeeded(photoPath: String?, logId: Int, fieldName: String): String? {
                if (photoPath.isNullOrBlank() || (!photoPath.startsWith("content:") && !photoPath.startsWith("file:") && !photoPath.startsWith("/"))) {
                    return photoPath
                }
                val result = uploadFileToGoogleDriveInternal(applicationContext, photoPath, "image/jpeg", "log_${fieldName}_${logId}.jpg", webAppUrl)
                return result?.second ?: photoPath
            }

            // Upload local device photos/BA files to Drive first and update database
            val syncedDevices = devices.map { device ->
                var updatedDevice = device
                if (device.photoUri != null && (device.photoUri.startsWith("content:") || device.photoUri.startsWith("file:") || device.photoUri.startsWith("/"))) {
                    val result = uploadFileToGoogleDriveInternal(applicationContext, device.photoUri, "image/jpeg", "device_photo_${device.id}.jpg", webAppUrl)
                    if (result != null) {
                        updatedDevice = updatedDevice.copy(photoUri = result.second)
                        repository.addDevice(updatedDevice)
                    }
                }
                if (device.baFileUri != null && (device.baFileUri.startsWith("content:") || device.baFileUri.startsWith("file:") || device.baFileUri.startsWith("/"))) {
                    val ext = if (device.baFileName?.endsWith(".pdf", ignoreCase = true) == true) "pdf" else "jpg"
                    val mime = if (ext == "pdf") "application/pdf" else "image/jpeg"
                    val result = uploadFileToGoogleDriveInternal(applicationContext, device.baFileUri, mime, device.baFileName ?: "device_ba_${device.id}.$ext", webAppUrl)
                    if (result != null) {
                        updatedDevice = updatedDevice.copy(baFileUri = result.second)
                        repository.addDevice(updatedDevice)
                    }
                }
                updatedDevice
            }

            // Upload local tech photos to Drive first and update database
            val syncedTechnicians = technicians.map { tech ->
                var updatedTech = tech
                if (tech.photoUri != null && (tech.photoUri.startsWith("content:") || tech.photoUri.startsWith("file:") || tech.photoUri.startsWith("/"))) {
                    val result = uploadFileToGoogleDriveInternal(applicationContext, tech.photoUri, "image/jpeg", "tech_photo_${tech.id}.jpg", webAppUrl)
                    if (result != null) {
                        updatedTech = updatedTech.copy(photoUri = result.second)
                        repository.addTechnician(updatedTech)
                    }
                }
                updatedTech
            }

            // Upload local trouble ticket photos to Drive first and update database
            val syncedTickets = troubleTickets.map { ticket ->
                var updatedTicket = ticket
                if (ticket.photoBefore != null && (ticket.photoBefore.startsWith("content:") || ticket.photoBefore.startsWith("file:") || ticket.photoBefore.startsWith("/"))) {
                    val result = uploadFileToGoogleDriveInternal(applicationContext, ticket.photoBefore, "image/jpeg", "ticket_before_${ticket.id}.jpg", webAppUrl)
                    if (result != null) {
                        updatedTicket = updatedTicket.copy(photoBefore = result.second)
                        repository.addTroubleTicket(updatedTicket)
                    }
                }
                if (ticket.photoAfter != null && (ticket.photoAfter.startsWith("content:") || ticket.photoAfter.startsWith("file:") || ticket.photoAfter.startsWith("/"))) {
                    val result = uploadFileToGoogleDriveInternal(applicationContext, ticket.photoAfter, "image/jpeg", "ticket_after_${ticket.id}.jpg", webAppUrl)
                    if (result != null) {
                        updatedTicket = updatedTicket.copy(photoAfter = result.second)
                        repository.addTroubleTicket(updatedTicket)
                    }
                }
                updatedTicket
            }

            // Upload local maintenance log photos to Drive first and update database
            val syncedLogs = logs.map { log ->
                var updatedLog = log
                var changed = false
                
                val healthReportBeforePhoto = uploadLogPhotoIfNeeded(log.healthReportBeforePhoto, log.id, "healthReportBefore")
                if (healthReportBeforePhoto != log.healthReportBeforePhoto) { updatedLog = updatedLog.copy(healthReportBeforePhoto = healthReportBeforePhoto); changed = true }
                
                val healthReportAfterPhoto = uploadLogPhotoIfNeeded(log.healthReportAfterPhoto, log.id, "healthReportAfter")
                if (healthReportAfterPhoto != log.healthReportAfterPhoto) { updatedLog = updatedLog.copy(healthReportAfterPhoto = healthReportAfterPhoto); changed = true }
                
                val diskCleanupBeforePhoto = uploadLogPhotoIfNeeded(log.diskCleanupBeforePhoto, log.id, "diskCleanupBefore")
                if (diskCleanupBeforePhoto != log.diskCleanupBeforePhoto) { updatedLog = updatedLog.copy(diskCleanupBeforePhoto = diskCleanupBeforePhoto); changed = true }
                
                val diskCleanupAfterPhoto = uploadLogPhotoIfNeeded(log.diskCleanupAfterPhoto, log.id, "diskCleanupAfter")
                if (diskCleanupAfterPhoto != log.diskCleanupAfterPhoto) { updatedLog = updatedLog.copy(diskCleanupAfterPhoto = diskCleanupAfterPhoto); changed = true }
                
                val hardwareCleanupBeforePhoto = uploadLogPhotoIfNeeded(log.hardwareCleanupBeforePhoto, log.id, "hardwareCleanupBefore")
                if (hardwareCleanupBeforePhoto != log.hardwareCleanupBeforePhoto) { updatedLog = updatedLog.copy(hardwareCleanupBeforePhoto = hardwareCleanupBeforePhoto); changed = true }
                
                val hardwareCleanupAfterPhoto = uploadLogPhotoIfNeeded(log.hardwareCleanupAfterPhoto, log.id, "hardwareCleanupAfter")
                if (hardwareCleanupAfterPhoto != log.hardwareCleanupAfterPhoto) { updatedLog = updatedLog.copy(hardwareCleanupAfterPhoto = hardwareCleanupAfterPhoto); changed = true }
                
                val checkingDriveErrorBeforePhoto = uploadLogPhotoIfNeeded(log.checkingDriveErrorBeforePhoto, log.id, "checkingDriveErrorBefore")
                if (checkingDriveErrorBeforePhoto != log.checkingDriveErrorBeforePhoto) { updatedLog = updatedLog.copy(checkingDriveErrorBeforePhoto = checkingDriveErrorBeforePhoto); changed = true }
                
                val checkingDriveErrorAfterPhoto = uploadLogPhotoIfNeeded(log.checkingDriveErrorAfterPhoto, log.id, "checkingDriveErrorAfter")
                if (checkingDriveErrorAfterPhoto != log.checkingDriveErrorAfterPhoto) { updatedLog = updatedLog.copy(checkingDriveErrorAfterPhoto = checkingDriveErrorAfterPhoto); changed = true }
                
                val scanningVirusBeforePhoto = uploadLogPhotoIfNeeded(log.scanningVirusBeforePhoto, log.id, "scanningVirusBefore")
                if (scanningVirusBeforePhoto != log.scanningVirusBeforePhoto) { updatedLog = updatedLog.copy(scanningVirusBeforePhoto = scanningVirusBeforePhoto); changed = true }
                
                val scanningVirusAfterPhoto = uploadLogPhotoIfNeeded(log.scanningVirusAfterPhoto, log.id, "scanningVirusAfter")
                if (scanningVirusAfterPhoto != log.scanningVirusAfterPhoto) { updatedLog = updatedLog.copy(scanningVirusAfterPhoto = scanningVirusAfterPhoto); changed = true }
                
                val checkingNetworkBeforePhoto = uploadLogPhotoIfNeeded(log.checkingNetworkBeforePhoto, log.id, "checkingNetworkBefore")
                if (checkingNetworkBeforePhoto != log.checkingNetworkBeforePhoto) { updatedLog = updatedLog.copy(checkingNetworkBeforePhoto = checkingNetworkBeforePhoto); changed = true }
                
                val checkingNetworkAfterPhoto = uploadLogPhotoIfNeeded(log.checkingNetworkAfterPhoto, log.id, "checkingNetworkAfter")
                if (checkingNetworkAfterPhoto != log.checkingNetworkAfterPhoto) { updatedLog = updatedLog.copy(checkingNetworkAfterPhoto = checkingNetworkAfterPhoto); changed = true }
                
                val updatingAntivirusBeforePhoto = uploadLogPhotoIfNeeded(log.updatingAntivirusBeforePhoto, log.id, "updatingAntivirusBefore")
                if (updatingAntivirusBeforePhoto != log.updatingAntivirusBeforePhoto) { updatedLog = updatedLog.copy(updatingAntivirusBeforePhoto = updatingAntivirusBeforePhoto); changed = true }
                
                val updatingAntivirusAfterPhoto = uploadLogPhotoIfNeeded(log.updatingAntivirusAfterPhoto, log.id, "updatingAntivirusAfter")
                if (updatingAntivirusAfterPhoto != log.updatingAntivirusAfterPhoto) { updatedLog = updatedLog.copy(updatingAntivirusAfterPhoto = updatingAntivirusAfterPhoto); changed = true }
                
                val updatingAplikasiBeforePhoto = uploadLogPhotoIfNeeded(log.updatingAplikasiBeforePhoto, log.id, "updatingAplikasiBefore")
                if (updatingAplikasiBeforePhoto != log.updatingAplikasiBeforePhoto) { updatedLog = updatedLog.copy(updatingAplikasiBeforePhoto = updatingAplikasiBeforePhoto); changed = true }
                
                val updatingAplikasiAfterPhoto = uploadLogPhotoIfNeeded(log.updatingAplikasiAfterPhoto, log.id, "updatingAplikasiAfter")
                if (updatingAplikasiAfterPhoto != log.updatingAplikasiAfterPhoto) { updatedLog = updatedLog.copy(updatingAplikasiAfterPhoto = updatingAplikasiAfterPhoto); changed = true }
                
                if (changed) {
                    repository.addMaintenanceLog(updatedLog)
                }
                updatedLog
            }

            fun escapeJson(s: String?): String {
                if (s == null) return ""
                return s.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "")
            }

            val jsonBuilder = StringBuilder()
            jsonBuilder.append("{")
            jsonBuilder.append("\"action\":\"sync_all_data\",")

            // 1. Devices
            jsonBuilder.append("\"devices\":[")
            syncedDevices.forEachIndexed { i, device ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(device.lastMaintenance))
                jsonBuilder.append("{")
                jsonBuilder.append("\"id\":${device.id},")
                jsonBuilder.append("\"type\":\"${escapeJson(device.type)}\",")
                jsonBuilder.append("\"name\":\"${escapeJson(device.name)}\",")
                jsonBuilder.append("\"brand\":\"${escapeJson(device.brand)}\",")
                jsonBuilder.append("\"serialNumber\":\"${escapeJson(device.serialNumber)}\",")
                jsonBuilder.append("\"condition\":\"${escapeJson(device.condition)}\",")
                jsonBuilder.append("\"lastMaintenance\":\"${escapeJson(dateStr)}\",")
                jsonBuilder.append("\"description\":\"${escapeJson(device.description)}\",")
                jsonBuilder.append("\"sn\":\"${escapeJson(device.sn)}\",")
                jsonBuilder.append("\"photoUri\":\"${escapeJson(device.photoUri)}\",")
                jsonBuilder.append("\"baFileUri\":\"${escapeJson(device.baFileUri)}\",")
                jsonBuilder.append("\"baFileName\":\"${escapeJson(device.baFileName)}\"")
                jsonBuilder.append("}")
                if (i < syncedDevices.size - 1) jsonBuilder.append(",")
            }
            jsonBuilder.append("],")

            // 2. Technicians
            jsonBuilder.append("\"technicians\":[")
            syncedTechnicians.forEachIndexed { i, tech ->
                jsonBuilder.append("{")
                jsonBuilder.append("\"id\":${tech.id},")
                jsonBuilder.append("\"name\":\"${escapeJson(tech.name)}\",")
                jsonBuilder.append("\"role\":\"${escapeJson(tech.role)}\",")
                jsonBuilder.append("\"phone\":\"${escapeJson(tech.phone)}\",")
                jsonBuilder.append("\"status\":\"${escapeJson(tech.status)}\",")
                jsonBuilder.append("\"photoUri\":\"${escapeJson(tech.photoUri)}\"")
                jsonBuilder.append("}")
                if (i < syncedTechnicians.size - 1) jsonBuilder.append(",")
            }
            jsonBuilder.append("],")

            // 3. Trouble Tickets
            jsonBuilder.append("\"trouble_tickets\":[")
            syncedTickets.forEachIndexed { i, ticket ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ticket.timestamp))
                jsonBuilder.append("{")
                jsonBuilder.append("\"id\":${ticket.id},")
                jsonBuilder.append("\"deviceId\":${ticket.deviceId},")
                jsonBuilder.append("\"deviceName\":\"${escapeJson(ticket.deviceName)}\",")
                jsonBuilder.append("\"description\":\"${escapeJson(ticket.description)}\",")
                jsonBuilder.append("\"reportedBy\":\"${escapeJson(ticket.reportedBy)}\",")
                jsonBuilder.append("\"status\":\"${escapeJson(ticket.status)}\",")
                jsonBuilder.append("\"actionTaken\":\"${escapeJson(ticket.actionTaken)}\",")
                jsonBuilder.append("\"duration\":\"${escapeJson(ticket.duration)}\",")
                jsonBuilder.append("\"photoBefore\":\"${escapeJson(ticket.photoBefore)}\",")
                jsonBuilder.append("\"photoAfter\":\"${escapeJson(ticket.photoAfter)}\",")
                jsonBuilder.append("\"timestamp\":\"${escapeJson(dateStr)}\"")
                jsonBuilder.append("}")
                if (i < syncedTickets.size - 1) jsonBuilder.append(",")
            }
            jsonBuilder.append("],")

            // 4. Maintenance Logs
            jsonBuilder.append("\"maintenance_logs\":[")
            syncedLogs.forEachIndexed { i, log ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                jsonBuilder.append("{")
                jsonBuilder.append("\"id\":${log.id},")
                jsonBuilder.append("\"deviceId\":${log.deviceId},")
                jsonBuilder.append("\"deviceName\":\"${escapeJson(log.deviceName)}\",")
                jsonBuilder.append("\"technicianName\":\"${escapeJson(log.technicianName)}\",")
                jsonBuilder.append("\"actionTaken\":\"${escapeJson(log.actionTaken)}\",")
                jsonBuilder.append("\"date\":\"${escapeJson(dateStr)}\",")
                jsonBuilder.append("\"healthReport\":${log.healthReport},")
                jsonBuilder.append("\"diskCleanup\":${log.diskCleanup},")
                jsonBuilder.append("\"hardwareCleanup\":${log.hardwareCleanup},")
                jsonBuilder.append("\"checkingDriveError\":${log.checkingDriveError},")
                jsonBuilder.append("\"scanningVirus\":${log.scanningVirus},")
                jsonBuilder.append("\"checkingNetwork\":${log.checkingNetwork},")
                jsonBuilder.append("\"updatingAntivirus\":${log.updatingAntivirus},")
                jsonBuilder.append("\"updatingAplikasi\":${log.updatingAplikasi},")
                jsonBuilder.append("\"windowsLicense\":\"${escapeJson(log.windowsLicense)}\",")
                jsonBuilder.append("\"officeLicense\":\"${escapeJson(log.officeLicense)}\",")
                jsonBuilder.append("\"notes\":\"${escapeJson(log.notes)}\",")
                jsonBuilder.append("\"signatureData\":\"${escapeJson(log.signatureData)}\",")
                jsonBuilder.append("\"techSignatureData\":\"${escapeJson(log.techSignatureData)}\",")
                jsonBuilder.append("\"healthReportBeforePhoto\":\"${escapeJson(log.healthReportBeforePhoto)}\",")
                jsonBuilder.append("\"healthReportAfterPhoto\":\"${escapeJson(log.healthReportAfterPhoto)}\",")
                jsonBuilder.append("\"diskCleanupBeforePhoto\":\"${escapeJson(log.diskCleanupBeforePhoto)}\",")
                jsonBuilder.append("\"diskCleanupAfterPhoto\":\"${escapeJson(log.diskCleanupAfterPhoto)}\",")
                jsonBuilder.append("\"hardwareCleanupBeforePhoto\":\"${escapeJson(log.hardwareCleanupBeforePhoto)}\",")
                jsonBuilder.append("\"hardwareCleanupAfterPhoto\":\"${escapeJson(log.hardwareCleanupAfterPhoto)}\",")
                jsonBuilder.append("\"checkingDriveErrorBeforePhoto\":\"${escapeJson(log.checkingDriveErrorBeforePhoto)}\",")
                jsonBuilder.append("\"checkingDriveErrorAfterPhoto\":\"${escapeJson(log.checkingDriveErrorAfterPhoto)}\",")
                jsonBuilder.append("\"scanningVirusBeforePhoto\":\"${escapeJson(log.scanningVirusBeforePhoto)}\",")
                jsonBuilder.append("\"scanningVirusAfterPhoto\":\"${escapeJson(log.scanningVirusAfterPhoto)}\",")
                jsonBuilder.append("\"checkingNetworkBeforePhoto\":\"${escapeJson(log.checkingNetworkBeforePhoto)}\",")
                jsonBuilder.append("\"checkingNetworkAfterPhoto\":\"${escapeJson(log.checkingNetworkAfterPhoto)}\",")
                jsonBuilder.append("\"updatingAntivirusBeforePhoto\":\"${escapeJson(log.updatingAntivirusBeforePhoto)}\",")
                jsonBuilder.append("\"updatingAntivirusAfterPhoto\":\"${escapeJson(log.updatingAntivirusAfterPhoto)}\",")
                jsonBuilder.append("\"updatingAplikasiBeforePhoto\":\"${escapeJson(log.updatingAplikasiBeforePhoto)}\",")
                jsonBuilder.append("\"updatingAplikasiAfterPhoto\":\"${escapeJson(log.updatingAplikasiAfterPhoto)}\"")
                jsonBuilder.append("}")
                if (i < syncedLogs.size - 1) jsonBuilder.append(",")
            }
            jsonBuilder.append("],")

            // 5. Uploaded Files
            jsonBuilder.append("\"uploaded_files\":[")
            uploadedFiles.forEachIndexed { i, file ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.uploadDate))
                jsonBuilder.append("{")
                jsonBuilder.append("\"id\":${file.id},")
                jsonBuilder.append("\"fileName\":\"${escapeJson(file.fileName)}\",")
                jsonBuilder.append("\"fileSize\":\"${escapeJson(file.fileSize)}\",")
                jsonBuilder.append("\"uploadDate\":\"${escapeJson(dateStr)}\",")
                jsonBuilder.append("\"fileType\":\"${escapeJson(file.fileType)}\"")
                jsonBuilder.append("}")
                if (i < uploadedFiles.size - 1) jsonBuilder.append(",")
            }
            jsonBuilder.append("]")

            jsonBuilder.append("}")

            val jsonString = jsonBuilder.toString()
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val body = jsonString.toRequestBody(mediaType)
            var currentUrl = webAppUrl
            var response: okhttp3.Response? = null
            var redirectCount = 0
            val maxRedirects = 5
            var useGet = false

            while (redirectCount < maxRedirects) {
                val requestBuilder = okhttp3.Request.Builder().url(currentUrl)
                val request = if (useGet) {
                    requestBuilder.get().build()
                } else {
                    requestBuilder.post(body).build()
                }

                response = client.newCall(request).execute()
                val code = response.code
                android.util.Log.d("SyncRedirect", "URL: $currentUrl, Response Code: $code")
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    val location = response.header("Location")
                    response.close()
                    if (!location.isNullOrBlank()) {
                        currentUrl = location
                        redirectCount++
                        if (code == 301 || code == 302 || code == 303) {
                            useGet = true
                        }
                        continue
                    }
                }
                break
            }

            if (response != null && response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                response.close()
                val isSuccess = bodyStr.contains("\"success\"") || bodyStr.contains("success")
                if (isSuccess) {
                    Pair(true, "Berhasil")
                } else {
                    Pair(false, "Server tidak mengonfirmasi sukses. Respons: $bodyStr")
                }
            } else {
                val code = response?.code ?: -1
                val errorBody = response?.body?.string() ?: ""
                val msg = response?.message ?: "Unknown error"
                response?.close()
                Pair(false, "HTTP Error $code: $msg. Detail: $errorBody")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Terjadi kesalahan koneksi internet")
        }
    }

    // Background Auto Sync Trigger
    private fun triggerAutoSync() {
        val supabasePrefs = applicationContext.getSharedPreferences("supabase_r2_prefs", android.content.Context.MODE_PRIVATE)
        val supabaseUrl = supabasePrefs.getString("supabase_url", "") ?: ""
        val anonKey = supabasePrefs.getString("supabase_anon_key", "") ?: ""
        val workerUrl = supabasePrefs.getString("cloudflare_worker_url", "") ?: ""

        if (supabaseUrl.isNotBlank() && anonKey.isNotBlank()) {
            viewModelScope.launch {
                try {
                    val result = syncDatabaseWithSupabase(supabaseUrl, anonKey, workerUrl)
                    if (result.isSuccess) {
                        android.util.Log.d("AutoSync", "Auto sync Supabase successful")
                        supabasePrefs.edit().putLong("last_supabase_sync_time", System.currentTimeMillis()).apply()
                    } else {
                        android.util.Log.e("AutoSync", "Auto sync Supabase failed: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Manual Sync Triggered From Profile UI
    fun performManualSync(webAppUrl: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = performGoogleSheetsSyncAll(
                webAppUrl = webAppUrl,
                devices = repository.getDevicesList(),
                technicians = repository.getTechniciansList(),
                troubleTickets = repository.getTroubleTicketsList(),
                logs = repository.getMaintenanceLogsList(),
                uploadedFiles = repository.getUploadedFilesList()
            )
            onResult(result.first, result.second)
        }
    }

    // Pull/Download Data from Google Sheets to overwrite local Room database
    fun pullDataFromGoogleSheets(webAppUrl: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = performGoogleSheetsPullAll(webAppUrl)
            onResult(result.first, result.second)
        }
    }

    private suspend fun performGoogleSheetsPullAll(webAppUrl: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val jsonRequest = "{\"action\":\"fetch_all_data\"}"
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val body = jsonRequest.toRequestBody(mediaType)
            var currentUrl = webAppUrl
            var response: okhttp3.Response? = null
            var redirectCount = 0
            val maxRedirects = 5
            var useGet = false

            while (redirectCount < maxRedirects) {
                val requestBuilder = okhttp3.Request.Builder().url(currentUrl)
                val request = if (useGet) {
                    requestBuilder.get().build()
                } else {
                    requestBuilder.post(body).build()
                }

                response = client.newCall(request).execute()
                val code = response.code
                android.util.Log.d("PullRedirect", "URL: $currentUrl, Response Code: $code")
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    val location = response.header("Location")
                    response.close()
                    if (!location.isNullOrBlank()) {
                        currentUrl = location
                        redirectCount++
                        if (code == 301 || code == 302 || code == 303) {
                            useGet = true
                        }
                        continue
                    }
                }
                break
            }

            if (response != null && response.isSuccessful) {
                response.use { resp ->
                    val code = resp.code
                    val bodyStr = resp.body?.string() ?: ""
                    val jsonObject = org.json.JSONObject(bodyStr)
                    val status = jsonObject.optString("status", "")
                    if (status == "success") {
                        // 1. Devices
                        val devicesArray = jsonObject.optJSONArray("devices")
                        val fetchedDevices = mutableListOf<Device>()
                        if (devicesArray != null) {
                            for (i in 0 until devicesArray.length()) {
                                val obj = devicesArray.getJSONObject(i)
                                val lastMaintenanceStr = obj.optString("lastMaintenance", "")
                                val lastMaintenanceLong = parseDateToMillis(lastMaintenanceStr)
                                fetchedDevices.add(
                                    Device(
                                        id = obj.optInt("id", 0),
                                        type = obj.optString("type", ""),
                                        name = obj.optString("name", ""),
                                        brand = obj.optString("brand", ""),
                                        serialNumber = obj.optString("serialNumber", ""),
                                        condition = obj.optString("condition", ""),
                                        lastMaintenance = lastMaintenanceLong,
                                        description = obj.optString("description", ""),
                                        sn = obj.optString("sn", ""),
                                        photoUri = convertBase64ToLocalUri(applicationContext, if (obj.isNull("photoUri") || obj.optString("photoUri").isEmpty()) null else obj.optString("photoUri")),
                                        baFileUri = convertBase64ToLocalBaFileUri(applicationContext, if (obj.isNull("baFileUri") || obj.optString("baFileUri").isEmpty()) null else obj.optString("baFileUri"), if (obj.isNull("baFileName")) null else obj.optString("baFileName")),
                                        baFileName = if (obj.isNull("baFileName") || obj.optString("baFileName").isEmpty()) null else obj.optString("baFileName")
                                    )
                                )
                            }
                        }

                        // 2. Technicians
                        val techniciansArray = jsonObject.optJSONArray("technicians")
                        val fetchedTechnicians = mutableListOf<Technician>()
                        if (techniciansArray != null) {
                            for (i in 0 until techniciansArray.length()) {
                                val obj = techniciansArray.getJSONObject(i)
                                fetchedTechnicians.add(
                                    Technician(
                                        id = obj.optInt("id", 0),
                                        name = obj.optString("name", ""),
                                        role = obj.optString("role", ""),
                                        phone = obj.optString("phone", ""),
                                        status = obj.optString("status", ""),
                                        photoUri = convertBase64ToLocalUri(applicationContext, if (obj.isNull("photoUri") || obj.optString("photoUri").isEmpty()) null else obj.optString("photoUri"))
                                    )
                                )
                            }
                        }

                        // 3. Trouble Tickets
                        val ticketsArray = jsonObject.optJSONArray("trouble_tickets")
                        val fetchedTickets = mutableListOf<TroubleTicket>()
                        if (ticketsArray != null) {
                            for (i in 0 until ticketsArray.length()) {
                                val obj = ticketsArray.getJSONObject(i)
                                val timestampStr = obj.optString("timestamp", "")
                                val timestampLong = parseDateToMillis(timestampStr)
                                fetchedTickets.add(
                                    TroubleTicket(
                                        id = obj.optInt("id", 0),
                                        deviceId = obj.optInt("deviceId", 0),
                                        deviceName = obj.optString("deviceName", ""),
                                        description = obj.optString("description", ""),
                                        reportedBy = obj.optString("reportedBy", ""),
                                        status = obj.optString("status", ""),
                                        actionTaken = obj.optString("actionTaken", ""),
                                        duration = obj.optString("duration", ""),
                                        photoBefore = convertBase64ToLocalUri(applicationContext, if (obj.isNull("photoBefore") || obj.optString("photoBefore").isEmpty()) null else obj.optString("photoBefore")),
                                        photoAfter = convertBase64ToLocalUri(applicationContext, if (obj.isNull("photoAfter") || obj.optString("photoAfter").isEmpty()) null else obj.optString("photoAfter")),
                                        timestamp = timestampLong
                                    )
                                )
                            }
                        }

                        // 4. Maintenance Logs
                        val logsArray = jsonObject.optJSONArray("maintenance_logs")
                        val fetchedLogs = mutableListOf<MaintenanceLog>()
                        if (logsArray != null) {
                            for (i in 0 until logsArray.length()) {
                                val obj = logsArray.getJSONObject(i)
                                val dateStr = obj.optString("date", "")
                                val dateLong = parseDateToMillis(dateStr)
                                fetchedLogs.add(
                                    MaintenanceLog(
                                        id = obj.optInt("id", 0),
                                        deviceId = obj.optInt("deviceId", 0),
                                        deviceName = obj.optString("deviceName", ""),
                                        technicianName = obj.optString("technicianName", ""),
                                        actionTaken = obj.optString("actionTaken", ""),
                                        timestamp = dateLong,
                                        healthReport = obj.optBoolean("healthReport", false),
                                        diskCleanup = obj.optBoolean("diskCleanup", false),
                                        hardwareCleanup = obj.optBoolean("hardwareCleanup", false),
                                        checkingDriveError = obj.optBoolean("checkingDriveError", false),
                                        scanningVirus = obj.optBoolean("scanningVirus", false),
                                        checkingNetwork = obj.optBoolean("checkingNetwork", false),
                                        updatingAntivirus = obj.optBoolean("updatingAntivirus", false),
                                        updatingAplikasi = obj.optBoolean("updatingAplikasi", false),
                                        windowsLicense = if (obj.isNull("windowsLicense")) null else obj.optString("windowsLicense"),
                                        officeLicense = if (obj.isNull("officeLicense")) null else obj.optString("officeLicense"),
                                        notes = if (obj.isNull("notes")) null else obj.optString("notes"),
                                        signatureData = if (obj.isNull("signatureData") || obj.optString("signatureData").isEmpty()) null else obj.optString("signatureData"),
                                        techSignatureData = if (obj.isNull("techSignatureData") || obj.optString("techSignatureData").isEmpty()) null else obj.optString("techSignatureData"),
                                        healthReportBeforePhoto = convertBase64ToLocalUri(applicationContext, if (obj.isNull("healthReportBeforePhoto") || obj.optString("healthReportBeforePhoto").isEmpty()) null else obj.optString("healthReportBeforePhoto")),
                                        healthReportAfterPhoto = convertBase64ToLocalUri(applicationContext, if (obj.isNull("healthReportAfterPhoto") || obj.optString("healthReportAfterPhoto").isEmpty()) null else obj.optString("healthReportAfterPhoto")),
                                        diskCleanupBeforePhoto = convertBase64ToLocalUri(applicationContext, if (obj.isNull("diskCleanupBeforePhoto") || obj.optString("diskCleanupBeforePhoto").isEmpty()) null else obj.optString("diskCleanupBeforePhoto")),
                                        diskCleanupAfterPhoto = convertBase64ToLocalUri(applicationContext, if (obj.isNull("diskCleanupAfterPhoto") || obj.optString("diskCleanupAfterPhoto").isEmpty()) null else obj.optString("diskCleanupAfterPhoto")),
                                        hardwareCleanupBeforePhoto = convertBase64ToLocalUri(applicationContext, if (obj.isNull("hardwareCleanupBeforePhoto") || obj.optString("hardwareCleanupBeforePhoto").isEmpty()) null else obj.optString("hardwareCleanupBeforePhoto")),
                                        hardwareCleanupAfterPhoto = convertBase64ToLocalUri(applicationContext, if (obj.isNull("hardwareCleanupAfterPhoto") || obj.optString("hardwareCleanupAfterPhoto").isEmpty()) null else obj.optString("hardwareCleanupAfterPhoto")),
                                        checkingDriveErrorBeforePhoto = convertBase64ToLocalUri(applicationContext, if (obj.isNull("checkingDriveErrorBeforePhoto") || obj.optString("checkingDriveErrorBeforePhoto").isEmpty()) null else obj.optString("checkingDriveErrorBeforePhoto")),
                                        checkingDriveErrorAfterPhoto = convertBase64ToLocalUri(applicationContext, if (obj.isNull("checkingDriveErrorAfterPhoto") || obj.optString("checkingDriveErrorAfterPhoto").isEmpty()) null else obj.optString("checkingDriveErrorAfterPhoto")),
                                        scanningVirusBeforePhoto = convertBase64ToLocalUri(applicationContext, if (obj.isNull("scanningVirusBeforePhoto") || obj.optString("scanningVirusBeforePhoto").isEmpty()) null else obj.optString("scanningVirusBeforePhoto")),
                                        scanningVirusAfterPhoto = convertBase64ToLocalUri(applicationContext, if (obj.isNull("scanningVirusAfterPhoto") || obj.optString("scanningVirusAfterPhoto").isEmpty()) null else obj.optString("scanningVirusAfterPhoto")),
                                        checkingNetworkBeforePhoto = convertBase64ToLocalUri(applicationContext, if (obj.isNull("checkingNetworkBeforePhoto") || obj.optString("checkingNetworkBeforePhoto").isEmpty()) null else obj.optString("checkingNetworkBeforePhoto")),
                                        checkingNetworkAfterPhoto = convertBase64ToLocalUri(applicationContext, if (obj.isNull("checkingNetworkAfterPhoto") || obj.optString("checkingNetworkAfterPhoto").isEmpty()) null else obj.optString("checkingNetworkAfterPhoto")),
                                        updatingAntivirusBeforePhoto = convertBase64ToLocalUri(applicationContext, if (obj.isNull("updatingAntivirusBeforePhoto") || obj.optString("updatingAntivirusBeforePhoto").isEmpty()) null else obj.optString("updatingAntivirusBeforePhoto")),
                                        updatingAntivirusAfterPhoto = convertBase64ToLocalUri(applicationContext, if (obj.isNull("updatingAntivirusAfterPhoto") || obj.optString("updatingAntivirusAfterPhoto").isEmpty()) null else obj.optString("updatingAntivirusAfterPhoto")),
                                        updatingAplikasiBeforePhoto = convertBase64ToLocalUri(applicationContext, if (obj.isNull("updatingAplikasiBeforePhoto") || obj.optString("updatingAplikasiBeforePhoto").isEmpty()) null else obj.optString("updatingAplikasiBeforePhoto")),
                                        updatingAplikasiAfterPhoto = convertBase64ToLocalUri(applicationContext, if (obj.isNull("updatingAplikasiAfterPhoto") || obj.optString("updatingAplikasiAfterPhoto").isEmpty()) null else obj.optString("updatingAplikasiAfterPhoto"))
                                    )
                                )
                            }
                        }

                        // 5. Uploaded Files
                        val filesArray = jsonObject.optJSONArray("uploaded_files")
                        val fetchedFiles = mutableListOf<UploadedFile>()
                        if (filesArray != null) {
                            for (i in 0 until filesArray.length()) {
                                val obj = filesArray.getJSONObject(i)
                                val uploadDateStr = obj.optString("uploadDate", "")
                                val uploadDateLong = parseDateToMillis(uploadDateStr)
                                fetchedFiles.add(
                                    UploadedFile(
                                        id = obj.optInt("id", 0),
                                        fileName = obj.optString("fileName", ""),
                                        fileSize = obj.optString("fileSize", ""),
                                        uploadDate = uploadDateLong,
                                        fileType = obj.optString("fileType", "")
                                    )
                                )
                            }
                        }

                        // Merge local photo/document URIs to prevent losing files on pulling/re-syncing
                        val localDevicesMap = repository.devices.first().associateBy { it.id }
                        val localTechsMap = repository.technicians.first().associateBy { it.id }
                        val localTicketsMap = repository.troubleTickets.first().associateBy { it.id }
                        val localLogsMap = repository.maintenanceLogs.first().associateBy { it.id }

                        val mergedDevices = fetchedDevices.map { fetched ->
                            val local = localDevicesMap[fetched.id]
                            if (local != null) {
                                fetched.copy(
                                    photoUri = if (fetched.photoUri.isNullOrBlank()) local.photoUri else fetched.photoUri,
                                    baFileUri = if (fetched.baFileUri.isNullOrBlank()) local.baFileUri else fetched.baFileUri,
                                    baFileName = if (fetched.baFileName.isNullOrBlank()) local.baFileName else fetched.baFileName
                                )
                            } else {
                                fetched
                            }
                        }

                        val mergedTechnicians = fetchedTechnicians.map { fetched ->
                            val local = localTechsMap[fetched.id]
                            if (local != null) {
                                fetched.copy(
                                    photoUri = if (fetched.photoUri.isNullOrBlank()) local.photoUri else fetched.photoUri
                                )
                            } else {
                                fetched
                            }
                        }

                        val mergedTickets = fetchedTickets.map { fetched ->
                            val local = localTicketsMap[fetched.id]
                            if (local != null) {
                                fetched.copy(
                                    photoBefore = if (fetched.photoBefore.isNullOrBlank()) local.photoBefore else fetched.photoBefore,
                                    photoAfter = if (fetched.photoAfter.isNullOrBlank()) local.photoAfter else fetched.photoAfter
                                )
                            } else {
                                fetched
                            }
                        }

                        val mergedLogs = fetchedLogs.map { fetched ->
                            val local = localLogsMap[fetched.id]
                            if (local != null) {
                                fetched.copy(
                                    healthReportBeforePhoto = if (fetched.healthReportBeforePhoto.isNullOrBlank()) local.healthReportBeforePhoto else fetched.healthReportBeforePhoto,
                                    healthReportAfterPhoto = if (fetched.healthReportAfterPhoto.isNullOrBlank()) local.healthReportAfterPhoto else fetched.healthReportAfterPhoto,
                                    diskCleanupBeforePhoto = if (fetched.diskCleanupBeforePhoto.isNullOrBlank()) local.diskCleanupBeforePhoto else fetched.diskCleanupBeforePhoto,
                                    diskCleanupAfterPhoto = if (fetched.diskCleanupAfterPhoto.isNullOrBlank()) local.diskCleanupAfterPhoto else fetched.diskCleanupAfterPhoto,
                                    hardwareCleanupBeforePhoto = if (fetched.hardwareCleanupBeforePhoto.isNullOrBlank()) local.hardwareCleanupBeforePhoto else fetched.hardwareCleanupBeforePhoto,
                                    hardwareCleanupAfterPhoto = if (fetched.hardwareCleanupAfterPhoto.isNullOrBlank()) local.hardwareCleanupAfterPhoto else fetched.hardwareCleanupAfterPhoto,
                                    checkingDriveErrorBeforePhoto = if (fetched.checkingDriveErrorBeforePhoto.isNullOrBlank()) local.checkingDriveErrorBeforePhoto else fetched.checkingDriveErrorBeforePhoto,
                                    checkingDriveErrorAfterPhoto = if (fetched.checkingDriveErrorAfterPhoto.isNullOrBlank()) local.checkingDriveErrorAfterPhoto else fetched.checkingDriveErrorAfterPhoto,
                                    scanningVirusBeforePhoto = if (fetched.scanningVirusBeforePhoto.isNullOrBlank()) local.scanningVirusBeforePhoto else fetched.scanningVirusBeforePhoto,
                                    scanningVirusAfterPhoto = if (fetched.scanningVirusAfterPhoto.isNullOrBlank()) local.scanningVirusAfterPhoto else fetched.scanningVirusAfterPhoto,
                                    checkingNetworkBeforePhoto = if (fetched.checkingNetworkBeforePhoto.isNullOrBlank()) local.checkingNetworkBeforePhoto else fetched.checkingNetworkBeforePhoto,
                                    checkingNetworkAfterPhoto = if (fetched.checkingNetworkAfterPhoto.isNullOrBlank()) local.checkingNetworkAfterPhoto else fetched.checkingNetworkAfterPhoto,
                                    updatingAntivirusBeforePhoto = if (fetched.updatingAntivirusBeforePhoto.isNullOrBlank()) local.updatingAntivirusBeforePhoto else fetched.updatingAntivirusBeforePhoto,
                                    updatingAntivirusAfterPhoto = if (fetched.updatingAntivirusAfterPhoto.isNullOrBlank()) local.updatingAntivirusAfterPhoto else fetched.updatingAntivirusAfterPhoto,
                                    updatingAplikasiBeforePhoto = if (fetched.updatingAplikasiBeforePhoto.isNullOrBlank()) local.updatingAplikasiBeforePhoto else fetched.updatingAplikasiBeforePhoto,
                                    updatingAplikasiAfterPhoto = if (fetched.updatingAplikasiAfterPhoto.isNullOrBlank()) local.updatingAplikasiAfterPhoto else fetched.updatingAplikasiAfterPhoto
                                )
                            } else {
                                fetched
                            }
                        }

                        repository.overwriteDatabaseFromSync(
                            devices = mergedDevices,
                            troubleTickets = mergedTickets,
                            maintenanceLogs = mergedLogs,
                            uploadedFiles = fetchedFiles
                        )

                        downloadAllDriveFiles(
                            context = applicationContext,
                            devicesList = mergedDevices,
                            techniciansList = mergedTechnicians,
                            ticketsList = mergedTickets,
                            logsList = mergedLogs
                        )

                        Pair(true, "Berhasil")
                    } else {
                        Pair(false, "Gagal mengunduh: ${jsonObject.optString("message", "Unknown error")}")
                    }
                }
            } else {
                val code = response?.code ?: -1
                val errorBody = response?.body?.string() ?: ""
                response?.close()
                Pair(false, "HTTP Error $code: ${response?.message}. Detail: $errorBody")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Terjadi kesalahan koneksi internet")
        }
    }

    // Actions
    fun addDevice(
        type: String,
        name: String,
        brand: String,
        serialNumber: String,
        condition: String = "Baik",
        description: String,
        sn: String = "",
        photoUri: String? = null,
        baFileUri: String? = null,
        baFileName: String? = null,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val initialDevice = Device(
                type = type,
                name = name,
                brand = brand,
                serialNumber = serialNumber,
                condition = condition,
                description = description,
                lastMaintenance = System.currentTimeMillis(),
                sn = sn,
                photoUri = photoUri,
                baFileUri = baFileUri,
                baFileName = baFileName
            )
            repository.addDevice(initialDevice)

            // Direct Push ke Supabase
            val prefs = applicationContext.getSharedPreferences("supabase_r2_prefs", android.content.Context.MODE_PRIVATE)
            val url = prefs.getString("supabase_url", "") ?: ""
            val key = prefs.getString("supabase_anon_key", "") ?: ""

            if (url.isNotBlank() && key.isNotBlank()) {
                val pushRes = supabaseRepository.saveOrUpdateSingleDevice(url, key, initialDevice)
                if (pushRes.isSuccess) {
                    val saved = pushRes.getOrNull()
                    if (saved != null && saved.id > 0) {
                        repository.addDevice(saved)
                    }
                    onResult?.invoke(true, "Perangkat berhasil disimpan ke HP & Supabase!")
                } else {
                    val err = pushRes.exceptionOrNull()?.message ?: "Gagal push ke Supabase"
                    android.util.Log.e("AddDevice", "Error Supabase push: $err")
                    onResult?.invoke(false, "Tersimpan di HP, gagal ke Supabase: $err")
                }
            } else {
                onResult?.invoke(false, "Tersimpan di HP (Supabase belum diatur di Profil)")
            }
            triggerAutoSync()
        }
    }

    fun updateDeviceCondition(device: Device, newCondition: String) {
        viewModelScope.launch {
            val updated = device.copy(condition = newCondition, lastMaintenance = System.currentTimeMillis())
            repository.addDevice(updated)
            val prefs = applicationContext.getSharedPreferences("supabase_r2_prefs", android.content.Context.MODE_PRIVATE)
            val url = prefs.getString("supabase_url", "") ?: ""
            val key = prefs.getString("supabase_anon_key", "") ?: ""
            if (url.isNotBlank() && key.isNotBlank()) {
                supabaseRepository.saveOrUpdateSingleDevice(url, key, updated)
            }
            triggerAutoSync()
        }
    }

    fun updateDevice(device: Device, onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            repository.addDevice(device)
            val prefs = applicationContext.getSharedPreferences("supabase_r2_prefs", android.content.Context.MODE_PRIVATE)
            val url = prefs.getString("supabase_url", "") ?: ""
            val key = prefs.getString("supabase_anon_key", "") ?: ""
            if (url.isNotBlank() && key.isNotBlank()) {
                val remoteRes = supabaseRepository.saveOrUpdateSingleDevice(url, key, device)
                if (remoteRes.isSuccess) {
                    onResult?.invoke(true, "Perangkat berhasil diperbarui di HP & Supabase")
                } else {
                    val err = remoteRes.exceptionOrNull()?.message ?: "Gagal update ke Supabase"
                    onResult?.invoke(false, "Diperbarui di HP, gagal ke Supabase: $err")
                }
            } else {
                onResult?.invoke(false, "Diperbarui di HP (Supabase belum diatur di Profil)")
            }
            triggerAutoSync()
        }
    }

    fun deleteDevice(id: Int) {
        viewModelScope.launch {
            val device = repository.getDevicesList().find { it.id == id }
            repository.removeDevice(id)
            val prefs = applicationContext.getSharedPreferences("supabase_r2_prefs", android.content.Context.MODE_PRIVATE)
            val url = prefs.getString("supabase_url", "") ?: ""
            val key = prefs.getString("supabase_anon_key", "") ?: ""
            val workerUrl = prefs.getString("cloudflare_worker_url", "") ?: ""
            if (device != null && workerUrl.isNotBlank()) {
                device.photoUri?.let { if (it.startsWith("http")) cloudStorageManager.deleteFromR2(workerUrl, it) }
                device.baFileUri?.let { if (it.startsWith("http")) cloudStorageManager.deleteFromR2(workerUrl, it) }
            }
            if (url.isNotBlank() && key.isNotBlank()) {
                supabaseRepository.deleteDeviceFromSupabase(url, key, id)
            }
            triggerAutoSync()
        }
    }

    fun addTechnician(name: String, role: String, phone: String, status: String = "Aktif", photoUri: String? = null) {
        viewModelScope.launch {
            repository.addTechnician(
                Technician(
                    name = name,
                    role = role,
                    phone = phone,
                    status = status,
                    photoUri = photoUri
                )
            )
            triggerAutoSync()
        }
    }

    fun updateTechnician(technician: Technician) {
        viewModelScope.launch {
            repository.addTechnician(technician)
            triggerAutoSync()
        }
    }

    fun deleteTechnician(id: Int) {
        viewModelScope.launch {
            repository.removeTechnician(id)
            triggerAutoSync()
        }
    }

    fun addTroubleTicket(
        deviceId: Int,
        deviceName: String,
        description: String,
        reportedBy: String,
        status: String = "Pending",
        timestamp: Long = System.currentTimeMillis(),
        actionTaken: String = "",
        duration: String = "",
        photoBefore: String? = null,
        photoAfter: String? = null
    ) {
        viewModelScope.launch {
            repository.addTroubleTicket(
                TroubleTicket(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    description = description,
                    reportedBy = reportedBy,
                    status = status,
                    timestamp = timestamp,
                    actionTaken = actionTaken,
                    duration = duration,
                    photoBefore = photoBefore,
                    photoAfter = photoAfter
                )
            )
            // Automatically set corresponding device status
            repository.devices.first().find { it.id == deviceId }?.let { device ->
                val newCondition = if (status == "Selesai") "Baik" else "Trouble"
                repository.addDevice(device.copy(condition = newCondition))
            }
            triggerAutoSync()
        }
    }

    fun resolveTroubleTicket(
        ticket: TroubleTicket,
        technicianName: String,
        actionTaken: String,
        duration: String = "",
        photoBefore: String? = null,
        photoAfter: String? = null
    ) {
        viewModelScope.launch {
            // Update ticket status to "Selesai"
            repository.addTroubleTicket(
                ticket.copy(
                    status = "Selesai",
                    actionTaken = actionTaken,
                    duration = duration,
                    photoBefore = photoBefore ?: ticket.photoBefore,
                    photoAfter = photoAfter ?: ticket.photoAfter
                )
            )
            // Record maintenance log
            repository.addMaintenanceLog(
                MaintenanceLog(
                    deviceId = ticket.deviceId,
                    deviceName = ticket.deviceName,
                    technicianName = technicianName,
                    actionTaken = actionTaken
                )
            )
            // Update device condition back to "Baik"
            repository.devices.first().find { it.id == ticket.deviceId }?.let { device ->
                repository.addDevice(device.copy(condition = "Baik", lastMaintenance = System.currentTimeMillis()))
            }
            triggerAutoSync()
        }
    }

    fun deleteTroubleTicket(id: Int) {
        viewModelScope.launch {
            val ticket = repository.getTroubleTicketsList().find { it.id == id }
            repository.removeTroubleTicket(id)
            val prefs = applicationContext.getSharedPreferences("supabase_r2_prefs", android.content.Context.MODE_PRIVATE)
            val url = prefs.getString("supabase_url", "") ?: ""
            val key = prefs.getString("supabase_anon_key", "") ?: ""
            val workerUrl = prefs.getString("cloudflare_worker_url", "") ?: ""
            if (ticket != null && workerUrl.isNotBlank()) {
                ticket.photoBefore?.let { if (it.startsWith("http")) cloudStorageManager.deleteFromR2(workerUrl, it) }
                ticket.photoAfter?.let { if (it.startsWith("http")) cloudStorageManager.deleteFromR2(workerUrl, it) }
            }
            if (url.isNotBlank() && key.isNotBlank()) {
                supabaseRepository.deleteTroubleTicketFromSupabase(url, key, id)
            }
            triggerAutoSync()
        }
    }

    data class DirectMaintenanceItem(
        val device: Device,
        val technicianName: String = "",
        val actionTaken: String = "",
        val healthReport: Boolean = false,
        val diskCleanup: Boolean = false,
        val hardwareCleanup: Boolean = false,
        val checkingDriveError: Boolean = false,
        val scanningVirus: Boolean = false,
        val checkingNetwork: Boolean = false,
        val updatingAntivirus: Boolean = false,
        val updatingAplikasi: Boolean = false,
        val windowsLicense: String? = null,
        val officeLicense: String? = null,
        val notes: String? = null,
        val signatureData: String? = null,
        val techSignatureData: String? = null,
        val timestamp: Long = System.currentTimeMillis(),
        val healthReportBeforePhoto: String? = null,
        val healthReportAfterPhoto: String? = null,
        val diskCleanupBeforePhoto: String? = null,
        val diskCleanupAfterPhoto: String? = null,
        val hardwareCleanupBeforePhoto: String? = null,
        val hardwareCleanupAfterPhoto: String? = null,
        val checkingDriveErrorBeforePhoto: String? = null,
        val checkingDriveErrorAfterPhoto: String? = null,
        val scanningVirusBeforePhoto: String? = null,
        val scanningVirusAfterPhoto: String? = null,
        val checkingNetworkBeforePhoto: String? = null,
        val checkingNetworkAfterPhoto: String? = null,
        val updatingAntivirusBeforePhoto: String? = null,
        val updatingAntivirusAfterPhoto: String? = null,
        val updatingAplikasiBeforePhoto: String? = null,
        val updatingAplikasiAfterPhoto: String? = null
    )

    fun performBatchDirectMaintenance(
        items: List<DirectMaintenanceItem>,
        onComplete: (() -> Unit)? = null
    ) {
        if (items.isEmpty()) {
            onComplete?.invoke()
            return
        }
        viewModelScope.launch {
            val updatedDevices = items.map { it.device.copy(condition = "Baik", lastMaintenance = it.timestamp) }
            val logsToInsert = items.map { item ->
                MaintenanceLog(
                    deviceId = item.device.id,
                    deviceName = item.device.name,
                    technicianName = item.technicianName,
                    actionTaken = item.actionTaken,
                    healthReport = item.healthReport,
                    diskCleanup = item.diskCleanup,
                    hardwareCleanup = item.hardwareCleanup,
                    checkingDriveError = item.checkingDriveError,
                    scanningVirus = item.scanningVirus,
                    checkingNetwork = item.checkingNetwork,
                    updatingAntivirus = item.updatingAntivirus,
                    updatingAplikasi = item.updatingAplikasi,
                    windowsLicense = item.windowsLicense,
                    officeLicense = item.officeLicense,
                    notes = item.notes,
                    signatureData = item.signatureData,
                    techSignatureData = item.techSignatureData,
                    timestamp = item.timestamp,
                    healthReportBeforePhoto = item.healthReportBeforePhoto,
                    healthReportAfterPhoto = item.healthReportAfterPhoto,
                    diskCleanupBeforePhoto = item.diskCleanupBeforePhoto,
                    diskCleanupAfterPhoto = item.diskCleanupAfterPhoto,
                    hardwareCleanupBeforePhoto = item.hardwareCleanupBeforePhoto,
                    hardwareCleanupAfterPhoto = item.hardwareCleanupAfterPhoto,
                    checkingDriveErrorBeforePhoto = item.checkingDriveErrorBeforePhoto,
                    checkingDriveErrorAfterPhoto = item.checkingDriveErrorAfterPhoto,
                    scanningVirusBeforePhoto = item.scanningVirusBeforePhoto,
                    scanningVirusAfterPhoto = item.scanningVirusAfterPhoto,
                    checkingNetworkBeforePhoto = item.checkingNetworkBeforePhoto,
                    checkingNetworkAfterPhoto = item.checkingNetworkAfterPhoto,
                    updatingAntivirusBeforePhoto = item.updatingAntivirusBeforePhoto,
                    updatingAntivirusAfterPhoto = item.updatingAntivirusAfterPhoto,
                    updatingAplikasiBeforePhoto = item.updatingAplikasiBeforePhoto,
                    updatingAplikasiAfterPhoto = item.updatingAplikasiAfterPhoto
                )
            }

            // 1. Save to local Room immediately
            repository.addDevices(updatedDevices)
            repository.addMaintenanceLogs(logsToInsert)

            // 2. Direct Push to Supabase & Cloudflare R2 if configured
            val supabasePrefs = applicationContext.getSharedPreferences("supabase_r2_prefs", android.content.Context.MODE_PRIVATE)
            val supabaseUrl = supabasePrefs.getString("supabase_url", "") ?: ""
            val anonKey = supabasePrefs.getString("supabase_anon_key", "") ?: ""
            val workerUrl = supabasePrefs.getString("cloudflare_worker_url", "") ?: ""

            if (supabaseUrl.isNotBlank() && anonKey.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        val logsWithR2 = if (workerUrl.isNotBlank()) {
                            logsToInsert.map { uploadLogPhotosToR2(it, workerUrl) }
                        } else logsToInsert

                        val insertRes = supabaseRepository.insertNewMaintenanceLogs(supabaseUrl, anonKey, logsWithR2)
                        if (insertRes.isSuccess) {
                            val created = insertRes.getOrDefault(emptyList())
                            if (created.isNotEmpty()) {
                                repository.addMaintenanceLogs(created)
                            }
                        }
                        supabaseRepository.upsertDevices(supabaseUrl, anonKey, updatedDevices)
                    } catch (e: Exception) {
                        android.util.Log.e("BatchDirectPush", "Error direct push ke Supabase: ${e.message}")
                    }
                }
            }

            // 3. Trigger auto sync to reconcile
            triggerAutoSync()
            onComplete?.invoke()
        }
    }

    fun performDirectMaintenance(
        device: Device,
        technicianName: String,
        actionTaken: String,
        healthReport: Boolean = false,
        diskCleanup: Boolean = false,
        hardwareCleanup: Boolean = false,
        checkingDriveError: Boolean = false,
        scanningVirus: Boolean = false,
        checkingNetwork: Boolean = false,
        updatingAntivirus: Boolean = false,
        updatingAplikasi: Boolean = false,
        windowsLicense: String? = null,
        officeLicense: String? = null,
        notes: String? = null,
        signatureData: String? = null,
        techSignatureData: String? = null,
        timestamp: Long = System.currentTimeMillis(),
        healthReportBeforePhoto: String? = null,
        healthReportAfterPhoto: String? = null,
        diskCleanupBeforePhoto: String? = null,
        diskCleanupAfterPhoto: String? = null,
        hardwareCleanupBeforePhoto: String? = null,
        hardwareCleanupAfterPhoto: String? = null,
        checkingDriveErrorBeforePhoto: String? = null,
        checkingDriveErrorAfterPhoto: String? = null,
        scanningVirusBeforePhoto: String? = null,
        scanningVirusAfterPhoto: String? = null,
        checkingNetworkBeforePhoto: String? = null,
        checkingNetworkAfterPhoto: String? = null,
        updatingAntivirusBeforePhoto: String? = null,
        updatingAntivirusAfterPhoto: String? = null,
        updatingAplikasiBeforePhoto: String? = null,
        updatingAplikasiAfterPhoto: String? = null,
        onComplete: (() -> Unit)? = null
    ) {
        performBatchDirectMaintenance(
            listOf(
                DirectMaintenanceItem(
                    device = device,
                    technicianName = technicianName,
                    actionTaken = actionTaken,
                    healthReport = healthReport,
                    diskCleanup = diskCleanup,
                    hardwareCleanup = hardwareCleanup,
                    checkingDriveError = checkingDriveError,
                    scanningVirus = scanningVirus,
                    checkingNetwork = checkingNetwork,
                    updatingAntivirus = updatingAntivirus,
                    updatingAplikasi = updatingAplikasi,
                    windowsLicense = windowsLicense,
                    officeLicense = officeLicense,
                    notes = notes,
                    signatureData = signatureData,
                    techSignatureData = techSignatureData,
                    timestamp = timestamp,
                    healthReportBeforePhoto = healthReportBeforePhoto,
                    healthReportAfterPhoto = healthReportAfterPhoto,
                    diskCleanupBeforePhoto = diskCleanupBeforePhoto,
                    diskCleanupAfterPhoto = diskCleanupAfterPhoto,
                    hardwareCleanupBeforePhoto = hardwareCleanupBeforePhoto,
                    hardwareCleanupAfterPhoto = hardwareCleanupAfterPhoto,
                    checkingDriveErrorBeforePhoto = checkingDriveErrorBeforePhoto,
                    checkingDriveErrorAfterPhoto = checkingDriveErrorAfterPhoto,
                    scanningVirusBeforePhoto = scanningVirusBeforePhoto,
                    scanningVirusAfterPhoto = scanningVirusAfterPhoto,
                    checkingNetworkBeforePhoto = checkingNetworkBeforePhoto,
                    checkingNetworkAfterPhoto = checkingNetworkAfterPhoto,
                    updatingAntivirusBeforePhoto = updatingAntivirusBeforePhoto,
                    updatingAntivirusAfterPhoto = updatingAntivirusAfterPhoto,
                    updatingAplikasiBeforePhoto = updatingAplikasiBeforePhoto,
                    updatingAplikasiAfterPhoto = updatingAplikasiAfterPhoto
                )
            ),
            onComplete = onComplete
        )
    }

    fun cancelMaintenanceForDeviceInMonth(device: Device, timestamp: Long, onComplete: (() -> Unit)? = null) {
        val targetCal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val targetYear = targetCal.get(java.util.Calendar.YEAR)
        val targetMonth = targetCal.get(java.util.Calendar.MONTH)

        val currentLogs = maintenanceLogs.value
        val allDevices = devices.value

        val deviceSn = device.sn.trim()
        val deviceBarcode = device.serialNumber.trim()

        val matchingDeviceIds = allDevices.filter { dev ->
            dev.id == device.id ||
            (deviceSn.isNotEmpty() && dev.sn.trim().equals(deviceSn, ignoreCase = true)) ||
            (deviceBarcode.isNotEmpty() && dev.serialNumber.trim().equals(deviceBarcode, ignoreCase = true))
        }.map { it.id }.toSet()

        val logsToDelete = currentLogs.filter { log ->
            if (log.deviceId in matchingDeviceIds) {
                val logCal = java.util.Calendar.getInstance().apply { timeInMillis = log.timestamp }
                logCal.get(java.util.Calendar.YEAR) == targetYear && logCal.get(java.util.Calendar.MONTH) == targetMonth
            } else false
        }

        viewModelScope.launch(Dispatchers.IO) {
            val prefs = applicationContext.getSharedPreferences("supabase_r2_prefs", android.content.Context.MODE_PRIVATE)
            val url = prefs.getString("supabase_url", "") ?: ""
            val key = prefs.getString("supabase_anon_key", "") ?: ""
            val workerUrl = prefs.getString("cloudflare_worker_url", "") ?: ""

            for (log in logsToDelete) {
                repository.removeMaintenanceLog(log.id)
                if (url.isNotBlank() && key.isNotBlank()) {
                    supabaseRepository.deleteMaintenanceLogWithPhotos(url, key, workerUrl, log)
                }
            }

            val remainingLogs = currentLogs.filter { it !in logsToDelete && it.deviceId in matchingDeviceIds }
            val newLastMaintenance = remainingLogs.maxOfOrNull { it.timestamp } ?: 0L
            val updatedDevice = device.copy(lastMaintenance = newLastMaintenance)
            repository.addDevice(updatedDevice)
            if (url.isNotBlank() && key.isNotBlank()) {
                supabaseRepository.saveOrUpdateSingleDevice(url, key, updatedDevice)
            }
            triggerAutoSync()
            withContext(Dispatchers.Main) {
                onComplete?.invoke()
            }
        }
    }

    fun addUploadedFile(fileName: String, fileSize: String, fileType: String) {
        viewModelScope.launch {
            repository.addUploadedFile(
                UploadedFile(
                    fileName = fileName,
                    fileSize = fileSize,
                    fileType = fileType
                )
            )
            triggerAutoSync()
        }
    }

    fun deleteUploadedFile(id: Int) {
        viewModelScope.launch {
            val file = repository.getUploadedFilesList().find { it.id == id }
            repository.removeUploadedFile(id)
            val prefs = applicationContext.getSharedPreferences("supabase_r2_prefs", android.content.Context.MODE_PRIVATE)
            val url = prefs.getString("supabase_url", "") ?: ""
            val key = prefs.getString("supabase_anon_key", "") ?: ""
            val workerUrl = prefs.getString("cloudflare_worker_url", "") ?: ""
            if (file != null && workerUrl.isNotBlank()) {
                file.fileUri?.let { if (it.startsWith("http")) cloudStorageManager.deleteFromR2(workerUrl, it) }
            }
            if (url.isNotBlank() && key.isNotBlank()) {
                supabaseRepository.deleteUploadedFileFromSupabase(url, key, id)
            }
            triggerAutoSync()
        }
    }

    fun clearAllMaintenanceLogs() {
        viewModelScope.launch {
            repository.clearAllMaintenanceLogs()
            triggerAutoSync()
        }
    }

    private fun convertUriToBase64(context: android.content.Context, uriString: String?): String {
        if (uriString.isNullOrBlank() || uriString == "null" || uriString == "undefined") return ""
        if (!uriString.startsWith("file:") && !uriString.startsWith("content:") && !uriString.startsWith("/")) {
            return uriString
        }
        try {
            val uri = android.net.Uri.parse(uriString)
            val isOurFileProvider = uri.authority == "${context.packageName}.fileprovider"
            var inputStream: java.io.InputStream? = null
            
            if (uriString.startsWith("/") || uri.scheme.isNullOrBlank()) {
                val file = java.io.File(uriString)
                if (file.exists()) {
                    inputStream = java.io.FileInputStream(file)
                }
            }
            
            if (inputStream == null && (uri.scheme == "file" || uriString.startsWith("file://"))) {
                val path = uri.path
                if (path != null) {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        inputStream = java.io.FileInputStream(file)
                    }
                }
            }
            
            if (inputStream == null && isOurFileProvider) {
                val lastPathSegment = uri.lastPathSegment
                if (lastPathSegment != null) {
                    val cachePath = java.io.File(context.cacheDir, "images")
                    val localFile = java.io.File(cachePath, lastPathSegment)
                    if (localFile.exists()) {
                        inputStream = java.io.FileInputStream(localFile)
                    } else {
                        val maintPath = java.io.File(context.filesDir, "maintenance_photos")
                        val maintFile = java.io.File(maintPath, lastPathSegment)
                        if (maintFile.exists()) {
                            inputStream = java.io.FileInputStream(maintFile)
                        }
                    }
                }
            }
            
            if (inputStream == null) {
                inputStream = context.contentResolver.openInputStream(uri)
            }
            
            if (inputStream == null) return uriString
            
            val bytes = inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return uriString
            
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            
            val maxDimension = 300
            var sampleSize = 1
            val height = options.outHeight
            val width = options.outWidth
            if (height > maxDimension || width > maxDimension) {
                while (height / sampleSize > maxDimension || width / sampleSize > maxDimension) {
                    sampleSize *= 2
                }
            }
            
            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return uriString
            
            val baos = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, baos)
            val compressedBytes = baos.toByteArray()
            
            return android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
        } catch (t: Throwable) {
            t.printStackTrace()
            return uriString
        }
    }

    private fun isContentUriReadable(context: android.content.Context, uriString: String): Boolean {
        return try {
            val uri = android.net.Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun isFileExists(pathOrUri: String): Boolean {
        return try {
            val path = if (pathOrUri.startsWith("file://")) pathOrUri.substring(7) else pathOrUri
            java.io.File(path).exists()
        } catch (e: Exception) {
            false
        }
    }

    private fun convertBase64ToLocalUri(context: android.content.Context, base64Str: String?): String? {
        if (base64Str.isNullOrBlank() || base64Str == "null" || base64Str == "undefined") return null
        if (base64Str.startsWith("file:") || base64Str.startsWith("content:") || base64Str.startsWith("/") || base64Str.startsWith("http://") || base64Str.startsWith("https://")) {
            if (base64Str.startsWith("content://") && !isContentUriReadable(context, base64Str)) {
                return null
            }
            if ((base64Str.startsWith("file://") || base64Str.startsWith("/")) && !isFileExists(base64Str)) {
                return null
            }
            return base64Str
        }
        if (base64Str.length < 100) {
            return base64Str
        }
        try {
            val decodedBytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
            if (decodedBytes.isEmpty()) return base64Str
            
            val cachePath = java.io.File(context.filesDir, "maintenance_photos")
            cachePath.mkdirs()
            val file = java.io.File(cachePath, "img_sync_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}.jpg")
            file.outputStream().use { os ->
                os.write(decodedBytes)
            }
            return android.net.Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return base64Str
        }
    }

    fun isDeviceAlreadyMaintainedInMonth(device: Device, timestamp: Long): Boolean {
        val targetCal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val targetYear = targetCal.get(java.util.Calendar.YEAR)
        val targetMonth = targetCal.get(java.util.Calendar.MONTH)

        val currentLogs = maintenanceLogs.value
        val allDevices = devices.value

        val deviceSn = device.sn.trim()
        val deviceBarcode = device.serialNumber.trim()

        val matchingDeviceIds = allDevices.filter { dev ->
            dev.id == device.id ||
            (deviceSn.isNotEmpty() && dev.sn.trim().equals(deviceSn, ignoreCase = true)) ||
            (deviceBarcode.isNotEmpty() && dev.serialNumber.trim().equals(deviceBarcode, ignoreCase = true))
        }.map { it.id }.toSet()

        return currentLogs.any { log ->
            if (log.deviceId in matchingDeviceIds) {
                val logCal = java.util.Calendar.getInstance().apply { timeInMillis = log.timestamp }
                val logYear = logCal.get(java.util.Calendar.YEAR)
                val logMonth = logCal.get(java.util.Calendar.MONTH)
                logYear == targetYear && logMonth == targetMonth
            } else {
                false
            }
        }
    }

    fun saveUriToInternalStorage(context: android.content.Context, uri: android.net.Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val cachePath = java.io.File(context.filesDir, "maintenance_photos")
            cachePath.mkdirs()
            val file = java.io.File(cachePath, "img_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}.jpg")
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            android.net.Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveBaFileToInternalStorage(context: android.content.Context, uri: android.net.Uri, fileName: String?): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val cachePath = java.io.File(context.filesDir, "ba_files")
            cachePath.mkdirs()
            val safeName = if (!fileName.isNullOrBlank()) {
                fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            } else {
                "ba_${System.currentTimeMillis()}.pdf"
            }
            val file = java.io.File(cachePath, safeName)
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            android.net.Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun convertFileToBase64(context: android.content.Context, uriString: String?): String {
        if (uriString.isNullOrBlank() || uriString == "null" || uriString == "undefined") return ""
        if (!uriString.startsWith("file:") && !uriString.startsWith("content:") && !uriString.startsWith("/")) {
            return uriString
        }
        try {
            val uri = android.net.Uri.parse(uriString)
            val isOurFileProvider = uri.authority == "${context.packageName}.fileprovider"
            var inputStream: java.io.InputStream? = null
            
            if (uriString.startsWith("/") || uri.scheme.isNullOrBlank()) {
                val file = java.io.File(uriString)
                if (file.exists()) {
                    inputStream = java.io.FileInputStream(file)
                }
            }
            
            if (inputStream == null && (uri.scheme == "file" || uriString.startsWith("file://"))) {
                val path = uri.path
                if (path != null) {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        inputStream = java.io.FileInputStream(file)
                    }
                }
            }
            
            if (inputStream == null && isOurFileProvider) {
                val lastPathSegment = uri.lastPathSegment
                if (lastPathSegment != null) {
                    val cachePath = java.io.File(context.cacheDir, "images")
                    val localFile = java.io.File(cachePath, lastPathSegment)
                    if (localFile.exists()) {
                        inputStream = java.io.FileInputStream(localFile)
                    } else {
                        val maintPath = java.io.File(context.filesDir, "maintenance_photos")
                        val maintFile = java.io.File(maintPath, lastPathSegment)
                        if (maintFile.exists()) {
                            inputStream = java.io.FileInputStream(maintFile)
                        } else {
                            val baPath = java.io.File(context.filesDir, "ba_files")
                            val baFile = java.io.File(baPath, lastPathSegment)
                            if (baFile.exists()) {
                                inputStream = java.io.FileInputStream(baFile)
                            }
                        }
                    }
                }
            }
            
            if (inputStream == null) {
                inputStream = context.contentResolver.openInputStream(uri)
            }
            
            if (inputStream == null) return uriString
            
            val bytes = inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return uriString
            
            return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (t: Throwable) {
            t.printStackTrace()
            return uriString
        }
    }

    private fun convertBase64ToLocalBaFileUri(context: android.content.Context, base64Str: String?, fileName: String?): String? {
        if (base64Str.isNullOrBlank() || base64Str == "null" || base64Str == "undefined") return null
        if (base64Str.startsWith("file:") || base64Str.startsWith("content:") || base64Str.startsWith("/") || base64Str.startsWith("http://") || base64Str.startsWith("https://")) {
            return base64Str
        }
        if (base64Str.length < 5) {
            return base64Str
        }
        try {
            val decodedBytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
            if (decodedBytes.isEmpty()) return base64Str
            
            val cachePath = java.io.File(context.filesDir, "ba_files")
            cachePath.mkdirs()
            val safeFileName = if (!fileName.isNullOrBlank()) {
                fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            } else {
                "ba_${System.currentTimeMillis()}.pdf"
            }
            val file = java.io.File(cachePath, safeFileName)
            file.outputStream().use { os ->
                os.write(decodedBytes)
            }
            return android.net.Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return base64Str
        }
    }

    suspend fun uploadFileToGoogleDrive(context: android.content.Context, uri: android.net.Uri, mimeType: String, originalName: String): Triple<String, String, String>? = withContext(Dispatchers.IO) {
        val sharedPrefs = context.getSharedPreferences("google_sheets_sync_prefs", android.content.Context.MODE_PRIVATE)
        val webAppUrl = sharedPrefs.getString("web_app_url", "") ?: ""
        if (webAppUrl.isBlank()) return@withContext null
        return@withContext uploadFileToGoogleDriveInternal(context, uri.toString(), mimeType, originalName, webAppUrl)
    }

    suspend fun downloadFileFromUrl(context: android.content.Context, urlStr: String, folderName: String): String? = withContext(Dispatchers.IO) {
        if (urlStr.isBlank() || !urlStr.startsWith("http")) return@withContext null
        try {
            val fileId = extractFileIdFromUrl(urlStr) ?: urlStr.hashCode().toString()
            val extension = if (folderName == "ba_files") ".pdf" else ".jpg"
            val cachePath = java.io.File(context.filesDir, folderName)
            cachePath.mkdirs()
            val destFile = java.io.File(cachePath, "gdrive_${fileId}${extension}")
            
            android.util.Log.d("DownloadDrive", "Checking local cache for Drive file: ${destFile.absolutePath}")
            if (destFile.exists() && destFile.length() > 0) {
                android.util.Log.d("DownloadDrive", "File already cached: ${destFile.absolutePath}")
                return@withContext android.net.Uri.fromFile(destFile).toString()
            }

            android.util.Log.d("DownloadDrive", "Downloading file from Drive: $urlStr")
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val request = okhttp3.Request.Builder()
                .url(urlStr)
                .build()

            client.newCall(request).execute().use { response ->
                android.util.Log.d("DownloadDrive", "Download response code for file: ${response.code}")
                if (response.isSuccessful) {
                    val body = response.body ?: return@withContext null
                    destFile.outputStream().use { output ->
                        body.byteStream().copyTo(output)
                    }
                    android.util.Log.d("DownloadDrive", "Successfully downloaded and saved file: ${destFile.absolutePath}, Size: ${destFile.length()} bytes")
                    return@withContext android.net.Uri.fromFile(destFile).toString()
                } else {
                    android.util.Log.e("DownloadDrive", "Failed to download file: code ${response.code}, message: ${response.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadDrive", "Exception during file download: ${e.message}", e)
            e.printStackTrace()
        }
        return@withContext null
    }

    fun getLocalFilePathForUrl(context: android.content.Context, urlOrUri: String?, folderName: String): String? {
        if (urlOrUri.isNullOrBlank() || urlOrUri == "null" || urlOrUri == "undefined") return null
        
        var cleanUrl = urlOrUri
        if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            if (cleanUrl.contains("pub-r2.yourdomain.com") || cleanUrl.contains("example.com") || cleanUrl.contains("yourdomain.com")) {
                val prefs = context.getSharedPreferences("supabase_r2_prefs", android.content.Context.MODE_PRIVATE)
                val workerUrl = prefs.getString("cloudflare_worker_url", "") ?: ""
                if (workerUrl.isNotBlank()) {
                    val fileKey = cleanUrl.substringBefore("?").substringAfterLast("/")
                    cleanUrl = "${workerUrl.trimEnd('/')}/$fileKey"
                }
            }
            return cleanUrl
        }
        
        return cleanUrl
    }

    private fun extractFileIdFromUrl(url: String): String? {
        if (!url.startsWith("http")) return url
        try {
            val uri = android.net.Uri.parse(url)
            val idParam = uri.getQueryParameter("id")
            if (!idParam.isNullOrBlank()) return idParam
            
            val pathSegments = uri.pathSegments
            val dIndex = pathSegments.indexOf("d")
            if (dIndex != -1 && dIndex + 1 < pathSegments.size) {
                return pathSegments[dIndex + 1]
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun downloadAllDriveFiles(
        context: android.content.Context,
        devicesList: List<Device>? = null,
        techniciansList: List<Technician>? = null,
        ticketsList: List<TroubleTicket>? = null,
        logsList: List<MaintenanceLog>? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val devs = devicesList ?: repository.devices.first()
            val techs = techniciansList ?: repository.technicians.first()
            val tickets = ticketsList ?: repository.troubleTickets.first()
            val mLogs = logsList ?: repository.maintenanceLogs.first()

            android.util.Log.d("DownloadDrive", "Triggering downloadAllDriveFiles. Devs: ${devs.size}, Techs: ${techs.size}, Tickets: ${tickets.size}, Logs: ${mLogs.size}")

            devs.forEach { device ->
                if (device.photoUri?.startsWith("http") == true) {
                    downloadFileFromUrl(context, device.photoUri, "maintenance_photos")
                }
                if (device.baFileUri?.startsWith("http") == true) {
                    downloadFileFromUrl(context, device.baFileUri, "ba_files")
                }
            }
            techs.forEach { tech ->
                if (tech.photoUri?.startsWith("http") == true) {
                    downloadFileFromUrl(context, tech.photoUri, "maintenance_photos")
                }
            }
            tickets.forEach { ticket ->
                if (ticket.photoBefore?.startsWith("http") == true) {
                    downloadFileFromUrl(context, ticket.photoBefore, "maintenance_photos")
                }
                if (ticket.photoAfter?.startsWith("http") == true) {
                    downloadFileFromUrl(context, ticket.photoAfter, "maintenance_photos")
                }
            }
            mLogs.forEach { log ->
                val photos = listOf(
                    log.healthReportBeforePhoto, log.healthReportAfterPhoto,
                    log.diskCleanupBeforePhoto, log.diskCleanupAfterPhoto,
                    log.hardwareCleanupBeforePhoto, log.hardwareCleanupAfterPhoto,
                    log.checkingDriveErrorBeforePhoto, log.checkingDriveErrorAfterPhoto,
                    log.scanningVirusBeforePhoto, log.scanningVirusAfterPhoto,
                    log.checkingNetworkBeforePhoto, log.checkingNetworkAfterPhoto,
                    log.updatingAntivirusBeforePhoto, log.updatingAntivirusAfterPhoto,
                    log.updatingAplikasiBeforePhoto, log.updatingAplikasiAfterPhoto
                )
                photos.forEach { photo ->
                    if (photo?.startsWith("http") == true) {
                        downloadFileFromUrl(context, photo, "maintenance_photos")
                    }
                }
            }
        }
    }

    private fun parseDateToMillis(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
        
        dateStr.toLongOrNull()?.let {
            return if (it < 10000000000L) it * 1000L else it
        }
        
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy",
            "EEE MMM dd yyyy HH:mm:ss"
        )
        
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                val parsed = sdf.parse(dateStr)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        
        try {
            val cleanStr = dateStr.trim()
            val parts = cleanStr.split(" ", "T")
            if (parts.isNotEmpty()) {
                val dParts = parts[0].split("-", "/", ".")
                if (dParts.size == 3) {
                    val p1 = dParts[0].toIntOrNull() ?: 0
                    val p2 = dParts[1].toIntOrNull() ?: 0
                    val p3 = dParts[2].toIntOrNull() ?: 0
                    val year: Int
                    val month: Int
                    val day: Int
                    if (p1 > 1000) {
                        year = p1; month = p2 - 1; day = p3
                    } else {
                        day = p1; month = p2 - 1; year = p3
                    }
                    var hour = 0
                    var min = 0
                    var sec = 0
                    if (parts.size > 1) {
                        val tParts = parts[1].split(":")
                        if (tParts.isNotEmpty()) hour = tParts[0].toIntOrNull() ?: 0
                        if (tParts.size > 1) min = tParts[1].toIntOrNull() ?: 0
                        if (tParts.size > 2) sec = tParts[2].toIntOrNull() ?: 0
                    }
                    if (year > 1900 && month in 0..11 && day in 1..31) {
                        val cal = java.util.Calendar.getInstance()
                        cal.set(year, month, day, hour, min, sec)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                        return cal.timeInMillis
                    }
                }
            }
        } catch (_: Exception) {}

        return System.currentTimeMillis()
    }
}

class ViewModelFactory(
    private val repository: MaintenanceRepository,
    private val applicationContext: android.content.Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MaintenanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MaintenanceViewModel(repository, applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
