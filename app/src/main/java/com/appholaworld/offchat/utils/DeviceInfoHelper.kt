package com.appholaworld.offchat.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.nio.ByteOrder
import kotlin.coroutines.resume

data class WifiNetworkInfo(
    val bssid: String,      // BSSID (access point MAC)
    val ssid: String,       // WiFi network name
    val ip: String          // IP address on that network
)

object DeviceInfoHelper {

    /** Returns the Android device ID (unique per app install on device). */
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""
    }

    /**
     * Returns the current device IP address from the active WiFi connection.
     * Falls back to empty string if WiFi is not connected.
     */
    suspend fun getCurrentIpAddress(context: Context): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt == 0) return@withContext ""
            val ipBytes = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                byteArrayOf(
                    (ipInt and 0xFF).toByte(),
                    (ipInt shr 8 and 0xFF).toByte(),
                    (ipInt shr 16 and 0xFF).toByte(),
                    (ipInt shr 24 and 0xFF).toByte()
                )
            } else {
                byteArrayOf(
                    (ipInt shr 24 and 0xFF).toByte(),
                    (ipInt shr 16 and 0xFF).toByte(),
                    (ipInt shr 8 and 0xFF).toByte(),
                    (ipInt and 0xFF).toByte()
                )
            }
            InetAddress.getByAddress(ipBytes).hostAddress ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Triggers a fresh WiFi scan, waits for the SCAN_RESULTS_AVAILABLE broadcast
     * (max 10 seconds), then reads and returns the results.
     * Falls back to cached results if scan times out or fails.
     */
    @Suppress("MissingPermission")
    suspend fun getScanResults(context: Context): List<WifiNetworkInfo> = withContext(Dispatchers.IO) {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

        // If WiFi is off, nothing to scan
        if (!wifiManager.isWifiEnabled) return@withContext emptyList()

        val currentIp = getCurrentIpAddress(context)

        // Trigger a fresh scan and wait for the result broadcast (10s timeout)
        val scanSuccess = withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        try { context.unregisterReceiver(this) } catch (_: Exception) {}
                        val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                        cont.resume(success)
                    }
                }

                context.registerReceiver(
                    receiver,
                    IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                )

                cont.invokeOnCancellation {
                    try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                }

                // Start the scan — if it fails immediately, fall back to cached results
                val started = wifiManager.startScan()
                if (!started) {
                    try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                    cont.resume(false)
                }
            }
        }

        // Whether scan succeeded, timed out, or fell back — read whatever results are available
        return@withContext try {
            wifiManager.scanResults.map { result ->
                WifiNetworkInfo(
                    bssid = result.BSSID ?: "",
                    ssid = result.SSID?.trim()?.ifEmpty { "<hidden>" } ?: "<hidden>",
                    ip = currentIp
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
