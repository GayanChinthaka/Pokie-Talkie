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
import kotlinx.coroutines.flow.firstOrNull
import org.webrtc.SessionDescription
import com.pokerlanka.pokie_talkie.bluetooth.BluetoothTransportManager

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

    fun sendChatMessage(targetId: String, content: String, type: MessageType, messageId: String? = null) {
        val payload = SignalingPayload(
            type = SignalingType.CHAT_MESSAGE,
            senderId = myUserId,
            senderName = myUserName,
            targetId = targetId,
            messageId = messageId,
            messageContent = content,
            messageType = type
        )
        dispatchPayload(targetId, payload)
    }

    fun sendDeliveryReceipt(targetId: String, messageId: String) {
        val payload = SignalingPayload(
            type = SignalingType.DELIVERY_RECEIPT,
            senderId = myUserId,
            senderName = myUserName,
            targetId = targetId,
            messageId = messageId
        )
        dispatchPayload(targetId, payload)
    }

    fun sendReadReceipt(targetId: String, messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        val payload = SignalingPayload(
            type = SignalingType.READ_RECEIPT,
            senderId = myUserId,
            senderName = myUserName,
            targetId = targetId,
            messageIds = messageIds
        )
        dispatchPayload(targetId, payload)
    }

    fun broadcastProfileUpdate(newName: String) {
        val payload = SignalingPayload(
            type = SignalingType.PROFILE_UPDATE,
            senderId = myUserId,
            senderName = newName,
            targetId = ""
        )
        scope.launch(Dispatchers.IO) {
            val contacts = contactRepository.getContacts().firstOrNull() ?: emptyList()
            for (c in contacts) {
                if (c.id != myUserId) {
                    dispatchPayload(c.id, payload.copy(targetId = c.id))
                }
            }
        }
    }

    private fun dispatchPayload(targetId: String, payload: SignalingPayload) {
        scope.launch(Dispatchers.IO) {
            val contact = contactRepository.getContact(targetId)
            var lanSuccess = false

            // Priority 1: Direct Local LAN Wi-Fi Socket
            if (contact != null && contact.isOnlineLan && contact.ipAddress.isNotBlank()) {
                Log.d(tag, "Attempting Priority 1 direct LAN dispatch to ${contact.name} at ${contact.ipAddress}:${contact.port}")
                lanSuccess = lanSocketServer.sendPayload(contact.ipAddress, contact.port, payload)
            }

            // Priority 2: Bluetooth RFCOMM Direct Proximity (Zero WiFi router needed)
            var btSuccess = false
            if (!lanSuccess) {
                Log.d(tag, "LAN delivery unavailable or offline. Attempting Priority 2 Bluetooth RFCOMM to $targetId (${contact?.name})")
                btSuccess = bluetoothTransportManager.sendPayload(targetId, payload)
            }

            if (!lanSuccess && !btSuccess) {
                Log.w(tag, "Dispatch failed: Peer $targetId unreachable on LAN Wi-Fi and Bluetooth")
            }
        }
    }
}
