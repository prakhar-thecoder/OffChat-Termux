package com.appholaworld.offchat.viewmodels

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appholaworld.offchat.repository.OnlineChatRepository
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.appholaworld.offchat.models.OnlineInboxItem
import kotlinx.coroutines.launch

sealed class ChatUiEvent {
    data class ShowError(val message: String) : ChatUiEvent()
    data class NavigateToChat(val chatId: String, val targetUid: String, val targetUsername: String, val myUsername: String) : ChatUiEvent()
}

data class OnlineInboxItemWithStatus(
    val item: OnlineInboxItem,
    val status: String
)

class OnlineChatViewModel(private val repository: OnlineChatRepository) : ViewModel(), DefaultLifecycleObserver {

    private val _isLoadingChats = MutableStateFlow(true)
    val isLoadingChats: StateFlow<Boolean> = _isLoadingChats.asStateFlow()

    private val _uiEvent = MutableSharedFlow<ChatUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val _inbox = MutableStateFlow<List<OnlineInboxItem>>(emptyList())
    private val _searchQuery = MutableStateFlow("")

    private var myUsername: String = ""

    val inboxWithStatus: StateFlow<List<OnlineInboxItemWithStatus>> = _inbox
        .flatMapLatest { items ->
            if (items.isEmpty()) flowOf(emptyList())
            else {
                val flows = items.map { item ->
                    repository.observeUserStatus(item.targetUid).map { status ->
                        OnlineInboxItemWithStatus(item, if (status == "online") "online" else "offline")
                    }
                }
                combine(flows) { it.toList() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredInbox: StateFlow<List<OnlineInboxItemWithStatus>> = combine(
        inboxWithStatus,
        _searchQuery
    ) { inbox, query ->
        if (query.isBlank()) inbox
        else inbox.filter { it.item.targetUsername.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val TAG = "OnlineChatViewModel"

    init {
        com.google.firebase.database.FirebaseDatabase.getInstance().goOnline()
        setupInboxObservation()
    }

    private fun setupInboxObservation() {
        val user = repository.getCurrentUser()
        if (user != null) {
            viewModelScope.launch {
                val userData = repository.getUserData(user.uid)
                myUsername = userData?.get("username") as? String ?: ""
                observeInbox()
            }
        }
    }

    private fun observeInbox() {
        viewModelScope.launch {
            repository.observeInbox().collect { inboxItems ->
                _inbox.value = inboxItems
                _isLoadingChats.value = false

                inboxItems.forEach { item ->
                    if (item.unreadCount > 0 && item.lastMessageId.isNotEmpty()) {
                        repository.markMessageAsDelivered(item.chatId, item.lastMessageId)
                    }
                }
            }
        }
    }

    fun initiateChat(targetUsername: String) {
        val currentUser = repository.getCurrentUser() ?: return
        val myUid = currentUser.uid

        viewModelScope.launch {
            val targetUid = repository.findUidByUsername(targetUsername)
            if (targetUid == null) {
                _uiEvent.emit(ChatUiEvent.ShowError("User not found"))
                return@launch
            }

            if (targetUid == myUid) {
                _uiEvent.emit(ChatUiEvent.ShowError("You cannot chat with yourself"))
                return@launch
            }

            val chatId = if (myUid < targetUid) "${myUid}_${targetUid}" else "${targetUid}_${myUid}"
            _uiEvent.emit(ChatUiEvent.NavigateToChat(chatId, targetUid, targetUsername, myUsername))
        }
    }

    fun getMyUsername(): String = myUsername

    override fun onStart(owner: LifecycleOwner) {
        val user = repository.getCurrentUser()
        if (user != null) {
            repository.updateStatus(user.uid, "online")
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        val user = repository.getCurrentUser()
        if (user != null) {
            repository.updateStatus(user.uid, ServerValue.TIMESTAMP)
        }
    }

    override fun onCleared() {
        super.onCleared()
        com.google.firebase.database.FirebaseDatabase.getInstance().goOffline()
    }
}