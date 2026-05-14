package com.appholaworld.offchat.models

sealed class OnlineChatItem {
    data class MessageItem(val msg: OnlineMessage) : OnlineChatItem()
    data class DateHeader(val dateString: String) : OnlineChatItem()
}
