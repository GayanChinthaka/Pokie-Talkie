package com.pokerlanka.pokie_talkie.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.pokerlanka.pokie_talkie.MainActivity
import com.pokerlanka.pokie_talkie.PokieApp
import com.pokerlanka.pokie_talkie.R
import com.pokerlanka.pokie_talkie.data.model.CallState
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.MessageStatus
import com.pokerlanka.pokie_talkie.data.model.MessageType
import com.pokerlanka.pokie_talkie.data.model.SignalingPayload
import com.pokerlanka.pokie_talkie.data.model.SignalingType
import java.util.UUID
import com.pokerlanka.pokie_talkie.telecom.PokieConnectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.SessionDescription

class PokieFirebaseMessagingService : FirebaseMessagingService() {

    private val tag = "PokieFCMService"
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val CHANNEL_MESSAGES_ID = "pokie_chat_messages"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(tag, "New FCM Registration Token: $token")
        // Update user token in local repository & LAN discovery info
        val app = application as? PokieApp ?: return
        scope.launch {
            app.appContainer.contactRepository.updateFcmToken(app.appContainer.currentUserId, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(tag, "FCM Message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val jsonPayload = data["payload"]
            if (jsonPayload != null) {
                try {
                    val payload = gson.fromJson(jsonPayload, SignalingPayload::class.java)
                    handleSignalingPayload(payload)
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing FCM payload: ${e.message}")
                }
            } else {
                // Direct key-value payload fallback
                val typeStr = data["type"]
                val senderId = data["senderId"] ?: "unknown"
                val senderName = data["senderName"] ?: "Family Member"
                val callId = data["callId"] ?: ""
                val callTypeStr = data["callType"] ?: CallType.VOICE.name
                val callType = try { CallType.valueOf(callTypeStr) } catch (e: Exception) { CallType.VOICE }
                val sdp = data["sdp"]
                val content = data["content"]

                val type = try {
                    SignalingType.valueOf(typeStr?.uppercase() ?: "PING")
                } catch (e: Exception) {
                    SignalingType.PING
                }

                val payload = SignalingPayload(
                    type = type,
                    senderId = senderId,
                    senderName = senderName,
                    targetId = "",
                    callId = callId,
                    callType = callType,
                    sdp = sdp,
                    messageContent = content
                )
                handleSignalingPayload(payload)
            }
        }
    }

    private fun handleSignalingPayload(payload: SignalingPayload) {
        val app = application as? PokieApp ?: return
        val container = app.appContainer

        when (payload.type) {
            SignalingType.OFFER -> {
                Log.i(tag, "Incoming CALL OFFER from ${payload.senderName} (${payload.senderId})")
                val callType = payload.callType ?: CallType.VOICE
                val callId = payload.callId ?: System.currentTimeMillis().toString()

                // Store pending remote SDP offer for when the user answers
                container.webRtcManager.pendingRemoteOfferSdp = payload.sdp

                // Trigger Android TelecomManager / ConnectionService native incoming call
                container.telecomCallManager.handleIncomingCall(
                    callerId = payload.senderId,
                    callerName = payload.senderName,
                    callType = callType,
                    callId = callId
                )
            }

            SignalingType.ANSWER -> {
                Log.i(tag, "Received CALL ANSWER from ${payload.senderName}")
                payload.sdp?.let { sdpString ->
                    container.webRtcManager.setRemoteDescription(sdpString, SessionDescription.Type.ANSWER)
                }
                PokieConnectionService.activeConnection?.markConnected()
                container.callRepository.updateCallState(CallState.CONNECTED)
            }

            SignalingType.ICE_CANDIDATE -> {
                payload.iceCandidate?.let { candidateModel ->
                    container.webRtcManager.addRemoteIceCandidate(candidateModel)
                }
            }

            SignalingType.HANGUP -> {
                Log.i(tag, "Received CALL HANGUP from ${payload.senderName}")
                container.webRtcManager.endCall()
                PokieConnectionService.activeConnection?.markDisconnected()
                container.telecomCallManager.dismissCallNotification()
                scope.launch {
                    container.callRepository.endCallAndLog()
                }
            }

            SignalingType.CHAT_MESSAGE -> {
                Log.i(tag, "Received CHAT MESSAGE from ${payload.senderName}")
                val content = payload.messageContent ?: return
                val msgType = payload.messageType ?: MessageType.TEXT
                val messageId = payload.messageId ?: UUID.randomUUID().toString()
                val isChatCurrentlyOpen = container.activeChatPeerId == payload.senderId
                val initialStatus = if (isChatCurrentlyOpen) MessageStatus.READ else MessageStatus.DELIVERED

                scope.launch {
                    container.chatRepository.saveMessage(
                        id = messageId,
                        senderId = payload.senderId,
                        receiverId = container.currentUserId,
                        content = content,
                        type = msgType,
                        isFromMe = false,
                        status = initialStatus
                    )

                    container.fcmSignalingClient.sendDeliveryReceipt(payload.senderId, messageId)
                    if (isChatCurrentlyOpen) {
                        container.fcmSignalingClient.sendReadReceipt(payload.senderId, listOf(messageId))
                    }
                }
                if (!isChatCurrentlyOpen) {
                    showChatMessageNotification(payload.senderName, content)
                }
            }

            SignalingType.DELIVERY_RECEIPT -> {
                val msgId = payload.messageId ?: return
                scope.launch {
                    container.chatRepository.updateMessageStatusIfNotRead(msgId, MessageStatus.DELIVERED)
                }
            }

            SignalingType.READ_RECEIPT -> {
                val ids = payload.messageIds ?: listOfNotNull(payload.messageId)
                if (ids.isNotEmpty()) {
                    scope.launch {
                        container.chatRepository.updateMessagesStatus(ids, MessageStatus.READ)
                    }
                }
            }

            SignalingType.PROFILE_UPDATE -> {
                Log.d(tag, "Received PROFILE_UPDATE from ${payload.senderId}: '${payload.senderName}'")
                if (payload.senderName.isNotBlank()) {
                    scope.launch {
                        container.contactRepository.updateContactName(payload.senderId, payload.senderName)
                    }
                }
            }

            SignalingType.PING -> {
                Log.d(tag, "Received PING payload from ${payload.senderName}")
            }
        }
    }

    private fun showChatMessageNotification(senderName: String, messageText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            201,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
