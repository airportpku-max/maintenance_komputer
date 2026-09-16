package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.MaintenanceDatabase
import com.example.data.MaintenanceRepository
import com.example.ui.MaintenanceViewModel
import com.example.ui.ViewModelFactory
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    // Initialize Database, Repository, and ViewModel
    private val database by lazy { MaintenanceDatabase.getDatabase(applicationContext) }
    private val repository by lazy { MaintenanceRepository(database.maintenanceDao()) }
    private val viewModel: MaintenanceViewModel by viewModels {
        ViewModelFactory(repository, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Root screen bottom navigation visibility check
                val isRootScreen = currentRoute in listOf(
                    ROUTE_DASHBOARD,
                    ROUTE_SCANNER,
                    ROUTE_PROFILE
                )

                Scaffold(
                    bottomBar = {
                        if (isRootScreen) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 8.dp,
                                modifier = Modifier
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .testTag("bottom_nav")
                            ) {
                                NavigationBarItem(
                                    selected = currentRoute == ROUTE_DASHBOARD,
                                    onClick = {
                                        navController.navigate(ROUTE_DASHBOARD) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentRoute == ROUTE_DASHBOARD) Icons.Filled.Home else Icons.Outlined.Home,
                                            contentDescription = "Home"
                                        )
                                    },
                                    label = { Text("Home") },
                                    modifier = Modifier.testTag("nav_home")
                                )

                                NavigationBarItem(
                                    selected = currentRoute == ROUTE_SCANNER,
                                    onClick = {
                                        navController.navigate(ROUTE_SCANNER) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentRoute == ROUTE_SCANNER) Icons.Filled.QrCodeScanner else Icons.Outlined.QrCodeScanner,
                                            contentDescription = "Scanner"
                                        )
                                    },
                                    label = { Text("Barcode") },
                                    modifier = Modifier.testTag("nav_scanner")
                                )

                                NavigationBarItem(
                                    selected = currentRoute == ROUTE_PROFILE,
                                    onClick = {
                                        navController.navigate(ROUTE_PROFILE) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentRoute == ROUTE_PROFILE) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle,
                                            contentDescription = "Profile"
                                        )
                                    },
                                    label = { Text("Profil") },
                                    modifier = Modifier.testTag("nav_profile")
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DASHBOARD,
                        modifier = Modifier.padding(
                            bottom = if (isRootScreen) innerPadding.calculateBottomPadding() else 0.dp
                        )
                    ) {
                        composable(ROUTE_DASHBOARD) {
                            DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToDataMaster = { navController.navigate(ROUTE_DATA_MASTER) },
                                onNavigateToUploadFile = { navController.navigate(ROUTE_UPLOAD_FILE) },
                                onNavigateToDaftarTeknisi = { navController.navigate(ROUTE_TEKNISI) },
                                onNavigateToTrouble = { navController.navigate(ROUTE_TROUBLE) },
                                onNavigateToReport = { navController.navigate(ROUTE_REPORT) },
                                onNavigateToBatchMaintenance = { navController.navigate(ROUTE_BATCH_MAINTENANCE) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        composable(ROUTE_DATA_MASTER) {
                            DataMasterScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        composable(ROUTE_UPLOAD_FILE) {
                            UploadFileScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        composable(ROUTE_TEKNISI) {
                            DaftarTeknisiScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        composable(ROUTE_TROUBLE) {
                            TroubleScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        composable(ROUTE_SCANNER) {
                            BarcodeScannerScreen(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        composable(ROUTE_PROFILE) {
                            ProfileScreen(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        composable(ROUTE_REPORT) {
                            ReportScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        composable(ROUTE_BATCH_MAINTENANCE) {
                            InputMaintenanceScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ROUTE_DASHBOARD = "dashboard"
        const val ROUTE_DATA_MASTER = "data_master"
        const val ROUTE_UPLOAD_FILE = "upload_file"
        const val ROUTE_TEKNISI = "teknisi"
        const val ROUTE_TROUBLE = "trouble"
        const val ROUTE_SCANNER = "scanner"
        const val ROUTE_PROFILE = "profile"
        const val ROUTE_REPORT = "report"
        const val ROUTE_BATCH_MAINTENANCE = "batch_maintenance"
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
