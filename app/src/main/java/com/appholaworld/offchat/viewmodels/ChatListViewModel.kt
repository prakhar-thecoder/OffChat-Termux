package com.appholaworld.offchat.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.appholaworld.offchat.OffChatApp
import com.appholaworld.offchat.models.ChatSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch

class ChatListViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = (application as OffChatApp).chatRepository
    private val meshManager = (application as OffChatApp).meshManager

    private val _searchQuery = MutableStateFlow("")
    val filteredChats: LiveData<List<ChatSession>> = repository.allChatSessions
        .combine(_searchQuery) { chats, query ->
            if (query.isEmpty()) {
                chats
            } else {
                chats.filter { 
                    it.peerName.contains(query, ignoreCase = true) || 
                    it.lastMessage.contains(query, ignoreCase = true) 
                }
            }
        }.asLiveData()

    val totalUnreadCount: LiveData<Int?> = repository.getTotalUnreadCount().asLiveData()
    val connectedDevices: LiveData<Set<String>> = meshManager.connectedDevices.asLiveData()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteChat(peerId: String) {
        viewModelScope.launch {
            repository.deleteChat(peerId)
        }
    }
}
