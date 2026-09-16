package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UploadedFile
import com.example.ui.MaintenanceViewModel
import com.example.ui.theme.Green40
import com.example.ui.theme.GreenLight
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadFileScreen(
    viewModel: MaintenanceViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val files by viewModel.uploadedFiles.collectAsState()
    val context = LocalContext.current

    var showUploadDialog by remember { mutableStateOf(false) }

    // Dialog state
    var fileName by remember { mutableStateOf("") }
    var fileType by remember { mutableStateOf("PDF") }
    val fileTypes = listOf("PDF", "XLSX", "PNG", "DOCX")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload File & Laporan", color = Color.White, fontWeight = FontWeight.Bold) },
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
                onClick = { showUploadDialog = true },
                containerColor = Green40,
                contentColor = Color.White,
                modifier = Modifier.testTag("upload_file_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Upload")
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
            // Hero section for upload instructions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(GreenLight)
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Cloud Upload",
                        tint = Green40,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Arsip Dokumen Maintenance",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Green40
                    )
                    Text(
                        text = "Unggah & simpan invoice pembelian part, laporan bulanan teknisi, atau dokumentasi inventaris dalam bentuk PDF, Excel, atau Foto.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
                    )
                }
            }

            Text(
                text = "File Terunggah (${files.size})",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, bottom = 12.dp, top = 8.dp)
            )

            if (files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Folder Kosong",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Belum ada dokumen diunggah",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
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
                    items(files, key = { it.id }) { file ->
                        FileCard(
                            file = file,
                            onDelete = { viewModel.deleteUploadedFile(file.id) },
                            onDownload = {
                                Toast.makeText(context, "Membuka file: ${file.fileName}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // Upload Dialog
    if (showUploadDialog) {
        var expandedType by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showUploadDialog = false },
            title = { Text("Unggah Dokumen Baru", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("Nama File Dokumen") },
                        placeholder = { Text("Contoh: Invoice_RAM_Vigen_Lab3") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_file_name")
                    )

                    // Type Selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = fileType,
                            onValueChange = {},
                            label = { Text("Format File") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { expandedType = !expandedType }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Format")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedType = !expandedType }
                                .testTag("file_type_dropdown")
                        )

                        DropdownMenu(
                            expanded = expandedType,
                            onDismissRequest = { expandedType = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            fileTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        fileType = type
                                        expandedType = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (fileName.isNotBlank()) {
                            val finalName = if (fileName.contains('.')) fileName else "$fileName.${fileType.lowercase()}"
                            val randomSize = "${(1..5).random()}.${(0..9).random()} MB"
                            viewModel.addUploadedFile(finalName, randomSize, fileType)
                            // Reset
                            fileName = ""
                            fileType = "PDF"
                            showUploadDialog = false
                            Toast.makeText(context, "Dokumen berhasil diunggah", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Green40),
                    modifier = Modifier.testTag("save_file_button")
                ) {
                    Text("Unggah")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUploadDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun FileCard(
    file: UploadedFile,
    onDelete: () -> Unit,
    onDownload: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(file.uploadDate))

    val (iconText, iconBgColor, iconColor) = when (file.fileType) {
        "PDF" -> Triple("PDF", Color(0xFFFFEBEE), Color(0xFFC62828))
        "XLSX" -> Triple("XLS", Color(0xFFE8F5E9), Color(0xFF2E7D32))
        "PNG" -> Triple("IMG", Color(0xFFE3F2FD), Color(0xFF1565C0))
        else -> Triple("DOC", Color(0xFFECEFF1), Color(0xFF37474F))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDownload),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Type Icon Card
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(iconBgColor, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = iconText,
                        color = iconColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Column {
                    Text(
                        text = file.fileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$dateStr • ${file.fileSize}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            // Action row
            Row {
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download file",
                        tint = Green40
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus file",
                        tint = Color.Red.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
