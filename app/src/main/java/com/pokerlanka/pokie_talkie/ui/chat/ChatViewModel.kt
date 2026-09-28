package com.pokerlanka.pokie_talkie.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.Contact
import com.pokerlanka.pokie_talkie.data.model.Message
import com.pokerlanka.pokie_talkie.data.model.MessageStatus
import com.pokerlanka.pokie_talkie.data.model.MessageType
import com.pokerlanka.pokie_talkie.data.model.Sticker
import com.pokerlanka.pokie_talkie.data.repository.ChatRepository
import com.pokerlanka.pokie_talkie.data.repository.ContactRepository
import com.pokerlanka.pokie_talkie.fcm.FcmSignalingClient
import com.pokerlanka.pokie_talkie.lan.ConnectivityMonitor
import com.pokerlanka.pokie_talkie.lan.ActiveTransportMode
import com.pokerlanka.pokie_talkie.telecom.TelecomCallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val contact: Contact? = null,
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isStickerPickerOpen: Boolean = false
)

class ChatViewModel(
    private val peerId: String,
    private val myUserId: String,
    private val chatRepository: ChatRepository,
    private val contactRepository: ContactRepository,
    private val fcmSignalingClient: FcmSignalingClient,
    private val telecomCallManager: TelecomCallManager,
    private val connectivityMonitor: ConnectivityMonitor,
    // Sends must outlive the screen, or leaving the chat strands the message in SENDING
    private val appScope: CoroutineScope
) : ViewModel() {

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isStickerPickerOpen = MutableStateFlow(false)
    val isStickerPickerOpen: StateFlow<Boolean> = _isStickerPickerOpen.asStateFlow()

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
        _inputText.value = newText
    }

    fun toggleStickerPicker() {
        _isStickerPickerOpen.value = !_isStickerPickerOpen.value
    }

    fun closeStickerPicker() {
        _isStickerPickerOpen.value = false
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        _inputText.value = ""
        appScope.launch {
            val messageId = UUID.randomUUID().toString()

            chatRepository.saveMessage(
                id = messageId,
                senderId = myUserId,
                receiverId = peerId,
                content = text,
                type = MessageType.TEXT,
                isFromMe = true,
                status = MessageStatus.SENDING
            )

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

    fun sendSticker(sticker: Sticker) {
        _isStickerPickerOpen.value = false
        appScope.launch {
            val messageId = UUID.randomUUID().toString()

            chatRepository.saveMessage(
                id = messageId,
                senderId = myUserId,
                receiverId = peerId,
                content = sticker.emojiRepresentation,
                type = MessageType.STICKER,
                isFromMe = true,
                status = MessageStatus.SENDING,
                extraData = sticker.title
            )

            val result = fcmSignalingClient.sendChatMessageSync(
                targetId = peerId,
                content = sticker.emojiRepresentation,
                type = MessageType.STICKER,
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

    fun initiateCall(callType: CallType): Boolean {
        val ctx = telecomCallManager.context
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
            callType = callType,
            callId = callId
        )
        return true
    }

    companion object {
        fun provideFactory(
            peerId: String,
            myUserId: String,
            chatRepository: ChatRepository,
            contactRepository: ContactRepository,
            fcmSignalingClient: FcmSignalingClient,
            telecomCallManager: TelecomCallManager,
            connectivityMonitor: ConnectivityMonitor,
            appScope: CoroutineScope
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(peerId, myUserId, chatRepository, contactRepository, fcmSignalingClient, telecomCallManager, connectivityMonitor, appScope) as T
            }
        }
    }
}
