package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.MaintenanceViewModel
import com.example.ui.theme.Green40
import com.example.ui.theme.GreenLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MaintenanceViewModel,
    modifier: Modifier = Modifier
) {
    val devices by viewModel.devices.collectAsState()
    val technicians by viewModel.technicians.collectAsState()
    val logs by viewModel.maintenanceLogs.collectAsState()
    val tickets by viewModel.troubleTickets.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val sharedPrefs = remember { context.getSharedPreferences("google_sheets_sync_prefs", Context.MODE_PRIVATE) }
    var webAppUrl by remember { mutableStateOf(sharedPrefs.getString("web_app_url", "") ?: "") }
    var deleteLocalAfterSync by remember { mutableStateOf(sharedPrefs.getBoolean("delete_local_after_sync", false)) }
    var lastSyncTime by remember { mutableStateOf(sharedPrefs.getLong("last_sync_time", 0L)) }

    val supabasePrefs = remember { context.getSharedPreferences("supabase_r2_prefs", Context.MODE_PRIVATE) }
    var supabaseUrl by remember { mutableStateOf(supabasePrefs.getString("supabase_url", "") ?: "") }
    var supabaseAnonKey by remember { mutableStateOf(supabasePrefs.getString("supabase_anon_key", "") ?: "") }
    var cloudflareWorkerUrl by remember { mutableStateOf(supabasePrefs.getString("cloudflare_worker_url", "") ?: "") }

    var showSyncDialog by remember { mutableStateOf(false) }
    var showSupabaseDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var isSupabaseSyncing by remember { mutableStateOf(false) }
    var supabaseSyncMessage by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }
    var isEditingUrl by remember { mutableStateOf(false) }
    var isInstructionsExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // App header background inside profile
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Green40)
                    .padding(top = 40.dp, bottom = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Profile Avatar
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(Color.White, CircleShape)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(GreenLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Admin",
                                tint = Green40,
                                modifier = Modifier.size(50.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "IT Administrator",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = "admin.maintenance@company.com",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Level: Super Admin",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Section Title: Ringkasan Sistem
        item {
            Text(
                text = "Statistik Sistem Maintenance",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp)
            )
        }

        // Stats summary cards
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileStatItem(
                        title = "Perangkat",
                        value = devices.size.toString(),
                        icon = Icons.Default.Computer,
                        iconColor = Color(0xFF1E88E5),
                        modifier = Modifier.weight(1f)
                    )
                    ProfileStatItem(
                        title = "Log Selesai",
                        value = logs.size.toString(),
                        icon = Icons.Default.History,
                        iconColor = Color(0xFF009688),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileStatItem(
                        title = "Sisa Trouble",
                        value = tickets.count { it.status == "Pending" }.toString(),
                        icon = Icons.Default.Warning,
                        iconColor = Color(0xFFE53935),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Section Title: Pengaturan & Aplikasi
        item {
            Text(
                text = "Informasi Aplikasi",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp)
            )
        }

        // Menu Lists
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    ProfileMenuItem(
                        icon = Icons.Default.Info,
                        title = "Versi Aplikasi",
                        subtitle = "v1.0.0 Stable (Build 2026)",
                        onClick = {}
                    )
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    ProfileMenuItem(
                        icon = Icons.Default.CloudSync,
                        title = "Keamanan, Database & Storage Cloud",
                        subtitle = if (supabaseUrl.isNotBlank() && cloudflareWorkerUrl.isNotBlank()) "Terkoneksi (Supabase PostgreSQL + Cloudflare R2)" else "Konfigurasi Supabase PostgreSQL & Worker R2",
                        onClick = { showSupabaseDialog = true }
                    )
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    ProfileMenuItem(
                        icon = Icons.Default.Help,
                        title = "Pusat Bantuan",
                        subtitle = "Panduan operasional dan FAQ",
                        onClick = {}
                    )
                }
            }
        }
    }

    if (false) {
        AlertDialog(
            onDismissRequest = { if (!isSyncing) showSyncDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = null,
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Row: Cloud Icon + Title/Subtitle + Status Badge + Chevron Up/Down
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Soft green container for cloud icon
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Title and Subtitle column
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Koneksi Google Sheets\n(Live Mode)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Database terhubung langsung secara realtime",
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Right side: Badge + Chevron (Clickable to collapse/close)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clickable { showSyncDialog = false }
                        ) {
                            val hasUrl = webAppUrl.isNotBlank()
                            val isConnected = hasUrl && lastSyncTime > 0L
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isConnected) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isConnected) "Terhubung" else "Belum Terhubung",
                                    color = if (isConnected) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // HP terhubung langsung dengan Spreadsheet Cloud. Ubah URL / TextField
                    if (isEditingUrl || webAppUrl.isBlank()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = webAppUrl,
                                onValueChange = {
                                    webAppUrl = it
                                    sharedPrefs.edit().putString("web_app_url", it).apply()
                                },
                                label = { Text("Google Web App URL", fontSize = 12.sp) },
                                placeholder = { Text("https://script.google.com/macros/s/.../exec", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                                trailingIcon = {
                                    if (webAppUrl.isNotBlank()) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Valid",
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            )
                            if (webAppUrl.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { isEditingUrl = false }
                                    ) {
                                        Text("Selesai", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "HP terhubung langsung dengan Spreadsheet Cloud.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Ubah URL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1976D2),
                                modifier = Modifier
                                    .clickable { isEditingUrl = true }
                                    .padding(4.dp)
                            )
                        }
                    }

                    // Green info card: "Bagaimana Cara Kerja Mode Live?"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9).copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, Color(0xFFC8E6C9)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Bagaimana Cara Kerja Mode Live?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                            
                            Text(
                                text = "Setiap data perangkat, teknisi, trouble ticket, dan log maintenance yang Anda tambah atau ubah di HP akan otomatis langsung disimpan ke Google Sheets Anda secara realtime.",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Text(
                                text = "Jika Anda baru saja mengubah atau menghapus data langsung pada file spreadsheet Google Sheets, silakan ketuk tombol di bawah untuk menyinkronkan ulang agar data di HP langsung mengikuti data di Cloud.",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Last Synchronized + Reload Button
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Terakhir Sinkronisasi",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (lastSyncTime > 0L) {
                                        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(lastSyncTime))
                                    } else {
                                        "-"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Large green button: "Muat Ulang dari Google Sheets"
                            Button(
                                onClick = {
                                    if (webAppUrl.isNotBlank()) {
                                        isSyncing = true
                                        syncMessage = "Menarik data dari Google Sheets..."
                                        viewModel.pullDataFromGoogleSheets(webAppUrl) { success, msg ->
                                            isSyncing = false
                                            if (success) {
                                                syncMessage = "Berhasil memuat data dari Google Sheets!"
                                                lastSyncTime = System.currentTimeMillis()
                                                sharedPrefs.edit().putLong("last_sync_time", lastSyncTime).apply()
                                            } else {
                                                syncMessage = "Gagal memuat data: $msg"
                                            }
                                        }
                                    } else {
                                        syncMessage = "Harap masukkan URL Web App terlebih dahulu"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(start = 16.dp),
                                enabled = !isSyncing && webAppUrl.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Muat Ulang dari Google Sheets",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        // Syncing / Success / Error Progress Messages
                        if (isSyncing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF2E7D32))
                                Text(text = if (syncMessage.isNotEmpty()) syncMessage else "Menghubungkan ke Google Sheets...", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        } else if (syncMessage.isNotEmpty()) {
                            Text(
                                text = syncMessage,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (syncMessage.contains("Berhasil") || syncMessage.contains("berhasil") || syncMessage.contains("sukses") || syncMessage.contains("Sukses")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Collapsible "Cara Menghubungkan Google Sheets"
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isInstructionsExpanded = !isInstructionsExpanded }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = Color(0xFF006064),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Cara Menghubungkan Google Sheets",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = if (isInstructionsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (isInstructionsExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Silakan ikuti instruksi 5 menit di bawah ini untuk menghubungkan Google Sheets pribadi secara instan:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )

                                // Step 1
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(Color(0xFFE0F7FA), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("1", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF006064))
                                    }
                                    Text(
                                        text = "Buka Google Sheets baru di browser laptop/ponsel Anda.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Step 2
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(Color(0xFFE0F7FA), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("2", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF006064))
                                    }
                                    Text(
                                        text = "Pilih menu Ekstensi > Apps Script di bagian atas.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Step 3
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(Color(0xFFE0F7FA), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("3", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF006064))
                                    }
                                    Text(
                                        text = "Hapus semua teks bawaan, lalu gabungkan dengan menempelkan kode Apps Script di bawah ini.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 15.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Copy button
                                Button(
                                    onClick = {
                                        val scriptCode = getAppsScriptCode()
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Google Apps Script Code", scriptCode)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Kode disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0F7FA), contentColor = Color(0xFF006064)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF006064))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Salin Kode Apps Script", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = null
        )
    }

    if (showSupabaseDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSupabaseSyncing) showSupabaseDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = null,
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFFE0F2FE), shape = RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Supabase PostgreSQL & R2",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Database Supabase & Cloudflare R2 Upload",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { showSupabaseDialog = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    OutlinedTextField(
                        value = supabaseUrl,
                        onValueChange = {
                            supabaseUrl = it
                            supabasePrefs.edit().putString("supabase_url", it).apply()
                        },
                        label = { Text("Supabase Project URL", fontSize = 12.sp) },
                        placeholder = { Text("https://xxx.supabase.co", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                    )

                    OutlinedTextField(
                        value = supabaseAnonKey,
                        onValueChange = {
                            supabaseAnonKey = it
                            supabasePrefs.edit().putString("supabase_anon_key", it).apply()
                        },
                        label = { Text("Supabase Anon Key", fontSize = 12.sp) },
                        placeholder = { Text("eyJhbGciOiJIUzI1NiI...", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                    )

                    OutlinedTextField(
                        value = cloudflareWorkerUrl,
                        onValueChange = {
                            cloudflareWorkerUrl = it
                            supabasePrefs.edit().putString("cloudflare_worker_url", it).apply()
                        },
                        label = { Text("Cloudflare Worker API URL", fontSize = 12.sp) },
                        placeholder = { Text("https://my-r2-worker.workers.dev", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                    )

                    if (isSupabaseSyncing) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = supabaseSyncMessage, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (supabaseSyncMessage.isNotBlank()) {
                        Text(
                            text = supabaseSyncMessage,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (supabaseSyncMessage.startsWith("Berhasil")) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }

                    // Card Kembalikan / Restore Data Perangkat ke Supabase
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Kembalikan Data Perangkat ke Supabase", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF166534))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Jika data devices di Supabase hilang atau kosong, tekan tombol di bawah untuk mengunggah ulang seluruh data perangkat dari HP / Google Sheets langsung ke Supabase.",
                                fontSize = 10.5.sp,
                                color = Color(0xFF15803D),
                                lineHeight = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
                                        supabaseSyncMessage = "Supabase URL dan Anon Key harus diisi!"
                                        return@Button
                                    }
                                    isSupabaseSyncing = true
                                    supabaseSyncMessage = "Memulihkan & mengunggah ulang data perangkat ke Supabase..."
                                    coroutineScope.launch {
                                        val res = viewModel.restoreDevicesToSupabase(supabaseUrl, supabaseAnonKey)
                                        isSupabaseSyncing = false
                                        if (res.isSuccess) {
                                            supabaseSyncMessage = res.getOrDefault("Berhasil mengembalikan data perangkat ke Supabase!")
                                        } else {
                                            supabaseSyncMessage = "Gagal: ${res.exceptionOrNull()?.message}"
                                        }
                                    }
                                },
                                enabled = !isSupabaseSyncing,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kembalikan Semua Data Perangkat", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showGuideDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Panduan Setup", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
                                    supabaseSyncMessage = "Supabase URL dan Anon Key harus diisi!"
                                    return@Button
                                }
                                isSupabaseSyncing = true
                                supabaseSyncMessage = "Menghubungkan & Sinkronisasi ke Supabase PostgreSQL..."
                                coroutineScope.launch {
                                    val res = viewModel.syncDatabaseWithSupabase(supabaseUrl, supabaseAnonKey, cloudflareWorkerUrl)
                                    isSupabaseSyncing = false
                                    if (res.isSuccess) {
                                        supabaseSyncMessage = res.getOrDefault("Sinkronisasi Berhasil!")
                                    } else {
                                        supabaseSyncMessage = "Gagal: ${res.exceptionOrNull()?.message}"
                                    }
                                }
                            },
                            enabled = !isSupabaseSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sinkronkan", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = null
        )
    }

    if (showGuideDialog) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showGuideDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("Panduan Setup Supabase & Cloudflare R2", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "1. Salin Kode Cloudflare Worker (worker.js) lalu deploy di Cloudflare Dashboard > Workers & Pages.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(getWorkerJsCode()))
                            Toast.makeText(context, "Kode Worker disalin!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin Kode Cloudflare Worker (worker.js)", fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "2. Salin Script SQL Schema berikut lalu jalankan di Supabase SQL Editor.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(getSupabaseSqlSchema()))
                            Toast.makeText(context, "SQL Schema disalin!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin SQL Schema Supabase", fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGuideDialog = false }) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

private fun getAppsScriptCode(): String {
    return """
function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var action = data.action;
    
    if (action === "upload_file") {
      var fileData = Utilities.base64Decode(data.fileData);
      var blob = Utilities.newBlob(fileData, data.mimeType, data.fileName);
      var file = DriveApp.createFile(blob);
      try {
        file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
      } catch (err) {
        // ignore sharing error
      }
      var fileId = file.getId();
      var downloadUrl = "https://docs.google.com/uc?export=download&id=" + fileId;
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        fileId: fileId,
        downloadUrl: downloadUrl,
        fileName: data.fileName
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    if (action === "sync_all_data") {
      var ss = SpreadsheetApp.getActiveSpreadsheet();
      
      // 1. Devices Sheet
      syncDevicesSheet(ss, data.devices || []);
      
      // 2. Technicians Sheet
      syncTechniciansSheet(ss, data.technicians || []);
      
      // 3. Trouble Tickets Sheet
      syncTroubleTicketsSheet(ss, data.trouble_tickets || []);
      
      // 4. Maintenance Logs Sheet
      syncMaintenanceLogsSheet(ss, data.maintenance_logs || []);
      
      // 5. Uploaded Files Sheet
      syncUploadedFilesSheet(ss, data.uploaded_files || []);
      
      return ContentService.createTextOutput(JSON.stringify({status: "success", message: "All 5 sheets synchronized successfully!"}))
        .setMimeType(ContentService.MimeType.JSON);
    }
    
    if (action === "fetch_all_data") {
      var ss = SpreadsheetApp.getActiveSpreadsheet();
      var response = {
        status: "success",
        devices: fetchDevicesSheet(ss),
        technicians: fetchTechniciansSheet(ss),
        trouble_tickets: fetchTroubleTicketsSheet(ss),
        maintenance_logs: fetchMaintenanceLogsSheet(ss),
        uploaded_files: fetchUploadedFilesSheet(ss)
      };
      return ContentService.createTextOutput(JSON.stringify(response))
        .setMimeType(ContentService.MimeType.JSON);
    }
    
    return ContentService.createTextOutput(JSON.stringify({status: "error", message: "Unknown action"}))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({status: "error", message: err.toString()}))
      .setMimeType(ContentService.MimeType.JSON);
  }
}

function syncDevicesSheet(ss, devices) {
  if (!devices || devices.length === 0) return;
  var name = "Master Data Perangkat";
  var sheet = ss.getSheetByName(name) || ss.insertSheet(name);
  sheet.clearContents();
  sheet.appendRow([
    "ID Perangkat",
    "Tipe Perangkat",
    "Nama User",
    "Merek & Tipe",
    "ID Scan Barcode",
    "Kondisi",
    "Terakhir Maintenance",
    "Lokasi / Deskripsi",
    "SN",
    "Foto Fisik",
    "File BA (URI)",
    "Nama File BA"
  ]);
  for (var i = 0; i < devices.length; i++) {
    var item = devices[i];
    sheet.appendRow([
      item.id,
      item.type || "",
      item.name || "",
      item.brand || "",
      item.serialNumber || "",
      item.condition || "",
      item.lastMaintenance || "",
      item.description || "",
      item.sn || "",
      item.photoUri || "",
      item.baFileUri || "",
      item.baFileName || ""
    ]);
  }
}

function syncTechniciansSheet(ss, technicians) {
  if (!technicians || technicians.length === 0) return;
  var name = "Master Data Teknisi";
  var sheet = ss.getSheetByName(name) || ss.insertSheet(name);
  sheet.clearContents();
  sheet.appendRow([
    "ID Teknisi",
    "Nama",
    "Role",
    "No Telepon",
    "Status",
    "Foto"
  ]);
  for (var i = 0; i < technicians.length; i++) {
    var item = technicians[i];
    sheet.appendRow([
      item.id,
      item.name || "",
      item.role || "",
      item.phone || "",
      item.status || "",
      item.photoUri || ""
    ]);
  }
}

function syncTroubleTicketsSheet(ss, tickets) {
  if (!tickets || tickets.length === 0) return;
  var name = "Data Trouble Ticket";
  var sheet = ss.getSheetByName(name) || ss.insertSheet(name);
  sheet.clearContents();
  sheet.appendRow([
    "ID Ticket",
    "ID Perangkat",
    "Nama Perangkat",
    "Deskripsi Masalah",
    "Dilaporkan Oleh",
    "Status",
    "Tindakan",
    "Durasi Tindakan",
    "Foto Sebelum (URI)",
    "Foto Sesudah (URI)",
    "Tanggal Laporan"
  ]);
  for (var i = 0; i < tickets.length; i++) {
    var item = tickets[i];
    sheet.appendRow([
      item.id,
      item.deviceId,
      item.deviceName || "",
      item.description || "",
      item.reportedBy || "",
      item.status || "",
      item.actionTaken || "",
      item.duration || "",
      item.photoBefore || "",
      item.photoAfter || "",
      item.timestamp || ""
    ]);
  }
}

function getHeaderIndex(headers, possibleNames, defaultIdx) {
  if (!headers || headers.length === 0) return defaultIdx;
  for (var i = 0; i < headers.length; i++) {
    var h = headers[i] ? headers[i].toString().toLowerCase().trim() : "";
    for (var j = 0; j < possibleNames.length; j++) {
      if (h.indexOf(possibleNames[j].toLowerCase()) !== -1) {
        return i;
      }
    }
  }
  return defaultIdx;
}

function syncMaintenanceLogsSheet(ss, logs) {
  if (!logs || logs.length === 0) return;
  var name = "Data Maintenance";
  var sheet = ss.getSheetByName(name) || ss.insertSheet(name);
  sheet.clearContents();
  sheet.appendRow([
    "ID Log", 
    "Tanggal Maintenance", 
    "ID Perangkat",
    "Nama Perangkat", 
    "Nama Teknisi", 
    "Action Taken",
    "Health Report", 
    "Disk Cleanup", 
    "Hardware Cleanup", 
    "Checking Drive Error",
    "Scanning Virus", 
    "Checking Network", 
    "Updating Antivirus", 
    "Updating Aplikasi",
    "Windows License", 
    "Office License", 
    "Notes",
    "Paraf User (Base64)",
    "Paraf Petugas (Base64)",
    "Health Report (Before)",
    "Health Report (After)",
    "Disk Cleanup (Before)",
    "Disk Cleanup (After)",
    "Hardware Cleanup (Before)",
    "Hardware Cleanup (After)",
    "Checking Drive Error (Before)",
    "Checking Drive Error (After)",
    "Scanning Virus (Before)",
    "Scanning Virus (After)",
    "Checking Network (Before)",
    "Checking Network (After)",
    "Updating Antivirus (Before)",
    "Updating Antivirus (After)",
    "Updating Aplikasi (Before)",
    "Updating Aplikasi (After)"
  ]);
  for (var i = 0; i < logs.length; i++) {
    var item = logs[i];
    sheet.appendRow([
      item.id,
      item.date || "",
      item.deviceId,
      item.deviceName || "",
      item.technicianName || "",
      item.actionTaken || "",
      item.healthReport ? "YA" : "TIDAK",
      item.diskCleanup ? "YA" : "TIDAK",
      item.hardwareCleanup ? "YA" : "TIDAK",
      item.checkingDriveError ? "YA" : "TIDAK",
      item.scanningVirus ? "YA" : "TIDAK",
      item.checkingNetwork ? "YA" : "TIDAK",
      item.updatingAntivirus ? "YA" : "TIDAK",
      item.updatingAplikasi ? "YA" : "TIDAK",
      item.windowsLicense || "",
      item.officeLicense || "",
      item.notes || "",
      item.signatureData || "",
      item.techSignatureData || "",
      item.healthReportBeforePhoto || "",
      item.healthReportAfterPhoto || "",
      item.diskCleanupBeforePhoto || "",
      item.diskCleanupAfterPhoto || "",
      item.hardwareCleanupBeforePhoto || "",
      item.hardwareCleanupAfterPhoto || "",
      item.checkingDriveErrorBeforePhoto || "",
      item.checkingDriveErrorAfterPhoto || "",
      item.scanningVirusBeforePhoto || "",
      item.scanningVirusAfterPhoto || "",
      item.checkingNetworkBeforePhoto || "",
      item.checkingNetworkAfterPhoto || "",
      item.updatingAntivirusBeforePhoto || "",
      item.updatingAntivirusAfterPhoto || "",
      item.updatingAplikasiBeforePhoto || "",
      item.updatingAplikasiAfterPhoto || ""
    ]);
  }
}

function syncUploadedFilesSheet(ss, files) {
  if (!files || files.length === 0) return;
  var name = "Dokumen & Foto Upload";
  var sheet = ss.getSheetByName(name) || ss.insertSheet(name);
  sheet.clearContents();
  sheet.appendRow([
    "ID File",
    "Nama File",
    "Ukuran File",
    "Tanggal Upload",
    "Tipe File"
  ]);
  for (var i = 0; i < files.length; i++) {
    var item = files[i];
    sheet.appendRow([
      item.id,
      item.fileName || "",
      item.fileSize || "",
      item.uploadDate || "",
      item.fileType || ""
    ]);
  }
}

function fetchDevicesSheet(ss) {
  var name = "Master Data Perangkat";
  var sheet = ss.getSheetByName(name);
  if (!sheet) return [];
  var values = sheet.getDataRange().getValues();
  if (values.length <= 1) return [];
  var headers = values[0];
  var idxLm = getHeaderIndex(headers, ["terakhir maintenance", "lastmaintenance"], 6);
  var list = [];
  for (var i = 1; i < values.length; i++) {
    var r = values[i];
    var lmVal = r[idxLm];
    if (lmVal instanceof Date) {
      lmVal = Utilities.formatDate(lmVal, Session.getScriptTimeZone(), "yyyy-MM-dd HH:mm:ss");
    } else {
      lmVal = lmVal ? lmVal.toString() : "";
    }
    list.push({
      id: parseInt(r[0]) || 0,
      type: r[1] ? r[1].toString() : "",
      name: r[2] ? r[2].toString() : "",
      brand: r[3] ? r[3].toString() : "",
      serialNumber: r[4] ? r[4].toString() : "",
      condition: r[5] ? r[5].toString() : "",
      lastMaintenance: lmVal,
      description: r[7] ? r[7].toString() : "",
      sn: r[8] ? r[8].toString() : "",
      photoUri: r[9] ? r[9].toString() : "",
      baFileUri: r[10] ? r[10].toString() : "",
      baFileName: r[11] ? r[11].toString() : ""
    });
  }
  return list;
}

function fetchTechniciansSheet(ss) {
  var name = "Master Data Teknisi";
  var sheet = ss.getSheetByName(name);
  if (!sheet) return [];
  var values = sheet.getDataRange().getValues();
  if (values.length <= 1) return [];
  var list = [];
  for (var i = 1; i < values.length; i++) {
    var r = values[i];
    list.push({
      id: parseInt(r[0]) || 0,
      name: r[1] ? r[1].toString() : "",
      role: r[2] ? r[2].toString() : "",
      phone: r[3] ? r[3].toString() : "",
      status: r[4] ? r[4].toString() : "",
      photoUri: r[5] ? r[5].toString() : ""
    });
  }
  return list;
}

function fetchTroubleTicketsSheet(ss) {
  var name = "Data Trouble Ticket";
  var sheet = ss.getSheetByName(name);
  if (!sheet) return [];
  var values = sheet.getDataRange().getValues();
  if (values.length <= 1) return [];
  var headers = values[0];
  var idxTs = getHeaderIndex(headers, ["tanggal laporan", "timestamp"], 10);
  var list = [];
  for (var i = 1; i < values.length; i++) {
    var r = values[i];
    var tsVal = r[idxTs];
    if (tsVal instanceof Date) {
      tsVal = Utilities.formatDate(tsVal, Session.getScriptTimeZone(), "yyyy-MM-dd HH:mm:ss");
    } else {
      tsVal = tsVal ? tsVal.toString() : "";
    }
    list.push({
      id: parseInt(r[0]) || 0,
      deviceId: parseInt(r[1]) || 0,
      deviceName: r[2] ? r[2].toString() : "",
      description: r[3] ? r[3].toString() : "",
      reportedBy: r[4] ? r[4].toString() : "",
      status: r[5] ? r[5].toString() : "",
      actionTaken: r[6] ? r[6].toString() : "",
      duration: r[7] ? r[7].toString() : "",
      photoBefore: r[8] ? r[8].toString() : "",
      photoAfter: r[9] ? r[9].toString() : "",
      timestamp: tsVal
    });
  }
  return list;
}

function fetchMaintenanceLogsSheet(ss) {
  var name = "Data Maintenance";
  var sheet = ss.getSheetByName(name);
  if (!sheet) return [];
  var values = sheet.getDataRange().getValues();
  if (values.length <= 1) return [];
  
  var headers = values[0];
  var idxId = getHeaderIndex(headers, ["id log", "id_log"], 0);
  var idxDate = getHeaderIndex(headers, ["tanggal maintenance", "tanggal mainten", "tanggal", "date"], 1);
  var idxDeviceId = getHeaderIndex(headers, ["id perangkat", "deviceid"], 2);
  var idxDeviceName = getHeaderIndex(headers, ["nama perangkat", "devicename"], 3);
  var idxTechName = getHeaderIndex(headers, ["nama teknisi", "technicianname", "teknisi"], 4);
  var idxActionTaken = getHeaderIndex(headers, ["action taken", "tindakan"], 5);
  var idxHealth = getHeaderIndex(headers, ["health report"], 6);
  var idxDisk = getHeaderIndex(headers, ["disk cleanup"], 7);
  var idxHw = getHeaderIndex(headers, ["hardware cleanup", "hardware clean"], 8);
  var idxDriveErr = getHeaderIndex(headers, ["checking drive error"], 9);
  var idxVirus = getHeaderIndex(headers, ["scanning virus"], 10);
  var idxNetwork = getHeaderIndex(headers, ["checking network"], 11);
  var idxAv = getHeaderIndex(headers, ["updating antivirus"], 12);
  var idxApp = getHeaderIndex(headers, ["updating aplikasi"], 13);
  var idxWinLic = getHeaderIndex(headers, ["windows license", "windows lisensi"], 14);
  var idxOffLic = getHeaderIndex(headers, ["office license", "office lisensi"], 15);
  var idxNotes = getHeaderIndex(headers, ["notes", "keterangan"], 16);
  var idxSig = getHeaderIndex(headers, ["paraf user", "tanda tangan user", "paraf", "tanda tangan", "signature", "ttd"], 17);
  var idxTechSig = getHeaderIndex(headers, ["paraf petugas", "paraf teknisi", "tanda tangan petugas", "tanda tangan teknisi", "techsignature", "parafpetugas"], 18);
  var idxHb = getHeaderIndex(headers, ["health report (before)"], 19);
  var idxHa = getHeaderIndex(headers, ["health report (after)"], 20);
  var idxDb = getHeaderIndex(headers, ["disk cleanup (before)"], 21);
  var idxDa = getHeaderIndex(headers, ["disk cleanup (after)"], 22);
  var idxHwb = getHeaderIndex(headers, ["hardware cleanup (before)"], 23);
  var idxHwa = getHeaderIndex(headers, ["hardware cleanup (after)"], 24);
  var idxDreb = getHeaderIndex(headers, ["checking drive error (before)"], 25);
  var idxDrea = getHeaderIndex(headers, ["checking drive error (after)"], 26);
  var idxSvb = getHeaderIndex(headers, ["scanning virus (before)"], 27);
  var idxSva = getHeaderIndex(headers, ["scanning virus (after)"], 28);
  var idxCnb = getHeaderIndex(headers, ["checking network (before)"], 29);
  var idxCna = getHeaderIndex(headers, ["checking network (after)"], 30);
  var idxUab = getHeaderIndex(headers, ["updating antivirus (before)"], 31);
  var idxUaa = getHeaderIndex(headers, ["updating antivirus (after)"], 32);
  var idxUpb = getHeaderIndex(headers, ["updating aplikasi (before)"], 33);
  var idxUpa = getHeaderIndex(headers, ["updating aplikasi (after)"], 34);

  var list = [];
  for (var i = 1; i < values.length; i++) {
    var r = values[i];
    var dateVal = r[idxDate];
    if (dateVal instanceof Date) {
      dateVal = Utilities.formatDate(dateVal, Session.getScriptTimeZone(), "yyyy-MM-dd HH:mm:ss");
    } else {
      dateVal = dateVal ? dateVal.toString() : "";
    }
    list.push({
      id: parseInt(r[idxId]) || 0,
      date: dateVal,
      deviceId: parseInt(r[idxDeviceId]) || 0,
      deviceName: r[idxDeviceName] ? r[idxDeviceName].toString() : "",
      technicianName: r[idxTechName] ? r[idxTechName].toString() : "",
      actionTaken: r[idxActionTaken] ? r[idxActionTaken].toString() : "",
      healthReport: r[idxHealth] === "YA",
      diskCleanup: r[idxDisk] === "YA",
      hardwareCleanup: r[idxHw] === "YA",
      checkingDriveError: r[idxDriveErr] === "YA",
      scanningVirus: r[idxVirus] === "YA",
      checkingNetwork: r[idxNetwork] === "YA",
      updatingAntivirus: r[idxAv] === "YA",
      updatingAplikasi: r[idxApp] === "YA",
      windowsLicense: r[idxWinLic] ? r[idxWinLic].toString() : "",
      officeLicense: r[idxOffLic] ? r[idxOffLic].toString() : "",
      notes: r[idxNotes] ? r[idxNotes].toString() : "",
      signatureData: r[idxSig] ? r[idxSig].toString() : "",
      techSignatureData: r[idxTechSig] ? r[idxTechSig].toString() : "",
      healthReportBeforePhoto: r[idxHb] ? r[idxHb].toString() : "",
      healthReportAfterPhoto: r[idxHa] ? r[idxHa].toString() : "",
      diskCleanupBeforePhoto: r[idxDb] ? r[idxDb].toString() : "",
      diskCleanupAfterPhoto: r[idxDa] ? r[idxDa].toString() : "",
      hardwareCleanupBeforePhoto: r[idxHwb] ? r[idxHwb].toString() : "",
      hardwareCleanupAfterPhoto: r[idxHwa] ? r[idxHwa].toString() : "",
      checkingDriveErrorBeforePhoto: r[idxDreb] ? r[idxDreb].toString() : "",
      checkingDriveErrorAfterPhoto: r[idxDrea] ? r[idxDrea].toString() : "",
      scanningVirusBeforePhoto: r[idxSvb] ? r[idxSvb].toString() : "",
      scanningVirusAfterPhoto: r[idxSva] ? r[idxSva].toString() : "",
      checkingNetworkBeforePhoto: r[idxCnb] ? r[idxCnb].toString() : "",
      checkingNetworkAfterPhoto: r[idxCna] ? r[idxCna].toString() : "",
      updatingAntivirusBeforePhoto: r[idxUab] ? r[idxUab].toString() : "",
      updatingAntivirusAfterPhoto: r[idxUaa] ? r[idxUaa].toString() : "",
      updatingAplikasiBeforePhoto: r[idxUpb] ? r[idxUpb].toString() : "",
      updatingAplikasiAfterPhoto: r[idxUpa] ? r[idxUpa].toString() : ""
    });
  }
  return list;
}

function fetchUploadedFilesSheet(ss) {
  var name = "Dokumen & Foto Upload";
  var sheet = ss.getSheetByName(name);
  if (!sheet) return [];
  var values = sheet.getDataRange().getValues();
  if (values.length <= 1) return [];
  var headers = values[0];
  var idxUd = getHeaderIndex(headers, ["tanggal upload", "uploaddate"], 3);
  var list = [];
  for (var i = 1; i < values.length; i++) {
    var r = values[i];
    var udVal = r[idxUd];
    if (udVal instanceof Date) {
      udVal = Utilities.formatDate(udVal, Session.getScriptTimeZone(), "yyyy-MM-dd HH:mm:ss");
    } else {
      udVal = udVal ? udVal.toString() : "";
    }
    list.push({
      id: parseInt(r[0]) || 0,
      fileName: r[1] ? r[1].toString() : "",
      fileSize: r[2] ? r[2].toString() : "",
      uploadDate: udVal,
      fileType: r[4] ? r[4].toString() : ""
    });
  }
  return list;
}

function doGet(e) {
  return ContentService.createTextOutput("Google Sheets Sync Web App is active and running! Use POST request to sync data.")
    .setMimeType(ContentService.MimeType.TEXT);
}
""".trimIndent()
}

@Composable
fun ProfileStatItem(
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(85.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Green40,
            modifier = Modifier.size(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Selengkapnya",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun getWorkerJsCode(): String {
    val dollar = "$"
    return """
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
    };

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }

    // UPLOAD FILE TO CLOUDFLARE R2
    if (request.method === "POST" && (url.pathname === "/upload" || url.pathname === "/")) {
      try {
        const formData = await request.formData();
        const file = formData.get("file");
        if (!file) {
          return new Response(JSON.stringify({ success: false, error: "No file uploaded" }), {
            status: 400,
            headers: corsHeaders,
          });
        }

        const filename = formData.get("filename") || `${dollar}{crypto.randomUUID()}_${dollar}{Date.now()}.jpg`;
        await env.MY_R2_BUCKET.put(filename, file.stream(), {
          httpMetadata: { contentType: file.type || "image/jpeg" },
        });

        const baseUrl = new URL(request.url).origin;
        const publicDomain = (env.R2_PUBLIC_DOMAIN && env.R2_PUBLIC_DOMAIN.trim() !== "") ? env.R2_PUBLIC_DOMAIN.trim().replace(/\/+$/, "") : baseUrl;
        const publicUrl = `${dollar}{publicDomain}/${dollar}{filename}`;
        return new Response(JSON.stringify({ success: true, url: publicUrl, fileKey: filename }), {
          headers: corsHeaders,
        });
      } catch (err) {
        return new Response(JSON.stringify({ success: false, error: err.message }), {
          status: 500,
          headers: corsHeaders,
        });
      }
    }

    // GET FILE FROM CLOUDFLARE R2
    if (request.method === "GET") {
      const fileKey = url.searchParams.get("key") || (url.pathname !== "/" && url.pathname !== "/upload" && url.pathname !== "/delete" ? url.pathname.slice(1) : null);
      if (fileKey) {
        try {
          const object = await env.MY_R2_BUCKET.get(fileKey);
          if (!object) {
            return new Response("File not found in R2 bucket", { status: 404, headers: corsHeaders });
          }
          const headers = new Headers(corsHeaders);
          object.writeHttpMetadata(headers);
          headers.set("etag", object.httpEtag);
          if (!headers.has("content-type")) {
            if (fileKey.endsWith(".pdf")) headers.set("content-type", "application/pdf");
            else headers.set("content-type", "image/jpeg");
          }
          return new Response(object.body, { headers });
        } catch (err) {
          return new Response(err.message, { status: 500, headers: corsHeaders });
        }
      }
    }

    // DELETE FILE FROM CLOUDFLARE R2
    if (request.method === "DELETE" || (request.method === "POST" && url.pathname === "/delete")) {
      try {
        let fileKey = url.searchParams.get("key");
        if (!fileKey && url.pathname !== "/delete" && url.pathname !== "/") {
          fileKey = url.pathname.slice(1);
        }
        if (!fileKey) {
          try {
            const jsonBody = await request.json();
            fileKey = jsonBody.key || jsonBody.fileKey;
          } catch (_) {}
        }
        if (!fileKey) {
          return new Response(JSON.stringify({ success: false, error: "Missing key parameter" }), {
            status: 400,
            headers: corsHeaders,
          });
        }
        if (fileKey.includes("/")) {
          fileKey = fileKey.substring(fileKey.lastIndexOf("/") + 1);
        }
        await env.MY_R2_BUCKET.delete(fileKey);
        return new Response(JSON.stringify({ success: true, message: "File deleted" }), {
          headers: corsHeaders,
        });
      } catch (err) {
        return new Response(JSON.stringify({ success: false, error: err.message }), {
          status: 500,
          headers: corsHeaders,
        });
      }
    }

    return new Response("Cloudflare Worker R2 API Uploader Active", { headers: corsHeaders });
  }
};
""".trimIndent()
}

private fun getSupabaseSqlSchema(): String {
    return """
-- ============================================================
-- Supabase PostgreSQL Table Schema (AMAN: TIDAK MENGHAPUS DATA LAMA)
-- Jalankan skrip ini di SQL Editor Supabase
-- ============================================================

CREATE TABLE IF NOT EXISTS devices (
  id SERIAL PRIMARY KEY,
  type TEXT,
  name TEXT,
  brand TEXT,
  barcode_id TEXT,
  condition TEXT DEFAULT 'Baik',
  last_maintenance BIGINT,
  description TEXT,
  sn TEXT,
  photo_uri TEXT,
  ba_file_uri TEXT,
  ba_file_name TEXT
);

-- Tambahkan kolom bila belum ada (backward-compatible)
ALTER TABLE devices ADD COLUMN IF NOT EXISTS barcode_id TEXT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS type TEXT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS name TEXT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS brand TEXT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS condition TEXT DEFAULT 'Baik';
ALTER TABLE devices ADD COLUMN IF NOT EXISTS last_maintenance BIGINT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS sn TEXT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS photo_uri TEXT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS ba_file_uri TEXT;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS ba_file_name TEXT;

CREATE TABLE IF NOT EXISTS trouble_tickets (
  id SERIAL PRIMARY KEY,
  device_id INT,
  device_name TEXT,
  description TEXT,
  reported_by TEXT,
  status TEXT DEFAULT 'Pending',
  timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  action_taken TEXT,
  duration TEXT,
  photo_before TEXT,
  photo_after TEXT
);

CREATE TABLE IF NOT EXISTS maintenance_logs (
  id SERIAL PRIMARY KEY,
  device_id INT,
  device_name TEXT,
  action_taken TEXT,
  timestamp BIGINT,
  health_report BOOLEAN DEFAULT false,
  disk_cleanup BOOLEAN DEFAULT false,
  hardware_cleanup BOOLEAN DEFAULT false,
  checking_drive_error BOOLEAN DEFAULT false,
  scanning_virus BOOLEAN DEFAULT false,
  checking_network BOOLEAN DEFAULT false,
  updating_antivirus BOOLEAN DEFAULT false,
  updating_aplikasi BOOLEAN DEFAULT false,
  notes TEXT,
  signature_data TEXT,
  tech_signature_data TEXT,

  -- Cloudflare R2 Public URLs
  health_report_before_photo TEXT,
  health_report_after_photo TEXT,
  disk_cleanup_before_photo TEXT,
  disk_cleanup_after_photo TEXT,
  hardware_cleanup_before_photo TEXT,
  hardware_cleanup_after_photo TEXT,
  checking_drive_error_before_photo TEXT,
  checking_drive_error_after_photo TEXT,
  scanning_virus_before_photo TEXT,
  scanning_virus_after_photo TEXT,
  checking_network_before_photo TEXT,
  checking_network_after_photo TEXT,
  updating_antivirus_before_photo TEXT,
  updating_antivirus_after_photo TEXT,
  updating_aplikasi_before_photo TEXT,
  updating_aplikasi_after_photo TEXT
);

CREATE TABLE IF NOT EXISTS uploaded_files (
  id SERIAL PRIMARY KEY,
  file_name TEXT,
  file_size TEXT,
  upload_date BIGINT,
  file_type TEXT,
  file_uri TEXT
);

-- Matikan RLS (Row Level Security) agar anon key bisa BACA & TULIS data
ALTER TABLE IF EXISTS devices DISABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS trouble_tickets DISABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS maintenance_logs DISABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS uploaded_files DISABLE ROW LEVEL SECURITY;

-- Berikan Hak Akses Penuh
GRANT ALL ON ALL TABLES IN SCHEMA public TO anon, authenticated, postgres;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon, authenticated, postgres;
""".trimIndent()
}
