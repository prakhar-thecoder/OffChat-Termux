package com.appholaworld.offchat.utils

import android.content.Context
import android.content.SharedPreferences
import com.appholaworld.offchat.models.User
import org.json.JSONArray
import org.json.JSONObject

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "offchat_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_PROFILE_COMPLETED = "profile_completed"
        private const val KEY_TERMS_ACCEPTED = "terms_accepted"
        private const val KEY_PENDING_NOTIFICATIONS = "pending_notifications"
    }

    fun saveUser(user: User) {
        prefs.edit().apply {
            putString(KEY_USER_ID, user.id)
            putString(KEY_USER_NAME, user.name)
            putString(KEY_USER_PHONE, user.phoneNumber)
            putBoolean(KEY_PROFILE_COMPLETED, user.isProfileCompleted)
            apply()
        }
    }

    fun getUser(): User? {
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        val name = prefs.getString(KEY_USER_NAME, "") ?: ""
        val phone = prefs.getString(KEY_USER_PHONE, "") ?: ""
        val completed = prefs.getBoolean(KEY_PROFILE_COMPLETED, false)
        return User(id, name, phone, android.os.Build.MODEL, completed)
    }

    fun isProfileCompleted(): Boolean {
        return prefs.getBoolean(KEY_PROFILE_COMPLETED, false)
    }

    fun isTermsAccepted(): Boolean {
        return prefs.getBoolean(KEY_TERMS_ACCEPTED, false)
    }

    fun setTermsAccepted(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_TERMS_ACCEPTED, accepted).apply()
    }

    fun savePendingNotification(id: Int, data: Map<String, String>) {
        val pendingStr = prefs.getString(KEY_PENDING_NOTIFICATIONS, "[]")
        val jsonArray = JSONArray(pendingStr)
        val jsonObject = JSONObject()
        jsonObject.put("notification_id", id)
        val dataObject = JSONObject()
        for ((key, value) in data) {
            dataObject.put(key, value)
        }
        jsonObject.put("data", dataObject)
        jsonArray.put(jsonObject)
        prefs.edit().putString(KEY_PENDING_NOTIFICATIONS, jsonArray.toString()).apply()
    }

    fun getPendingNotifications(): List<Pair<Int, Map<String, String>>> {
        val pendingStr = prefs.getString(KEY_PENDING_NOTIFICATIONS, "[]")
        val jsonArray = JSONArray(pendingStr)
        val list = mutableListOf<Pair<Int, Map<String, String>>>()
        for (i in 0 until jsonArray.length()) {
            try {
                val jsonObject = jsonArray.getJSONObject(i)
                val id = jsonObject.getInt("notification_id")
                val dataObject = jsonObject.getJSONObject("data")
                val map = mutableMapOf<String, String>()
                val keys = dataObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = dataObject.getString(key)
                }
                list.add(Pair(id, map))
            } catch (e: Exception) {
                // Skip invalid entries
            }
        }
        return list
    }

    fun removePendingNotification(id: Int) {
        val pendingStr = prefs.getString(KEY_PENDING_NOTIFICATIONS, "[]")
        val jsonArray = JSONArray(pendingStr)
        val newArray = JSONArray()
        for (i in 0 until jsonArray.length()) {
            try {
                val jsonObject = jsonArray.getJSONObject(i)
                if (jsonObject.getInt("notification_id") != id) {
                    newArray.put(jsonObject)
                }
            } catch (e: Exception) {
                // Skip invalid
            }
        }
        prefs.edit().putString(KEY_PENDING_NOTIFICATIONS, newArray.toString()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
