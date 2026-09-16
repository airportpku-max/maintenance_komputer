package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceDao {
    // Devices
    @Query("SELECT * FROM devices ORDER BY name ASC")
    fun getDevices(): Flow<List<Device>>

    @Query("SELECT * FROM devices ORDER BY name ASC")
    suspend fun getDevicesList(): List<Device>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: Device)

    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun deleteDevice(id: Int)

    // Trouble Tickets
    @Query("SELECT * FROM trouble_tickets ORDER BY timestamp DESC")
    fun getTroubleTickets(): Flow<List<TroubleTicket>>

    @Query("SELECT * FROM trouble_tickets ORDER BY timestamp DESC")
    suspend fun getTroubleTicketsList(): List<TroubleTicket>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTroubleTicket(ticket: TroubleTicket)

    @Query("DELETE FROM trouble_tickets WHERE id = :id")
    suspend fun deleteTroubleTicket(id: Int)

    // Maintenance Logs
    @Query("SELECT * FROM maintenance_logs ORDER BY timestamp DESC")
    fun getMaintenanceLogs(): Flow<List<MaintenanceLog>>

    @Query("SELECT * FROM maintenance_logs ORDER BY timestamp DESC")
    suspend fun getMaintenanceLogsList(): List<MaintenanceLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaintenanceLog(log: MaintenanceLog)

    @Query("DELETE FROM maintenance_logs")
    suspend fun deleteAllMaintenanceLogs()

    @Query("DELETE FROM maintenance_logs WHERE id = :id")
    suspend fun deleteMaintenanceLog(id: Int)

    @Query("DELETE FROM devices")
    suspend fun deleteAllDevices()

    @Query("DELETE FROM trouble_tickets")
    suspend fun deleteAllTroubleTickets()

    @Query("DELETE FROM uploaded_files")
    suspend fun deleteAllUploadedFiles()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<Device>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTroubleTickets(tickets: List<TroubleTicket>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaintenanceLogs(logs: List<MaintenanceLog>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUploadedFiles(files: List<UploadedFile>)

    // Uploaded Files
    @Query("SELECT * FROM uploaded_files ORDER BY uploadDate DESC")
    fun getUploadedFiles(): Flow<List<UploadedFile>>

    @Query("SELECT * FROM uploaded_files ORDER BY uploadDate DESC")
    suspend fun getUploadedFilesList(): List<UploadedFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUploadedFile(file: UploadedFile)

    @Query("DELETE FROM uploaded_files WHERE id = :id")
    suspend fun deleteUploadedFile(id: Int)
}
