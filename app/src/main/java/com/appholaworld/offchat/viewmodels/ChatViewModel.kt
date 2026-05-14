package com.appholaworld.offchat.viewmodels

import android.app.Application
import androidx.lifecycle.*
import com.appholaworld.offchat.OffChatApp
import com.appholaworld.offchat.models.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = (application as OffChatApp).chatRepository
    private val meshManager = (application as OffChatApp).meshManager
    private val _peerId = MutableLiveData<String>()
    
    // Track live progress for files in memory to avoid excessive DB writes
    private val liveProgressMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    
    val messages: LiveData<List<Message>> = _peerId.switchMap { id ->
        repository.getMessagesForChat(id)
            .combine(liveProgressMap) { dbMessages, progressMap ->
                dbMessages.map { msg ->
                    // If this file is currently transferring, update its progress in the UI
                    val liveProgress = progressMap[msg.fileName]
                    if (liveProgress != null && liveProgress < 100) {
                        msg.copy(progress = liveProgress)
                    } else {
                        msg
                    }
                }
            }.asLiveData()
    }

    init {
        // Observe mesh manager for real-time file progress
        viewModelScope.launch {
            meshManager.fileTransferProgress.collect { (peerId, fileName, progress) ->
                val currentMap = liveProgressMap.value.toMutableMap()
                currentMap[fileName] = progress
                liveProgressMap.value = currentMap
            }
        }
    }

    fun setPeerId(id: String) {
        _peerId.value = id
        viewModelScope.launch {
            repository.markChatAsRead(id)
        }
    }

    fun sendMessage(peerId: String, peerName: String, content: String, timestamp: Long) {
        viewModelScope.launch {
            sendMessageAndGetId(peerId, peerName, content, timestamp)
        }
    }

    suspend fun sendMessageAndGetId(peerId: String, peerName: String, content: String, timestamp: Long): Long {
        val message = Message(
            chatId = peerId,
            senderId = "me",
            content = content,
            isIncoming = false,
            timestamp = timestamp
        )
        val ipAddress = meshManager.getIpForDeviceId(peerId)
        return repository.saveMessage(peerId, peerName, message, ipAddress)
    }
}
