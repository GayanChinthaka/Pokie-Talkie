package com.pokerlanka.pokie_talkie.ui.chat

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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
    private val connectivityMonitor: ConnectivityMonitor
) : ViewModel() {

    private val _inputText = MutableStateFlow("")
    private val _isStickerPickerOpen = MutableStateFlow(false)
    val isStickerPickerOpen: StateFlow<Boolean> = _isStickerPickerOpen.asStateFlow()

    val messages: StateFlow<List<Message>> = chatRepository.getMessages(peerId, myUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contact: StateFlow<Contact?> = contactRepository.getContactFlow(peerId)
        .map { c ->
            if (c != null && c.bluetoothAddress.isBlank()) {
                val btAddr = fcmSignalingClient.bluetoothTransportManager.getKnownOrPairedAddress(c.id, c.name, c.bluetoothAddress)
                if (!btAddr.isNullOrBlank()) c.copy(bluetoothAddress = btAddr) else c
            } else c
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        markConversationAsRead()
        viewModelScope.launch {
            messages.collect { msgs ->
                val hasUnreadIncoming = msgs.any { !it.isFromMe && it.status != MessageStatus.READ }
                if (hasUnreadIncoming) {
                    markConversationAsRead()
                }
            }
        }
    }

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
        viewModelScope.launch {
            val contactObj = contact.value
            val isLan = contactObj?.isOnlineLan == true
            val initialStatus = if (isLan) MessageStatus.SENT_LAN else MessageStatus.SENT_FCM
            val messageId = UUID.randomUUID().toString()

            chatRepository.saveMessage(
                id = messageId,
                senderId = myUserId,
                receiverId = peerId,
                content = text,
                type = MessageType.TEXT,
                isFromMe = true,
                status = initialStatus
            )

            // Dispatch payload with messageId
            fcmSignalingClient.sendChatMessage(
                targetId = peerId,
                content = text,
                type = MessageType.TEXT,
                messageId = messageId
            )
        }
    }

    fun sendSticker(sticker: Sticker) {
        _isStickerPickerOpen.value = false
        viewModelScope.launch {
            val contactObj = contact.value
            val isLan = contactObj?.isOnlineLan == true
            val initialStatus = if (isLan) MessageStatus.SENT_LAN else MessageStatus.SENT_FCM
            val messageId = UUID.randomUUID().toString()

            chatRepository.saveMessage(
                id = messageId,
                senderId = myUserId,
                receiverId = peerId,
                content = sticker.emojiRepresentation,
                type = MessageType.STICKER,
                isFromMe = true,
                status = initialStatus,
                extraData = sticker.title
            )

            fcmSignalingClient.sendChatMessage(
                targetId = peerId,
                content = sticker.emojiRepresentation,
                type = MessageType.STICKER,
                messageId = messageId
            )
        }
    }

    fun initiateCall(callType: CallType) {
        val peer = contact.value ?: return
        val callId = UUID.randomUUID().toString()
        val isBt = !peer.isOnlineLan || connectivityMonitor.activeTransport.value == ActiveTransportMode.BLUETOOTH
        telecomCallManager.startOutgoingCall(
            recipientId = peer.id,
            recipientName = peer.name,
            callType = if (isBt) CallType.VOICE else callType,
            callId = callId,
            isBluetoothVoice = isBt
        )
    }

    companion object {
        fun provideFactory(
            peerId: String,
            myUserId: String,
            chatRepository: ChatRepository,
            contactRepository: ContactRepository,
            fcmSignalingClient: FcmSignalingClient,
            telecomCallManager: TelecomCallManager,
            connectivityMonitor: ConnectivityMonitor
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(peerId, myUserId, chatRepository, contactRepository, fcmSignalingClient, telecomCallManager, connectivityMonitor) as T
            }
        }
    }
}
