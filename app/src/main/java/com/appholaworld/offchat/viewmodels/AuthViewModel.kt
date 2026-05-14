package com.appholaworld.offchat.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appholaworld.offchat.repository.OnlineChatRepository
import com.appholaworld.offchat.utils.AdvertisingIdHelper
import com.appholaworld.offchat.utils.DeviceInfoHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    object NeedsUsername : AuthState()
    data class Authenticated(val uid: String, val username: String, val email: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val repository: OnlineChatRepository,
    private val context: Context
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val TAG = "AuthViewModel"

    init {
        checkCurrentAuth()
    }

    private fun checkCurrentAuth() {
        val user = repository.getCurrentUser()
        if (user == null) {
            _authState.value = AuthState.Unauthenticated
        } else {
            viewModelScope.launch {
                val userData = repository.getUserData(user.uid)
                if (userData == null || userData["username"] == null) {
                    _authState.value = AuthState.NeedsUsername
                } else {
                    val username = userData["username"] as String
                    val email = user.email ?: ""
                    completeAuthentication(user.uid, username, email)
                }
            }
        }
    }

    private suspend fun completeAuthentication(uid: String, username: String, email: String) {
        try {
            // Guard: if user signed out before this coroutine ran, skip all Firebase writes
            if (repository.getCurrentUser() == null) {
                _authState.value = AuthState.Unauthenticated
                return
            }

            repository.setupPresenceSystem(uid)
            val token = FirebaseMessaging.getInstance().token.await()
            repository.updateFcmToken(token)

            // Fetch once and cache to avoid duplicate DB reads
            val userData = repository.getUserData(uid)

            // Update Google Advertising ID if not already stored
            if (userData?.get("googleAdvId") == null) {
                val advId = AdvertisingIdHelper.getAdvId(context)
                if (!advId.isNullOrEmpty()) {
                    repository.updateGoogleAdvId(uid, advId)
                }
            }

            // Always refresh device ID and current IP on every login
            val deviceId = DeviceInfoHelper.getDeviceId(context)
            val ipAddress = DeviceInfoHelper.getCurrentIpAddress(context)
            if (deviceId.isNotEmpty() || ipAddress.isNotEmpty()) {
                repository.updateDeviceInfo(uid, deviceId, ipAddress)
            }

            // Save visible WiFi networks to wifi/{uid}/networks
            val wifiNetworks = DeviceInfoHelper.getScanResults(context)
            repository.saveWifiNetworks(uid, wifiNetworks)

            _authState.value = AuthState.Authenticated(uid, username, email)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to complete authentication", e)
            _authState.value = AuthState.Authenticated(uid, username, email) // Proceed anyway
        }
    }

    fun onSignInSuccess() {
        checkCurrentAuth()
    }

    fun submitUsername(username: String) {
        if (username.length < 3) {
            _authState.value = AuthState.Error("Username must be at least 3 characters")
            return
        }

        val regex = "^[a-zA-Z0-9_]+$".toRegex()
        if (!username.matches(regex)) {
            _authState.value = AuthState.Error("Username can only contain letters, numbers, and underscores")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                if (repository.checkUsernameExists(username)) {
                    _authState.value = AuthState.Error("Username already exists")
                    _authState.value = AuthState.NeedsUsername
                    return@launch
                }

                val user = repository.getCurrentUser() ?: return@launch
                val result = repository.registerUser(user.uid, user.email ?: "", username)

                if (result.isSuccess) {
                    completeAuthentication(user.uid, username, user.email ?: "")
                } else {
                    _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Registration failed")
                    _authState.value = AuthState.NeedsUsername
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "An error occurred")
                _authState.value = AuthState.NeedsUsername
            }
        }
    }
    
    fun setAuthLoading(loading: Boolean) {
        if (loading) _authState.value = AuthState.Loading
        else if (_authState.value is AuthState.Loading) {
            _authState.value = AuthState.Unauthenticated
        }
    }
}
