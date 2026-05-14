package com.appholaworld.offchat.utils

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.appholaworld.offchat.models.ConnectionType
import com.appholaworld.offchat.models.NearbyDevice

class WiFiHelper(private val context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val SERVICE_TYPE = "_offchat._tcp."
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startAdvertising(userName: String) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = userName
            serviceType = SERVICE_TYPE
            port = 8888 // Fixed port for OffChat
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d("WiFiHelper", "Service registered: ${NsdServiceInfo.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    fun startDiscovery(localUserName: String, onDeviceFound: (NearbyDevice) -> Unit) {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("WiFiHelper", "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                // Filter out self
                if (service.serviceName == localUserName) {
                    Log.d("WiFiHelper", "Skipping self discovery: ${service.serviceName}")
                    return
                }

                if (service.serviceType == SERVICE_TYPE) {
                    val device = NearbyDevice(
                        id = service.host?.hostAddress ?: service.serviceName,
                        name = "WiFi Device",
                        userName = service.serviceName,
                        ipAddress = service.host?.hostAddress,
                        type = ConnectionType.WIFI
                    )
                    onDeviceFound(device)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        registrationListener?.let { nsdManager.unregisterService(it) }
    }
}
