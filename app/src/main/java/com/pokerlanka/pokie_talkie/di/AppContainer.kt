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
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pokerlanka.pokie_talkie.BuildConfig
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
import com.pokerlanka.pokie_talkie.wifip2p.WifiDirectManager
import com.pokerlanka.pokie_talkie.telecom.RingtoneVibrationManager
import com.pokerlanka.pokie_talkie.telecom.CallController
import com.pokerlanka.pokie_talkie.telecom.EndReason
import org.webrtc.SessionDescription

import com.pokerlanka.pokie_talkie.data.local.UserPreferencesManager
import com.pokerlanka.pokie_talkie.data.model.Contact
import kotlinx.coroutines.CoroutineExceptionHandler

class AppContainer(val context: Context) {
    private val tag = "AppContainer"

    companion object {
        const val CHANNEL_MESSAGES_ID = "pokie_chat_messages_v2"
    }

    val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            Log.e(tag, "Uncaught exception in applicationScope", throwable)
        }
    )

    // User Preferences & Device Identity
    val userPreferencesManager by lazy { UserPreferencesManager(context) }
    val currentUserId: String get() = userPreferencesManager.userId
    val currentUserName: String get() = userPreferencesManager.getUserName()

    // Database & Repositories
    val database by lazy { PokieDatabase.getInstance(context) }
    val chatRepository by lazy { ChatRepository(database.messageDao(), database.contactDao(), database) }
    val contactRepository by lazy { ContactRepository(database.contactDao(), database) }
    val callRepository by lazy { CallRepository(database.callLogDao(), applicationScope) }
    val callController by lazy {
        CallController(
            callRepository = callRepository,
            webRtcManager = webRtcManager,
            telecomCallManager = telecomCallManager,
            fcmSignalingClient = fcmSignalingClient,
            scope = applicationScope
        )
    }

    // Networking & Transport
    val lanSocketServer by lazy { LanSocketServer(applicationScope) }
    val lanDiscoveryManager by lazy { LanDiscoveryManager(context, contactRepository, applicationScope) }
    val wifiDirectManager by lazy {
        WifiDirectManager(context) { peerIp, isGroupOwner ->
            if (!isGroupOwner) {
                Log.i(tag, "Wi-Fi Direct peer connected with IP: $peerIp")
                applicationScope.launch {
                    addManualContact(
                        name = "Nearby Peer ($peerIp)",
                        ipAddress = peerIp,
                        port = 52220
                    )
                }
            }
        }
    }

    val connectivityMonitor by lazy {
        ConnectivityMonitor(context, applicationScope, wifiDirectManager)
    }

    val fcmV1Client by lazy { com.pokerlanka.pokie_talkie.fcm.FcmV1Client(context) }

    val fcmSignalingClient by lazy {
        FcmSignalingClient(
            contactRepository = contactRepository,
            lanSocketServer = lanSocketServer,
            userPreferencesManager = userPreferencesManager,
            scope = applicationScope,
            myUserId = currentUserId,
            userNameProvider = { currentUserName },
            fcmV1Client = fcmV1Client
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
        // Item 3.4: resetAllLanStatus() before starting discovery
        applicationScope.launch(Dispatchers.IO) {
            contactRepository.resetAllLanStatus()
            val savedFcmToken = userPreferencesManager.getFcmToken()
            lanDiscoveryManager.initialize(currentUserId, currentUserName, lanSocketServer.serverPort, savedFcmToken)
            lanDiscoveryManager.onPeerDiscovered = { peer ->
                Log.i(tag, "LAN peer discovered: ${peer.name} (${peer.id}), sending direct PING exchange")
                fcmSignalingClient.sendPing(peer.id)
            }
            lanDiscoveryManager.startDiscoveryAndRegistration()
        }

        // Fetch fresh FCM registration token from Google Play Services
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        if (!token.isNullOrBlank()) {
                            Log.i(tag, "FCM registration token fetched: ${token.take(15)}...")
                            userPreferencesManager.setFcmToken(token)
                            lanDiscoveryManager.updateFcmToken(token)
                            applicationScope.launch(Dispatchers.IO) {
                                contactRepository.updateFcmToken(currentUserId, token)
                            }
                        }
                    } else {
                        Log.w(tag, "Fetching FCM token failed: ${task.exception?.message}")
                    }
                }
        } catch (e: Exception) {
            Log.w(tag, "FirebaseMessaging token fetch error: ${e.message}")
        }

        // Item 3.7: link lanDiscoveryManager for re-resolving failed LAN sends
        fcmSignalingClient.lanDiscoveryManager = lanDiscoveryManager

        // Setup WebRTC Callbacks
        setupWebRtcCallbacks()

        // Ring timeout must run for the first call too, before anything else touches the lazy controller
        callController.start()

        // Initialize Wi-Fi Direct
        try {
            wifiDirectManager.initialize()
        } catch (e: Exception) {
            Log.w(tag, "Wi-Fi Direct init deferred: ${e.message}")
        }

        // Start Connectivity Monitor with callback for Wi-Fi loss
        connectivityMonitor.onWifiLost = {
            applicationScope.launch(Dispatchers.IO) {
                contactRepository.resetAllLanStatus()
            }
        }
        connectivityMonitor.start()

        // Listen for direct LAN incoming payloads
        listenForLanPayloads()

        // Item 4.3: Auto-check for updates on app start
        checkAutoUpdates()
    }

    private fun checkAutoUpdates() {
        if (!userPreferencesManager.isAutoCheckUpdates()) return
        val lastCheck = userPreferencesManager.getLastUpdateCheckTime()
        val now = System.currentTimeMillis()
        if (now - lastCheck < 24 * 60 * 60 * 1000L) return

        applicationScope.launch(Dispatchers.IO) {
            userPreferencesManager.setLastUpdateCheckTime(now)
            val result = com.pokerlanka.pokie_talkie.utils.Updater.getLatestRelease()
            result.onSuccess { release ->
                val currentVersion = BuildConfig.VERSION_NAME
                if (com.pokerlanka.pokie_talkie.utils.Updater.isUpdateAvailable(release.tagName, currentVersion)) {
                    showUpdateAvailableNotification(release)
                }
            }
        }
    }

    private fun showUpdateAvailableNotification(release: com.pokerlanka.pokie_talkie.utils.ReleaseInfo) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl ?: release.htmlUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            301,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Pokie-Talkie Update Available")
            .setContentText("Version ${release.tagName} is available. Tap to download.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        notificationManager.notify(3001, notification)
    }

    private fun setupWebRtcCallbacks() {
        webRtcManager.onLocalSdpCreated = { sdp ->
            val currentCall = callRepository.currentCall.value
            if (currentCall != null) {
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
            if (currentCall != null) {
                fcmSignalingClient.sendIceCandidate(
                    targetId = currentCall.peerId,
                    callId = currentCall.callId,
                    candidate = candidate
                )
            }
        }

        webRtcManager.onCallDisconnected = {
            callController.endCall(EndReason.CONNECTION_FAILED, notifyPeer = true)
        }
    }

    private fun listenForLanPayloads() {
        applicationScope.launch(Dispatchers.IO) {
            lanSocketServer.incomingPayloads.collect { payload ->
                handleIncomingSignalingPayload(payload, "LAN")
            }
        }
    }

    fun handleIncomingSignalingPayload(payload: SignalingPayload, source: String) {
        try {
            Log.d(tag, "Handling incoming $source payload [${payload.type}] from ${payload.senderName} (${payload.senderId})")

            val isLan = (source == "LAN")
            val effectiveIp = if (isLan && !payload.sourceIp.isNullOrBlank()) payload.sourceIp else payload.senderIp
            val effectivePort = payload.senderPort ?: 52220

            applicationScope.launch(Dispatchers.IO) {
                val existing = contactRepository.getContact(payload.senderId)
                if (existing == null) {
                    val newContact = Contact(
                        id = payload.senderId,
                        name = payload.senderName.ifBlank { "Family Member" },
                        fcmToken = payload.senderFcmToken ?: "",
                        ipAddress = effectiveIp ?: "",
                        port = effectivePort,
                        isOnlineLan = isLan,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )
                    contactRepository.saveOrUpdateContact(newContact)
                    Log.i(tag, "Saved new contact from $source: ${newContact.name} (${newContact.id}, IP: ${newContact.ipAddress}, FCM: ${newContact.fcmToken.take(15)}...)")
                } else {
                    var updated = existing
                    var changed = false
                    if (!payload.senderFcmToken.isNullOrBlank() && payload.senderFcmToken != existing.fcmToken) {
                        updated = updated.copy(fcmToken = payload.senderFcmToken)
                        changed = true
                        Log.i(tag, "Updated FCM token for ${existing.name} from $source payload")
                    }
                    if (!effectiveIp.isNullOrBlank() && (effectiveIp != existing.ipAddress || (isLan && !existing.isOnlineLan))) {
                        updated = updated.copy(
                            ipAddress = effectiveIp,
                            port = effectivePort,
                            isOnlineLan = isLan || existing.isOnlineLan
                        )
                        changed = true
                        Log.i(tag, "Updated LAN info for ${existing.name}: $effectiveIp:$effectivePort (online: ${updated.isOnlineLan})")
                    } else if (isLan && !existing.isOnlineLan) {
                        updated = updated.copy(isOnlineLan = true)
                        changed = true
                    }
                    if (payload.senderName.isNotBlank() && payload.senderName != existing.name) {
                        updated = updated.copy(name = payload.senderName)
                        changed = true
                    }
                    if (changed) {
                        contactRepository.saveOrUpdateContact(updated.copy(lastSeenTimestamp = System.currentTimeMillis()))
                    }
                }
            }

            when (payload.type) {
            SignalingType.OFFER -> {
                val callType = payload.callType ?: CallType.VOICE
                val incomingCallId = payload.callId ?: System.currentTimeMillis().toString()
                val currentCall = callRepository.currentCall.value

                if (currentCall != null && currentCall.state in listOf(CallState.RINGING_INCOMING, CallState.RINGING_OUTGOING, CallState.CONNECTED)) {
                    if (currentCall.callId != incomingCallId) {
                        Log.w(tag, "Busy: active call ${currentCall.callId} in progress, rejecting offer $incomingCallId from ${payload.senderId}")
                        if (incomingCallId.isNotBlank()) {
                            fcmSignalingClient.sendHangup(payload.senderId, incomingCallId)
                        }
                        return
                    } else {
                        Log.d(tag, "Ignoring duplicate OFFER for callId $incomingCallId")
                        return
                    }
                }

                // Store pending remote SDP offer for when the user answers
                webRtcManager.pendingRemoteOfferSdp = payload.sdp

                telecomCallManager.handleIncomingCall(
                    callerId = payload.senderId,
                    callerName = payload.senderName,
                    callType = callType,
                    callId = incomingCallId
                )
            }

            SignalingType.ANSWER -> {
                val currentCall = callRepository.currentCall.value
                val incomingCallId = payload.callId ?: ""
                if (currentCall == null || currentCall.callId != incomingCallId || currentCall.state != CallState.RINGING_OUTGOING) {
                    Log.w(tag, "Ignoring ANSWER: callId mismatch or not in RINGING_OUTGOING (current=${currentCall?.callId}, incoming=$incomingCallId, state=${currentCall?.state})")
                    return
                }
                // Stop ringback tone on the sender side when remote answers
                ringtoneVibrationManager.stopAll()
                callController.onCallConnected()
                payload.sdp?.let { sdpString ->
                    webRtcManager.setRemoteDescription(sdpString, SessionDescription.Type.ANSWER)
                }
                PokieConnectionService.activeConnection?.markConnected()
                callRepository.updateCallState(CallState.CONNECTED)
            }

            SignalingType.ICE_CANDIDATE -> {
                val currentCall = callRepository.currentCall.value
                val incomingCallId = payload.callId ?: ""
                if (currentCall == null || currentCall.callId != incomingCallId) {
                    Log.d(tag, "Ignoring ICE_CANDIDATE for non-matching callId: $incomingCallId (current=${currentCall?.callId})")
                    return
                }
                payload.iceCandidate?.let { candidate ->
                    webRtcManager.addRemoteIceCandidate(candidate)
                }
            }

            SignalingType.HANGUP -> {
                val currentCall = callRepository.currentCall.value
                val incomingCallId = payload.callId ?: ""
                if (currentCall != null && currentCall.callId == incomingCallId) {
                    Log.i(tag, "Received matching HANGUP for call $incomingCallId from ${payload.senderName}")
                    callController.endCall(EndReason.REMOTE_HANGUP, notifyPeer = false, expectedCallId = incomingCallId)
                } else {
                    Log.d(tag, "Ignoring HANGUP for non-matching callId: $incomingCallId (current=${currentCall?.callId})")
                }
            }

            SignalingType.CHAT_MESSAGE -> {
                val content = payload.messageContent ?: return
                val type = payload.messageType ?: MessageType.TEXT
                val messageId = payload.messageId ?: UUID.randomUUID().toString()
                val isChatCurrentlyOpen = activeChatPeerId == payload.senderId
                val initialStatus = if (isChatCurrentlyOpen) MessageStatus.READ else MessageStatus.DELIVERED

                applicationScope.launch(Dispatchers.IO) {
                    val existingContact = contactRepository.getContact(payload.senderId)
                    val contactIp = if (isLan && !payload.sourceIp.isNullOrBlank()) payload.sourceIp!! else ""

                    if (existingContact == null) {
                        val byLan = if (contactIp.isNotBlank()) contactRepository.getContact("lan_${contactIp.replace(".", "_")}") else null
                        val oldContact = byLan

                        if (oldContact != null) {
                            contactRepository.mergeContactAndMigrateMessages(
                                oldId = oldContact.id,
                                newContact = oldContact.copy(
                                    id = payload.senderId,
                                    name = payload.senderName.ifBlank { oldContact.name },
                                    isOnlineLan = isLan || oldContact.isOnlineLan,
                                    ipAddress = if (contactIp.isNotBlank()) contactIp else oldContact.ipAddress,
                                    port = 52220,
                                    lastSeenTimestamp = System.currentTimeMillis()
                                )
                            )
                        } else {
                            contactRepository.saveOrUpdateContact(
                                Contact(
                                    id = payload.senderId,
                                    name = payload.senderName.ifBlank { "Family Member" },
                                    isOnlineLan = isLan,
                                    ipAddress = contactIp,
                                    port = 52220,
                                    lastSeenTimestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    } else {
                        val byLan = if (contactIp.isNotBlank() && "lan_${contactIp.replace(".", "_")}" != payload.senderId) contactRepository.getContact("lan_${contactIp.replace(".", "_")}") else null
                        val oldContact = byLan
                        if (oldContact != null) {
                            contactRepository.mergeContactAndMigrateMessages(
                                oldId = oldContact.id,
                                newContact = existingContact.copy(
                                    name = payload.senderName.ifBlank { existingContact.name },
                                    isOnlineLan = isLan || existingContact.isOnlineLan,
                                    ipAddress = if (contactIp.isNotBlank()) contactIp else existingContact.ipAddress,
                                    port = 52220,
                                    lastSeenTimestamp = System.currentTimeMillis()
                                )
                            )
                        } else {
                            if (isLan && !payload.sourceIp.isNullOrBlank()) {
                                contactRepository.updateLanStatus(payload.senderId, true, payload.sourceIp!!, 52220)
                            } else {
                                contactRepository.updateLanOnlineStatusOnly(payload.senderId, isLan)
                            }
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
                val incomingCallId = payload.callId ?: ""
                if (currentCall != null && currentCall.callId == incomingCallId && currentCall.state == CallState.CONNECTED) {
                    Log.i(tag, "Received SWITCH_TO_VIDEO_REQUEST from ${payload.senderName}")
                    callRepository.setVideoUpgradeRequest(payload.senderName.ifBlank { "Caller" })
                }
            }

            SignalingType.SWITCH_TO_VIDEO_ACCEPT -> {
                val currentCall = callRepository.currentCall.value
                val incomingCallId = payload.callId ?: ""
                if (currentCall != null && currentCall.callId == incomingCallId && currentCall.state == CallState.CONNECTED) {
                    Log.i(tag, "Received SWITCH_TO_VIDEO_ACCEPT from ${payload.senderName}")
                    callRepository.updateCallType(CallType.VIDEO)
                    webRtcManager.upgradeToVideo()
                }
            }

            SignalingType.SWITCH_TO_VIDEO_DECLINE -> {
                val currentCall = callRepository.currentCall.value
                val incomingCallId = payload.callId ?: ""
                if (currentCall != null && currentCall.callId == incomingCallId) {
                    Log.i(tag, "Received SWITCH_TO_VIDEO_DECLINE from ${payload.senderName}")
                    callRepository.emitMessage("${payload.senderName.ifBlank { "Peer" }} declined switch to video")
                }
            }

            SignalingType.CAMERA_STATE -> {
                val currentCall = callRepository.currentCall.value
                val incomingCallId = payload.callId ?: ""
                if (currentCall != null && currentCall.callId == incomingCallId) {
                    val isCameraOn = payload.isCameraOn ?: true
                    Log.i(tag, "Received CAMERA_STATE from ${payload.senderName}: isCameraOn=$isCameraOn")
                    webRtcManager.setRemoteVideoEnabled(isCameraOn)
                }
            }

            SignalingType.RENEGOTIATION_OFFER -> {
                val sdp = payload.sdp ?: return
                val currentCall = callRepository.currentCall.value
                val incomingCallId = payload.callId ?: ""
                if (currentCall != null && currentCall.callId == incomingCallId && currentCall.state == CallState.CONNECTED) {
                    Log.i(tag, "Received RENEGOTIATION_OFFER from ${payload.senderName}")
                    callRepository.updateCallType(CallType.VIDEO)
                    webRtcManager.upgradeToVideo()
                    webRtcManager.setRemoteDescription(sdp, SessionDescription.Type.OFFER, autoAnswer = false)
                    webRtcManager.createRenegotiationAnswer { answerSdp ->
                        fcmSignalingClient.sendRenegotiationAnswer(payload.senderId, currentCall.callId, answerSdp)
                    }
                }
            }

            SignalingType.RENEGOTIATION_ANSWER -> {
                val sdp = payload.sdp ?: return
                val currentCall = callRepository.currentCall.value
                val incomingCallId = payload.callId ?: ""
                if (currentCall != null && currentCall.callId == incomingCallId && currentCall.state == CallState.CONNECTED) {
                    Log.i(tag, "Received RENEGOTIATION_ANSWER from ${payload.senderName}")
                    webRtcManager.setRemoteDescription(sdp, SessionDescription.Type.ANSWER)
                }
            }

            SignalingType.PING -> {
                Log.i(tag, "Received PING from ${payload.senderName} (${payload.senderId}), responding with PONG")
                fcmSignalingClient.sendPong(payload.senderId)
            }
            SignalingType.PONG -> {
                Log.i(tag, "Received PONG from ${payload.senderName} (${payload.senderId}) - Peer handshake confirmed")
            }
            }
        } catch (e: Throwable) {
            Log.e(tag, "Error handling incoming $source payload: ${e.message}", e)
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
            senderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(senderId.hashCode(), notification)
        ringtoneVibrationManager.playMessageNotification()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            try {
                notificationManager.deleteNotificationChannel("pokie_incoming_calls")
                notificationManager.deleteNotificationChannel("pokie_chat_messages")
            } catch (e: Exception) {
                Log.w(tag, "Failed to delete old notification channels: ${e.message}")
            }

            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            // Incoming Calls Channel (Full audio & vibration attributes for heads-up and fullScreenIntent)
            val callsChannel = NotificationChannel(
                TelecomCallManager.CHANNEL_CALLS_ID,
                context.getString(R.string.channel_calls_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_calls_desc)
                setSound(ringtoneUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 1000, 1000)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // Chat Messages Channel (Silent channel - ringtoneVibrationManager plays message sound & vibration per prefs)
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                context.getString(R.string.channel_messages_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_messages_desc)
                setSound(null, null)
                enableVibration(false)
            }

            notificationManager.createNotificationChannels(listOf(callsChannel, messagesChannel))
        }
    }
}
