package com.pokerlanka.pokie_talkie

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(
                this,
                "Microphone, Camera & Call permissions are recommended for full LAN calling features.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        val app = application as PokieApp
        val container = app.appContainer

        checkAndRequestPermissions()

        setContent {
            val themeMode by container.userPreferencesManager.themeModeFlow.collectAsState()

            PokieTalkieTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val currentCall by container.callRepository.currentCall.collectAsState()

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
        }
    }
}
