package com.appholaworld.offchat.utils

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AdvertisingIdHelper {
    suspend fun getAdvId(context: Context): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val info = AdvertisingIdClient.getAdvertisingIdInfo(context.applicationContext)
            if (info.isLimitAdTrackingEnabled) null else info.id
        } catch (e: Exception) {
            null
        }
    }
}
