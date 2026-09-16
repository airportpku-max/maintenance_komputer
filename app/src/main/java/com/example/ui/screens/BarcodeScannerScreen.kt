package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.core.content.ContextCompat
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.example.data.Device
import com.example.ui.MaintenanceViewModel
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.Green40
import com.example.ui.theme.GreenDark
import com.example.ui.theme.GreenLight
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun BarcodeScannerScreen(
    viewModel: MaintenanceViewModel,
    modifier: Modifier = Modifier
) {
    val devices by viewModel.devices.collectAsState()
    val logs by viewModel.maintenanceLogs.collectAsState()
    val technicians by viewModel.technicians.collectAsState()
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var showQuickMaintenanceDialog by remember { mutableStateOf(false) }

    // Trigger permission request on entry if we are looking to scan
    LaunchedEffect(selectedDevice) {
        if (selectedDevice == null && !cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    // Quick Maintenance states
    var technicianName by remember { mutableStateOf("") }
    var actionTaken by remember { mutableStateOf("") }

    // Laser Animation setup for scanning simulator
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserYOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserOffset"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Green40)
                .padding(vertical = 16.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Barcode Scanner Perangkat",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        }

        if (selectedDevice == null) {
            // Scanner View Simulator
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Arahkan Kamera ke Barcode / QR",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Gunakan simulator scanning di bawah untuk memilih barcode perangkat komputer dan memeriksa riwayat maintenance.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // The Scanner Frame with real camera preview or permission fallback
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .border(4.dp, Green40, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (cameraPermissionState.status.isGranted) {
                        CameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            onBarcodeDetected = { scannedBarcode ->
                                val found = devices.find { it.serialNumber.trim().equals(scannedBarcode.trim(), ignoreCase = true) }
                                if (found != null) {
                                    selectedDevice = found
                                    Toast.makeText(context, "Berhasil Scan: ${found.name}", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Barcode Terdeteksi: $scannedBarcode (Tidak Terdaftar!)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } else {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Permission Required",
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Kamera Nonaktif",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            Button(
                                onClick = { cameraPermissionState.launchPermissionRequest() },
                                colors = ButtonDefaults.buttonColors(containerColor = Green40),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Izinkan Kamera", fontSize = 11.sp)
                            }
                        }
                    }

                    // Custom Canvas to draw laser
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val y = (laserYOffset / 200f) * size.height
                        drawLine(
                            color = Green40,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                var searchBarcodeQuery by remember { mutableStateOf("") }

                OutlinedTextField(
                    value = searchBarcodeQuery,
                    onValueChange = { searchBarcodeQuery = it },
                    label = { Text("Ketik ID Barcode Manual") },
                    placeholder = { Text("Contoh: BC-AIO-41235") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = "Barcode", tint = Green40) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val found = devices.find { it.serialNumber.trim().equals(searchBarcodeQuery.trim(), ignoreCase = true) }
                                if (found != null) {
                                    selectedDevice = found
                                    Toast.makeText(context, "Perangkat Ditemukan!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Barcode tidak terdaftar!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Cari", tint = Green40)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Green40,
                        focusedLabelColor = Green40
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Atau simulasikan scanning dengan memilih salah satu perangkat di bawah:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Select barcode device simulator
                var dropdownExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("simulate_scan_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Green40),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Green40))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Barcode")
                            Text("Pilih Perangkat untuk Scan")
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        devices.forEach { dev ->
                            val isMaintained = viewModel.isDeviceAlreadyMaintainedInMonth(dev, System.currentTimeMillis())
                            DropdownMenuItem(
                                enabled = !isMaintained,
                                text = { 
                                    val brandText = if (dev.brand.isNotBlank()) " (${dev.brand})" else ""
                                    val statusText = if (isMaintained) " - (Sudah Maintenance)" else ""
                                    Text(
                                        text = "[${dev.serialNumber}] - ${dev.name}$brandText$statusText",
                                        color = if (isMaintained) Color.Gray.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = if (isMaintained) FontWeight.Normal else FontWeight.Medium
                                    ) 
                                },
                                onClick = {
                                    if (isMaintained) {
                                        Toast.makeText(context, "Perangkat sudah di-maintenance bulan ini!", Toast.LENGTH_SHORT).show()
                                        return@DropdownMenuItem
                                    }
                                    isScanning = true
                                    dropdownExpanded = false
                                    // Simulate scanning delay
                                    selectedDevice = dev
                                    isScanning = false
                                    playBeepAndVibrate(context)
                                    Toast.makeText(context, "Scan Barcode Berhasil!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // Scanned result View
            val dev = selectedDevice!!
            val deviceLogs = logs.filter { it.deviceId == dev.id }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Header of Scanned Device
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = GreenLight)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .background(Color.White, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(if (dev.type == "AIO") "🖥️" else "💻", fontSize = 24.sp)
                                    }
                                    Column {
                                        Text(dev.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = GreenDark)
                                        if (dev.brand.isNotBlank()) {
                                            Text("Merek: ${dev.brand}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                                        }
                                        Text("Barcode: ${dev.serialNumber}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                }

                                IconButton(
                                    onClick = { selectedDevice = null },
                                    modifier = Modifier.background(Color.White, CircleShape).size(36.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Red)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = Color.White.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Lokasi", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    Text(
                                        text = dev.description.ifBlank { "Tidak dispesifikasi" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Status Kondisi", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(if (dev.condition == "Baik") Color(0xFF4CAF50) else AccentOrange, CircleShape)
                                        )
                                        Text(
                                            text = dev.condition,
                                            fontWeight = FontWeight.Bold,
                                            color = if (dev.condition == "Baik") Color(0xFF4CAF50) else AccentOrange,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Perform quick maintenance action button
                            Button(
                                onClick = {
                                    val checkTime = System.currentTimeMillis()
                                    if (viewModel.isDeviceAlreadyMaintainedInMonth(dev, checkTime)) {
                                        val monthName = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("id", "ID")).format(java.util.Date(checkTime))
                                        Toast.makeText(context, "Perangkat dengan SN ini sudah di-maintenance pada bulan $monthName!", Toast.LENGTH_LONG).show()
                                    } else {
                                        technicianName = technicians.firstOrNull()?.name ?: ""
                                        showQuickMaintenanceDialog = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Green40),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("quick_maint_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Build, contentDescription = "Maint", modifier = Modifier.size(16.dp))
                                    Text("Lakukan Maintenance Langsung", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Title: Riwayat Maintenance
                item {
                    Text(
                        text = "Riwayat Maintenance (${deviceLogs.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                if (deviceLogs.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Belum ada riwayat maintenance tercatat untuk perangkat ini.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(deviceLogs, key = { it.id }) { log ->
                        val logDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(log.timestamp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column {
                                        Text(
                                            text = log.technicianName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Teknisi Maintenance",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }
                                    Text(
                                        text = logDate,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                Spacer(modifier = Modifier.height(12.dp))

                                // Checklist results
                                Text(
                                    text = "Tindakan & Pemeriksaan:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                val checkedItems = remember(log) {
                                    mutableListOf<String>().apply {
                                        if (log.healthReport) add("Health Report")
                                        if (log.diskCleanup) add("Disk CleanUp")
                                        if (log.hardwareCleanup) add("Hardware CleanUp")
                                        if (log.checkingDriveError) add("Checking Drive Error")
                                        if (log.scanningVirus) add("Scanning Virus")
                                        if (log.checkingNetwork) add("Checking Network")
                                        if (log.updatingAplikasi) add("Updating Aplikasi")
                                    }
                                }

                                if (checkedItems.isNotEmpty()) {
                                    checkedItems.forEach { item ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Checked",
                                                tint = Green40,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = item,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = log.actionTaken,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                }



                                // Notes / Keterangan
                                val notesDisplay = log.notes ?: ""
                                if (notesDisplay.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Keterangan:",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = notesDisplay,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                }

                                // User Signature
                                val sigData = log.signatureData
                                if (!sigData.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Tanda Tangan User:",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    SignatureView(
                                        signatureData = sigData,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(60.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Quick Maintenance Dialog
    if (showQuickMaintenanceDialog) {
        var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }

        var healthReport by remember { mutableStateOf(false) }
        var diskCleanup by remember { mutableStateOf(false) }
        var hardwareCleanup by remember { mutableStateOf(false) }
        var checkingDriveError by remember { mutableStateOf(false) }
        var scanningVirus by remember { mutableStateOf(false) }
        var checkingNetwork by remember { mutableStateOf(false) }
        var updatingAntivirus by remember { mutableStateOf(false) }
        var updatingAplikasi by remember { mutableStateOf(false) }

        var healthReportBefore by remember { mutableStateOf<String?>(null) }
        var healthReportAfter by remember { mutableStateOf<String?>(null) }
        var diskCleanupBefore by remember { mutableStateOf<String?>(null) }
        var diskCleanupAfter by remember { mutableStateOf<String?>(null) }
        var hardwareCleanupBefore by remember { mutableStateOf<String?>(null) }
        var hardwareCleanupAfter by remember { mutableStateOf<String?>(null) }
        var checkingDriveErrorBefore by remember { mutableStateOf<String?>(null) }
        var checkingDriveErrorAfter by remember { mutableStateOf<String?>(null) }
        var scanningVirusBefore by remember { mutableStateOf<String?>(null) }
        var scanningVirusAfter by remember { mutableStateOf<String?>(null) }
        var checkingNetworkBefore by remember { mutableStateOf<String?>(null) }
        var checkingNetworkAfter by remember { mutableStateOf<String?>(null) }
        var updatingAntivirusBefore by remember { mutableStateOf<String?>(null) }
        var updatingAntivirusAfter by remember { mutableStateOf<String?>(null) }
        var updatingAplikasiBefore by remember { mutableStateOf<String?>(null) }
        var updatingAplikasiAfter by remember { mutableStateOf<String?>(null) }

        var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
        var activeCaptureKey by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
        var showPhotoSourceDialog by remember { mutableStateOf(false) }

        var windowsLicense by remember { mutableStateOf("YA") }
        var officeLicense by remember { mutableStateOf("YA") }
        var signaturePaths by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
        var techSignaturePaths by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }

        val context = LocalContext.current
        val dialogScrollState = rememberScrollState()

        val scope = rememberCoroutineScope()
        val updateBarcodePhotoState = { itemKey: String, isBefore: Boolean, uriStr: String ->
            when (itemKey) {
                "healthReport" -> {
                    if (isBefore) healthReportBefore = uriStr else healthReportAfter = uriStr
                }
                "diskCleanup" -> {
                    if (isBefore) diskCleanupBefore = uriStr else diskCleanupAfter = uriStr
                }
                "hardwareCleanup" -> {
                    if (isBefore) hardwareCleanupBefore = uriStr else hardwareCleanupAfter = uriStr
                }
                "checkingDriveError" -> {
                    if (isBefore) checkingDriveErrorBefore = uriStr else checkingDriveErrorAfter = uriStr
                }
                "scanningVirus" -> {
                    if (isBefore) scanningVirusBefore = uriStr else scanningVirusAfter = uriStr
                }
                "checkingNetwork" -> {
                    if (isBefore) checkingNetworkBefore = uriStr else checkingNetworkAfter = uriStr
                }
                "updatingAntivirus" -> {
                    if (isBefore) updatingAntivirusBefore = uriStr else updatingAntivirusAfter = uriStr
                }
                "updatingAplikasi" -> {
                    if (isBefore) updatingAplikasiBefore = uriStr else updatingAplikasiAfter = uriStr
                }
            }
        }

        val cameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture()
        ) { success ->
            if (success && tempPhotoUri != null && activeCaptureKey != null) {
                val savedUriStr = saveUriToInternalStorage(context, tempPhotoUri!!) ?: tempPhotoUri.toString()
                val (itemKey, isBefore) = activeCaptureKey!!
                updateBarcodePhotoState(itemKey, isBefore, savedUriStr)
            }
        }

        val galleryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null && activeCaptureKey != null) {
                val savedUriStr = saveUriToInternalStorage(context, uri) ?: uri.toString()
                val (itemKey, isBefore) = activeCaptureKey!!
                updateBarcodePhotoState(itemKey, isBefore, savedUriStr)
            }
        }

        val onPhotoSlotClick = { key: String, isBefore: Boolean ->
            activeCaptureKey = Pair(key, isBefore)
            showPhotoSourceDialog = true
        }

        if (showPhotoSourceDialog) {
            AlertDialog(
                onDismissRequest = { showPhotoSourceDialog = false },
                title = { Text("Pilih Sumber Foto") },
                text = { Text("Silakan pilih kamera atau galeri untuk mengunggah foto.") },
                confirmButton = {
                    TextButton(onClick = {
                        showPhotoSourceDialog = false
                        if (cameraPermissionState.status.isGranted) {
                            try {
                                val uri = createHighQualityPhotoUri(context)
                                if (uri != null) {
                                    tempPhotoUri = uri
                                    cameraLauncher.launch(uri)
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Tidak dapat membuka kamera: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    }) {
                        Text("Kamera")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showPhotoSourceDialog = false
                        galleryLauncher.launch("image/*")
                    }) {
                        Text("Galeri")
                    }
                }
            )
        }

        AlertDialog(
            onDismissRequest = { showQuickMaintenanceDialog = false },
            title = { Text("Pencatatan Maintenance Langsung", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(dialogScrollState)
                ) {
                    Text("Perangkat: ${selectedDevice?.name}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                    // Date Selection Form
                    val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
                    val datePickerDialog = android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val newCal = Calendar.getInstance()
                            newCal.set(year, month, dayOfMonth)
                            selectedDate = newCal.timeInMillis
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )

                    val formattedDate = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date(selectedDate))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = formattedDate,
                            onValueChange = {},
                            label = { Text("Tanggal Pemeriksaan") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { datePickerDialog.show() }) {
                                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "Pilih Tanggal")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { datePickerDialog.show() }
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                    Text("Daftar Pemeriksaan / Checklist:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    // Checklist checkbox items with support for before & after photos
                    @Composable
                    fun ChecklistItemRow(
                        label: String,
                        checked: Boolean,
                        onCheckedChange: (Boolean) -> Unit,
                        beforeUri: String?,
                        afterUri: String?,
                        onBeforeClick: () -> Unit,
                        onAfterClick: () -> Unit,
                        onClearBefore: () -> Unit,
                        onClearAfter: () -> Unit
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCheckedChange(!checked) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = onCheckedChange,
                                    colors = CheckboxDefaults.colors(checkedColor = Green40)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }

                            if (checked) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, top = 4.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // BEFORE Photo Container
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Sebelum", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(80.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { onBeforeClick() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (beforeUri != null) {
                                                AsyncImage(
                                                    model = beforeUri,
                                                    contentDescription = "Sebelum",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                IconButton(
                                                    onClick = { onClearBefore() },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .size(22.dp)
                                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Hapus", tint = Color.White, modifier = Modifier.size(12.dp))
                                                }
                                            } else {
                                                Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = "Ambil Foto", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                            }
                                        }
                                    }

                                    // AFTER Photo Container
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Sesudah", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(80.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { onAfterClick() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (afterUri != null) {
                                                AsyncImage(
                                                    model = afterUri,
                                                    contentDescription = "Sesudah",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                IconButton(
                                                    onClick = { onClearAfter() },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .size(22.dp)
                                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Hapus", tint = Color.White, modifier = Modifier.size(12.dp))
                                                }
                                            } else {
                                                Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = "Ambil Foto", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ChecklistItemRow("Health Report", healthReport, { healthReport = it }, viewModel.getLocalFilePathForUrl(context, healthReportBefore, "maintenance_photos"), viewModel.getLocalFilePathForUrl(context, healthReportAfter, "maintenance_photos"), { onPhotoSlotClick("healthReport", true) }, { onPhotoSlotClick("healthReport", false) }, { healthReportBefore = null }, { healthReportAfter = null })
                    ChecklistItemRow("Disk CleanUp", diskCleanup, { diskCleanup = it }, viewModel.getLocalFilePathForUrl(context, diskCleanupBefore, "maintenance_photos"), viewModel.getLocalFilePathForUrl(context, diskCleanupAfter, "maintenance_photos"), { onPhotoSlotClick("diskCleanup", true) }, { onPhotoSlotClick("diskCleanup", false) }, { diskCleanupBefore = null }, { diskCleanupAfter = null })
                    ChecklistItemRow("Hardware CleanUp", hardwareCleanup, { hardwareCleanup = it }, viewModel.getLocalFilePathForUrl(context, hardwareCleanupBefore, "maintenance_photos"), viewModel.getLocalFilePathForUrl(context, hardwareCleanupAfter, "maintenance_photos"), { onPhotoSlotClick("hardwareCleanup", true) }, { onPhotoSlotClick("hardwareCleanup", false) }, { hardwareCleanupBefore = null }, { hardwareCleanupAfter = null })
                    ChecklistItemRow("Checking Drive Error", checkingDriveError, { checkingDriveError = it }, viewModel.getLocalFilePathForUrl(context, checkingDriveErrorBefore, "maintenance_photos"), viewModel.getLocalFilePathForUrl(context, checkingDriveErrorAfter, "maintenance_photos"), { onPhotoSlotClick("checkingDriveError", true) }, { onPhotoSlotClick("checkingDriveError", false) }, { checkingDriveErrorBefore = null }, { checkingDriveErrorAfter = null })
                    ChecklistItemRow("Scanning Virus", scanningVirus, { scanningVirus = it }, viewModel.getLocalFilePathForUrl(context, scanningVirusBefore, "maintenance_photos"), viewModel.getLocalFilePathForUrl(context, scanningVirusAfter, "maintenance_photos"), { onPhotoSlotClick("scanningVirus", true) }, { onPhotoSlotClick("scanningVirus", false) }, { scanningVirusBefore = null }, { scanningVirusAfter = null })
                    ChecklistItemRow("Checking Network", checkingNetwork, { checkingNetwork = it }, viewModel.getLocalFilePathForUrl(context, checkingNetworkBefore, "maintenance_photos"), viewModel.getLocalFilePathForUrl(context, checkingNetworkAfter, "maintenance_photos"), { onPhotoSlotClick("checkingNetwork", true) }, { onPhotoSlotClick("checkingNetwork", false) }, { checkingNetworkBefore = null }, { checkingNetworkAfter = null })
                    ChecklistItemRow("Updating Antivirus Databases", updatingAntivirus, { updatingAntivirus = it }, viewModel.getLocalFilePathForUrl(context, updatingAntivirusBefore, "maintenance_photos"), viewModel.getLocalFilePathForUrl(context, updatingAntivirusAfter, "maintenance_photos"), { onPhotoSlotClick("updatingAntivirus", true) }, { onPhotoSlotClick("updatingAntivirus", false) }, { updatingAntivirusBefore = null }, { updatingAntivirusAfter = null })
                    ChecklistItemRow("Updating Aplikasi", updatingAplikasi, { updatingAplikasi = it }, viewModel.getLocalFilePathForUrl(context, updatingAplikasiBefore, "maintenance_photos"), viewModel.getLocalFilePathForUrl(context, updatingAplikasiAfter, "maintenance_photos"), { onPhotoSlotClick("updatingAplikasi", true) }, { onPhotoSlotClick("updatingAplikasi", false) }, { updatingAplikasiBefore = null }, { updatingAplikasiAfter = null })

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                    OutlinedTextField(
                        value = actionTaken,
                        onValueChange = { actionTaken = it },
                        label = { Text("Keterangan") },
                        placeholder = { Text("Tambahkan keterangan atau catatan tambahan mengenai proses maintenance...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("input_scanner_maint_action")
                    )

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                    Text("Tanda Tangan Petugas:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    SignaturePad(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        paths = techSignaturePaths,
                        onSignatureChanged = { paths ->
                            techSignaturePaths = paths
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Tanda Tangan User:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    SignaturePad(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        paths = signaturePaths,
                        onSignatureChanged = { paths ->
                            signaturePaths = paths
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val dev = selectedDevice
                        if (dev != null) {
                            // Compile description for backwards compatibility
                            val checklistSummary = mutableListOf<String>()
                            if (healthReport) checklistSummary.add("Health Report")
                            if (diskCleanup) checklistSummary.add("Disk CleanUp")
                            if (hardwareCleanup) checklistSummary.add("Hardware CleanUp")
                            if (checkingDriveError) checklistSummary.add("Checking Drive Error")
                            if (scanningVirus) checklistSummary.add("Scanning Virus")
                            if (checkingNetwork) checklistSummary.add("Checking Network")
                            if (updatingAntivirus) checklistSummary.add("Updating Antivirus Databases")
                            if (updatingAplikasi) checklistSummary.add("Updating Aplikasi")

                            val compiledAction = buildString {
                                append("Pemeriksaan:\n")
                                if (checklistSummary.isEmpty()) {
                                    append("- Tidak ada tindakan dipilih\n")
                                } else {
                                    checklistSummary.forEach { append("- $it\n") }
                                }
                                if (actionTaken.isNotBlank()) {
                                    append("\nKeterangan:\n")
                                    append(actionTaken)
                                }
                            }

                            val signatureDataStr = if (signaturePaths.isNotEmpty()) {
                                serializePaths(signaturePaths)
                            } else {
                                ""
                            }

                            val techSignatureDataStr = if (techSignaturePaths.isNotEmpty()) {
                                serializePaths(techSignaturePaths)
                            } else {
                                ""
                            }

                            // Use the first technician name or default as background value
                            val backgroundTechName = technicians.firstOrNull()?.name ?: "Hendi Rahmadi"

                            if (viewModel.isDeviceAlreadyMaintainedInMonth(dev, selectedDate)) {
                                val monthName = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("id", "ID")).format(java.util.Date(selectedDate))
                                Toast.makeText(context, "Perangkat dengan SN ini sudah di-maintenance pada bulan $monthName!", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            viewModel.performDirectMaintenance(
                                device = dev,
                                technicianName = backgroundTechName,
                                actionTaken = compiledAction,
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
                                notes = actionTaken,
                                signatureData = signatureDataStr,
                                techSignatureData = techSignatureDataStr,
                                timestamp = selectedDate,
                                healthReportBeforePhoto = healthReportBefore,
                                healthReportAfterPhoto = healthReportAfter,
                                diskCleanupBeforePhoto = diskCleanupBefore,
                                diskCleanupAfterPhoto = diskCleanupAfter,
                                hardwareCleanupBeforePhoto = hardwareCleanupBefore,
                                hardwareCleanupAfterPhoto = hardwareCleanupAfter,
                                checkingDriveErrorBeforePhoto = checkingDriveErrorBefore,
                                checkingDriveErrorAfterPhoto = checkingDriveErrorAfter,
                                scanningVirusBeforePhoto = scanningVirusBefore,
                                scanningVirusAfterPhoto = scanningVirusAfter,
                                checkingNetworkBeforePhoto = checkingNetworkBefore,
                                checkingNetworkAfterPhoto = checkingNetworkAfter,
                                updatingAntivirusBeforePhoto = updatingAntivirusBefore,
                                updatingAntivirusAfterPhoto = updatingAntivirusAfter,
                                updatingAplikasiBeforePhoto = updatingAplikasiBefore,
                                updatingAplikasiAfterPhoto = updatingAplikasiAfter
                            )

                            // Refetch device details to update screen
                            selectedDevice = dev.copy(condition = "Baik")
                            actionTaken = ""
                            showQuickMaintenanceDialog = false
                            Toast.makeText(context, "Maintenance berhasil disimpan!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Green40),
                    modifier = Modifier.testTag("scanner_maint_save")
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickMaintenanceDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

// Signature Serialization/Deserialization
fun serializePaths(paths: List<List<Offset>>): String {
    return paths.joinToString("|") { path ->
        path.joinToString(";") { offset ->
            "${offset.x},${offset.y}"
        }
    }
}

fun deserializePaths(data: String): List<List<Offset>> {
    if (data.isBlank()) return emptyList()
    return try {
        data.split("|").map { pathStr ->
            pathStr.split(";").map { offsetStr ->
                val coords = offsetStr.split(",")
                Offset(coords[0].toFloat(), coords[1].toFloat())
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

@Composable
private fun SignaturePad(
    modifier: Modifier = Modifier,
    paths: List<List<Offset>> = emptyList(),
    onSignatureChanged: (List<List<Offset>>) -> Unit
) {
    val currentPath = remember { mutableStateListOf<Offset>() }
    val completedPaths = remember { mutableStateListOf<List<Offset>>() }

    LaunchedEffect(paths) {
        if (completedPaths.toList() != paths) {
            completedPaths.clear()
            completedPaths.addAll(paths)
        }
    }

    Box(
        modifier = modifier
            .background(Color.White, shape = RoundedCornerShape(8.dp))
            .border(1.dp, Color.Gray.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPath.clear()
                            currentPath.add(offset)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            currentPath.add(change.position)
                        },
                        onDragEnd = {
                            if (currentPath.isNotEmpty()) {
                                completedPaths.add(currentPath.toList())
                                currentPath.clear()
                                onSignatureChanged(completedPaths.toList())
                            }
                        }
                    )
                }
        ) {
            // Draw completed paths
            completedPaths.forEach { path ->
                if (path.size > 1) {
                    val p = Path().apply {
                        moveTo(path.first().x, path.first().y)
                        for (i in 1 until path.size) {
                            lineTo(path[i].x, path[i].y)
                        }
                    }
                    drawPath(
                        path = p,
                        color = Color.Black,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
            // Draw current path
            if (currentPath.size > 1) {
                val p = Path().apply {
                    moveTo(currentPath.first().x, currentPath.first().y)
                    for (i in 1 until currentPath.size) {
                        lineTo(currentPath[i].x, currentPath[i].y)
                    }
                }
                drawPath(
                    path = p,
                    color = Color.Black,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        // Clear button
        IconButton(
            onClick = {
                completedPaths.clear()
                currentPath.clear()
                onSignatureChanged(emptyList())
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Hapus Tanda Tangan",
                tint = Color.Red.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun SignatureView(
    signatureData: String,
    modifier: Modifier = Modifier
) {
    val paths = remember(signatureData) { deserializePaths(signatureData) }
    if (paths.isEmpty()) {
        Box(
            modifier = modifier
                .background(Color.White, shape = RoundedCornerShape(8.dp))
                .border(1.dp, Color.Gray.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Tidak ada tanda tangan", fontSize = 11.sp, color = Color.Gray)
        }
        return
    }
    Canvas(
        modifier = modifier
            .background(Color.White, shape = RoundedCornerShape(8.dp))
            .border(1.dp, Color.Gray.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp))
    ) {
        // Find bounding box to scale signature to fit the view
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        paths.forEach { path ->
            path.forEach { offset ->
                if (offset.x < minX) minX = offset.x
                if (offset.x > maxX) maxX = offset.x
                if (offset.y < minY) minY = offset.y
                if (offset.y > maxY) maxY = offset.y
            }
        }

        val sigWidth = maxX - minX
        val sigHeight = maxY - minY

        if (sigWidth > 0 && sigHeight > 0) {
            val scaleX = (size.width - 24) / sigWidth
            val scaleY = (size.height - 24) / sigHeight
            val scale = minOf(scaleX, scaleY, 1.0f)

            val offsetX = (size.width - sigWidth * scale) / 2 - minX * scale
            val offsetY = (size.height - sigHeight * scale) / 2 - minY * scale

            paths.forEach { path ->
                if (path.size > 1) {
                    val p = Path().apply {
                        moveTo(path.first().x * scale + offsetX, path.first().y * scale + offsetY)
                        for (i in 1 until path.size) {
                            lineTo(path[i].x * scale + offsetX, path[i].y * scale + offsetY)
                        }
                    }
                    drawPath(
                        path = p,
                        color = Color.Black,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

private fun createHighQualityPhotoUri(context: android.content.Context): Uri? {
    return try {
        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = java.io.File(cachePath, "captured_device_${System.currentTimeMillis()}.jpg")
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun saveUriToInternalStorage(context: android.content.Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val cachePath = java.io.File(context.filesDir, "maintenance_photos")
        cachePath.mkdirs()
        val file = java.io.File(cachePath, "img_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}.jpg")
        file.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        Uri.fromFile(file).toString()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


