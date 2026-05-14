package com.appholaworld.offchat.fragments

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.appholaworld.offchat.adapters.WifiNetworkAdapter
import com.termux.databinding.FragmentWifiScanBinding
import com.appholaworld.offchat.viewmodels.WifiScanViewModel
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch

class WifiScanFragment : Fragment() {

    private var _binding: FragmentWifiScanBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WifiScanViewModel by viewModels()
    private lateinit var adapter: WifiNetworkAdapter
    private lateinit var wifiManager: WifiManager

    private var isInitialLoad = true
    private var pendingManualScan = false

    // Handle the native Location Settings popup result
    private val locationSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // User tapped "OK" on the native prompt. Proceed to check permissions and scan.
            checkPermissionsAndScan(isManual = pendingManualScan)
        } else {
            // User dismissed the prompt or tapped "No thanks"
            binding.tvEmptyState.text = "Device Location is off. Android requires it to scan Wi-Fi."
            viewModel.setRefreshing(false)
            isInitialLoad = false
        }
    }

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                scanSuccess()
            } else {
                scanFailure(isThrottled = false, isManual = false)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWifiScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        setupRecyclerView()
        setupSwipeRefresh()
        setupObservers()
    }

    private fun setupRecyclerView() {
        adapter = WifiNetworkAdapter()
        binding.rvWifiNetworks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWifiNetworks.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            startWifiScanFlow(isManual = true)
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.networks.collect { networks ->
                adapter.updateList(networks)
                binding.tvEmptyState.visibility = if (networks.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.isRefreshing.collect { isRefreshing ->
                binding.swipeRefresh.isRefreshing = isRefreshing
            }
        }
    }

    // Step 1: Entry point for scanning. Validates Wi-Fi state, then requests GPS check.
    private fun startWifiScanFlow(isManual: Boolean) {
        viewModel.setRefreshing(true)
        pendingManualScan = isManual

        if (!wifiManager.isWifiEnabled) {
            if (isManual || isInitialLoad) {
                Toast.makeText(requireContext(), "Please enable Wi-Fi to scan", Toast.LENGTH_SHORT).show()
            }
            binding.tvEmptyState.text = "Wi-Fi is disabled. Please turn it on."
            viewModel.setRefreshing(false)
            isInitialLoad = false
            return
        }

        // Check if GPS is enabled, if not, show the native Google prompt
        checkLocationSettings(isManual)
    }

    // Step 2: Uses Google Play Services to check GPS. Pops the native dialog if it's off.
    private fun checkLocationSettings(isManual: Boolean) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_LOW_POWER, 10000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(requireActivity())

        client.checkLocationSettings(builder.build()).addOnSuccessListener {
            // Location is ON. Move to permission check.
            checkPermissionsAndScan(isManual)
        }.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                // Location is OFF, but we can prompt the user to turn it on natively
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettingsLauncher.launch(intentSenderRequest)
                } catch (sendEx: IntentSender.SendIntentException) {
                    viewModel.setRefreshing(false)
                }
            } else {
                // Location is off and device doesn't support the automatic resolution
                if (isManual || isInitialLoad) {
                    Toast.makeText(requireContext(), "Please turn on Device Location (GPS).", Toast.LENGTH_LONG).show()
                }
                binding.tvEmptyState.text = "Device Location is off. Android requires it to scan Wi-Fi."
                viewModel.setRefreshing(false)
                isInitialLoad = false
            }
        }
    }

    // Step 3: GPS is on. Check app permissions. If granted, execute the hardware scan.
    private fun checkPermissionsAndScan(isManual: Boolean) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            binding.tvEmptyState.text = "Location permission is required to scan."
            if (isManual || isInitialLoad) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSIONS_REQUEST_CODE)
            } else {
                viewModel.setRefreshing(false)
            }
            return
        }

        // Everything is green. Reset text, load cached results, and execute hardware scan.
        binding.tvEmptyState.text = "No networks found. Swipe down to scan."

        val cachedResults = wifiManager.scanResults
        viewModel.updateNetworks(cachedResults)

        val success = wifiManager.startScan()
        if (!success) {
            scanFailure(isThrottled = true, isManual = isManual)
        }
        isInitialLoad = false
    }

    private fun scanSuccess() {
        val results = wifiManager.scanResults
        viewModel.updateNetworks(results)
        viewModel.setRefreshing(false)
    }

    private fun scanFailure(isThrottled: Boolean, isManual: Boolean) {
        val results = wifiManager.scanResults
        viewModel.updateNetworks(results)
        viewModel.setRefreshing(false)

        if (isManual && isThrottled) {
            Toast.makeText(requireContext(), "Scan rate-limited by Android. Showing cached results.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        requireContext().registerReceiver(wifiScanReceiver, intentFilter)

        // Trigger checks silently on tab open
        startWifiScanFlow(isManual = false)
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(wifiScanReceiver)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkPermissionsAndScan(isManual = pendingManualScan)
            } else {
                Toast.makeText(requireContext(), "Location permission required to scan Wi-Fi", Toast.LENGTH_SHORT).show()
                viewModel.setRefreshing(false)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 101
    }
}
