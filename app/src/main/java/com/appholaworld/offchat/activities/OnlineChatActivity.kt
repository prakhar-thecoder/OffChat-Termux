package com.appholaworld.offchat.activities

import android.os.Bundle
import android.widget.Toast
import com.termux.R
import com.termux.databinding.ActivityOnlineChatBinding
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appholaworld.offchat.OffChatApp
import com.appholaworld.offchat.adapters.OnlineMessageAdapter
import com.appholaworld.offchat.models.OnlineChatItem
import com.appholaworld.offchat.viewmodels.OnlineChatRoomViewModel
import com.appholaworld.offchat.models.OnlineChatStatus
import kotlinx.coroutines.launch

class OnlineChatActivity : BaseActivity() {

    private lateinit var binding: ActivityOnlineChatBinding
    private lateinit var chatId: String
    private lateinit var targetUid: String
    private lateinit var targetUsername: String
    private lateinit var myUsername: String
    private var lastMessageTimestamp = -1L
    private var activityStartTime = System.currentTimeMillis()
    private var soundPool: SoundPool? = null
    private var soundId: Int = 0

    private val viewModel: OnlineChatRoomViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = (application as OffChatApp).onlineChatRepository
                return OnlineChatRoomViewModel(repository, chatId, targetUid) as T
            }
        }
    }

    private lateinit var adapter: OnlineMessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnlineChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        targetUid = intent.getStringExtra("TARGET_UID") ?: ""
        targetUsername = intent.getStringExtra("TARGET_USERNAME") ?: "Chat"
        myUsername = intent.getStringExtra("MY_USERNAME") ?: ""

        if (chatId.isEmpty() || targetUid.isEmpty()) {
            Toast.makeText(this, "Error: Chat data missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
        initSoundPool()
        setStatusBarColor(R.color.surface)
    }

    private fun initSoundPool() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(attributes)
            .build()
        soundId = soundPool?.load(this, R.raw.message_pop, 1) ?: 0
    }

    override fun onResume() {
        super.onResume()
        OnlineChatStatus.activeChatId = chatId
    }

    override fun onPause() {
        super.onPause()
        OnlineChatStatus.activeChatId = null
        viewModel.clearUnreadCountOnExit()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        binding.tvUserName.text = targetUsername
        
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        val myUid = (application as OffChatApp).onlineChatRepository.getCurrentUser()?.uid ?: ""
        adapter = OnlineMessageAdapter(myUid)
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@OnlineChatActivity).apply {
                stackFromEnd = true
            }
            adapter = this@OnlineChatActivity.adapter
        }
    }

    private fun setupListeners() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.sendMessage(text, targetUid, targetUsername, myUsername)
                binding.etMessage.setText("")
            }
        }

        binding.rvMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                
                // Pagination logic
                if (dy < 0 && layoutManager.findFirstVisibleItemPosition() == 0) {
                    viewModel.loadMoreMessages()
                }

                // Scroll to bottom button visibility
                val totalItems = layoutManager.itemCount
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (totalItems - lastVisible > 5) {
                    binding.fabScrollToBottom.show()
                } else {
                    binding.fabScrollToBottom.hide()
                }
            }
        })

        binding.fabScrollToBottom.setOnClickListener {
            val totalItems = adapter.itemCount
            if (totalItems > 0) {
                binding.rvMessages.smoothScrollToPosition(totalItems - 1)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                val layoutManager = binding.rvMessages.layoutManager as LinearLayoutManager
                val firstVisiblePos = layoutManager.findFirstVisibleItemPosition()
                val firstVisibleView = layoutManager.findViewByPosition(firstVisiblePos)
                val offset = firstVisibleView?.top ?: 0
                
                val isAtBottom = layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 2
                val isFirstLoad = lastMessageTimestamp == -1L
                val lastChatItem = messages.lastOrNull { it is OnlineChatItem.MessageItem }
                val lastMsg = (lastChatItem as? OnlineChatItem.MessageItem)?.msg
                val isNewMessage = lastMsg != null && lastMsg.timestamp > lastMessageTimestamp
                val oldSize = adapter.itemCount
                
                val currentLastTimestamp = lastMsg?.timestamp ?: lastMessageTimestamp
                
                adapter.submitList(messages) {
                    if (isFirstLoad) {
                        binding.rvMessages.scrollToPosition(messages.size - 1)
                    } else if (isNewMessage) {
                        binding.rvMessages.scrollToPosition(messages.size - 1)
                    } else if (messages.size > oldSize && firstVisiblePos == 0 && !isAtBottom) {
                        val addedCount = messages.size - oldSize
                        layoutManager.scrollToPositionWithOffset(addedCount, offset)
                    }
                }
                
                if (isNewMessage && !isFirstLoad && lastMsg?.senderId == targetUid && lastMsg.timestamp > activityStartTime) {
                    playNotificationSound()
                }
                lastMessageTimestamp = currentLastTimestamp
            }
        }
        lifecycleScope.launch {
            viewModel.isLoadingMore.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            viewModel.targetStatus.collect { status ->
                binding.tvStatus.text = status
            }
        }
    }

    private fun playNotificationSound() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            soundPool?.play(soundId, 1f, 1f, 1, 0, 1f)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool?.release()
        soundPool = null
    }
}
