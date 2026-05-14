package com.appholaworld.offchat.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.termux.databinding.ActivitySplashBinding
import com.appholaworld.offchat.models.User
import com.appholaworld.offchat.utils.PreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var onlineChatRepository: com.appholaworld.offchat.repository.OnlineChatRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)
        onlineChatRepository = (application as com.appholaworld.offchat.OffChatApp).onlineChatRepository

        lifecycleScope.launch {
            delay(2000)
            checkAuthAndNavigate()
        }
    }

    private suspend fun checkAuthAndNavigate() {
        val bashFile = java.io.File("/data/data/com.termux/files/usr/bin/bash")
        if (!bashFile.exists()) {
            // Environment missing. Route to TermuxActivity for automated setup.
            val intent = android.content.Intent(this@SplashActivity, com.termux.app.TermuxActivity::class.java)
            intent.putExtra("OFFCHAT_SETUP_MODE", true)
            startActivity(intent)
            finish()
            return
        }

        try {
            val user = onlineChatRepository.getCurrentUser()
            // Fast path: Check local preferences first
            val isLocalProfileComplete = preferenceManager.getUser()?.isProfileCompleted == true

            if (user != null && isLocalProfileComplete) {
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
                return
            }

            if (user == null) {
                startActivity(Intent(this@SplashActivity, AuthActivity::class.java))
                finish()
                return
            }

            // Fallback: Check Firebase if local profile isn't marked complete
            val userData = onlineChatRepository.getUserData(user.uid)
            if (userData != null && userData["username"] != null) {
                // Profile complete remotely, save locally for next time
                val localUser = User(
                    id = user.uid,
                    name = userData["username"] as String,
                    phoneNumber = "",
                    deviceName = android.os.Build.MODEL,
                    isProfileCompleted = true
                )
                preferenceManager.saveUser(localUser)
                preferenceManager.setTermsAccepted(true)

                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            } else {
                startActivity(Intent(this@SplashActivity, AuthActivity::class.java))
                finish()
            }
        } catch (e: Exception) {
            // Fallback in case of network timeout or error to prevent hanging on Splash
            startActivity(Intent(this@SplashActivity, AuthActivity::class.java))
            finish()
        }
    }
}
