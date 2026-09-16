package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class MaintenanceRepository(private val dao: MaintenanceDao) {
    val devices: Flow<List<Device>> = dao.getDevices()
    val technicians: Flow<List<Technician>> = flowOf(emptyList())
    val troubleTickets: Flow<List<TroubleTicket>> = dao.getTroubleTickets()
    val maintenanceLogs: Flow<List<MaintenanceLog>> = dao.getMaintenanceLogs()
    val uploadedFiles: Flow<List<UploadedFile>> = dao.getUploadedFiles()

    suspend fun getDevicesList(): List<Device> = dao.getDevicesList()
    suspend fun getTechniciansList(): List<Technician> = emptyList()
    suspend fun getTroubleTicketsList(): List<TroubleTicket> = dao.getTroubleTicketsList()
    suspend fun getMaintenanceLogsList(): List<MaintenanceLog> = dao.getMaintenanceLogsList()
    suspend fun getUploadedFilesList(): List<UploadedFile> = dao.getUploadedFilesList()

    suspend fun addDevice(device: Device) = dao.insertDevice(device)
    suspend fun removeDevice(id: Int) = dao.deleteDevice(id)

    suspend fun addTechnician(technician: Technician) {}
    suspend fun removeTechnician(id: Int) {}

    suspend fun addTroubleTicket(ticket: TroubleTicket) = dao.insertTroubleTicket(ticket)
    suspend fun removeTroubleTicket(id: Int) = dao.deleteTroubleTicket(id)

    suspend fun addMaintenanceLog(log: MaintenanceLog) = dao.insertMaintenanceLog(log)
    suspend fun removeMaintenanceLog(id: Int) = dao.deleteMaintenanceLog(id)
    suspend fun clearAllMaintenanceLogs() = dao.deleteAllMaintenanceLogs()

    suspend fun addDevices(devices: List<Device>) = dao.insertDevices(devices)
    suspend fun addMaintenanceLogs(logs: List<MaintenanceLog>) = dao.insertMaintenanceLogs(logs)

    suspend fun addUploadedFile(file: UploadedFile) = dao.insertUploadedFile(file)
    suspend fun removeUploadedFile(id: Int) = dao.deleteUploadedFile(id)

    suspend fun overwriteDatabaseFromSync(
        devices: List<Device>,
        troubleTickets: List<TroubleTicket>,
        maintenanceLogs: List<MaintenanceLog>,
        uploadedFiles: List<UploadedFile>
    ) {
        // Clear tables first (technicians table is no longer used)
        dao.deleteAllDevices()
        dao.deleteAllTroubleTickets()
        dao.deleteAllMaintenanceLogs()
        dao.deleteAllUploadedFiles()

        // Batch insert the fetched data
        if (devices.isNotEmpty()) dao.insertDevices(devices)
        if (troubleTickets.isNotEmpty()) dao.insertTroubleTickets(troubleTickets)
        if (maintenanceLogs.isNotEmpty()) dao.insertMaintenanceLogs(maintenanceLogs)
        if (uploadedFiles.isNotEmpty()) dao.insertUploadedFiles(uploadedFiles)
    }
}
