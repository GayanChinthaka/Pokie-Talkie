package com.pokerlanka.pokie_talkie.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pokerlanka.pokie_talkie.data.local.ConnectionMode
import com.pokerlanka.pokie_talkie.data.local.UserPreferencesManager
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.Contact
import com.pokerlanka.pokie_talkie.data.model.Message
import com.pokerlanka.pokie_talkie.data.model.MessageStatus
import com.pokerlanka.pokie_talkie.data.model.MessageType
import com.pokerlanka.pokie_talkie.data.repository.ChatRepository
import com.pokerlanka.pokie_talkie.data.repository.ContactRepository
import com.pokerlanka.pokie_talkie.di.AppContainer
import com.pokerlanka.pokie_talkie.fcm.FcmSignalingClient
import com.pokerlanka.pokie_talkie.telecom.TelecomCallManager
import com.pokerlanka.pokie_talkie.utils.MediaStorageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatViewModel(
    val peerId: String,
    private val myUserId: String,
    private val chatRepository: ChatRepository,
    private val contactRepository: ContactRepository,
    private val fcmSignalingClient: FcmSignalingClient,
    private val telecomCallManager: TelecomCallManager,
    private val userPreferencesManager: UserPreferencesManager,
    private val appContainer: AppContainer,
    // Sends must outlive the screen, or leaving the chat strands the message in SENDING
    private val appScope: CoroutineScope
) : ViewModel() {

    val connectionMode: StateFlow<ConnectionMode> = userPreferencesManager.connectionModeFlow
    val isGodModeEnabled: StateFlow<Boolean> = userPreferencesManager.godModeEnabledFlow

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _editingMessage = MutableStateFlow<Message?>(null)
    val editingMessage: StateFlow<Message?> = _editingMessage.asStateFlow()

    val messages: StateFlow<List<Message>> = chatRepository.getMessages(peerId, myUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contact: StateFlow<Contact?> = contactRepository.getContactFlow(peerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun markConversationAsRead() {
        viewModelScope.launch {
            val unreadIds = chatRepository.markIncomingAsRead(peerId, myUserId)
            if (unreadIds.isNotEmpty()) {
                fcmSignalingClient.sendReadReceipt(peerId, unreadIds)
            }
        }
    }

    fun onInputTextChanged(newText: String) {
        val isLan = contact.value?.isOnlineLan == true && userPreferencesManager.getConnectionMode() == ConnectionMode.WIFI
        val limit = if (isLan) MAX_LAN_MESSAGE_LENGTH else MAX_FCM_MESSAGE_LENGTH
        _inputText.value = newText.take(limit)
    }

    fun startEditing(message: Message) {
        if (!message.isFromMe || message.isDeleted || message.type != MessageType.TEXT) return
        _editingMessage.value = message
        _inputText.value = message.content
    }

    fun cancelEditing() {
        _editingMessage.value = null
        _inputText.value = ""
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        val editing = _editingMessage.value
        if (editing != null) {
            _editingMessage.value = null
            _inputText.value = ""
            if (text == editing.content) return
            appScope.launch(Dispatchers.IO) {
                chatRepository.editMessage(editing.id, text)
                fcmSignalingClient.sendMessageEdit(peerId, editing.id, text)
            }
            return
        }

        _inputText.value = ""
        dispatchTextMessage(UUID.randomUUID().toString(), text, isRetry = false)
    }

    private fun dispatchTextMessage(messageId: String, text: String, isRetry: Boolean) {
        appScope.launch {
            if (!isRetry) {
                chatRepository.saveMessage(
                    id = messageId,
                    senderId = myUserId,
                    receiverId = peerId,
                    content = text,
                    type = MessageType.TEXT,
                    isFromMe = true,
                    status = MessageStatus.SENDING
                )
            } else {
                chatRepository.updateMessageStatus(messageId, MessageStatus.SENDING)
            }

            val result = fcmSignalingClient.sendChatMessageSync(
                targetId = peerId,
                content = text,
                type = MessageType.TEXT,
                messageId = messageId
            )
            val finalStatus = if (result.success) {
                if (result.isLan) MessageStatus.SENT_LAN else MessageStatus.SENT_FCM
            } else {
                MessageStatus.FAILED
            }
            // Only if still SENDING: a delivery receipt may already have moved it to DELIVERED/READ
            chatRepository.updateMessageStatusIfSending(messageId, finalStatus)
        }
    }

    fun sendMediaBatchFromUris(
        context: Context,
        uris: List<Uri>
    ) {
        if (uris.isEmpty()) return
        val mode = userPreferencesManager.getConnectionMode()
        val peer = contact.value
        if (mode != ConnectionMode.WIFI || peer == null || !peer.isOnlineLan || peer.ipAddress.isBlank()) {
            Toast.makeText(
                context,
                "Photos, GIFs & files require Wi-Fi LAN connection with peer online",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        appScope.launch {
            for (uri in uris) {
                sendSingleMediaInternal(context, uri, mimeTypeHint = null, preferSticker = false)
            }
        }
    }

    fun sendMediaFromUri(
        context: Context,
        uri: Uri,
        mimeTypeHint: String? = null,
        preferSticker: Boolean = false
    ) {
        val mode = userPreferencesManager.getConnectionMode()
        val peer = contact.value
        if (mode != ConnectionMode.WIFI || peer == null || !peer.isOnlineLan || peer.ipAddress.isBlank()) {
            Toast.makeText(
                context,
                "Photos, GIFs & files require Wi-Fi LAN connection with peer online",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        appScope.launch {
            sendSingleMediaInternal(context, uri, mimeTypeHint, preferSticker)
        }
    }

    private suspend fun sendSingleMediaInternal(
        context: Context,
        uri: Uri,
        mimeTypeHint: String?,
        preferSticker: Boolean
    ) {
        val messageId = UUID.randomUUID().toString()
        val prepared = MediaStorageHelper.prepareOutgoingMedia(
            context = context,
            uri = uri,
            messageId = messageId,
            mimeTypeHint = mimeTypeHint,
            preferSticker = preferSticker
        )
        if (prepared == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Could not prepare file (max 100 MB)", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val displayContent = when (prepared.messageType) {
            MessageType.IMAGE -> "Photo"
            MessageType.GIF -> "GIF"
            MessageType.STICKER -> "Sticker"
            else -> prepared.fileName
        }

        chatRepository.saveMessage(
            id = messageId,
            senderId = myUserId,
            receiverId = peerId,
            content = displayContent,
            type = prepared.messageType,
            isFromMe = true,
            status = MessageStatus.SENDING,
            localFilePath = prepared.file.absolutePath,
            mimeType = prepared.mimeType,
            fileName = prepared.fileName,
            fileSize = prepared.fileSize
        )

        val sent = appContainer.sendMediaToPeer(peerId, messageId, prepared)
        val finalStatus = if (sent) MessageStatus.SENT_LAN else MessageStatus.FAILED
        chatRepository.updateMessageStatusIfSending(messageId, finalStatus)
        if (!sent) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to send ${prepared.fileName} over Wi-Fi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun unsendMessage(message: Message) {
        if (!message.isFromMe || message.isDeleted) return
        appScope.launch(Dispatchers.IO) {
            chatRepository.markMessageUnsent(message.id)
            fcmSignalingClient.sendMessageUnsend(peerId, message.id)
        }
    }

    fun deleteMessageForMe(message: Message) {
        appScope.launch(Dispatchers.IO) {
            chatRepository.deleteMessageForMe(message.id)
        }
    }

    fun retryFailedMessage(message: Message) {
        if (!message.isFromMe || message.status != MessageStatus.FAILED || message.isDeleted) return
        if (message.type == MessageType.TEXT) {
            dispatchTextMessage(message.id, message.content, isRetry = true)
        }
    }

    fun clearChat() {
        appScope.launch(Dispatchers.IO) {
            chatRepository.clearChat(peerId, myUserId)
        }
    }

    fun initiateCall(callType: CallType): Boolean {
        val ctx = telecomCallManager.context
        val isBtMode = userPreferencesManager.getConnectionMode() == ConnectionMode.BLUETOOTH
        if (isBtMode && callType == CallType.VIDEO) {
            Toast.makeText(ctx, "Video calls require Wi-Fi mode", Toast.LENGTH_SHORT).show()
            return false
        }
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            (callType == CallType.VIDEO && ActivityCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(ctx, "Microphone and Camera permissions are required to make calls", Toast.LENGTH_LONG).show()
            return false
        }
        val peer = contact.value ?: return false
        val callId = UUID.randomUUID().toString()
        telecomCallManager.startOutgoingCall(
            recipientId = peer.id,
            recipientName = peer.name,
            callType = if (isBtMode) CallType.VOICE else callType,
            callId = callId,
            isBluetoothVoice = isBtMode
        )
        return true
    }

    companion object {
        private const val MAX_FCM_MESSAGE_LENGTH = 1000
        private const val MAX_LAN_MESSAGE_LENGTH = 8000

        fun provideFactory(
            peerId: String,
            myUserId: String,
            chatRepository: ChatRepository,
            contactRepository: ContactRepository,
            fcmSignalingClient: FcmSignalingClient,
            telecomCallManager: TelecomCallManager,
            userPreferencesManager: UserPreferencesManager,
            appContainer: AppContainer,
            appScope: CoroutineScope
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(
                    peerId,
                    myUserId,
                    chatRepository,
                    contactRepository,
                    fcmSignalingClient,
                    telecomCallManager,
                    userPreferencesManager,
                    appContainer,
                    appScope
                ) as T
            }
        }
    }
}
