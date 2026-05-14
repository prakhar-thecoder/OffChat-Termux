package com.appholaworld.offchat.models

data class NearbyDevice(
    val id: String, // MAC address or UUID
    val name: String, // Device model name
    val userName: String? = null, // The user's chosen name
    val ipAddress: String? = null, // IP Address of the device
    val type: ConnectionType,
    var status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val rssi: Int = 0, // Real-time signal strength
    val metadata: Map<String, String> = emptyMap()
)

enum class ConnectionType {
    BLUETOOTH, WIFI
}

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, PENDING, REJECTED
}
