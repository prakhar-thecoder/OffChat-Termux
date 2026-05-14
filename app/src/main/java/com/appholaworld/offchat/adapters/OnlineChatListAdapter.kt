package com.appholaworld.offchat.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.termux.databinding.ItemChatBinding
import com.appholaworld.offchat.models.OnlineInboxItem
import java.text.SimpleDateFormat
import java.util.*

import com.appholaworld.offchat.viewmodels.OnlineInboxItemWithStatus

class OnlineChatListAdapter(
    private val onChatClick: (OnlineInboxItem) -> Unit
) : RecyclerView.Adapter<OnlineChatListAdapter.ViewHolder>() {

    private var items = listOf<OnlineInboxItemWithStatus>()

    class ViewHolder(val binding: ItemChatBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val wrapper = items[position]
        val item = wrapper.item
        holder.binding.apply {
            tvName.text = item.targetUsername
            tvLastMessage.text = item.lastMessage
            tvTime.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(item.timestamp))
            
            statusDot.visibility = View.GONE
            
            if (wrapper.status == "online") {
                onlineStatusDot.visibility = View.VISIBLE
                tvIpAddress.visibility = View.GONE
            } else {
                onlineStatusDot.visibility = View.GONE
                tvIpAddress.visibility = View.VISIBLE
                tvIpAddress.text = "Offline"
            }
            
            if (item.unreadCount > 0) {
                tvUnreadBadge.visibility = View.VISIBLE
                tvUnreadBadge.text = item.unreadCount.toString()
            } else {
                tvUnreadBadge.visibility = View.GONE
            }
            
            root.setOnClickListener { onChatClick(item) }
        }
    }

    override fun getItemCount() = items.size

    fun submitList(newList: List<OnlineInboxItemWithStatus>) {
        items = newList
        notifyDataSetChanged()
    }
}
