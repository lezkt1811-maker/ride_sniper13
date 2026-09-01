package com.ridesniper.app.ui

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ridesniper.app.RideSniperApp
import com.ridesniper.app.service.RideSniperForegroundService
import com.ridesniper.app.ui.screens.*
import com.ridesniper.app.ui.theme.RideSniperTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RideSniperViewModel by viewModels {
        RideSniperViewModel.Factory(application as RideSniperApp)
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* user returns from Settings; re-check on resume */ }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, RideSniperForegroundService::class.java).apply {
                action = RideSniperForegroundService.ACTION_START
                putExtra(RideSniperForegroundService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(RideSniperForegroundService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless; notification is best-effort on Android 13+ */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        RideSniperForegroundService.onNeedsManualCorrection = { parsed ->
            runOnUiThread { viewModel.requestManualCorrection(parsed) }
        }

        setContent {
            RideSniperTheme {
                RideSniperRoot(
                    viewModel = viewModel,
                    hasOverlayPermission = { hasOverlayPermission() },
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onStartAnalyzerService = { requestNotificationPermissionThenProjection() }
                )
            }
        }
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestNotificationPermissionThenProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            if (granted != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}

@Composable
fun RideSniperRoot(
    viewModel: RideSniperViewModel,
    hasOverlayPermission: () -> Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartAnalyzerService: () -> Unit
) {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { RideSniperBottomBar(navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    hasOverlayPermission = hasOverlayPermission(),
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onStartAnalyzer = onStartAnalyzerService
                )
            }
            composable("history") { HistoryScreen(viewModel) }
            composable("stats") { StatsScreen(viewModel) }
            composable("settings") { SettingsScreen(viewModel) }
            composable("debug") { DebugScreen() }
        }
    }

    val pendingCorrection by viewModel.pendingCorrection.collectAsState()
    pendingCorrection?.let { parsed ->
        CorrectionSheet(
            parsed = parsed,
            onDismiss = { viewModel.dismissManualCorrection() },
            onCalculate = { input -> viewModel.submitManualCorrection(input) }
        )
    }
}

@Composable
private fun RideSniperBottomBar(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination?.route

    NavigationBar {
        val items = listOf(
            Triple("home", "Home", Icons.Filled.Home),
            Triple("history", "History", Icons.Filled.History),
            Triple("stats", "Stats", Icons.Filled.BarChart),
            Triple("settings", "Settings", Icons.Filled.Settings),
            Triple("debug", "Debug", Icons.Filled.BugReport)
        )
        items.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = current == route,
                onClick = {
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}
