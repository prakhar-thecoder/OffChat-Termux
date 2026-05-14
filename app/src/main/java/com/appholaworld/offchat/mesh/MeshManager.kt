package com.appholaworld.offchat.mesh

import android.content.Context
import android.util.Log
import com.appholaworld.offchat.OffChatApp
import com.appholaworld.offchat.models.Message
import com.appholaworld.offchat.models.MessageStatus
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.LocalNodeState
import com.ustadmobile.meshrabiya.vnet.MeshrabiyaConnectLink
import com.ustadmobile.meshrabiya.vnet.wifi.ConnectBand
import com.ustadmobile.meshrabiya.vnet.wifi.WifiConnectConfig
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import com.appholaworld.offchat.utils.PreferenceManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json as KJson

@Serializable
data class MeshPacket(
    val type: String, // CHAT, HANDSHAKE_REQ, HANDSHAKE_ACCEPT, HANDSHAKE_REJECT, ACK, FILE_HEADER, GROUP_CHAT, CALL_OFFER, CALL_ANSWER, CALL_ICE, CALL_HANGUP, CALL_REJECT
    val senderName: String,
    val deviceId: String,
    val senderAddress: String = "",
    val payload: String = "",
    val messageId: String = "",
    val fileName: String? = null,
    val fileSize: Long = 0,
    val isGroup: Boolean = false
)

class MeshManager(private val context: Context) {

    private val virtualNode: AndroidVirtualNode = (context.applicationContext as OffChatApp).virtualNode
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val preferenceManager = PreferenceManager(context)
    private val packetJson = KJson { ignoreUnknownKeys = true }
    
    private val _incomingMessages = MutableSharedFlow<Pair<String, Message>>()
    val incomingMessages: SharedFlow<Pair<String, Message>> = _incomingMessages

    private val _handshakeRequests = MutableSharedFlow<Pair<String, String>>()
    val handshakeRequests: SharedFlow<Pair<String, String>> = _handshakeRequests

    private val _connectionEvents = MutableSharedFlow<Pair<String, Boolean>>()
    val connectionEvents: SharedFlow<Pair<String, Boolean>> = _connectionEvents
    
    private val _fileTransferProgress = MutableSharedFlow<Triple<String, String, Int>>() // peerId, fileName, progress
    val fileTransferProgress: SharedFlow<Triple<String, String, Int>> = _fileTransferProgress

    private val _callSignals = MutableSharedFlow<MeshPacket>()
    val callSignals: SharedFlow<MeshPacket> = _callSignals
    
    private val _connectedDevices = MutableStateFlow<Set<String>>(emptySet())
    val connectedDevices: StateFlow<Set<String>> = _connectedDevices

    private val deviceIdToIp = ConcurrentHashMap<String, String>()
    private val deviceIdToPort = ConcurrentHashMap<String, Int>()
    private val ipToDeviceId = ConcurrentHashMap<String, String>()
    val localDeviceId: String by lazy {
        android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
    }

    private var serverSocket: ServerSocket? = null
    private val activeConnections = ConcurrentHashMap<String, Socket>()
    private val processedMessages = ConcurrentHashMap.newKeySet<String>() // Relay Guard Cache
    
    private var CHAT_PORT = 8888

