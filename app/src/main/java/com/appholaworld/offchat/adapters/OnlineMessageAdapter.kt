package com.appholaworld.offchat.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.termux.databinding.ItemDateHeaderBinding
import com.termux.databinding.ItemMessageIncomingBinding
import com.termux.databinding.ItemMessageOutgoingBinding
import com.appholaworld.offchat.models.OnlineChatItem
import com.appholaworld.offchat.models.OnlineMessage
import java.text.SimpleDateFormat
import java.util.*

class OnlineMessageAdapter(private val currentUid: String) : ListAdapter<OnlineChatItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TYPE_INCOMING = 1
        private const val TYPE_OUTGOING = 2
        private const val TYPE_HEADER = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is OnlineChatItem.DateHeader -> TYPE_HEADER
            is OnlineChatItem.MessageItem -> {
                if (item.msg.senderId == currentUid) TYPE_OUTGOING else TYPE_INCOMING
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_INCOMING -> {
                val binding = ItemMessageIncomingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                IncomingViewHolder(binding)
            }
            TYPE_OUTGOING -> {
                val binding = ItemMessageOutgoingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                OutgoingViewHolder(binding)
            }
            else -> {
                val binding = ItemDateHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                DateHeaderViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is IncomingViewHolder -> holder.bind((item as OnlineChatItem.MessageItem).msg)
            is OutgoingViewHolder -> holder.bind((item as OnlineChatItem.MessageItem).msg)
            is DateHeaderViewHolder -> holder.bind((item as OnlineChatItem.DateHeader).dateString)
        }
    }

    class IncomingViewHolder(private val binding: ItemMessageIncomingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: OnlineMessage) {
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
            binding.tvMessage.text = message.text
            binding.tvTime.text = time
            binding.layoutFile.visibility = View.GONE
            binding.tvSenderName.visibility = View.GONE
        }
    }

    class OutgoingViewHolder(private val binding: ItemMessageOutgoingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: OnlineMessage) {
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
            val statusText = when (message.status) {
                "READ" -> " ✓✓"
                "DELIVERED" -> " ✓✓"
                "SENT" -> " ✓"
                else -> ""
            }
            binding.tvMessage.text = message.text
            binding.tvTime.text = "$time$statusText"
            binding.layoutFile.visibility = View.GONE
        }
    }

    class DateHeaderViewHolder(private val binding: ItemDateHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(date: String) {
            binding.tvDateHeader.text = date
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<OnlineChatItem>() {
        override fun areItemsTheSame(oldItem: OnlineChatItem, newItem: OnlineChatItem): Boolean {
            return if (oldItem is OnlineChatItem.MessageItem && newItem is OnlineChatItem.MessageItem) {
                // Now using messageId for accurate comparison
                oldItem.msg.messageId == newItem.msg.messageId
            } else if (oldItem is OnlineChatItem.DateHeader && newItem is OnlineChatItem.DateHeader) {
                oldItem.dateString == newItem.dateString
            } else {
                false
            }
        }

        override fun areContentsTheSame(oldItem: OnlineChatItem, newItem: OnlineChatItem): Boolean {
            return oldItem == newItem
        }
    }
}
