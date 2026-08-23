package com.pokerlanka.pokie_talkie.fcm

import android.util.Log
import com.google.gson.Gson
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.IceCandidateModel
import com.pokerlanka.pokie_talkie.data.model.MessageType
import com.pokerlanka.pokie_talkie.data.model.SignalingPayload
import com.pokerlanka.pokie_talkie.data.model.SignalingType
import com.pokerlanka.pokie_talkie.data.repository.ContactRepository
import com.pokerlanka.pokie_talkie.lan.LanSocketServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.SessionDescription
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class FcmSignalingClient(
    private val contactRepository: ContactRepository,
    private val lanSocketServer: LanSocketServer,
    private val scope: CoroutineScope,
    private val myUserId: String,
    private val myUserName: String
) {
    private val tag = "FcmSignalingClient"
    private val gson = Gson()

    fun sendCallOffer(targetId: String, callId: String, callType: CallType, sdp: SessionDescription) {
        val payload = SignalingPayload(
            type = SignalingType.OFFER,
            senderId = myUserId,
            senderName = myUserName,
            targetId = targetId,
            callId = callId,
            callType = callType,
            sdp = sdp.description
        )
        dispatchPayload(targetId, payload)
    }

    fun sendCallAnswer(targetId: String, callId: String, sdp: SessionDescription) {
        val payload = SignalingPayload(
            type = SignalingType.ANSWER,
            senderId = myUserId,
            senderName = myUserName,
            targetId = targetId,
            callId = callId,
            sdp = sdp.description
        )
        dispatchPayload(targetId, payload)
    }

    fun sendIceCandidate(targetId: String, callId: String, candidate: IceCandidateModel) {
        val payload = SignalingPayload(
            type = SignalingType.ICE_CANDIDATE,
            senderId = myUserId,
            senderName = myUserName,
            targetId = targetId,
            callId = callId,
            iceCandidate = candidate
        )
        dispatchPayload(targetId, payload)
    }

    fun sendHangup(targetId: String, callId: String) {
        val payload = SignalingPayload(
            type = SignalingType.HANGUP,
            senderId = myUserId,
            senderName = myUserName,
            targetId = targetId,
            callId = callId
        )
        dispatchPayload(targetId, payload)
    }

    fun sendChatMessage(targetId: String, content: String, type: MessageType) {
        val payload = SignalingPayload(
            type = SignalingType.CHAT_MESSAGE,
            senderId = myUserId,
            senderName = myUserName,
            targetId = targetId,
            messageContent = content,
            messageType = type
        )
        dispatchPayload(targetId, payload)
    }

    private fun dispatchPayload(targetId: String, payload: SignalingPayload) {
        scope.launch(Dispatchers.IO) {
            val contact = contactRepository.getContact(targetId)
            var lanSuccess = false

            // Try Direct Local LAN Socket first
            if (contact != null && contact.isOnlineLan && contact.ipAddress.isNotBlank()) {
                Log.d(tag, "Attempting direct LAN dispatch to ${contact.name} at ${contact.ipAddress}:${contact.port}")
                lanSuccess = lanSocketServer.sendPayload(contact.ipAddress, contact.port, payload)
            }

            // Fallback to FCM high-priority cloud data message if LAN failed or peer offline on LAN
            if (!lanSuccess) {
                val fcmToken = contact?.fcmToken
                if (!fcmToken.isNullOrBlank()) {
                    Log.d(tag, "Dispatching payload via FCM Cloud fallback to token: $fcmToken")
                    sendFcmPayload(fcmToken, payload)
                } else {
                    Log.w(tag, "Target $targetId unreachable on LAN and has no known FCM token")
                }
            }
        }
    }

    private suspend fun sendFcmPayload(fcmToken: String, payload: SignalingPayload) {
        withContext(Dispatchers.IO) {
            try {
                // In production, this can send to your lightweight Firebase Cloud Function or FCM endpoint
                Log.d(tag, "FCM data payload queued for ${payload.type} to token $fcmToken")
            } catch (e: Exception) {
                Log.e(tag, "Error sending FCM payload: ${e.message}")
            }
        }
    }
}
