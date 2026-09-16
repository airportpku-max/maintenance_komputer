package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Device
import com.example.data.MaintenanceLog
import com.example.data.TroubleTicket
import com.example.ui.MaintenanceViewModel
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.Green40
import com.example.ui.theme.GreenLight
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: MaintenanceViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var logToDelete by remember { mutableStateOf<MaintenanceLog?>(null) }
    var ticketToDelete by remember { mutableStateOf<TroubleTicket?>(null) }

    val devices by viewModel.devices.collectAsState()
    val logs by viewModel.maintenanceLogs.collectAsState()
    val tickets by viewModel.troubleTickets.collectAsState()

    // Filter states
    var selectedDeviceType by remember { mutableStateOf("Semua") } // "Semua", "Laptop", "AIO"
    val selectedBrand = "Semua"

    val calendar = Calendar.getInstance()
    var selectedMonth by remember { mutableStateOf(calendar.get(Calendar.MONTH)) } // 0-11
    var selectedYear by remember { mutableStateOf(calendar.get(Calendar.YEAR)) } // e.g. 2026
    
    var showMonthMenu by remember { mutableStateOf(false) }
    var showYearMenu by remember { mutableStateOf(false) }

    var reportType by remember { mutableStateOf("Lap Ceklis") } // "Lap Ceklis", "Dokumentasi PM", "Lap Trouble", "Dokumentasi CM"

    // Custom Header Configs
    val airportLocation = "BANDARA SULTAN SYARIF KASIM II PEKANBARU"

    val monthsIndonesian = listOf(
        "JANUARI", "FEBRUARI", "MARET", "APRIL", "MEI", "JUNI",
        "JULI", "AGUSTUS", "SEPTEMBER", "OKTOBER", "NOVEMBER", "DESEMBER"
    )
    val years = listOf(2025, 2026, 2027, 2028)

    // Filtered Logs
    val filteredLogs = remember(logs, devices, selectedDeviceType, selectedBrand, selectedMonth, selectedYear) {
        logs.filter { log ->
            val device = devices.find { it.id == log.deviceId }
            
            // Filter by Device Type
            val matchesType = when (selectedDeviceType) {
                "Semua" -> true
                else -> device?.type?.equals(selectedDeviceType, ignoreCase = true) == true
            }

            // Filter by Brand
            val matchesBrand = when (selectedBrand) {
                "Semua" -> true
                else -> device?.brand?.contains(selectedBrand, ignoreCase = true) == true
            }

            // Filter by Month and Year
            val logCal = Calendar.getInstance().apply { timeInMillis = log.timestamp }
            val matchesMonth = logCal.get(Calendar.MONTH) == selectedMonth
            val matchesYear = logCal.get(Calendar.YEAR) == selectedYear

            matchesType && matchesBrand && matchesMonth && matchesYear
        }
    }

    // Helper to check if a maintenance log has been signed/paraf-ed
    fun isLogParafed(log: MaintenanceLog): Boolean {
        val sig = log.signatureData?.trim()
        if (sig.isNullOrBlank() || sig == "[]" || sig == "null") return false
        val paths = deserializePaths(sig)
        return paths.isNotEmpty() || sig.length > 20
    }

    // Display logs for PM:
    // For "Lap Ceklis": only data that has been paraf-ed (signed by user)
    // For "Dokumentasi PM": deduplicate by date so only 1 log per date is taken
    val displayLogs = remember(filteredLogs, reportType) {
        val sorted = filteredLogs.sortedBy { it.timestamp }
        when (reportType) {
            "Lap Ceklis" -> {
                sorted.filter { isLogParafed(it) }
            }
            "Dokumentasi PM" -> {
                val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sorted.groupBy { sdfDate.format(Date(it.timestamp)) }.map { (_, logsOnDate) ->
                    logsOnDate.firstOrNull { log ->
                        listOf(
                            log.healthReportBeforePhoto, log.healthReportAfterPhoto,
                            log.diskCleanupBeforePhoto, log.diskCleanupAfterPhoto,
                            log.hardwareCleanupBeforePhoto, log.hardwareCleanupAfterPhoto,
                            log.checkingDriveErrorBeforePhoto, log.checkingDriveErrorAfterPhoto,
                            log.scanningVirusBeforePhoto, log.scanningVirusAfterPhoto,
                            log.checkingNetworkBeforePhoto, log.checkingNetworkAfterPhoto,
                            log.updatingAntivirusBeforePhoto, log.updatingAntivirusAfterPhoto,
                            log.updatingAplikasiBeforePhoto, log.updatingAplikasiAfterPhoto
                        ).any { !it.isNullOrBlank() }
                    } ?: logsOnDate.first()
                }.sortedBy { it.timestamp }
            }
            else -> sorted
        }
    }

    // Filtered Trouble Tickets
    val filteredTickets = remember(tickets, devices, selectedDeviceType, selectedMonth, selectedYear) {
        tickets.filter { ticket ->
            val device = devices.find { it.id == ticket.deviceId }
            
            // Filter by Device Type
            val matchesType = when (selectedDeviceType) {
                "Semua" -> true
                else -> device?.type?.equals(selectedDeviceType, ignoreCase = true) == true
            }

            // Filter by Month and Year
            val ticketCal = Calendar.getInstance().apply { timeInMillis = ticket.timestamp }
            val matchesMonth = ticketCal.get(Calendar.MONTH) == selectedMonth
            val matchesYear = ticketCal.get(Calendar.YEAR) == selectedYear

            matchesType && matchesMonth && matchesYear
        }
    }

    if (isGeneratingPdf) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Green40)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sedang membuat PDF...",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Mohon tunggu beberapa saat",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Laporan & Cetak PDF", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green40)
            )
        },
        floatingActionButton = {
            val hasData = if (reportType.contains("Trouble") || reportType.contains("CM")) {
                filteredTickets.isNotEmpty()
            } else {
                displayLogs.isNotEmpty()
            }
            if (hasData) {
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    ExtendedFloatingActionButton(
                        text = {
                            if (isGeneratingPdf) {
                                Text("Memproses PDF...", color = Color.White, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Cetak PDF", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        icon = {
                            if (isGeneratingPdf) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = "Cetak PDF", tint = Color.White)
                            }
                        },
                        onClick = {
                            if (isGeneratingPdf) return@ExtendedFloatingActionButton
                            coroutineScope.launch {
                                isGeneratingPdf = true
                                try {
                                    withContext(Dispatchers.IO) {
                                        when (reportType) {
                                            "Lap Ceklis" -> {
                                                generateAndSavePdf(
                                                    context = context,
                                                    logs = displayLogs,
                                                    devices = devices,
                                                    monthName = monthsIndonesian[selectedMonth],
                                                    year = selectedYear,
                                                    deviceTypeFilter = selectedDeviceType,
                                                    brandFilter = selectedBrand,
                                                    location = airportLocation
                                                )
                                            }
                                            "Dokumentasi PM" -> {
                                                generateAndSaveDokumentasiPdf(
                                                    context = context,
                                                    logs = displayLogs,
                                                    monthName = monthsIndonesian[selectedMonth],
                                                    year = selectedYear
                                                )
                                            }
                                            "Lap Trouble" -> {
                                                generateAndSaveTroublePdf(
                                                    context = context,
                                                    tickets = filteredTickets,
                                                    devices = devices,
                                                    monthName = monthsIndonesian[selectedMonth],
                                                    year = selectedYear,
                                                    deviceTypeFilter = selectedDeviceType,
                                                    location = airportLocation
                                                )
                                            }
                                            "Dokumentasi CM" -> {
                                                generateAndSaveTroubleDokumentasiPdf(
                                                    context = context,
                                                    tickets = filteredTickets,
                                                    monthName = monthsIndonesian[selectedMonth],
                                                    year = selectedYear
                                                )
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    isGeneratingPdf = false
                                }
                            }
                        },
                        containerColor = Green40,
                        modifier = Modifier.testTag("cetak_pdf_fab")
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            // Filter card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Pengaturan Laporan",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Choice chips for Device Type
                    Column {
                        Text(
                            text = "Tipe Perangkat:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Semua", "Laptop", "AIO").forEach { type ->
                                val isSelected = selectedDeviceType == type
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedDeviceType = type },
                                    label = { Text(type) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Green40,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Filters for Month and Year in a row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Month Dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = monthsIndonesian[selectedMonth],
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Bulan") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Pilih Bulan",
                                        modifier = Modifier.clickable { showMonthMenu = !showMonthMenu }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showMonthMenu = !showMonthMenu }
                                    .testTag("report_month_select"),
                                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                            )
                            DropdownMenu(
                                expanded = showMonthMenu,
                                onDismissRequest = { showMonthMenu = false }
                            ) {
                                monthsIndonesian.forEachIndexed { index, name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            selectedMonth = index
                                            showMonthMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Year Dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = selectedYear.toString(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Tahun") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Pilih Tahun",
                                        modifier = Modifier.clickable { showYearMenu = !showYearMenu }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showYearMenu = !showYearMenu }
                                    .testTag("report_year_select"),
                                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                            )
                            DropdownMenu(
                                expanded = showYearMenu,
                                onDismissRequest = { showYearMenu = false }
                            ) {
                                years.forEach { y ->
                                    DropdownMenuItem(
                                        text = { Text(y.toString()) },
                                        onClick = {
                                            selectedYear = y
                                            showYearMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4 Report types grid (2x2 layout)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: PM Reports
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Tab "Lap Ceklis"
                    val isCeklis = reportType == "Lap Ceklis"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCeklis) Green40 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, if (isCeklis) Green40 else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .clickable { reportType = "Lap Ceklis" }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Lap Ceklis",
                            color = if (isCeklis) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Tab "Dokumentasi PM"
                    val isDokPm = reportType == "Dokumentasi PM"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDokPm) Green40 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, if (isDokPm) Green40 else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .clickable { reportType = "Dokumentasi PM" }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Dokumentasi PM",
                            color = if (isDokPm) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Row 2: CM/Trouble Reports
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Tab "Lap Trouble"
                    val isTrouble = reportType == "Lap Trouble"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isTrouble) AccentOrange else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, if (isTrouble) AccentOrange else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .clickable { reportType = "Lap Trouble" }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Lap Trouble",
                            color = if (isTrouble) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Tab "Dokumentasi CM"
                    val isDokCm = reportType == "Dokumentasi CM"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDokCm) AccentOrange else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, if (isDokCm) AccentOrange else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .clickable { reportType = "Dokumentasi CM" }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Dokumentasi CM",
                            color = if (isDokCm) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Results Section
            val isTroubleSelected = reportType.contains("Trouble") || reportType.contains("CM")
            
            Text(
                text = if (isTroubleSelected) {
                    "Pratinjau Hasil CM (${filteredTickets.size})"
                } else {
                    "Pratinjau Hasil PM (${displayLogs.size})"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (isTroubleSelected) {
                if (filteredTickets.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.AssignmentLate,
                                contentDescription = "Tidak ada data",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Belum ada laporan trouble pada periode ini.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "Silakan catat laporan gangguan baru via menu Trouble.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredTickets) { ticket ->
                            val device = devices.find { it.id == ticket.deviceId }
                            TroubleReportPreviewCard(
                                ticket = ticket,
                                device = device,
                                onDelete = { ticketToDelete = ticket }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            } else {
                if (displayLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.AssignmentLate,
                                contentDescription = "Tidak ada data",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (reportType == "Lap Ceklis") "Belum ada data PM yang sudah diparaf pada periode ini." else "Belum ada log maintenance pada periode ini.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = if (reportType == "Lap Ceklis") "Hanya data yang telah diparaf user yang ditampilkan pada Lap Ceklis." else "Silakan catat maintenance log via scanner atau menu utama.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayLogs) { log ->
                            val device = devices.find { it.id == log.deviceId }
                            ReportPreviewCard(
                                log = log,
                                device = device,
                                onDelete = { logToDelete = log }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }

        // Delete Maintenance Log Confirmation Dialog
        if (logToDelete != null) {
            AlertDialog(
                onDismissRequest = { logToDelete = null },
                icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Hapus Log Maintenance") },
                text = { Text("Apakah Anda yakin ingin menghapus data maintenance untuk ${logToDelete?.deviceName ?: "perangkat ini"}? Data yang dihapus tidak dapat dikembalikan.") },
                confirmButton = {
                    Button(
                        onClick = {
                            val targetLog = logToDelete
                            if (targetLog != null) {
                                viewModel.deleteMaintenanceLog(targetLog)
                                Toast.makeText(context, "Log maintenance berhasil dihapus", Toast.LENGTH_SHORT).show()
                            }
                            logToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Hapus", color = Color.White)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { logToDelete = null }) {
                        Text("Batal")
                    }
                }
            )
        }

        // Delete Trouble Ticket Confirmation Dialog
        if (ticketToDelete != null) {
            AlertDialog(
                onDismissRequest = { ticketToDelete = null },
                icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Hapus Laporan Trouble") },
                text = { Text("Apakah Anda yakin ingin menghapus laporan trouble untuk ${ticketToDelete?.deviceName ?: "perangkat ini"}? Data yang dihapus tidak dapat dikembalikan.") },
                confirmButton = {
                    Button(
                        onClick = {
                            val targetTicket = ticketToDelete
                            if (targetTicket != null) {
                                viewModel.deleteTroubleTicket(targetTicket.id)
                                Toast.makeText(context, "Laporan trouble berhasil dihapus", Toast.LENGTH_SHORT).show()
                            }
                            ticketToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Hapus", color = Color.White)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { ticketToDelete = null }) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}

@Composable
fun ReportPreviewCard(
    log: MaintenanceLog,
    device: Device?,
    onDelete: (() -> Unit)? = null
) {
    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(log.timestamp))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Section Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device?.name ?: log.deviceName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${device?.brand ?: "Device"} • SN: ${device?.sn ?: ""}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Green40.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = dateStr,
                            color = Green40,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Hapus Log",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 10.dp))

            // Checklist Preview Summary Chips
            Text("Tindakan Checklist:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val checklistItems = mutableListOf<String>()
                if (log.healthReport) checklistItems.add("Health")
                if (log.diskCleanup) checklistItems.add("Disk")
                if (log.hardwareCleanup) checklistItems.add("Hardware")
                if (log.checkingDriveError) checklistItems.add("Drive")
                if (log.scanningVirus) checklistItems.add("Virus")
                if (log.checkingNetwork) checklistItems.add("Net")
                if (log.updatingAntivirus) checklistItems.add("AV")
                if (log.updatingAplikasi) checklistItems.add("App")

                if (checklistItems.isEmpty()) {
                    Text("Tidak ada checklist yang dicentang", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            checklistItems.take(5).forEach { item ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(item, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            if (checklistItems.size > 5) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("+${checklistItems.size - 5}", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            val photoCount = listOf(
                log.healthReportBeforePhoto, log.healthReportAfterPhoto,
                log.diskCleanupBeforePhoto, log.diskCleanupAfterPhoto,
                log.hardwareCleanupBeforePhoto, log.hardwareCleanupAfterPhoto,
                log.checkingDriveErrorBeforePhoto, log.checkingDriveErrorAfterPhoto,
                log.scanningVirusBeforePhoto, log.scanningVirusAfterPhoto,
                log.checkingNetworkBeforePhoto, log.checkingNetworkAfterPhoto,
                log.updatingAntivirusBeforePhoto, log.updatingAntivirusAfterPhoto,
                log.updatingAplikasiBeforePhoto, log.updatingAplikasiAfterPhoto
            ).count { !it.isNullOrBlank() }

            if (photoCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Dokumentasi Foto",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$photoCount Foto Dokumentasi Tersedia",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Signature preview row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Paraf Petugas Preview
                if (!log.techSignatureData.isNullOrBlank()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Paraf Petugas", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Box(
                            modifier = Modifier
                                .size(70.dp, 35.dp)
                                .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .background(Color.White)
                        ) {
                            val paths = remember(log.techSignatureData) { deserializePaths(log.techSignatureData) }
                            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                                if (paths.isNotEmpty()) {
                                    var minX = Float.MAX_VALUE
                                    var maxX = Float.MIN_VALUE
                                    var minY = Float.MAX_VALUE
                                    var maxY = Float.MIN_VALUE
                                    for (path in paths) {
                                        for (pt in path) {
                                            if (pt.x < minX) minX = pt.x
                                            if (pt.x > maxX) maxX = pt.x
                                            if (pt.y < minY) minY = pt.y
                                            if (pt.y > maxY) maxY = pt.y
                                        }
                                    }
                                    val sigW = maxX - minX
                                    val sigH = maxY - minY
                                    if (sigW > 0 && sigH > 0) {
                                        val scale = minOf((size.width * 0.8f) / sigW, (size.height * 0.8f) / sigH)
                                        val dx = (size.width - sigW * scale) / 2f
                                        val dy = (size.height - sigH * scale) / 2f
                                        
                                        for (path in paths) {
                                            if (path.isEmpty()) continue
                                            for (i in 0 until path.size - 1) {
                                                val p1 = path[i]
                                                val p2 = path[i + 1]
                                                drawLine(
                                                    color = Color.Black,
                                                    start = Offset(dx + (p1.x - minX) * scale, dy + (p1.y - minY) * scale),
                                                    end = Offset(dx + (p2.x - minX) * scale, dy + (p2.y - minY) * scale),
                                                    strokeWidth = 1.5f
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Small Signature Preview
                if (!log.signatureData.isNullOrBlank()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Paraf User", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Box(
                            modifier = Modifier
                                .size(70.dp, 35.dp)
                                .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .background(Color.White)
                        ) {
                            val paths = remember(log.signatureData) { deserializePaths(log.signatureData) }
                            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                                if (paths.isNotEmpty()) {
                                    var minX = Float.MAX_VALUE
                                    var maxX = Float.MIN_VALUE
                                    var minY = Float.MAX_VALUE
                                    var maxY = Float.MIN_VALUE
                                    for (path in paths) {
                                        for (pt in path) {
                                            if (pt.x < minX) minX = pt.x
                                            if (pt.x > maxX) maxX = pt.x
                                            if (pt.y < minY) minY = pt.y
                                            if (pt.y > maxY) maxY = pt.y
                                        }
                                    }
                                    val sigW = maxX - minX
                                    val sigH = maxY - minY
                                    if (sigW > 0 && sigH > 0) {
                                        val scale = minOf((size.width * 0.8f) / sigW, (size.height * 0.8f) / sigH)
                                        val dx = (size.width - sigW * scale) / 2f
                                        val dy = (size.height - sigH * scale) / 2f
                                        
                                        for (path in paths) {
                                            if (path.isEmpty()) continue
                                            for (i in 0 until path.size - 1) {
                                                val p1 = path[i]
                                                val p2 = path[i + 1]
                                                drawLine(
                                                    color = Color.Black,
                                                    start = Offset(dx + (p1.x - minX) * scale, dy + (p1.y - minY) * scale),
                                                    end = Offset(dx + (p2.x - minX) * scale, dy + (p2.y - minY) * scale),
                                                    strokeWidth = 1.5f
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!log.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Keterangan: ${log.notes}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

private data class DocItem(
    val name: String,
    val beforeUri: String?,
    val afterUri: String?
)

private fun getFreshInputStream(context: Context, uriString: String): java.io.InputStream? {
    try {
        if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
            val url = java.net.URL(uriString)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            return conn.inputStream
        }
        val uri = Uri.parse(uriString)
        
        // 0. Direct check for raw file path
        if (uriString.startsWith("/") || uri.scheme.isNullOrBlank()) {
            val file = java.io.File(uriString)
            if (file.exists()) {
                return java.io.FileInputStream(file)
            }
        }
        
        // 1. Try file scheme directly
        if (uri.scheme == "file" || uriString.startsWith("file://")) {
            val path = uri.path
            if (path != null) {
                val file = java.io.File(path)
                if (file.exists()) {
                    return java.io.FileInputStream(file)
                }
            }
        }
        
        // 2. Try to load directly from local file cache if it is our file provider URI
        val isOurFileProvider = uri.authority == "${context.packageName}.fileprovider"
        if (isOurFileProvider) {
            val lastPathSegment = uri.lastPathSegment
            if (lastPathSegment != null) {
                val cachePath = java.io.File(context.cacheDir, "images")
                val localFile = java.io.File(cachePath, lastPathSegment)
                if (localFile.exists()) {
                    return java.io.FileInputStream(localFile)
                } else {
                    // check filesDir/maintenance_photos too
                    val maintPath = java.io.File(context.filesDir, "maintenance_photos")
                    val maintFile = java.io.File(maintPath, lastPathSegment)
                    if (maintFile.exists()) {
                        return java.io.FileInputStream(maintFile)
                    }
                }
            }
        }
        
        // 3. Fallback to ContentResolver if not loaded yet
        return context.contentResolver.openInputStream(uri)
    } catch (t: Throwable) {
        t.printStackTrace()
        return null
    }
}

private fun loadUriToBitmap(context: Context, uriString: String?): Bitmap? {
    if (uriString.isNullOrBlank() || uriString == "null" || uriString == "undefined") return null
    try {
        val httpBytes: ByteArray? = if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
            try {
                val url = java.net.URL(uriString)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.inputStream.use { it.readBytes() }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else null

        // Step 1: Decode dimensions only to save memory
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        val is1 = if (httpBytes != null) java.io.ByteArrayInputStream(httpBytes) else getFreshInputStream(context, uriString) ?: return null
        try {
            BitmapFactory.decodeStream(is1, null, options)
        } finally {
            try { is1.close() } catch (e: Exception) {}
        }
        
        // Target max dimension of 800px (sufficient for high-quality printed PDF cells)
        val maxDimension = 800
        var sampleSize = 1
        val height = options.outHeight
        val width = options.outWidth
        if (height > maxDimension || width > maxDimension) {
            while (height / sampleSize > maxDimension || width / sampleSize > maxDimension) {
                sampleSize *= 2
            }
        }
        
        // Step 2: Decode scaled bitmap
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val is2 = if (httpBytes != null) java.io.ByteArrayInputStream(httpBytes) else getFreshInputStream(context, uriString) ?: return null
        val bitmap = try {
            BitmapFactory.decodeStream(is2, null, decodeOptions)
        } finally {
            try { is2.close() } catch (e: Exception) {}
        }
        
        return bitmap
    } catch (t: Throwable) {
        t.printStackTrace()
        return null
    }
}

private fun drawBitmapInPdfCell(
    context: Context,
    canvas: Canvas,
    photoUriStr: String?,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    placeholderPaint: Paint,
    textPaint: Paint,
    cachedBitmap: Bitmap? = null
) {
    val margin = 5f
    val cellW = (right - left) - (2 * margin)
    val cellH = (bottom - top) - (2 * margin)
    
    val bitmap = cachedBitmap ?: loadUriToBitmap(context, photoUriStr)
    if (bitmap != null) {
        try {
            if (bitmap.width <= 0 || bitmap.height <= 0) {
                drawPlaceholderBox(canvas, left, right, top, bottom, placeholderPaint, textPaint)
                if (cachedBitmap == null) bitmap.recycle()
                return
            }
            val scaleX = cellW / bitmap.width
            val scaleY = cellH / bitmap.height
            val scale = maxOf(scaleX, scaleY)
            
            val srcW = cellW / scale
            val srcH = cellH / scale
            val srcLeft = (bitmap.width - srcW) / 2f
            val srcTop = (bitmap.height - srcH) / 2f
            
            val srcLeftInt = maxOf(0, srcLeft.toInt())
            val srcTopInt = maxOf(0, srcTop.toInt())
            val srcRightInt = minOf(bitmap.width, (srcLeft + srcW).toInt())
            val srcBottomInt = minOf(bitmap.height, (srcTop + srcH).toInt())
            
            val srcRect = android.graphics.Rect(srcLeftInt, srcTopInt, srcRightInt, srcBottomInt)
            val destRect = android.graphics.RectF(
                left + margin,
                top + margin,
                right - margin,
                bottom - margin
            )
            
            canvas.drawBitmap(bitmap, srcRect, destRect, null)
            if (cachedBitmap == null) bitmap.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
            drawPlaceholderBox(canvas, left, right, top, bottom, placeholderPaint, textPaint)
            if (cachedBitmap == null) {
                try { bitmap.recycle() } catch (ex: Exception) {}
            }
        }
    } else {
        drawPlaceholderBox(canvas, left, right, top, bottom, placeholderPaint, textPaint)
    }
}

private fun drawPlaceholderBox(
    canvas: Canvas,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    placeholderPaint: Paint,
    textPaint: Paint
) {
    val margin = 5f
    canvas.drawRect(left + margin, top + margin, right - margin, bottom - margin, placeholderPaint)
    
    val text = "Tidak Ada Foto"
    val textW = textPaint.measureText(text)
    val fontMetrics = textPaint.fontMetrics
    val textH = fontMetrics.bottom - fontMetrics.top
    val textX = left + (right - left - textW) / 2f
    val textY = top + (bottom - top) / 2f - fontMetrics.top - textH / 2f
    canvas.drawText(text, textX, textY, textPaint)
}

private suspend fun generateAndSaveDokumentasiPdf(
    context: Context,
    logs: List<MaintenanceLog>,
    monthName: String,
    year: Int
) {
    try {
        val pdfDocument = PdfDocument()
        
        // Define standard A4 Portrait size
        val pageW = 595
        val pageH = 842
        
        // Common Paints
        val titlePaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 9.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 9f
            isAntiAlias = true
        }
        val gridPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val placeholderBgPaint = Paint().apply {
            color = android.graphics.Color.rgb(240, 240, 240)
            style = Paint.Style.FILL
        }
        val placeholderTextPaint = Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 8.5f
            isAntiAlias = true
        }

        // We will collect all sheets of documentation. Each log gets its own pages.
        data class PageToWrite(
            val log: MaintenanceLog,
            val deviceDetail: String,
            val pageNumInLog: Int,
            val totalPagesInLog: Int,
            val items: List<DocItem>
        )
        
        val pagesList = mutableListOf<PageToWrite>()
        
        // Deduplicate logs by date so only 1 log is included per date for photo documentation, sorted ascending by timestamp
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val deduplicatedLogs = logs.sortedBy { it.timestamp }.groupBy { dateFmt.format(Date(it.timestamp)) }.map { (_, logsOnDate) ->
            logsOnDate.firstOrNull { log ->
                listOf(
                    log.healthReportBeforePhoto, log.healthReportAfterPhoto,
                    log.diskCleanupBeforePhoto, log.diskCleanupAfterPhoto,
                    log.hardwareCleanupBeforePhoto, log.hardwareCleanupAfterPhoto,
                    log.checkingDriveErrorBeforePhoto, log.checkingDriveErrorAfterPhoto,
                    log.scanningVirusBeforePhoto, log.scanningVirusAfterPhoto,
                    log.checkingNetworkBeforePhoto, log.checkingNetworkAfterPhoto,
                    log.updatingAntivirusBeforePhoto, log.updatingAntivirusAfterPhoto,
                    log.updatingAplikasiBeforePhoto, log.updatingAplikasiAfterPhoto
                ).any { !it.isNullOrBlank() }
            } ?: logsOnDate.first()
        }.sortedBy { it.timestamp }

        for (log in deduplicatedLogs) {
            val items = mutableListOf<DocItem>()
            if (log.healthReport) items.add(DocItem("Health Report", log.healthReportBeforePhoto, log.healthReportAfterPhoto))
            if (log.diskCleanup) items.add(DocItem("Disk CleanUp", log.diskCleanupBeforePhoto, log.diskCleanupAfterPhoto))
            if (log.hardwareCleanup) items.add(DocItem("Hardware CleanUp", log.hardwareCleanupBeforePhoto, log.hardwareCleanupAfterPhoto))
            if (log.checkingDriveError) items.add(DocItem("Checking Drive Error", log.checkingDriveErrorBeforePhoto, log.checkingDriveErrorAfterPhoto))
            if (log.scanningVirus) items.add(DocItem("Scanning Virus", log.scanningVirusBeforePhoto, log.scanningVirusAfterPhoto))
            if (log.checkingNetwork) items.add(DocItem("Checking Network", log.checkingNetworkBeforePhoto, log.checkingNetworkAfterPhoto))
            if (log.updatingAntivirus) items.add(DocItem("Updating Antivirus Databases", log.updatingAntivirusBeforePhoto, log.updatingAntivirusAfterPhoto))
            if (log.updatingAplikasi) items.add(DocItem("Updating Aplikasi", log.updatingAplikasiBeforePhoto, log.updatingAplikasiAfterPhoto))
            
            // If the log is empty, let's still add a single blank/empty page to document that maintenance was done
            if (items.isEmpty()) {
                pagesList.add(PageToWrite(log, "Perangkat: ${log.deviceName}", 1, 1, emptyList()))
            } else {
                val chunks = items.chunked(4)
                chunks.forEachIndexed { idx, chunk ->
                    pagesList.add(PageToWrite(log, "Perangkat: ${log.deviceName}", idx + 1, chunks.size, chunk))
                }
            }
        }
        
        if (pagesList.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Tidak ada data log untuk dibuat dokumentasi", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Collect all distinct photo URIs to pre-fetch in parallel
        val uniquePhotoUris = pagesList
            .flatMap { page -> page.items.flatMap { item -> listOfNotNull(item.beforeUri, item.afterUri) } }
            .filter { !it.isNullOrBlank() && it != "null" && it != "undefined" }
            .distinct()

        val bitmapCache: Map<String, Bitmap?> = coroutineScope {
            uniquePhotoUris.map { uri ->
                async(Dispatchers.IO) {
                    uri to loadUriToBitmap(context, uri)
                }
            }.awaitAll().toMap()
        }

        try {
            for (pageIndex in pagesList.indices) {
                val pageData = pagesList[pageIndex]
                val log = pageData.log
                
                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageIndex + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                
                val tableYStart: Float
                
                if (pageIndex == 0) {
                    // 1. Draw Title (only on page 1)
                    val titleText = "LAMPIRAN FOTO PERAWATAN LAPTOP & PC AIO"
                    val titleW = titlePaint.measureText(titleText)
                    canvas.drawText(titleText, (pageW - titleW) / 2f, 50f, titlePaint)
                    
                    // 2. Draw Subtitle Date
                    val dateText = "Tanggal : ${SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date(log.timestamp))}"
                    val dateW = subtitlePaint.measureText(dateText)
                    canvas.drawText(dateText, (pageW - dateW) / 2f, 70f, subtitlePaint)
                    
                    tableYStart = 95f
                } else if (pageData.pageNumInLog == 1) {
                    // Subsequent pages for a new date: show Date header only
                    val dateText = "Tanggal : ${SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date(log.timestamp))}"
                    val dateW = subtitlePaint.measureText(dateText)
                    canvas.drawText(dateText, (pageW - dateW) / 2f, 40f, subtitlePaint)
                    
                    tableYStart = 62f
                } else {
                    // Continuation page of same date: omit Title and Date
                    tableYStart = 35f
                }
                
                // 4. Draw Table Header
                val tableYHeaderHeight = 25f
                val headerBot = tableYStart + tableYHeaderHeight
                
                val col1Left = 40f
                val col1Right = 215f
                val col2Left = 215f
                val col2Right = 385f
                val col3Left = 385f
                val col3Right = 555f
                
                // Draw Headers Background or Border
                canvas.drawRect(col1Left, tableYStart, col3Right, headerBot, gridPaint)
                canvas.drawLine(col2Left, tableYStart, col2Left, headerBot, gridPaint)
                canvas.drawLine(col3Left, tableYStart, col3Left, headerBot, gridPaint)
                
                // Header Text
                val hText1 = "JENIS PEMERIKSAAN"
                val hText2 = "SEBELUM"
                val hText3 = "SESUDAH"
                
                // Center headers
                val h1W = labelPaint.measureText(hText1)
                val h2W = labelPaint.measureText(hText2)
                val h3W = labelPaint.measureText(hText3)
                
                canvas.drawText(hText1, col1Left + (col1Right - col1Left - h1W) / 2f, tableYStart + 16f, labelPaint)
                canvas.drawText(hText2, col2Left + (col2Right - col2Left - h2W) / 2f, tableYStart + 16f, labelPaint)
                canvas.drawText(hText3, col3Left + (col3Right - col3Left - h3W) / 2f, tableYStart + 16f, labelPaint)
                
                // 5. Draw Row content
                val rowHeight = 135f
                var currentY = headerBot
                
                if (pageData.items.isEmpty()) {
                    // Show Empty checklist row
                    val emptyRowBot = currentY + rowHeight
                    canvas.drawRect(col1Left, currentY, col3Right, emptyRowBot, gridPaint)
                    canvas.drawLine(col2Left, currentY, col2Left, emptyRowBot, gridPaint)
                    canvas.drawLine(col3Left, currentY, col3Left, emptyRowBot, gridPaint)
                    
                    val noMaintText = "Tidak ada item pemeriksaan yang aktif"
                    val noMaintW = textPaint.measureText(noMaintText)
                    canvas.drawText(noMaintText, col1Left + (col1Right - col1Left - noMaintW) / 2f, currentY + rowHeight / 2f, textPaint)
                    
                    // Draw empty placeholders
                    drawPlaceholderBox(canvas, col2Left, col2Right, currentY, emptyRowBot, placeholderBgPaint, placeholderTextPaint)
                    drawPlaceholderBox(canvas, col3Left, col3Right, currentY, emptyRowBot, placeholderBgPaint, placeholderTextPaint)
                } else {
                    for (item in pageData.items) {
                        val nextY = currentY + rowHeight
                        canvas.drawRect(col1Left, currentY, col3Right, nextY, gridPaint)
                        canvas.drawLine(col2Left, currentY, col2Left, nextY, gridPaint)
                        canvas.drawLine(col3Left, currentY, col3Left, nextY, gridPaint)
                        
                        // Draw Item Name Centered Vertically and Horizontally in Col 1
                        val itemW = textPaint.measureText(item.name)
                        val fontMetrics = textPaint.fontMetrics
                        val itemH = fontMetrics.bottom - fontMetrics.top
                        val itemX = col1Left + (col1Right - col1Left - itemW) / 2f
                        val itemY = currentY + rowHeight / 2f - fontMetrics.top - itemH / 2f
                        canvas.drawText(item.name, itemX, itemY, textPaint)
                        
                        // Draw Before Photo
                        drawBitmapInPdfCell(context, canvas, item.beforeUri, col2Left, col2Right, currentY, nextY, placeholderBgPaint, placeholderTextPaint, item.beforeUri?.let { bitmapCache[it] })
                        
                        // Draw After Photo
                        drawBitmapInPdfCell(context, canvas, item.afterUri, col3Left, col3Right, currentY, nextY, placeholderBgPaint, placeholderTextPaint, item.afterUri?.let { bitmapCache[it] })
                        
                        currentY = nextY
                    }
                }
                
                pdfDocument.finishPage(page)
            }
            
            // 7. Save and Notify
            val displayFileName = "Dokumentasi_Perawatan_${monthName.substring(0, minOf(3, monthName.length))}_$year.pdf"
            val savedFile = savePdfBytesToStorage(context, pdfDocument, displayFileName)
            if (savedFile != null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Dokumentasi PDF berhasil disimpan di Downloads/ atau Dokumen!", Toast.LENGTH_LONG).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal menyimpan Dokumentasi PDF", Toast.LENGTH_SHORT).show()
                }
            }
        } finally {
            bitmapCache.values.forEach { bmp ->
                try {
                    if (bmp != null && !bmp.isRecycled) {
                        bmp.recycle()
                    }
                } catch (e: Exception) {}
            }
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Terjadi kesalahan saat mencetak Dokumentasi: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    } finally {
        System.gc()
    }
}

// Core PDF Generation function using built-in android.graphics.pdf.PdfDocument
private suspend fun generateAndSavePdf(
    context: Context,
    logs: List<MaintenanceLog>,
    devices: List<Device>,
    monthName: String,
    year: Int,
    deviceTypeFilter: String,
    brandFilter: String,
    location: String
) {
    try {
        val pdfDocument = PdfDocument()
        
        // Document Dimensions (A4 Landscape is exactly 842 x 595)
        val pageW = 842
        val pageH = 595
        val marginL = 30f
        val marginR = 812f
        val tableW = 782f
        
        // Pagination logic (16 items per page so 1 page fits 15-20 rows)
        val itemsPerPage = 16
        
        data class LogGroup(val typeLabel: String, val groupLogs: List<MaintenanceLog>)
        val groups = mutableListOf<LogGroup>()
        
        if (deviceTypeFilter == "Semua") {
            val pcGroup = logs.filter { log ->
                val dev = devices.find { it.id == log.deviceId }
                dev == null || dev.type.equals("AIO", ignoreCase = true) || !dev.type.equals("Laptop", ignoreCase = true)
            }
            val laptopGroup = logs.filter { log ->
                val dev = devices.find { it.id == log.deviceId }
                dev?.type?.equals("Laptop", ignoreCase = true) == true
            }
            if (pcGroup.isNotEmpty()) {
                groups.add(LogGroup("PC / AIO", pcGroup))
            }
            if (laptopGroup.isNotEmpty()) {
                groups.add(LogGroup("LAPTOP", laptopGroup))
            }
        } else {
            val label = if (deviceTypeFilter.equals("AIO", ignoreCase = true)) "PC / AIO" else deviceTypeFilter.uppercase()
            groups.add(LogGroup(label, logs))
        }

        var globalPageIndex = 0
        
        // Define Column Widths
        val colNoW = 20f
        val colLokW = 70f
        val colUsrW = 70f
        val colSnW = 60f
        val colMerkW = 50f
        val colMaintW = 240f // total width of 8 sub-columns, each 30f
        val colTglW = 60f
        val colPetW = 60f   // Paraf Petugas
        val colSigW = 60f   // Paraf User
        val colKetW = 92f
        
        // Sub-column widths
        val subMaintW = 30f

        // Paints for Canvas Drawing
        val borderPaint = Paint().apply {
            color = android.graphics.Color.BLUE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        
        val gridPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        
        val headerBgPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#CFE2F3")
            style = Paint.Style.FILL
        }

        val textHeaderPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 6f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val textBodyPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 6f
            isAntiAlias = true
        }

        val textTitlePaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 9.5f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val checkboxPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }

        val checkSignPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            isAntiAlias = true
        }

        val signatureDrawPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            isAntiAlias = true
        }

        // Draw multiple pages
        for (group in groups) {
            val chunks = group.groupLogs.chunked(itemsPerPage)
            for (pageIndex in chunks.indices) {
                val pageLogs = chunks[pageIndex]
                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, globalPageIndex + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                globalPageIndex++

                // 1. Draw Page Outer Border
                canvas.drawRect(20f, 20f, pageW - 20f, pageH - 20f, borderPaint)

                // 2. Draw Title Text Section
                val filterBrandStr = if (brandFilter == "Semua") "DELL" else brandFilter.uppercase()
                val titleText = "FORM CHECKLIST MAINTENANCE ${group.typeLabel} $filterBrandStr $location BULAN $monthName TAHUN $year"
                
                // Measure title length to center it
                val titleX = (pageW - textTitlePaint.measureText(titleText)) / 2f
                canvas.drawText(titleText, titleX, 40f, textTitlePaint)

                // 3. Draw Table Header Boundaries (Top = 55, Bottom Row 1 = 72, Bottom Row 2 = 88)
                val tTop = 55f
                val tMid = 72f
                val tBot = 88f
            
            // Draw Header Background Fill
            canvas.drawRect(marginL, tTop, marginR, tBot, headerBgPaint)

            // Draw Column Borders (Draw rectangles for cells to automatically build grid)
            // No.
            canvas.drawRect(marginL, tTop, marginL + colNoW, tBot, gridPaint)
            drawCellTextInPdf(canvas, "No.", marginL, marginL + colNoW, tTop, tBot, textHeaderPaint)

            // Lokasi
            val colLokL = marginL + colNoW
            canvas.drawRect(colLokL, tTop, colLokL + colLokW, tBot, gridPaint)
            drawCellTextInPdf(canvas, "Lokasi", colLokL, colLokL + colLokW, tTop, tBot, textHeaderPaint)

            // User
            val colUsrL = colLokL + colLokW
            canvas.drawRect(colUsrL, tTop, colUsrL + colUsrW, tBot, gridPaint)
            drawCellTextInPdf(canvas, "User", colUsrL, colUsrL + colUsrW, tTop, tBot, textHeaderPaint)

            // S/N
            val colSnL = colUsrL + colUsrW
            canvas.drawRect(colSnL, tTop, colSnL + colSnW, tBot, gridPaint)
            drawCellTextInPdf(canvas, "S/N", colSnL, colSnL + colSnW, tTop, tBot, textHeaderPaint)

            // Merk
            val colMerkL = colSnL + colSnW
            canvas.drawRect(colMerkL, tTop, colMerkL + colMerkW, tBot, gridPaint)
            drawCellTextInPdf(canvas, "Merk", colMerkL, colMerkL + colMerkW, tTop, tBot, textHeaderPaint)

            // Data Maintenance (Spans 8 Columns)
            val colMaintL = colMerkL + colMerkW
            canvas.drawRect(colMaintL, tTop, colMaintL + colMaintW, tMid, gridPaint)
            drawCellTextInPdf(canvas, "Data Maintenance", colMaintL, colMaintL + colMaintW, tTop, tMid, textHeaderPaint)

            // Data Maintenance Sub-columns
            val dataMaintHeaders = listOf(
                "Health\nReport", "Disk\nCleanup", "Hardware\nCleanup", 
                "Checking\nDrive\nError", "Scanning\nVirus", "Checking\nNetwork", 
                "Updating\nAntivirus", "Updating\nApplicat."
            )
            for (i in 0 until 8) {
                val subL = colMaintL + i * subMaintW
                canvas.drawRect(subL, tMid, subL + subMaintW, tBot, gridPaint)
                drawCellTextInPdf(canvas, dataMaintHeaders[i], subL, subL + subMaintW, tMid, tBot, textHeaderPaint)
            }

            // Tanggal
            val colTglL = colMaintL + colMaintW
            canvas.drawRect(colTglL, tTop, colTglL + colTglW, tBot, gridPaint)
            drawCellTextInPdf(canvas, "Tanggal", colTglL, colTglL + colTglW, tTop, tBot, textHeaderPaint)

            // Paraf Petugas
            val colPetL = colTglL + colTglW
            canvas.drawRect(colPetL, tTop, colPetL + colPetW, tBot, gridPaint)
            drawCellTextInPdf(canvas, "Paraf\nPetugas", colPetL, colPetL + colPetW, tTop, tBot, textHeaderPaint)

            // Paraf User
            val colSigL = colPetL + colPetW
            canvas.drawRect(colSigL, tTop, colSigL + colSigW, tBot, gridPaint)
            drawCellTextInPdf(canvas, "Paraf\nUser", colSigL, colSigL + colSigW, tTop, tBot, textHeaderPaint)

            // Keterangan
            val colKetL = colSigL + colSigW
            canvas.drawRect(colKetL, tTop, colKetL + colKetW, tBot, gridPaint)
            drawCellTextInPdf(canvas, "Keterangan", colKetL, colKetL + colKetW, tTop, tBot, textHeaderPaint)

            // 4. Draw Rows of Data
            val rowH = 18f
            for (i in pageLogs.indices) {
                val log = pageLogs[i]
                val device = devices.find { it.id == log.deviceId }
                val rowTop = tBot + i * rowH
                val rowBot = rowTop + rowH

                // Columns Drawing
                // No.
                canvas.drawRect(marginL, rowTop, marginL + colNoW, rowBot, gridPaint)
                val globalNo = (pageIndex * itemsPerPage) + i + 1
                drawCellTextInPdf(canvas, globalNo.toString(), marginL, marginL + colNoW, rowTop, rowBot, textBodyPaint)

                // Lokasi
                canvas.drawRect(colLokL, rowTop, colLokL + colLokW, rowBot, gridPaint)
                drawCellTextInPdf(canvas, device?.description ?: "-", colLokL, colLokL + colLokW, rowTop, rowBot, textBodyPaint)

                // User
                canvas.drawRect(colUsrL, rowTop, colUsrL + colUsrW, rowBot, gridPaint)
                drawCellTextInPdf(canvas, device?.name ?: log.deviceName, colUsrL, colUsrL + colUsrW, rowTop, rowBot, textBodyPaint)

                // S/N
                canvas.drawRect(colSnL, rowTop, colSnL + colSnW, rowBot, gridPaint)
                drawCellTextInPdf(canvas, device?.sn ?: "", colSnL, colSnL + colSnW, rowTop, rowBot, textBodyPaint)

                // Merk
                canvas.drawRect(colMerkL, rowTop, colMerkL + colMerkW, rowBot, gridPaint)
                drawCellTextInPdf(canvas, device?.brand ?: "", colMerkL, colMerkL + colMerkW, rowTop, rowBot, textBodyPaint)

                // Data Maintenance Checklist (8 Columns)
                val checkStates = listOf(
                    log.healthReport, log.diskCleanup, log.hardwareCleanup, 
                    log.checkingDriveError, log.scanningVirus, log.checkingNetwork,
                    log.updatingAntivirus, log.updatingAplikasi
                )
                for (c in 0 until 8) {
                    val subL = colMaintL + c * subMaintW
                    canvas.drawRect(subL, rowTop, subL + subMaintW, rowBot, gridPaint)
                    drawCheckboxInPdf(canvas, checkStates[c], subL, subL + subMaintW, rowTop, rowBot, checkboxPaint, checkSignPaint)
                }

                // Tanggal
                canvas.drawRect(colTglL, rowTop, colTglL + colTglW, rowBot, gridPaint)
                val rowDateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(log.timestamp))
                drawCellTextInPdf(canvas, rowDateStr, colTglL, colTglL + colTglW, rowTop, rowBot, textBodyPaint)

                // Paraf Petugas
                canvas.drawRect(colPetL, rowTop, colPetL + colPetW, rowBot, gridPaint)
                if (!log.techSignatureData.isNullOrBlank()) {
                    drawSignatureInPdf(canvas, log.techSignatureData, colPetL, colPetL + colPetW, rowTop, rowBot, signatureDrawPaint)
                }

                // Paraf User
                canvas.drawRect(colSigL, rowTop, colSigL + colSigW, rowBot, gridPaint)
                if (!log.signatureData.isNullOrBlank()) {
                    drawSignatureInPdf(canvas, log.signatureData, colSigL, colSigL + colSigW, rowTop, rowBot, signatureDrawPaint)
                }

                // Keterangan
                canvas.drawRect(colKetL, rowTop, colKetL + colKetW, rowBot, gridPaint)
                drawCellTextInPdf(canvas, log.notes ?: "", colKetL, colKetL + colKetW, rowTop, rowBot, textBodyPaint)
            }

            if (pageIndex == chunks.size - 1) {
                // Drawing the Signature Block at the bottom of the last page
                val footerStartY = 400f
                
                // Paint for Titles (AIRPORT TECHNOLOGY ENGINEER, etc.)
                val sigTitlePaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 7.5f
                    isFakeBoldText = true
                    isAntiAlias = true
                }

                // Paint for Names (ARIFATUL AZHAR, EKO ARIF RAHMANTO)
                val sigNamePaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 7.5f
                    isFakeBoldText = true
                    isUnderlineText = true
                    isAntiAlias = true
                }

                // Paint for Body/Regular Text (Mengetahui,, Pelaksana Tugas names)
                val sigBodyPaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 7.5f
                    isAntiAlias = true
                }

                // Paint for the signature/underline line for technicians
                val sigLinePaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                }

                // --- 1. Left Column: Pengawas, AIRPORT TECHNOLOGY ENGINEER ---
                val col1CenterX = marginL + 150f // Center point for Left Column
                val textSuper = "Pengawas,"
                val title1 = "AIRPORT TECHNOLOGY ENGINEER"
                val name1 = "ARIFATUL AZHAR"
                val tsW = sigBodyPaint.measureText(textSuper)
                val t1W = sigTitlePaint.measureText(title1)
                val n1W = sigNamePaint.measureText(name1)
                canvas.drawText(textSuper, col1CenterX - (tsW / 2), footerStartY, sigBodyPaint)
                canvas.drawText(title1, col1CenterX - (t1W / 2), footerStartY + 15f, sigTitlePaint)
                canvas.drawText(name1, col1CenterX - (n1W / 2), footerStartY + 85f, sigNamePaint)

                // --- 2. Right Column: Mengetahui, AIRPORT TECHNOLOGY DEPARTMENT HEAD ---
                val col2CenterX = marginR - 150f // Center point for Right Column
                val reportTimestamp = pageLogs.lastOrNull()?.timestamp ?: logs.lastOrNull()?.timestamp ?: System.currentTimeMillis()
                val cityDateText = "Pekanbaru, ${SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date(reportTimestamp))}"
                val cdW = sigBodyPaint.measureText(cityDateText)
                canvas.drawText(cityDateText, col2CenterX - (cdW / 2), footerStartY - 15f, sigBodyPaint)

                val textKnow = "Mengetahui,"
                val title2 = "AIRPORT TECHNOLOGY DEPARTMENT HEAD"
                val name2 = "EKO ARIF RAHMANTO"
                val tkW = sigBodyPaint.measureText(textKnow)
                val t2W = sigTitlePaint.measureText(title2)
                val n2W = sigNamePaint.measureText(name2)
                canvas.drawText(textKnow, col2CenterX - (tkW / 2), footerStartY, sigBodyPaint)
                canvas.drawText(title2, col2CenterX - (t2W / 2), footerStartY + 15f, sigTitlePaint)
                canvas.drawText(name2, col2CenterX - (n2W / 2), footerStartY + 85f, sigNamePaint)
            }

            pdfDocument.finishPage(page)
        }
    }

        // 5. Save the generated PDF
        val displayFileName = "Laporan_Maintenance_${monthName.substring(0, minOf(3, monthName.length))}_$year.pdf"
        val savedFile = savePdfBytesToStorage(context, pdfDocument, displayFileName)
        
        if (savedFile != null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Laporan PDF berhasil disimpan di Downloads/ atau Dokumen!", Toast.LENGTH_LONG).show()
            }
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Gagal menyimpan laporan PDF", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Terjadi kesalahan saat mencetak PDF: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    } finally {
        System.gc()
    }
}

// Draw text cell helper
private fun drawCellTextInPdf(
    canvas: Canvas,
    text: String,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    paint: Paint,
    isCentered: Boolean = true
) {
    val width = right - left
    val height = bottom - top
    val padding = 3f

    // Wrap words nicely
    val words = text.split(" ", "\n")
    val lines = mutableListOf<String>()
    var currentLine = ""
    for (word in words) {
        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
        val testWidth = paint.measureText(testLine)
        if (testWidth > (width - padding * 2) && currentLine.isNotEmpty()) {
            lines.add(currentLine)
            currentLine = word
        } else {
            currentLine = testLine
        }
    }
    if (currentLine.isNotEmpty()) {
        lines.add(currentLine)
    }

    val textHeight = paint.fontMetrics.descent - paint.fontMetrics.ascent
    val totalHeight = lines.size * textHeight
    var startY = top + (height - totalHeight) / 2f - paint.fontMetrics.ascent

    for (line in lines) {
        val startX = if (isCentered) {
            left + (width - paint.measureText(line)) / 2f
        } else {
            left + padding
        }
        canvas.drawText(line, startX, startY, paint)
        startY += textHeight
    }
}

// Draw checkbox helper
private fun drawCheckboxInPdf(
    canvas: Canvas,
    checked: Boolean,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    boxPaint: Paint,
    signPaint: Paint
) {
    val cellW = right - left
    val cellH = bottom - top
    val cx = left + cellW / 2
    val cy = top + cellH / 2
    val boxSize = 5f // 10x10 square
    
    // Draw outer checkbox box
    canvas.drawRect(cx - boxSize, cy - boxSize, cx + boxSize, cy + boxSize, boxPaint)
    
    // Draw checkmark inside if checked
    if (checked) {
        canvas.drawLine(cx - 3.5f, cy, cx - 1f, cy + 3f, signPaint)
        canvas.drawLine(cx - 1f, cy + 3f, cx + 3.5f, cy - 3.5f, signPaint)
    }
}

// Draw signature path helper
private fun drawSignatureInPdf(
    canvas: Canvas,
    signatureData: String,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    paint: Paint
) {
    val paths = deserializePaths(signatureData)
    if (paths.isEmpty()) return

    val cellW = right - left
    val cellH = bottom - top

    // Find bounds
    var minX = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var minY = Float.MAX_VALUE
    var maxY = Float.MIN_VALUE
    for (path in paths) {
        for (pt in path) {
            if (pt.x < minX) minX = pt.x
            if (pt.x > maxX) maxX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.y > maxY) maxY = pt.y
        }
    }

    val sigW = maxX - minX
    val sigH = maxY - minY

    if (sigW > 0 && sigH > 0) {
        val padW = 2f
        val padH = 1f
        val targetW = cellW - padW * 2
        val targetH = cellH - padH * 2
        val scale = minOf(targetW / sigW, targetH / sigH)

        val dx = left + (cellW - sigW * scale) / 2f
        val dy = top + (cellH - sigH * scale) / 2f

        for (path in paths) {
            if (path.isEmpty()) continue
            val p = android.graphics.Path()
            val start = path[0]
            p.moveTo(dx + (start.x - minX) * scale, dy + (start.y - minY) * scale)
            for (i in 1 until path.size) {
                val pt = path[i]
                p.lineTo(dx + (pt.x - minX) * scale, dy + (pt.y - minY) * scale)
            }
            canvas.drawPath(p, paint)
        }
    }
}

// Robust save PDF helper supporting modern MediaStore + Legacy File streams
private fun savePdfBytesToStorage(context: Context, pdfDocument: PdfDocument, fileName: String): File? {
    val baos = java.io.ByteArrayOutputStream()
    try {
        pdfDocument.writeTo(baos)
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    } finally {
        try {
            pdfDocument.close()
        } catch (e: Exception) {
            // ignore
        }
    }
    val pdfBytes = baos.toByteArray()

    // 1. Android Q+ Scoped Storage (Media Store Downloads)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { os ->
                    os.write(pdfBytes)
                }
                
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                
                return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    resolver.delete(uri, null, null)
                } catch (ex: Exception) {
                    // ignore
                }
            }
        }
    }

    // 2. Fallback to Standard File Stream Storage (Downloads Directory)
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (!downloadsDir.exists()) {
        downloadsDir.mkdirs()
    }
    val file = File(downloadsDir, fileName)
    try {
        FileOutputStream(file).use { fos ->
            fos.write(pdfBytes)
        }
        return file
    } catch (e: Exception) {
        e.printStackTrace()
        // 3. Final Safe App Private External Storage Directory Fallback
        val safeDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val safeFile = File(safeDir, fileName)
        try {
            FileOutputStream(safeFile).use { fos ->
                fos.write(pdfBytes)
            }
            return safeFile
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
    }
    return null
}

private fun saveCsvToStorage(context: Context, csvBytes: ByteArray, fileName: String): File? {
    // 1. Android Q+ Scoped Storage (Media Store Downloads)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { os ->
                    os.write(csvBytes)
                }
                
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                
                return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    resolver.delete(uri, null, null)
                } catch (ex: Exception) {
                    // ignore
                }
            }
        }
    }

    // 2. Fallback to Standard File Stream Storage (Downloads Directory)
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (!downloadsDir.exists()) {
        downloadsDir.mkdirs()
    }
    val file = File(downloadsDir, fileName)
    try {
        FileOutputStream(file).use { fos ->
            fos.write(csvBytes)
        }
        return file
    } catch (e: Exception) {
        e.printStackTrace()
        // 3. Final Safe App Private External Storage Directory Fallback
        val safeDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val safeFile = File(safeDir, fileName)
        try {
            FileOutputStream(safeFile).use { fos ->
                fos.write(csvBytes)
            }
            return safeFile
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
    }
    return null
}

private fun exportLogsToCsv(
    context: Context,
    logs: List<MaintenanceLog>,
    devices: List<Device>,
    monthName: String,
    year: Int
) {
    try {
        val fileName = "Data_Maintenance_${monthName.substring(0, minOf(3, monthName.length))}_$year.csv"
        val builder = java.lang.StringBuilder()
        
        // CSV Header
        builder.append("No,ID Log,Tanggal,Nama Teknisi,Nama Perangkat,Merek & Tipe,Serial Number,Status,Action Taken,Health Report,Disk Cleanup,Hardware Cleanup,Checking Drive Error,Scanning Virus,Checking Network,Updating Antivirus,Updating Aplikasi,Windows License,Office License,Notes\n")
        
        logs.forEachIndexed { index, log ->
            val device = devices.find { it.id == log.deviceId }
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
            val deviceType = device?.type ?: ""
            val deviceBrand = device?.brand ?: ""
            val sn = device?.sn ?: ""
            val condition = device?.condition ?: ""
            
            // Safe CSV cell escaping (quotes and commas)
            fun escapeCsv(value: String?): String {
                if (value == null) return ""
                if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains(";")) {
                    return "\"" + value.replace("\"", "\"\"") + "\""
                }
                return value
            }
            
            builder.append("${index + 1},")
            builder.append("${log.id},")
            builder.append("${escapeCsv(dateStr)},")
            builder.append("${escapeCsv(log.technicianName)},")
            builder.append("${escapeCsv(log.deviceName)},")
            builder.append("${escapeCsv("$deviceType $deviceBrand")},")
            builder.append("${escapeCsv(sn)},")
            builder.append("${escapeCsv(condition)},")
            builder.append("${escapeCsv(log.actionTaken)},")
            builder.append("${if (log.healthReport) "YA" else "TIDAK"},")
            builder.append("${if (log.diskCleanup) "YA" else "TIDAK"},")
            builder.append("${if (log.hardwareCleanup) "YA" else "TIDAK"},")
            builder.append("${if (log.checkingDriveError) "YA" else "TIDAK"},")
            builder.append("${if (log.scanningVirus) "YA" else "TIDAK"},")
            builder.append("${if (log.checkingNetwork) "YA" else "TIDAK"},")
            builder.append("${if (log.updatingAntivirus) "YA" else "TIDAK"},")
            builder.append("${if (log.updatingAplikasi) "YA" else "TIDAK"},")
            builder.append("${escapeCsv(log.windowsLicense)},")
            builder.append("${escapeCsv(log.officeLicense)},")
            builder.append("${escapeCsv(log.notes)}\n")
        }
        
        val csvBytes = builder.toString().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
        val fileSaved = saveCsvToStorage(context, csvBytes, fileName)
        
        if (fileSaved != null) {
            Toast.makeText(context, "Data berhasil diekspor ke CSV (Downloads/)!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Gagal mengekspor data ke CSV", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Terjadi kesalahan saat ekspor CSV: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun TroubleReportPreviewCard(
    ticket: TroubleTicket,
    device: Device?,
    onDelete: (() -> Unit)? = null
) {
    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(ticket.timestamp))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device?.name ?: ticket.deviceName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${device?.brand ?: "Device"} • SN: ${device?.sn ?: ""}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentOrange.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = dateStr,
                            color = AccentOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Hapus Laporan",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 10.dp))

            Text("Kendala / Kerusakan:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(ticket.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)

            if (ticket.actionTaken.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Tindakan / Solusi:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(ticket.actionTaken, fontSize = 12.sp, color = Green40, fontWeight = FontWeight.Medium)
            }

            if (ticket.duration.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Durasi Tindakan: ${ticket.duration}", fontSize = 11.sp, color = Color.Gray)
            }

            val hasPhotos = !ticket.photoBefore.isNullOrBlank() || !ticket.photoAfter.isNullOrBlank()
            if (hasPhotos) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Dokumentasi Foto",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Foto Sebelum & Sesudah Tersedia",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dilaporkan Oleh: ${ticket.reportedBy}", fontSize = 11.sp, color = Color.Gray)
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (ticket.status == "Pending") Color(0xFFFFF3E0) else GreenLight)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = ticket.status.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (ticket.status == "Pending") AccentOrange else Green40
                    )
                }
            }
        }
    }
}

private suspend fun generateAndSaveTroublePdf(
    context: Context,
    tickets: List<TroubleTicket>,
    devices: List<Device>,
    monthName: String,
    year: Int,
    deviceTypeFilter: String,
    location: String
) {
    try {
        val pdfDocument = PdfDocument()
        val pageW = 842
        val pageH = 595
        val marginL = 30f
        val marginR = 812f
        val tableW = 782f
        
        // Pagination logic
        val itemsPerPage = 8
        val chunks = tickets.chunked(itemsPerPage)
        
        // Column widths
        val colNoW = 20f
        val colTglW = 70f
        val colUsrW = 95f
        val colTipeW = 55f
        val colLokW = 75f
        val colSnW = 70f
        val colDescW = 150f
        val colActionW = 150f
        val colDurW = 45f
        val colStatusW = 52f

        val borderPaint = Paint().apply {
            color = android.graphics.Color.BLUE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        
        val gridPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        
        val headerBgPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#FFE0B2") // Warm orange/light amber accent for trouble tickets
            style = Paint.Style.FILL
        }

        val textHeaderPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 7f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val textBodyPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 6.5f
            isAntiAlias = true
        }

        val textTitlePaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }

        for (pageIndex in chunks.indices) {
            val pageTickets = chunks[pageIndex]
            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Page Outer Border
            canvas.drawRect(20f, 20f, pageW - 20f, pageH - 20f, borderPaint)

            // Title
            val titleText = "LAPORAN CORRECTIVE MAINTENANCE LAPTOP & PC AIO BANDARA SULTAN SYARIF KASIM II PEKANBARU BULAN ${monthName.uppercase()} TAHUN $year"
            val titleX = (pageW - textTitlePaint.measureText(titleText)) / 2f
            canvas.drawText(titleText, titleX, 60f, textTitlePaint)

            // Table Header boundaries
            val tTop = 110f
            val tBot = 140f
            
            canvas.drawRect(marginL, tTop, marginR, tBot, headerBgPaint)

            var currentX = marginL
            
            // Draw Headers
            val headers = listOf(
                "No" to colNoW,
                "Tanggal" to colTglW,
                "User" to colUsrW,
                "Tipe" to colTipeW,
                "Lokasi" to colLokW,
                "S/N" to colSnW,
                "Deskripsi Kerusakan" to colDescW,
                "Tindakan / Solusi" to colActionW,
                "Durasi" to colDurW,
                "Status" to colStatusW
            )

            for (h in headers) {
                canvas.drawRect(currentX, tTop, currentX + h.second, tBot, gridPaint)
                drawCellTextInPdf(canvas, h.first, currentX, currentX + h.second, tTop, tBot, textHeaderPaint)
                currentX += h.second
            }

            // Data Rows
            val rowHeight = 40f
            var rowTop = tBot
            
            for (index in pageTickets.indices) {
                val ticket = pageTickets[index]
                val dev = devices.find { it.id == ticket.deviceId }
                val rowBot = rowTop + rowHeight
                
                // Outer rectangle boundary
                canvas.drawRect(marginL, rowTop, marginR, rowBot, gridPaint)
                
                var cellX = marginL
                
                // 1. No
                val globalIndex = pageIndex * itemsPerPage + index + 1
                drawCellTextInPdf(canvas, globalIndex.toString(), cellX, cellX + colNoW, rowTop, rowBot, textBodyPaint)
                canvas.drawLine(cellX + colNoW, rowTop, cellX + colNoW, rowBot, gridPaint)
                cellX += colNoW
                
                // 2. Tanggal
                val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID")).format(Date(ticket.timestamp))
                drawCellTextInPdf(canvas, dateStr, cellX, cellX + colTglW, rowTop, rowBot, textBodyPaint)
                canvas.drawLine(cellX + colTglW, rowTop, cellX + colTglW, rowBot, gridPaint)
                cellX += colTglW
                
                // 3. User
                val userStr = dev?.name ?: ticket.deviceName
                drawCellTextInPdf(canvas, userStr, cellX, cellX + colUsrW, rowTop, rowBot, textBodyPaint)
                canvas.drawLine(cellX + colUsrW, rowTop, cellX + colUsrW, rowBot, gridPaint)
                cellX += colUsrW
                
                // 4. Tipe
                val tipeStr = dev?.type ?: "-"
                drawCellTextInPdf(canvas, tipeStr, cellX, cellX + colTipeW, rowTop, rowBot, textBodyPaint)
                canvas.drawLine(cellX + colTipeW, rowTop, cellX + colTipeW, rowBot, gridPaint)
                cellX += colTipeW
                
                // 5. Lokasi
                val lokStr = dev?.description ?: "-"
                drawCellTextInPdf(canvas, lokStr, cellX, cellX + colLokW, rowTop, rowBot, textBodyPaint)
                canvas.drawLine(cellX + colLokW, rowTop, cellX + colLokW, rowBot, gridPaint)
                cellX += colLokW
                
                // 6. SN
                val snStr = dev?.sn ?: "-"
                drawCellTextInPdf(canvas, snStr, cellX, cellX + colSnW, rowTop, rowBot, textBodyPaint)
                canvas.drawLine(cellX + colSnW, rowTop, cellX + colSnW, rowBot, gridPaint)
                cellX += colSnW
                
                // 7. Deskripsi Kerusakan
                drawCellTextInPdf(canvas, ticket.description, cellX, cellX + colDescW, rowTop, rowBot, textBodyPaint)
                canvas.drawLine(cellX + colDescW, rowTop, cellX + colDescW, rowBot, gridPaint)
                cellX += colDescW
                
                // 8. Tindakan / Solusi
                val actStr = ticket.actionTaken.ifBlank { "Menunggu tindakan" }
                drawCellTextInPdf(canvas, actStr, cellX, cellX + colActionW, rowTop, rowBot, textBodyPaint)
                canvas.drawLine(cellX + colActionW, rowTop, cellX + colActionW, rowBot, gridPaint)
                cellX += colActionW
                
                // 9. Durasi
                val durStr = ticket.duration.ifBlank { "-" }
                drawCellTextInPdf(canvas, durStr, cellX, cellX + colDurW, rowTop, rowBot, textBodyPaint)
                canvas.drawLine(cellX + colDurW, rowTop, cellX + colDurW, rowBot, gridPaint)
                cellX += colDurW
                
                // 10. Status
                val statStr = ticket.status.uppercase()
                drawCellTextInPdf(canvas, statStr, cellX, cellX + colStatusW, rowTop, rowBot, textBodyPaint)
                
                rowTop = rowBot
            }

            // Draw signature block at the bottom of the last page
            if (pageIndex == chunks.size - 1) {
                val footerStartY = 430f
                
                val sigTitlePaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 7.5f
                    isFakeBoldText = true
                    isAntiAlias = true
                }

                val sigNamePaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 7.5f
                    isFakeBoldText = true
                    isUnderlineText = true
                    isAntiAlias = true
                }

                val sigBodyPaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 7.5f
                    isAntiAlias = true
                }

                // --- 1. Left Column: Pengawas, AIRPORT TECHNOLOGY ENGINEER ---
                val col1CenterX = marginL + 150f
                val textSuper = "Pengawas,"
                val title1 = "AIRPORT TECHNOLOGY ENGINEER"
                val name1 = "ARIFATUL AZHAR"
                val tsW = sigBodyPaint.measureText(textSuper)
                val t1W = sigTitlePaint.measureText(title1)
                val n1W = sigNamePaint.measureText(name1)
                canvas.drawText(textSuper, col1CenterX - (tsW / 2), footerStartY, sigBodyPaint)
                canvas.drawText(title1, col1CenterX - (t1W / 2), footerStartY + 15f, sigTitlePaint)
                canvas.drawText(name1, col1CenterX - (n1W / 2), footerStartY + 85f, sigNamePaint)

                // --- 2. Right Column: Mengetahui, AIRPORT TECHNOLOGY DEPARTMENT HEAD ---
                val col2CenterX = marginR - 150f
                val reportTimestamp = pageTickets.lastOrNull()?.timestamp ?: tickets.lastOrNull()?.timestamp ?: System.currentTimeMillis()
                val cityDateText = "Pekanbaru, ${SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date(reportTimestamp))}"
                val cdW = sigBodyPaint.measureText(cityDateText)
                canvas.drawText(cityDateText, col2CenterX - (cdW / 2), footerStartY - 15f, sigBodyPaint)

                val textKnow = "Mengetahui,"
                val title2 = "AIRPORT TECHNOLOGY DEPARTMENT HEAD"
                val name2 = "EKO ARIF RAHMANTO"
                val tkW = sigBodyPaint.measureText(textKnow)
                val t2W = sigTitlePaint.measureText(title2)
                val n2W = sigNamePaint.measureText(name2)
                canvas.drawText(textKnow, col2CenterX - (tkW / 2), footerStartY, sigBodyPaint)
                canvas.drawText(title2, col2CenterX - (t2W / 2), footerStartY + 15f, sigTitlePaint)
                canvas.drawText(name2, col2CenterX - (n2W / 2), footerStartY + 85f, sigNamePaint)
            }

            // Draw Page Number
            val pageNumStr = "Halaman ${pageIndex + 1} dari ${chunks.size}"
            canvas.drawText(pageNumStr, pageW - 100f, pageH - 30f, textBodyPaint)

            pdfDocument.finishPage(page)
        }

        val displayFileName = "Laporan_Aduan_Trouble_${monthName.substring(0, minOf(3, monthName.length))}_$year.pdf"
        val savedFile = savePdfBytesToStorage(context, pdfDocument, displayFileName)
        if (savedFile != null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Laporan Trouble PDF berhasil disimpan!", Toast.LENGTH_LONG).show()
            }
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Gagal menyimpan Laporan Trouble PDF", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Terjadi kesalahan saat mencetak PDF: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

private suspend fun generateAndSaveTroubleDokumentasiPdf(
    context: Context,
    tickets: List<TroubleTicket>,
    monthName: String,
    year: Int
) {
    try {
        val pdfDocument = PdfDocument()
        val pageW = 595
        val pageH = 842
        
        val titlePaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 9.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 9f
            isAntiAlias = true
        }
        val gridPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val placeholderBgPaint = Paint().apply {
            color = android.graphics.Color.rgb(240, 240, 240)
            style = Paint.Style.FILL
        }
        val placeholderTextPaint = Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 8.5f
            isAntiAlias = true
        }

        val chunks = tickets.chunked(2)

        if (tickets.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Tidak ada data aduan trouble untuk dibuat dokumentasi", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Collect all distinct photo URIs to pre-fetch in parallel
        val uniquePhotoUris = tickets
            .flatMap { listOfNotNull(it.photoBefore, it.photoAfter) }
            .filter { !it.isNullOrBlank() && it != "null" && it != "undefined" }
            .distinct()

        val bitmapCache: Map<String, Bitmap?> = coroutineScope {
            uniquePhotoUris.map { uri ->
                async(Dispatchers.IO) {
                    uri to loadUriToBitmap(context, uri)
                }
            }.awaitAll().toMap()
        }

        try {
            for (pageIndex in chunks.indices) {
                val pageTickets = chunks[pageIndex]
                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageIndex + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val ticketTimestamp = pageTickets.firstOrNull()?.timestamp ?: System.currentTimeMillis()
                val dateText = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date(ticketTimestamp))
                val dW = subtitlePaint.measureText(dateText)

                var currentY: Float
                if (pageIndex == 0) {
                    // 1. Title: LAMPIRAN FOTO CORRECTIVE MAINTENANCE (only on page 1)
                    val titleText = "LAMPIRAN FOTO CORRECTIVE MAINTENANCE"
                    val tW = titlePaint.measureText(titleText)
                    canvas.drawText(titleText, (pageW - tW) / 2f, 50f, titlePaint)

                    // 2. Subtitle: Date
                    canvas.drawText(dateText, (pageW - dW) / 2f, 72f, subtitlePaint)
                    currentY = 100f
                } else {
                    canvas.drawText(dateText, (pageW - dW) / 2f, 42f, subtitlePaint)
                    currentY = 65f
                }

                for (ticket in pageTickets) {
                    // Draw Table Headers for Photos
                    val photoHeaderY = currentY
                    val photoHeaderH = 22f
                    canvas.drawRect(40f, photoHeaderY, 555f, photoHeaderY + photoHeaderH, gridPaint)
                    canvas.drawLine(297.5f, photoHeaderY, 297.5f, photoHeaderY + photoHeaderH, gridPaint)

                    val textBefore = "FOTO SEBELUM PERBAIKAN"
                    val textAfter = "FOTO SESUDAH PERBAIKAN"
                    val tbW = labelPaint.measureText(textBefore)
                    val taW = labelPaint.measureText(textAfter)

                    canvas.drawText(textBefore, 40f + (257.5f - tbW) / 2f, photoHeaderY + 15f, labelPaint)
                    canvas.drawText(textAfter, 297.5f + (257.5f - taW) / 2f, photoHeaderY + 15f, labelPaint)

                    // Draw Photos Row
                    val photoRowY = photoHeaderY + photoHeaderH
                    val photoRowH = 260f
                    canvas.drawRect(40f, photoRowY, 555f, photoRowY + photoRowH, gridPaint)
                    canvas.drawLine(297.5f, photoRowY, 297.5f, photoRowY + photoRowH, gridPaint)

                    // Before Photo
                    drawBitmapInPdfCell(context, canvas, ticket.photoBefore, 40f, 297.5f, photoRowY, photoRowY + photoRowH, placeholderBgPaint, placeholderTextPaint, ticket.photoBefore?.let { bitmapCache[it] })

                    // After Photo
                    drawBitmapInPdfCell(context, canvas, ticket.photoAfter, 297.5f, 555f, photoRowY, photoRowY + photoRowH, placeholderBgPaint, placeholderTextPaint, ticket.photoAfter?.let { bitmapCache[it] })

                    currentY = photoRowY + photoRowH + 30f
                }

                // Page Number
                val pageStr = "Halaman ${pageIndex + 1} dari ${chunks.size}"
                canvas.drawText(pageStr, pageW - 100f, pageH - 40f, textPaint)

                pdfDocument.finishPage(page)
            }

            val displayFileName = "Dokumentasi_Trouble_CM_${monthName.substring(0, minOf(3, monthName.length))}_$year.pdf"
            val savedFile = savePdfBytesToStorage(context, pdfDocument, displayFileName)
            if (savedFile != null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Dokumentasi Trouble PDF berhasil disimpan!", Toast.LENGTH_LONG).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal menyimpan Dokumentasi Trouble PDF", Toast.LENGTH_SHORT).show()
                }
            }
        } finally {
            bitmapCache.values.forEach { bmp ->
                try {
                    if (bmp != null && !bmp.isRecycled) {
                        bmp.recycle()
                    }
                } catch (e: Exception) {}
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Terjadi kesalahan saat mencetak Dokumentasi: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
