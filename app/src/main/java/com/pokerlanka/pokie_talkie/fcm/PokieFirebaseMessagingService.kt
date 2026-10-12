package com.pokerlanka.pokie_talkie.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.pokerlanka.pokie_talkie.PokieApp
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.SignalingPayload
import com.pokerlanka.pokie_talkie.data.model.SignalingType
import com.pokerlanka.pokie_talkie.data.model.isValid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PokieFirebaseMessagingService : FirebaseMessagingService() {

    private val tag = "PokieFcmService"
    private val gson = Gson()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(tag, "New FCM Registration Token received: $token")
        val app = applicationContext as? PokieApp ?: return
        val container = app.appContainer
        container.userPreferencesManager.setFcmToken(token)
        container.lanDiscoveryManager.updateFcmToken(token)
        serviceScope.launch {
            container.contactRepository.updateFcmToken(container.currentUserId, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(tag, "FCM Message received from: ${remoteMessage.from}")

        val app = applicationContext as? PokieApp ?: return
        val container = app.appContainer

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val jsonPayload = data["payload"]
            val payload = if (!jsonPayload.isNullOrBlank()) {
                try {
                    gson.fromJson(jsonPayload, SignalingPayload::class.java)
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing jsonPayload from FCM: ${e.message}")
                    null
                }
            } else {
                try {
                    val typeStr = data["type"] ?: return
                    val type = try { SignalingType.valueOf(typeStr.uppercase()) } catch (e: Exception) { return }
                    val senderId = data["senderId"] ?: return
                    val senderName = data["senderName"] ?: "Unknown"
                    val callId = data["callId"]
                    val callTypeStr = data["callType"]
                    val callType = callTypeStr?.let { try { CallType.valueOf(it) } catch (e: Exception) { null } }
                    val content = data["content"]
                    val senderFcmToken = data["senderFcmToken"]
                    val senderIp = data["senderIp"]
                    val senderPort = data["senderPort"]?.toIntOrNull()
                    SignalingPayload(
                        type = type,
                        senderId = senderId,
                        senderName = senderName,
                        targetId = container.currentUserId,
                        callId = callId,
                        callType = callType,
                        messageContent = content,
                        senderFcmToken = senderFcmToken,
                        senderIp = senderIp,
                        senderPort = senderPort
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Error building fallback payload from FCM: ${e.message}")
                    null
                }
            }

            val decompressedPayload = if (payload?.sdp != null && payload.sdp.startsWith("gz:")) {
                payload.copy(sdp = SdpCompressor.decompress(payload.sdp))
            } else {
                payload
            }

            if (decompressedPayload != null && decompressedPayload.isValid()) {
                Log.i(tag, "Valid FCM payload received: [${decompressedPayload.type}] from ${decompressedPayload.senderName} (${decompressedPayload.senderId})")
                container.handleIncomingSignalingPayload(decompressedPayload, "FCM")
            } else {
                Log.w(tag, "Invalid or dropped FCM payload: $payload")
            }
        }
    }
}
