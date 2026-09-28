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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.room.withTransaction
import com.pokerlanka.pokie_talkie.data.local.PokieDatabase
import java.util.UUID

class ChatRepository(
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val database: PokieDatabase? = null
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

    suspend fun updateMessageStatusIfSending(messageId: String, status: MessageStatus) {
        messageDao.updateMessageStatusIfSending(messageId, status)
    }

    suspend fun updateMessagesStatus(messageIds: List<String>, status: MessageStatus) {
        if (messageIds.isNotEmpty()) {
            messageIds.chunked(900).forEach { chunk ->
                messageDao.updateMessagesStatus(chunk, status)
            }
        }
    }

    suspend fun markIncomingAsRead(peerId: String, myId: String): List<String> {
        val execute: suspend () -> List<String> = {
            val unreadIds = messageDao.getUnreadIncomingMessageIds(peerId, myId)
            if (unreadIds.isNotEmpty()) {
                unreadIds.chunked(900).forEach { chunk ->
                    messageDao.updateMessagesStatus(chunk, MessageStatus.READ)
                }
            }
            unreadIds
        }
        return if (database != null) {
            database.withTransaction { execute() }
        } else {
            execute()
        }
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
    private val contactDao: ContactDao,
    private val database: PokieDatabase? = null
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

    suspend fun updateLanStatus(id: String, isOnline: Boolean, ip: String, port: Int = 52220) {
        contactDao.updateLanStatus(id, isOnline, ip, port, System.currentTimeMillis())
    }

    suspend fun updateLanOnlineStatusOnly(id: String, isOnline: Boolean) {
        contactDao.updateLanOnlineOnly(id, isOnline, System.currentTimeMillis())
    }

    suspend fun resetAllLanStatus() {
        contactDao.resetAllLanStatus()
    }

    suspend fun saveOrUpdateContact(contact: Contact) {
        val existing = contactDao.getContactById(contact.id)
        if (existing != null) {
            val merged = existing.copy(
                name = contact.name.ifBlank { existing.name },
                ipAddress = contact.ipAddress.ifBlank { existing.ipAddress },
                port = if (contact.port > 0) contact.port else existing.port,
                bluetoothAddress = contact.bluetoothAddress.ifBlank { existing.bluetoothAddress },
                isOnlineLan = contact.isOnlineLan,
                lastSeenTimestamp = contact.lastSeenTimestamp,
                fcmToken = contact.fcmToken.ifBlank { existing.fcmToken },
                // No payload carries a colour, so an incoming Contact only ever has the
                // default (0xFF00A884); always keep the stored one.
                avatarColor = existing.avatarColor
            )
            contactDao.insertOrUpdateContact(merged)
        } else {
            contactDao.insertOrUpdateContact(contact.toEntity())
        }
    }

    suspend fun mergeContactAndMigrateMessages(oldId: String, newContact: Contact) {
        if (database != null) {
            database.withTransaction {
                database.messageDao().migrateSenderId(oldId, newContact.id)
                database.messageDao().migrateReceiverId(oldId, newContact.id)
                saveOrUpdateContact(newContact)
                deleteContact(oldId)
            }
        } else {
            saveOrUpdateContact(newContact)
            deleteContact(oldId)
        }
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
    private val callLogDao: CallLogDao,
    private val scope: CoroutineScope? = null
) {
    private val _currentCall = MutableStateFlow<CallSession?>(null)
    val currentCall: StateFlow<CallSession?> = _currentCall.asStateFlow()

    private val _videoUpgradeRequest = MutableStateFlow<String?>(null)
    val videoUpgradeRequest: StateFlow<String?> = _videoUpgradeRequest.asStateFlow()

    private val _uiMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    private var durationTimerJob: Job? = null

    fun setCallSession(session: CallSession?) {
        _currentCall.value = session
    }

    fun setVideoUpgradeRequest(requesterName: String?) {
        _videoUpgradeRequest.value = requesterName
    }

    fun emitMessage(message: String) {
        _uiMessage.tryEmit(message)
    }

    fun updateCallState(state: CallState) {
        _currentCall.value = _currentCall.value?.copy(state = state)
        if (state == CallState.CONNECTED) {
            startDurationTimer()
        } else if (state == CallState.DISCONNECTED || state == CallState.REJECTED || state == CallState.MISSED) {
            stopDurationTimer()
        }
    }

    private fun startDurationTimer() {
        if (durationTimerJob?.isActive == true) return
        val currentScope = scope ?: return
        val timedCallId = _currentCall.value?.callId ?: return
        durationTimerJob = currentScope.launch(Dispatchers.Default) {
            var seconds = _currentCall.value?.durationSeconds ?: 0L
            while (isActive) {
                delay(1000)
                seconds++
                // Atomic, and only while this same call is still connected, so a tick can't
                // overwrite a concurrent state/type change or the ended state with a stale copy
                _currentCall.update { session ->
                    if (session != null && session.callId == timedCallId && session.state == CallState.CONNECTED) {
                        session.copy(durationSeconds = seconds)
                    } else {
                        session
                    }
                }
            }
        }
    }

    private fun stopDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = null
    }

    fun updateCallType(callType: CallType) {
        _currentCall.value = _currentCall.value?.copy(callType = callType)
    }

    fun updateDuration(duration: Long) {
        _currentCall.value = _currentCall.value?.copy(durationSeconds = duration)
    }

    suspend fun endCallAndLog(finalState: CallState = CallState.DISCONNECTED) {
        stopDurationTimer()
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
        _videoUpgradeRequest.value = null
        val latest = _currentCall.value ?: session
        _currentCall.value = latest.copy(state = finalState)
    }

    fun clearSession() {
        stopDurationTimer()
        _videoUpgradeRequest.value = null
        _currentCall.value = null
    }

    fun getCallLogs(): Flow<List<CallLogEntity>> = callLogDao.getAllCallLogs()
}
