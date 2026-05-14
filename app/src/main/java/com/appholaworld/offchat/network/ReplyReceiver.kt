package com.appholaworld.offchat.network

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appholaworld.offchat.OffChatApp
import com.appholaworld.offchat.models.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence("KEY_TEXT_REPLY")?.toString()
        val peerId = intent.getStringExtra("PEER_ID") ?: return
        val peerName = intent.getStringExtra("PEER_NAME") ?: "Peer"
        val notificationId = intent.getIntExtra("NOTIF_ID", 102)

        if (!replyText.isNullOrEmpty()) {
            val app = context.applicationContext as OffChatApp
            val meshManager = app.meshManager
            val repository = app.chatRepository
            val timestamp = System.currentTimeMillis()

            CoroutineScope(Dispatchers.IO).launch {
                // Save to DB
                val message = Message(
                    chatId = peerId,
                    senderId = "me",
                    content = replyText,
                    isIncoming = false,
                    timestamp = timestamp
                )
                repository.saveMessage(peerId, peerName, message)
                
                // Send via Mesh
                meshManager.sendMessage(peerId, replyText, timestamp)

                // Clear notification
                val updatedNotif = (context.applicationContext as OffChatApp).meshManager.toString() // Placeholder for getting actual service
                // In a real app, we would update the notification to show "Sent"
            }
        }
    }
}
