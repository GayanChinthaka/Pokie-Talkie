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
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkBubbleMe
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkBubblePeer
import com.pokerlanka.pokie_talkie.ui.theme.PokieEmerald
import com.pokerlanka.pokie_talkie.ui.theme.PokieLanActiveBadge
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextPrimary
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AvatarView(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    backgroundColor: Color = PokieEmerald,
    isOnlineLan: Boolean = false
) {
    val initials = name.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()

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
            Text(
                text = initials.ifEmpty { "P" },
                color = Color.White,
                fontSize = (size.value * 0.4).sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (isOnlineLan) {
            Box(
                modifier = Modifier
                    .size(size * 0.32f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(PokieLanActiveBadge)
                    .border(2.dp, Color(0xFF111B21), CircleShape)
            )
        }
    }
}

@Composable
fun LanStatusBadge(
    isOnlineLan: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isOnlineLan) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFF8696A0).copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isOnlineLan) Icons.Default.Wifi else Icons.Default.Cloud,
                contentDescription = null,
                tint = if (isOnlineLan) PokieLanActiveBadge else PokieTextSecondary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isOnlineLan) "LAN Active" else "Cloud (FCM)",
                color = if (isOnlineLan) PokieLanActiveBadge else PokieTextSecondary,
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
    val bubbleColor = if (isMe) PokieDarkBubbleMe else PokieDarkBubblePeer
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
                            color = PokieTextPrimary,
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
                        color = PokieTextSecondary,
                        fontSize = 11.sp
                    )

                    if (isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            MessageStatus.SENDING -> {
                                Text(
                                    text = "...",
                                    color = PokieTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            MessageStatus.SENT_LAN -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent via LAN",
                                    tint = PokieLanActiveBadge,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            MessageStatus.SENT_FCM -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent via FCM",
                                    tint = PokieTextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            MessageStatus.DELIVERED, MessageStatus.READ -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = if (message.status == MessageStatus.READ) Color(0xFF53BDEB) else PokieTextSecondary,
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
