package com.appholaworld.offchat.activities

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.appholaworld.offchat.OffChatApp
import com.termux.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class BaseActivity : AppCompatActivity() {

    val meshManager by lazy { (application as OffChatApp).meshManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeHandshakeRequests()
        setStatusBarColor(R.color.background)
    }

    protected fun setStatusBarColor(colorRes: Int) {
        window.statusBarColor = getColor(colorRes)
    }

    private var isHandshakeDialogShowing = false

    private fun observeHandshakeRequests() {
        lifecycleScope.launch {
            meshManager.handshakeRequests.collectLatest { (peerId, peerName) ->
                if (!isHandshakeDialogShowing) {
                    showHandshakeDialog(peerId, peerName)
                }
            }
        }
    }

    private fun showHandshakeDialog(peerId: String, peerName: String) {
        isHandshakeDialogShowing = true
        val dialogView = layoutInflater.inflate(R.layout.dialog_handshake, null)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvMessage)
        val btnAccept = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAccept)
        val btnDecline = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDecline)

        tvMessage.text = peerName

        val pulseView = dialogView.findViewById<android.view.View>(R.id.pulseView)
        pulseView.animate()
            .scaleX(1.4f)
            .scaleY(1.4f)
            .alpha(0f)
            .setDuration(1500)
            .withEndAction {
                pulseView.scaleX = 1f
                pulseView.scaleY = 1f
                pulseView.alpha = 0.2f
                // We'll restart it in a real app, but for now a simple one-shot or repeating
            }
            .start()

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Apply rounded corners and transparent background if needed, though MaterialCardView in layout might handle it
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnAccept.setOnClickListener {
            dialog.dismiss()
            isHandshakeDialogShowing = false
            lifecycleScope.launch {
                meshManager.acceptHandshake(peerId)
                navigateToChat(peerId, peerName)
            }
        }

        btnDecline.setOnClickListener {
            dialog.dismiss()
            isHandshakeDialogShowing = false
            lifecycleScope.launch {
                meshManager.rejectHandshake(peerId)
            }
        }

        dialog.setOnCancelListener { isHandshakeDialogShowing = false }

        if (!isFinishing && !isDestroyed) {
            dialog.show()
        }
    }

    private fun navigateToChat(peerId: String, peerName: String) {
        if (this is ChatActivity) return // Already in chat

        val intent = android.content.Intent(this, ChatActivity::class.java).apply {
            putExtra("PEER_ID", peerId)
            putExtra("PEER_NAME", peerName)
        }
        startActivity(intent)
    }
}
