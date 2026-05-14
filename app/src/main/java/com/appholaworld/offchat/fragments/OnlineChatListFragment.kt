package com.appholaworld.offchat.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.core.widget.doOnTextChanged
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.appholaworld.offchat.OffChatApp
import com.termux.R
import com.appholaworld.offchat.activities.OnlineChatActivity
import com.appholaworld.offchat.adapters.OnlineChatListAdapter
import com.appholaworld.offchat.viewmodels.ChatUiEvent
import com.appholaworld.offchat.viewmodels.OnlineChatViewModel
import com.termux.databinding.FragmentOnlineChatListBinding
import kotlinx.coroutines.launch

class OnlineChatListFragment : Fragment() {

    private var _binding: FragmentOnlineChatListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: OnlineChatListAdapter

    private val viewModel: OnlineChatViewModel by viewModels({ requireParentFragment() }) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = (requireActivity().application as OffChatApp).onlineChatRepository
                return OnlineChatViewModel(repository) as T
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnlineChatListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ProcessLifecycleOwner.get().lifecycle.addObserver(viewModel)

        binding.fabNewChat.setOnClickListener {
            showNewChatDialog()
        }

        binding.etSearchOnline.doOnTextChanged { text, _, _, _ ->
            viewModel.setSearchQuery(text.toString())
        }

        setupRecyclerView()
        observeState()
        observeEvents()
        observeInbox()
    }

    private fun setupRecyclerView() {
        adapter = OnlineChatListAdapter { item ->
            val myUsername = viewModel.getMyUsername()
            val intent = Intent(requireContext(), OnlineChatActivity::class.java).apply {
                putExtra("CHAT_ID", item.chatId)
                putExtra("TARGET_UID", item.targetUid)
                putExtra("TARGET_USERNAME", item.targetUsername)
                putExtra("MY_USERNAME", myUsername)
            }
            startActivity(intent)
        }
        binding.rvOnlineChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@OnlineChatListFragment.adapter
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoadingChats.collect { isLoading ->
                    binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

                    if (isLoading) {
                        binding.rvOnlineChats.visibility = View.GONE
                        binding.layoutEmpty.visibility = View.GONE
                    } else {
                        val isInboxEmpty = adapter.itemCount == 0
                        binding.rvOnlineChats.visibility = if (isInboxEmpty) View.GONE else View.VISIBLE
                        binding.layoutEmpty.visibility = if (isInboxEmpty) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect { event ->
                    when (event) {
                        is ChatUiEvent.ShowError -> Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                        is ChatUiEvent.NavigateToChat -> {
                            val intent = Intent(requireContext(), OnlineChatActivity::class.java).apply {
                                putExtra("CHAT_ID", event.chatId)
                                putExtra("TARGET_UID", event.targetUid)
                                putExtra("TARGET_USERNAME", event.targetUsername)
                                putExtra("MY_USERNAME", event.myUsername)
                            }
                            startActivity(intent)
                        }
                    }
                }
            }
        }
    }

    private fun observeInbox() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredInbox.collect { items ->
                    adapter.submitList(items)

                    val isLoading = viewModel.isLoadingChats.value
                    if (!isLoading) {
                        val isInboxEmpty = items.isEmpty()
                        val isSearchActive = binding.etSearchOnline.text.toString().isNotEmpty()

                        if (isInboxEmpty) {
                            binding.rvOnlineChats.visibility = View.GONE
                            binding.layoutEmpty.visibility = View.VISIBLE
                            if (isSearchActive) {
                                binding.tvEmptyTitle.text = "No Matches Found"
                                binding.tvEmptySubtitle.text = "Try searching with a different name."
                            } else {
                                binding.tvEmptyTitle.text = "No Conversations Yet"
                                binding.tvEmptySubtitle.text = "Start a new chat with your online friends."
                            }
                        } else {
                            binding.rvOnlineChats.visibility = View.VISIBLE
                            binding.layoutEmpty.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun showNewChatDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom_input, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvDescription = dialogView.findViewById<TextView>(R.id.tvDialogDescription)
        val tilInput = dialogView.findViewById<TextInputLayout>(R.id.tilInput)
        val etInput = dialogView.findViewById<TextInputEditText>(R.id.etInput)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSubmit = dialogView.findViewById<MaterialButton>(R.id.btnSubmit)

        tvTitle.text = "New Chat"
        tvDescription.text = "Enter the username of the person you want to chat with."
        tilInput.hint = "Target Username"
        btnSubmit.text = "Start Chat"

        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_OffChat_Dialog)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSubmit.setOnClickListener {
            val username = etInput.text.toString().trim()
            if (username.isNotEmpty()) {
                viewModel.initiateChat(username)
                dialog.dismiss()
            } else {
                etInput.error = "Username cannot be empty"
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(viewModel)
        _binding = null
    }
}
