package com.pokerlanka.pokie_talkie.di

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pokerlanka.pokie_talkie.MainActivity
import com.pokerlanka.pokie_talkie.R
import com.pokerlanka.pokie_talkie.data.local.PokieDatabase
import com.pokerlanka.pokie_talkie.data.model.CallSession
import com.pokerlanka.pokie_talkie.data.model.CallState
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.MessageStatus
import com.pokerlanka.pokie_talkie.data.model.MessageType
import com.pokerlanka.pokie_talkie.data.model.SignalingType
import com.pokerlanka.pokie_talkie.data.model.SignalingPayload
import java.util.UUID
import com.pokerlanka.pokie_talkie.data.repository.CallRepository
import com.pokerlanka.pokie_talkie.data.repository.ChatRepository
import com.pokerlanka.pokie_talkie.data.repository.ContactRepository
import com.pokerlanka.pokie_talkie.fcm.FcmSignalingClient
import com.pokerlanka.pokie_talkie.lan.LanDiscoveryManager
import com.pokerlanka.pokie_talkie.lan.LanSocketServer
import com.pokerlanka.pokie_talkie.lan.ConnectivityMonitor
import com.pokerlanka.pokie_talkie.lan.ActiveTransportMode
import com.pokerlanka.pokie_talkie.telecom.PokieConnectionService
import com.pokerlanka.pokie_talkie.telecom.TelecomCallManager
import com.pokerlanka.pokie_talkie.webrtc.WebRtcManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.pokerlanka.pokie_talkie.bluetooth.BluetoothTransportManager
import com.pokerlanka.pokie_talkie.bluetooth.BluetoothVoiceManager
import com.pokerlanka.pokie_talkie.wifip2p.WifiDirectManager
import com.pokerlanka.pokie_talkie.telecom.RingtoneVibrationManager
import org.webrtc.SessionDescription

import com.pokerlanka.pokie_talkie.data.local.UserPreferencesManager
import com.pokerlanka.pokie_talkie.data.model.Contact

class AppContainer(val context: Context) {
    private val tag = "AppContainer"

    companion object {
        const val CHANNEL_MESSAGES_ID = "pokie_chat_messages"
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // User Preferences & Device Identity
    val userPreferencesManager by lazy { UserPreferencesManager(context) }
    val currentUserId: String get() = userPreferencesManager.userId
    val currentUserName: String get() = userPreferencesManager.getUserName()

    // Database & Repositories
    val database by lazy { PokieDatabase.getInstance(context) }
    val chatRepository by lazy { ChatRepository(database.messageDao(), database.contactDao()) }
    val contactRepository by lazy { ContactRepository(database.contactDao()) }
    val callRepository by lazy { CallRepository(database.callLogDao()) }

    // Networking, Bluetooth & Proximity Transport
    val lanSocketServer by lazy { LanSocketServer(applicationScope) }
    val lanDiscoveryManager by lazy { LanDiscoveryManager(context, contactRepository, applicationScope) }
    val bluetoothTransportManager by lazy { BluetoothTransportManager(context, applicationScope, contactRepository) }
    val bluetoothVoiceManager by lazy { BluetoothVoiceManager(context, applicationScope, bluetoothTransportManager.bluetoothAdapter) }
    val wifiDirectManager by lazy {
        WifiDirectManager(context) { peerIp, isGroupOwner ->
            Log.i(tag, "Wi-Fi Direct peer connected with IP: $peerIp, isGroupOwner: $isGroupOwner")
            applicationScope.launch {
                addManualContact(
                    name = "Nearby Peer ($peerIp)",
                    ipAddress = peerIp,
                    port = 52220
                )
            }
        }
    }

    val connectivityMonitor by lazy {
        ConnectivityMonitor(context, applicationScope, wifiDirectManager, bluetoothTransportManager)
    }

    val fcmSignalingClient by lazy {
        FcmSignalingClient(
            contactRepository = contactRepository,
            lanSocketServer = lanSocketServer,
            bluetoothTransportManager = bluetoothTransportManager,
            userPreferencesManager = userPreferencesManager,
            scope = applicationScope,
            myUserId = currentUserId,
            myUserName = currentUserName
        )
    }

    val webRtcManager by lazy { WebRtcManager(context, applicationScope) }
    val ringtoneVibrationManager by lazy { RingtoneVibrationManager(context, userPreferencesManager) }
    val telecomCallManager by lazy { TelecomCallManager(context, callRepository, webRtcManager, ringtoneVibrationManager) }

    @Volatile
    var activeChatPeerId: String? = null

    fun onChatOpened(peerId: String) {
        activeChatPeerId = peerId
        dismissChatMessageNotification(peerId)
        applicationScope.launch {
            val unreadIds = chatRepository.markIncomingAsRead(peerId, currentUserId)
            if (unreadIds.isNotEmpty()) {
                Log.d(tag, "Marked ${unreadIds.size} messages as READ for peer $peerId, sending read receipt")
                fcmSignalingClient.sendReadReceipt(peerId, unreadIds)
            }
        }
    }

    fun dismissChatMessageNotification(senderId: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(senderId.hashCode())
            Log.d(tag, "Dismissed chat message notification for peer: $senderId")
        } catch (e: Exception) {
            Log.w(tag, "Failed to dismiss notification: ${e.message}")
        }
    }

