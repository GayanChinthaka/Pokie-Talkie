package com.pokerlanka.pokie_talkie.data.repository

import com.pokerlanka.pokie_talkie.data.local.dao.CallLogDao
import com.pokerlanka.pokie_talkie.data.local.dao.ContactDao
import com.pokerlanka.pokie_talkie.data.local.dao.MessageDao
import com.pokerlanka.pokie_talkie.data.local.entity.CallLogEntity
import com.pokerlanka.pokie_talkie.data.local.entity.ContactEntity
import com.pokerlanka.pokie_talkie.data.local.entity.MessageEntity
import com.pokerlanka.pokie_talkie.data.model.CallSession
import com.pokerlanka.pokie_talkie.data.model.CallState
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.Contact
import com.pokerlanka.pokie_talkie.data.model.Message
import com.pokerlanka.pokie_talkie.data.model.MessageStatus
import com.pokerlanka.pokie_talkie.data.model.MessageType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ChatRepository(
    private val messageDao: MessageDao,
    private val contactDao: ContactDao
) {
    fun getMessages(peerId: String, myId: String): Flow<List<Message>> {
        return messageDao.getMessagesForConversation(peerId, myId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun saveMessage(
        senderId: String,
        receiverId: String,
        content: String,
        type: MessageType,
        isFromMe: Boolean,
        status: MessageStatus = if (isFromMe) MessageStatus.SENDING else MessageStatus.DELIVERED,
        extraData: String? = null,
        id: String = UUID.randomUUID().toString()
    ): Message {
        val message = Message(
            id = id,
            senderId = senderId,
            receiverId = receiverId,
            content = content,
            type = type,
            status = status,
            timestamp = System.currentTimeMillis(),
            isFromMe = isFromMe,
            extraData = extraData
        )
        messageDao.insertMessage(message.toEntity())
        return message
    }

    suspend fun updateMessageStatus(messageId: String, status: MessageStatus) {
        messageDao.updateMessageStatus(messageId, status)
    }

    suspend fun updateMessageStatusIfNotRead(messageId: String, status: MessageStatus) {
        messageDao.updateMessageStatusIfNotRead(messageId, status)
    }

    suspend fun updateMessagesStatus(messageIds: List<String>, status: MessageStatus) {
        if (messageIds.isNotEmpty()) {
            messageDao.updateMessagesStatus(messageIds, status)
        }
    }

    suspend fun markIncomingAsRead(peerId: String, myId: String): List<String> {
        val unreadIds = messageDao.getUnreadIncomingMessageIds(peerId, myId)
        if (unreadIds.isNotEmpty()) {
            messageDao.markIncomingMessagesStatus(peerId, myId, MessageStatus.READ)
        }
        return unreadIds
    }

    fun getUnreadCounts(myId: String): Flow<Map<String, Int>> {
        return messageDao.getUnreadCountsBySender(myId).map { list ->
            list.associate { it.senderId to it.count }
        }
    }

    suspend fun clearChat(peerId: String, myId: String) {
        messageDao.clearConversation(peerId, myId)
    }

    private fun MessageEntity.toDomain() = Message(
        id = id,
        senderId = senderId,
        receiverId = receiverId,
        content = content,
        type = type,
        status = status,
        timestamp = timestamp,
        isFromMe = isFromMe,
        extraData = extraData
    )

    private fun Message.toEntity() = MessageEntity(
        id = id,
        senderId = senderId,
        receiverId = receiverId,
        content = content,
        type = type,
        status = status,
        timestamp = timestamp,
        isFromMe = isFromMe,
        extraData = extraData
    )
}

class ContactRepository(
    private val contactDao: ContactDao
) {
    fun getContacts(): Flow<List<Contact>> {
        return contactDao.getAllContacts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getContactFlow(id: String): Flow<Contact?> {
        return contactDao.getContactFlow(id).map { it?.toDomain() }
    }

    suspend fun getContact(id: String): Contact? {
        return contactDao.getContactById(id)?.toDomain()
    }

    suspend fun updateLanStatus(id: String, isOnline: Boolean, ip: String = "", port: Int = 52220) {
        contactDao.updateLanStatus(id, isOnline, ip, port, System.currentTimeMillis())
    }

    suspend fun resetAllLanStatus() {
        contactDao.resetAllLanStatus()
    }

    suspend fun saveOrUpdateContact(contact: Contact) {
        contactDao.insertOrUpdateContact(contact.toEntity())
    }

    suspend fun updateContactName(id: String, newName: String) {
        contactDao.updateContactName(id, newName)
    }

    suspend fun updateBluetoothAddress(id: String, btAddress: String) {
        contactDao.updateBluetoothAddress(id, btAddress)
    }

    suspend fun updateFcmToken(id: String, token: String) {
        contactDao.updateFcmToken(id, token)
    }

    suspend fun deleteContact(id: String) {
        contactDao.deleteContact(id)
    }

    suspend fun clearAllContacts() {
        contactDao.deleteAllContacts()
    }

    private fun ContactEntity.toDomain() = Contact(
        id = id,
        name = name,
        fcmToken = fcmToken,
        ipAddress = ipAddress,
        port = port,
        bluetoothAddress = bluetoothAddress,
        isOnlineLan = isOnlineLan,
        avatarColor = avatarColor,
        lastSeenTimestamp = lastSeenTimestamp
    )

    private fun Contact.toEntity() = ContactEntity(
        id = id,
        name = name,
        fcmToken = fcmToken,
        ipAddress = ipAddress,
        port = port,
        bluetoothAddress = bluetoothAddress,
        isOnlineLan = isOnlineLan,
        avatarColor = avatarColor,
        lastSeenTimestamp = lastSeenTimestamp
    )
}

class CallRepository(
    private val callLogDao: CallLogDao
) {
    private val _currentCall = MutableStateFlow<CallSession?>(null)
    val currentCall: StateFlow<CallSession?> = _currentCall.asStateFlow()

    fun setCallSession(session: CallSession?) {
        _currentCall.value = session
    }

    fun updateCallState(state: CallState) {
        _currentCall.value = _currentCall.value?.copy(state = state)
    }

    fun updateDuration(duration: Long) {
        _currentCall.value = _currentCall.value?.copy(durationSeconds = duration)
    }

    suspend fun endCallAndLog() {
        val session = _currentCall.value ?: return
        val wasConnected = session.state == CallState.CONNECTED
        val log = CallLogEntity(
            id = session.callId,
            peerId = session.peerId,
            peerName = session.peerName,
            callType = session.callType,
            isIncoming = session.isIncoming,
            wasConnected = wasConnected,
            durationSeconds = session.durationSeconds,
            timestamp = session.startTimestamp
        )
        callLogDao.insertCallLog(log)
        _currentCall.value = session.copy(state = CallState.DISCONNECTED)
    }

    fun clearSession() {
        _currentCall.value = null
    }

    fun getCallLogs(): Flow<List<CallLogEntity>> = callLogDao.getAllCallLogs()
}
