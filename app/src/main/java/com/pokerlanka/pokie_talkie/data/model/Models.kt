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
    EMOJI,
    STICKER,
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
    @SerializedName("ping")
    PING
}

data class Contact(
    val id: String,
    val name: String,
    val fcmToken: String = "",
    val ipAddress: String = "",
    val port: Int = 52220,
    val isOnlineLan: Boolean = false,
    val avatarColor: Long = 0xFF00A884,
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
    val extraData: String? = null
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
    @SerializedName("messageContent")
    val messageContent: String? = null,
    @SerializedName("messageType")
    val messageType: MessageType? = null,
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

data class CallSession(
    val callId: String,
    val peerId: String,
    val peerName: String,
    val callType: CallType,
    val isIncoming: Boolean,
    val state: CallState = CallState.IDLE,
    val durationSeconds: Long = 0L,
    val startTimestamp: Long = System.currentTimeMillis()
)

data class Sticker(
    val id: String,
    val emojiRepresentation: String,
    val title: String,
    val packName: String
)
