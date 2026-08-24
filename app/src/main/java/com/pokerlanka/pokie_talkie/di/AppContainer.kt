package com.pokerlanka.pokie_talkie.di

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
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
import com.pokerlanka.pokie_talkie.data.model.MessageType
import com.pokerlanka.pokie_talkie.data.model.SignalingType
import com.pokerlanka.pokie_talkie.data.repository.CallRepository
import com.pokerlanka.pokie_talkie.data.repository.ChatRepository
import com.pokerlanka.pokie_talkie.data.repository.ContactRepository
import com.pokerlanka.pokie_talkie.fcm.FcmSignalingClient
import com.pokerlanka.pokie_talkie.fcm.PokieFirebaseMessagingService
import com.pokerlanka.pokie_talkie.lan.LanDiscoveryManager
import com.pokerlanka.pokie_talkie.lan.LanSocketServer
import com.pokerlanka.pokie_talkie.telecom.PokieConnectionService
import com.pokerlanka.pokie_talkie.telecom.TelecomCallManager
import com.pokerlanka.pokie_talkie.webrtc.WebRtcManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.webrtc.SessionDescription

import com.google.firebase.messaging.FirebaseMessaging
import com.pokerlanka.pokie_talkie.data.local.UserPreferencesManager
import com.pokerlanka.pokie_talkie.data.model.Contact

class AppContainer(val context: Context) {
    private val tag = "AppContainer"
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

    // Networking & Realtime
    val lanSocketServer by lazy { LanSocketServer(applicationScope) }
    val lanDiscoveryManager by lazy { LanDiscoveryManager(context, contactRepository, applicationScope) }

    val fcmSignalingClient by lazy {
        FcmSignalingClient(
            contactRepository = contactRepository,
            lanSocketServer = lanSocketServer,
            scope = applicationScope,
            myUserId = currentUserId,
            myUserName = currentUserName
        )
    }

    val webRtcManager by lazy { WebRtcManager(context, applicationScope) }
    val telecomCallManager by lazy { TelecomCallManager(context, callRepository, webRtcManager) }

    fun updateMyProfileName(newName: String) {
        userPreferencesManager.setUserName(newName)
        lanDiscoveryManager.updateProfileName(newName)
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
        telecomCallManager.registerPhoneAccount()

        // Start LAN Socket Server on Port 52220
        lanSocketServer.start(52220)

        // Initialize and start NSD / mDNS discovery with real profile info
        val savedFcmToken = userPreferencesManager.getFcmToken()
        lanDiscoveryManager.initialize(currentUserId, currentUserName, lanSocketServer.serverPort, savedFcmToken)
        lanDiscoveryManager.startDiscoveryAndRegistration()

        // Fetch real FCM Token
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    if (!token.isNullOrBlank()) {
                        Log.i(tag, "Real FCM Token retrieved: $token")
                        userPreferencesManager.setFcmToken(token)
                        lanDiscoveryManager.updateFcmToken(token)
                        applicationScope.launch {
                            contactRepository.updateFcmToken(currentUserId, token)
                        }
                    }
                } else {
                    Log.w(tag, "Fetching FCM token failed: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error accessing FirebaseMessaging: ${e.message}")
        }

        // Setup WebRTC Callbacks
        setupWebRtcCallbacks()

        // Listen for direct LAN incoming payloads
        listenForLanPayloads()
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
            applicationScope.launch {
                callRepository.endCallAndLog()
            }
        }
    }

    private fun listenForLanPayloads() {
        applicationScope.launch(Dispatchers.IO) {
            lanSocketServer.incomingPayloads.collect { payload ->
                Log.d(tag, "Handling incoming LAN payload [${payload.type}] from ${payload.senderName}")
                when (payload.type) {
                    SignalingType.OFFER -> {
                        val callType = payload.callType ?: CallType.VOICE
                        val callId = payload.callId ?: System.currentTimeMillis().toString()

                        // Store pending remote SDP offer for when the user answers
                        webRtcManager.pendingRemoteOfferSdp = payload.sdp

                        telecomCallManager.handleIncomingCall(
                            callerId = payload.senderId,
                            callerName = payload.senderName,
                            callType = callType,
                            callId = callId
                        )
                    }

                    SignalingType.ANSWER -> {
                        payload.sdp?.let { sdpString ->
                            webRtcManager.setRemoteDescription(sdpString, SessionDescription.Type.ANSWER)
                        }
                        PokieConnectionService.activeConnection?.markConnected()
                        callRepository.updateCallState(CallState.CONNECTED)
                    }

                    SignalingType.ICE_CANDIDATE -> {
                        payload.iceCandidate?.let { candidate ->
                            webRtcManager.addRemoteIceCandidate(candidate)
                        }
                    }

                    SignalingType.HANGUP -> {
                        webRtcManager.endCall()
                        PokieConnectionService.activeConnection?.markDisconnected()
                        telecomCallManager.dismissCallNotification()
                        callRepository.endCallAndLog()
                    }

                    SignalingType.CHAT_MESSAGE -> {
                        val content = payload.messageContent ?: return@collect
                        val type = payload.messageType ?: MessageType.TEXT
                        chatRepository.saveMessage(
                            senderId = payload.senderId,
                            receiverId = currentUserId,
                            content = content,
                            type = type,
                            isFromMe = false
                        )
                        showChatMessageNotification(payload.senderName, content)
                    }

                    SignalingType.PING -> {}
                }
            }
        }
    }

    fun showChatMessageNotification(senderName: String, messageText: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            201,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, PokieFirebaseMessagingService.CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
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
            val messagesChannel = NotificationChannel(
                PokieFirebaseMessagingService.CHANNEL_MESSAGES_ID,
                context.getString(R.string.channel_messages_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_messages_desc)
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(listOf(callsChannel, messagesChannel))
        }
    }
}
