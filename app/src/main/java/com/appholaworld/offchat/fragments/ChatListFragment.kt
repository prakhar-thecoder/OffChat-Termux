package com.appholaworld.offchat.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.appholaworld.offchat.activities.ChatActivity
import com.appholaworld.offchat.adapters.ChatListAdapter
import com.termux.databinding.FragmentChatListBinding
import com.appholaworld.offchat.viewmodels.ChatListViewModel

class ChatListFragment : Fragment() {

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatListViewModel by viewModels()
    private lateinit var adapter: ChatListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        setupUI()
    }

    private fun setupRecyclerView() {
        adapter = ChatListAdapter(
            chats = emptyList(),
            onChatClick = { chat ->
                context?.let { ctx ->
                    val intent = Intent(ctx, ChatActivity::class.java).apply {
                        putExtra("PEER_ID", chat.peerId)
                        putExtra("PEER_NAME", chat.peerName)
                    }
                    startActivity(intent)
                }
            },
            onChatLongClick = { chat ->
                if (chat.peerId != "PUBLIC_SQUARE") {
                    showDeleteConfirmation(chat)
                }
            }
        )
        binding.rvChats.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChats.adapter = adapter
    }

    private fun showDeleteConfirmation(chat: com.appholaworld.offchat.models.ChatSession) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Chat")
            .setMessage("Are you sure you want to delete your conversation with ${chat.peerName}? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteChat(chat.peerId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupObservers() {
        viewModel.filteredChats.observe(viewLifecycleOwner) { chats ->
            _binding?.let {
                val publicSquare = com.appholaworld.offchat.models.ChatSession(
                    peerId = "PUBLIC_SQUARE",
                    peerName = "Global Public Square",
                    lastMessage = "Tap to broadcast to everyone nearby",
                    lastTimestamp = System.currentTimeMillis()
                )
                
                val combinedList = mutableListOf(publicSquare)
                combinedList.addAll(chats.filter { it.peerId != "PUBLIC_SQUARE" })

                it.layoutEmpty.visibility = if (combinedList.size <= 1 && chats.isEmpty()) View.VISIBLE else View.GONE
                it.rvChats.visibility = if (combinedList.size > 1 || chats.isNotEmpty()) View.VISIBLE else View.GONE
                adapter.updateList(combinedList)
            }
        }

        viewModel.connectedDevices.observe(viewLifecycleOwner) { connectedIds ->
            adapter.updateOnlineStatus(connectedIds)
        }
    }

    private fun setupUI() {
        binding.btnShare.setOnClickListener {
            shareApp()
        }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Download OffChat")
            putExtra(Intent.EXTRA_TEXT, "Hey! Join me on OffChat, a secure offline messaging app: https://offchat.example.com/download")
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
