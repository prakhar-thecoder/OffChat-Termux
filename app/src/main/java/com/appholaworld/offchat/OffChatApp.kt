package com.appholaworld.offchat

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.appholaworld.offchat.repository.ChatRepository
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.appholaworld.offchat.mesh.MeshManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.appholaworld.offchat.database.AppDatabase
import com.appholaworld.offchat.repository.OnlineChatRepository

val Context.dataStore by preferencesDataStore(name = "meshr_settings")

open class OffChatApp : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var foregroundActivityCount = 0
    val isAppInForeground get() = foregroundActivityCount > 0
    
    val database by lazy { AppDatabase.getDatabase(this) }
    val chatRepository by lazy { ChatRepository(database.chatDao()) }
    val onlineChatRepository by lazy { OnlineChatRepository(this) }
    
    lateinit var virtualNode: AndroidVirtualNode
        private set

    lateinit var meshManager: MeshManager
        private set

    open override fun onCreate() {
        super.onCreate()
        
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) { foregroundActivityCount++ }
            override fun onActivityStopped(activity: android.app.Activity) { foregroundActivityCount-- }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })

        virtualNode = AndroidVirtualNode(
            appContext = this,
            dataStore = dataStore
        )

        meshManager = MeshManager(this)
    }
}