    fun onChatClosed(peerId: String) {
        if (activeChatPeerId == peerId) {
            activeChatPeerId = null
        }
    }

    fun updateMyProfileName(newName: String) {
        userPreferencesManager.setUserName(newName)
        lanDiscoveryManager.updateProfileName(newName)
        fcmSignalingClient.broadcastProfileUpdate(newName)
    }

    suspend fun addManualContact(name: String, ipAddress: String, port: Int = 52220, fcmToken: String = "") {
        val contactId = if (ipAddress.isNotBlank()) "lan_${ipAddress.replace(".", "_")}" else "user_${System.currentTimeMillis()}"
        val contact = Contact(
            id = contactId,
            name = name,
            ipAddress = ipAddress,
            port = port,
            fcmToken = fcmToken,
            isOnlineLan = ipAddress.isNotBlank(),
            lastSeenTimestamp = System.currentTimeMillis()
        )
        contactRepository.saveOrUpdateContact(contact)
    }

    fun initialize() {
        createNotificationChannels()
        resetAudioModeToNormal()
        telecomCallManager.registerPhoneAccount()

        // Start LAN Socket Server on Port 52220
        lanSocketServer.start(52220)

        // Initialize and start NSD / mDNS discovery with real profile info
        val savedFcmToken = userPreferencesManager.getFcmToken()
        lanDiscoveryManager.initialize(currentUserId, currentUserName, lanSocketServer.serverPort, savedFcmToken)
        lanDiscoveryManager.startDiscoveryAndRegistration()
        // Setup WebRTC Callbacks
        setupWebRtcCallbacks()

        // Start Bluetooth listeners
        try {
            bluetoothTransportManager.startServer()
            bluetoothVoiceManager.startVoiceServer()
        } catch (e: Exception) {
            Log.w(tag, "Bluetooth listener start deferred: ${e.message}")
        }

        // Initialize Wi-Fi Direct
        try {
            wifiDirectManager.initialize()
        } catch (e: Exception) {
            Log.w(tag, "Wi-Fi Direct init deferred: ${e.message}")
        }

        // Start Connectivity Monitor
        connectivityMonitor.start()

        // Wire Bluetooth Call Outgoing Trigger
        telecomCallManager.onBluetoothCallInitiated = { recipientId, recipientName, callId ->
            fcmSignalingClient.sendBluetoothCallOffer(recipientId, recipientName, callId)
        }

        fcmSignalingClient.onCallFallbackToBluetooth = { _ ->
            webRtcManager.endCall()
            callRepository.updateBluetoothVoice(true)
        }

        applicationScope.launch(Dispatchers.IO) {
            bluetoothTransportManager.syncPairedDevicesToContacts()
        }

        // Listen for direct LAN & Bluetooth incoming payloads
        listenForLanPayloads()
        listenForBluetoothPayloads()
    }

