package com.kire.remotecontroller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kire.remotecontroller.ui.AppViewModel
import com.kire.remotecontroller.ui.DiscoverScreen
import com.kire.remotecontroller.ui.EpgScreen
import com.kire.remotecontroller.ui.PairingScreen
import com.kire.remotecontroller.ui.RemoteScreen
import com.kire.remotecontroller.ui.RemoteTheme

class MainActivity : ComponentActivity() {
    private var viewModel: AppViewModel? = null

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onPermissionsReady() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            RemoteTheme {
                RemoteApp(
                    onViewModelReady = { vm ->
                        viewModel = vm
                        requestPermissionsIfNeeded()
                    },
                )
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            onPermissionsReady()
        } else {
            permissionsLauncher.launch(needed.toTypedArray())
        }
    }

    private fun onPermissionsReady() {
        viewModel?.onPermissionsReady()
    }

    private fun requiredPermissions(): List<String> {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        return permissions
    }
}

@Composable
private fun RemoteApp(onViewModelReady: (AppViewModel) -> Unit) {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()
    LaunchedEffect(vm) {
        onViewModelReady(vm)
    }

    NavHost(navController = nav, startDestination = "discover") {
        composable("discover") {
            DiscoverScreen(vm) {
                nav.navigate(
                    if (!vm.needsPhilipsPairing && !vm.needsAtvPairing) "remote" else "pairing",
                )
            }
        }
        composable("pairing") {
            PairingScreen(vm) { nav.navigate("remote") { popUpTo("discover") } }
        }
        composable("remote") {
            RemoteScreen(vm, onOpenGuide = { nav.navigate("epg") })
        }
        composable("epg") {
            EpgScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}
