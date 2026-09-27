package com.pokerlanka.pokie_talkie.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pokerlanka.pokie_talkie.data.local.entity.CallLogEntity
import com.pokerlanka.pokie_talkie.data.local.entity.ContactEntity
import com.pokerlanka.pokie_talkie.data.local.entity.MessageEntity
import com.pokerlanka.pokie_talkie.data.model.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE (senderId = :peerId AND receiverId = :myId) OR (senderId = :myId AND receiverId = :peerId) ORDER BY timestamp ASC")
    fun getMessagesForConversation(peerId: String, myId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :newStatus WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, newStatus: MessageStatus)

    @Query("UPDATE messages SET status = :newStatus WHERE id = :messageId AND status != 'READ'")
    suspend fun updateMessageStatusIfNotRead(messageId: String, newStatus: MessageStatus)

    @Query("UPDATE messages SET status = :newStatus WHERE id = :messageId AND status = 'SENDING'")
    suspend fun updateMessageStatusIfSending(messageId: String, newStatus: MessageStatus)

    @Query("UPDATE messages SET status = :newStatus WHERE id IN (:messageIds)")
    suspend fun updateMessagesStatus(messageIds: List<String>, newStatus: MessageStatus)

    @Query("SELECT id FROM messages WHERE senderId = :peerId AND receiverId = :myId AND status != 'READ'")
    suspend fun getUnreadIncomingMessageIds(peerId: String, myId: String): List<String>

    @Query("UPDATE messages SET status = :newStatus WHERE senderId = :peerId AND receiverId = :myId AND status != 'READ'")
    suspend fun markIncomingMessagesStatus(peerId: String, myId: String, newStatus: MessageStatus)

    @Query("SELECT senderId, COUNT(*) as count FROM messages WHERE receiverId = :myId AND status != 'READ' GROUP BY senderId")
    fun getUnreadCountsBySender(myId: String): Flow<List<SenderUnreadCount>>

    @Query("DELETE FROM messages WHERE (senderId = :peerId AND receiverId = :myId) OR (senderId = :myId AND receiverId = :peerId)")
    suspend fun clearConversation(peerId: String, myId: String)

    @Query("UPDATE messages SET senderId = :newId WHERE senderId = :oldId")
    suspend fun migrateSenderId(oldId: String, newId: String)

    @Query("UPDATE messages SET receiverId = :newId WHERE receiverId = :oldId")
    suspend fun migrateReceiverId(oldId: String, newId: String)
}

data class SenderUnreadCount(
    val senderId: String,
    val count: Int
)

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY isOnlineLan DESC, name ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getContactById(id: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    fun getContactFlow(id: String): Flow<ContactEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateContact(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateContacts(contacts: List<ContactEntity>)

    @Query("UPDATE contacts SET isOnlineLan = :isOnline, ipAddress = :ip, port = :port, lastSeenTimestamp = :timestamp WHERE id = :id")
    suspend fun updateLanStatus(id: String, isOnline: Boolean, ip: String, port: Int, timestamp: Long)

    @Query("UPDATE contacts SET isOnlineLan = :isOnline, lastSeenTimestamp = :timestamp WHERE id = :id")
    suspend fun updateLanOnlineOnly(id: String, isOnline: Boolean, timestamp: Long)

    @Query("UPDATE contacts SET isOnlineLan = 0")
    suspend fun resetAllLanStatus()

    @Query("UPDATE contacts SET name = :newName WHERE id = :id")
    suspend fun updateContactName(id: String, newName: String)

    @Query("UPDATE contacts SET bluetoothAddress = :btAddress WHERE id = :id")
    suspend fun updateBluetoothAddress(id: String, btAddress: String)

    @Query("UPDATE contacts SET fcmToken = :token WHERE id = :id")
    suspend fun updateFcmToken(id: String, token: String)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContact(id: String)

    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()
}

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntity)

    @Query("DELETE FROM call_logs")
    suspend fun clearAllCallLogs()
}
