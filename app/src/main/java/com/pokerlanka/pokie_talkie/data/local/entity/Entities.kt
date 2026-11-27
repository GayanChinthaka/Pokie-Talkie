package com.pokerlanka.pokie_talkie.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
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
    val extraData: String? = null,
    val localFilePath: String? = null,
    val mimeType: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val fcmToken: String,
    val ipAddress: String,
    val port: Int,
    val bluetoothAddress: String = "",
    val isOnlineLan: Boolean,
    val avatarColor: Long,
    val profileImagePath: String? = null,
    val avatarHash: String = "",
    val lastSeenTimestamp: Long
)
