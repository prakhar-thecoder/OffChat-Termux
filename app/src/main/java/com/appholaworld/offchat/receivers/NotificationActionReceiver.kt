package com.appholaworld.offchat.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.appholaworld.offchat.utils.PreferenceManager

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notification_id", -1)
        Log.d("NotificationReceiver", "Action tapped for notification ID: $notificationId")

        if (notificationId != -1) {
            val preferenceManager = PreferenceManager(context)
            preferenceManager.removePendingNotification(notificationId)
        }

        val targetIntent = intent.getParcelableExtra<Intent>("target_intent")
        if (targetIntent != null) {
            try {
                context.startActivity(targetIntent)
            } catch (e: Exception) {
                Log.e("NotificationReceiver", "Failed to start target intent", e)
            }
        }
    }
}
