package com.appholaworld.offchat.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.termux.R
import com.termux.databinding.ItemChatBinding
import com.appholaworld.offchat.models.ChatSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatListAdapter(
    private var chats: List<ChatSession>,
    private var onlinePeerIds: Set<String> = emptySet(),
    private val onChatClick: (ChatSession) -> Unit,
    private val onChatLongClick: (ChatSession) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemChatBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chat = chats[position]
        holder.binding.apply {
            tvName.text = chat.peerName
            
            // Online/Offline Status logic
            val isOnline = onlinePeerIds.contains(chat.peerId) || 
                           onlinePeerIds.contains(chat.lastIpAddress) ||
                           chat.peerId == "PUBLIC_SQUARE"
            
            statusDot.visibility = if (isOnline) View.VISIBLE else View.GONE
            
            tvLastMessage.text = if (chat.lastMessage.isNullOrEmpty()) "No messages" else chat.lastMessage
            tvTime.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(chat.lastTimestamp))
            tvIpAddress.text = if (isOnline) "Connected • ${chat.lastIpAddress ?: "Mesh"}" else "Offline"
            tvIpAddress.setTextColor(if (isOnline) holder.itemView.context.getColor(R.color.secondary) else holder.itemView.context.getColor(R.color.hint))

            // Unread Count Badge
            if (chat.unreadCount > 0) {
                tvUnreadBadge.visibility = View.VISIBLE
                tvUnreadBadge.text = chat.unreadCount.toString()
            } else {
                tvUnreadBadge.visibility = View.GONE
            }
            
            root.setOnClickListener { onChatClick(chat) }
            root.setOnLongClickListener { 
                onChatLongClick(chat)
                true
            }
        }
    }

    override fun getItemCount() = chats.size

    fun updateList(newList: List<ChatSession>) {
        chats = newList
        notifyDataSetChanged()
    }

    fun updateOnlineStatus(newOnlineIds: Set<String>) {
        onlinePeerIds = newOnlineIds
        notifyDataSetChanged()
    }
}
