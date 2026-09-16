package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.data.TroubleTicket
import com.example.ui.MaintenanceViewModel
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.Green40
import com.example.ui.theme.GreenLight
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun TroubleScreen(
    viewModel: MaintenanceViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tickets by viewModel.troubleTickets.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val technicians by viewModel.technicians.collectAsState()

    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    var showReportDialog by remember { mutableStateOf(false) }
    var showBarcodeScannerInTroubleForm by remember { mutableStateOf(false) }
    var selectedTicketForResolve by remember { mutableStateOf<TroubleTicket?>(null) }

    // New Ticket state variables
    var reportDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    var ticketDescription by remember { mutableStateOf("") }
    var reportedBy by remember { mutableStateOf("Staf") }
    var ticketActionTaken by remember { mutableStateOf("") }
    var ticketDuration by remember { mutableStateOf("") }
    var photoBefore by remember { mutableStateOf<String?>(null) }
    var photoAfter by remember { mutableStateOf<String?>(null) }
    var ticketStatus by remember { mutableStateOf("Pending") }

    // Resolve state variables
    var selectedTechnician by remember { mutableStateOf<Technician?>(null) }
    var actionTaken by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var resolvePhotoBefore by remember { mutableStateOf<String?>(null) }
    var resolvePhotoAfter by remember { mutableStateOf<String?>(null) }

    // Media capturing state
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var activeCaptureIsBefore by remember { mutableStateOf(true) } // true: before, false: after
    var isCapturingForResolve by remember { mutableStateOf(false) } // true: resolve form, false: report form
    var showPhotoSourceDialog by remember { mutableStateOf(false) }

    // Duration Picker state variables
    var showDurationPicker by remember { mutableStateOf(false) }
    var isPickerForReportForm by remember { mutableStateOf(true) }
    var pickedHours by remember { mutableStateOf(0) }
    var pickedMinutes by remember { mutableStateOf(30) }
    val scope = rememberCoroutineScope()

    val updateTroublePhotoState = { isResolve: Boolean, isBefore: Boolean, uriStr: String ->
        if (isResolve) {
            if (isBefore) resolvePhotoBefore = uriStr else resolvePhotoAfter = uriStr
        } else {
            if (isBefore) photoBefore = uriStr else photoAfter = uriStr
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            val savedUriStr = saveUriToInternalStorage(context, tempPhotoUri!!) ?: tempPhotoUri.toString()
            val isResolve = isCapturingForResolve
            val isBefore = activeCaptureIsBefore
            updateTroublePhotoState(isResolve, isBefore, savedUriStr)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedUriStr = saveUriToInternalStorage(context, uri) ?: uri.toString()
            val isResolve = isCapturingForResolve
            val isBefore = activeCaptureIsBefore
            updateTroublePhotoState(isResolve, isBefore, savedUriStr)
        }
    }

    val onPhotoSlotClick = { isBefore: Boolean, isResolve: Boolean ->
        activeCaptureIsBefore = isBefore
        isCapturingForResolve = isResolve
        showPhotoSourceDialog = true
    }

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text("Pilih Sumber Foto", fontWeight = FontWeight.Bold) },
            text = { Text("Silakan pilih kamera untuk mengambil foto secara langsung, atau galeri untuk memilih foto yang sudah ada.") },
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
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Kamera")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPhotoSourceDialog = false
                    galleryLauncher.launch("image/*")
                }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Galeri")
                    }
                }
            }
        )
    }

    if (showDurationPicker) {
        AlertDialog(
            onDismissRequest = { showDurationPicker = false },
            title = {
                Text(
                    text = "Pilih Durasi Tindakan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hours selector
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Jam", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { if (pickedHours > 0) pickedHours-- },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Kurang Jam",
                                        tint = AccentOrange
                                    )
                                }
                                Text(
                                    text = "$pickedHours",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(30.dp),
                                    textAlign = TextAlign.Center
                                )
                                IconButton(
                                    onClick = { if (pickedHours < 23) pickedHours++ },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Tambah Jam",
                                        tint = AccentOrange
                                    )
                                }
                            }
                        }

                        // Divider
                        Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold)

                        // Minutes selector
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Menit", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { 
                                        if (pickedMinutes >= 5) {
                                            pickedMinutes -= 5
                                        } else {
                                            pickedMinutes = 55
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Kurang Menit",
                                        tint = AccentOrange
                                    )
                                }
                                Text(
                                    text = "$pickedMinutes",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(30.dp),
                                    textAlign = TextAlign.Center
                                )
                                IconButton(
                                    onClick = { 
                                        if (pickedMinutes <= 50) {
                                            pickedMinutes += 5
                                        } else {
                                            pickedMinutes = 0
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Tambah Menit",
                                        tint = AccentOrange
                                    )
                                }
                            }
                        }
                    }
                    
                    // Quick select chips
                    Text("Pilihan Cepat:", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Start))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 45).forEach { mins ->
                            Box(
                                modifier = Modifier
                                    .border(1.dp, AccentOrange, RoundedCornerShape(16.dp))
                                    .background(AccentOrange.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                    .clickable {
                                        pickedHours = 0
                                        pickedMinutes = mins
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("$mins Menit", fontSize = 11.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
                            }
                        }
                        listOf(1, 2).forEach { hrs ->
                            Box(
                                modifier = Modifier
                                    .border(1.dp, AccentOrange, RoundedCornerShape(16.dp))
                                    .background(AccentOrange.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                    .clickable {
                                        pickedHours = hrs
                                        pickedMinutes = 0
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("$hrs Jam", fontSize = 11.sp, color = AccentOrange, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val formattedDuration = if (pickedHours == 0) {
                            "$pickedMinutes menit"
                        } else if (pickedMinutes == 0) {
                            "$pickedHours jam"
                        } else {
                            "$pickedHours jam $pickedMinutes menit"
                        }
                        
                        if (isPickerForReportForm) {
                            ticketDuration = formattedDuration
                        } else {
                            duration = formattedDuration
                        }
                        showDurationPicker = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                ) {
                    Text("Pilih")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDurationPicker = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showBarcodeScannerInTroubleForm) {
        val scannerContext = LocalContext.current

        // Laser Animation setup for scanning simulator
        val infiniteTransition = rememberInfiniteTransition(label = "trouble_laser")
        val laserYOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 200f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "trouble_laserOffset"
        )

        // Request permission automatically on dialog launch
        LaunchedEffect(Unit) {
            if (!cameraPermissionState.status.isGranted) {
                cameraPermissionState.launchPermissionRequest()
            }
        }

        var tempBarcode by remember { mutableStateOf("") }
        var isScanned by remember { mutableStateOf(false) }

        // Scan success function
        fun triggerScanSuccess(scannedBarcode: String) {
            val found = devices.find { it.serialNumber.trim().equals(scannedBarcode.trim(), ignoreCase = true) }
            if (found != null) {
                selectedDevice = found
                Toast.makeText(scannerContext, "Perangkat Ditemukan: ${found.name}", Toast.LENGTH_SHORT).show()
                showBarcodeScannerInTroubleForm = false
            } else {
                Toast.makeText(scannerContext, "Barcode $scannedBarcode tidak terdaftar!", Toast.LENGTH_LONG).show()
            }
        }

        AlertDialog(
            onDismissRequest = { showBarcodeScannerInTroubleForm = false },
            title = {
                Text(
                    text = "Scanning Barcode Perangkat...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (cameraPermissionState.status.isGranted)
                            "Arahkan kamera ke barcode perangkat..."
                        else
                            "Izin kamera diperlukan untuk melakukan scan barcode perangkat.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )

                    // Scanner Viewport
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .border(4.dp, AccentOrange, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cameraPermissionState.status.isGranted) {
                            CameraPreview(
                                modifier = Modifier.fillMaxSize(),
                                onBarcodeDetected = { scannedBarcode ->
                                    tempBarcode = scannedBarcode
                                    isScanned = true
                                    triggerScanSuccess(scannedBarcode)
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
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    "Kamera Nonaktif",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                                Button(
                                    onClick = { cameraPermissionState.launchPermissionRequest() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
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
                                color = AccentOrange,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                    }

                    // Scanned value editor input
                    OutlinedTextField(
                        value = tempBarcode,
                        onValueChange = { tempBarcode = it },
                        label = { Text("ID Barcode Terdeteksi") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            focusedLabelColor = AccentOrange
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (cameraPermissionState.status.isGranted) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isScanned) {
                                CircularProgressIndicator(
                                    color = AccentOrange,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Mencari barcode...",
                                    fontSize = 12.sp,
                                    color = AccentOrange,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Terdeteksi",
                                    tint = AccentOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Barcode berhasil dideteksi!",
                                    fontSize = 12.sp,
                                    color = AccentOrange,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { showBarcodeScannerInTroubleForm = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal", color = Color.Gray)
                    }
                    Button(
                        onClick = {
                            if (tempBarcode.isNotBlank()) {
                                triggerScanSuccess(tempBarcode)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Gunakan", color = Color.White)
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tiket Trouble & Aduan", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green40)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Reset report states
                    reportDate = System.currentTimeMillis()
                    selectedDevice = if (devices.any { it.condition == "Trouble" }) {
                        devices.firstOrNull { it.condition == "Trouble" }
                    } else {
                        devices.firstOrNull()
                    }
                    ticketDescription = ""
                    reportedBy = "Staf"
                    ticketActionTaken = ""
                    ticketDuration = ""
                    photoBefore = null
                    photoAfter = null
                    ticketStatus = "Pending"
                    showReportDialog = true
                },
                containerColor = AccentOrange,
                contentColor = Color.White,
                modifier = Modifier.testTag("report_trouble_fab")
            ) {
                Icon(imageVector = Icons.Default.Warning, contentDescription = "Laporkan Trouble")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Stats Row inside Trouble Screen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Sisa Trouble", fontSize = 11.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                        Text(
                            text = tickets.count { it.status == "Pending" }.toString(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFC62828)
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = GreenLight)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Selesai Ditangani", fontSize = 11.sp, color = Green40, fontWeight = FontWeight.Bold)
                        Text(
                            text = tickets.count { it.status == "Selesai" }.toString(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Green40
                        )
                    }
                }
            }

            if (tickets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "No trouble",
                            tint = Color(0xFF4CAF50).copy(alpha = 0.3f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Semua komputer beroperasi normal!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(tickets, key = { it.id }) { ticket ->
                        val dev = devices.find { it.id == ticket.deviceId }
                        TroubleTicketCard(
                            ticket = ticket,
                            device = dev,
                            viewModel = viewModel,
                            onResolve = {
                                selectedTicketForResolve = ticket
                                selectedTechnician = technicians.firstOrNull()
                                actionTaken = ""
                                duration = ""
                                resolvePhotoBefore = ticket.photoBefore
                                resolvePhotoAfter = ticket.photoAfter
                            },
                            onDelete = { viewModel.deleteTroubleTicket(ticket.id) }
                        )
                    }
                }
            }
        }
    }

    // Report Trouble Dialog
    if (showReportDialog) {
        var devExpanded by remember { mutableStateOf(false) }
        val reportScrollState = rememberScrollState()

        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Form Laporan Kerusakan / Trouble", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(reportScrollState)
                ) {
                    // Date picker field
                    val calendar = Calendar.getInstance().apply { timeInMillis = reportDate }
                    val datePickerDialog = android.app.DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val newCal = Calendar.getInstance()
                            newCal.set(year, month, dayOfMonth)
                            reportDate = newCal.timeInMillis
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )

                    val formattedDate = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date(reportDate))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = formattedDate,
                            onValueChange = {},
                            label = { Text("Pilih Tanggal") },
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

                    // Scan Barcode / Device selector
                    Text("Pilih Perangkat (Scan Barcode):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = selectedDevice?.let { "[${it.serialNumber}] - ${it.name}" } ?: "Scan/Pilih Perangkat...",
                                onValueChange = {},
                                label = { Text("Barcode / Nama Perangkat") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { devExpanded = !devExpanded }) {
                                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { devExpanded = !devExpanded }
                                    .testTag("select_device_dropdown")
                            )

                            DropdownMenu(
                                expanded = devExpanded,
                                onDismissRequest = { devExpanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                devices.forEach { dev ->
                                    DropdownMenuItem(
                                        text = { Text("[${dev.serialNumber}] - ${dev.name} (${dev.type})") },
                                        onClick = {
                                            selectedDevice = dev
                                            devExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { showBarcodeScannerInTroubleForm = true },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan Barcode",
                                tint = AccentOrange
                            )
                        }
                    }

                    // Auto-populated fields if device is selected
                    selectedDevice?.let { dev ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = GreenLight.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Detail Perangkat (Otomatis):", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Green40)
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Nama User:", fontSize = 11.sp, color = Color.Gray)
                                    Text(dev.name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Tipe Perangkat:", fontSize = 11.sp, color = Color.Gray)
                                    Text(dev.type, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Lokasi:", fontSize = 11.sp, color = Color.Gray)
                                    Text(dev.description.ifBlank { "N/A" }, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Serial Number (SN):", fontSize = 11.sp, color = Color.Gray)
                                    Text(dev.sn.ifBlank { "N/A" }, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = ticketDescription,
                        onValueChange = { ticketDescription = it },
                        label = { Text("Deskripsi Kerusakan") },
                        placeholder = { Text("Contoh: Layar monitor mati, CPU berbunyi keras") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .testTag("input_trouble_desc")
                    )

                    OutlinedTextField(
                        value = ticketActionTaken,
                        onValueChange = { ticketActionTaken = it },
                        label = { Text("Tindakan (Opsional jika Selesai)") },
                        placeholder = { Text("Langkah perbaikan yang diambil") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    )

                    OutlinedTextField(
                        value = ticketDuration,
                        onValueChange = {},
                        label = { Text("Durasi Tindakan (Klik untuk Memilih)") },
                        placeholder = { Text("Pilih durasi...") },
                        singleLine = true,
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isPickerForReportForm = true
                                pickedHours = 0
                                pickedMinutes = 30
                                showDurationPicker = true
                            },
                        trailingIcon = {
                            IconButton(onClick = {
                                isPickerForReportForm = true
                                pickedHours = 0
                                pickedMinutes = 30
                                showDurationPicker = true
                            }) {
                                Icon(imageVector = Icons.Default.AccessTime, contentDescription = "Pilih Durasi")
                            }
                        }
                    )

                    // Before and After Photos
                    Text("Unggah Foto Laporan:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // BEFORE Photo
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Foto Sebelum", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Gray.copy(alpha = 0.05f))
                                    .clickable { onPhotoSlotClick(true, false) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (photoBefore != null) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        val resolvedPath = viewModel.getLocalFilePathForUrl(context, photoBefore, "maintenance_photos")
                                        if (resolvedPath != null) {
                                            AsyncImage(
                                                model = resolvedPath,
                                                contentDescription = "Foto Sebelum",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(Color(0xFFFFEBEE)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "content:// lama",
                                                    color = Color.Red,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { photoBefore = null },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(24.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = "Pilih Foto", tint = Color.Gray, modifier = Modifier.size(24.dp))
                                        Text("Unggah", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }

                        // AFTER Photo
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Foto Sesudah", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Gray.copy(alpha = 0.05f))
                                    .clickable { onPhotoSlotClick(false, false) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (photoAfter != null) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        val resolvedPath = viewModel.getLocalFilePathForUrl(context, photoAfter, "maintenance_photos")
                                        if (resolvedPath != null) {
                                            AsyncImage(
                                                model = resolvedPath,
                                                contentDescription = "Foto Sesudah",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(Color(0xFFFFEBEE)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "content:// lama",
                                                    color = Color.Red,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { photoAfter = null },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(24.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = "Pilih Foto", tint = Color.Gray, modifier = Modifier.size(24.dp))
                                        Text("Unggah", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }

                    // Status Switch
                    Text("Status Tiket Aduan:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = ticketStatus == "Pending",
                            onClick = { ticketStatus = "Pending" },
                            label = { Text("PENDING") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFF3E0),
                                selectedLabelColor = AccentOrange
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = ticketStatus == "Selesai",
                            onClick = { ticketStatus = "Selesai" },
                            label = { Text("SELESAI") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GreenLight,
                                selectedLabelColor = Green40
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val dev = selectedDevice
                        if (dev != null && ticketDescription.isNotBlank() && reportedBy.isNotBlank()) {
                            viewModel.addTroubleTicket(
                                deviceId = dev.id,
                                deviceName = dev.name,
                                description = ticketDescription,
                                reportedBy = reportedBy,
                                status = ticketStatus,
                                timestamp = reportDate,
                                actionTaken = ticketActionTaken,
                                duration = ticketDuration,
                                photoBefore = photoBefore,
                                photoAfter = photoAfter
                            )
                            // Reset state
                            ticketDescription = ""
                            reportedBy = "Staf"
                            ticketActionTaken = ""
                            ticketDuration = ""
                            photoBefore = null
                            photoAfter = null
                            selectedDevice = null
                            showReportDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    modifier = Modifier.testTag("save_trouble_button")
                ) {
                    Text("Kirim Laporan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Resolve Trouble Dialog (Tindak Lanjut)
    selectedTicketForResolve?.let { ticket ->
        val resolveScrollState = rememberScrollState()

        AlertDialog(
            onDismissRequest = { selectedTicketForResolve = null },
            title = { Text("Tindak Lanjut & Selesaikan", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(resolveScrollState)
                ) {
                    Text("Perangkat: ${ticket.deviceName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Kendala: \"${ticket.description}\"", color = Color.Red, fontSize = 12.sp)

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    OutlinedTextField(
                        value = actionTaken,
                        onValueChange = { actionTaken = it },
                        label = { Text("Tindakan / Solusi") },
                        placeholder = { Text("Contoh: Melakukan pembersihan RAM, install ulang driver VGA") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .testTag("input_action_taken")
                    )

                    OutlinedTextField(
                        value = duration,
                        onValueChange = {},
                        label = { Text("Durasi Tindakan (Klik untuk Memilih)") },
                        placeholder = { Text("Pilih durasi...") },
                        singleLine = true,
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isPickerForReportForm = false
                                pickedHours = 0
                                pickedMinutes = 30
                                showDurationPicker = true
                            },
                        trailingIcon = {
                            IconButton(onClick = {
                                isPickerForReportForm = false
                                pickedHours = 0
                                pickedMinutes = 30
                                showDurationPicker = true
                            }) {
                                Icon(imageVector = Icons.Default.AccessTime, contentDescription = "Pilih Durasi")
                            }
                        }
                    )

                    // Before and After Photos inside Resolve Dialog
                    Text("Unggah Foto Perbaikan:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // BEFORE Photo
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Foto Sebelum", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Gray.copy(alpha = 0.05f))
                                    .clickable { onPhotoSlotClick(true, true) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (resolvePhotoBefore != null) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        val resolvedPath = viewModel.getLocalFilePathForUrl(context, resolvePhotoBefore, "maintenance_photos")
                                        if (resolvedPath != null) {
                                            AsyncImage(
                                                model = resolvedPath,
                                                contentDescription = "Foto Sebelum",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(Color(0xFFFFEBEE)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "content:// lama",
                                                    color = Color.Red,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { resolvePhotoBefore = null },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(24.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = "Pilih Foto", tint = Color.Gray, modifier = Modifier.size(24.dp))
                                        Text("Unggah", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }

                        // AFTER Photo
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Foto Sesudah", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Gray.copy(alpha = 0.05f))
                                    .clickable { onPhotoSlotClick(false, true) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (resolvePhotoAfter != null) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        val resolvedPath = viewModel.getLocalFilePathForUrl(context, resolvePhotoAfter, "maintenance_photos")
                                        if (resolvedPath != null) {
                                            AsyncImage(
                                                model = resolvedPath,
                                                contentDescription = "Foto Sesudah",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(Color(0xFFFFEBEE)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "content:// lama",
                                                    color = Color.Red,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { resolvePhotoAfter = null },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(24.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = "Pilih Foto", tint = Color.Gray, modifier = Modifier.size(24.dp))
                                        Text("Unggah", fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (actionTaken.isNotBlank()) {
                            val techName = selectedTechnician?.name
                                ?: technicians.firstOrNull()?.name
                                ?: "Petugas Maintenance"
                            viewModel.resolveTroubleTicket(
                                ticket = ticket,
                                technicianName = techName,
                                actionTaken = actionTaken,
                                duration = duration,
                                photoBefore = resolvePhotoBefore,
                                photoAfter = resolvePhotoAfter
                            )
                            // Reset state
                            selectedTechnician = null
                            actionTaken = ""
                            duration = ""
                            resolvePhotoBefore = null
                            resolvePhotoAfter = null
                            selectedTicketForResolve = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Green40),
                    modifier = Modifier.testTag("resolve_trouble_confirm")
                ) {
                    Text("Selesaikan")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedTicketForResolve = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun TroubleTicketCard(
    ticket: TroubleTicket,
    device: Device?,
    viewModel: MaintenanceViewModel,
    onResolve: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(ticket.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ticket.status == "Pending") Color(0xFFFFFDE7) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = if (ticket.status == "Pending") Color(0xFFFFEB3B).copy(alpha = 0.2f) else GreenLight,
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (ticket.status == "Pending") Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = "Status",
                            tint = if (ticket.status == "Pending") AccentOrange else Green40,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = ticket.deviceName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Dilaporkan: $date",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus aduan",
                            tint = Color.Red.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle detail",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))

                // Display Device Details (Nama User, Tipe, Lokasi, SN)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Nama User:", fontSize = 11.sp, color = Color.Gray)
                        Text(device?.name ?: ticket.deviceName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Tipe Perangkat:", fontSize = 11.sp, color = Color.Gray)
                        Text(device?.type ?: "-", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Lokasi:", fontSize = 11.sp, color = Color.Gray)
                        Text(device?.description ?: "-", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("SN:", fontSize = 11.sp, color = Color.Gray)
                        Text(device?.sn ?: "-", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Kerusakan / Detail:",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = ticket.description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 2.dp)
            )

            // Tindakan & Durasi
            if (ticket.actionTaken.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tindakan / Solusi:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = ticket.actionTaken,
                    fontSize = 13.sp,
                    color = Green40,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (ticket.duration.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                    Text(
                        text = "Durasi Tindakan: ${ticket.duration}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Before and After Photos if present
            if (!ticket.photoBefore.isNullOrBlank() || !ticket.photoAfter.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ticket.photoBefore?.let { beforeUrl ->
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Sebelum", fontSize = 10.sp, color = Color.Gray)
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                            ) {
                                val resolvedPath = viewModel.getLocalFilePathForUrl(context, beforeUrl, "maintenance_photos")
                                if (resolvedPath != null) {
                                    AsyncImage(
                                        model = resolvedPath,
                                        contentDescription = "Foto Sebelum",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color(0xFFFFEBEE)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "content:// lama",
                                            color = Color.Red,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ticket.photoAfter?.let { afterUrl ->
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Sesudah", fontSize = 10.sp, color = Color.Gray)
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                            ) {
                                val resolvedPath = viewModel.getLocalFilePathForUrl(context, afterUrl, "maintenance_photos")
                                if (resolvedPath != null) {
                                    AsyncImage(
                                        model = resolvedPath,
                                        contentDescription = "Foto Sesudah",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color(0xFFFFEBEE)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "content:// lama",
                                            color = Color.Red,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Person, contentDescription = "Pelapor", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                Text(
                    text = "Pelapor: ${ticket.reportedBy}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (ticket.status == "Pending") Color(0xFFFFF3E0) else GreenLight
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (ticket.status == "Pending") "PENDING" else "SELESAI",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (ticket.status == "Pending") AccentOrange else Green40
                    )
                }

                // Action button
                if (ticket.status == "Pending") {
                    Button(
                        onClick = onResolve,
                        colors = ButtonDefaults.buttonColors(containerColor = Green40),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Build, contentDescription = "Resolve", tint = Color.White, modifier = Modifier.size(12.dp))
                            Text("Tindak Lanjut", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
}

private fun createHighQualityPhotoUri(context: android.content.Context): Uri? {
    return try {
        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = java.io.File(cachePath, "captured_trouble_${System.currentTimeMillis()}.jpg")
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
        val cachePath = java.io.File(context.filesDir, "trouble_photos")
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
