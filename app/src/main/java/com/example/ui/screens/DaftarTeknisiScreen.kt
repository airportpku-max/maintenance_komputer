package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Technician
import com.example.ui.MaintenanceViewModel
import com.example.ui.theme.Green40
import com.example.ui.theme.GreenLight
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DaftarTeknisiScreen(
    viewModel: MaintenanceViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val technicians by viewModel.technicians.collectAsState()
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedEditTechnician by remember { mutableStateOf<Technician?>(null) }

    // Form states
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var editPhotoUri by remember { mutableStateOf<String?>(null) }
    var activePhotoTarget by remember { mutableStateOf("add") } // "add" or "edit"
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()

    val updateTechnicianPhotoState = { uriStr: String ->
        if (activePhotoTarget == "add") {
            photoUri = uriStr
        } else {
            editPhotoUri = uriStr
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = viewModel.saveUriToInternalStorage(context, uri)
            val finalUriStr = saved ?: uri.toString()
            updateTechnicianPhotoState(finalUriStr)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            val saved = viewModel.saveUriToInternalStorage(context, tempCameraUri!!)
            val finalUriStr = saved ?: tempCameraUri.toString()
            updateTechnicianPhotoState(finalUriStr)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daftar Teknisi", color = Color.White, fontWeight = FontWeight.Bold) },
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
                    name = ""
                    phone = ""
                    photoUri = null
                    showAddDialog = true
                },
                containerColor = Green40,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_technician_fab")
            ) {
                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Tambah Teknisi")
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
            // Header stats
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GreenLight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Teknisi Siaga",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Green40
                        )
                        Text(
                            text = "Teknisi aktif yang siap menangani trouble",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(Green40, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = technicians.size.toString(),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            if (technicians.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Engineering,
                            contentDescription = "Empty",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Belum ada teknisi terdaftar",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
                    items(technicians, key = { it.id }) { tech ->
                        TechnicianCard(
                            tech = tech,
                            viewModel = viewModel,
                            onEdit = { selectedEditTechnician = tech },
                            onDelete = { viewModel.deleteTechnician(tech.id) },
                            onCall = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${tech.phone}"))
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Tidak bisa melakukan panggilan", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onWhatsApp = {
                                var formattedNumber = tech.phone
                                if (formattedNumber.startsWith("0")) {
                                    formattedNumber = "62" + formattedNumber.substring(1)
                                }
                                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber&text=Halo%20${tech.name},%20ada%20perangkat%20komputer%20yang%20butuh%20maintenance.")
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Aplikasi WhatsApp tidak ditemukan", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Technician Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Tambah Teknisi", fontWeight = FontWeight.Bold) },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Lengkap") },
                        placeholder = { Text("Contoh: Andi Wijaya") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_tech_name")
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Nomor Telepon") },
                        placeholder = { Text("Contoh: 0812XXXXXXXX") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_tech_phone")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Sua Foto / Selfie",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (photoUri != null) {
                                val resolvedPath = viewModel.getLocalFilePathForUrl(context, photoUri, "technicians")
                                if (resolvedPath != null) {
                                    AsyncImage(
                                        model = resolvedPath,
                                        contentDescription = "Foto Teknisi",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color(0xFFFFEBEE)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "content://\nlama",
                                            color = Color.Red,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "No Photo",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        activePhotoTarget = "add"
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
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = "Kamera", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Kamera", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        activePhotoTarget = "add"
                                        photoPickerLauncher.launch("image/*")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Galeri", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Galeri", fontSize = 11.sp)
                                }
                            }

                            if (photoUri != null) {
                                TextButton(
                                    onClick = { photoUri = null },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                                    modifier = Modifier.align(Alignment.Start),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Hapus Foto", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && phone.isNotBlank()) {
                            viewModel.addTechnician(name, "Teknisi", phone, photoUri = photoUri)
                            // Reset
                            name = ""
                            phone = ""
                            photoUri = null
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Green40),
                    modifier = Modifier.testTag("save_tech_button")
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

    // Edit Technician Dialog
    val editTech = selectedEditTechnician
    if (editTech != null) {
        var editName by remember(editTech.id) { mutableStateOf(editTech.name) }
        var editPhone by remember(editTech.id) { mutableStateOf(editTech.phone) }

        AlertDialog(
            onDismissRequest = { selectedEditTechnician = null },
            title = { Text("Edit Teknisi", fontWeight = FontWeight.Bold) },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nama Lengkap") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Nomor Telepon") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Sua Foto / Selfie",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (editPhotoUri != null) {
                                val resolvedPath = viewModel.getLocalFilePathForUrl(context, editPhotoUri, "technicians")
                                if (resolvedPath != null) {
                                    AsyncImage(
                                        model = resolvedPath,
                                        contentDescription = "Foto Teknisi",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(Color(0xFFFFEBEE)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "content://\nlama",
                                            color = Color.Red,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "No Photo",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        activePhotoTarget = "edit"
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
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = "Kamera", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Kamera", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        activePhotoTarget = "edit"
                                        photoPickerLauncher.launch("image/*")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Galeri", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Galeri", fontSize = 11.sp)
                                }
                            }

                            if (editPhotoUri != null) {
                                TextButton(
                                    onClick = { editPhotoUri = null },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                                    modifier = Modifier.align(Alignment.Start),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Hapus Foto", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank() && editPhone.isNotBlank()) {
                            viewModel.updateTechnician(editTech.copy(name = editName, phone = editPhone, photoUri = editPhotoUri))
                            selectedEditTechnician = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Green40)
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedEditTechnician = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun TechnicianCard(
    tech: Technician,
    viewModel: MaintenanceViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(GreenLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (tech.photoUri != null) {
                            val resolvedPath = viewModel.getLocalFilePathForUrl(context, tech.photoUri, "technicians")
                            if (resolvedPath != null) {
                                AsyncImage(
                                    model = resolvedPath,
                                    contentDescription = "Foto ${tech.name}",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color(0xFFFFEBEE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Avatar",
                                        tint = Color.Red,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Avatar",
                                tint = Green40,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = tech.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Green40
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus Teknisi",
                            tint = Color.Red.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Telepon / WA",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = tech.phone,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                // Call and WhatsApp Quick Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onCall,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFE3F2FD), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Hubungi",
                            tint = Color(0xFF1E88E5),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onWhatsApp,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFE8F5E9), CircleShape)
                    ) {
                        // Quick message text icon
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "WhatsApp",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
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
        val file = java.io.File(cachePath, "captured_tech_${System.currentTimeMillis()}.jpg")
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
