package com.appholaworld.offchat.utils

import android.content.Context
import android.content.SharedPreferences
import com.appholaworld.offchat.models.User

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "offchat_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_PROFILE_COMPLETED = "profile_completed"
        private const val KEY_TERMS_ACCEPTED = "terms_accepted"
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

    fun clear() {
        prefs.edit().clear().apply()
    }
}
