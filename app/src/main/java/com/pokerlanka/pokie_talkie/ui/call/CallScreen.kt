package com.pokerlanka.pokie_talkie.ui.call

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.compose.ui.platform.LocalContext
import com.pokerlanka.pokie_talkie.data.model.CallState
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.ui.components.AvatarView
import com.pokerlanka.pokie_talkie.ui.components.CallControlButton
import com.pokerlanka.pokie_talkie.ui.components.SwipeableIncomingCallControl
import com.pokerlanka.pokie_talkie.ui.theme.PokieCallGreen
import com.pokerlanka.pokie_talkie.ui.theme.PokieCallRed
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkBg
import com.pokerlanka.pokie_talkie.ui.theme.PokieLanActiveBadge
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextPrimary
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextSecondary
import com.pokerlanka.pokie_talkie.webrtc.AudioRoute
import com.pokerlanka.pokie_talkie.webrtc.WebRtcVideoView
import org.webrtc.RendererCommon
import java.util.Locale

@Composable
fun CallScreen(
    viewModel: CallViewModel,
    onCallEnded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val callSession by viewModel.currentCall.collectAsState()
    val peerContact by viewModel.peerContact.collectAsState()
    val localVideoTrack by viewModel.localVideoTrack.collectAsState()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isVideoEnabled by viewModel.isVideoEnabled.collectAsState()
    val isRemoteVideoEnabled by viewModel.isRemoteVideoEnabled.collectAsState()
    val isScreenSharing by viewModel.isScreenSharing.collectAsState()
    val isRemoteScreenSharing by viewModel.isRemoteScreenSharing.collectAsState()
    val activeAudioRoute by viewModel.activeAudioRoute.collectAsState()
    val availableAudioRoutes by viewModel.availableAudioRoutes.collectAsState()
    val videoUpgradeRequester by viewModel.videoUpgradeRequest.collectAsState()
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var showAudioRouteSheet by remember { mutableStateOf(false) }

    val screenShareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startScreenShare(result.data!!)
        }
    }

    val isCallActive = callSession?.state in listOf(
        CallState.RINGING_INCOMING,
        CallState.RINGING_OUTGOING,
        CallState.CONNECTED
    )
    BackHandler(enabled = isCallActive) {
        (context as? Activity)?.moveTaskToBack(true)
    }

    LaunchedEffect(Unit) {
        viewModel.uiMessage.collect { msg ->
            snackbarMessage = msg
            kotlinx.coroutines.delay(3200)
            if (snackbarMessage == msg) snackbarMessage = null
        }
    }

    // Automatically navigate back when call is finished
    LaunchedEffect(callSession?.state) {
        if (callSession == null ||
            callSession?.state == CallState.DISCONNECTED ||
            callSession?.state == CallState.REJECTED ||
            callSession?.state == CallState.MISSED
        ) {
            onCallEnded()
        }
    }

    val isVideo = callSession?.callType == CallType.VIDEO || isRemoteScreenSharing || isScreenSharing
    val isRingingIncoming = callSession?.state == CallState.RINGING_INCOMING
    val isConnected = callSession?.state == CallState.CONNECTED
    val isBtCall = callSession?.isBluetoothVoice == true
    val formattedDuration = callSession?.durationSeconds?.let { seconds ->
        String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
    } ?: "00:00"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PokieDarkBg)
    ) {
        // Video Call Background / Remote Video Track (only when remote camera is enabled)
        if (isVideo && remoteVideoTrack != null && isRemoteVideoEnabled) {
            WebRtcVideoView(
                videoTrack = remoteVideoTrack,
                eglBaseContext = viewModel.eglBaseContext,
                modifier = Modifier.fillMaxSize(),
                isMirror = false,
                scalingType = if (isRemoteScreenSharing) {
                    RendererCommon.ScalingType.SCALE_ASPECT_FIT
                } else {
                    RendererCommon.ScalingType.SCALE_ASPECT_FILL
                }
            )
        }

        // Dark gradient overlay for visibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (remoteVideoTrack != null) 0.35f else 0.75f),
                            Color.Transparent,
                            Color.Black.copy(alpha = if (remoteVideoTrack != null) 0.45f else 0.85f)
                        )
                    )
                )
        )

        // Main Content Layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Peer Info & Call Status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 32.dp)
            ) {
                // Transport Badge
                val badgeColor = if (isBtCall) Color(0xFF2979FF) else PokieLanActiveBadge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeColor.copy(alpha = 0.2f),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isBtCall) Icons.Default.Bluetooth else Icons.Default.Wifi,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBtCall) "Bluetooth Walkie-Talkie" else "LAN Direct WebRTC",
                            color = badgeColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = callSession?.peerName ?: "Family Member",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = when (callSession?.state) {
                        CallState.RINGING_INCOMING -> "Incoming ${if (isVideo) "Video" else "Voice"} Call..."
                        CallState.RINGING_OUTGOING -> if (isBtCall) "Calling over Bluetooth..." else "Ringing..."
                        CallState.CONNECTING -> if (isBtCall) "Connecting Bluetooth Audio..." else "Connecting WebRTC..."
                        CallState.CONNECTED -> if (isBtCall) "Connected • $formattedDuration" else formattedDuration
                        else -> "Connecting..."
                    },
                    color = if (isConnected) badgeColor else PokieTextSecondary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Normal
                )
            }

            // Middle Section: Voice Call Avatar OR Video Call Camera-Off view (No plain black screen!)
            val isRemoteVideoActive = isVideo && remoteVideoTrack != null && isRemoteVideoEnabled
            if (!isRemoteVideoActive && !isScreenSharing) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (isConnected) 1.08f else 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "avatarPulse"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .scale(if (!isConnected) pulseScale else 1f),
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer Ring
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        )
                        AvatarView(
                            name = callSession?.peerName ?: "Family",
                            size = 120.dp,
                            backgroundColor = Color(peerContact?.avatarColor ?: MaterialTheme.colorScheme.primary.value.toLong()),
                            imagePath = peerContact?.profileImagePath
                        )
                    }

                    // Crossed-out camera badge shown when it's a Video Call and peer has camera off
                    if (isVideo && !isRemoteVideoEnabled && !isScreenSharing) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideocamOff,
                                    contentDescription = "Camera is off",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${callSession?.peerName ?: "Peer"}'s camera is off",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Actions & Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                if (isRingingIncoming) {
                    // Swipeable & Tappable Incoming Call Accept / Decline with Animated Chevrons
                    SwipeableIncomingCallControl(
                        isVideo = isVideo,
                        onAnswer = viewModel::answerCall,
                        onDecline = viewModel::hangupCall
                    )
                } else {
                    // Active / Outgoing Call In-Call Controls (Two clean rows so buttons never crowd)
                    val isCameraVideoControls = callSession?.callType == CallType.VIDEO && !isScreenSharing
                    val hasShareControl = !isBtCall && isConnected
                    val topRowCount = 2 + (if (isCameraVideoControls) 2 else if (!isScreenSharing && !isBtCall) 1 else 0) + (if (hasShareControl) 1 else 0)
                    val controlBtnSize = if (topRowCount >= 5) 48.dp else 54.dp

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Row 1: Audio / Video / Screen Share toggles
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(vertical = 12.dp, horizontal = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mic Mute
                            CallControlButton(
                                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                label = if (isMuted) "Unmute" else "Mute",
                                backgroundColor = if (isMuted) Color.White else Color.White.copy(alpha = 0.2f),
                                iconTint = if (isMuted) Color.Black else Color.White,
                                size = controlBtnSize,
                                onClick = viewModel::toggleMute
                            )

                            // Audio Output Route (Bluetooth / Speaker / Earpiece / Headset)
                            val audioRouteIcon = when (activeAudioRoute) {
                                AudioRoute.BLUETOOTH -> Icons.Default.Bluetooth
                                AudioRoute.WIRED_HEADSET -> Icons.Default.Headset
                                AudioRoute.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
                                AudioRoute.EARPIECE -> Icons.AutoMirrored.Filled.VolumeDown
                            }
                            val audioRouteLabel = when (activeAudioRoute) {
                                AudioRoute.BLUETOOTH -> "Bluetooth"
                                AudioRoute.WIRED_HEADSET -> "Headset"
                                AudioRoute.SPEAKER -> "Speaker"
                                AudioRoute.EARPIECE -> "Earpiece"
                            }
                            val isAudioRouteHighlighted = activeAudioRoute != AudioRoute.EARPIECE
                            CallControlButton(
                                icon = audioRouteIcon,
                                label = audioRouteLabel,
                                backgroundColor = if (isAudioRouteHighlighted) PokieLanActiveBadge.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                                iconTint = Color.White,
                                size = controlBtnSize,
                                onClick = {
                                    if (availableAudioRoutes.size > 2) {
                                        showAudioRouteSheet = true
                                    } else {
                                        viewModel.toggleSpeaker()
                                    }
                                }
                            )

                            // Video / Camera Toggle (if video call & not currently screen sharing)
                            if (isCameraVideoControls) {
                                CallControlButton(
                                    icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                    label = if (isVideoEnabled) "Cam Off" else "Cam On",
                                    backgroundColor = if (!isVideoEnabled) Color.White else Color.White.copy(alpha = 0.2f),
                                    iconTint = if (!isVideoEnabled) Color.Black else Color.White,
                                    size = controlBtnSize,
                                    onClick = viewModel::toggleVideo
                                )

                                CallControlButton(
                                    icon = Icons.Default.Cameraswitch,
                                    label = "Flip",
                                    size = controlBtnSize,
                                    onClick = viewModel::switchCamera
                                )
                            } else if (!isScreenSharing && !isBtCall) {
                                // Switch to Video Call Option for Audio Calls
                                CallControlButton(
                                    icon = Icons.Default.Videocam,
                                    label = "Video",
                                    backgroundColor = Color.White.copy(alpha = 0.2f),
                                    iconTint = Color.White,
                                    size = controlBtnSize,
                                    onClick = viewModel::requestSwitchToVideo
                                )
                            }

                            // Screen Share Toggle (for active LAN/WebRTC calls)
                            if (hasShareControl) {
                                CallControlButton(
                                    icon = if (isScreenSharing) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Filled.ScreenShare,
                                    label = if (isScreenSharing) "Stop" else "Share",
                                    backgroundColor = if (isScreenSharing) Color(0xFF2979FF) else Color.White.copy(alpha = 0.2f),
                                    iconTint = Color.White,
                                    size = controlBtnSize,
                                    onClick = {
                                        if (isScreenSharing) {
                                            viewModel.stopScreenShare()
                                        } else {
                                            val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                            screenShareLauncher.launch(mpm.createScreenCaptureIntent())
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Row 2: Dedicated Centered End Call Button
                        CallControlButton(
                            icon = Icons.Default.CallEnd,
                            label = "End Call",
                            backgroundColor = PokieCallRed,
                            iconTint = Color.White,
                            size = 64.dp,
                            onClick = viewModel::hangupCall
                        )
                    }
                }
            }
        }

        // Local Video PIP Thumbnail (Top-Right) for Camera Video Calls (hidden while screen sharing)
        if (callSession?.callType == CallType.VIDEO && !isScreenSharing && localVideoTrack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 20.dp)
                    .size(width = 110.dp, height = 160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E1E).copy(alpha = 0.95f))
                    .border(2.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .clickable {
                        if (isVideoEnabled) viewModel.switchCamera() else viewModel.toggleVideo()
                    }
            ) {
                if (isVideoEnabled) {
                    WebRtcVideoView(
                        videoTrack = localVideoTrack,
                        eglBaseContext = viewModel.eglBaseContext,
                        modifier = Modifier.fillMaxSize(),
                        isMirror = true
                    )

                    // Tap to switch icon hint
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(16.dp)
                    )
                } else {
                    // Camera Off crossed-out camera icon placeholder
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideocamOff,
                                contentDescription = "Camera Off",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Camera off",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap to enable",
                            color = PokieTextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        // Incoming Switch to Video Request Dialog
        if (videoUpgradeRequester != null) {
            AlertDialog(
                onDismissRequest = { viewModel.declineSwitchToVideo() },
                containerColor = PokieDarkBg,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "Switch to Video Call?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = "$videoUpgradeRequester wants to switch this call to video.",
                        color = PokieTextSecondary,
                        textAlign = TextAlign.Center
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.acceptSwitchToVideo() },
                        colors = ButtonDefaults.buttonColors(containerColor = PokieCallGreen)
                    ) {
                        Text("Switch to Video", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.declineSwitchToVideo() }) {
                        Text("Decline", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            )
        }

        // Remote Screen Sharing indicator pill
        if (isRemoteScreenSharing) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 138.dp)
                    .zIndex(9f),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF2979FF).copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ScreenShare,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${callSession?.peerName ?: "Peer"} is sharing screen",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Local Screen Sharing active banner with quick Stop button
        if (isScreenSharing) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .zIndex(9f),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.82f),
                border = BorderStroke(1.dp, Color(0xFF2979FF).copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ScreenShare,
                        contentDescription = null,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "You are sharing your screen",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${callSession?.peerName ?: "Family member"} can see your display",
                        color = PokieTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = viewModel::stopScreenShare,
                        colors = ButtonDefaults.buttonColors(containerColor = PokieCallRed)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.StopScreenShare,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop Sharing", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Audio Output Route Picker Sheet (Bluetooth / Phone Speaker / Earpiece / Wired Headset)
        if (showAudioRouteSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAudioRouteSheet = false },
                containerColor = Color(0xFF1A222C),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp)
                ) {
                    Text(
                        text = "Audio Output",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    availableAudioRoutes.forEach { option ->
                        val selected = option.route == activeAudioRoute
                        val optionIcon = when (option.route) {
                            AudioRoute.BLUETOOTH -> Icons.Default.Bluetooth
                            AudioRoute.WIRED_HEADSET -> Icons.Default.Headset
                            AudioRoute.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
                            AudioRoute.EARPIECE -> Icons.Default.PhoneInTalk
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    viewModel.selectAudioRoute(option.route)
                                    showAudioRouteSheet = false
                                },
                            color = if (selected) PokieLanActiveBadge.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = optionIcon,
                                        contentDescription = null,
                                        tint = if (selected) PokieLanActiveBadge else Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = option.label,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = PokieLanActiveBadge,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating in-call notification chip
        snackbarMessage?.let { msg ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 95.dp)
                    .zIndex(10f),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Text(
                    text = msg,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
