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
import com.pokerlanka.pokie_talkie.consent.ConsentBus
import com.pokerlanka.pokie_talkie.consent.ConsentRequest
import com.pokerlanka.pokie_talkie.data.local.PokieDatabase
import com.pokerlanka.pokie_talkie.data.model.CallSession
import com.pokerlanka.pokie_talkie.data.model.CallState
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.IceCandidateModel
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
import com.pokerlanka.pokie_talkie.lan.LanMediaServer
import com.pokerlanka.pokie_talkie.lan.LanSocketServer
import com.pokerlanka.pokie_talkie.lan.MediaTransferHeader
import com.pokerlanka.pokie_talkie.lan.MediaTransferKind
import com.pokerlanka.pokie_talkie.lan.ConnectivityMonitor
import com.pokerlanka.pokie_talkie.utils.MediaStorageHelper
import com.pokerlanka.pokie_talkie.utils.PreparedMediaFile
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
import com.pokerlanka.pokie_talkie.telecom.RingtoneVibrationManager
import com.pokerlanka.pokie_talkie.telecom.CallController
import com.pokerlanka.pokie_talkie.telecom.EndReason
import com.pokerlanka.pokie_talkie.telecom.ProximityLock
import com.pokerlanka.pokie_talkie.service.PokieLocalService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.SessionDescription

