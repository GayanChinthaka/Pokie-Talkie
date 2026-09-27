package com.pokerlanka.pokie_talkie.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerlanka.pokie_talkie.data.model.Message
import com.pokerlanka.pokie_talkie.data.model.MessageStatus
import com.pokerlanka.pokie_talkie.data.model.MessageType
import com.pokerlanka.pokie_talkie.ui.theme.PokieCallGreen
import com.pokerlanka.pokie_talkie.ui.theme.PokieCallRed
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkBg
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkBubbleMe
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkBubblePeer
import com.pokerlanka.pokie_talkie.ui.theme.PokieLanActiveBadge
import com.pokerlanka.pokie_talkie.ui.theme.PokieLightBubbleMe
import com.pokerlanka.pokie_talkie.ui.theme.PokieLightBubblePeer
import com.pokerlanka.pokie_talkie.ui.theme.PokieLightTextPrimary
import com.pokerlanka.pokie_talkie.ui.theme.PokieLightTextSecondary
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextPrimary
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextSecondary
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AvatarView(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    imagePath: String? = null,
    isOnlineLan: Boolean = false
) {
    val initials = name.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()

    val bitmap = remember(imagePath, size) {
        if (!imagePath.isNullOrBlank()) {
            val file = java.io.File(imagePath)
            if (file.exists()) {
                try {
                    val boundsOptions = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
                    val targetPx = (size.value * 3).toInt().coerceAtLeast(1)
                    var sampleSize = 1
                    while (boundsOptions.outHeight / (sampleSize * 2) >= targetPx &&
                           boundsOptions.outWidth / (sampleSize * 2) >= targetPx) {
                        sampleSize *= 2
                    }
                    val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                } catch (e: Exception) {
                    null
                }
            } else null
        } else null
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = initials.ifEmpty { "P" },
                    color = Color.White,
                    fontSize = (size.value * 0.4).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isOnlineLan) {
            Box(
                modifier = Modifier
                    .size(size * 0.30f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun LanStatusBadge(
    isOnlineLan: Boolean,
    bluetoothAddress: String? = null,
    modifier: Modifier = Modifier
) {
    val isBtReady = !bluetoothAddress.isNullOrBlank()
    val lanColor = MaterialTheme.colorScheme.primary
    val badgeBg = when {
        isOnlineLan -> lanColor.copy(alpha = 0.15f)
        isBtReady -> androidx.compose.ui.graphics.Color(0xFF2979FF).copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    }
    val badgeContentColor = when {
        isOnlineLan -> lanColor
        isBtReady -> androidx.compose.ui.graphics.Color(0xFF2979FF)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val badgeIcon = when {
        isOnlineLan -> Icons.Default.Wifi
        isBtReady -> Icons.Default.Bluetooth
        else -> Icons.Default.Sensors
    }
    val badgeText = when {
        isOnlineLan -> "LAN Active"
        isBtReady -> "Bluetooth Ready"
        else -> "Bluetooth Proximity"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = badgeBg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = badgeIcon,
                contentDescription = null,
                tint = badgeContentColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = badgeText,
                color = badgeContentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun CallControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White.copy(alpha = 0.2f),
    iconTint: Color = Color.White,
    size: Dp = 56.dp
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(backgroundColor)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(size * 0.48f)
            )
        }
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun SwipeableIncomingCallControl(
    isVideo: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val declineOffsetX = remember { Animatable(0f) }
    val answerOffsetX = remember { Animatable(0f) }

    // Pulsing animations for chevrons & halos
    val infiniteTransition = rememberInfiniteTransition(label = "swipeAnim")
    val leftArrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "leftArrow"
    )
    val rightArrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rightArrow"
    )
    val chevronAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chevronAlpha"
    )
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloPulse"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT: Decline Area (<<< [Red Button])
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Start
            ) {
                // Animated Left Chevrons <<<
                Row(
                    modifier = Modifier
                        .offset(x = leftArrowOffset.dp)
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Swipe Left to Decline",
                        tint = PokieCallRed.copy(alpha = (chevronAlpha * 0.5f).coerceIn(0f, 1f)),
                        modifier = Modifier.size(20.dp)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = PokieCallRed.copy(alpha = (chevronAlpha * 0.8f).coerceIn(0f, 1f)),
                        modifier = Modifier.size(20.dp).offset(x = (-4).dp)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = PokieCallRed.copy(alpha = chevronAlpha),
                        modifier = Modifier.size(20.dp).offset(x = (-8).dp)
                    )
                }

                // Decline Action Button (Draggable left & Tappable)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset { IntOffset(declineOffsetX.value.roundToInt(), 0) }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (declineOffsetX.value < -70f) {
                                        onDecline()
                                    } else {
                                        coroutineScope.launch {
                                            declineOffsetX.animateTo(0f, tween(300))
                                        }
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    val newOffset = (declineOffsetX.value + dragAmount).coerceIn(-160f, 0f)
                                    coroutineScope.launch {
                                        declineOffsetX.snapTo(newOffset)
                                    }
                                }
                            )
                        }
                ) {
                    // Pulsing Halo
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(haloPulse)
                            .clip(CircleShape)
                            .background(PokieCallRed.copy(alpha = 0.22f))
                    )
                    IconButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(PokieCallRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Decline Call",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // RIGHT: Answer Area ([Green Button] >>>)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End
            ) {
                // Answer Action Button (Draggable right & Tappable)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset { IntOffset(answerOffsetX.value.roundToInt(), 0) }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (answerOffsetX.value > 70f) {
                                        onAnswer()
                                    } else {
                                        coroutineScope.launch {
                                            answerOffsetX.animateTo(0f, tween(300))
                                        }
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    val newOffset = (answerOffsetX.value + dragAmount).coerceIn(0f, 160f)
                                    coroutineScope.launch {
                                        answerOffsetX.snapTo(newOffset)
                                    }
                                }
                            )
                        }
                ) {
                    // Pulsing Halo
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(haloPulse)
                            .clip(CircleShape)
                            .background(PokieCallGreen.copy(alpha = 0.25f))
                    )
                    IconButton(
                        onClick = onAnswer,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(PokieCallGreen)
                    ) {
                        Icon(
                            imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = "Accept Call",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Animated Right Chevrons >>>
                Row(
                    modifier = Modifier
                        .offset(x = rightArrowOffset.dp)
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Swipe Right to Answer",
                        tint = PokieCallGreen.copy(alpha = chevronAlpha),
                        modifier = Modifier.size(20.dp)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = PokieCallGreen.copy(alpha = (chevronAlpha * 0.8f).coerceIn(0f, 1f)),
                        modifier = Modifier.size(20.dp).offset(x = (-4).dp)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = PokieCallGreen.copy(alpha = (chevronAlpha * 0.5f).coerceIn(0f, 1f)),
                        modifier = Modifier.size(20.dp).offset(x = (-8).dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val isMe = message.isFromMe
    val isDark = MaterialTheme.colorScheme.background == PokieDarkBg
    val bubbleColor = if (isMe) {
        if (isDark) PokieDarkBubbleMe else PokieLightBubbleMe
    } else {
        if (isDark) PokieDarkBubblePeer else PokieLightBubblePeer
    }
    val contentTextColor = if (isMe) {
        if (isDark) PokieTextPrimary else PokieLightTextPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val timeTextColor = if (isMe) {
        if (isDark) PokieTextSecondary else PokieLightTextSecondary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val formattedTime = remember(message.timestamp) {
        val nowCal = java.util.Calendar.getInstance()
        val msgCal = java.util.Calendar.getInstance().apply { timeInMillis = message.timestamp }
        val timeStr = android.text.format.DateFormat.getTimeFormat(context).format(Date(message.timestamp))
        val isToday = nowCal.get(java.util.Calendar.YEAR) == msgCal.get(java.util.Calendar.YEAR) &&
                      nowCal.get(java.util.Calendar.DAY_OF_YEAR) == msgCal.get(java.util.Calendar.DAY_OF_YEAR)
        if (isToday) {
            timeStr
        } else {
            val dateStr = android.text.format.DateFormat.getMediumDateFormat(context).format(Date(message.timestamp))
            "$dateStr, $timeStr"
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 2.dp,
                bottomEnd = if (isMe) 2.dp else 16.dp
            ),
            color = bubbleColor,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                when (message.type) {
                    MessageType.STICKER -> {
                        Text(
                            text = message.content,
                            fontSize = 54.sp,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                    MessageType.EMOJI -> {
                        Text(
                            text = message.content,
                            fontSize = 36.sp,
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                    else -> {
                        Text(
                            text = message.content,
                            color = contentTextColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        color = timeTextColor,
                        fontSize = 11.sp
                    )

                    if (isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            MessageStatus.SENDING -> {
                                Text(
                                    text = "...",
                                    color = timeTextColor,
                                    fontSize = 10.sp
                                )
                            }
                            MessageStatus.SENT_LAN, MessageStatus.SENT_FCM -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent",
                                    tint = timeTextColor,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            MessageStatus.DELIVERED -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = timeTextColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            MessageStatus.READ -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Read",
                                    tint = Color(0xFF34B7F1),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            MessageStatus.FAILED -> {
                                Text(
                                    text = "!",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
