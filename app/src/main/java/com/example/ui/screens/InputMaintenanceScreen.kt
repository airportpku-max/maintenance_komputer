package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Device
import com.example.data.Technician
import com.example.ui.MaintenanceViewModel
import com.example.ui.theme.Green40
import com.example.ui.theme.GreenDark
import com.example.ui.theme.GreenLight
import com.example.ui.theme.AccentOrange
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun InputMaintenanceScreen(
    viewModel: MaintenanceViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val devices by viewModel.devices.collectAsState()
    val technicians by viewModel.technicians.collectAsState()
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    // State Variables
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedLocation by remember { mutableStateOf("") }

    // Checklist States
    var healthReport by remember { mutableStateOf(false) }
    var diskCleanup by remember { mutableStateOf(false) }
    var hardwareCleanup by remember { mutableStateOf(false) }
    var checkingDriveError by remember { mutableStateOf(false) }
    var scanningVirus by remember { mutableStateOf(false) }
    var checkingNetwork by remember { mutableStateOf(false) }
    var updatingAntivirus by remember { mutableStateOf(false) }
    var updatingAplikasi by remember { mutableStateOf(false) }

    // Before and After Photo States
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

    // Photo Source Dialog States
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var activeCaptureKey by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // itemKey to isBefore
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()

    val updatePhotoState = { itemKey: String, isBefore: Boolean, uriStr: String ->
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
            updatePhotoState(itemKey, isBefore, savedUriStr)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && activeCaptureKey != null) {
            val savedUriStr = saveUriToInternalStorage(context, uri) ?: uri.toString()
            val (itemKey, isBefore) = activeCaptureKey!!
            updatePhotoState(itemKey, isBefore, savedUriStr)
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

    // Per-user States (Keyed by Device ID)
    val techSignatures = remember { mutableStateMapOf<Int, List<List<Offset>>>() }
    val signatures = remember { mutableStateMapOf<Int, List<List<Offset>>>() }
    val notes = remember { mutableStateMapOf<Int, String>() }
    val selectedDeviceIds = remember { mutableStateMapOf<Int, Boolean>() }
    var deviceToReset by remember { mutableStateOf<Device?>(null) }

    // Dialog Konfirmasi Batalkan Status Maintenance
    if (deviceToReset != null) {
        val target = deviceToReset!!
        val monthName = SimpleDateFormat("MMMM yyyy", Locale("id", "ID")).format(Date(selectedDate))
        AlertDialog(
            onDismissRequest = { deviceToReset = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Batalkan Status Maintenance?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Apakah Anda ingin membatalkan status maintenance untuk ${target.name} (${target.serialNumber}) pada bulan $monthName? Log riwayat untuk perangkat ini pada bulan tersebut akan dihapus dan statusnya akan kembali menjadi belum dirawat.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelMaintenanceForDeviceInMonth(target, selectedDate) {
                            Toast.makeText(context, "Status maintenance ${target.name} berhasil dibatalkan!", Toast.LENGTH_SHORT).show()
                        }
                        deviceToReset = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Ya, Batalkan", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deviceToReset = null }) {
                    Text("Tutup")
                }
            }
        )
    }

    // Dropdowns
    var locationDropdownExpanded by remember { mutableStateOf(false) }

    // Extract unique locations from devices
    val locations = remember(devices) {
        devices.map { it.description.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    // Filter devices in selected location
    val devicesInLocation = remember(devices, selectedLocation) {
        if (selectedLocation.isBlank()) emptyList()
        else devices.filter { it.description.trim().equals(selectedLocation.trim(), ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Input Maintenance Massal", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green40)
            )
        },
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. HEADER CONFIGURATION CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Green40,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Konfigurasi Maintenance",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        // Date Selector
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
                        OutlinedTextField(
                            value = formattedDate,
                            onValueChange = {},
                            label = { Text("Tanggal Pemeriksaan") },
                            readOnly = true,
                            leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Green40) },
                            trailingIcon = {
                                IconButton(onClick = { datePickerDialog.show() }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Ubah Tanggal", tint = Green40)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { datePickerDialog.show() },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Green40,
                                focusedLabelColor = Green40
                            )
                        )

                        // Location Picker
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedLocation.ifBlank { "Pilih Lokasi" },
                                onValueChange = {},
                                label = { Text("Lokasi Pemeriksaan") },
                                readOnly = true,
                                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = Green40) },
                                trailingIcon = {
                                    IconButton(onClick = { locationDropdownExpanded = true }) {
                                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = Green40)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { locationDropdownExpanded = true },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Green40,
                                    focusedLabelColor = Green40
                                )
                            )

                            DropdownMenu(
                                expanded = locationDropdownExpanded,
                                onDismissRequest = { locationDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                if (locations.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Belum ada lokasi perangkat terdaftar") },
                                        onClick = { locationDropdownExpanded = false }
                                    )
                                } else {
                                    locations.forEach { loc ->
                                        val devicesInLoc = devices.filter { it.description.trim().equals(loc.trim(), ignoreCase = true) }
                                        val unmaintainedCount = devicesInLoc.count { !viewModel.isDeviceAlreadyMaintainedInMonth(it, selectedDate) }
                                        val isAllMaintained = unmaintainedCount == 0

                                        DropdownMenuItem(
                                            enabled = !isAllMaintained,
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = loc,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isAllMaintained) FontWeight.Normal else FontWeight.Medium,
                                                        color = if (isAllMaintained) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(end = 8.dp)
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = if (isAllMaintained) Color(0xFFEEEEEE) else Color(0xFFFFF3E0),
                                                        contentColor = if (isAllMaintained) Color(0xFF9E9E9E) else Color(0xFFE65100)
                                                    ) {
                                                        Text(
                                                            text = if (isAllMaintained) "0 belum" else "$unmaintainedCount belum",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                if (!isAllMaintained) {
                                                    selectedLocation = loc
                                                    locationDropdownExpanded = false
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. CHECKLIST CARDS (Daftar Pemeriksaan Massal)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = null,
                                tint = Green40,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Daftar Pemeriksaan Massal",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Text(
                            text = "Item pemeriksaan terpilih di bawah ini akan langsung diterapkan secara massal ke seluruh perangkat di lokasi yang Anda pilih.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 8 Checkboxes in a neat layout
                        ChecklistToggleRow("Health Report", healthReport, { healthReport = it }, viewModel.getLocalFilePathForUrl(context, healthReportBefore, "maintenance_photos"), viewModel.getLocalFilePathForUrl(context, healthReportAfter, "maintenance_photos"), { onPhotoSlotClick("healthReport", true) }, { onPhotoSlotClick("healthReport", false) }, { healthReportBefore = null }, { healthReportAfter = null })
                        ChecklistToggleRow("Disk CleanUp", diskCleanup, { diskCleanup = it }, viewModel.getLocalFilePathForUrl(context, diskCleanupBefore, "maintenance_photos"), viewModel.getLocalFilePathForUrl(context, diskCleanupAfter, "maintenance_photos"), { onPhotoSlotClick("diskCleanup", true) }, { onPhotoSlotClick("diskCleanup", false) }, { diskCleanupBefore = null }, { diskCleanupAfter = null })
                        ChecklistToggleRow("Hardware CleanUp", hardwareCleanup, { hardwareCleanup = it }, viewModel.getLocalFilePathForUrl(context, hardwareCleanupBefore, "maintenance_photos"), viewModel.getLocalFilePathForUrl(context, hardwareCleanupAfter, "maintenance_photos"), { onPhotoSlotClick("hardwareCleanup", true) }, { onPhotoSlotClick("hardwareCleanup", false) }, { hardwareCleanupBefore = null }, { hardwareCleanupAfter = null })
                        ChecklistToggleRow("Checking Drive Error", checkingDriveError, { checkingDriveError = it }, viewModel.getLocalFilePathForUrl(context, checkingDriveErrorBefore, "maintenance_photos"), viewModel.getLocalFilePathForUrl(context, checkingDriveErrorAfter, "maintenance_photos"), { onPhotoSlotClick("checkingDriveError", true) }, { onPhotoSlotClick("checkingDriveError", false) }, { checkingDriveErrorBefore = null }, { checkingDriveErrorAfter = null })
                        ChecklistToggleRow("Scanning Virus", scanningVirus, { scanningVirus = it }, viewModel.getLocalFilePathForUrl(context, scanningVirusBefore, "maintenance_photos"), viewModel.getLocalFilePathForUrl(context, scanningVirusAfter, "maintenance_photos"), { onPhotoSlotClick("scanningVirus", true) }, { onPhotoSlotClick("scanningVirus", false) }, { scanningVirusBefore = null }, { scanningVirusAfter = null })
                        ChecklistToggleRow("Checking Network", checkingNetwork, { checkingNetwork = it }, viewModel.getLocalFilePathForUrl(context, checkingNetworkBefore, "maintenance_photos"), viewModel.getLocalFilePathForUrl(context, checkingNetworkAfter, "maintenance_photos"), { onPhotoSlotClick("checkingNetwork", true) }, { onPhotoSlotClick("checkingNetwork", false) }, { checkingNetworkBefore = null }, { checkingNetworkAfter = null })
                        ChecklistToggleRow("Updating Antivirus", updatingAntivirus, { updatingAntivirus = it }, viewModel.getLocalFilePathForUrl(context, updatingAntivirusBefore, "maintenance_photos"), viewModel.getLocalFilePathForUrl(context, updatingAntivirusAfter, "maintenance_photos"), { onPhotoSlotClick("updatingAntivirus", true) }, { onPhotoSlotClick("updatingAntivirus", false) }, { updatingAntivirusBefore = null }, { updatingAntivirusAfter = null })
                        ChecklistToggleRow("Updating Aplikasi", updatingAplikasi, { updatingAplikasi = it }, viewModel.getLocalFilePathForUrl(context, updatingAplikasiBefore, "maintenance_photos"), viewModel.getLocalFilePathForUrl(context, updatingAplikasiAfter, "maintenance_photos"), { onPhotoSlotClick("updatingAplikasi", true) }, { onPhotoSlotClick("updatingAplikasi", false) }, { updatingAplikasiBefore = null }, { updatingAplikasiAfter = null })
                    }
                }
            }

            // 3. TARGET DEVICES HEADER OR EMPTY STATE
            if (selectedLocation.isBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = Color.Gray.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Pilih Lokasi Terlebih Dahulu",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = "Silakan pilih lokasi di atas untuk melihat daftar user & perangkat yang akan diperiksa.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daftar User di ${selectedLocation} (${devicesInLocation.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                if (devicesInLocation.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Tidak ada perangkat terdaftar di lokasi ${selectedLocation}.",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(devicesInLocation, key = { it.id }) { device ->
                        val deviceTechSignaturePaths = techSignatures[device.id] ?: emptyList()
                        val deviceSignaturePaths = signatures[device.id] ?: emptyList()
                        val deviceNotes = notes[device.id] ?: ""
                        val isAlreadyMaintained = viewModel.isDeviceAlreadyMaintainedInMonth(device, selectedDate)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAlreadyMaintained) Color(0xFFF7F7F7) else MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isAlreadyMaintained) 0.dp else 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (isAlreadyMaintained) {
                                    val monthName = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("id", "ID")).format(java.util.Date(selectedDate))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF2E7D32),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Sudah di-maintenance bulan $monthName.",
                                                color = Color(0xFF2E7D32),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { deviceToReset = device },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD32F2F))
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Batalkan / Reset", color = Color(0xFFD32F2F), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    val isSelected = selectedDeviceIds[device.id] ?: (deviceTechSignaturePaths.isNotEmpty() || deviceSignaturePaths.isNotEmpty())
                                    val hasSignature = deviceTechSignaturePaths.isNotEmpty() || deviceSignaturePaths.isNotEmpty()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isSelected) GreenLight.copy(alpha = 0.5f) else Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedDeviceIds[device.id] = !isSelected
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { selectedDeviceIds[device.id] = it },
                                                colors = CheckboxDefaults.colors(checkedColor = Green40)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isSelected) "Dipilih untuk Maintenance" else "Pilih Unit Ini untuk Maintenance",
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Green40 else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (hasSignature) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                                        ) {
                                            Text(
                                                text = if (hasSignature) "✓ Ada Paraf" else "Belum Diparaf",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (hasSignature) Color(0xFF2E7D32) else Color(0xFFE65100),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // User Title Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(GreenLight, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(if (device.type == "AIO") "🖥️" else "💻", fontSize = 18.sp)
                                        }
                                        Column {
                                            Text(
                                                text = device.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${device.type} - ${device.brand} (${device.serialNumber})",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    }

                                    // Condition Tag
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (device.condition == "Baik") Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = device.condition,
                                            color = if (device.condition == "Baik") Color(0xFF4CAF50) else Color(0xFFD32F2F),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                                // Paraf / Signature Petugas Area
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Paraf / Tanda Tangan Petugas:",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (deviceTechSignaturePaths.isNotEmpty()) {
                                            Text(
                                                text = "Sudah Ditandatangani",
                                                color = Color(0xFF4CAF50),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Inline Signature Pad Petugas
                                    SignaturePad(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp),
                                        enabled = !isAlreadyMaintained,
                                        paths = deviceTechSignaturePaths,
                                        onSignatureChanged = { paths ->
                                            techSignatures[device.id] = paths
                                            selectedDeviceIds[device.id] = true
                                        }
                                    )
                                }

                                // Paraf / Signature User Area
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Paraf / Tanda Tangan User:",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (deviceSignaturePaths.isNotEmpty()) {
                                            Text(
                                                text = "Sudah Ditandatangani",
                                                color = Color(0xFF4CAF50),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Inline Signature Pad User
                                    SignaturePad(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp),
                                        enabled = !isAlreadyMaintained,
                                        paths = deviceSignaturePaths,
                                        onSignatureChanged = { paths ->
                                            signatures[device.id] = paths
                                            selectedDeviceIds[device.id] = true
                                        }
                                    )
                                }

                                // Keterangan / Notes Input
                                OutlinedTextField(
                                    value = deviceNotes,
                                    onValueChange = { notes[device.id] = it },
                                    enabled = !isAlreadyMaintained,
                                    label = { Text("Keterangan Pemeriksaan User") },
                                    placeholder = { Text("Misal: Selesai maintenance, lancar...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Green40,
                                        focusedLabelColor = Green40
                                    )
                                )

                                if (!isAlreadyMaintained) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                if (deviceTechSignaturePaths.isEmpty() && deviceSignaturePaths.isEmpty()) {
                                                    Toast.makeText(context, "Harap isi paraf petugas atau paraf user terlebih dahulu untuk ${device.name}", Toast.LENGTH_LONG).show()
                                                    return@OutlinedButton
                                                }

                                                val backgroundTechName = if (technicians.isNotEmpty()) technicians.first().name else "Hendi Rahmadi"
                                                val devTechSignatureStr = if (deviceTechSignaturePaths.isNotEmpty()) serializePaths(deviceTechSignaturePaths) else ""
                                                val devSignatureStr = if (deviceSignaturePaths.isNotEmpty()) serializePaths(deviceSignaturePaths) else ""

                                                val checklistSummary = mutableListOf<String>()
                                                if (healthReport) checklistSummary.add("Health Report")
                                                if (diskCleanup) checklistSummary.add("Disk CleanUp")
                                                if (hardwareCleanup) checklistSummary.add("Hardware CleanUp")
                                                if (checkingDriveError) checklistSummary.add("Checking Drive Error")
                                                if (scanningVirus) checklistSummary.add("Scanning Virus")
                                                if (checkingNetwork) checklistSummary.add("Checking Network")
                                                if (updatingAntivirus) checklistSummary.add("Updating Antivirus")
                                                if (updatingAplikasi) checklistSummary.add("Updating Aplikasi")

                                                val compiledAction = buildString {
                                                    append("Pemeriksaan Satuan Lokasi ${selectedLocation}:\n")
                                                    if (checklistSummary.isEmpty()) {
                                                        append("- Tidak ada tindakan dipilih\n")
                                                    } else {
                                                        checklistSummary.forEach { append("- $it\n") }
                                                    }
                                                    if (deviceNotes.isNotBlank()) {
                                                        append("\nKeterangan Spesifik:\n")
                                                        append(deviceNotes)
                                                    }
                                                }

                                                viewModel.performDirectMaintenance(
                                                    device = device,
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
                                                    windowsLicense = "YA",
                                                    officeLicense = "YA",
                                                    notes = deviceNotes,
                                                    signatureData = devSignatureStr,
                                                    techSignatureData = devTechSignatureStr,
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
                                                    updatingAplikasiAfterPhoto = updatingAplikasiAfter,
                                                    onComplete = {
                                                        Toast.makeText(context, "Maintenance untuk ${device.name} berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Green40)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Simpan Unit Ini Saja", fontSize = 12.sp, color = Green40, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. SUBMIT ALL BUTTON
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (selectedLocation.isBlank()) {
                                    Toast.makeText(context, "Harap pilih lokasi terlebih dahulu", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (devicesInLocation.isEmpty()) {
                                    Toast.makeText(context, "Tidak ada perangkat untuk diproses di lokasi ini", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                // Process batch maintenance saving
                                val backgroundTechName = if (technicians.isNotEmpty()) technicians.first().name else "Hendi Rahmadi"

                                val alreadyMaintained = mutableListOf<Device>()
                                val candidates = mutableListOf<Device>()

                                devicesInLocation.forEach { dev ->
                                    if (viewModel.isDeviceAlreadyMaintainedInMonth(dev, selectedDate)) {
                                        alreadyMaintained.add(dev)
                                    } else {
                                        candidates.add(dev)
                                    }
                                }

                                // Only process devices that are checked OR have signatures!
                                val toMaintain = candidates.filter { dev ->
                                    selectedDeviceIds[dev.id] == true ||
                                    (signatures[dev.id]?.isNotEmpty() == true) ||
                                    (techSignatures[dev.id]?.isNotEmpty() == true)
                                }

                                val monthName = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("id", "ID")).format(java.util.Date(selectedDate))

                                if (toMaintain.isEmpty()) {
                                    if (alreadyMaintained.isNotEmpty() && candidates.isEmpty()) {
                                        Toast.makeText(context, "Semua perangkat di lokasi ini sudah di-maintenance pada bulan $monthName!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Tidak ada perangkat yang dipilih atau diparaf untuk disimpan. Harap berikan paraf pada unit yang telah dirawat.", Toast.LENGTH_LONG).show()
                                    }
                                    return@Button
                                }

                                val batchItems = toMaintain.mapIndexed { index, dev ->
                                    val isFirstDevice = index == 0
                                    val devNotes = notes[dev.id] ?: ""
                                    val devTechSigs = techSignatures[dev.id] ?: emptyList()
                                    val devSigs = signatures[dev.id] ?: emptyList()
                                    val techSignatureDataStr = if (devTechSigs.isNotEmpty()) serializePaths(devTechSigs) else ""
                                    val signatureDataStr = if (devSigs.isNotEmpty()) serializePaths(devSigs) else ""

                                    // Build descriptive summary Action Taken text
                                    val checklistSummary = mutableListOf<String>()
                                    if (healthReport) checklistSummary.add("Health Report")
                                    if (diskCleanup) checklistSummary.add("Disk CleanUp")
                                    if (hardwareCleanup) checklistSummary.add("Hardware CleanUp")
                                    if (checkingDriveError) checklistSummary.add("Checking Drive Error")
                                    if (scanningVirus) checklistSummary.add("Scanning Virus")
                                    if (checkingNetwork) checklistSummary.add("Checking Network")
                                    if (updatingAntivirus) checklistSummary.add("Updating Antivirus")
                                    if (updatingAplikasi) checklistSummary.add("Updating Aplikasi")

                                    val compiledAction = buildString {
                                        append("Pemeriksaan Massal:\n")
                                        if (checklistSummary.isEmpty()) {
                                             append("- Tidak ada tindakan dipilih\n")
                                        } else {
                                             checklistSummary.forEach { append("- $it\n") }
                                        }
                                        if (devNotes.isNotBlank()) {
                                             append("\nKeterangan Spesifik:\n")
                                             append(devNotes)
                                        }
                                    }

                                    MaintenanceViewModel.DirectMaintenanceItem(
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
                                        windowsLicense = "YA",
                                        officeLicense = "YA",
                                        notes = devNotes,
                                        signatureData = signatureDataStr,
                                        techSignatureData = techSignatureDataStr,
                                        timestamp = selectedDate,
                                        healthReportBeforePhoto = if (isFirstDevice) healthReportBefore else null,
                                        healthReportAfterPhoto = if (isFirstDevice) healthReportAfter else null,
                                        diskCleanupBeforePhoto = if (isFirstDevice) diskCleanupBefore else null,
                                        diskCleanupAfterPhoto = if (isFirstDevice) diskCleanupAfter else null,
                                        hardwareCleanupBeforePhoto = if (isFirstDevice) hardwareCleanupBefore else null,
                                        hardwareCleanupAfterPhoto = if (isFirstDevice) hardwareCleanupAfter else null,
                                        checkingDriveErrorBeforePhoto = if (isFirstDevice) checkingDriveErrorBefore else null,
                                        checkingDriveErrorAfterPhoto = if (isFirstDevice) checkingDriveErrorAfter else null,
                                        scanningVirusBeforePhoto = if (isFirstDevice) scanningVirusBefore else null,
                                        scanningVirusAfterPhoto = if (isFirstDevice) scanningVirusAfter else null,
                                        checkingNetworkBeforePhoto = if (isFirstDevice) checkingNetworkBefore else null,
                                        checkingNetworkAfterPhoto = if (isFirstDevice) checkingNetworkAfter else null,
                                        updatingAntivirusBeforePhoto = if (isFirstDevice) updatingAntivirusBefore else null,
                                        updatingAntivirusAfterPhoto = if (isFirstDevice) updatingAntivirusAfter else null,
                                        updatingAplikasiBeforePhoto = if (isFirstDevice) updatingAplikasiBefore else null,
                                        updatingAplikasiAfterPhoto = if (isFirstDevice) updatingAplikasiAfter else null
                                    )
                                }

                                viewModel.performBatchDirectMaintenance(batchItems) {
                                    Toast.makeText(context, "Berhasil menyimpan ${toMaintain.size} data maintenance massal!", Toast.LENGTH_LONG).show()
                                    onNavigateBack()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Green40),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_submit_batch_maintenance"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Simpan Maintenance Massal", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ChecklistToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    beforeUri: String? = null,
    afterUri: String? = null,
    onBeforeClick: (() -> Unit)? = null,
    onAfterClick: (() -> Unit)? = null,
    onClearBefore: (() -> Unit)? = null,
    onClearAfter: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = Green40)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (checked && onBeforeClick != null && onAfterClick != null && onClearBefore != null && onClearAfter != null) {
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

@Composable
private fun SignaturePad(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    paths: List<List<Offset>> = emptyList(),
    onSignatureChanged: (List<List<Offset>>) -> Unit
) {
    val completedPaths = remember { mutableStateListOf<List<Offset>>() }
    val currentPath = remember { mutableStateListOf<Offset>() }

    LaunchedEffect(paths) {
        if (completedPaths.toList() != paths) {
            completedPaths.clear()
            completedPaths.addAll(paths)
        }
    }

    Box(
        modifier = modifier
            .background(if (enabled) Color(0xFFF9F9F9) else Color(0xFFEFEFEF), shape = RoundedCornerShape(12.dp))
            .border(1.dp, Color.Gray.copy(alpha = if (enabled) 0.25f else 0.15f), shape = RoundedCornerShape(12.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (enabled) {
                        Modifier.pointerInput(Unit) {
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
                    } else {
                        Modifier
                    }
                )
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
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
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
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        // Clear button
        if (enabled) {
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
                    tint = Color.Red.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
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
