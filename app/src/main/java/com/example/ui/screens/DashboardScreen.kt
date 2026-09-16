package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MaintenanceViewModel
import com.example.ui.theme.AccentOrange
import com.example.ui.theme.Green40
import com.example.ui.theme.GreenDark
import com.example.ui.theme.GreenLight

@Composable
fun DashboardScreen(
    viewModel: MaintenanceViewModel,
    onNavigateToDataMaster: () -> Unit,
    onNavigateToUploadFile: () -> Unit,
    onNavigateToDaftarTeknisi: () -> Unit,
    onNavigateToTrouble: () -> Unit,
    onNavigateToReport: () -> Unit,
    onNavigateToBatchMaintenance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val devices by viewModel.devices.collectAsState()
    val tickets by viewModel.troubleTickets.collectAsState()
    val logs by viewModel.maintenanceLogs.collectAsState()

    // Calculate Stats
    val totalAIO = devices.count { it.type == "AIO" }
    val totalLaptop = devices.count { it.type == "Laptop" }
    val totalDevices = devices.size

    val totalTrouble = devices.count { it.condition == "Trouble" }
    val totalBaik = devices.count { it.condition == "Baik" }

    val activeTicketsCount = tickets.count { it.status == "Pending" }
    val maintainedDevicesCount = devices.count { viewModel.isDeviceAlreadyMaintainedInMonth(it, System.currentTimeMillis()) }
    val remainingDevicesCount = (totalDevices - maintainedDevicesCount).coerceAtLeast(0)

    val infiniteTransition = rememberInfiniteTransition(label = "bell_shake")
    val rotationAngle by if (activeTicketsCount > 0) {
        infiniteTransition.animateFloat(
            initialValue = -15f,
            targetValue = 15f,
            animationSpec = infiniteRepeatable(
                animation = tween(150, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rotation"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Green Header & Floating Summary Card overlay (merged into a single crash-free container)
        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Background Box with gradient and bottom curves
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(GreenDark, Green40)
                            ),
                            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                        )
                )

                // Foreground Content Column
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header Content
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, start = 24.dp, end = 24.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Dashboard Maintenance",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Monitor maintenance workflow and status",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Box(modifier = Modifier.size(40.dp)) {
                            IconButton(
                                onClick = onNavigateToTrouble,
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                    .size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notification",
                                    tint = Color.White,
                                    modifier = Modifier.rotate(rotationAngle)
                                )
                            }
                            if (activeTicketsCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(16.dp)
                                        .background(Color.Red, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = activeTicketsCount.toString(),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Floating Summary Card
                    Card(
                        modifier = Modifier
                            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                            .fillMaxWidth()
                            .testTag("summary_card"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                    // Header inside card
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(GreenLight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "System Status",
                                tint = Green40,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Computer Maintenance System",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Status real-time perangkat & aktifitas teknisi",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // 1st Row Stats: PC AIO vs Laptop, Baik vs Trouble
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left Section: Perangkat
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TOTAL PERANGKAT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$totalDevices Unit",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Green40
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "🖥️ AIO: $totalAIO | 💻 Laptop: $totalLaptop",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }

                        // Vertical Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(60.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        )

                        // Right Section: Kondisi
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                        ) {
                            Text(
                                text = "KONDISI PERANGKAT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFF4CAF50), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$totalBaik Baik",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(AccentOrange, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$totalTrouble Trouble",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentOrange
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Aktif Trouble ticket: $activeTicketsCount",
                                fontSize = 11.sp,
                                color = if (activeTicketsCount > 0) AccentOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontWeight = if (activeTicketsCount > 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // 2nd Row Stats: Completed vs Remaining (Selesai dan Sisa)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "AKTIFITAS MAINTENANCE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$maintainedDevicesCount Selesai Dikerjakan",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (remainingDevicesCount > 0) "Ada $remainingDevicesCount perangkat lagi yang perlu dikerjakan" else "Ada 0 perangkat lagi yang perlu dikerjakan",
                                fontSize = 11.sp,
                                color = if (remainingDevicesCount > 0) AccentOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        // Progress Bar showing maintenance ratio
                        val ratio = if (totalDevices > 0) maintainedDevicesCount.toFloat() / totalDevices.toFloat() else 0f
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(ratio)
                                    .background(Green40)
                            )
                        }
                    }
                }
            }
        }
    }
}

        // 3. Section Title
        item {
            Text(
                text = "Menu",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp)
            )
        }

        // 4. Grid Menu - Beautiful 3-row compact layout
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GridMenuCard(
                        title = "Data Master",
                        subtitle = "AIO PC & Laptop",
                        icon = Icons.Default.Computer,
                        backgroundColor = Color(0xFFE3F2FD),
                        iconColor = Color(0xFF1E88E5),
                        onClick = onNavigateToDataMaster,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("menu_data_master")
                    )

                    GridMenuCard(
                        title = "Berkas",
                        subtitle = "Laporan",
                        icon = Icons.Default.UploadFile,
                        backgroundColor = Color(0xFFFFF3E0),
                        iconColor = Color(0xFFFB8C00),
                        onClick = onNavigateToUploadFile,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("menu_upload_file")
                    )

                    GridMenuCard(
                        title = "Laporan",
                        subtitle = "Cetak PDF",
                        icon = Icons.Default.Assignment,
                        backgroundColor = Color(0xFFE1F5FE),
                        iconColor = Color(0xFF0288D1),
                        onClick = onNavigateToReport,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("menu_report")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GridMenuCard(
                        title = "Trouble",
                        subtitle = "Tiket",
                        icon = Icons.Default.Warning,
                        backgroundColor = Color(0xFFFFEBEE),
                        iconColor = Color(0xFFE53935),
                        badgeCount = activeTicketsCount,
                        onClick = onNavigateToTrouble,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("menu_trouble")
                    )

                    GridMenuCard(
                        title = "Input Main.",
                        subtitle = "Praktis",
                        icon = Icons.Default.LibraryAdd,
                        backgroundColor = Color(0xFFE0F2F1),
                        iconColor = Color(0xFF009688),
                        onClick = onNavigateToBatchMaintenance,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("menu_batch_maintenance")
                    )
                }
            }
        }

        // Extra spacing at the bottom
        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridMenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    backgroundColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Icon Box
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(backgroundColor, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Text details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                }
            }

            // Notification Badge if count > 0
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(18.dp)
                        .background(Color.Red, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
