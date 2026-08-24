package com.pokerlanka.pokie_talkie.ui.call

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextPrimary
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextSecondary
import com.pokerlanka.pokie_talkie.webrtc.WebRtcVideoView
import java.util.Locale

@Composable
fun CallScreen(
    viewModel: CallViewModel,
    onCallEnded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val callSession by viewModel.currentCall.collectAsState()
    val localVideoTrack by viewModel.localVideoTrack.collectAsState()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isVideoEnabled by viewModel.isVideoEnabled.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()

    // Automatically navigate back when call is finished
    LaunchedEffect(callSession?.state) {
        if (callSession == null ||
            callSession?.state == CallState.DISCONNECTED ||
            callSession?.state == CallState.REJECTED
        ) {
            onCallEnded()
        }
    }

    val isVideo = callSession?.callType == CallType.VIDEO
    val isRingingIncoming = callSession?.state == CallState.RINGING_INCOMING
    val isConnected = callSession?.state == CallState.CONNECTED
    val formattedDuration = callSession?.durationSeconds?.let { seconds ->
        String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
    } ?: "00:00"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PokieDarkBg)
    ) {
        // Video Call Background / Remote Video Track
        if (isVideo && remoteVideoTrack != null) {
            WebRtcVideoView(
                videoTrack = remoteVideoTrack,
                eglBaseContext = viewModel.eglBaseContext,
                modifier = Modifier.fillMaxSize(),
                isMirror = false
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
                // LAN Badge
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
                    text = callSession?.peerName ?: "Family Member",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = when (callSession?.state) {
                        CallState.RINGING_INCOMING -> "Incoming ${if (isVideo) "Video" else "Voice"} Call..."
                        CallState.RINGING_OUTGOING -> "Ringing over LAN..."
                        CallState.CONNECTING -> "Connecting WebRTC..."
                        CallState.CONNECTED -> formattedDuration
                        else -> "Connecting..."
                    },
                    color = if (isConnected) PokieLanActiveBadge else PokieTextSecondary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Normal
                )
            }

            // Middle Section: Large Avatar with Pulse (Voice Calls)
            if (!isVideo || remoteVideoTrack == null) {
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
                    // Outer Ring
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(PokieEmerald.copy(alpha = 0.15f))
                    )
                    AvatarView(
                        name = callSession?.peerName ?: "Family",
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
                        onAnswer = viewModel::answerCall,
                        onDecline = viewModel::hangupCall
                    )
                } else {
                    // Active / Outgoing Call In-Call Controls
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
                            onClick = viewModel::toggleMute
                        )

                        // Speakerphone
                        CallControlButton(
                            icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                            label = if (isSpeakerOn) "Speaker" else "Earpiece",
                            backgroundColor = if (isSpeakerOn) PokieLanActiveBadge.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                            iconTint = Color.White,
                            onClick = viewModel::toggleSpeaker
                        )

                        // Video / Camera Toggle (if video call)
                        if (isVideo) {
                            CallControlButton(
                                icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                label = if (isVideoEnabled) "Video Off" else "Video On",
                                backgroundColor = if (!isVideoEnabled) Color.White else Color.White.copy(alpha = 0.2f),
                                iconTint = if (!isVideoEnabled) Color.Black else Color.White,
                                onClick = viewModel::toggleVideo
                            )

                            CallControlButton(
                                icon = Icons.Default.Cameraswitch,
                                label = "Flip",
                                onClick = viewModel::switchCamera
                            )
                        }

                        // End Call Button
                        CallControlButton(
                            icon = Icons.Default.CallEnd,
                            label = "End",
                            backgroundColor = PokieCallRed,
                            iconTint = Color.White,
                            size = 56.dp,
                            onClick = viewModel::hangupCall
                        )
                    }
                }
            }
        }

        // Local Video PIP Thumbnail (Top-Right) for Video Calls
        if (isVideo && localVideoTrack != null && isVideoEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 20.dp)
                    .size(width = 110.dp, height = 160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .clickable { viewModel.switchCamera() }
            ) {
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
            }
        }
    }
}
