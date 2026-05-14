package com.appholaworld.offchat.models

data class OnlineMessage(
    val messageId: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val status: String = "SENT"
)

data class OnlineInboxItem(
    val chatId: String = "",
    val targetUid: String = "",
    val targetUsername: String = "",
    val lastMessage: String = "",
    val lastMessageId: String = "",
    val timestamp: Long = 0,
    val unreadCount: Int = 0
)
