package com.pokerlanka.pokie_talkie.fcm

import android.util.Log
import com.google.gson.Gson
import com.pokerlanka.pokie_talkie.data.local.ConnectionMode
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
import com.pokerlanka.pokie_talkie.lan.LanDiscoveryManager
import com.pokerlanka.pokie_talkie.bluetooth.BluetoothTransportManager

data class DispatchResult(val success: Boolean, val isLan: Boolean)

class FcmSignalingClient(
    private val contactRepository: ContactRepository,
    private val lanSocketServer: LanSocketServer,
    val bluetoothTransportManager: BluetoothTransportManager,
    private val userPreferencesManager: UserPreferencesManager,
    private val scope: CoroutineScope,
    private val myUserId: String,
    private val userNameProvider: () -> String = { userPreferencesManager.getUserName() },
    var fcmV1Client: FcmV1Client? = null,
    private val gson: Gson = Gson()
) {
    private val myUserName: String get() = userNameProvider()
    private val tag = "FcmSignalingClient"
    var onCallFallbackToBluetooth: ((callId: String) -> Unit)? = null
    var lanDiscoveryManager: LanDiscoveryManager? = null

    private fun createPayload(
        type: SignalingType,
        targetId: String,
        callId: String? = null,
        callType: CallType? = null,
        sdp: String? = null,
        iceCandidate: IceCandidateModel? = null,
        messageId: String? = null,
        messageIds: List<String>? = null,
        messageContent: String? = null,
        messageType: MessageType? = null,
        isCameraOn: Boolean? = null,
        isScreenShare: Boolean? = null,
        isBluetoothVoice: Boolean = false,
        includeProfileMeta: Boolean = false,
        avatarBase64: String? = null
    ): SignalingPayload {
        val fcmToken = userPreferencesManager.getFcmToken().takeIf { it.isNotBlank() }
        val localIp = lanSocketServer.getLocalIpAddress()
        return SignalingPayload(
            type = type,
            senderId = myUserId,
            senderName = myUserName,
            targetId = targetId,
            callId = callId,
            callType = callType,
            sdp = sdp,
            iceCandidate = iceCandidate,
            messageId = messageId,
            messageIds = messageIds,
            messageContent = messageContent,
            messageType = messageType,
            isCameraOn = isCameraOn,
            isScreenShare = isScreenShare,
            isBluetoothVoice = isBluetoothVoice,
            senderFcmToken = fcmToken,
            senderIp = localIp,
            senderPort = lanSocketServer.serverPort,
            senderAvatarColor = if (includeProfileMeta) userPreferencesManager.getAvatarColor() else null,
            senderAvatarHash = if (includeProfileMeta) userPreferencesManager.getProfileImageHash() else null,
            avatarBase64 = avatarBase64
        )
    }

    fun sendCallOffer(targetId: String, callId: String, callType: CallType, sdp: SessionDescription) {
        val payload = createPayload(
            type = SignalingType.OFFER,
            targetId = targetId,
            callId = callId,
            callType = callType,
            sdp = sdp.description
        )
        dispatchPayload(targetId, payload)
    }

    fun sendBluetoothCallOffer(targetId: String, callerName: String, callId: String) {
        val payload = createPayload(
            type = SignalingType.OFFER,
            targetId = targetId,
            callId = callId,
            callType = CallType.VOICE,
            isBluetoothVoice = true
        ).copy(senderName = callerName.ifBlank { myUserName })
        dispatchPayload(targetId, payload)
    }

    fun sendCallAnswer(targetId: String, callId: String, sdp: SessionDescription) {
        val payload = createPayload(
            type = SignalingType.ANSWER,
            targetId = targetId,
            callId = callId,
            sdp = sdp.description
        )
        dispatchPayload(targetId, payload)
    }

    fun sendBluetoothCallAnswer(targetId: String, callId: String) {
        val payload = createPayload(
            type = SignalingType.ANSWER,
            targetId = targetId,
            callId = callId,
            callType = CallType.VOICE,
            isBluetoothVoice = true
        )
        dispatchPayload(targetId, payload)
    }

    fun sendIceCandidate(targetId: String, callId: String, candidate: IceCandidateModel) {
        val payload = createPayload(
            type = SignalingType.ICE_CANDIDATE,
            targetId = targetId,
            callId = callId,
            iceCandidate = candidate
        )
        dispatchPayload(targetId, payload)
    }

    fun sendHangup(targetId: String, callId: String) {
        val payload = createPayload(
            type = SignalingType.HANGUP,
            targetId = targetId,
            callId = callId
        )
        dispatchPayload(targetId, payload)
    }

    fun sendChatMessage(targetId: String, content: String, type: MessageType, messageId: String? = null) {
        val payload = createPayload(
            type = SignalingType.CHAT_MESSAGE,
            targetId = targetId,
            messageId = messageId,
            messageContent = content,
            messageType = type
        )
        dispatchPayload(targetId, payload)
    }

    fun sendDeliveryReceipt(targetId: String, messageId: String) {
        val payload = createPayload(
            type = SignalingType.DELIVERY_RECEIPT,
            targetId = targetId,
            messageId = messageId
        )
        dispatchPayload(targetId, payload)
    }

    fun sendReadReceipt(targetId: String, messageIds: List<String>) {
        val validIds = messageIds.filter { it.isNotBlank() }
        if (validIds.isEmpty()) return
        val payload = createPayload(
            type = SignalingType.READ_RECEIPT,
            targetId = targetId,
            messageId = validIds.firstOrNull(),
            messageIds = validIds
        )
        dispatchPayload(targetId, payload)
    }

    fun sendSwitchToVideoRequest(targetId: String, callId: String) {
        val payload = createPayload(
            type = SignalingType.SWITCH_TO_VIDEO_REQUEST,
            targetId = targetId,
            callId = callId,
            callType = CallType.VIDEO
        )
        dispatchPayload(targetId, payload)
    }

    fun sendSwitchToVideoAccept(targetId: String, callId: String) {
        val payload = createPayload(
            type = SignalingType.SWITCH_TO_VIDEO_ACCEPT,
            targetId = targetId,
            callId = callId,
            callType = CallType.VIDEO
        )
        dispatchPayload(targetId, payload)
    }

    fun sendSwitchToVideoDecline(targetId: String, callId: String) {
        val payload = createPayload(
            type = SignalingType.SWITCH_TO_VIDEO_DECLINE,
            targetId = targetId,
            callId = callId
        )
        dispatchPayload(targetId, payload)
    }

    fun sendCameraState(
        targetId: String,
        callId: String,
        isCameraOn: Boolean,
        isScreenShare: Boolean? = null
    ) {
        val payload = createPayload(
            type = SignalingType.CAMERA_STATE,
            targetId = targetId,
            callId = callId,
            isCameraOn = isCameraOn,
            isScreenShare = isScreenShare
        )
        dispatchPayload(targetId, payload)
    }

    fun sendRenegotiationOffer(
        targetId: String,
        callId: String,
        sdp: SessionDescription,
        isScreenShare: Boolean = false
    ) {
        val payload = createPayload(
            type = SignalingType.RENEGOTIATION_OFFER,
            targetId = targetId,
            callId = callId,
            callType = CallType.VIDEO,
            sdp = sdp.description,
            isScreenShare = isScreenShare
        )
        dispatchPayload(targetId, payload)
    }

    fun sendRenegotiationAnswer(targetId: String, callId: String, sdp: SessionDescription) {
        val payload = createPayload(
            type = SignalingType.RENEGOTIATION_ANSWER,
            targetId = targetId,
            callId = callId,
            callType = CallType.VIDEO,
            sdp = sdp.description
        )
        dispatchPayload(targetId, payload)
    }

    fun sendPing(targetId: String) {
        val payload = createPayload(
            type = SignalingType.PING,
            targetId = targetId,
            includeProfileMeta = true
        )
        dispatchPayload(targetId, payload)
    }

    fun sendPong(targetId: String) {
        val payload = createPayload(
            type = SignalingType.PONG,
            targetId = targetId,
            includeProfileMeta = true
        )
        dispatchPayload(targetId, payload)
    }

    fun sendAvatarRequest(targetId: String) {
        val payload = createPayload(
            type = SignalingType.AVATAR_REQUEST,
            targetId = targetId,
            includeProfileMeta = true
        )
        dispatchPayload(targetId, payload)
    }

    fun sendAvatarData(targetId: String, avatarHash: String, avatarBase64: String) {
        val payload = createPayload(
            type = SignalingType.AVATAR_DATA,
            targetId = targetId,
            includeProfileMeta = true,
            avatarBase64 = avatarBase64
        ).copy(senderAvatarHash = avatarHash)
        dispatchPayload(targetId, payload)
    }

    fun sendMessageEdit(targetId: String, messageId: String, newContent: String) {
        val payload = createPayload(
            type = SignalingType.MESSAGE_EDIT,
            targetId = targetId,
            messageId = messageId,
            messageContent = newContent
        )
        dispatchPayload(targetId, payload)
    }

    fun sendMessageUnsend(targetId: String, messageId: String) {
        val payload = createPayload(
            type = SignalingType.MESSAGE_UNSEND,
            targetId = targetId,
            messageId = messageId
        )
        dispatchPayload(targetId, payload)
    }

    suspend fun sendChatMessageSync(
        targetId: String,
        content: String,
        type: MessageType,
        messageId: String? = null
    ): DispatchResult {
        val payload = createPayload(
            type = SignalingType.CHAT_MESSAGE,
            targetId = targetId,
            messageId = messageId,
            messageContent = content,
            messageType = type
        )
        return dispatchPayloadSync(targetId, payload)
    }

    fun broadcastProfileUpdate(newName: String, avatarColor: Long, avatarHash: String) {
        val payload = createPayload(
            type = SignalingType.PROFILE_UPDATE,
            targetId = "",
            includeProfileMeta = true
        ).copy(
            senderName = newName,
            senderAvatarColor = avatarColor,
            senderAvatarHash = avatarHash
        )
        scope.launch(Dispatchers.IO) {
            val contacts = contactRepository.getContacts().firstOrNull() ?: emptyList()
            for (c in contacts) {
                val isMacKeyed = c.id.contains(":")
                val isLanKeyed = c.id.startsWith("lan_")
                if (c.id != myUserId && !isMacKeyed && !isLanKeyed) {
                    dispatchPayload(c.id, payload.copy(targetId = c.id))
                }
            }
        }
    }

    suspend fun dispatchPayloadSync(targetId: String, payload: SignalingPayload): DispatchResult {
        return withContext(Dispatchers.IO) {
            val connectionMode = userPreferencesManager.getConnectionMode()
            var contact = contactRepository.getContact(targetId)

            // If contact is missing or has blank fcmToken (e.g. manual lan_ contact), check if another record has this IP and an FCM token
            if (contact == null || (contact.fcmToken.isBlank() && contact.ipAddress.isNotBlank())) {
                val allContacts = contactRepository.getContacts().firstOrNull() ?: emptyList()
                val targetIp = contact?.ipAddress ?: if (targetId.startsWith("lan_")) targetId.removePrefix("lan_").replace("_", ".") else ""
                val match = if (targetIp.isBlank()) null else allContacts.find { it.ipAddress == targetIp && it.isOnlineLan && it.fcmToken.isNotBlank() }
                if (match != null) {
                    Log.i(tag, "Mapped target $targetId to contact with FCM token: ${match.name} (${match.id})")
                    contact = match
                }
            }

            if (connectionMode == ConnectionMode.WIFI) {
                // Priority 1: Direct Local LAN Wi-Fi Socket
                if (contact != null && contact.isOnlineLan && contact.ipAddress.isNotBlank()) {
                    Log.d(tag, "Attempting Priority 1 direct LAN dispatch to ${contact.name} at ${contact.ipAddress}:${contact.port}")
                    val lanSuccess = lanSocketServer.sendPayload(contact.ipAddress, contact.port, payload)
                    if (!lanSuccess) {
                        Log.w(tag, "LAN direct socket to ${contact.ipAddress}:${contact.port} failed. Marking contact ${contact.id} as offline on LAN and re-resolving.")
                        contactRepository.updateLanOnlineStatusOnly(contact.id, false)
                        lanDiscoveryManager?.reResolvePeer(contact.id)
                    } else {
                        return@withContext DispatchResult(success = true, isLan = true)
                    }
                }

                // Priority 2: FCM Cloud Push (High priority remote wake-up)
                // Skip FCM push for local PING/PONG messages
                if (payload.type != SignalingType.PING && payload.type != SignalingType.PONG) {
                    val fcmToken = contact?.fcmToken
                    if (!fcmToken.isNullOrBlank() && fcmV1Client?.isConfigured() == true) {
                        Log.d(tag, "Attempting Priority 2 FCM Cloud Push to ${contact?.name} ($targetId) token: ${fcmToken.take(15)}...")
                        val fcmSuccess = fcmV1Client?.sendPush(fcmToken, payload) == true
                        if (fcmSuccess) {
                            Log.i(tag, "FCM Cloud push dispatched successfully to ${contact?.name}")
                            return@withContext DispatchResult(success = true, isLan = false)
                        } else {
                            Log.w(tag, "FCM Cloud push failed to $targetId")
                        }
                    }
                }

                Log.w(tag, "Wi-Fi mode active: skipping Bluetooth fallback for [${payload.type}] to $targetId")
                return@withContext DispatchResult(success = false, isLan = false)
            }

            // ConnectionMode.BLUETOOTH: strictly Bluetooth RFCOMM only
            val canSendViaBluetooth = when (payload.type) {
                SignalingType.ICE_CANDIDATE,
                SignalingType.RENEGOTIATION_OFFER,
                SignalingType.RENEGOTIATION_ANSWER -> false
                else -> true
            }

            if (!canSendViaBluetooth) {
                Log.w(tag, "Bluetooth mode active: [${payload.type}] is not applicable for Bluetooth RFCOMM")
                return@withContext DispatchResult(success = false, isLan = false)
            }

            // Priority 3: Bluetooth RFCOMM Direct Proximity
            Log.d(tag, "Attempting Bluetooth RFCOMM to $targetId (${contact?.name})")
            val outboundPayload = if (payload.type == SignalingType.OFFER && !payload.isBluetoothVoice) {
                Log.i(tag, "Bluetooth mode active: converting offer to Bluetooth Voice call offer for $targetId")
                onCallFallbackToBluetooth?.invoke(payload.callId ?: "")
                payload.copy(isBluetoothVoice = true, sdp = null, callType = CallType.VOICE)
            } else {
                payload
            }
            val btSuccess = bluetoothTransportManager.sendPayload(targetId, outboundPayload)
            if (!btSuccess) {
                Log.w(tag, "Dispatch failed: Peer $targetId unreachable on LAN Wi-Fi, FCM and Bluetooth")
            }
            return@withContext DispatchResult(success = btSuccess, isLan = false)
        }
    }

    private fun dispatchPayload(targetId: String, payload: SignalingPayload) {
        scope.launch(Dispatchers.IO) {
            dispatchPayloadSync(targetId, payload)
        }
    }
}
