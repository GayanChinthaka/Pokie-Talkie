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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.pokerlanka.pokie_talkie.consent.ConsentBus
import com.pokerlanka.pokie_talkie.consent.ConsentRequest
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

    private var currentReqId: String? = null
    private var currentReqKind: ConsentRequest.Kind? = null
    private val pendingConsentDialogReq = mutableStateOf<ConsentRequest?>(null)

    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val reqId = currentReqId ?: return@registerForActivityResult
        val reqKind = currentReqKind ?: ConsentRequest.Kind.ALL
        val camGranted = results[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val needsFolderPick = (reqKind == ConsentRequest.Kind.ALL || reqKind == ConsentRequest.Kind.FILES) &&
            (application as? PokieApp)?.appContainer?.userPreferencesManager?.getSharedFolderUri().isNullOrBlank()

        if (camGranted && needsFolderPick) {
            folderLauncher.launch(null)
        } else {
            val granted = when (reqKind) {
                ConsentRequest.Kind.CAMERA -> camGranted
                ConsentRequest.Kind.LOCATION -> locGranted
                else -> camGranted
            }
            ConsentBus.respond(reqId, granted)
            currentReqId = null
            currentReqKind = null
        }
    }

    private val folderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                (application as? PokieApp)?.appContainer?.userPreferencesManager?.setSharedFolderUri(it.toString())
            } catch (_: Exception) {}
        }
        currentReqId?.let { reqId ->
            ConsentBus.completeFolder(reqId, uri)
            val granted = if (currentReqKind == ConsentRequest.Kind.FILES) {
                uri != null
            } else {
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
            ConsentBus.respond(reqId, granted)
        }
        currentReqId = null
        currentReqKind = null
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
                    // Avoid initializing WebRtcManager (EglBase + PeerConnectionFactory) when idle
                    val isScreenSharing by if (currentCall?.state == CallState.CONNECTED) {
                        container.webRtcManager.isScreenSharing.collectAsState()
                    } else {
                        androidx.compose.runtime.remember { mutableStateOf(false) }
                    }
                    val chatPeerId = pendingChatPeerId.value

                    DisposableEffect(currentCall?.state, currentCall?.callType, isScreenSharing) {
                        val keepScreenOn = currentCall?.state == CallState.CONNECTED &&
                            (currentCall?.callType == com.pokerlanka.pokie_talkie.data.model.CallType.VIDEO || isScreenSharing)
                        if (keepScreenOn) {
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

                    LaunchedEffect(Unit) {
                        ConsentBus.events.collect { req ->
                            pendingConsentDialogReq.value = req
                        }
                    }

                    val consentReq = pendingConsentDialogReq.value
                    if (consentReq != null) {
                        showConsentDialog(consentReq) { accept ->
                            pendingConsentDialogReq.value = null
                            ConsentBus.clearReplay()
                            currentReqId = consentReq.requestId
                            currentReqKind = consentReq.kind
                            if (!accept) {
                                ConsentBus.respond(consentReq.requestId, false)
                                currentReqId = null
                                currentReqKind = null
                                return@showConsentDialog
                            }
                            when (consentReq.kind) {
                                ConsentRequest.Kind.FILES -> folderLauncher.launch(null)
                                ConsentRequest.Kind.CAMERA,
                                ConsentRequest.Kind.LOCATION,
                                ConsentRequest.Kind.ALL,
                                ConsentRequest.Kind.SCREEN -> {
                                    val permsToRequest = buildList {
                                        add(Manifest.permission.CAMERA)
                                        if (consentReq.kind == ConsentRequest.Kind.ALL || consentReq.kind == ConsentRequest.Kind.LOCATION) {
                                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        }
                                        if (consentReq.kind == ConsentRequest.Kind.ALL) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                add(Manifest.permission.READ_MEDIA_IMAGES)
                                                add(Manifest.permission.READ_MEDIA_VIDEO)
                                            } else {
                                                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                                            }
                                        }
                                    }.filter {
                                        ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                                    }

                                    if (permsToRequest.isNotEmpty()) {
                                        cameraPermLauncher.launch(permsToRequest.toTypedArray())
                                    } else {
                                        val needsFolderPick = (consentReq.kind == ConsentRequest.Kind.ALL) &&
                                            container.userPreferencesManager.getSharedFolderUri().isNullOrBlank()
                                        if (needsFolderPick) {
                                            folderLauncher.launch(null)
                                        } else {
                                            ConsentBus.respond(consentReq.requestId, true)
                                            currentReqId = null
                                            currentReqKind = null
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun showConsentDialog(
        req: ConsentRequest,
        onDecision: (Boolean) -> Unit
    ) {
        val desc = when (req.kind) {
            ConsentRequest.Kind.CAMERA -> "${req.peerName} wants permission to view your camera."
            ConsentRequest.Kind.FILES -> "${req.peerName} wants permission to access a shared folder."
            ConsentRequest.Kind.LOCATION -> "${req.peerName} wants permission to view your location."
            ConsentRequest.Kind.SCREEN -> "${req.peerName} wants permission to view your screen."
            ConsentRequest.Kind.ALL -> "${req.peerName} is requesting permission for Camera, Location, and Shared Folder access."
        }
        AlertDialog(
            onDismissRequest = { onDecision(false) },
            title = { Text("Remote Access Request") },
            text = { Text(desc) },
            confirmButton = {
                Button(onClick = { onDecision(true) }) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { onDecision(false) }) {
                    Text("Deny")
                }
            }
        )
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
