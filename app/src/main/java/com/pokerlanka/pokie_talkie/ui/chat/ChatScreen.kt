package com.pokerlanka.pokie_talkie.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.MessageStatus
import com.pokerlanka.pokie_talkie.ui.components.AvatarView
import com.pokerlanka.pokie_talkie.ui.components.ChatBubble
import com.pokerlanka.pokie_talkie.ui.components.LanStatusBadge
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkBg
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkInput
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkSurface
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextPrimary
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onNavigateToCall: (String, CallType) -> Unit,
    onChatOpened: () -> Unit = {},
    onChatClosed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var isResumed by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isResumed = true
                onChatOpened()
                viewModel.markConversationAsRead()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                isResumed = false
                onChatClosed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            isResumed = false
            onChatClosed()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val messages by viewModel.messages.collectAsState()
    val contact by viewModel.contact.collectAsState()
    val isStickerPickerOpen by viewModel.isStickerPickerOpen.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(messages, isResumed) {
        if (isResumed) {
            val hasUnreadIncoming = messages.any { !it.isFromMe && it.status != MessageStatus.READ }
            if (hasUnreadIncoming) {
                viewModel.markConversationAsRead()
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarView(
                            name = contact?.name ?: "Family Member",
                            backgroundColor = Color(contact?.avatarColor ?: 0xFF00A884),
                            isOnlineLan = contact?.isOnlineLan == true,
                            size = 38.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = contact?.name ?: "Family Member",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            LanStatusBadge(
                                isOnlineLan = contact?.isOnlineLan == true,
                                bluetoothAddress = contact?.bluetoothAddress
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (viewModel.initiateCall(CallType.VOICE)) {
                            contact?.id?.let { onNavigateToCall(it, CallType.VOICE) }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Voice Call",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = {
                        val isLan = contact?.isOnlineLan == true
                        val type = if (isLan) CallType.VIDEO else CallType.VOICE
                        if (viewModel.initiateCall(type)) {
                            contact?.id?.let { onNavigateToCall(it, type) }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video Call",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message = message)
                }
            }

            // Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = viewModel::toggleStickerPicker
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEmotions,
                        contentDescription = "Emojis & Stickers",
                        tint = if (isStickerPickerOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = viewModel::onInputTextChanged,
                    placeholder = {
                        Text("Type a family message...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )

                IconButton(
                    onClick = viewModel::sendMessage,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Sticker Drawer
            AnimatedVisibility(visible = isStickerPickerOpen) {
                StickerPicker(
                    onStickerSelected = { sticker ->
                        viewModel.sendSticker(sticker)
                    }
                )
            }
        }
    }
}