    private fun setupWebRtcCallbacks() {
        webRtcManager.onLocalSdpCreated = { sdp ->
            val currentCall = callRepository.currentCall.value
            if (currentCall != null && !currentCall.isBluetoothVoice) {
                if (sdp.type == SessionDescription.Type.OFFER) {
                    fcmSignalingClient.sendCallOffer(
                        targetId = currentCall.peerId,
                        callId = currentCall.callId,
                        callType = currentCall.callType,
                        sdp = sdp
                    )
                } else if (sdp.type == SessionDescription.Type.ANSWER) {
                    fcmSignalingClient.sendCallAnswer(
                        targetId = currentCall.peerId,
                        callId = currentCall.callId,
                        sdp = sdp
                    )
                }
            }
        }

        webRtcManager.onLocalIceCandidate = { candidate ->
            val currentCall = callRepository.currentCall.value
            if (currentCall != null && !currentCall.isBluetoothVoice) {
                fcmSignalingClient.sendIceCandidate(
                    targetId = currentCall.peerId,
                    callId = currentCall.callId,
                    candidate = candidate
                )
            }
        }

        webRtcManager.onCallDisconnected = {
            applicationScope.launch {
                callRepository.endCallAndLog()
            }
        }
    }

    private fun listenForLanPayloads() {
        applicationScope.launch(Dispatchers.IO) {
            lanSocketServer.incomingPayloads.collect { payload ->
                handleIncomingSignalingPayload(payload, "LAN")
            }
        }
    }

    private fun listenForBluetoothPayloads() {
        applicationScope.launch(Dispatchers.IO) {
            bluetoothTransportManager.incomingPayloads.collect { payload ->
                handleIncomingSignalingPayload(payload, "Bluetooth")
            }
        }
    }

