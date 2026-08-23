package com.pokerlanka.pokie_talkie.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.MessageStatus
import com.pokerlanka.pokie_talkie.data.model.MessageType

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val type: MessageType,
    val status: MessageStatus,
    val timestamp: Long,
    val isFromMe: Boolean,
    val extraData: String? = null
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val fcmToken: String,
    val ipAddress: String,
    val port: Int,
    val isOnlineLan: Boolean,
    val avatarColor: Long,
    val lastSeenTimestamp: Long
)

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey
    val id: String,
    val peerId: String,
    val peerName: String,
    val callType: CallType,
    val isIncoming: Boolean,
    val wasConnected: Boolean,
    val durationSeconds: Long,
    val timestamp: Long
)
