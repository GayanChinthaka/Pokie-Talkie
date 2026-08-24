package com.pokerlanka.pokie_talkie.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerlanka.pokie_talkie.data.model.Message
import com.pokerlanka.pokie_talkie.data.model.MessageStatus
import com.pokerlanka.pokie_talkie.data.model.MessageType
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkBg
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkBubbleMe
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkBubblePeer
import com.pokerlanka.pokie_talkie.ui.theme.PokieEmerald
import com.pokerlanka.pokie_talkie.ui.theme.PokieLanActiveBadge
import com.pokerlanka.pokie_talkie.ui.theme.PokieLightBubbleMe
import com.pokerlanka.pokie_talkie.ui.theme.PokieLightBubblePeer
import com.pokerlanka.pokie_talkie.ui.theme.PokieLightTextPrimary
import com.pokerlanka.pokie_talkie.ui.theme.PokieLightTextSecondary
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextPrimary
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.remember

@Composable
fun AvatarView(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    backgroundColor: Color = PokieEmerald,
    imagePath: String? = null,
    isOnlineLan: Boolean = false
) {
    val initials = name.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()

    val bitmap = remember(imagePath) {
        if (!imagePath.isNullOrBlank()) {
            val file = java.io.File(imagePath)
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
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
                    .background(PokieEmerald)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun LanStatusBadge(
    isOnlineLan: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeBg = if (isOnlineLan) PokieEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val badgeContentColor = if (isOnlineLan) PokieEmerald else MaterialTheme.colorScheme.onSurfaceVariant

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
                imageVector = if (isOnlineLan) Icons.Default.Wifi else Icons.Default.Cloud,
                contentDescription = null,
                tint = badgeContentColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isOnlineLan) "LAN Active" else "Cloud (FCM)",
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

    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formattedTime = timeFormatter.format(Date(message.timestamp))

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
                            MessageStatus.SENT_LAN -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent via LAN",
                                    tint = PokieEmerald,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            MessageStatus.SENT_FCM -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent via FCM",
                                    tint = timeTextColor,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            MessageStatus.DELIVERED, MessageStatus.READ -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = if (message.status == MessageStatus.READ) Color(0xFF53BDEB) else timeTextColor,
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
