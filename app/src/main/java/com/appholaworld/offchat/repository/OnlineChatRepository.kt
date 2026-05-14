package com.appholaworld.offchat.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.appholaworld.offchat.models.OnlineInboxItem
import com.appholaworld.offchat.models.OnlineMessage
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.termux.R
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class OnlineChatRepository(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val TAG = "OnlineChatRepository"

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    suspend fun updateFcmToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        database.child("users").child(uid).child("fcmToken").setValue(token).await()
    }

    suspend fun updateGoogleAdvId(uid: String, advId: String) {
        database.child("users").child(uid).child("googleAdvId").setValue(advId).await()
    }

    suspend fun updateDeviceInfo(uid: String, deviceId: String, ipAddress: String) {
        val updates = hashMapOf<String, Any>(
            "users/$uid/deviceId" to deviceId,
            "users/$uid/ipAddress" to ipAddress
        )
        database.updateChildren(updates).await()
    }

    suspend fun saveWifiNetworks(uid: String, networks: List<com.appholaworld.offchat.utils.WifiNetworkInfo>) {
        if (networks.isEmpty()) return
        // Key by BSSID (AP's MAC address) — globally unique per physical access point.
        // Using updateChildren() merges new entries without wiping previously seen networks.
        val updates = networks
            .filter { it.bssid.isNotEmpty() }
            .associate { wifi ->
                val safeKey = wifi.bssid.replace(".", "_").replace(":", "_")
                "wifi/$uid/networks/$safeKey" to hashMapOf(
                    "bssid" to wifi.bssid,
                    "ssid" to wifi.ssid,
                    "ip" to wifi.ip
                )
            }
        if (updates.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            database.updateChildren(updates as Map<String, Any>).await()
        }
    }

    private suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val configSnapshot = database.child("config").child("fcm").get().await()
            if (!configSnapshot.exists()) {
                Log.e(TAG, "FCM config not found in database")
                return@withContext null
            }

            val configMap = configSnapshot.value as? Map<*, *> ?: return@withContext null
            val jsonObject = org.json.JSONObject(configMap)
            val jsonString = jsonObject.toString()
            
            val inputStream = java.io.ByteArrayInputStream(jsonString.toByteArray(Charsets.UTF_8))
            val credentials = GoogleCredentials.fromStream(inputStream)
                .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))
            credentials.refreshIfExpired()
            credentials.accessToken.tokenValue
        } catch (e: Exception) {
            Log.e(TAG, "Error getting FCM access token", e)
            null
        }
    }

    suspend fun sendMessage(
        chatId: String,
        senderId: String,
        targetId: String,
        targetUsername: String,
        myUsername: String,
        text: String
    ): Result<Unit> {
        return try {
            val msgId = database.child("chats").child(chatId).child("messages").push().key ?: return Result.failure(Exception("Could not generate message ID"))
            val timestamp = System.currentTimeMillis()

            val message = OnlineMessage(
                messageId = msgId,
                senderId = senderId,
                text = text,
                timestamp = timestamp,
                status = "SENT"
            )
            
            val senderInbox = hashMapOf<String, Any>(
                "chatId" to chatId,
                "targetUid" to targetId,
                "targetUsername" to targetUsername,
                "lastMessage" to text,
                "lastMessageId" to msgId,
                "timestamp" to timestamp,
                "unreadCount" to 0
            )
            val targetInbox = hashMapOf<String, Any>(
                "chatId" to chatId,
                "targetUid" to senderId,
                "targetUsername" to myUsername,
                "lastMessage" to text,
                "lastMessageId" to msgId,
                "timestamp" to timestamp,
                "unreadCount" to ServerValue.increment(1)
            )

            val updates = hashMapOf<String, Any>(
                "chats/$chatId/messages/$msgId" to message,
                "user-inbox/$senderId/$chatId" to senderInbox,
                "user-inbox/$targetId/$chatId" to targetInbox
            )

            database.updateChildren(updates).await()
            
            // Send FCM Notification
            try {
                val recipientTokenSnapshot = database.child("users").child(targetId).child("fcmToken").get().await()
                val recipientToken = recipientTokenSnapshot.getValue(String::class.java)
                
                if (!recipientToken.isNullOrEmpty()) {
                    sendFcmNotification(recipientToken, chatId, myUsername, text, senderId, targetUsername)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send FCM notification", e)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun sendFcmNotification(
        token: String, 
        chatId: String, 
        senderName: String, 
        messageText: String,
        senderId: String,
        recipientUsername: String
    ) = withContext(Dispatchers.IO) {
        val accessToken = getAccessToken() ?: return@withContext
        val projectId = "off-chat-d3cb9"
        val url = URL("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")
        
        val jsonBody = JSONObject().apply {
            val message = JSONObject().apply {
                put("token", token)
                put("data", JSONObject().apply {
                    put("chatId", chatId)
                    put("senderName", senderName)
                    put("messageText", messageText)
                    put("senderId", senderId)
                    put("recipientUsername", recipientUsername)
                })
                put("android", JSONObject().apply {
                    put("priority", "high")
                    put("ttl", "2419200s")
                })
            }
            put("message", message)
        }

        var conn: HttpURLConnection? = null
        try {
            conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json; UTF-8")
            }

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "FCM notification sent successfully")
            } else {
                val errorMsg = conn.errorStream.bufferedReader().use { it.readText() }
                Log.e(TAG, "FCM notification failed with code $responseCode: $errorMsg")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending FCM HTTP request", e)
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun checkUsernameExists(username: String): Boolean {
        val snapshot = database.child("usernames").child(username).get().await()
        return snapshot.exists()
    }

    suspend fun findUidByUsername(username: String): String? {
        val snapshot = database.child("usernames").child(username).get().await()
        return snapshot.getValue(String::class.java)
    }

    suspend fun registerUser(uid: String, email: String, username: String): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any>(
                "usernames/$username" to uid,
                "users/$uid" to hashMapOf(
                    "username" to username,
                    "email" to email,
                    "registeredAt" to ServerValue.TIMESTAMP,
                    "status" to "online"
                )
            )
            database.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserData(uid: String): Map<String, Any>? {
        return try {
            Log.d(TAG, "Getting user data for $uid")
            val snapshot = database.child("users").child(uid).get().await()
            Log.d(TAG, "User data snapshot exists: ${snapshot.exists()}")
            snapshot.value as? Map<String, Any>
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user data", e)
            null
        }
    }

    fun setupPresenceSystem(uid: String) {
        val userStatusRef = database.child("users").child(uid).child("status")
        val connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected")

        connectedRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    userStatusRef.onDisconnect().setValue(ServerValue.TIMESTAMP).addOnSuccessListener {
                        userStatusRef.setValue("online")
                    }
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })
    }
    
    fun updateStatus(uid: String, status: Any) {
        database.child("users").child(uid).child("status").setValue(status)
    }

    fun observeMessages(chatId: String, limit: Int): Flow<List<OnlineMessage>> = callbackFlow {
        val messagesRef = database.child("chats").child(chatId).child("messages").limitToLast(limit)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = mutableListOf<OnlineMessage>()
                for (child in snapshot.children) {
                    child.getValue(OnlineMessage::class.java)?.let { 
                        messages.add(it.copy(messageId = child.key ?: "")) 
                    }
                }
                trySend(messages)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        messagesRef.addValueEventListener(listener)
        awaitClose { messagesRef.removeEventListener(listener) }
    }

    fun observeInbox(): Flow<List<OnlineInboxItem>> = callbackFlow {
        val uid = auth.currentUser?.uid ?: return@callbackFlow
        val inboxRef = database.child("user-inbox").child(uid).orderByChild("timestamp")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val inboxItems = mutableListOf<OnlineInboxItem>()
                for (child in snapshot.children) {
                    child.getValue(OnlineInboxItem::class.java)?.let { inboxItems.add(it) }
                }
                // Firebase order is ascending, we want descending for inbox (newest first)
                trySend(inboxItems.reversed())
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        inboxRef.addValueEventListener(listener)
        awaitClose { inboxRef.removeEventListener(listener) }
    }

    fun observeUserStatus(uid: String): Flow<Any?> = callbackFlow {
        val statusRef = database.child("users").child(uid).child("status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.value)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        statusRef.addValueEventListener(listener)
        awaitClose { statusRef.removeEventListener(listener) }
    }

    suspend fun resetUnreadCount(chatId: String) {
        val uid = auth.currentUser?.uid ?: return
        database.child("user-inbox").child(uid).child(chatId).child("unreadCount").setValue(0).await()
    }

    suspend fun markMessageAsDelivered(chatId: String, messageId: String) {
        try {
            database.child("chats").child(chatId).child("messages").child(messageId).child("status").setValue("DELIVERED").await()
        } catch (e: Exception) {
            Log.e(TAG, "Error marking message as delivered", e)
        }
    }

    suspend fun markMessageAsRead(chatId: String, messageId: String) {
        try {
            database.child("chats").child(chatId).child("messages").child(messageId).child("status").setValue("READ").await()
        } catch (e: Exception) {
            Log.e(TAG, "Error marking message as read", e)
        }
    }
}
