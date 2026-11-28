package com.pokerlanka.pokie_talkie.ui.chat

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.pokerlanka.pokie_talkie.data.local.ConnectionMode
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.Message
import com.pokerlanka.pokie_talkie.data.model.MessageStatus
import com.pokerlanka.pokie_talkie.data.model.MessageType
import com.pokerlanka.pokie_talkie.ui.components.AvatarView
import com.pokerlanka.pokie_talkie.ui.components.ChatBubble
import com.pokerlanka.pokie_talkie.ui.components.PokieImageLoader
import com.pokerlanka.pokie_talkie.utils.MediaStorageHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onNavigateToCall: (String, CallType) -> Unit,
    onNavigateToPeerGodMode: (String) -> Unit = {},
    onChatOpened: () -> Unit = {},
    onChatClosed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
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
    val connectionMode by viewModel.connectionMode.collectAsState()
    val isGodModeEnabled by viewModel.isGodModeEnabled.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val editingMessage by viewModel.editingMessage.collectAsState()
    val listState = rememberLazyListState()

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showClearChatConfirm by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var selectedMessageForMenu by remember { mutableStateOf<Message?>(null) }
    var previewMediaPath by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val maxPhotos = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            minOf(100, MediaStore.getPickImagesMaxLimit()).coerceAtLeast(2)
        } else {
            100
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxPhotos)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.sendMediaBatchFromUris(context, uris)
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.sendMediaFromUri(context, uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            viewModel.sendMediaFromUri(context, uri, mimeTypeHint = "image/jpeg")
        }
    }

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

    val isBtMode = connectionMode == ConnectionMode.BLUETOOTH
    val peerStatusText = when {
        isBtMode -> if (!contact?.bluetoothAddress.isNullOrBlank()) "Bluetooth Ready" else "Bluetooth"
        contact?.isOnlineLan == true -> "Online"
        else -> "Offline"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isGodModeEnabled) {
                                    Modifier.clickable {
                                        onNavigateToPeerGodMode(contact?.id ?: viewModel.peerId)
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        AvatarView(
                            name = contact?.name ?: "Family Member",
                            backgroundColor = Color(contact?.avatarColor ?: 0xFF00A884),
                            imagePath = contact?.profileImagePath,
                            isOnlineLan = contact?.isOnlineLan == true,
                            size = 38.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact?.name ?: "Family Member",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = peerStatusText,
                                color = if (contact?.isOnlineLan == true && !isBtMode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 11.sp
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
                        if (isBtMode) {
                            viewModel.initiateCall(CallType.VIDEO)
                        } else {
                            val isLan = contact?.isOnlineLan == true
                            val type = if (isLan) CallType.VIDEO else CallType.VOICE
                            if (viewModel.initiateCall(type)) {
                                contact?.id?.let { onNavigateToCall(it, type) }
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video Call",
                            tint = if (isBtMode) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }

                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear Chat") },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showClearChatConfirm = true
                                }
                            )
                        }
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
                    ChatBubble(
                        message = message,
                        onLongClick = { selectedMessageForMenu = message },
                        onMediaClick = {
                            if (!message.isDeleted && !message.localFilePath.isNullOrBlank()) {
                                when (message.type) {
                                    MessageType.IMAGE, MessageType.GIF, MessageType.STICKER -> {
                                        previewMediaPath = message.localFilePath
                                    }
                                    MessageType.FILE -> {
                                        if (!MediaStorageHelper.openFileExternally(context, message.localFilePath, message.mimeType)) {
                                            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    )
                }
            }

            // Editing Banner
            val activeEdit = editingMessage
            if (activeEdit != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Editing message",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = activeEdit.content,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = viewModel::cancelEditing,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel editing",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
                    onClick = { showAttachmentSheet = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach photo or file",
                        tint = if (isBtMode || contact?.isOnlineLan != true) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }

                RichContentChatInput(
                    text = inputText,
                    onTextChanged = viewModel::onInputTextChanged,
                    onMediaReceived = { uri, mime ->
                        viewModel.sendMediaFromUri(
                            context = context,
                            uri = uri,
                            mimeTypeHint = mime,
                            preferSticker = (mime == "image/webp" || mime == "image/png")
                        )
                    },
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
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // Attachment Sheet (Wi-Fi Mode Only)
    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Share Media & Files",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isBtMode || contact?.isOnlineLan != true) {
                    Text(
                        text = "Note: Photos, GIFs, files and docs require Wi-Fi LAN connection with this peer online.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                AttachmentOptionRow(
                    icon = Icons.Default.PhotoLibrary,
                    title = "Select Photo",
                    subtitle = "Up to $maxPhotos photos",
                    onClick = {
                        showAttachmentSheet = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                AttachmentOptionRow(
                    icon = Icons.Default.CameraAlt,
                    title = "Take Photo",
                    subtitle = "Capture with camera",
                    onClick = {
                        showAttachmentSheet = false
                        val (uri, _) = MediaStorageHelper.createCameraCaptureUri(context)
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    }
                )
                AttachmentOptionRow(
                    icon = Icons.Default.Description,
                    title = "Document",
                    subtitle = "Up to 100 MB",
                    onClick = {
                        showAttachmentSheet = false
                        documentPickerLauncher.launch(arrayOf("*/*"))
                    }
                )
            }
        }
    }

    // Message Long-Press Action Dialog
    val menuMessage = selectedMessageForMenu
    if (menuMessage != null) {
        AlertDialog(
            onDismissRequest = { selectedMessageForMenu = null },
            title = {
                Text(
                    text = "Message Options",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!menuMessage.isDeleted && menuMessage.type == MessageType.TEXT) {
                        MessageActionRow(
                            icon = Icons.Default.ContentCopy,
                            label = "Copy Text",
                            onClick = {
                                clipboardManager.setText(AnnotatedString(menuMessage.content))
                                selectedMessageForMenu = null
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    if (menuMessage.isFromMe && !menuMessage.isDeleted && menuMessage.type == MessageType.TEXT) {
                        MessageActionRow(
                            icon = Icons.Default.Edit,
                            label = "Edit Message",
                            onClick = {
                                viewModel.startEditing(menuMessage)
                                selectedMessageForMenu = null
                            }
                        )
                    }
                    if (menuMessage.isFromMe && menuMessage.status == MessageStatus.FAILED && !menuMessage.isDeleted && menuMessage.type == MessageType.TEXT) {
                        MessageActionRow(
                            icon = Icons.Default.Refresh,
                            label = "Retry Send",
                            onClick = {
                                viewModel.retryFailedMessage(menuMessage)
                                selectedMessageForMenu = null
                            }
                        )
                    }
                    if (menuMessage.isFromMe && !menuMessage.isDeleted) {
                        MessageActionRow(
                            icon = Icons.AutoMirrored.Filled.Undo,
                            label = "Unsend for Everyone",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = {
                                viewModel.unsendMessage(menuMessage)
                                selectedMessageForMenu = null
                            }
                        )
                    }
                    MessageActionRow(
                        icon = Icons.Default.Delete,
                        label = "Delete for Me",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = {
                            viewModel.deleteMessageForMe(menuMessage)
                            selectedMessageForMenu = null
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedMessageForMenu = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Clear Chat Confirmation Dialog
    if (showClearChatConfirm) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirm = false },
            title = { Text("Clear Chat?", fontWeight = FontWeight.Bold) },
            text = { Text("This will delete all messages and downloaded media in this conversation on your device.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearChat()
                        showClearChatConfirm = false
                        Toast.makeText(context, "Chat cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full-Screen Image / GIF Preview Dialog
    val previewPath = previewMediaPath
    if (previewPath != null) {
        Dialog(
            onDismissRequest = { previewMediaPath = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { previewMediaPath = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = File(previewPath),
                    contentDescription = "Media Preview",
                    imageLoader = remember(context) { PokieImageLoader.get(context) },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
                IconButton(
                    onClick = { previewMediaPath = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close preview",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun MessageActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
