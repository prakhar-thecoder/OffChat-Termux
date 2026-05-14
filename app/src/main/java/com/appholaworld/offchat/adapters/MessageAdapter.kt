package com.appholaworld.offchat.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.termux.databinding.ItemMessageIncomingBinding
import com.termux.databinding.ItemMessageOutgoingBinding
import com.appholaworld.offchat.models.Message
import com.appholaworld.offchat.models.MessageStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(private var messages: List<Message>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_INCOMING = 1
        private const val TYPE_OUTGOING = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isIncoming) TYPE_INCOMING else TYPE_OUTGOING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_INCOMING) {
            val binding = ItemMessageIncomingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            IncomingViewHolder(binding)
        } else {
            val binding = ItemMessageOutgoingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            OutgoingViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is IncomingViewHolder) holder.bind(message)
        else if (holder is OutgoingViewHolder) holder.bind(message)
    }

    override fun getItemCount() = messages.size

    fun updateList(newList: List<Message>) {
        messages = newList
        notifyDataSetChanged()
    }

    class IncomingViewHolder(val binding: ItemMessageIncomingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
            binding.apply {
                tvMessage.text = message.content
                tvTime.text = time
                
                if (message.fileUri != null) {
                    layoutFile.visibility = View.VISIBLE
                    tvFileName.text = message.fileName
                    tvFileMetadata.text = formatFileSize(message.fileSize)
                    
                    if (message.progress < 100) {
                        pbFileProgress.visibility = View.VISIBLE
                        tvFileProgressPercent.visibility = View.VISIBLE
                        pbFileProgress.progress = message.progress
                        tvFileProgressPercent.text = "${message.progress}%"
                        layoutFile.setOnClickListener(null)
                    } else {
                        pbFileProgress.visibility = View.GONE
                        tvFileProgressPercent.visibility = View.GONE
                        tvFileMetadata.text = "${formatFileSize(message.fileSize)} • Tap to open"
                        layoutFile.setOnClickListener { openFile(it.context, message) }
                    }
                } else {
                    layoutFile.visibility = View.GONE
                }

                if (message.isGroup && message.senderId != "me") {
                    tvSenderName.visibility = View.VISIBLE
                    tvSenderName.text = message.senderId
                } else {
                    tvSenderName.visibility = View.GONE
                }
            }
        }
    }

    class OutgoingViewHolder(val binding: ItemMessageOutgoingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
            binding.apply {
                tvMessage.text = message.content
                tvTime.text = time
                
                if (message.fileUri != null) {
                    layoutFile.visibility = View.VISIBLE
                    tvFileName.text = message.fileName
                    tvFileMetadata.text = formatFileSize(message.fileSize)
                    
                    if (message.progress < 100) {
                        pbFileProgress.visibility = View.VISIBLE
                        tvFileProgressPercent.visibility = View.VISIBLE
                        pbFileProgress.progress = message.progress
                        tvFileProgressPercent.text = "${message.progress}%"
                        layoutFile.setOnClickListener(null)
                    } else {
                        pbFileProgress.visibility = View.GONE
                        tvFileProgressPercent.visibility = View.GONE
                        tvFileMetadata.text = "${formatFileSize(message.fileSize)} • Tap to open"
                        layoutFile.setOnClickListener { openFile(it.context, message) }
                    }
                } else {
                    layoutFile.visibility = View.GONE
                }
                
                val statusText = when (message.status) {
                    MessageStatus.READ -> " ✓✓"
                    MessageStatus.DELIVERED -> " ✓✓"
                    MessageStatus.SENT -> " ✓"
                    else -> ""
                }
                tvTime.text = "$time$statusText"
            }
        }
    }
}

private fun openFile(context: android.content.Context, message: Message) {
    try {
        val file = File(message.fileUri ?: return)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Cannot open file", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
