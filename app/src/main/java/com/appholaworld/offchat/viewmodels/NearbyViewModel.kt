package com.appholaworld.offchat.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.appholaworld.offchat.OffChatApp
import com.appholaworld.offchat.models.ConnectionStatus
import com.appholaworld.offchat.models.ConnectionType
import com.appholaworld.offchat.models.NearbyDevice
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NearbyViewModel(application: Application) : AndroidViewModel(application) {

    private val _nearbyDevices = MutableLiveData<List<NearbyDevice>>(emptyList())
    val nearbyDevices: LiveData<List<NearbyDevice>> = _nearbyDevices

    private val _isScanning = MutableLiveData<Boolean>(false)
    val isScanning: LiveData<Boolean> = _isScanning

    private val meshManager = (application as OffChatApp).meshManager
    val handshakeRequests: LiveData<Pair<String, String>> = meshManager.handshakeRequests.asLiveData()
    val connectionEvents = meshManager.connectionEvents

    private val connectionStatuses = mutableMapOf<String, ConnectionStatus>()

    init {
        viewModelScope.launch {
            meshManager.discoveredDevices.collectLatest { discoveredMap ->
                val myId = meshManager.localDeviceId
                val myIp = meshManager.getLocalVirtualIp()
                
                val devices = discoveredMap.mapNotNull { (uri, name) ->
                    val link = try {
                        com.ustadmobile.meshrabiya.vnet.MeshrabiyaConnectLink.parseUri(uri)
                    } catch (e: Exception) { null }
                    
                    val virtualIp = link?.virtualAddress?.addressToDotNotation() ?: return@mapNotNull null
                    val peerId = meshManager.getStableId(virtualIp)
                    
                    // SELF-FILTERING: Don't show myself
                    if (peerId == myId || virtualIp == myIp) return@mapNotNull null
                    
                    val status = connectionStatuses[peerId] ?: ConnectionStatus.DISCONNECTED
                    
                    NearbyDevice(
                        id = peerId,
                        name = name,
                        userName = name,
                        ipAddress = virtualIp,
                        type = ConnectionType.WIFI,
                        status = status,
                        rssi = -70,
                        metadata = mapOf("uri" to uri)
                    )
                }.distinctBy { it.id } // AGGRESSIVE DEDUPLICATION
                
                _nearbyDevices.postValue(devices.sortedBy { it.userName ?: it.name })
            }
        }

        viewModelScope.launch {
            meshManager.connectedDevices.collect { connectedIds ->
                // If a device is marked as CONNECTED but not in the set, mark it DISCONNECTED
                val iterator = connectionStatuses.entries.iterator()
                var changed = false
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.value == ConnectionStatus.CONNECTED && !meshManager.isPeerConnected(entry.key)) {
                        entry.setValue(ConnectionStatus.DISCONNECTED)
                        changed = true
                    }
                }
                if (changed) _nearbyDevices.postValue(_nearbyDevices.value)
            }
        }
        
        viewModelScope.launch {
            meshManager.connectionEvents.collect { (peerId, accepted) ->
                val status = if (accepted) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED
                setConnectionStatus(peerId, status)
            }
        }
    }

    fun startScan() {
        if (_isScanning.value == true) return
        _isScanning.value = true
        meshManager.clearDiscoveredDevices()
        meshManager.startHotspot()
        meshManager.startManualScan()
    }

    fun stopScan() {
        _isScanning.value = false
        meshManager.stopHotspot()
        meshManager.stopDiscovery()
    }

    fun sendHandshakeRequest(peerId: String) {
        connectionStatuses[peerId] = ConnectionStatus.CONNECTING
        viewModelScope.launch {
            meshManager.sendHandshakeRequest(peerId)
        }
    }

    fun acceptHandshake(peerId: String) {
        connectionStatuses[peerId] = ConnectionStatus.CONNECTED
        viewModelScope.launch {
            meshManager.acceptHandshake(peerId)
        }
    }

    fun rejectHandshake(peerId: String) {
        connectionStatuses[peerId] = ConnectionStatus.DISCONNECTED
        viewModelScope.launch {
            meshManager.rejectHandshake(peerId)
        }
    }

    private fun setConnectionStatus(peerId: String, status: ConnectionStatus) {
        connectionStatuses[peerId] = status
        // Trigger UI refresh
        _nearbyDevices.postValue(_nearbyDevices.value)
    }

    fun connectToNode(uri: String) {
        meshManager.connectToNode(uri)
    }

    fun disconnectFromNode(peerId: String) {
        // Handle disconnect logic if needed
        setConnectionStatus(peerId, ConnectionStatus.DISCONNECTED)
    }
}
