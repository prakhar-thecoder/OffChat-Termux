package com.appholaworld.offchat.activities

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.termux.R
import com.appholaworld.offchat.adapters.MessageAdapter
import com.termux.databinding.ActivityChatBinding
import com.appholaworld.offchat.models.Message
import com.appholaworld.offchat.viewmodels.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ChatActivity : BaseActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessageAdapter
    private lateinit var peerId: String
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var peerName: String

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleFileSelection(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        peerId = intent.getStringExtra("PEER_ID") ?: ""
        if (peerId.isEmpty()) {
            Toast.makeText(this, "Error: Peer ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        peerName = intent.getStringExtra("PEER_NAME") ?: meshManager.getNodeName(peerId) ?: "Chat"
        binding.tvUserName.text = peerName
        
        viewModel.setPeerId(peerId)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        setupInput()
        observeMessages()
        observeConnectionStatus()
        setStatusBarColor(R.color.surface)
    }

    private fun observeConnectionStatus() {
        if (peerId == "PUBLIC_SQUARE") {
            binding.tvStatus.text = "Public Square"
            return
        }
        
        lifecycleScope.launch {
            meshManager.connectedDevices.collect { _ ->
                val isConnected = meshManager.isPeerConnected(peerId)
                binding.tvStatus.text = if (isConnected) "Online" else "Offline"
                binding.tvStatus.setTextColor(if (isConnected) getColor(R.color.secondary) else getColor(R.color.hint))
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(mutableListOf())
        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvMessages.adapter = adapter
    }

    private fun setupInput() {
        binding.btnSend.setOnClickListener {
            val content = binding.etMessage.text.toString().trim()
            if (content.isNotEmpty()) {
                sendMessage(content)
                binding.etMessage.setText("")
            }
        }
        binding.btnAttach.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }
    }

    private fun handleFileSelection(uri: Uri) {
        lifecycleScope.launch {
            try {
                val file = copyUriToInternalStorage(uri)
                Toast.makeText(this@ChatActivity, "Sending file: ${file.name}", Toast.LENGTH_SHORT).show()
                
                val timestamp = System.currentTimeMillis()
                val message = Message(
                    chatId = peerId,
                    senderId = "me",
                    content = "Sent a file: ${file.name}",
                    isIncoming = false,
                    timestamp = timestamp,
                    fileUri = file.absolutePath,
                    fileName = file.name,
                    fileSize = file.length()
                )
                val messageId = viewModel.sendMessageAndGetId(peerId, peerName, message.content, timestamp)
                
                if (peerId == "PUBLIC_SQUARE") {
                    meshManager.broadcastGroupMessage(message.content)
                } else {
                    meshManager.sendFile(peerId, file, messageId)
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Failed to prepare file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun copyUriToInternalStorage(uri: Uri): File = withContext(Dispatchers.IO) {
        val cursor = contentResolver.query(uri, null, null, null, null)
        val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor?.moveToFirst()
        val name = cursor?.getString(nameIndex ?: 0) ?: "shared_file_${System.currentTimeMillis()}"
        cursor?.close()

        val file = File(getExternalFilesDir(null), name)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        file
    }

    private fun observeMessages() {
        viewModel.messages.observe(this) { messages ->
            adapter.updateList(messages)
            if (messages.isNotEmpty()) {
                binding.rvMessages.smoothScrollToPosition(messages.size - 1)
            }
        }
    }

    private fun sendMessage(content: String) {
        val timestamp = System.currentTimeMillis()
        if (peerId == "PUBLIC_SQUARE") {
            lifecycleScope.launch {
                meshManager.broadcastGroupMessage(content)
            }
        } else {
            lifecycleScope.launch {
                val messageId = viewModel.sendMessageAndGetId(peerId, peerName, content, timestamp)
                meshManager.sendMessage(peerId, content, timestamp, messageId)
            }
        }
    }
}
