package com.pokerlanka.pokie_talkie.fcm

import android.util.Log
import com.google.gson.Gson
import com.pokerlanka.pokie_talkie.data.local.UserPreferencesManager
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
import org.json.JSONObject
import org.webrtc.SessionDescription
import java.io.OutputStreamWriter
import com.pokerlanka.pokie_talkie.bluetooth.BluetoothTransportManager
import java.net.HttpURLConnection
import java.net.URL

class FcmSignalingClient(
    private val contactRepository: ContactRepository,
    private val lanSocketServer: LanSocketServer,
    private val bluetoothTransportManager: BluetoothTransportManager,
    private val userPreferencesManager: UserPreferencesManager,
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

            // Priority 1: Direct Local LAN Wi-Fi Socket
            if (contact != null && contact.isOnlineLan && contact.ipAddress.isNotBlank()) {
                Log.d(tag, "Attempting direct LAN dispatch to ${contact.name} at ${contact.ipAddress}:${contact.port}")
                lanSuccess = lanSocketServer.sendPayload(contact.ipAddress, contact.port, payload)
            }

            // Priority 2: Bluetooth RFCOMM Direct Proximity Fallback (Zero WiFi router needed)
            var btSuccess = false
            if (!lanSuccess) {
                Log.d(tag, "LAN delivery failed or offline. Attempting Priority 2 Bluetooth RFCOMM to $targetId")
                btSuccess = bluetoothTransportManager.sendPayload(targetId, payload)
            }

            // Priority 3: Cloud / Remote Push Fallback
            if (!lanSuccess && !btSuccess) {
                val fcmToken = contact?.fcmToken
                if (!fcmToken.isNullOrBlank()) {
                    Log.d(tag, "LAN & Bluetooth unavailable. Attempting Priority 3 FCM push to token: $fcmToken")
                    sendFcmPayload(fcmToken, payload)
                } else {
                    Log.w(tag, "Target $targetId unreachable on LAN or Bluetooth and has no known FCM token")
                }
            }
        }
    }

    private suspend fun sendFcmPayload(fcmToken: String, payload: SignalingPayload) {
        withContext(Dispatchers.IO) {
            try {
                val serverKey = userPreferencesManager.getFcmServerKey()
                if (serverKey.isNullOrBlank()) {
                    Log.w(
                        tag,
                        "FCM Server Key is not set in Settings. Please add your Firebase Cloud Messaging Server Key in Settings -> About & Network to enable remote push when the app is closed."
                    )
                    return@withContext
                }

                val url = URL("https://fcm.googleapis.com/fcm/send")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; UTF-8")
                    setRequestProperty("Authorization", "key=$serverKey")
                    doOutput = true
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                val payloadJson = gson.toJson(payload)

                val rootJson = JSONObject().apply {
                    put("to", fcmToken)
                    put("priority", "high")
                    put("content_available", true)
                    put("time_to_live", 120) // 2 minutes

                    val dataObj = JSONObject().apply {
                        put("payload", payloadJson)
                        put("type", payload.type.name)
                        put("senderId", payload.senderId)
                        put("senderName", payload.senderName)
                        put("callId", payload.callId ?: "")
                        put("callType", payload.callType?.name ?: "VOICE")
                        payload.sdp?.let { put("sdp", it) }
                        payload.messageContent?.let { put("content", it) }
                    }
                    put("data", dataObj)
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(rootJson.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    Log.i(tag, "FCM Cloud push dispatched successfully (HTTP $responseCode): $response")
                } else {
                    val errorResponse = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e(tag, "FCM Cloud push failed (HTTP $responseCode): $errorResponse")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error sending FCM payload to $fcmToken: ${e.message}", e)
            }
        }
    }
}
