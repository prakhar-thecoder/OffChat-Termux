package com.appholaworld.offchat.repository

import com.appholaworld.offchat.database.ChatDao
import com.appholaworld.offchat.models.ChatSession
import com.appholaworld.offchat.models.Message
import com.appholaworld.offchat.models.MessageStatus
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {
    
    val allChatSessions: Flow<List<ChatSession>> = chatDao.getAllChatSessions()
    
    fun getMessagesForChat(chatId: String): Flow<List<Message>> {
        return chatDao.getMessagesForChat(chatId)
    }

    suspend fun saveMessage(peerId: String, peerName: String, message: Message, ipAddress: String? = null): Long {
        return chatDao.updateChatWithMessage(peerId, peerName, message, ipAddress)
    }

    suspend fun updateMessageStatus(messageId: Long, status: MessageStatus) {
        chatDao.updateMessageStatus(messageId, status)
    }

    suspend fun getMessageIdByTimestamp(chatId: String, timestamp: Long): Long? {
        return chatDao.getMessageIdByTimestamp(chatId, timestamp)
    }

    suspend fun updateChatSession(session: ChatSession) {
        chatDao.insertChatSession(session)
    }

    suspend fun markChatAsRead(chatId: String) {
        chatDao.markChatAsRead(chatId)
    }

    suspend fun deleteChat(peerId: String) {
        chatDao.deleteChat(peerId)
    }

    fun getTotalUnreadCount(): Flow<Int?> {
        return chatDao.getTotalUnreadCount()
    }
}