    private fun handleIncomingSignalingPayload(payload: SignalingPayload, source: String) {
        Log.d(tag, "Handling incoming $source payload [${payload.type}] from ${payload.senderName}")
        when (payload.type) {
            SignalingType.OFFER -> {
                val callType = payload.callType ?: CallType.VOICE
                val callId = payload.callId ?: System.currentTimeMillis().toString()
                val isBtCall = payload.isBluetoothVoice || 
                    payload.sdp.isNullOrBlank() || 
                    source == "Bluetooth" || 
                    connectivityMonitor.activeTransport.value == ActiveTransportMode.BLUETOOTH

                if (!isBtCall) {
                    // Store pending remote SDP offer for when the user answers
                    webRtcManager.pendingRemoteOfferSdp = payload.sdp
                } else {
                    bluetoothVoiceManager.startVoiceServer()
                }

                telecomCallManager.handleIncomingCall(
                    callerId = payload.senderId,
                    callerName = payload.senderName,
                    callType = callType,
                    callId = callId,
                    isBluetoothVoice = isBtCall
                )
            }

            SignalingType.ANSWER -> {
                val currentCall = callRepository.currentCall.value
                val isBtCall = payload.isBluetoothVoice || currentCall?.isBluetoothVoice == true
                // Stop ringback tone on the sender side when remote answers
                ringtoneVibrationManager.stopAll()
                if (isBtCall) {
                    Log.i(tag, "Received Bluetooth call ANSWER from ${payload.senderName}")
                    PokieConnectionService.activeConnection?.markConnected()
                    callRepository.updateCallState(CallState.CONNECTED)
                    val peerBtAddress = bluetoothTransportManager.getKnownOrPairedAddress(
                        payload.senderId,
                        payload.senderName,
                        null
                    )
                    if (!peerBtAddress.isNullOrBlank()) {
                        applicationScope.launch(Dispatchers.IO) {
                            kotlinx.coroutines.delay(250)
                            bluetoothVoiceManager.startOutgoingCall(peerBtAddress)
                        }
                    } else {
                        Log.e(tag, "Cannot start Bluetooth voice: unknown MAC for ${payload.senderId}")
                    }
                } else {
                    payload.sdp?.let { sdpString ->
                        webRtcManager.setRemoteDescription(sdpString, SessionDescription.Type.ANSWER)
                    }
                    PokieConnectionService.activeConnection?.markConnected()
                    callRepository.updateCallState(CallState.CONNECTED)
                }
            }

            SignalingType.ICE_CANDIDATE -> {
                payload.iceCandidate?.let { candidate ->
                    webRtcManager.addRemoteIceCandidate(candidate)
                }
            }

            SignalingType.HANGUP -> {
                webRtcManager.endCall()
                bluetoothVoiceManager.stopDuplexAudio()
                PokieConnectionService.activeConnection?.markDisconnected()
                telecomCallManager.dismissCallNotification()
                applicationScope.launch {
                    callRepository.endCallAndLog()
                }
            }

            SignalingType.CHAT_MESSAGE -> {
                val content = payload.messageContent ?: return
                val type = payload.messageType ?: MessageType.TEXT
                val messageId = payload.messageId ?: UUID.randomUUID().toString()
                val isChatCurrentlyOpen = activeChatPeerId == payload.senderId
                val initialStatus = if (isChatCurrentlyOpen) MessageStatus.READ else MessageStatus.DELIVERED

                applicationScope.launch {
                    val existingContact = contactRepository.getContact(payload.senderId)
                    val remoteBtAddress = bluetoothTransportManager.getPeerAddress(payload.senderId) ?: ""
                    val isLan = (source == "LAN")

                    if (existingContact == null) {
                        val byBt = if (remoteBtAddress.isNotBlank()) contactRepository.getContact(remoteBtAddress) else null
                        if (byBt != null) {
                            contactRepository.deleteContact(byBt.id)
                            contactRepository.saveOrUpdateContact(
                                byBt.copy(
                                    id = payload.senderId,
                                    name = payload.senderName.ifBlank { byBt.name },
                                    bluetoothAddress = remoteBtAddress,
                                    isOnlineLan = isLan,
                                    lastSeenTimestamp = System.currentTimeMillis()
                                )
                            )
                        } else {
                            contactRepository.saveOrUpdateContact(
                                Contact(
                                    id = payload.senderId,
                                    name = payload.senderName.ifBlank { "Family Member" },
                                    bluetoothAddress = remoteBtAddress,
                                    isOnlineLan = isLan,
                                    lastSeenTimestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    } else {
                        contactRepository.updateLanStatus(payload.senderId, isLan)
                        if (remoteBtAddress.isNotBlank() && existingContact.bluetoothAddress.isBlank()) {
                            contactRepository.updateBluetoothAddress(payload.senderId, remoteBtAddress)
                        }
                    }

                    chatRepository.saveMessage(
                        id = messageId,
                        senderId = payload.senderId,
                        receiverId = currentUserId,
                        content = content,
                        type = type,
                        isFromMe = false,
                        status = initialStatus
                    )

                    // If user currently has this chat screen open, send Read Receipt directly!
                    // Otherwise, send Delivery Receipt.
                    if (isChatCurrentlyOpen) {
                        Log.d(tag, "Chat is currently open with ${payload.senderId}, sending READ_RECEIPT for $messageId")
                        fcmSignalingClient.sendReadReceipt(payload.senderId, listOf(messageId))
                    } else {
                        Log.d(tag, "Chat is closed with ${payload.senderId}, sending DELIVERY_RECEIPT for $messageId")
                        fcmSignalingClient.sendDeliveryReceipt(payload.senderId, messageId)
                    }
                }

                // Only show system notification if user is NOT currently inside the chat screen with this contact
                if (!isChatCurrentlyOpen) {
                    showChatMessageNotification(payload.senderId, payload.senderName, content)
                    ringtoneVibrationManager.playMessageNotification()
                }
            }

            SignalingType.DELIVERY_RECEIPT -> {
                val msgId = payload.messageId ?: return
                Log.d(tag, "Received DELIVERY_RECEIPT for message $msgId from ${payload.senderName}")
                applicationScope.launch {
                    chatRepository.updateMessageStatusIfNotRead(msgId, MessageStatus.DELIVERED)
                }
            }

            SignalingType.READ_RECEIPT -> {
                val ids = (payload.messageIds ?: listOfNotNull(payload.messageId)).filter { it.isNotBlank() }
                if (ids.isNotEmpty()) {
                    Log.i(tag, "Received READ_RECEIPT for messages $ids from ${payload.senderName}")
                    applicationScope.launch {
                        chatRepository.updateMessagesStatus(ids, MessageStatus.READ)
                    }
                }
            }

            SignalingType.PROFILE_UPDATE -> {
                Log.d(tag, "Received PROFILE_UPDATE from ${payload.senderId}: '${payload.senderName}'")
                if (payload.senderName.isNotBlank()) {
                    applicationScope.launch {
                        contactRepository.updateContactName(payload.senderId, payload.senderName)
                    }
                }
            }

            SignalingType.SWITCH_TO_VIDEO_REQUEST -> {
                val currentCall = callRepository.currentCall.value
                if (currentCall != null && currentCall.state == CallState.CONNECTED) {
                    Log.i(tag, "Received SWITCH_TO_VIDEO_REQUEST from ${payload.senderName}")
                    callRepository.setVideoUpgradeRequest(payload.senderName.ifBlank { "Caller" })
                }
            }

            SignalingType.SWITCH_TO_VIDEO_ACCEPT -> {
                Log.i(tag, "Received SWITCH_TO_VIDEO_ACCEPT from ${payload.senderName}")
                callRepository.updateCallType(CallType.VIDEO)
                webRtcManager.upgradeToVideo()
                val currentCall = callRepository.currentCall.value
                if (currentCall != null) {
                    webRtcManager.createRenegotiationOffer { sdp ->
                        fcmSignalingClient.sendRenegotiationOffer(payload.senderId, currentCall.callId, sdp)
                    }
                }
            }

            SignalingType.SWITCH_TO_VIDEO_DECLINE -> {
                Log.i(tag, "Received SWITCH_TO_VIDEO_DECLINE from ${payload.senderName}")
                callRepository.emitMessage("${payload.senderName.ifBlank { "Peer" }} declined switch to video")
            }

            SignalingType.CAMERA_STATE -> {
                val isCameraOn = payload.isCameraOn ?: true
                Log.i(tag, "Received CAMERA_STATE from ${payload.senderName}: isCameraOn=$isCameraOn")
                webRtcManager.setRemoteVideoEnabled(isCameraOn)
            }

            SignalingType.RENEGOTIATION_OFFER -> {
                val sdp = payload.sdp ?: return
                val currentCall = callRepository.currentCall.value
                Log.i(tag, "Received RENEGOTIATION_OFFER from ${payload.senderName}")
                callRepository.updateCallType(CallType.VIDEO)
                webRtcManager.upgradeToVideo()
                webRtcManager.setRemoteDescription(sdp, SessionDescription.Type.OFFER)
                if (currentCall != null) {
                    webRtcManager.createRenegotiationAnswer { answerSdp ->
                        fcmSignalingClient.sendRenegotiationAnswer(payload.senderId, currentCall.callId, answerSdp)
                    }
                }
            }

            SignalingType.RENEGOTIATION_ANSWER -> {
                val sdp = payload.sdp ?: return
                Log.i(tag, "Received RENEGOTIATION_ANSWER from ${payload.senderName}")
                webRtcManager.setRemoteDescription(sdp, SessionDescription.Type.ANSWER)
            }

            SignalingType.PING -> {}
        }
    }

    fun ensureListenersActive() {
        try {
            if (!lanSocketServer.isRunning) {
                lanSocketServer.start(52220)
            }
        } catch (e: Exception) {
            Log.w(tag, "LanSocketServer start error: ${e.message}")
        }
        try {
            if (!bluetoothTransportManager.isRunning) {
                bluetoothTransportManager.startServer()
            }
        } catch (e: Exception) {
            Log.w(tag, "Bluetooth start error: ${e.message}")
        }
        try {
            bluetoothVoiceManager.startVoiceServer()
        } catch (e: Exception) {
            Log.w(tag, "Bluetooth voice server start error: ${e.message}")
        }
    }

    fun resetAudioModeToNormal() {
        try {
            ringtoneVibrationManager.stopAll()
            webRtcManager.audioDeviceManager.resetToNormal()
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
            Log.d(tag, "App audio mode reset to MODE_NORMAL")
        } catch (e: Exception) {
            Log.w(tag, "Failed to reset audio mode: ${e.message}")
        }
    }

    fun showChatMessageNotification(senderId: String, senderName: String, messageText: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("peerId", senderId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            201,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultNotifSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSound(defaultNotifSound)
            .setVibrate(longArrayOf(0, 200))
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(senderId.hashCode(), notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Incoming Calls Channel (High Priority with Sound & Vibration)
            val callSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val callsChannel = NotificationChannel(
                TelecomCallManager.CHANNEL_CALLS_ID,
                context.getString(R.string.channel_calls_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_calls_desc)
                setSound(callSound, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // Chat Messages Channel
            val defaultNotifSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val notifAudioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                context.getString(R.string.channel_messages_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_messages_desc)
                setSound(defaultNotifSound, notifAudioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200)
            }

            notificationManager.createNotificationChannels(listOf(callsChannel, messagesChannel))
        }
    }
}
