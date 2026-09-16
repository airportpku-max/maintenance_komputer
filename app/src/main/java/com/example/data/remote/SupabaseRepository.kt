package com.example.data.remote

import com.example.data.Device
import com.example.data.MaintenanceLog
import com.example.data.Technician
import com.example.data.TroubleTicket
import com.example.data.UploadedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val cloudStorageManager: CloudStorageManager = CloudStorageManager()
) {

    private fun buildRequest(supabaseUrl: String, anonKey: String, path: String): Request.Builder {
        var cleanUrl = supabaseUrl.trim().trimEnd('/')
        if (cleanUrl.endsWith("/rest/v1")) {
            cleanUrl = cleanUrl.substringBefore("/rest/v1").trimEnd('/')
        }
        val cleanKey = anonKey.trim()
        return Request.Builder()
            .url("$cleanUrl/rest/v1/$path")
            .addHeader("apikey", cleanKey)
            .addHeader("Authorization", "Bearer $cleanKey")
            .addHeader("Content-Type", "application/json")
    }

    // --- DEVICES ---

    suspend fun getDevices(supabaseUrl: String, anonKey: String): Result<List<Device>> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(supabaseUrl, anonKey, "devices?select=*").get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Supabase HTTP ${response.code}: $body"))

            val jsonArray = JSONArray(body)
            val list = mutableListOf<Device>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    Device(
                        id = obj.optInt("id"),
                        type = obj.optString("type", obj.optString("qr_code", "AIO")),
                        name = obj.optString("name", obj.optString("device_name", "")),
                        brand = obj.optString("brand", ""),
                        serialNumber = obj.optString("barcode_id", obj.optString("serial_number", obj.optString("serialNumber", ""))),
                        condition = obj.optString("condition", obj.optString("status", "Baik")),
                        lastMaintenance = obj.optLong("last_maintenance", obj.optLong("lastMaintenance", System.currentTimeMillis())),
                        description = obj.optString("description", obj.optString("location", "")),
                        sn = obj.optString("sn", obj.optString("model", "")),
                        photoUri = obj.optString("photo_uri", obj.optString("photoUri", "")).takeIf { it.isNotBlank() },
                        baFileUri = obj.optString("ba_file_uri", obj.optString("baFileUri", "")).takeIf { it.isNotBlank() },
                        baFileName = obj.optString("ba_file_name", obj.optString("baFileName", "")).takeIf { it.isNotBlank() }
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveOrUpdateSingleDevice(supabaseUrl: String, anonKey: String, device: Device): Result<Device> = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || anonKey.isBlank()) {
            return@withContext Result.failure(Exception("Supabase belum dikonfigurasi di menu Profil"))
        }
        try {
            val barcode = device.serialNumber.trim()
            val singleObj = JSONObject().apply {
                put("type", device.type)
                put("name", device.name)
                put("brand", device.brand)
                put("barcode_id", device.serialNumber)
                put("condition", device.condition)
                put("last_maintenance", device.lastMaintenance)
                put("description", device.description)
                put("sn", device.sn)
                put("photo_uri", device.photoUri ?: "")
                put("ba_file_uri", device.baFileUri ?: "")
                put("ba_file_name", device.baFileName ?: "")
            }

            // Cek apakah perangkat sudah ada di Supabase berdasarkan id atau barcode_id
            var existingRemoteId: Int? = null
            if (device.id > 0) {
                try {
                    val checkIdReq = buildRequest(supabaseUrl, anonKey, "devices?id=eq.${device.id}&select=id").get().build()
                    val checkIdResp = client.newCall(checkIdReq).execute()
                    val body = checkIdResp.body?.string() ?: "[]"
                    checkIdResp.close()
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        existingRemoteId = arr.getJSONObject(0).optInt("id")
                    }
                } catch (e: Exception) {
                    // Ignore, coba cek barcode
                }
            }
            if (existingRemoteId == null && barcode.isNotBlank()) {
                try {
                    val checkBcReq = buildRequest(supabaseUrl, anonKey, "devices?barcode_id=eq.${android.net.Uri.encode(barcode)}&select=id").get().build()
                    val checkBcResp = client.newCall(checkBcReq).execute()
                    val body = checkBcResp.body?.string() ?: "[]"
                    checkBcResp.close()
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        existingRemoteId = arr.getJSONObject(0).optInt("id")
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            if (existingRemoteId != null && existingRemoteId > 0) {
                // PATCH update baris yang sudah ada
                val patchReq = buildRequest(supabaseUrl, anonKey, "devices?id=eq.$existingRemoteId")
                    .addHeader("Prefer", "return=representation")
                    .patch(singleObj.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                val patchResp = client.newCall(patchReq).execute()
                val patchBody = patchResp.body?.string() ?: ""
                val patchSuccess = patchResp.isSuccessful
                val patchCode = patchResp.code
                patchResp.close()
                if (patchSuccess) {
                    return@withContext Result.success(device.copy(id = existingRemoteId))
                } else {
                    return@withContext Result.failure(Exception("Gagal update perangkat (HTTP $patchCode): $patchBody"))
                }
            } else {
                // POST insert baris baru (tanpa id agar sequence Supabase auto-increment tanpa tabrakan)
                val postReq = buildRequest(supabaseUrl, anonKey, "devices")
                    .addHeader("Prefer", "return=representation")
                    .post(singleObj.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                val postResp = client.newCall(postReq).execute()
                val postBody = postResp.body?.string() ?: ""
                val postSuccess = postResp.isSuccessful
                val postCode = postResp.code
                postResp.close()

                if (postSuccess) {
                    val arr = JSONArray(postBody)
                    val newId = if (arr.length() > 0) arr.getJSONObject(0).optInt("id", device.id) else device.id
                    return@withContext Result.success(device.copy(id = newId))
                } else {
                    return@withContext Result.failure(Exception("Gagal insert perangkat (HTTP $postCode): $postBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertNewDevices(supabaseUrl: String, anonKey: String, devices: List<Device>): Result<List<Device>> = withContext(Dispatchers.IO) {
        if (devices.isEmpty()) return@withContext Result.success(emptyList())
        try {
            val jsonArray = JSONArray()
            for (dev in devices) {
                val obj = JSONObject().apply {
                    put("type", dev.type)
                    put("name", dev.name)
                    put("brand", dev.brand)
                    put("barcode_id", dev.serialNumber)
                    put("condition", dev.condition)
                    put("last_maintenance", dev.lastMaintenance)
                    put("description", dev.description)
                    put("sn", dev.sn)
                    put("photo_uri", dev.photoUri ?: "")
                    put("ba_file_uri", dev.baFileUri ?: "")
                    put("ba_file_name", dev.baFileName ?: "")
                }
                jsonArray.put(obj)
            }

            val request = buildRequest(supabaseUrl, anonKey, "devices")
                .addHeader("Prefer", "return=representation")
                .post(jsonArray.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (!response.isSuccessful) {
                val restoreRes = forceRestoreDevices(supabaseUrl, anonKey, devices)
                if (restoreRes.isSuccess) {
                    return@withContext Result.success(devices)
                }
                return@withContext Result.failure(Exception("Gagal simpan devices ke Supabase (HTTP ${response.code}): $body"))
            }

            val returnedArray = JSONArray(body)
            val created = mutableListOf<Device>()
            for (i in 0 until returnedArray.length()) {
                val obj = returnedArray.getJSONObject(i)
                val assignedId = obj.optInt("id", 0)
                val orig = devices.getOrNull(i)
                if (orig != null) {
                    created.add(orig.copy(id = if (assignedId > 0) assignedId else orig.id))
                }
            }
            Result.success(created)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertDevices(supabaseUrl: String, anonKey: String, devices: List<Device>): Result<Boolean> = withContext(Dispatchers.IO) {
        if (devices.isEmpty()) return@withContext Result.success(true)
        val res = forceRestoreDevices(supabaseUrl, anonKey, devices)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Gagal upsert devices"))
    }

    suspend fun forceRestoreDevices(supabaseUrl: String, anonKey: String, devices: List<Device>): Result<Int> = withContext(Dispatchers.IO) {
        if (devices.isEmpty()) return@withContext Result.success(0)
        try {
            var successCount = 0

            // 1. Try Batch Upsert with on_conflict=barcode_id
            val jsonArrayBarcode = JSONArray()
            for (dev in devices) {
                val obj = JSONObject().apply {
                    if (dev.id > 0) put("id", dev.id)
                    put("type", dev.type)
                    put("name", dev.name)
                    put("brand", dev.brand)
                    put("barcode_id", dev.serialNumber)
                    put("condition", dev.condition)
                    put("last_maintenance", dev.lastMaintenance)
                    put("description", dev.description)
                    put("sn", dev.sn)
                    put("photo_uri", dev.photoUri ?: "")
                    put("ba_file_uri", dev.baFileUri ?: "")
                    put("ba_file_name", dev.baFileName ?: "")
                }
                jsonArrayBarcode.put(obj)
            }

            val requestBarcode = buildRequest(supabaseUrl, anonKey, "devices?on_conflict=barcode_id")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(jsonArrayBarcode.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val responseBarcode = client.newCall(requestBarcode).execute()
            if (responseBarcode.isSuccessful) {
                responseBarcode.close()
                return@withContext Result.success(devices.size)
            }
            responseBarcode.close()

            // 2. Try Batch Upsert with on_conflict=id
            val requestId = buildRequest(supabaseUrl, anonKey, "devices?on_conflict=id")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(jsonArrayBarcode.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val responseId = client.newCall(requestId).execute()
            if (responseId.isSuccessful) {
                responseId.close()
                return@withContext Result.success(devices.size)
            }
            responseId.close()

            // 3. Try plain POST batch without id (for empty/fresh table)
            val jsonArrayNoId = JSONArray()
            for (dev in devices) {
                val obj = JSONObject().apply {
                    put("type", dev.type)
                    put("name", dev.name)
                    put("brand", dev.brand)
                    put("barcode_id", dev.serialNumber)
                    put("condition", dev.condition)
                    put("last_maintenance", dev.lastMaintenance)
                    put("description", dev.description)
                    put("sn", dev.sn)
                    put("photo_uri", dev.photoUri ?: "")
                    put("ba_file_uri", dev.baFileUri ?: "")
                    put("ba_file_name", dev.baFileName ?: "")
                }
                jsonArrayNoId.put(obj)
            }

            val requestPlain = buildRequest(supabaseUrl, anonKey, "devices")
                .post(jsonArrayNoId.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val responsePlain = client.newCall(requestPlain).execute()
            if (responsePlain.isSuccessful) {
                responsePlain.close()
                return@withContext Result.success(devices.size)
            }
            responsePlain.close()

            // 4. Fallback: Individual item upsert (Check barcode_id, then PATCH or POST)
            for (dev in devices) {
                val singleObj = JSONObject().apply {
                    put("type", dev.type)
                    put("name", dev.name)
                    put("brand", dev.brand)
                    put("barcode_id", dev.serialNumber)
                    put("condition", dev.condition)
                    put("last_maintenance", dev.lastMaintenance)
                    put("description", dev.description)
                    put("sn", dev.sn)
                    put("photo_uri", dev.photoUri ?: "")
                    put("ba_file_uri", dev.baFileUri ?: "")
                    put("ba_file_name", dev.baFileName ?: "")
                }

                val checkUrl = "devices?barcode_id=eq.${android.net.Uri.encode(dev.serialNumber)}"
                val checkReq = buildRequest(supabaseUrl, anonKey, checkUrl).get().build()
                val checkResp = client.newCall(checkReq).execute()
                val checkBody = checkResp.body?.string() ?: "[]"
                val checkArr = JSONArray(checkBody)

                if (checkArr.length() > 0) {
                    val remoteId = checkArr.getJSONObject(0).optInt("id")
                    val patchReq = buildRequest(supabaseUrl, anonKey, "devices?id=eq.$remoteId")
                        .patch(singleObj.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                    val patchResp = client.newCall(patchReq).execute()
                    if (patchResp.isSuccessful) successCount++
                    patchResp.close()
                } else {
                    val postReq = buildRequest(supabaseUrl, anonKey, "devices")
                        .post(singleObj.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()
                    val postResp = client.newCall(postReq).execute()
                    if (postResp.isSuccessful) successCount++
                    postResp.close()
                }
            }

            if (successCount > 0) {
                Result.success(successCount)
            } else {
                Result.failure(Exception("Gagal mengunggah data devices ke Supabase. Periksa koneksi atau schema tabel."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDeviceFromSupabase(supabaseUrl: String, anonKey: String, id: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        if (id <= 0 || supabaseUrl.isBlank() || anonKey.isBlank()) return@withContext Result.success(true)
        try {
            val request = buildRequest(supabaseUrl, anonKey, "devices?id=eq.$id").delete().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) Result.success(true) else Result.failure(Exception("Gagal hapus device"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- TECHNICIANS (REMOVED) ---

    suspend fun getTechnicians(supabaseUrl: String, anonKey: String): Result<List<Technician>> = withContext(Dispatchers.IO) {
        Result.success(emptyList())
    }

    suspend fun upsertTechnicians(supabaseUrl: String, anonKey: String, technicians: List<Technician>): Result<Boolean> = withContext(Dispatchers.IO) {
        Result.success(true)
    }

    // --- TROUBLE TICKETS ---

    suspend fun getTroubleTickets(supabaseUrl: String, anonKey: String): Result<List<TroubleTicket>> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(supabaseUrl, anonKey, "trouble_tickets?select=*").get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Supabase HTTP ${response.code}: $body"))

            val jsonArray = JSONArray(body)
            val list = mutableListOf<TroubleTicket>()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val rawTs = obj.opt("timestamp")
                val parsedTs: Long = when (rawTs) {
                    is Number -> rawTs.toLong()
                    is String -> {
                        try {
                            if (rawTs.matches(Regex("\\d+"))) rawTs.toLong()
                            else (sdf.parse(rawTs)?.time ?: System.currentTimeMillis())
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }
                    }
                    else -> System.currentTimeMillis()
                }

                list.add(
                    TroubleTicket(
                        id = obj.optInt("id"),
                        deviceId = obj.optInt("device_id", obj.optInt("deviceId", 0)),
                        deviceName = obj.optString("device_name", obj.optString("deviceName", "")),
                        description = obj.optString("description", ""),
                        reportedBy = obj.optString("reported_by", obj.optString("reportedBy", "")),
                        status = obj.optString("status", "Pending"),
                        timestamp = parsedTs,
                        actionTaken = obj.optString("action_taken", obj.optString("actionTaken", "")),
                        duration = obj.optString("duration", ""),
                        photoBefore = obj.optString("photo_before", obj.optString("photoBefore", "")).takeIf { it.isNotBlank() },
                        photoAfter = obj.optString("photo_after", obj.optString("photoAfter", "")).takeIf { it.isNotBlank() }
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertNewTroubleTickets(supabaseUrl: String, anonKey: String, tickets: List<TroubleTicket>): Result<List<TroubleTicket>> = withContext(Dispatchers.IO) {
        if (tickets.isEmpty()) return@withContext Result.success(emptyList())
        try {
            val jsonArray = JSONArray()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            for (t in tickets) {
                val formattedDateStr = sdf.format(java.util.Date(t.timestamp))
                val obj = JSONObject().apply {
                    put("device_id", t.deviceId)
                    put("device_name", t.deviceName)
                    put("description", t.description)
                    put("reported_by", t.reportedBy)
                    put("status", t.status)
                    put("timestamp", formattedDateStr)
                    put("action_taken", t.actionTaken ?: "")
                    put("duration", t.duration ?: "")
                    put("photo_before", t.photoBefore ?: "")
                    put("photo_after", t.photoAfter ?: "")
                }
                jsonArray.put(obj)
            }

            val request = buildRequest(supabaseUrl, anonKey, "trouble_tickets")
                .addHeader("Prefer", "return=representation")
                .post(jsonArray.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gagal simpan trouble tickets ke Supabase (HTTP ${response.code}): $body"))
            }

            val returnedArray = JSONArray(body)
            val created = mutableListOf<TroubleTicket>()
            for (i in 0 until returnedArray.length()) {
                val obj = returnedArray.getJSONObject(i)
                val assignedId = obj.optInt("id", 0)
                val orig = tickets.getOrNull(i)
                if (orig != null) {
                    created.add(orig.copy(id = if (assignedId > 0) assignedId else orig.id))
                }
            }
            Result.success(created)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertTroubleTickets(supabaseUrl: String, anonKey: String, tickets: List<TroubleTicket>): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val jsonArrayA = JSONArray()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            for (t in tickets) {
                val formattedDateStr = sdf.format(java.util.Date(t.timestamp))
                val obj = JSONObject().apply {
                    if (t.id > 0) put("id", t.id)
                    put("device_id", t.deviceId)
                    put("device_name", t.deviceName)
                    put("description", t.description)
                    put("reported_by", t.reportedBy)
                    put("status", t.status)
                    put("timestamp", formattedDateStr)
                    put("action_taken", t.actionTaken ?: "")
                    put("duration", t.duration ?: "")
                    put("photo_before", t.photoBefore ?: "")
                    put("photo_after", t.photoAfter ?: "")
                }
                jsonArrayA.put(obj)
            }

            val requestA = buildRequest(supabaseUrl, anonKey, "trouble_tickets?on_conflict=id")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(jsonArrayA.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val responseA = client.newCall(requestA).execute()
            val bodyA = responseA.body?.string() ?: ""
            if (responseA.isSuccessful) return@withContext Result.success(true)

            val requestA2 = buildRequest(supabaseUrl, anonKey, "trouble_tickets")
                .post(jsonArrayA.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
            val responseA2 = client.newCall(requestA2).execute()
            if (responseA2.isSuccessful) return@withContext Result.success(true)

            Result.failure(Exception("Gagal simpan trouble tickets ke Supabase (HTTP ${responseA.code}): $bodyA"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTroubleTicketFromSupabase(supabaseUrl: String, anonKey: String, id: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        if (id <= 0 || supabaseUrl.isBlank() || anonKey.isBlank()) return@withContext Result.success(true)
        try {
            val request = buildRequest(supabaseUrl, anonKey, "trouble_tickets?id=eq.$id").delete().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) Result.success(true) else Result.failure(Exception("Gagal hapus trouble ticket"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- MAINTENANCE LOGS ---

    suspend fun getMaintenanceLogs(supabaseUrl: String, anonKey: String): Result<List<MaintenanceLog>> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(supabaseUrl, anonKey, "maintenance_logs?select=*").get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Supabase HTTP ${response.code}: $body"))

            val jsonArray = JSONArray(body)
            val list = mutableListOf<MaintenanceLog>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    MaintenanceLog(
                        id = obj.optInt("id"),
                        deviceId = obj.optInt("device_id", obj.optInt("deviceId", 0)),
                        deviceName = obj.optString("device_name", obj.optString("deviceName", "")),
                        technicianName = obj.optString("technician_name", obj.optString("technicianName", "")),
                        actionTaken = obj.optString("action_taken", obj.optString("actionTaken", obj.optString("activity", ""))),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        healthReport = obj.optBoolean("health_report", obj.optBoolean("healthReport")),
                        diskCleanup = obj.optBoolean("disk_cleanup", obj.optBoolean("diskCleanup")),
                        hardwareCleanup = obj.optBoolean("hardware_cleanup", obj.optBoolean("hardwareCleanup")),
                        checkingDriveError = obj.optBoolean("checking_drive_error", obj.optBoolean("checkingDriveError")),
                        scanningVirus = obj.optBoolean("scanning_virus", obj.optBoolean("scanningVirus")),
                        checkingNetwork = obj.optBoolean("checking_network", obj.optBoolean("checkingNetwork")),
                        updatingAntivirus = obj.optBoolean("updating_antivirus", obj.optBoolean("updatingAntivirus")),
                        updatingAplikasi = obj.optBoolean("updating_aplikasi", obj.optBoolean("updatingAplikasi")),
                        windowsLicense = obj.optString("windows_license", obj.optString("windowsLicense", "")).takeIf { it.isNotBlank() },
                        officeLicense = obj.optString("office_license", obj.optString("officeLicense", "")).takeIf { it.isNotBlank() },
                        notes = obj.optString("notes", "").takeIf { it.isNotBlank() },
                        signatureData = obj.optString("signature_data", obj.optString("signatureData", "")).takeIf { it.isNotBlank() },
                        techSignatureData = obj.optString("tech_signature_data", obj.optString("techSignatureData", "")).takeIf { it.isNotBlank() },

                        // Cloudflare R2 Public URLs
                        healthReportBeforePhoto = obj.optString("health_report_before_photo", obj.optString("healthReportBeforePhoto", "")).takeIf { it.isNotBlank() },
                        healthReportAfterPhoto = obj.optString("health_report_after_photo", obj.optString("healthReportAfterPhoto", "")).takeIf { it.isNotBlank() },
                        diskCleanupBeforePhoto = obj.optString("disk_cleanup_before_photo", obj.optString("diskCleanupBeforePhoto", "")).takeIf { it.isNotBlank() },
                        diskCleanupAfterPhoto = obj.optString("disk_cleanup_after_photo", obj.optString("diskCleanupAfterPhoto", "")).takeIf { it.isNotBlank() },
                        hardwareCleanupBeforePhoto = obj.optString("hardware_cleanup_before_photo", obj.optString("hardwareCleanupBeforePhoto", "")).takeIf { it.isNotBlank() },
                        hardwareCleanupAfterPhoto = obj.optString("hardware_cleanup_after_photo", obj.optString("hardwareCleanupAfterPhoto", "")).takeIf { it.isNotBlank() },
                        checkingDriveErrorBeforePhoto = obj.optString("checking_drive_error_before_photo", obj.optString("checkingDriveErrorBeforePhoto", "")).takeIf { it.isNotBlank() },
                        checkingDriveErrorAfterPhoto = obj.optString("checking_drive_error_after_photo", obj.optString("checkingDriveErrorAfterPhoto", "")).takeIf { it.isNotBlank() },
                        scanningVirusBeforePhoto = obj.optString("scanning_virus_before_photo", obj.optString("scanningVirusBeforePhoto", "")).takeIf { it.isNotBlank() },
                        scanningVirusAfterPhoto = obj.optString("scanning_virus_after_photo", obj.optString("scanningVirusAfterPhoto", "")).takeIf { it.isNotBlank() },
                        checkingNetworkBeforePhoto = obj.optString("checking_network_before_photo", obj.optString("checkingNetworkBeforePhoto", "")).takeIf { it.isNotBlank() },
                        checkingNetworkAfterPhoto = obj.optString("checking_network_after_photo", obj.optString("checkingNetworkAfterPhoto", "")).takeIf { it.isNotBlank() },
                        updatingAntivirusBeforePhoto = obj.optString("updating_antivirus_before_photo", obj.optString("updatingAntivirusBeforePhoto", "")).takeIf { it.isNotBlank() },
                        updatingAntivirusAfterPhoto = obj.optString("updating_antivirus_after_photo", obj.optString("updatingAntivirusAfterPhoto", "")).takeIf { it.isNotBlank() },
                        updatingAplikasiBeforePhoto = obj.optString("updating_aplikasi_before_photo", obj.optString("updatingAplikasiBeforePhoto", "")).takeIf { it.isNotBlank() },
                        updatingAplikasiAfterPhoto = obj.optString("updating_aplikasi_after_photo", obj.optString("updatingAplikasiAfterPhoto", "")).takeIf { it.isNotBlank() }
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertNewMaintenanceLogs(supabaseUrl: String, anonKey: String, logs: List<MaintenanceLog>): Result<List<MaintenanceLog>> = withContext(Dispatchers.IO) {
        if (logs.isEmpty()) return@withContext Result.success(emptyList())
        try {
            val jsonArray = JSONArray()
            for (m in logs) {
                val obj = JSONObject().apply {
                    // Do NOT put "id" so Supabase auto-generates via SERIAL PRIMARY KEY
                    put("device_id", m.deviceId)
                    put("device_name", m.deviceName)
                    put("action_taken", m.actionTaken)
                    put("timestamp", m.timestamp)
                    put("health_report", m.healthReport)
                    put("disk_cleanup", m.diskCleanup)
                    put("hardware_cleanup", m.hardwareCleanup)
                    put("checking_drive_error", m.checkingDriveError)
                    put("scanning_virus", m.scanningVirus)
                    put("checking_network", m.checkingNetwork)
                    put("updating_antivirus", m.updatingAntivirus)
                    put("updating_aplikasi", m.updatingAplikasi)
                    put("notes", m.notes ?: "")
                    put("signature_data", m.signatureData ?: "")
                    put("tech_signature_data", m.techSignatureData ?: "")

                    // Cloudflare R2 Public URLs stored in Supabase
                    put("health_report_before_photo", m.healthReportBeforePhoto ?: "")
                    put("health_report_after_photo", m.healthReportAfterPhoto ?: "")
                    put("disk_cleanup_before_photo", m.diskCleanupBeforePhoto ?: "")
                    put("disk_cleanup_after_photo", m.diskCleanupAfterPhoto ?: "")
                    put("hardware_cleanup_before_photo", m.hardwareCleanupBeforePhoto ?: "")
                    put("hardware_cleanup_after_photo", m.hardwareCleanupAfterPhoto ?: "")
                    put("checking_drive_error_before_photo", m.checkingDriveErrorBeforePhoto ?: "")
                    put("checking_drive_error_after_photo", m.checkingDriveErrorAfterPhoto ?: "")
                    put("scanning_virus_before_photo", m.scanningVirusBeforePhoto ?: "")
                    put("scanning_virus_after_photo", m.scanningVirusAfterPhoto ?: "")
                    put("checking_network_before_photo", m.checkingNetworkBeforePhoto ?: "")
                    put("checking_network_after_photo", m.checkingNetworkAfterPhoto ?: "")
                    put("updating_antivirus_before_photo", m.updatingAntivirusBeforePhoto ?: "")
                    put("updating_antivirus_after_photo", m.updatingAntivirusAfterPhoto ?: "")
                    put("updating_aplikasi_before_photo", m.updatingAplikasiBeforePhoto ?: "")
                    put("updating_aplikasi_after_photo", m.updatingAplikasiAfterPhoto ?: "")
                }
                jsonArray.put(obj)
            }

            val request = buildRequest(supabaseUrl, anonKey, "maintenance_logs")
                .addHeader("Prefer", "return=representation")
                .post(jsonArray.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gagal simpan maintenance logs ke Supabase (HTTP ${response.code}): $body"))
            }

            val returnedArray = JSONArray(body)
            val created = mutableListOf<MaintenanceLog>()
            for (i in 0 until returnedArray.length()) {
                val obj = returnedArray.getJSONObject(i)
                val assignedId = obj.optInt("id", 0)
                val orig = logs.getOrNull(i)
                if (orig != null) {
                    created.add(orig.copy(id = if (assignedId > 0) assignedId else orig.id))
                }
            }
            Result.success(created)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertMaintenanceLogs(supabaseUrl: String, anonKey: String, logs: List<MaintenanceLog>): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val jsonArrayA = JSONArray()
            for (m in logs) {
                val obj = JSONObject().apply {
                    if (m.id > 0) put("id", m.id)
                    put("device_id", m.deviceId)
                    put("device_name", m.deviceName)
                    put("action_taken", m.actionTaken)
                    put("timestamp", m.timestamp)
                    put("health_report", m.healthReport)
                    put("disk_cleanup", m.diskCleanup)
                    put("hardware_cleanup", m.hardwareCleanup)
                    put("checking_drive_error", m.checkingDriveError)
                    put("scanning_virus", m.scanningVirus)
                    put("checking_network", m.checkingNetwork)
                    put("updating_antivirus", m.updatingAntivirus)
                    put("updating_aplikasi", m.updatingAplikasi)
                    put("notes", m.notes ?: "")
                    put("signature_data", m.signatureData ?: "")
                    put("tech_signature_data", m.techSignatureData ?: "")

                    // Cloudflare R2 Public URLs stored in Supabase
                    put("health_report_before_photo", m.healthReportBeforePhoto ?: "")
                    put("health_report_after_photo", m.healthReportAfterPhoto ?: "")
                    put("disk_cleanup_before_photo", m.diskCleanupBeforePhoto ?: "")
                    put("disk_cleanup_after_photo", m.diskCleanupAfterPhoto ?: "")
                    put("hardware_cleanup_before_photo", m.hardwareCleanupBeforePhoto ?: "")
                    put("hardware_cleanup_after_photo", m.hardwareCleanupAfterPhoto ?: "")
                    put("checking_drive_error_before_photo", m.checkingDriveErrorBeforePhoto ?: "")
                    put("checking_drive_error_after_photo", m.checkingDriveErrorAfterPhoto ?: "")
                    put("scanning_virus_before_photo", m.scanningVirusBeforePhoto ?: "")
                    put("scanning_virus_after_photo", m.scanningVirusAfterPhoto ?: "")
                    put("checking_network_before_photo", m.checkingNetworkBeforePhoto ?: "")
                    put("checking_network_after_photo", m.checkingNetworkAfterPhoto ?: "")
                    put("updating_antivirus_before_photo", m.updatingAntivirusBeforePhoto ?: "")
                    put("updating_antivirus_after_photo", m.updatingAntivirusAfterPhoto ?: "")
                    put("updating_aplikasi_before_photo", m.updatingAplikasiBeforePhoto ?: "")
                    put("updating_aplikasi_after_photo", m.updatingAplikasiAfterPhoto ?: "")
                }
                jsonArrayA.put(obj)
            }

            val requestA = buildRequest(supabaseUrl, anonKey, "maintenance_logs?on_conflict=id")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(jsonArrayA.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val responseA = client.newCall(requestA).execute()
            val bodyA = responseA.body?.string() ?: ""
            if (responseA.isSuccessful) return@withContext Result.success(true)

            val requestA2 = buildRequest(supabaseUrl, anonKey, "maintenance_logs")
                .post(jsonArrayA.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
            val responseA2 = client.newCall(requestA2).execute()
            if (responseA2.isSuccessful) return@withContext Result.success(true)

            Result.failure(Exception("Gagal simpan maintenance logs ke Supabase (HTTP ${responseA.code}): $bodyA"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- UPLOADED FILES ---

    suspend fun getUploadedFiles(supabaseUrl: String, anonKey: String): Result<List<UploadedFile>> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(supabaseUrl, anonKey, "uploaded_files?select=*").get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Supabase HTTP ${response.code}: $body"))

            val jsonArray = JSONArray(body)
            val list = mutableListOf<UploadedFile>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    UploadedFile(
                        id = obj.optInt("id"),
                        fileName = obj.optString("file_name", obj.optString("fileName", "")),
                        fileSize = obj.optString("file_size", obj.optString("fileSize", "")),
                        uploadDate = obj.optLong("upload_date", obj.optLong("uploadDate", System.currentTimeMillis())),
                        fileType = obj.optString("file_type", obj.optString("fileType", "")),
                        fileUri = obj.optString("file_uri", obj.optString("fileUri", "")).takeIf { it.isNotBlank() }
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertNewUploadedFiles(supabaseUrl: String, anonKey: String, files: List<UploadedFile>): Result<List<UploadedFile>> = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext Result.success(emptyList())
        try {
            val jsonArray = JSONArray()
            for (f in files) {
                val obj = JSONObject().apply {
                    put("file_name", f.fileName)
                    put("file_size", f.fileSize)
                    put("upload_date", f.uploadDate)
                    put("file_type", f.fileType)
                    put("file_uri", f.fileUri ?: "")
                }
                jsonArray.put(obj)
            }

            val request = buildRequest(supabaseUrl, anonKey, "uploaded_files")
                .addHeader("Prefer", "return=representation")
                .post(jsonArray.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gagal simpan uploaded files ke Supabase (HTTP ${response.code}): $body"))
            }

            val returnedArray = JSONArray(body)
            val created = mutableListOf<UploadedFile>()
            for (i in 0 until returnedArray.length()) {
                val obj = returnedArray.getJSONObject(i)
                val assignedId = obj.optInt("id", 0)
                val orig = files.getOrNull(i)
                if (orig != null) {
                    created.add(orig.copy(id = if (assignedId > 0) assignedId else orig.id))
                }
            }
            Result.success(created)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertUploadedFiles(supabaseUrl: String, anonKey: String, files: List<UploadedFile>): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            for (f in files) {
                val obj = JSONObject().apply {
                    if (f.id > 0) put("id", f.id)
                    put("file_name", f.fileName)
                    put("file_size", f.fileSize)
                    put("upload_date", f.uploadDate)
                    put("file_type", f.fileType)
                    put("file_uri", f.fileUri ?: "")
                }
                jsonArray.put(obj)
            }

            val request = buildRequest(supabaseUrl, anonKey, "uploaded_files?on_conflict=id")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(jsonArray.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) return@withContext Result.success(true)

            val request2 = buildRequest(supabaseUrl, anonKey, "uploaded_files")
                .post(jsonArray.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
            val response2 = client.newCall(request2).execute()
            if (response2.isSuccessful) return@withContext Result.success(true)

            Result.failure(Exception("Gagal simpan uploaded files ke Supabase (HTTP ${response.code}): $body"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMaintenanceLogWithPhotos(
        supabaseUrl: String,
        anonKey: String,
        workerUrl: String,
        log: MaintenanceLog
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val photos = listOfNotNull(
                log.healthReportBeforePhoto, log.healthReportAfterPhoto,
                log.diskCleanupBeforePhoto, log.diskCleanupAfterPhoto,
                log.hardwareCleanupBeforePhoto, log.hardwareCleanupAfterPhoto,
                log.checkingDriveErrorBeforePhoto, log.checkingDriveErrorAfterPhoto,
                log.scanningVirusBeforePhoto, log.scanningVirusAfterPhoto,
                log.checkingNetworkBeforePhoto, log.checkingNetworkAfterPhoto,
                log.updatingAntivirusBeforePhoto, log.updatingAntivirusAfterPhoto,
                log.updatingAplikasiBeforePhoto, log.updatingAplikasiAfterPhoto
            ).filter { it.isNotBlank() && it.startsWith("http") }

            if (workerUrl.isNotBlank()) {
                for (photoUrl in photos) {
                    cloudStorageManager.deleteFromR2(workerUrl, photoUrl)
                }
            }

            if (log.id > 0) {
                val request = buildRequest(supabaseUrl, anonKey, "maintenance_logs?id=eq.${log.id}")
                    .delete()
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Gagal menghapus log dari Supabase"))
                }
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteUploadedFileFromSupabase(supabaseUrl: String, anonKey: String, id: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        if (id <= 0 || supabaseUrl.isBlank() || anonKey.isBlank()) return@withContext Result.success(true)
        try {
            val request = buildRequest(supabaseUrl, anonKey, "uploaded_files?id=eq.$id").delete().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) Result.success(true) else Result.failure(Exception("Gagal hapus uploaded file"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
