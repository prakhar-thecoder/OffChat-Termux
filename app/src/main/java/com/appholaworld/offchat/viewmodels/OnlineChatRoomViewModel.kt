package com.appholaworld.offchat.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appholaworld.offchat.models.OnlineChatItem
import com.appholaworld.offchat.models.OnlineMessage
import com.appholaworld.offchat.repository.OnlineChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import java.text.SimpleDateFormat
import java.util.*

class OnlineChatRoomViewModel(
    private val repository: OnlineChatRepository,
    private val chatId: String,
    private val targetUid: String
) : ViewModel() {

    private val _messageLimit = MutableStateFlow(10)
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<OnlineChatItem>> = _messageLimit
        .flatMapLatest { limit ->
            _isLoadingMore.value = true
            repository.observeMessages(chatId, limit)
        }
        .map { msgList ->
            val chatItems = mutableListOf<OnlineChatItem>()
            var lastDate: String? = null

            msgList.forEach { message ->
                val currentDate = formatDateHeader(message.timestamp)
                if (currentDate != lastDate) {
                    chatItems.add(OnlineChatItem.DateHeader(currentDate))
                    lastDate = currentDate
                }
                chatItems.add(OnlineChatItem.MessageItem(message))
            }
            chatItems
        }
        .onEach { items ->
            _isLoadingMore.value = false
            resetUnreadCount()

            // Mark incoming messages as READ
            val myUid = repository.getCurrentUser()?.uid
            items.filterIsInstance<OnlineChatItem.MessageItem>()
                .map { it.msg }
                .filter { it.senderId != myUid && (it.status == "SENT" || it.status == "DELIVERED") }
                .forEach { msg ->
                    viewModelScope.launch {
                        repository.markMessageAsRead(chatId, msg.messageId)
                    }
                }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _targetStatus = MutableStateFlow<String>("Offline")
    val targetStatus: StateFlow<String> = _targetStatus.asStateFlow()

    init {
        com.google.firebase.database.FirebaseDatabase.getInstance().goOnline()
        observeTargetStatus()
        resetUnreadCount()
    }

    private fun resetUnreadCount() {
        viewModelScope.launch {
            try {
                repository.resetUnreadCount(chatId)
            } catch (e: Exception) {
                android.util.Log.e("OnlineChatRoomVM", "Reset Error: ${e.message}")
            }
        }
    }

    fun loadMoreMessages() {
        if (_isLoadingMore.value) return
        _messageLimit.value += 10
    }

    fun clearUnreadCountOnExit() {
        viewModelScope.launch {
            try {
                repository.resetUnreadCount(chatId)
            } catch (e: Exception) {
                android.util.Log.e("OnlineChatRoomVM", "Exit Reset Error: ${e.message}")
            }
        }
    }

    private fun observeTargetStatus() {
        viewModelScope.launch {
            try {
                repository.observeUserStatus(targetUid).collect { status ->
                    _targetStatus.value = formatStatus(status)
                }
            } catch (e: Exception) {
                android.util.Log.e("OnlineChatRoomVM", "Status Error: ${e.message}")
            }
        }
    }

    private fun formatStatus(status: Any?): String {
        return when (status) {
            "online" -> "Online"
            is Long -> {
                val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(status))
                "Last seen at $time"
            }
            else -> "Offline"
        }
    }

    private fun formatDateHeader(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }

        calendar.timeInMillis = timestamp

        return when {
            isSameDay(calendar, today) -> "Today"
            isSameDay(calendar, yesterday) -> "Yesterday"
            else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun sendMessage(text: String, targetUid: String, targetUsername: String, myUsername: String) {
        if (text.isBlank()) return
        val myUid = repository.getCurrentUser()?.uid ?: return
        
        viewModelScope.launch {
            repository.sendMessage(chatId, myUid, targetUid, targetUsername, myUsername, text)
        }
    }
}
