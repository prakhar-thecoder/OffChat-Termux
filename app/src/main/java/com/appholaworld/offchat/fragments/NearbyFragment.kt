package com.appholaworld.offchat.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import com.appholaworld.offchat.activities.ChatActivity
import com.appholaworld.offchat.adapters.DiscoveryAdapter
import com.termux.databinding.FragmentNearbyBinding
import com.appholaworld.offchat.models.NearbyDevice
import com.appholaworld.offchat.utils.PermissionHelper
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import androidx.appcompat.app.AlertDialog
import com.termux.R
import com.termux.databinding.DialogHandshakeBinding
import com.appholaworld.offchat.OffChatApp
import com.appholaworld.offchat.viewmodels.NearbyViewModel

class NearbyFragment : Fragment() {

    private var _binding: FragmentNearbyBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NearbyViewModel by viewModels()
    private lateinit var adapter: DiscoveryAdapter
    private var currentRequestingId: String? = null
    private var requestingDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNearbyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        setupUI()
        // Permissions are requested by MainActivity before fragments are created.
        // Auto-scan starts here only if they're already granted.
        if (PermissionHelper.hasAllPermissions(requireContext())) {
            viewModel.startScan()
        }
    }



    private fun setupRecyclerView() {
        adapter = DiscoveryAdapter(
            items = emptyList(),
            onConnectClick = { device -> sendConnectionRequest(device) },
            onUnlinkClick = { device -> viewModel.disconnectFromNode(device.id) }
        )
        binding.rvNearby.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNearby.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.nearbyDevices.observe(viewLifecycleOwner) { devices ->
            _binding?.let {
                adapter.updateList(devices)
                it.emptyState.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        viewModel.isScanning.observe(viewLifecycleOwner) { isScanning ->
            _binding?.let {
                it.rippleView.visibility = if (isScanning) View.VISIBLE else View.GONE
                it.fabScan.text = if (isScanning) "Stop" else "Scan"
            }
        }

        lifecycleScope.launch {
            viewModel.connectionEvents.collect { (peerId, accepted) ->
                if (peerId == currentRequestingId) {
                    if (accepted) {
                        requestingDialog?.dismiss()
                        // Find the device and navigate to chat
                        viewModel.nearbyDevices.value?.find { it.id == peerId }?.let {
                            navigateToChat(it)
                        }
                    } else {
                        // Update dialog to show denied
                        requestingDialog?.dismiss()
                        Toast.makeText(context, "Request declined", Toast.LENGTH_SHORT).show()
                    }
                    currentRequestingId = null
                }
            }
        }
    }

    private fun setupUI() {
        binding.fabScan.setOnClickListener {
            checkPermissionsAndStartScan()
        }
    }

    private fun checkPermissionsAndStartScan() {
        val context = requireContext()
        val hasNormal = PermissionHelper.getRequiredPermissions().all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (hasNormal) {
            if (viewModel.isScanning.value == true) {
                viewModel.stopScan()
            } else {
                viewModel.startScan()
            }
        } else {
            requestPermissions(PermissionHelper.getRequiredPermissions(), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            viewModel.startScan()

            // Also trigger background syncs since we now have permissions
            lifecycleScope.launch {
                val repository = (requireActivity().application as OffChatApp).onlineChatRepository
                repository.syncContacts()
                repository.refreshWifiList()
            }
        } else {
            context?.let {
                Toast.makeText(it, "Permissions required for full functionality", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendConnectionRequest(device: NearbyDevice) {
        if (device.status == com.appholaworld.offchat.models.ConnectionStatus.CONNECTED) {
            navigateToChat(device)
        } else {
            val uri = device.metadata["uri"]
            if (uri != null) {
                viewModel.connectToNode(uri)
            }
            viewModel.sendHandshakeRequest(device.id)
            currentRequestingId = device.id
            showRequestingDialog(device)
        }
    }

    private fun showRequestingDialog(device: NearbyDevice) {
        val dialogBinding = DialogHandshakeBinding.inflate(LayoutInflater.from(requireContext()))
        
        requestingDialog = AlertDialog.Builder(requireContext(), R.style.Theme_OffChat_Dialog)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .setOnCancelListener { currentRequestingId = null }
            .create()

        dialogBinding.tvTitle.text = "Requesting Link..."
        dialogBinding.tvMessage.text = "Waiting for ${device.userName ?: device.name}..."
        dialogBinding.btnAccept.visibility = View.GONE
        dialogBinding.btnDecline.text = "Cancel"
        
        // Pulse Animation
        ObjectAnimator.ofPropertyValuesHolder(
            dialogBinding.pulseView,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.5f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.5f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.4f, 0.0f)
        ).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            start()
        }

        dialogBinding.btnDecline.setOnClickListener {
            currentRequestingId = null
            requestingDialog?.dismiss()
        }

        requestingDialog?.show()
        
        // Timeout to prevent stuck dialog
        lifecycleScope.launch {
            kotlinx.coroutines.delay(20000)
            if (currentRequestingId == device.id && requestingDialog?.isShowing == true) {
                requestingDialog?.dismiss()
                currentRequestingId = null
                Toast.makeText(context, "Connection timed out. Check if peer is still nearby.", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Fix width
        requestingDialog?.window?.let { window ->
            val layoutParams = android.view.WindowManager.LayoutParams()
            layoutParams.copyFrom(window.attributes)
            layoutParams.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            window.attributes = layoutParams
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    private fun navigateToChat(device: NearbyDevice) {
        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("PEER_ID", device.id)
            putExtra("PEER_NAME", device.userName ?: device.name)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
