package com.appholaworld.offchat.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey val peerId: String,
    val peerName: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val unreadCount: Int = 0, // Track unread count per session
    val lastIpAddress: String? = null
)
