package com.appholaworld.offchat.viewmodels

import android.net.wifi.ScanResult
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiScanViewModel : ViewModel() {

    private val _networks = MutableStateFlow<List<ScanResult>>(emptyList())
    val networks: StateFlow<List<ScanResult>> = _networks.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun updateNetworks(newNetworks: List<ScanResult>) {
        _networks.value = newNetworks
        _isRefreshing.value = false
    }

    fun setRefreshing(refreshing: Boolean) {
        _isRefreshing.value = refreshing
    }
}
