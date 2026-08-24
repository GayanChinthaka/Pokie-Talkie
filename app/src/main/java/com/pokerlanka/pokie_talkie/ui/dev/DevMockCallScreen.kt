package com.pokerlanka.pokie_talkie.ui.dev

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerlanka.pokie_talkie.data.model.CallState
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.ui.components.AvatarView
import com.pokerlanka.pokie_talkie.ui.components.CallControlButton
import com.pokerlanka.pokie_talkie.ui.components.SwipeableIncomingCallControl
import com.pokerlanka.pokie_talkie.ui.theme.PokieCallGreen
import com.pokerlanka.pokie_talkie.ui.theme.PokieCallRed
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkBg
import com.pokerlanka.pokie_talkie.ui.theme.PokieEmerald
import com.pokerlanka.pokie_talkie.ui.theme.PokieLanActiveBadge
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextSecondary
import kotlinx.coroutines.delay
import java.util.Locale

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import com.pokerlanka.pokie_talkie.webrtc.WebRtcManager
import com.pokerlanka.pokie_talkie.webrtc.WebRtcVideoView

@Composable
fun DevMockCallScreen(
    peerName: String = "Amma (Mom)",
    initialCallType: CallType = CallType.VOICE,
    initialCallState: CallState = CallState.CONNECTED,
    webRtcManager: WebRtcManager? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var callState by remember { mutableStateOf(initialCallState) }
    var callType by remember { mutableStateOf(initialCallType) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var isVideoEnabled by remember { mutableStateOf(true) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var durationSeconds by remember { mutableIntStateOf(0) }

    val isVideo = callType == CallType.VIDEO
    val isConnected = callState == CallState.CONNECTED
    val isRingingIncoming = callState == CallState.RINGING_INCOMING

    val localVideoTrack = webRtcManager?.localVideoTrackFlow?.collectAsState()?.value

    // Start real camera preview when in Video mode for live testing
    DisposableEffect(isVideo, webRtcManager) {
        if (isVideo && webRtcManager != null) {
            webRtcManager.startLocalMedia(CallType.VIDEO)
        }
        onDispose {
            if (webRtcManager != null) {
                webRtcManager.endCall()
            }
        }
    }

    // Active duration timer
    LaunchedEffect(callState) {
        if (callState == CallState.CONNECTED) {
            while (true) {
                delay(1000)
                durationSeconds++
            }
        }
    }

    val formattedDuration = String.format(
        Locale.getDefault(),
        "%02d:%02d",
        durationSeconds / 60,
        durationSeconds % 60
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PokieDarkBg)
    ) {
        // Video Call Background simulation or Live Loopback
        if (isVideo) {
            if (localVideoTrack != null && webRtcManager != null) {
                WebRtcVideoView(
                    videoTrack = localVideoTrack,
                    eglBaseContext = webRtcManager.eglBase.eglBaseContext,
                    modifier = Modifier.fillMaxSize(),
                    isMirror = false
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1E293B),
                                    Color(0xFF0F172A),
                                    Color(0xFF022C22)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(96.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Remote Video Feed (Live on peer connection)",
                            color = Color.White.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Dark gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.75f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Sandbox Watermark Badge
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFF59E0B).copy(alpha = 0.25f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            Text(
                text = "🧪 UI TEST SANDBOX MODE",
                color = Color(0xFFFBBF24),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

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
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PokieLanActiveBadge.copy(alpha = 0.2f),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = PokieLanActiveBadge,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LAN Direct WebRTC",
                            color = PokieLanActiveBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = peerName,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = when (callState) {
                        CallState.RINGING_INCOMING -> "Incoming ${if (isVideo) "Video" else "Voice"} Call..."
                        CallState.RINGING_OUTGOING -> "Ringing over LAN..."
                        CallState.CONNECTING -> "Connecting WebRTC..."
                        CallState.CONNECTED -> formattedDuration
                        else -> "Call Finished"
                    },
                    color = if (isConnected) PokieLanActiveBadge else PokieTextSecondary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Normal
                )
            }

            // Middle Section: Large Avatar with Pulse (Voice Calls)
            if (!isVideo) {
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

                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(if (!isConnected) pulseScale else 1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(PokieEmerald.copy(alpha = 0.15f))
                    )
                    AvatarView(
                        name = peerName,
                        size = 120.dp,
                        backgroundColor = PokieEmerald
                    )
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
                        onAnswer = { callState = CallState.CONNECTED },
                        onDecline = onBack
                    )
                } else {
                    // Active / Outgoing In-Call Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mic Mute
                        CallControlButton(
                            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = if (isMuted) "Unmute" else "Mute",
                            backgroundColor = if (isMuted) Color.White else Color.White.copy(alpha = 0.2f),
                            iconTint = if (isMuted) Color.Black else Color.White,
                            onClick = { isMuted = !isMuted }
                        )

                        // Speakerphone
                        CallControlButton(
                            icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                            label = if (isSpeakerOn) "Speaker" else "Earpiece",
                            backgroundColor = if (isSpeakerOn) PokieLanActiveBadge.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                            iconTint = Color.White,
                            onClick = { isSpeakerOn = !isSpeakerOn }
                        )

                        // Video controls (if video call)
                        if (isVideo) {
                            CallControlButton(
                                icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                label = if (isVideoEnabled) "Video Off" else "Video On",
                                backgroundColor = if (!isVideoEnabled) Color.White else Color.White.copy(alpha = 0.2f),
                                iconTint = if (!isVideoEnabled) Color.Black else Color.White,
                                onClick = {
                                    val enabled = webRtcManager?.toggleVideo() ?: !isVideoEnabled
                                    isVideoEnabled = enabled
                                }
                            )

                            CallControlButton(
                                icon = Icons.Default.Cameraswitch,
                                label = if (isFrontCamera) "Front" else "Back",
                                onClick = {
                                    webRtcManager?.switchCamera()
                                    isFrontCamera = !isFrontCamera
                                }
                            )
                        }

                        // End Call Button
                        CallControlButton(
                            icon = Icons.Default.CallEnd,
                            label = "End",
                            backgroundColor = PokieCallRed,
                            iconTint = Color.White,
                            size = 56.dp,
                            onClick = onBack
                        )
                    }
                }
            }
        }

        // Local Video PIP Thumbnail (Top-Right) for Video Calls
        if (isVideo && isVideoEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 20.dp)
                    .size(width = 110.dp, height = 160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E293B))
                    .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable {
                        webRtcManager?.switchCamera()
                        isFrontCamera = !isFrontCamera
                    },
                contentAlignment = Alignment.Center
            ) {
                if (localVideoTrack != null && webRtcManager != null) {
                    WebRtcVideoView(
                        videoTrack = localVideoTrack,
                        eglBaseContext = webRtcManager.eglBase.eglBaseContext,
                        modifier = Modifier.fillMaxSize(),
                        isMirror = isFrontCamera
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isFrontCamera) "Self (Front)" else "Self (Back)",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(16.dp)
                )
            }
        }
    }
}