import com.pokerlanka.pokie_talkie.data.local.ConnectionMode
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

    // Shared JSON serializer to avoid repeated Gson allocation & reflection cache duplication
    val gson by lazy { com.google.gson.Gson() }

    // Database & Repositories
    val database by lazy { PokieDatabase.getInstance(context) }
    val chatRepository by lazy { ChatRepository(database.messageDao(), database.contactDao(), database) }
    val contactRepository by lazy { ContactRepository(database.contactDao(), database) }
    val callRepository by lazy { CallRepository(applicationScope) }
    val callController by lazy {
        CallController(
            callRepository = callRepository,
            webRtcManager = webRtcManager,
            bluetoothVoiceManager = bluetoothVoiceManager,
            telecomCallManager = telecomCallManager,
            fcmSignalingClient = fcmSignalingClient,
            ringtoneVibrationManager = ringtoneVibrationManager,
            proximityLock = proximityLock,
            scope = applicationScope
        )
    }

    // Networking & Bluetooth Transport
    val lanSocketServer by lazy { LanSocketServer(applicationScope, gson) }
    val lanMediaServer by lazy { LanMediaServer(context, applicationScope, gson) }
    val lanDiscoveryManager by lazy { LanDiscoveryManager(context, contactRepository, applicationScope) }
    val bluetoothTransportManager by lazy { BluetoothTransportManager(context, applicationScope, contactRepository, gson) }
    val bluetoothVoiceManager by lazy {
        BluetoothVoiceManager(
            context = context,
            scope = applicationScope,
            bluetoothAdapter = bluetoothTransportManager.bluetoothAdapter,
            audioDeviceManager = webRtcManager.audioDeviceManager
        )
    }

    val connectivityMonitor by lazy {
        ConnectivityMonitor(context, bluetoothTransportManager)
    }

    val fcmV1Client by lazy { com.pokerlanka.pokie_talkie.fcm.FcmV1Client(context, gson) }

    val fcmSignalingClient by lazy {
        FcmSignalingClient(
            contactRepository = contactRepository,
            lanSocketServer = lanSocketServer,
            bluetoothTransportManager = bluetoothTransportManager,
            userPreferencesManager = userPreferencesManager,
            scope = applicationScope,
            myUserId = currentUserId,
            userNameProvider = { currentUserName },
            fcmV1Client = fcmV1Client,
            gson = gson
        )
    }

    val webRtcManager by lazy { WebRtcManager(context, applicationScope) }
    val ringtoneVibrationManager by lazy { RingtoneVibrationManager(context, userPreferencesManager) }
    val proximityLock by lazy { ProximityLock(context) }
    val telecomCallManager by lazy { TelecomCallManager(context, callRepository, webRtcManager, ringtoneVibrationManager) }

    @Volatile
    var activeChatPeerId: String? = null

    // FCM doesn't preserve order, so ICE candidates can arrive before their OFFER: hold them by callId
    private val earlyIceCandidates = java.util.concurrent.ConcurrentHashMap<String, MutableList<IceCandidateModel>>()
    private val inFlightAvatarRequests = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val handledConsentRequestIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

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
        onMyProfileUpdated(pushAvatarToPeers = false)
    }

    fun onMyProfileUpdated(pushAvatarToPeers: Boolean) {
        val name = userPreferencesManager.getUserName()
        val color = userPreferencesManager.getAvatarColor()
        val hash = userPreferencesManager.getProfileImageHash()
        lanDiscoveryManager.updateProfile(name, color, hash)
        fcmSignalingClient.broadcastProfileUpdate(name, color, hash)
        if (pushAvatarToPeers && hash.isNotBlank()) {
            applicationScope.launch(Dispatchers.IO) {
                val contacts = contactRepository.getContacts().firstOrNull() ?: emptyList()
                for (c in contacts) {
                    if (c.id != currentUserId && !c.id.contains(":") && !c.id.startsWith("lan_")) {
                        sendMyAvatarToPeer(c.id)
                    }
                }
            }
        }
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

        // Initialize NSD / mDNS profile info synchronously BEFORE ensureListenersActive()
        // so registerService() never advertises an empty userId ("Pokie_").
        val savedFcmToken = userPreferencesManager.getFcmToken()
        lanDiscoveryManager.initialize(
            userId = currentUserId,
            userName = currentUserName,
            port = lanSocketServer.serverPort,
            fcmToken = savedFcmToken,
            avatarColor = userPreferencesManager.getAvatarColor(),
            avatarHash = userPreferencesManager.getProfileImageHash()
        )
        lanDiscoveryManager.onPeerDiscovered = { peer ->
            Log.i(tag, "LAN peer discovered: ${peer.name} (${peer.id}), sending direct PING exchange")
            if (userPreferencesManager.getConnectionMode() == ConnectionMode.WIFI) {
                fcmSignalingClient.sendPing(peer.id)
                applicationScope.launch(Dispatchers.IO) {
                    syncAvatarIfNeeded(peer.id, peer.avatarColor, peer.avatarHash)
                }
            }
        }

        // Start LAN Socket & Media Servers only if in Wi-Fi mode
        if (userPreferencesManager.getConnectionMode() == ConnectionMode.WIFI) {
            lanSocketServer.start(52220)
            lanMediaServer.start(LanMediaServer.DEFAULT_MEDIA_PORT)
        }

        // Reset LAN status, remove any legacy blank-ID ghost contacts, and start discovery
        applicationScope.launch(Dispatchers.IO) {
            contactRepository.deleteContact("")
            contactRepository.resetAllLanStatus()
            if (userPreferencesManager.getConnectionMode() == ConnectionMode.WIFI) {
                lanDiscoveryManager.startDiscoveryAndRegistration()
            }
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

        // Item 3.2: reject Bluetooth voice sockets if call is not active or app is not in Bluetooth mode
        bluetoothVoiceManager.isBluetoothCallActiveProvider = {
            val currentCall = callRepository.currentCall.value
            userPreferencesManager.getConnectionMode() == ConnectionMode.BLUETOOTH &&
                currentCall != null &&
                currentCall.state == CallState.CONNECTED &&
                currentCall.isBluetoothVoice
        }

        // Setup WebRTC Callbacks
        setupWebRtcCallbacks()

        // Ring timeout must run for the first call too, before anything else touches the lazy controller
        callController.start()

        // Start mode-appropriate listeners (Wi-Fi vs Bluetooth)
        ensureListenersActive()

        // Start Connectivity Monitor with callbacks for BT restore and Wi-Fi loss
        connectivityMonitor.onBluetoothStateOn = {
            ensureListenersActive()
        }
        connectivityMonitor.onWifiLost = {
            lanSocketServer.invalidateCachedIp()
            lanDiscoveryManager.clearDiscoveryCache()
            applicationScope.launch(Dispatchers.IO) {
                contactRepository.resetAllLanStatus()
            }
        }
        connectivityMonitor.start()

        // Wire Bluetooth Call Outgoing Trigger
        telecomCallManager.onBluetoothCallInitiated = { recipientId, recipientName, callId ->
            fcmSignalingClient.sendBluetoothCallOffer(recipientId, userPreferencesManager.getUserName(), callId)
        }

        fcmSignalingClient.onCallFallbackToBluetooth = { _ ->
            webRtcManager.endCall()
            callRepository.updateBluetoothVoice(true)
        }

        applicationScope.launch(Dispatchers.IO) {
            bluetoothTransportManager.syncPairedDevicesToContacts()
        }

        // Listen for direct LAN, Media & Bluetooth incoming payloads
        listenForLanPayloads()
        listenForLanMediaTransfers()
        listenForBluetoothPayloads()

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

    private fun listenForLanMediaTransfers() {
        applicationScope.launch(Dispatchers.IO) {
            lanMediaServer.incomingTransfers.collect { transfer ->
                val header = transfer.header
                val senderId = header.senderId
                if (senderId.isBlank()) return@collect

                when (header.kind) {
                    MediaTransferKind.AVATAR -> {
                        val existing = contactRepository.getContact(senderId)
                        val color = header.avatarColor ?: existing?.avatarColor ?: UserPreferencesManager.DEFAULT_AVATAR_COLOR
                        val hash = header.avatarHash ?: ""
                        if (existing != null) {
                            contactRepository.updateAvatarInfo(
                                id = senderId,
                                avatarColor = color,
                                profileImagePath = transfer.savedFile.absolutePath,
                                avatarHash = hash
                            )
                        } else {
                            contactRepository.saveOrUpdateContact(
                                Contact(
                                    id = senderId,
                                    name = header.senderName.ifBlank { "Family Member" },
                                    ipAddress = transfer.sourceIp ?: "",
                                    isOnlineLan = !transfer.sourceIp.isNullOrBlank(),
                                    avatarColor = color,
                                    profileImagePath = transfer.savedFile.absolutePath,
                                    avatarHash = hash
                                )
                            )
                        }
                        inFlightAvatarRequests.remove(senderId)
                    }
                    MediaTransferKind.MEDIA -> {
                        val msgId = header.messageId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                        val msgType = header.messageType ?: MessageType.FILE
                        val isChatCurrentlyOpen = activeChatPeerId == senderId
                        val initialStatus = if (isChatCurrentlyOpen) MessageStatus.READ else MessageStatus.DELIVERED
                        val displayContent = when (msgType) {
                            MessageType.IMAGE -> "Photo"
                            MessageType.GIF -> "GIF"
                            MessageType.STICKER -> "Sticker"
                            else -> header.fileName ?: "File"
                        }

                        chatRepository.saveMessage(
                            id = msgId,
                            senderId = senderId,
                            receiverId = currentUserId,
                            content = displayContent,
                            type = msgType,
                            isFromMe = false,
                            status = initialStatus,
                            localFilePath = transfer.savedFile.absolutePath,
                            mimeType = header.mimeType,
                            fileName = header.fileName,
                            fileSize = header.fileSize
                        )

                        if (isChatCurrentlyOpen) {
                            fcmSignalingClient.sendReadReceipt(senderId, listOf(msgId))
                        } else {
                            fcmSignalingClient.sendDeliveryReceipt(senderId, msgId)
                            val notifText = when (msgType) {
                                MessageType.IMAGE -> "📷 Photo"
                                MessageType.GIF -> "🎞️ GIF"
                                MessageType.STICKER -> "✨ Sticker"
                                else -> "📎 ${header.fileName ?: "File"}"
                            }
                            showChatMessageNotification(senderId, header.senderName.ifBlank { "Family Member" }, notifText)
                        }
                    }
                }
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

    fun handleIncomingSignalingPayload(payload: SignalingPayload, source: String) {
        try {
            val activeMode = userPreferencesManager.getConnectionMode()
            if (activeMode == ConnectionMode.WIFI && source == "Bluetooth") {
                Log.i(tag, "Ignoring incoming Bluetooth payload [${payload.type}] because app is in Wi-Fi mode")
                return
            }
            if (activeMode == ConnectionMode.BLUETOOTH && (source == "LAN" || source == "FCM")) {
                Log.i(tag, "Ignoring incoming $source payload [${payload.type}] because app is in Bluetooth mode")
                return
            }

            Log.d(tag, "Handling incoming $source payload [${payload.type}] from ${payload.senderName} (${payload.senderId})")

            val isLan = (source == "LAN")
            // Only trust an IP seen on the LAN socket: senderIp from FCM/Bluetooth is a private address on another network
            val effectiveIp = if (isLan) payload.sourceIp?.takeIf { it.isNotBlank() } ?: payload.senderIp else null
            val effectivePort = payload.senderPort ?: 52220

            applicationScope.launch(Dispatchers.IO) {
                val existing = contactRepository.getContact(payload.senderId)
                val peerBtAddr = if (source == "Bluetooth") bluetoothTransportManager.getPeerAddress(payload.senderId) else null
                if (existing == null) {
                    val oldBtContact = if (!peerBtAddr.isNullOrBlank()) contactRepository.getContact(peerBtAddr) else null
                    val newContact = Contact(
                        id = payload.senderId,
                        name = payload.senderName.ifBlank { oldBtContact?.name ?: "Family Member" },
                        fcmToken = payload.senderFcmToken ?: oldBtContact?.fcmToken ?: "",
                        bluetoothAddress = peerBtAddr ?: oldBtContact?.bluetoothAddress ?: "",
                        ipAddress = effectiveIp ?: "",
                        port = effectivePort,
                        isOnlineLan = isLan,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )
                    if (oldBtContact != null && oldBtContact.id != payload.senderId) {
                        contactRepository.mergeContactAndMigrateMessages(oldBtContact.id, newContact)
                        Log.i(tag, "Merged paired BT contact ${oldBtContact.id} into ${newContact.name} (${newContact.id})")
                    } else {
                        contactRepository.saveOrUpdateContact(newContact)
                        Log.i(tag, "Saved new contact from $source: ${newContact.name} (${newContact.id}, IP: ${newContact.ipAddress}, FCM: ${newContact.fcmToken.take(15)}...)")
                    }
                } else {
                    var updated = existing
                    var changed = false
                    if (!payload.senderFcmToken.isNullOrBlank() && payload.senderFcmToken != existing.fcmToken) {
                        updated = updated.copy(fcmToken = payload.senderFcmToken)
                        changed = true
                        Log.i(tag, "Updated FCM token for ${existing.name} from $source payload")
                    }
                    if (!peerBtAddr.isNullOrBlank() && !peerBtAddr.equals(existing.bluetoothAddress, ignoreCase = true)) {
                        updated = updated.copy(bluetoothAddress = peerBtAddr)
                        changed = true
                        Log.i(tag, "Updated Bluetooth address for ${existing.name}: $peerBtAddr")
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
                    val oldBtContact = if (!peerBtAddr.isNullOrBlank() && peerBtAddr != existing.id) {
                        contactRepository.getContact(peerBtAddr)
                    } else null
                    if (oldBtContact != null) {
                        contactRepository.mergeContactAndMigrateMessages(
                            oldId = oldBtContact.id,
                            newContact = updated.copy(lastSeenTimestamp = System.currentTimeMillis())
                        )
                        Log.i(tag, "Merged duplicate BT contact ${oldBtContact.id} into ${updated.name}")
                    } else if (changed) {
                        contactRepository.saveOrUpdateContact(updated.copy(lastSeenTimestamp = System.currentTimeMillis()))
                    }
                }
                if (payload.senderAvatarColor != null || payload.senderAvatarHash != null) {
                    syncAvatarIfNeeded(
                        peerId = payload.senderId,
                        remoteAvatarColor = payload.senderAvatarColor,
                        remoteAvatarHash = payload.senderAvatarHash
                    )
                }
            }

            when (payload.type) {
            SignalingType.OFFER -> {
                val callType = payload.callType ?: CallType.VOICE
                val incomingCallId = payload.callId ?: System.currentTimeMillis().toString()
                val currentCall = callRepository.currentCall.value
                val earlyCandidates = earlyIceCandidates.remove(incomingCallId)

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

                val isBtCall = payload.isBluetoothVoice ||
                    payload.sdp.isNullOrBlank() ||
                    source == "Bluetooth"

                if (!isBtCall) {
                    // Store pending remote SDP offer for when the user answers
                    webRtcManager.pendingRemoteOfferSdp = payload.sdp
                    // No peer connection yet, so WebRtcManager buffers these until the offer is applied
                    earlyCandidates?.forEach { webRtcManager.addRemoteIceCandidate(it) }
                } else {
                    bluetoothVoiceManager.startVoiceServer()
                }

                telecomCallManager.handleIncomingCall(
                    callerId = payload.senderId,
                    callerName = payload.senderName,
                    callType = if (isBtCall) CallType.VOICE else callType,
                    callId = incomingCallId,
                    isBluetoothVoice = isBtCall
                )
            }

            SignalingType.ANSWER -> {
                val currentCall = callRepository.currentCall.value
                val incomingCallId = payload.callId ?: ""
                if (currentCall == null || currentCall.callId != incomingCallId || currentCall.state != CallState.RINGING_OUTGOING) {
                    Log.w(tag, "Ignoring ANSWER: callId mismatch or not in RINGING_OUTGOING (current=${currentCall?.callId}, incoming=$incomingCallId, state=${currentCall?.state})")
                    return
                }
                val isBtCall = payload.isBluetoothVoice || currentCall.isBluetoothVoice
                // Stop ringback tone on the sender side when remote answers
                ringtoneVibrationManager.stopAll()
                callController.onCallConnected()
                if (isBtCall) {
                    Log.i(tag, "Received Bluetooth call ANSWER from ${payload.senderName}")
                    PokieConnectionService.activeConnection?.markConnected()
                    callRepository.updateCallState(CallState.CONNECTED)
                    val dialedPeerId = currentCall.peerId
                    applicationScope.launch(Dispatchers.IO) {
                        // The ANSWER may arrive over LAN or FCM, so peerAddressMap can be empty: also try the
                        // address stored on the contact, and the dialed contact ID when it is itself a MAC.
                        val storedAddress = contactRepository.getContact(payload.senderId)?.bluetoothAddress?.takeIf { it.isNotBlank() }
                            ?: contactRepository.getContact(dialedPeerId)?.bluetoothAddress?.takeIf { it.isNotBlank() }
                            ?: dialedPeerId.takeIf { android.bluetooth.BluetoothAdapter.checkBluetoothAddress(it) }
                        val peerBtAddress = bluetoothTransportManager.getKnownOrPairedAddress(
                            payload.senderId,
                            payload.senderName,
                            storedAddress
                        )
                        if (!peerBtAddress.isNullOrBlank()) {
                            kotlinx.coroutines.delay(250)
                            bluetoothVoiceManager.startOutgoingCall(peerBtAddress)
                        } else {
                            Log.e(tag, "Cannot start Bluetooth voice: unknown MAC for ${payload.senderId}")
                        }
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
                val currentCall = callRepository.currentCall.value
                val incomingCallId = payload.callId ?: ""
                if (currentCall == null || currentCall.callId != incomingCallId) {
                    val candidate = payload.iceCandidate
                    if (candidate != null && incomingCallId.isNotBlank()) {
                        Log.d(tag, "Holding early ICE_CANDIDATE for callId $incomingCallId until its OFFER arrives (current=${currentCall?.callId})")
                        if (earlyIceCandidates.size > 4 && !earlyIceCandidates.containsKey(incomingCallId)) {
                            earlyIceCandidates.clear()
                        }
                        val list = earlyIceCandidates.getOrPut(incomingCallId) { java.util.Collections.synchronizedList(mutableListOf()) }
                        if (list.size < 50) list.add(candidate)
                    }
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
                    val remoteBtAddress = bluetoothTransportManager.getPeerAddress(payload.senderId) ?: ""
                    val contactIp = if (isLan && !payload.sourceIp.isNullOrBlank()) payload.sourceIp!! else ""

                    if (existingContact == null) {
                        val byBt = if (remoteBtAddress.isNotBlank()) contactRepository.getContact(remoteBtAddress) else null
                        val byLan = if (contactIp.isNotBlank()) contactRepository.getContact("lan_${contactIp.replace(".", "_")}") else null
                        val oldContact = byBt ?: byLan

                        if (oldContact != null) {
                            contactRepository.mergeContactAndMigrateMessages(
                                oldId = oldContact.id,
                                newContact = oldContact.copy(
                                    id = payload.senderId,
                                    name = payload.senderName.ifBlank { oldContact.name },
                                    bluetoothAddress = remoteBtAddress.ifBlank { oldContact.bluetoothAddress },
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
                                    bluetoothAddress = remoteBtAddress,
                                    isOnlineLan = isLan,
                                    ipAddress = contactIp,
                                    port = 52220,
                                    lastSeenTimestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    } else {
                        val byBt = if (remoteBtAddress.isNotBlank() && remoteBtAddress != payload.senderId) contactRepository.getContact(remoteBtAddress) else null
                        val byLan = if (contactIp.isNotBlank() && "lan_${contactIp.replace(".", "_")}" != payload.senderId) contactRepository.getContact("lan_${contactIp.replace(".", "_")}") else null
                        val oldContact = byBt ?: byLan
                        if (oldContact != null) {
                            contactRepository.mergeContactAndMigrateMessages(
                                oldId = oldContact.id,
                                newContact = existingContact.copy(
                                    name = payload.senderName.ifBlank { existingContact.name },
                                    bluetoothAddress = remoteBtAddress.ifBlank { existingContact.bluetoothAddress },
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
                            if (remoteBtAddress.isNotBlank() && existingContact.bluetoothAddress.isBlank()) {
                                contactRepository.updateBluetoothAddress(payload.senderId, remoteBtAddress)
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
                    applicationScope.launch(Dispatchers.IO) {
                        contactRepository.updateContactName(payload.senderId, payload.senderName)
                    }
                }
            }

            SignalingType.AVATAR_REQUEST -> {
                Log.d(tag, "Received AVATAR_REQUEST from ${payload.senderId}")
                applicationScope.launch(Dispatchers.IO) {
                    sendMyAvatarToPeer(payload.senderId)
                }
            }

            SignalingType.AVATAR_DATA -> {
                val base64 = payload.avatarBase64 ?: return
                applicationScope.launch(Dispatchers.IO) {
                    try {
                        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                        val saved = MediaStorageHelper.savePeerAvatarBytes(
                            context = context,
                            peerId = payload.senderId,
                            bytes = bytes,
                            hashHint = payload.senderAvatarHash
                        )
                        if (saved != null) {
                            val (path, hash) = saved
                            val existing = contactRepository.getContact(payload.senderId)
                            val color = payload.senderAvatarColor ?: existing?.avatarColor ?: UserPreferencesManager.DEFAULT_AVATAR_COLOR
                            contactRepository.updateAvatarInfo(payload.senderId, color, path, hash)
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Failed decoding AVATAR_DATA from ${payload.senderId}: ${e.message}")
                    } finally {
                        inFlightAvatarRequests.remove(payload.senderId)
                    }
                }
            }

            SignalingType.MESSAGE_EDIT -> {
                val msgId = payload.messageId ?: return
                val newText = payload.messageContent ?: return
                applicationScope.launch(Dispatchers.IO) {
                    chatRepository.editMessage(msgId, newText)
                }
            }

            SignalingType.MESSAGE_UNSEND -> {
                val msgId = payload.messageId ?: return
                applicationScope.launch(Dispatchers.IO) {
                    chatRepository.markMessageUnsent(msgId)
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
                    Log.i(tag, "Received CAMERA_STATE from ${payload.senderName}: isCameraOn=$isCameraOn, isScreenShare=${payload.isScreenShare}")
                    webRtcManager.setRemoteVideoEnabled(isCameraOn, payload.isScreenShare)
                }
            }

            SignalingType.RENEGOTIATION_OFFER -> {
                val sdp = payload.sdp ?: return
                val currentCall = callRepository.currentCall.value
                val incomingCallId = payload.callId ?: ""
                if (currentCall != null && currentCall.callId == incomingCallId && currentCall.state == CallState.CONNECTED) {
                    val isScreenShareOffer = payload.isScreenShare == true
                    Log.i(tag, "Received RENEGOTIATION_OFFER from ${payload.senderName} (isScreenShare=$isScreenShareOffer)")
                    if (isScreenShareOffer) {
                        webRtcManager.prepareRemoteVideoOnly()
                        webRtcManager.setRemoteVideoEnabled(true, isScreenShare = true)
                    } else {
                        callRepository.updateCallType(CallType.VIDEO)
                        webRtcManager.upgradeToVideo()
                    }
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

            SignalingType.CONSENT_REQUEST -> {
                val reqId = payload.consentRequestId?.takeIf { it.isNotBlank() } ?: return
                val kindName = payload.consentKind ?: ConsentRequest.Kind.ALL.name
                val kind = try {
                    ConsentRequest.Kind.valueOf(kindName.uppercase())
                } catch (_: Exception) {
                    ConsentRequest.Kind.ALL
                }
                if (!handledConsentRequestIds.add(reqId)) {
                    return
                }
                if (handledConsentRequestIds.size > 64) {
                    handledConsentRequestIds.clear()
                    handledConsentRequestIds.add(reqId)
                }

                applicationScope.launch(Dispatchers.Main) {
                    val deferred = ConsentBus.request(reqId)
                    val peerDisplayName = payload.senderName.ifBlank { "Family Member" }
                    ConsentBus.events.tryEmit(
                        ConsentRequest(
                            requestId = reqId,
                            kind = kind,
                            peerId = payload.senderId,
                            peerName = peerDisplayName
                        )
                    )
                    bringMainActivityToFrontForConsent(peerDisplayName)

                    val granted = withTimeoutOrNull(60_000L) { deferred.await() } ?: false
                    if (granted) {
                        userPreferencesManager.setRemotePeerAuthorized(payload.senderId, true)
                    }
                    fcmSignalingClient.sendConsentResponse(
                        targetId = payload.senderId,
                        requestId = reqId,
                        kind = kind.name,
                        granted = granted,
                        rootUri = if (granted) userPreferencesManager.getSharedFolderUri() else null
                    )
                }
            }

            SignalingType.CONSENT_RESPONSE -> {
                val granted = payload.consentGranted == true
                userPreferencesManager.setPeerConsentGranted(payload.senderId, granted)
                val peerName = payload.senderName.ifBlank { "Peer" }
                val msg = if (granted) {
                    "$peerName granted access permissions"
                } else {
                    "$peerName denied access permission"
                }
                applicationScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            }
        } catch (e: Throwable) {
            Log.e(tag, "Error handling incoming $source payload: ${e.message}", e)
        }
    }

    fun onAppForegroundChanged(inForeground: Boolean) {
        if (userPreferencesManager.getConnectionMode() == ConnectionMode.WIFI) {
            if (inForeground) {
                lanSocketServer.invalidateCachedIp()
                lanDiscoveryManager.onAppForegrounded()
            } else {
                lanDiscoveryManager.onAppBackgrounded()
            }
        }
    }

    fun setConnectionMode(mode: ConnectionMode) {
        userPreferencesManager.setConnectionMode(mode)
        ensureListenersActive()
    }

    fun ensureListenersActive() {
        val mode = userPreferencesManager.getConnectionMode()
        val activeCall = callRepository.currentCall.value
        val hasOngoingCall = activeCall != null && activeCall.state in listOf(
            CallState.RINGING_INCOMING,
            CallState.RINGING_OUTGOING,
            CallState.CONNECTING,
            CallState.CONNECTED
        )

        if (mode == ConnectionMode.WIFI) {
            try {
                if (!lanSocketServer.isRunning) {
                    lanSocketServer.start(52220)
                }
                if (!lanMediaServer.isRunning) {
                    lanMediaServer.start(LanMediaServer.DEFAULT_MEDIA_PORT)
                }
                lanDiscoveryManager.startDiscoveryAndRegistration()
            } catch (e: Exception) {
                Log.w(tag, "LAN listener start error: ${e.message}")
            }
            if (!hasOngoingCall || activeCall?.isBluetoothVoice == false) {
                try {
                    bluetoothTransportManager.stopServer()
                    bluetoothVoiceManager.stop()
                } catch (e: Exception) {
                    Log.w(tag, "Bluetooth listener stop error: ${e.message}")
                }
            }
        } else {
            val wasBtServerRunning = bluetoothTransportManager.isRunning
            try {
                if (!wasBtServerRunning) {
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
            if (!wasBtServerRunning) {
                applicationScope.launch(Dispatchers.IO) {
                    bluetoothTransportManager.syncPairedDevicesToContacts()
                    bluetoothTransportManager.discoverPairedPokiePeers(currentUserId, currentUserName)
                }
            }
            if (!hasOngoingCall || activeCall?.isBluetoothVoice == true) {
                try {
                    lanSocketServer.stop()
                    lanMediaServer.stop()
                    lanDiscoveryManager.stop()
                } catch (e: Exception) {
                    Log.w(tag, "LAN listener stop error: ${e.message}")
                }
            }
        }
    }

    private suspend fun syncAvatarIfNeeded(
        peerId: String,
        remoteAvatarColor: Long?,
        remoteAvatarHash: String?
    ) {
        if (peerId.isBlank() || peerId == currentUserId) return
        val existing = contactRepository.getContact(peerId) ?: return
        if (remoteAvatarColor != null && remoteAvatarColor != existing.avatarColor) {
            contactRepository.updateAvatarColor(peerId, remoteAvatarColor)
        }
        if (remoteAvatarHash == null) return

        if (remoteAvatarHash.isEmpty()) {
            if (!existing.profileImagePath.isNullOrBlank() || existing.avatarHash.isNotEmpty()) {
                MediaStorageHelper.deletePeerAvatarFiles(context, peerId)
                contactRepository.updateAvatarInfo(
                    id = peerId,
                    avatarColor = remoteAvatarColor ?: existing.avatarColor,
                    profileImagePath = null,
                    avatarHash = ""
                )
            }
            return
        }

        val currentFileExists = !existing.profileImagePath.isNullOrBlank() && java.io.File(existing.profileImagePath).exists()
        if (remoteAvatarHash != existing.avatarHash || !currentFileExists) {
            if (inFlightAvatarRequests.add(peerId)) {
                fcmSignalingClient.sendAvatarRequest(peerId)
            }
        }
    }

    suspend fun sendMyAvatarToPeer(peerId: String) {
        val path = userPreferencesManager.getProfileImagePath() ?: return
        val file = java.io.File(path)
        if (!file.exists() || !file.isFile) return
        val hash = userPreferencesManager.getProfileImageHash()
        val color = userPreferencesManager.getAvatarColor()
        val mode = userPreferencesManager.getConnectionMode()

        if (mode == ConnectionMode.WIFI) {
            val contact = contactRepository.getContact(peerId)
            if (contact != null && contact.isOnlineLan && contact.ipAddress.isNotBlank()) {
                val header = MediaTransferHeader(
                    kind = MediaTransferKind.AVATAR,
                    senderId = currentUserId,
                    senderName = currentUserName,
                    fileName = file.name,
                    mimeType = "image/webp",
                    fileSize = file.length(),
                    avatarHash = hash,
                    avatarColor = color
                )
                lanMediaServer.sendFile(contact.ipAddress, LanMediaServer.DEFAULT_MEDIA_PORT, header, file)
            }
        } else {
            if (file.length() <= MediaStorageHelper.MAX_BT_AVATAR_BYTES) {
                val bytes = file.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                fcmSignalingClient.sendAvatarData(peerId, hash, base64)
            }
        }
    }

    suspend fun sendMediaToPeer(
        peerId: String,
        messageId: String,
        prepared: PreparedMediaFile
    ): Boolean {
        val contact = contactRepository.getContact(peerId) ?: return false
        if (!contact.isOnlineLan || contact.ipAddress.isBlank()) return false
        val header = MediaTransferHeader(
            kind = MediaTransferKind.MEDIA,
            senderId = currentUserId,
            senderName = currentUserName,
            messageId = messageId,
            messageType = prepared.messageType,
            fileName = prepared.fileName,
            mimeType = prepared.mimeType,
            fileSize = prepared.fileSize
        )
        return lanMediaServer.sendFile(
            ip = contact.ipAddress,
            port = LanMediaServer.DEFAULT_MEDIA_PORT,
            header = header,
            file = prepared.file
        )
    }

    fun startScreenShare(mediaProjectionPermissionData: Intent) {
        val session = callRepository.currentCall.value ?: return
        if (session.isBluetoothVoice) {
            callRepository.emitMessage("Screen sharing requires Wi-Fi LAN connection")
            return
        }
        applicationScope.launch(Dispatchers.Main) {
            PokieLocalService.notifyScreenShareChanged(context, active = true)
            withTimeoutOrNull(1500L) {
                PokieLocalService.isMediaProjectionForegroundActive.first { it }
            }
            var neededRenegotiation = false
            val started = webRtcManager.startScreenShare(
                mediaProjectionPermissionData = mediaProjectionPermissionData,
                onNeedRenegotiation = { neededRenegotiation = true },
                onSystemStopped = { stopScreenShare() }
            )
            if (started) {
                if (neededRenegotiation) {
                    webRtcManager.createRenegotiationOffer { sdp ->
                        fcmSignalingClient.sendRenegotiationOffer(
                            targetId = session.peerId,
                            callId = session.callId,
                            sdp = sdp,
                            isScreenShare = true
                        )
                    }
                }
                fcmSignalingClient.sendCameraState(
                    targetId = session.peerId,
                    callId = session.callId,
                    isCameraOn = true,
                    isScreenShare = true
                )
            } else {
                PokieLocalService.notifyScreenShareChanged(context, active = false)
                callRepository.emitMessage("Could not start screen sharing")
            }
        }
    }

    fun stopScreenShare() {
        webRtcManager.stopScreenShare()
        PokieLocalService.notifyScreenShareChanged(context, active = false)
        val session = callRepository.currentCall.value ?: return
        if (!session.isBluetoothVoice) {
            val hasActiveCamera = webRtcManager.localVideoTrackFlow.value != null && webRtcManager.isVideoEnabled.value
            fcmSignalingClient.sendCameraState(
                targetId = session.peerId,
                callId = session.callId,
                isCameraOn = hasActiveCamera,
                isScreenShare = false
            )
        }
    }

    fun requestPeerConsent(peerId: String, kind: ConsentRequest.Kind = ConsentRequest.Kind.ALL): String {
        val requestId = UUID.randomUUID().toString()
        fcmSignalingClient.sendConsentRequest(peerId, requestId, kind.name)
        return requestId
    }

    private fun bringMainActivityToFrontForConsent(peerName: String) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            if (com.pokerlanka.pokie_talkie.PokieApp.isAppInForeground) {
                context.startActivity(intent)
                return
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val pendingIntent = PendingIntent.getActivity(
                context,
                53100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Permission Request")
                .setContentText("$peerName is requesting remote access permissions")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            nm.notify(53100, notification)
        } catch (e: Exception) {
            Log.w(tag, "Could not surface consent prompt: ${e.message}")
        }
    }

    fun resetAudioModeToNormal() {
        try {
            ringtoneVibrationManager.stopAll()
            proximityLock.release()
            webRtcManager.audioDeviceManager.resetToNormal()
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
                notificationManager.deleteNotificationChannel("pokie_incoming_calls_v2")
                notificationManager.deleteNotificationChannel("pokie_incoming_calls_v3")
                notificationManager.deleteNotificationChannel("pokie_chat_messages")
            } catch (e: Exception) {
                Log.w(tag, "Failed to delete old notification channels: ${e.message}")
            }

            // Incoming Calls Channel (Silent channel - ringtoneVibrationManager plays the looping ringtone & vibration per prefs)
            val callsChannel = NotificationChannel(
                TelecomCallManager.CHANNEL_CALLS_ID,
                context.getString(R.string.channel_calls_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_calls_desc)
                setSound(null, null)
                enableVibration(false)
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
