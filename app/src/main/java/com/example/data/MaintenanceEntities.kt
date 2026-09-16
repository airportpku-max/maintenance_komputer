package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class Device(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "AIO" or "Laptop"
    val name: String, // Will represent "nama user"
    val brand: String = "", // Tipe & Merek perangkat
    val serialNumber: String, // Will represent "id scan barcode (otomatis)"
    val condition: String = "Baik", // "Baik" or "Trouble"
    val lastMaintenance: Long = System.currentTimeMillis(),
    val description: String = "", // Will represent "lokasi"
    val sn: String = "", // "SN"
    val photoUri: String? = null, // "foto fisik"
    val baFileUri: String? = null, // "file BA"
    val baFileName: String? = null
)

data class Technician(
    val id: Int = 0,
    val name: String = "",
    val role: String = "", // e.g., "Hardware Specialist", "Software Specialist"
    val phone: String = "",
    val status: String = "Aktif", // "Aktif" or "Tidak Aktif"
    val photoUri: String? = null
)

@Entity(tableName = "trouble_tickets")
data class TroubleTicket(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val deviceId: Int,
    val deviceName: String,
    val description: String,
    val reportedBy: String,
    val status: String = "Pending", // "Pending" or "Selesai"
    val timestamp: Long = System.currentTimeMillis(),
    val actionTaken: String = "",
    val duration: String = "",
    val photoBefore: String? = null,
    val photoAfter: String? = null
)

@Entity(tableName = "maintenance_logs")
data class MaintenanceLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val deviceId: Int,
    val deviceName: String,
    val technicianName: String,
    val actionTaken: String,
    val timestamp: Long = System.currentTimeMillis(),
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
    val updatingAplikasiAfterPhoto: String? = null,
    val techSignatureData: String? = null
)

@Entity(tableName = "uploaded_files")
data class UploadedFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val fileSize: String,
    val uploadDate: Long = System.currentTimeMillis(),
    val fileType: String, // "PDF", "XLSX", "PNG"
    val fileUri: String? = null
)
