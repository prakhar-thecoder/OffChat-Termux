package com.appholaworld.offchat.network

import com.termux.R
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.appholaworld.offchat.OffChatApp
import com.appholaworld.offchat.activities.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class OffChatService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val CHANNEL_ID_SERVICE = "OffChatServiceChannel"
        private const val CHANNEL_ID_MESSAGES = "OffChatMessagesChannel"
        private const val NOTIFICATION_ID_SERVICE = 101
        private const val NOTIFICATION_ID_MESSAGES = 102
        
        fun showMessageNotification(context: Context, peerId: String, peerName: String, content: String) {
            val intent = Intent(context, com.appholaworld.offchat.activities.ChatActivity::class.java).apply {
                putExtra("PEER_ID", peerId)
                putExtra("PEER_NAME", peerName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, peerId.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(peerName)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID_MESSAGES, notification)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startMeshObservation()
    }

    private fun startMeshObservation() {
        val app = application as OffChatApp
        serviceScope.launch {
            app.meshManager.startServer()
            app.meshManager.incomingMessages.collectLatest { (peerId, message) ->
                val peerName = app.meshManager.getNodeName(peerId) ?: "Unknown"
                val ipAddress = app.meshManager.getIpForDeviceId(peerId)
                app.chatRepository.saveMessage(peerId, peerName, message, ipAddress)
                
                if (!app.isAppInForeground) {
                    showMessageNotification(this@OffChatService, peerId, peerName, message.content)
                }
            }
        }
        
        serviceScope.launch {
            app.meshManager.connectedDevices.collect { devices ->
                val count = devices.size
                val statusText = if (count > 0) {
                    "$count device${if (count > 1) "s" else ""} connected in your mesh"
                } else {
                    "Securing your offline mesh..."
                }
                updateServiceNotification(statusText)
            }
        }
    }

    private fun updateServiceNotification(content: String) {
        val notification = createServiceNotification("OffChat is active", content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_SERVICE, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createServiceNotification("OffChat is active", "Securing your offline mesh...")
        startForeground(NOTIFICATION_ID_SERVICE, notification)
        return START_STICKY
    }

    private fun createServiceNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            // Channel for Foreground Service (Low Priority)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE, "OffChat Mesh Service",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(serviceChannel)

            // Channel for Incoming Messages (High Priority / Heads-up)
            val messageChannel = NotificationChannel(
                CHANNEL_ID_MESSAGES, "New Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows notifications for incoming offline messages"
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(messageChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
