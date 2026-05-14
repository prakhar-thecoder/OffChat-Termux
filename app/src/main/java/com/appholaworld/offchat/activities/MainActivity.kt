package com.appholaworld.offchat.activities

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.termux.R
import com.termux.databinding.ActivityMainBinding
import com.termux.databinding.DialogHandshakeBinding
import com.appholaworld.offchat.utils.PreferenceManager
import com.appholaworld.offchat.viewmodels.ChatListViewModel
import com.appholaworld.offchat.viewmodels.NearbyViewModel
import com.appholaworld.offchat.viewmodels.OnlineChatViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import com.appholaworld.offchat.OffChatApp

class MainActivity : BaseActivity() {

    private val onlineViewModel: OnlineChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = (application as OffChatApp).onlineChatRepository
                return OnlineChatViewModel(repository) as T
            }
        }
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var preferenceManager: PreferenceManager
    private val chatListViewModel: ChatListViewModel by viewModels()
    private val nearbyViewModel: NearbyViewModel by viewModels()
    private var isHandshakeDialogShowing = false
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onlineViewModel.hashCode()
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(onlineViewModel)

        preferenceManager = PreferenceManager(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        startOffChatService()
        setupNavigation()
        setupPreferenceListener()
        setupUnreadBadge()
        setupHandshakeObserver()
    }

    private fun startOffChatService() {
        val intent = Intent(this, com.appholaworld.offchat.network.OffChatService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateBottomNavVisibility(destination.id)
        }
    }

    private fun setupPreferenceListener() {
        val prefs = getSharedPreferences("offchat_prefs", android.content.Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == "profile_completed") {
                updateBottomNavVisibility(navController.currentDestination?.id ?: -1)
            }
        }
    }

    private fun updateBottomNavVisibility(destinationId: Int) {
        if (!preferenceManager.isProfileCompleted()) {
            binding.bottomNav.visibility = View.GONE
            if (destinationId != R.id.nav_account && destinationId != -1) {
                navController.navigate(R.id.nav_account)
            }
        } else {
            binding.bottomNav.visibility = View.VISIBLE
        }
    }

    private fun setupUnreadBadge() {
        chatListViewModel.totalUnreadCount.observe(this) { count ->
            val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_chats)
            if (count != null && count > 0) {
                badge.isVisible = true
                badge.number = count
            } else {
                badge.isVisible = false
            }
        }
    }

    private fun setupHandshakeObserver() {
        nearbyViewModel.handshakeRequests.observe(this) { (peerId, name) ->
            if (!isHandshakeDialogShowing) {
                showPremiumHandshakeDialog(peerId, name)
            }
        }
    }

    private fun showPremiumHandshakeDialog(peerId: String, name: String) {
        isHandshakeDialogShowing = true
        val dialogBinding = DialogHandshakeBinding.inflate(LayoutInflater.from(this))
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_OffChat_Dialog)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialogBinding.tvMessage.text = name

        // Pulse Animation
        val pulse = ObjectAnimator.ofPropertyValuesHolder(
            dialogBinding.pulseView,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.5f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.5f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.4f, 0.0f)
        ).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            start()
        }

        // INFINITE VIBRATION (Loop until dismissed)
        val pattern = longArrayOf(0, 500, 200, 500, 200) // Call-like pattern
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 1)) // 1 means repeat from index 1
        } else {
            vibrator?.vibrate(pattern, 1)
        }

        val cleanup = {
            vibrator?.cancel()
            pulse.cancel()
            isHandshakeDialogShowing = false
            dialog.dismiss()
        }

        dialogBinding.btnAccept.setOnClickListener {
            nearbyViewModel.acceptHandshake(peerId)
            cleanup()
            
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("PEER_ID", peerId)
                putExtra("PEER_NAME", name)
            }
            startActivity(intent)
        }

        dialogBinding.btnDecline.setOnClickListener {
            nearbyViewModel.rejectHandshake(peerId)
            cleanup()
        }

        dialog.show()

        // FORCE WIDTH (90% of screen)
        val layoutParams = WindowManager.LayoutParams()
        layoutParams.copyFrom(dialog.window?.attributes)
        val displayMetrics = resources.displayMetrics
        layoutParams.width = (displayMetrics.widthPixels * 0.9).toInt()
        dialog.window?.attributes = layoutParams
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onDestroy() {
        super.onDestroy()
        vibrator?.cancel()
    }
}