    private val nsdManager: android.net.nsd.NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? android.net.nsd.NsdManager
    }
    private var nsdRegistrationListener: android.net.nsd.NsdManager.RegistrationListener? = null
    private var nsdDiscoveryListener: android.net.nsd.NsdManager.DiscoveryListener? = null

    private val p2pManager: android.net.wifi.p2p.WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? android.net.wifi.p2p.WifiP2pManager
    }
    private var p2pChannel: android.net.wifi.p2p.WifiP2pManager.Channel? = null
    private val discoveredUris = ConcurrentHashMap.newKeySet<String>()
    private val nodeNames = ConcurrentHashMap<String, String>()
    private val physicalAddresses = ConcurrentHashMap<String, String>()
    private val _discoveredDevices = MutableStateFlow<Map<String, String>>(emptyMap())
    val discoveredDevices: StateFlow<Map<String, String>> = _discoveredDevices

    fun getStableId(address: String): String {
        return ipToDeviceId[address] ?: deviceIdToIp.entries.find { it.value == address }?.key ?: address
    }

    fun getIpForDeviceId(deviceId: String): String? {
        return deviceIdToIp[deviceId]
    }

    fun isPeerConnected(peerIdOrIp: String): Boolean {
        if (activeConnections.containsKey(peerIdOrIp)) {
            val socket = activeConnections[peerIdOrIp]
            if (socket != null && !socket.isClosed && socket.isConnected) return true
        }
        
        val resolvedIp = deviceIdToIp[peerIdOrIp]
        if (resolvedIp != null && activeConnections.containsKey(resolvedIp)) {
            val socket = activeConnections[resolvedIp]
            if (socket != null && !socket.isClosed && socket.isConnected) return true
        }
        
        val resolvedId = ipToDeviceId[peerIdOrIp]
        if (resolvedId != null && activeConnections.containsKey(resolvedId)) {
            val socket = activeConnections[resolvedId]
            if (socket != null && !socket.isClosed && socket.isConnected) return true
        }
        
        return false
    }

    fun getNodeName(id: String): String? {
        return nodeNames[id] ?: nodeNames[getStableId(id)]
    }

    fun getLocalVirtualIp(): String {
        return virtualNode.addressAsInt.addressToDotNotation()
    }

    init {
        startServer()
        startAutoDiscovery()
        checkSavedConnections()
    }

    private fun checkSavedConnections() {
        scope.launch {
            try {
                val app = context.applicationContext as? OffChatApp
                val repository = app?.chatRepository
                repository?.allChatSessions?.collect { sessions ->
                    sessions.forEach { session ->
                        val ip = session.lastIpAddress
                        if (ip != null && !activeConnections.containsKey(ip)) {
                            Log.d("MeshManager", "Saved session found for $ip")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MeshManager", "Error checking saved connections", e)
            }
        }
    }

    private fun startAutoDiscovery() {
        Log.d("MeshManager", "Auto-discovery loop disabled. Manual discovery only.")
    }

    fun startManualScan() {
        scope.launch {
            stopDiscovery()
            p2pChannel = p2pManager?.initialize(context, context.mainLooper, null)
            
            var uri: String? = null
            for (i in 1..10) {
                uri = getConnectUri()
                if (uri != null && (uri.contains("hotspot=") || uri.contains("bluetooth="))) break
                delay(1000)
            }
            
            if (uri != null) advertiseSelf()
            discoverPeers()
        }
    }

    fun stopDiscovery() {
        nsdDiscoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        nsdRegistrationListener?.let { nsdManager?.unregisterService(it) }
        nsdDiscoveryListener = null
        nsdRegistrationListener = null
        p2pChannel?.let { channel -> p2pManager?.clearServiceRequests(channel, null) }
    }

    fun clearDiscoveredDevices() {
        discoveredUris.clear()
        _discoveredDevices.value = emptyMap()
    }

    private suspend fun advertiseSelf() {
        val uri = getConnectUri() ?: return
        if (!uri.contains("hotspot=") && !uri.contains("bluetooth=")) return
        advertiseViaP2P(uri)
        advertiseViaNsd(uri)
    }

    private fun advertiseViaP2P(uri: String) {
        val p2pManager = p2pManager ?: return
        val channel = p2pChannel ?: return
        val record = mutableMapOf<String, String>()
        record["name"] = preferenceManager.getUser()?.name ?: android.os.Build.MODEL
        record["id"] = localDeviceId
        val chunks = uri.chunked(150)
        chunks.forEachIndexed { index, chunk -> record["u$index"] = chunk }
        record["cnt"] = chunks.size.toString()

        val serviceInfo = android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo.newInstance(
            "OffChat-${virtualNode.addressAsInt}",
            "_meshr._tcp",
            record
        )

        p2pManager.clearLocalServices(channel, object : android.net.wifi.p2p.WifiP2pManager.ActionListener {
            override fun onSuccess() {
                p2pManager.addLocalService(channel, serviceInfo, null)
            }
            override fun onFailure(reason: Int) {}
        })
    }

    private fun advertiseViaNsd(uri: String) {
        val nsdManager = nsdManager ?: return
        if (nsdRegistrationListener != null) return

        val serviceInfo = android.net.nsd.NsdServiceInfo().apply {
            serviceName = "OffChat-${virtualNode.addressAsInt}"
            serviceType = "_offchat._tcp"
            port = CHAT_PORT
            setAttribute("name", preferenceManager.getUser()?.name ?: android.os.Build.MODEL)
            setAttribute("id", localDeviceId)
            val chunks = uri.chunked(150)
            chunks.forEachIndexed { index, chunk -> setAttribute("u$index", chunk) }
            setAttribute("cnt", chunks.size.toString())
        }

        nsdRegistrationListener = object : android.net.nsd.NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: android.net.nsd.NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: android.net.nsd.NsdServiceInfo, errorCode: Int) { nsdRegistrationListener = null }
            override fun onServiceUnregistered(arg0: android.net.nsd.NsdServiceInfo) { nsdRegistrationListener = null }
            override fun onUnregistrationFailed(serviceInfo: android.net.nsd.NsdServiceInfo, errorCode: Int) {}
        }

        try {
            nsdManager.registerService(serviceInfo, android.net.nsd.NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener)
        } catch (e: Exception) {}
    }

    private fun discoverPeers() {
        discoverViaP2P()
        discoverViaNsd()
    }

    private fun discoverViaP2P() {
        val p2pManager = p2pManager ?: return
        val channel = p2pChannel ?: return

        p2pManager.setDnsSdResponseListeners(channel, { _, _, _ -> }, { _, txtRecordMap, srcDevice ->
            val count = txtRecordMap["cnt"]?.toIntOrNull() ?: 0
            if (count > 0) {
                val sb = StringBuilder()
                for (i in 0 until count) sb.append(txtRecordMap["u$i"] ?: "")
                val uri = sb.toString()
                val name = txtRecordMap["name"] ?: "P2P Device"
                val deviceId = txtRecordMap["id"]
                handleDiscoveredUri(uri, name, srcDevice.deviceAddress, deviceId)
            }
        })

        p2pManager.clearServiceRequests(channel, object : android.net.wifi.p2p.WifiP2pManager.ActionListener {
            override fun onSuccess() {
                val request = android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest.newInstance()
                p2pManager.addServiceRequest(channel, request, object : android.net.wifi.p2p.WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        p2pManager.discoverPeers(channel, null)
                        p2pManager.discoverServices(channel, null)
                    }
                    override fun onFailure(reason: Int) {}
                })
            }
            override fun onFailure(reason: Int) {}
        })
    }

    private fun discoverViaNsd() {
        val nsdManager = nsdManager ?: return
        if (nsdDiscoveryListener != null) return

        nsdDiscoveryListener = object : android.net.nsd.NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { nsdManager.stopServiceDiscovery(this) }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) { nsdDiscoveryListener = null }
            override fun onServiceFound(serviceInfo: android.net.nsd.NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("_offchat._tcp")) {
                    nsdManager.resolveService(serviceInfo, object : android.net.nsd.NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: android.net.nsd.NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(resolvedServiceInfo: android.net.nsd.NsdServiceInfo) {
                            val countStr = resolvedServiceInfo.attributes["cnt"]?.decodeToString()
                            if (countStr != null) {
                                try {
                                    val count = countStr.toInt()
                                    val sb = StringBuilder()
                                    for (i in 0 until count) sb.append(resolvedServiceInfo.attributes["u$i"]?.decodeToString() ?: "")
                                    val name = resolvedServiceInfo.attributes["name"]?.decodeToString() ?: resolvedServiceInfo.serviceName
                                    val deviceId = resolvedServiceInfo.attributes["id"]?.decodeToString()
                                    
                                    // Use host.hostAddress for now, it's the standard for this API
                                    val hostAddress = resolvedServiceInfo.host.hostAddress
                                    val port = resolvedServiceInfo.port
                                    handleDiscoveredUri(sb.toString(), name, hostAddress, deviceId, port)
                                } catch (e: Exception) {}
                            }
                        }
                    })
                }
            }
            override fun onServiceLost(serviceInfo: android.net.nsd.NsdServiceInfo) {}
        }
        nsdManager.discoverServices("_offchat._tcp.", android.net.nsd.NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener)
    }

    private fun handleDiscoveredUri(uri: String, deviceName: String, physicalIp: String? = null, deviceId: String? = null, port: Int = 8888) {
        val sanitizedUri = uri.trim().removeSuffix("?").removeSuffix("/")
        val link = try { MeshrabiyaConnectLink.parseUri(sanitizedUri) } catch (e: Exception) { null }
        val addrStr = link?.virtualAddress?.addressToDotNotation()
        if (addrStr == null || addrStr == getLocalVirtualIp()) return

        if (deviceId != null) {
            deviceIdToIp[deviceId] = addrStr
            deviceIdToPort[deviceId] = port
            ipToDeviceId[addrStr] = deviceId
            nodeNames[deviceId] = deviceName
        }
        nodeNames[addrStr] = deviceName
        if (physicalIp != null) physicalAddresses[addrStr] = physicalIp
        
        _discoveredDevices.update { it + (sanitizedUri to deviceName) }
        
        if (!discoveredUris.contains(sanitizedUri)) {
            discoveredUris.add(sanitizedUri)
            if (link.hotspotConfig != null) connectToNode(sanitizedUri)
        }
    }

     fun startServer() {
        scope.launch {
            var portFound = false
            for (p in 8888..8898) {
                try {
                    serverSocket = ServerSocket(p)
                    CHAT_PORT = p
                    portFound = true
                    break
                } catch (e: Exception) {}
            }
            if (!portFound) return@launch

            try {
                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    handleIncomingConnection(client)
                }
            } catch (e: Exception) {}
        }
    }

    private fun handleIncomingConnection(socket: Socket) {
        scope.launch {
            var currentPeerId: String? = null
            try {
                val inputStream = socket.getInputStream()
                val peerAddress = socket.inetAddress.hostAddress ?: "unknown"
                activeConnections[peerAddress] = socket
                _connectedDevices.value = activeConnections.keys.toSet()
                
                while (isActive) {
                    val line = readLineFromStream(inputStream) ?: break
                    try {
                        val packet = packetJson.decodeFromString<MeshPacket>(line)
                        
                        deviceIdToIp[packet.deviceId] = if (packet.senderAddress.isNotEmpty()) packet.senderAddress else peerAddress
                        ipToDeviceId[packet.senderAddress] = packet.deviceId
                        ipToDeviceId[peerAddress] = packet.deviceId
                        nodeNames[packet.deviceId] = packet.senderName
                        currentPeerId = packet.deviceId
                        
                        // Link the socket to the Device ID for reuse
                        activeConnections[packet.deviceId] = socket
                        _connectedDevices.value = activeConnections.keys.toSet()

                        when (packet.type) {
                            "CHAT" -> {
                                val message = Message(
                                    chatId = packet.deviceId,
                                    senderId = packet.deviceId,
                                    content = packet.payload,
                                    isIncoming = true
                                )
                                _incomingMessages.emit(packet.deviceId to message)
                                sendAck(packet.deviceId, packet.messageId)
                                if (packet.isGroup) relayGroupPacket(packet)
                            }
                            "FILE_HEADER" -> {
                                handleIncomingFile(inputStream, packet)
                            }
                            "GROUP_CHAT" -> {
                                if (processedMessages.add(packet.messageId)) {
                                    val message = Message(
                                        chatId = "PUBLIC_SQUARE",
                                        senderId = "${packet.senderName} (${packet.senderAddress})",
                                        content = packet.payload,
                                        isIncoming = true,
                                        isGroup = true
                                    )
                                    _incomingMessages.emit("PUBLIC_SQUARE" to message)
                                    relayGroupPacket(packet, excludeIp = peerAddress)
                                }
                            }
                            "ACK" -> {
                                val messageId = packet.messageId.toLongOrNull()
                                if (messageId != null) {
                                    (context.applicationContext as? OffChatApp)?.chatRepository?.updateMessageStatus(
                                        messageId, MessageStatus.DELIVERED
                                    )
                                }
                            }
                            "HANDSHAKE_REQ" -> _handshakeRequests.emit(packet.deviceId to packet.senderName)
                            "HANDSHAKE_ACCEPT" -> _connectionEvents.emit(packet.deviceId to true)
                            "HANDSHAKE_REJECT" -> _connectionEvents.emit(packet.deviceId to false)
                            "CALL_OFFER", "CALL_ANSWER", "CALL_ICE", "CALL_HANGUP", "CALL_REJECT" -> {
                                _callSignals.emit(packet)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MeshManager", "Error parsing packet: $line", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("MeshManager", "Connection error", e)
            } finally {
                val peerAddress = socket.inetAddress.hostAddress
                if (peerAddress != null) activeConnections.remove(peerAddress)
                currentPeerId?.let { activeConnections.remove(it) }
                _connectedDevices.value = activeConnections.keys.toSet()
                socket.close()
            }
        }
    }

    private fun readLineFromStream(inputStream: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var b: Int
        try {
            while (inputStream.read().also { b = it } != -1) {
                if (b == '\n'.toInt()) break
                baos.write(b)
            }
            if (b == -1 && baos.size() == 0) return null
            return baos.toString("UTF-8")
        } catch (e: Exception) {
            return null
        }
    }

    private suspend fun getOrCreateSocket(peerAddress: String): Socket {
        // Try direct lookup (either ID or IP)
        activeConnections[peerAddress]?.let { if (!it.isClosed && it.isConnected) return it }
        
        // Try resolved IP lookup if peerAddress is an ID
        val resolvedIp = deviceIdToIp[peerAddress]
        if (resolvedIp != null) {
            activeConnections[resolvedIp]?.let { if (!it.isClosed && it.isConnected) return it }
        }
        
        var lastError: Exception? = null
        for (i in 0 until 5) {
            try {
                val socket = withContext(Dispatchers.IO) { createClientSocket(peerAddress) }
                activeConnections[peerAddress] = socket
                _connectedDevices.value = activeConnections.keys.toSet()
                handleIncomingConnection(socket)
                return socket
            } catch (e: Exception) {
                lastError = e
                delay((Math.pow(2.0, i.toDouble()) * 1000).toLong())
            }
        }
        throw lastError ?: Exception("Connection failed")
    }

    private suspend fun sendAck(peerAddress: String, originalMessageId: String) {
        withContext(Dispatchers.IO) {
            try {
                val socket = getOrCreateSocket(peerAddress)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val packet = MeshPacket(
                    type = "ACK",
                    senderName = preferenceManager.getUser()?.name ?: android.os.Build.MODEL,
                    deviceId = localDeviceId,
                    senderAddress = getLocalVirtualIp(),
                    messageId = originalMessageId
                )
                writer.println(packetJson.encodeToString(packet))
            } catch (e: Exception) {}
        }
    }

    suspend fun sendMessage(peerAddress: String, content: String, timestamp: Long, messageId: Long? = null) {
        withContext(Dispatchers.IO) {
            try {
                val dbId = messageId ?: (context.applicationContext as? OffChatApp)?.chatRepository?.getMessageIdByTimestamp(peerAddress, timestamp)
                val socket = getOrCreateSocket(peerAddress)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val packet = MeshPacket(
                    type = "CHAT",
                    senderName = preferenceManager.getUser()?.name ?: android.os.Build.MODEL,
                    deviceId = localDeviceId,
                    senderAddress = getLocalVirtualIp(),
                    payload = content,
                    messageId = dbId?.toString() ?: ""
                )
                writer.println(packetJson.encodeToString(packet))
            } catch (e: Exception) {
                activeConnections.remove(peerAddress)
            }
        }
    }


    private fun createClientSocket(peerAddress: String): Socket {
        val resolvedAddress = deviceIdToIp[peerAddress] ?: peerAddress
        val ip = if (resolvedAddress.contains("://")) {
            java.net.URI.create(resolvedAddress).host
        } else {
            physicalAddresses[resolvedAddress] ?: resolvedAddress
        }
        
        val destAddr = InetAddress.getByName(ip)
        val targetPort = deviceIdToPort[peerAddress] ?: CHAT_PORT
        return if (ip.startsWith("169.254")) {
            virtualNode.socketFactory.createSocket(destAddr, targetPort)
        } else {
            Socket(destAddr, targetPort)
        }
    }

    private fun relayGroupPacket(packet: MeshPacket) {
        scope.launch {
            activeConnections.forEach { (ip, socket) ->
                if (ip != packet.senderAddress) {
                    try {
                        val writer = PrintWriter(socket.getOutputStream(), true)
                        writer.println(packetJson.encodeToString(packet))
                    } catch (e: Exception) {}
                }
            }
        }
    }

    private fun relayGroupPacket(packet: MeshPacket, excludeIp: String? = null) {
        // Relay Guard: Never relay the same message twice
        if (packet.messageId.isNotEmpty()) {
            processedMessages.add(packet.messageId)
        }

        scope.launch {
            activeConnections.forEach { (ip, socket) ->
                // Don't relay back to the sender (physical IP check)
                if (ip != excludeIp && ip != packet.senderAddress) {
                    try {
                        val writer = PrintWriter(socket.getOutputStream(), true)
                        writer.println(packetJson.encodeToString(packet))
                    } catch (e: Exception) {
                        Log.e("MeshManager", "Relay failed to $ip")
                    }
                }
            }
        }
    }

    private suspend fun handleIncomingFile(inputStream: InputStream, header: MeshPacket) {
        withContext(Dispatchers.IO) {
            try {
                val fileName = header.fileName ?: "received_file_${System.currentTimeMillis()}"
                val file = File(context.getExternalFilesDir(null), fileName)
                
                // Emit initial message to UI so progress bar shows up
                val initialMessage = Message(
                    chatId = header.deviceId,
                    senderId = header.deviceId,
                    content = "Incoming file: $fileName",
                    isIncoming = true,
                    fileName = fileName,
                    fileSize = header.fileSize,
                    progress = 0
                )
                _incomingMessages.emit(header.deviceId to initialMessage)
                sendAck(header.deviceId, header.messageId)

                val outputStream = FileOutputStream(file)
                val buffer = ByteArray(8192)
                var totalRead: Long = 0
                
                while (totalRead < header.fileSize) {
                    val toRead = Math.min(buffer.size.toLong(), header.fileSize - totalRead).toInt()
                    val bytesRead = inputStream.read(buffer, 0, toRead)
                    if (bytesRead == -1) break
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    
                    val progress = ((totalRead * 100) / header.fileSize).toInt()
                    _fileTransferProgress.emit(Triple(header.deviceId, fileName, progress))
                }
                outputStream.flush()
                outputStream.close()
                
                Log.d("MeshManager", "File transfer complete: $fileName")
                
                // Final update with file URI
                val completedMessage = Message(
                    chatId = header.deviceId,
                    senderId = header.deviceId,
                    content = "File received: $fileName",
                    isIncoming = true,
                    fileUri = file.absolutePath,
                    fileName = fileName,
                    fileSize = header.fileSize,
                    progress = 100
                )
                _incomingMessages.emit(header.deviceId to completedMessage)
            } catch (e: Exception) {
                Log.e("MeshManager", "File receive error", e)
            }
        }
    }

    suspend fun sendFile(peerAddress: String, file: File, messageId: Long? = null) {
        withContext(Dispatchers.IO) {
            try {
                val dbId = messageId ?: (context.applicationContext as? OffChatApp)?.chatRepository?.getMessageIdByTimestamp(peerAddress, System.currentTimeMillis()) // Approximation
                val socket = getOrCreateSocket(peerAddress)
                val outputStream = socket.getOutputStream()
                val writer = PrintWriter(outputStream, true)
                
                val header = MeshPacket(
                    type = "FILE_HEADER",
                    senderName = preferenceManager.getUser()?.name ?: android.os.Build.MODEL,
                    deviceId = localDeviceId,
                    fileName = file.name,
                    fileSize = file.length(),
                    messageId = dbId?.toString() ?: ""
                )
                writer.println(packetJson.encodeToString(header))
                
                val inputStream = FileInputStream(file)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalSent: Long = 0
                
                // Update local DB for sender
                val app = context.applicationContext as? OffChatApp
                val sentMessage = Message(
                    chatId = peerAddress,
                    senderId = "me",
                    content = "Sending file: ${file.name}",
                    isIncoming = false,
                    fileUri = file.absolutePath,
                    fileName = file.name,
                    fileSize = file.length(),
                    progress = 0
                )
                app?.chatRepository?.saveMessage(peerAddress, "Chat", sentMessage)

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalSent += bytesRead
                    val progress = ((totalSent * 100) / file.length()).toInt()
                    _fileTransferProgress.emit(Triple(peerAddress, file.name, progress))
                }
                
                // Mark as complete in DB
                sentMessage.progress = 100
                app?.chatRepository?.saveMessage(peerAddress, "Chat", sentMessage)
                
                outputStream.flush()
                inputStream.close()
            } catch (e: Exception) {
                Log.e("MeshManager", "File send error", e)
            }
        }
    }

    suspend fun broadcastGroupMessage(content: String) {
        withContext(Dispatchers.IO) {
            val messageId = "group_${System.currentTimeMillis()}_${localDeviceId.takeLast(4)}"
            val packet = MeshPacket(
                type = "GROUP_CHAT",
                senderName = preferenceManager.getUser()?.name ?: android.os.Build.MODEL,
                deviceId = localDeviceId,
                senderAddress = getLocalVirtualIp(),
                payload = content,
                messageId = messageId,
                isGroup = true
            )
            processedMessages.add(messageId)
            relayGroupPacket(packet)
            
            val message = Message(
                chatId = "PUBLIC_SQUARE",
                senderId = "me",
                content = content,
                isIncoming = false,
                isGroup = true
            )
            (context.applicationContext as? OffChatApp)?.chatRepository?.saveMessage("PUBLIC_SQUARE", "Public Square", message)
        }
    }

    suspend fun sendHandshakeRequest(peerAddress: String) {
        withContext(Dispatchers.IO) {
            try {
                val socket = getOrCreateSocket(peerAddress)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val packet = MeshPacket(
                    type = "HANDSHAKE_REQ",
                    senderName = preferenceManager.getUser()?.name ?: android.os.Build.MODEL,
                    deviceId = localDeviceId,
                    senderAddress = getLocalVirtualIp()
                )
                writer.println(packetJson.encodeToString(packet))
            } catch (e: Exception) {
                activeConnections.remove(peerAddress)
            }
        }
    }

    suspend fun acceptHandshake(peerAddress: String) {
        withContext(Dispatchers.IO) {
            try {
                val socket = getOrCreateSocket(peerAddress)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val packet = MeshPacket(
                    type = "HANDSHAKE_ACCEPT",
                    senderName = preferenceManager.getUser()?.name ?: android.os.Build.MODEL,
                    deviceId = localDeviceId,
                    senderAddress = getLocalVirtualIp()
                )
                writer.println(packetJson.encodeToString(packet))
            } catch (e: Exception) {
                activeConnections.remove(peerAddress)
            }
        }
    }

    suspend fun rejectHandshake(peerAddress: String) {
        withContext(Dispatchers.IO) {
            try {
                val socket = getOrCreateSocket(peerAddress)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val packet = MeshPacket(
                    type = "HANDSHAKE_REJECT",
                    senderName = preferenceManager.getUser()?.name ?: android.os.Build.MODEL,
                    deviceId = localDeviceId,
                    senderAddress = getLocalVirtualIp()
                )
                writer.println(packetJson.encodeToString(packet))
            } catch (e: Exception) {
                activeConnections.remove(peerAddress)
            }
        }
    }

    fun startHotspot() {
        scope.launch { virtualNode.setWifiHotspotEnabled(true, ConnectBand.BAND_2GHZ) }
    }

    fun stopHotspot() {
        scope.launch { virtualNode.setWifiHotspotEnabled(false, ConnectBand.BAND_2GHZ) }
    }

    fun connectToNode(uri: String) {
        try {
            val sanitizedUri = uri.trim().removeSuffix("?").removeSuffix("/")
            val link = MeshrabiyaConnectLink.parseUri(sanitizedUri)
            val connectConfig = link.hotspotConfig as? WifiConnectConfig
            val peerId = link.virtualAddress?.addressToDotNotation() ?: ""
            
            if (connectConfig != null) {
                scope.launch {
                    try {
                        // Turn off own hotspot to connect as station
                        virtualNode.setWifiHotspotEnabled(false, ConnectBand.BAND_2GHZ)
                        delay(1000)
                        virtualNode.connectAsStation(connectConfig)
                    } catch (e: Exception) {
                        Log.e("MeshManager", "Failed to connect to node: ${e.message}")
                        _connectionEvents.emit(peerId to false)
                    }
                }
            }
        } catch (e: Exception) {}
    }

    suspend fun getConnectUri(): String? {
        return (virtualNode.state.first() as? LocalNodeState)?.connectUri
    }

    suspend fun sendCallSignal(peerId: String, type: String, payload: String = "") {
        withContext(Dispatchers.IO) {
            try {
                val socket = getOrCreateSocket(peerId)
                val writer = PrintWriter(socket.getOutputStream(), true)
                val packet = MeshPacket(
                    type = type,
                    senderName = preferenceManager.getUser()?.name ?: android.os.Build.MODEL,
                    deviceId = localDeviceId,
                    senderAddress = getLocalVirtualIp(),
                    payload = payload
                )
                writer.println(packetJson.encodeToString(packet))
            } catch (e: Exception) {
                activeConnections.remove(peerId)
                _connectedDevices.value = activeConnections.keys.toSet()
            }
        }
    }

    fun disconnectFromNode(peerId: String) {
        val ip = deviceIdToIp[peerId] ?: peerId
        activeConnections[ip]?.close()
        activeConnections.remove(ip)
        _connectedDevices.value = activeConnections.keys.toSet()
        Log.d("MeshManager", "Disconnected from $peerId")
    }
}
