package com.appholaworld.offchat.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.IconCompat
import com.termux.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import com.appholaworld.offchat.receivers.NotificationActionReceiver

object DynamicNotificationHelper {

    private const val TAG = "DynamicNotification"

    suspend fun showNotification(
        context: Context,
        notificationId: Int,
        data: Map<String, String>
    ) {
        val title = data["title"] ?: return
        val description = data["description"] ?: ""
        val iconUrl = data["iconUrl"]
        val smallIcon = data["smallIcon"] ?: "ic_launcher"
        val titleColor = data["titleColor"]?.takeIf { it.isNotBlank() }
        val style = data["style"] ?: "normal"
        val actionType = data["actionType"] ?: "none"
        val actionData = data["actionData"] ?: ""

        var largeIcon: Bitmap? = null
        if (!iconUrl.isNullOrEmpty()) {
            largeIcon = withContext(Dispatchers.IO) {
                try {
                    val connection = URL(iconUrl).openConnection()
                    connection.doInput = true
                    connection.connect()
                    BitmapFactory.decodeStream(connection.inputStream)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load icon", e)
                    null
                }
            }
        }

        buildAndShowDynamicNotification(
            context,
            notificationId,
            title,
            description,
            largeIcon,
            smallIcon,
            titleColor,
            style,
            actionType,
            actionData
        )
    }

    private fun buildAndShowDynamicNotification(
        context: Context,
        notificationId: Int,
        title: String,
        desc: String,
        icon: Bitmap?,
        smallIcon: String,
        titleColor: String?,
        style: String,
        actionType: String,
        actionData: String
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dynamicChannelId = "DynamicNotifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                dynamicChannelId,
                "System Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        var intent: Intent? = null
        when (actionType) {
            "install" -> {
                val file = File(actionData)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                }
            }
            "uninstall" -> {
                intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$actionData")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            "url" -> {
                intent = Intent(Intent.ACTION_VIEW, Uri.parse(actionData)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
        }

        var pendingIntent: PendingIntent? = null
        var actionIntent: PendingIntent? = null

        val smallIconRes = when (smallIcon) {
            "ic_update" -> R.drawable.ic_update
            "ic_warning" -> R.drawable.ic_warning
            else -> R.mipmap.ic_launcher
        }
        val styledTitle = titleColor?.let { colorHex ->
            try {
                SpannableString(title).apply {
                    setSpan(
                        ForegroundColorSpan(Color.parseColor(colorHex)),
                        0,
                        length,
                        0
                    )
                }
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid titleColor provided: $colorHex", e)
                null
            }
        }

        if (intent != null || actionType == "none") {
            val receiverIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                putExtra("notification_id", notificationId)
                if (intent != null) {
                    putExtra("target_intent", intent)
                }
            }
            
            pendingIntent = PendingIntent.getBroadcast(
                context, notificationId, receiverIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            // For action button, we can use the same receiver intent if it's the same target intent.
            // If they are distinct, we would create separate ones, but here they just open the same thing.
            if (intent != null) {
                val actionReceiverIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    putExtra("notification_id", notificationId)
                    putExtra("target_intent", intent)
                }
                actionIntent = PendingIntent.getBroadcast(
                    context, notificationId + 10000, actionReceiverIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
        }

        val builder = NotificationCompat.Builder(context, dynamicChannelId)
            .setSmallIcon(smallIconRes)
            .setAutoCancel(true)

        if (icon != null) {
            val sender = Person.Builder()
                .setName(title)
                .setIcon(IconCompat.createWithBitmap(icon))
                .build()

            builder.setLargeIcon(icon)
            builder.setStyle(
                NotificationCompat.MessagingStyle(sender)
                    .setConversationTitle(styledTitle ?: title)
                    .addMessage(desc, System.currentTimeMillis(), sender)
            )
        } else {
            builder.setContentTitle(styledTitle ?: title)
            builder.setContentText(desc)

            if (style == "bigText") {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(desc))
            }
        }

        pendingIntent?.let { builder.setContentIntent(it) }

        when (actionType) {
            "install" -> actionIntent?.let { builder.addAction(0, "Install", it) }
            "uninstall" -> actionIntent?.let { builder.addAction(0, "Uninstall", it) }
            "url" -> actionIntent?.let { builder.addAction(0, "Open Link", it) }
        }

        manager.notify(notificationId, builder.build())
    }
}
