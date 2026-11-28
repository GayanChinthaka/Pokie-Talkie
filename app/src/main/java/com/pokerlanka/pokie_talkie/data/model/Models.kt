package com.pokerlanka.pokie_talkie.data.model

import com.google.gson.annotations.SerializedName

enum class CallType {
    VOICE,
    VIDEO
}

enum class CallState {
    IDLE,
    RINGING_INCOMING,
    RINGING_OUTGOING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    REJECTED,
    MISSED
}

enum class MessageType {
    TEXT,
    STICKER,
    IMAGE,
    GIF,
    FILE,
    SYSTEM
}

enum class MessageStatus {
    SENDING,
    SENT_LAN,
    SENT_FCM,
    DELIVERED,
    READ,
    FAILED
}

enum class SignalingType {
    @SerializedName("offer")
    OFFER,
    @SerializedName("answer")
    ANSWER,
    @SerializedName("ice_candidate")
    ICE_CANDIDATE,
    @SerializedName("hangup")
    HANGUP,
    @SerializedName("chat_message")
    CHAT_MESSAGE,
    @SerializedName("delivery_receipt")
    DELIVERY_RECEIPT,
    @SerializedName("read_receipt")
    READ_RECEIPT,
    @SerializedName("profile_update")
    PROFILE_UPDATE,
    @SerializedName("avatar_request")
    AVATAR_REQUEST,
    @SerializedName("avatar_data")
    AVATAR_DATA,
    @SerializedName("message_edit")
    MESSAGE_EDIT,
    @SerializedName("message_unsend")
    MESSAGE_UNSEND,
    @SerializedName("switch_to_video_request")
    SWITCH_TO_VIDEO_REQUEST,
    @SerializedName("switch_to_video_accept")
    SWITCH_TO_VIDEO_ACCEPT,
    @SerializedName("switch_to_video_decline")
    SWITCH_TO_VIDEO_DECLINE,
    @SerializedName("camera_state")
    CAMERA_STATE,
    @SerializedName("renegotiation_offer")
    RENEGOTIATION_OFFER,
    @SerializedName("renegotiation_answer")
    RENEGOTIATION_ANSWER,
    @SerializedName("ping")
    PING,
    @SerializedName("pong")
    PONG,
    @SerializedName("consent_request")
    CONSENT_REQUEST,
    @SerializedName("consent_response")
    CONSENT_RESPONSE
}

data class Contact(
    val id: String,
    val name: String,
    val fcmToken: String = "",
    val ipAddress: String = "",
    val port: Int = 52220,
    val bluetoothAddress: String = "",
    val isOnlineLan: Boolean = false,
    val avatarColor: Long = 0xFF00A884,
    val profileImagePath: String? = null,
    val avatarHash: String = "",
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

data class Message(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val type: MessageType,
    val status: MessageStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean = false,
    val extraData: String? = null,
    val localFilePath: String? = null,
    val mimeType: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false
)

data class IceCandidateModel(
    @SerializedName("sdpMid")
    val sdpMid: String?,
    @SerializedName("sdpMLineIndex")
    val sdpMLineIndex: Int,
    @SerializedName("candidate")
    val candidate: String
)

data class SignalingPayload(
    @SerializedName("type")
    val type: SignalingType,
    @SerializedName("senderId")
    val senderId: String,
    @SerializedName("senderName")
    val senderName: String,
    @SerializedName("targetId")
    val targetId: String,
    @SerializedName("callId")
    val callId: String? = null,
    @SerializedName("callType")
    val callType: CallType? = null,
    @SerializedName("sdp")
    val sdp: String? = null,
    @SerializedName("iceCandidate")
    val iceCandidate: IceCandidateModel? = null,
    @SerializedName("messageId")
    val messageId: String? = null,
    @SerializedName("messageIds")
    val messageIds: List<String>? = null,
    @SerializedName("messageContent")
    val messageContent: String? = null,
    @SerializedName("messageType")
    val messageType: MessageType? = null,
    @SerializedName("isBluetoothVoice")
    val isBluetoothVoice: Boolean = false,
    @SerializedName("isCameraOn")
    val isCameraOn: Boolean? = null,
    @SerializedName("isScreenShare")
    val isScreenShare: Boolean? = null,
    @SerializedName("senderFcmToken")
    val senderFcmToken: String? = null,
    @SerializedName("senderIp")
    val senderIp: String? = null,
    @SerializedName("senderPort")
    val senderPort: Int? = null,
    @SerializedName("senderAvatarColor")
    val senderAvatarColor: Long? = null,
    @SerializedName("senderAvatarHash")
    val senderAvatarHash: String? = null,
    @SerializedName("avatarBase64")
    val avatarBase64: String? = null,
    @SerializedName("fileName")
    val fileName: String? = null,
    @SerializedName("mimeType")
    val mimeType: String? = null,
    @SerializedName("fileSize")
    val fileSize: Long? = null,
    @SerializedName("consentRequestId")
    val consentRequestId: String? = null,
    @SerializedName("consentKind")
    val consentKind: String? = null,
    @SerializedName("consentGranted")
    val consentGranted: Boolean? = null,
    @SerializedName("consentRootUri")
    val consentRootUri: String? = null,
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    @Transient
    var sourceIp: String? = null
}

fun SignalingPayload.isValid(): Boolean {
    @Suppress("USELESS_CAST")
    val t = type as SignalingType?
    @Suppress("USELESS_CAST")
    val sId = senderId as String?
    @Suppress("USELESS_CAST")
    val tId = targetId as String?
    return t != null && !sId.isNullOrBlank() && tId != null
}

data class CallSession(
    val callId: String,
    val peerId: String,
    val peerName: String,
    val callType: CallType,
    val isIncoming: Boolean,
    val state: CallState = CallState.IDLE,
    val durationSeconds: Long = 0L,
    val isBluetoothVoice: Boolean = false,
    val startTimestamp: Long = System.currentTimeMillis()
)
