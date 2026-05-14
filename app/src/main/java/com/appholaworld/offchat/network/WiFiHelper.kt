package com.appholaworld.offchat.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.appholaworld.offchat.models.ConnectionType
import com.appholaworld.offchat.models.NearbyDevice

class WiFiHelper(private val context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val SERVICE_TYPE = "_offchat._tcp."
    
    interface DiscoveryListener {
        fun onDeviceFound(device: NearbyDevice)
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d("WiFiHelper", "Service discovery started")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            if (service.serviceType == SERVICE_TYPE) {
                val device = NearbyDevice(
                    id = service.serviceName,
                    name = service.serviceName,
                    type = ConnectionType.WIFI
                )
                // In real app, we would resolve the service to get IP
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {}
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
    }

    fun startDiscovery() {
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        nsdManager.stopServiceDiscovery(discoveryListener)
    }
}
