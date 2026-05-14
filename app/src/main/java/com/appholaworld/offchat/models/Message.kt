package com.appholaworld.offchat.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isIncoming: Boolean,
    var status: MessageStatus = MessageStatus.SENT,
    val fileUri: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val isGroup: Boolean = false,
    var progress: Int = 100,
    var isRead: Boolean = false // Track unread status
)

enum class MessageStatus {
    SENT, DELIVERED, READ, ERROR
}
