package com.pokerlanka.pokie_talkie

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.pokerlanka.pokie_talkie.data.model.CallState
import com.pokerlanka.pokie_talkie.ui.navigation.PokieNavGraph
import com.pokerlanka.pokie_talkie.ui.navigation.Screen
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkBg
import com.pokerlanka.pokie_talkie.ui.theme.PokieTalkieTheme

class MainActivity : ComponentActivity() {

    private val requiredPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.MANAGE_OWN_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Bluetooth permissions (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        // Nearby Wi-Fi Devices (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(
                this,
                "Microphone, Camera, Bluetooth & Local Network permissions are recommended for full proximity calling features.",
                Toast.LENGTH_LONG
            ).show()
        }
        com.pokerlanka.pokie_talkie.service.PokieLocalService.startService(this)
        (application as? PokieApp)?.appContainer?.ensureListenersActive()
    }

    private val pendingChatPeerId = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("peerId")?.let { peerId ->
            if (peerId.isNotBlank()) {
                pendingChatPeerId.value = peerId
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as PokieApp
        val container = app.appContainer

        // Only on a fresh start: on recreation (rotation, theme change) getIntent() still carries the
        // old notification's peerId, and the restored back stack already has that chat.
        if (savedInstanceState == null) {
            intent?.getStringExtra("peerId")?.let { peerId ->
                if (peerId.isNotBlank()) {
                    pendingChatPeerId.value = peerId
                }
            }
        }

        checkAndRequestPermissions()

        setContent {
            val themeMode by container.userPreferencesManager.themeModeFlow.collectAsState()
            val accentColor by container.userPreferencesManager.themeAccentColorFlow.collectAsState()

            PokieTalkieTheme(themeMode = themeMode, accentColor = accentColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val currentCall by container.callRepository.currentCall.collectAsState()
                    val chatPeerId = pendingChatPeerId.value

                    DisposableEffect(currentCall?.state) {
                        if (currentCall?.state == CallState.CONNECTED) {
                            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                        onDispose {
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }

                    // Auto navigate to chat when notification is clicked
                    LaunchedEffect(chatPeerId) {
                        chatPeerId?.let { peerId ->
                            navController.navigate(Screen.Chat.createRoute(peerId)) {
                                launchSingleTop = true
                            }
                            pendingChatPeerId.value = null
                        }
                    }

                    // Auto navigate to call screen when an incoming call arrives or starts
                    LaunchedEffect(currentCall) {
                        currentCall?.let { call ->
                            if (call.state == CallState.RINGING_INCOMING ||
                                call.state == CallState.RINGING_OUTGOING ||
                                call.state == CallState.CONNECTED
                            ) {
                                val currentRoute = navController.currentDestination?.route
                                if (currentRoute == null || !currentRoute.startsWith("call/")) {
                                    navController.navigate(Screen.Call.createRoute(call.peerId, call.callType))
                                }
                            }
                        }
                    }

                    PokieNavGraph(
                        navController = navController,
                        appContainer = container
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            com.pokerlanka.pokie_talkie.service.PokieLocalService.startService(this)
            (application as? PokieApp)?.appContainer?.ensureListenersActive()
        }
    }

    override fun onResume() {
        super.onResume()
        (application as? PokieApp)?.appContainer?.ensureListenersActive()
    }
}
