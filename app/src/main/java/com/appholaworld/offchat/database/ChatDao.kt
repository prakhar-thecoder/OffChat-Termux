package com.appholaworld.offchat.database

import androidx.room.*
import com.appholaworld.offchat.models.ChatSession
import com.appholaworld.offchat.models.Message
import com.appholaworld.offchat.models.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY lastTimestamp DESC")
    fun getAllChatSessions(): Flow<List<ChatSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatSession(session: ChatSession)

    @Query("SELECT * FROM chat_sessions WHERE peerId = :peerId")
    suspend fun getChatSession(peerId: String): ChatSession?

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: Long, status: MessageStatus)

    @Query("SELECT id FROM messages WHERE chatId = :chatId AND timestamp = :timestamp LIMIT 1")
    suspend fun getMessageIdByTimestamp(chatId: String, timestamp: Long): Long?

    @Query("UPDATE messages SET isRead = 1 WHERE chatId = :chatId AND isIncoming = 1")
    suspend fun markMessagesAsRead(chatId: String)

    @Query("UPDATE chat_sessions SET unreadCount = 0 WHERE peerId = :peerId")
    suspend fun resetUnreadCount(peerId: String)

    @Query("SELECT SUM(unreadCount) FROM chat_sessions")
    fun getTotalUnreadCount(): Flow<Int?>

    @Query("DELETE FROM chat_sessions WHERE peerId = :peerId")
    suspend fun deleteChatSession(peerId: String)

    @Query("DELETE FROM messages WHERE chatId = :peerId")
    suspend fun deleteMessagesForChat(peerId: String)

    @Transaction
    suspend fun deleteChat(peerId: String) {
        deleteChatSession(peerId)
        deleteMessagesForChat(peerId)
    }

    @Transaction
    suspend fun markChatAsRead(chatId: String) {
        markMessagesAsRead(chatId)
        resetUnreadCount(chatId)
    }
    
    @Transaction
    suspend fun updateChatWithMessage(peerId: String, peerName: String, message: Message, ipAddress: String?): Long {
        val session = getChatSession(peerId) ?: ChatSession(peerId, peerName, message.content, message.timestamp, lastIpAddress = ipAddress)
        
        val newUnreadCount = if (message.isIncoming && !message.isRead) {
            session.unreadCount + 1
        } else {
            session.unreadCount
        }

        val updatedSession = session.copy(
            lastMessage = message.content,
            lastTimestamp = message.timestamp,
            unreadCount = newUnreadCount,
            lastIpAddress = ipAddress ?: session.lastIpAddress
        )
        insertChatSession(updatedSession)
        return insertMessage(message)
    }
}
