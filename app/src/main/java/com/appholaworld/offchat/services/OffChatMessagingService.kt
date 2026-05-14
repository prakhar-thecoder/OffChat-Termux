package com.appholaworld.offchat.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.appholaworld.offchat.OffChatApp
import com.termux.R
import com.appholaworld.offchat.activities.OnlineChatActivity
import com.appholaworld.offchat.models.OnlineChatStatus
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OffChatMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val TAG = "OffChatMessagingService"
    private val CHANNEL_ID = "OnlineChatMessagesChannel"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val repository = (application as OffChatApp).onlineChatRepository
        serviceScope.launch {
            repository.updateFcmToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        val data = remoteMessage.data
        val chatId = data["chatId"] ?: return
        val senderName = data["senderName"] ?: "New Message"
        val messageText = data["messageText"] ?: ""

        if (OnlineChatStatus.activeChatId == chatId) {
            // Chat is active, Activity will handle the sound via its own ViewModel listener
            return 
        } else {
            // Show system notification
            showNotification(chatId, senderName, messageText, data)
        }
    }

    private fun playMessageSound() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            try {
                val mediaPlayer = MediaPlayer.create(this, R.raw.message_pop)
                mediaPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mediaPlayer.setOnCompletionListener { it.release() }
                mediaPlayer.start()
            } catch (e: Exception) {
                Log.e(TAG, "Error playing sound", e)
            }
        }
    }

    private fun showNotification(chatId: String, senderName: String, messageText: String, data: Map<String, String>) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Online Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for online chat messages"
                enableLights(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, OnlineChatActivity::class.java).apply {
            putExtra("CHAT_ID", chatId)
            putExtra("TARGET_USERNAME", senderName)
            putExtra("TARGET_UID", data["senderId"]) 
            putExtra("MY_USERNAME", data["recipientUsername"])
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, chatId.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setColor(getColor(R.color.primary))
            .setDefaults(Notification.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(chatId.hashCode(), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        // scope cancellation not strictly needed for service lifecycle if it's short lived, 
        // but good practice if we started anything long running.
    }
}
