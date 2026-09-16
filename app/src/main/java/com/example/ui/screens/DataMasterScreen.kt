package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.example.data.Device
import com.example.ui.MaintenanceViewModel
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.Green40
import com.example.ui.theme.GreenLight

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DataMasterScreen(
    viewModel: MaintenanceViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val devices by viewModel.devices.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0: Semua, 1: AIO, 2: Laptop
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedDetailDevice by remember { mutableStateOf<Device?>(null) }
    var selectedEditDevice by remember { mutableStateOf<Device?>(null) }

    // Dialog state
    var type by remember { mutableStateOf("AIO") }
    var name by remember { mutableStateOf("") } // Nama user
    var brand by remember { mutableStateOf("") } // Tipe & Merek perangkat
    var serialNumber by remember { mutableStateOf("") } // Barcode ID (Otomatis)
    var description by remember { mutableStateOf("") } // Lokasi
    var sn by remember { mutableStateOf("") } // SN
    var photoUri by remember { mutableStateOf<String?>(null) } // Foto fisik
    var baFileUri by remember { mutableStateOf<String?>(null) } // File BA URI (Opsional)
    var baFileName by remember { mutableStateOf<String?>(null) } // Nama File BA
    var showBarcodeScannerInForm by remember { mutableStateOf(false) }

    // Automatically generate barcode when dialog opens or type changes
    LaunchedEffect(showAddDialog, type) {
        if (showAddDialog) {
            val prefix = if (type == "AIO") "BC-AIO" else "BC-LAP"
            val rand = (10000..99999).random()
            serialNumber = "$prefix-$rand"
        }
    }

    val filteredDevices = devices.filter { device ->
        // Search filter
        val matchesSearch = device.name.contains(searchQuery, ignoreCase = true) || 
                            device.brand.contains(searchQuery, ignoreCase = true) ||
                            device.serialNumber.contains(searchQuery, ignoreCase = true) ||
                            device.sn.contains(searchQuery, ignoreCase = true)
        
        // Tab filter
        val matchesTab = when (selectedTab) {
            1 -> device.type == "AIO"
            2 -> device.type == "Laptop"
            else -> true
        }

        matchesSearch && matchesTab
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Master Perangkat", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val prefs = context.getSharedPreferences("supabase_r2_prefs", android.content.Context.MODE_PRIVATE)
                            val url = prefs.getString("supabase_url", "") ?: ""
                            val key = prefs.getString("supabase_anon_key", "") ?: ""
                            val worker = prefs.getString("cloudflare_worker_url", "") ?: ""
                            scope.launch {
                                Toast.makeText(context, "Menyinkronkan data perangkat...", Toast.LENGTH_SHORT).show()
                                if (url.isNotBlank() && key.isNotBlank()) {
                                    val res = viewModel.syncDatabaseWithSupabase(url, key, worker)
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "Perangkat tersinkronisasi!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Gagal: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Supabase belum dikonfigurasi di menu Profil", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = "Sinkronkan", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green40)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Green40,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_device_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Perangkat")
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
            // Search Bar & Filters
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Green40)
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari nama perangkat / serial...", color = Color.White.copy(alpha = 0.7f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    ),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari", tint = Color.White) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_field_device")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable/Fixed Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color.White
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Semua (${devices.size})", fontWeight = FontWeight.Bold, color = Color.White) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("AIO (${devices.count { it.type == "AIO" }})", fontWeight = FontWeight.Bold, color = Color.White) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Laptop (${devices.count { it.type == "Laptop" }})", fontWeight = FontWeight.Bold, color = Color.White) }
                    )
                }
            }

            // List of Devices
            if (filteredDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = "Empty",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tidak ada perangkat ditemukan",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Silakan tambah perangkat baru dengan tombol +",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    Toast.makeText(context, "Memeriksa & memulihkan data perangkat...", Toast.LENGTH_SHORT).show()
                                    val prefs = context.getSharedPreferences("supabase_r2_prefs", android.content.Context.MODE_PRIVATE)
                                    val url = prefs.getString("supabase_url", "") ?: ""
                                    val key = prefs.getString("supabase_anon_key", "") ?: ""
                                    val worker = prefs.getString("cloudflare_worker_url", "") ?: ""
                                    val sheetPrefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                                    val webAppUrl = sheetPrefs.getString("web_app_url", "") ?: ""

                                    if (url.isNotBlank() && key.isNotBlank()) {
                                        val res = viewModel.syncDatabaseWithSupabase(url, key, worker)
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "Data berhasil disinkronkan dari Supabase!", Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                    }
                                    if (webAppUrl.isNotBlank()) {
                                        viewModel.pullDataFromGoogleSheets(webAppUrl) { success, msg ->
                                            Toast.makeText(context, if (success) "Berhasil memulihkan dari Google Sheets!" else "Gagal: $msg", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Konfigurasikan Supabase atau Google Sheets di Profil untuk memulihkan data", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Green40)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pulihkan Data Perangkat", fontSize = 12.sp)
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredDevices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            onClick = { selectedDetailDevice = device },
                            onDelete = { viewModel.deleteDevice(device.id) },
                            onToggleCondition = {
                                val nextCond = if (device.condition == "Baik") "Trouble" else "Baik"
                                viewModel.updateDeviceCondition(device, nextCond)
                            }
                        )
                    }
                }
            }
        }
    }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isUploadingFile by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = viewModel.saveUriToInternalStorage(context, uri)
            photoUri = saved ?: uri.toString()
            
            scope.launch {
                isUploadingFile = true
                Toast.makeText(context, "Mengunggah foto ke Cloudflare R2...", Toast.LENGTH_SHORT).show()
                val uploadRes = viewModel.uploadUriToCloudflareR2(context, uri)
                isUploadingFile = false
                if (uploadRes.isSuccess) {
                    photoUri = uploadRes.getOrThrow()
                    Toast.makeText(context, "Foto berhasil diunggah ke Cloudflare R2", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Foto disimpan lokal (${uploadRes.exceptionOrNull()?.message})", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            val saved = viewModel.saveUriToInternalStorage(context, tempCameraUri!!)
            photoUri = saved ?: tempCameraUri.toString()
            
            scope.launch {
                isUploadingFile = true
                Toast.makeText(context, "Mengunggah foto ke Cloudflare R2...", Toast.LENGTH_SHORT).show()
                val uploadRes = viewModel.uploadUriToCloudflareR2(context, tempCameraUri!!)
                isUploadingFile = false
                if (uploadRes.isSuccess) {
                    photoUri = uploadRes.getOrThrow()
                    Toast.makeText(context, "Foto berhasil diunggah ke Cloudflare R2", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Foto disimpan lokal (${uploadRes.exceptionOrNull()?.message})", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val baFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val originalName = getFileName(context, uri) ?: "Dokumen_BA_${System.currentTimeMillis()}"
            val savedUri = viewModel.saveBaFileToInternalStorage(context, uri, originalName)
            baFileUri = savedUri ?: uri.toString()
            baFileName = originalName
            
            scope.launch {
                isUploadingFile = true
                Toast.makeText(context, "Mengunggah dokumen BA ke Cloudflare R2...", Toast.LENGTH_SHORT).show()
                val uploadRes = viewModel.uploadUriToCloudflareR2(context, uri, fileNameHint = originalName)
                isUploadingFile = false
                if (uploadRes.isSuccess) {
                    baFileUri = uploadRes.getOrThrow()
                    Toast.makeText(context, "Dokumen BA berhasil diunggah ke Cloudflare R2", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Dokumen BA disimpan lokal (${uploadRes.exceptionOrNull()?.message})", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Add Device Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Tambah Perangkat", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Dropdown State for Jenis Perangkat selection
                    var dropdownExpanded by remember { mutableStateOf(false) }

                    // 1. Dropdown Selection for Jenis Perangkat
                    Text("Jenis Perangkat:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (type == "AIO") "PC AIO" else "Laptop",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pilih PC AIO atau Laptop") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Pilih jenis perangkat"
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Green40,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Transparent overlay to detect clicks
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { dropdownExpanded = true }
                        )

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("🖥️ PC AIO") },
                                onClick = {
                                    type = "AIO"
                                    dropdownExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("💻 Laptop") },
                                onClick = {
                                    type = "Laptop"
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }

                    // 2. Barcode Column with Scan Icon next to it
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = serialNumber,
                            onValueChange = { serialNumber = it },
                            readOnly = false,
                            label = { Text("ID Scan Barcode") },
                            placeholder = { Text("Ketik atau scan barcode") },
                            leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = "Barcode", tint = Green40) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_device_barcode")
                        )

                        // Scan logo / icon next to the column
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Green40.copy(alpha = 0.12f))
                                .clickable {
                                    showBarcodeScannerInForm = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan Logo",
                                tint = Green40,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama User") },
                        placeholder = { Text("Contoh: Ahmad Subarjo") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = "User") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_device_name")
                    )

                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Tipe & Merek Perangkat") },
                        placeholder = { Text("Contoh: ASUS ExpertBook B1") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Computer, contentDescription = "Tipe & Merek") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_device_brand")
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Lokasi") },
                        placeholder = { Text("Contoh: Ruang IT Lantai 3") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Business, contentDescription = "Lokasi") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_device_desc")
                    )

                    OutlinedTextField(
                        value = sn,
                        onValueChange = { sn = it },
                        label = { Text("SN (Serial Number)") },
                        placeholder = { Text("Contoh: SN-HP-992318") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Pin, contentDescription = "SN") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_device_sn")
                    )

                    // Physical Photo
                    Text("Foto Fisik:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    if (photoUri == null) {
                                        if (cameraPermissionState.status.isGranted) {
                                            try {
                                                val uri = createHighQualityPhotoUri(context)
                                                if (uri != null) {
                                                    tempCameraUri = uri
                                                    cameraLauncher.launch(uri)
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Tidak dapat membuka kamera: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            cameraPermissionState.launchPermissionRequest()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (photoUri != null) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val resolvedPhotoUri = viewModel.getLocalFilePathForUrl(context, photoUri, "maintenance_photos")
                                    if (resolvedPhotoUri != null) {
                                        AsyncImage(
                                            model = resolvedPhotoUri,
                                            contentDescription = "Foto fisik perangkat",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize().background(Color(0xFFFFEBEE)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "File content:// lama. Silakan upload ulang.",
                                                color = Color.Red,
                                                fontSize = 10.sp,
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(2.dp)
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f))
                                            .clickable { photoUri = null },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Hapus Foto",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Ambil Foto Kamera",
                                    tint = Green40,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.weight(1f)
                        ) {
                            Button(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Image, contentDescription = "Galeri", modifier = Modifier.size(16.dp))
                                    Text("Pilih Galeri", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Upload File BA Section (Optional)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Upload File BA (Opsional):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (baFileUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GreenLight, shape = RoundedCornerShape(8.dp))
                                .border(1.dp, Green40.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Article,
                                    contentDescription = "File BA",
                                    tint = Green40,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = baFileName ?: "Dokumen BA",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Terlampir",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    baFileUri = null
                                    baFileName = null
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Hapus File",
                                    tint = Color.Red,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { baFilePickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = "Pilih File BA",
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("Pilih File BA (PDF, Image, etc.)")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && serialNumber.isNotBlank()) {
                            viewModel.addDevice(
                                type = type,
                                name = name,
                                brand = brand,
                                serialNumber = serialNumber,
                                condition = "Baik",
                                description = description,
                                sn = sn,
                                photoUri = photoUri,
                                baFileUri = baFileUri,
                                baFileName = baFileName,
                                onResult = { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            )
                            // Reset
                            name = ""
                            brand = ""
                            serialNumber = ""
                            description = ""
                            sn = ""
                            photoUri = null
                            baFileUri = null
                            baFileName = null
                            type = "AIO"
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Green40),
                    modifier = Modifier.testTag("save_device_button")
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showBarcodeScannerInForm) {
        val scannerContext = LocalContext.current

        // Laser Animation setup for scanning simulator
        val infiniteTransition = rememberInfiniteTransition(label = "form_laser")
        val laserYOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 200f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "form_laserOffset"
        )

        // Request permission automatically on dialog launch
        LaunchedEffect(Unit) {
            if (!cameraPermissionState.status.isGranted) {
                cameraPermissionState.launchPermissionRequest()
            }
        }

        var tempBarcode by remember { mutableStateOf("") }
        var isScanned by remember { mutableStateOf(false) }
        LaunchedEffect(showBarcodeScannerInForm) {
            if (showBarcodeScannerInForm) {
                isScanned = false
                if (serialNumber.isNotBlank()) {
                    tempBarcode = serialNumber
                } else {
                    val prefix = if (type == "AIO") "BC-AIO" else "BC-LAP"
                    val rand = (10000..99999).random()
                    tempBarcode = "$prefix-$rand"
                }
            }
        }

        // Scan success function
        fun triggerScanSuccess() {
            serialNumber = tempBarcode
            Toast.makeText(scannerContext, "Scan Barcode Berhasil!", Toast.LENGTH_SHORT).show()
            showBarcodeScannerInForm = false
        }

        AlertDialog(
            onDismissRequest = { showBarcodeScannerInForm = false },
            title = {
                Text(
                    text = "Scanning Barcode...",
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
                            .border(4.dp, Green40, RoundedCornerShape(16.dp))
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
                                    Toast.makeText(scannerContext, "Terdeteksi: $scannedBarcode", Toast.LENGTH_SHORT).show()
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
                                    colors = ButtonDefaults.buttonColors(containerColor = Green40),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Izinkan Kamera", fontSize = 11.sp)
                                }
                            }
                        }

                        // Custom Canvas to draw laser
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val y = (laserYOffset / 200f) * size.height
                            drawLine(
                                color = Green40,
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
                            focusedBorderColor = Green40,
                            focusedLabelColor = Green40
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
                                    color = Green40,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Menyesuaikan barcode...",
                                    fontSize = 12.sp,
                                    color = Green40,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Terdeteksi",
                                    tint = Green40,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Barcode berhasil dideteksi!",
                                    fontSize = 12.sp,
                                    color = Green40,
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
                        onClick = { showBarcodeScannerInForm = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal", color = Color.Gray)
                    }
                    Button(
                        onClick = { triggerScanSuccess() },
                        colors = ButtonDefaults.buttonColors(containerColor = Green40),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.5f).height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Simpan", modifier = Modifier.size(16.dp))
                            Text("Simpan Barcode", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        )
    }

    if (selectedDetailDevice != null) {
        DeviceDetailDialog(
            device = selectedDetailDevice!!,
            viewModel = viewModel,
            onDismiss = { selectedDetailDevice = null },
            onEditClick = { device ->
                selectedDetailDevice = null
                selectedEditDevice = device
            }
        )
    }

    if (selectedEditDevice != null) {
        DeviceEditDialog(
            device = selectedEditDevice!!,
            viewModel = viewModel,
            onDismiss = { selectedEditDevice = null },
            onSave = { updatedDevice ->
                viewModel.updateDevice(updatedDevice) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
                selectedEditDevice = null
            }
        )
    }
}

@Composable
fun DeviceCard(
    device: Device,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleCondition: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            .size(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GreenLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (device.type == "AIO") "🖥️" else "💻",
                            fontSize = 24.sp
                        )
                    }
                    Column {
                        Text(
                            text = "User: ${device.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (device.brand.isNotBlank()) {
                            Text(
                                text = "Tipe/Merek: ${device.brand}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                        if (device.sn.isNotBlank()) {
                            Text(
                                text = "SN: ${device.sn}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = "Barcode: ${device.serialNumber}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                // Delete icon button
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus perangkat",
                        tint = Color.Red.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Location description if present
            if (device.description.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Lokasi",
                        tint = Green40,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = device.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

            Spacer(modifier = Modifier.height(10.dp))

            // Footer of Card: status and toggle button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (device.condition == "Baik") Color(0xFF4CAF50) else AccentOrange,
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = "Kondisi: ${device.condition}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (device.condition == "Baik") Color(0xFF4CAF50) else AccentOrange
                    )
                }

                // Quick condition toggle button
                TextButton(
                    onClick = onToggleCondition,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (device.condition == "Baik") AccentOrange else Color(0xFF4CAF50)
                    )
                ) {
                    Text(
                        text = if (device.condition == "Baik") "Set Trouble ⚠️" else "Set Baik OK ✅",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun saveBitmapToCache(context: android.content.Context, bitmap: android.graphics.Bitmap): Uri? {
    return try {
        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = java.io.File(cachePath, "captured_device_${System.currentTimeMillis()}.jpg")
        val stream = java.io.FileOutputStream(file)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
        stream.close()
        Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
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

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
fun DeviceDetailDialog(
    device: Device,
    viewModel: MaintenanceViewModel,
    onDismiss: () -> Unit,
    onEditClick: (Device) -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detail Spesifikasi Perangkat", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (device.photoUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.LightGray)
                    ) {
                        val resolvedPhotoUri = viewModel.getLocalFilePathForUrl(context, device.photoUri, "maintenance_photos")
                        if (resolvedPhotoUri != null) {
                            AsyncImage(
                                model = resolvedPhotoUri,
                                contentDescription = "Foto Fisik Perangkat",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color(0xFFFFEBEE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "File content:// lama. Silakan upload ulang.",
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GreenLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (device.type == "AIO") "🖥️" else "💻",
                                fontSize = 40.sp
                            )
                            Text(
                                text = "Belum ada foto fisik",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DetailRow(label = "Jenis Perangkat", value = if (device.type == "AIO") "PC All-In-One (AIO)" else "Laptop")
                    DetailRow(label = "Nama User / Pemilik", value = device.name)
                    DetailRow(label = "Tipe & Merek", value = device.brand.ifBlank { "-" })
                    DetailRow(label = "Barcode ID", value = device.serialNumber)
                    DetailRow(label = "Serial Number (Hardware)", value = device.sn.ifBlank { "-" })
                    DetailRow(label = "Lokasi", value = device.description.ifBlank { "-" })
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kondisi:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (device.condition == "Baik") Color(0xFF4CAF50) else AccentOrange,
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = device.condition,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (device.condition == "Baik") Color(0xFF4CAF50) else AccentOrange
                            )
                        }
                    }
                    
                    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                    val dateStr = sdf.format(java.util.Date(device.lastMaintenance))
                    DetailRow(label = "Terakhir Maintenance", value = dateStr)

                    if (device.baFileUri != null) {
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "Dokumen Lampiran BA:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = GreenLight),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Article,
                                        contentDescription = "File BA",
                                        tint = Green40,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = device.baFileName ?: "Dokumen_BA",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "File siap dibuka atau diunduh",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            try {
                                                val resolvedPath = viewModel.getLocalFilePathForUrl(context, device.baFileUri, "ba_files")
                                                if (resolvedPath == null) {
                                                    Toast.makeText(context, "File tidak tersedia (content:// lama). Silakan upload ulang.", Toast.LENGTH_LONG).show()
                                                } else if (resolvedPath.startsWith("http")) {
                                                    val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(resolvedPath))
                                                    context.startActivity(webIntent)
                                                } else {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                        val fileUri = Uri.parse(resolvedPath)
                                                        val finalUri = if (fileUri.scheme == "file") {
                                                            val file = java.io.File(fileUri.path ?: "")
                                                            androidx.core.content.FileProvider.getUriForFile(
                                                                context,
                                                                "${context.packageName}.fileprovider",
                                                                file
                                                            )
                                                        } else {
                                                            fileUri
                                                        }
                                                        setDataAndType(finalUri, "application/pdf")
                                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Tidak dapat membuka file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Green40),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = "Buka", modifier = Modifier.size(16.dp))
                                            Text("Buka File", fontSize = 12.sp)
                                        }
                                    }
                                    
                                    Button(
                                        onClick = {
                                            try {
                                                val resolvedPath = viewModel.getLocalFilePathForUrl(context, device.baFileUri, "ba_files")
                                                if (resolvedPath == null) {
                                                    Toast.makeText(context, "File tidak tersedia (content:// lama). Silakan upload ulang.", Toast.LENGTH_LONG).show()
                                                } else if (resolvedPath.startsWith("http")) {
                                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(android.content.Intent.EXTRA_TEXT, resolvedPath)
                                                    }
                                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Bagikan Link Dokumen BA"))
                                                } else {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "application/pdf"
                                                        val fileUri = Uri.parse(resolvedPath)
                                                        val finalUri = if (fileUri.scheme == "file") {
                                                            val file = java.io.File(fileUri.path ?: "")
                                                            androidx.core.content.FileProvider.getUriForFile(
                                                                context,
                                                                "${context.packageName}.fileprovider",
                                                                file
                                                            )
                                                        } else {
                                                            fileUri
                                                        }
                                                        putExtra(android.content.Intent.EXTRA_STREAM, finalUri)
                                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(android.content.Intent.createChooser(intent, "Unduh / Bagikan File BA"))
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Gagal mengunduh/membagikan: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f).height(36.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = "Unduh / Bagikan", modifier = Modifier.size(16.dp))
                                            Text("Unduh / Share", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        DetailRow(label = "File BA", value = "Tidak ada lampiran")
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Tutup")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onEditClick(device) },
                    colors = ButtonDefaults.buttonColors(containerColor = Green40)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Edit")
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DeviceEditDialog(
    device: Device,
    viewModel: MaintenanceViewModel,
    onDismiss: () -> Unit,
    onSave: (Device) -> Unit
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    var type by remember { mutableStateOf(device.type) }
    var name by remember { mutableStateOf(device.name) }
    var brand by remember { mutableStateOf(device.brand) }
    var serialNumber by remember { mutableStateOf(device.serialNumber) }
    var sn by remember { mutableStateOf(device.sn) }
    var description by remember { mutableStateOf(device.description) }
    var condition by remember { mutableStateOf(device.condition) }
    var photoUri by remember { mutableStateOf(device.photoUri) }
    
    var showBarcodeScannerInEdit by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val scope = rememberCoroutineScope()
    var isUploadingFile by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = viewModel.saveUriToInternalStorage(context, uri)
            photoUri = saved ?: uri.toString()
            
            scope.launch {
                isUploadingFile = true
                Toast.makeText(context, "Mengunggah foto ke Cloudflare R2...", Toast.LENGTH_SHORT).show()
                val uploadRes = viewModel.uploadUriToCloudflareR2(context, uri)
                isUploadingFile = false
                if (uploadRes.isSuccess) {
                    photoUri = uploadRes.getOrThrow()
                    Toast.makeText(context, "Foto berhasil diunggah ke Cloudflare R2", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Foto disimpan lokal (${uploadRes.exceptionOrNull()?.message})", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            val saved = viewModel.saveUriToInternalStorage(context, tempCameraUri!!)
            photoUri = saved ?: tempCameraUri.toString()
            
            scope.launch {
                isUploadingFile = true
                Toast.makeText(context, "Mengunggah foto ke Cloudflare R2...", Toast.LENGTH_SHORT).show()
                val uploadRes = viewModel.uploadUriToCloudflareR2(context, tempCameraUri!!)
                isUploadingFile = false
                if (uploadRes.isSuccess) {
                    photoUri = uploadRes.getOrThrow()
                    Toast.makeText(context, "Foto berhasil diunggah ke Cloudflare R2", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Foto disimpan lokal (${uploadRes.exceptionOrNull()?.message})", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var baFileUri by remember { mutableStateOf(device.baFileUri) }
    var baFileName by remember { mutableStateOf(device.baFileName) }

    val baFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val originalName = getFileName(context, uri) ?: "Dokumen_BA_${System.currentTimeMillis()}"
            val savedUri = viewModel.saveBaFileToInternalStorage(context, uri, originalName)
            baFileUri = savedUri ?: uri.toString()
            baFileName = originalName
            
            scope.launch {
                isUploadingFile = true
                Toast.makeText(context, "Mengunggah dokumen BA ke Cloudflare R2...", Toast.LENGTH_SHORT).show()
                val uploadRes = viewModel.uploadUriToCloudflareR2(context, uri, fileNameHint = originalName)
                isUploadingFile = false
                if (uploadRes.isSuccess) {
                    baFileUri = uploadRes.getOrThrow()
                    Toast.makeText(context, "Dokumen BA berhasil diunggah ke Cloudflare R2", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Dokumen BA disimpan lokal (${uploadRes.exceptionOrNull()?.message})", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Perangkat", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Dropdown for type
                var dropdownExpanded by remember { mutableStateOf(false) }
                Text("Jenis Perangkat:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (type == "AIO") "PC AIO" else "Laptop",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Pilih PC AIO atau Laptop") },
                        trailingIcon = {
                            IconButton(onClick = { dropdownExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DropdownMenuItem(
                            text = { Text("PC AIO") },
                            onClick = {
                                type = "AIO"
                                dropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Laptop") },
                            onClick = {
                                type = "Laptop"
                                dropdownExpanded = false
                            }
                        )
                    }
                }

                // Nama User
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama User") },
                    placeholder = { Text("Contoh: Citra Lestari") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Nama User") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Tipe & Merek
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Tipe & Merek Perangkat") },
                    placeholder = { Text("Contoh: ASUS ExpertBook B1") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Computer, contentDescription = "Tipe & Merek") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Barcode ID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = serialNumber,
                        onValueChange = { serialNumber = it },
                        label = { Text("Barcode ID") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = "Barcode") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { showBarcodeScannerInEdit = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Green40),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan")
                    }
                }

                // SN
                OutlinedTextField(
                    value = sn,
                    onValueChange = { sn = it },
                    label = { Text("Serial Number (SN)") },
                    placeholder = { Text("Contoh: SN12345678") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = "SN") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Lokasi
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Lokasi") },
                    placeholder = { Text("Contoh: Ruang Meeting Utama") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = "Lokasi") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Kondisi Dropdown
                var condDropdownExpanded by remember { mutableStateOf(false) }
                Text("Kondisi Perangkat:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = condition,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kondisi") },
                        trailingIcon = {
                            IconButton(onClick = { condDropdownExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { condDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = condDropdownExpanded,
                        onDismissRequest = { condDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DropdownMenuItem(
                            text = { Text("Baik") },
                            onClick = {
                                condition = "Baik"
                                condDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Trouble") },
                            onClick = {
                                condition = "Trouble"
                                condDropdownExpanded = false
                            }
                        )
                    }
                }

                // Foto fisik
                Text("Foto Fisik Perangkat:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (cameraPermissionState.status.isGranted) {
                                try {
                                    val uri = createHighQualityPhotoUri(context)
                                    if (uri != null) {
                                        tempCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Tidak dapat membuka kamera: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                cameraPermissionState.launchPermissionRequest()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Kamera", modifier = Modifier.size(16.dp))
                            Text("Kamera", fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Galeri", modifier = Modifier.size(16.dp))
                            Text("Pilih Galeri", fontSize = 12.sp)
                        }
                    }
                }

                if (photoUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "Selected Photo Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { photoUri = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                                .size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus foto", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Upload File BA Section (Optional)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Upload File BA (Opsional):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (baFileUri != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GreenLight, shape = RoundedCornerShape(8.dp))
                            .border(1.dp, Green40.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Article,
                                contentDescription = "File BA",
                                tint = Green40,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = baFileName ?: "Dokumen BA",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Terlampir",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                baFileUri = null
                                baFileName = null
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hapus File",
                                tint = Color.Red,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { baFilePickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Pilih File BA",
                                modifier = Modifier.size(20.dp)
                            )
                            Text("Pilih File BA (PDF, Image, etc.)")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && serialNumber.isNotBlank()) {
                        onSave(
                            device.copy(
                                type = type,
                                name = name,
                                brand = brand,
                                serialNumber = serialNumber,
                                sn = sn,
                                description = description,
                                condition = condition,
                                photoUri = photoUri,
                                baFileUri = baFileUri,
                                baFileName = baFileName,
                                lastMaintenance = System.currentTimeMillis()
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Green40)
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )

    // Barcode scanner inside Edit Dialog if triggered
    if (showBarcodeScannerInEdit) {
        val scannerContext = LocalContext.current
        var editScannedBarcode by remember { mutableStateOf("") }
        var isEditScanned by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showBarcodeScannerInEdit = false },
            title = { Text("Scan Barcode", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, Green40, RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        // Display full active camera scanner
                        CameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            onBarcodeDetected = { scannedBarcode ->
                                editScannedBarcode = scannedBarcode
                                isEditScanned = true
                                Toast.makeText(scannerContext, "Terdeteksi: $scannedBarcode", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isEditScanned) GreenLight else Color.Yellow.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isEditScanned) {
                                CircularProgressIndicator(
                                    color = Green40,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Menyesuaikan barcode...",
                                    fontSize = 12.sp,
                                    color = Green40,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Terdeteksi",
                                    tint = Green40,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Barcode berhasil dideteksi!",
                                    fontSize = 12.sp,
                                    color = Green40,
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
                        onClick = { showBarcodeScannerInEdit = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal", color = Color.Gray)
                    }
                    Button(
                        onClick = {
                            if (isEditScanned && editScannedBarcode.isNotBlank()) {
                                serialNumber = editScannedBarcode
                            }
                            showBarcodeScannerInEdit = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Green40),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.5f).height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Simpan", modifier = Modifier.size(16.dp))
                            Text("Simpan Barcode", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        )
    }
}

private fun getFileName(context: android.content.Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    result = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result
}

