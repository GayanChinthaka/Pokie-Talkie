package com.pokerlanka.pokie_talkie.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class UserPreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pokie_user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "key_user_id"
        private const val KEY_USER_NAME = "key_user_name"
        private const val KEY_FCM_TOKEN = "key_fcm_token"
    }

    val userId: String
        get() {
            var id = prefs.getString(KEY_USER_ID, null)
            if (id.isNullOrBlank()) {
                id = "user_" + UUID.randomUUID().toString().take(8)
                prefs.edit().putString(KEY_USER_ID, id).apply()
            }
            return id
        }

    private val _userNameFlow = MutableStateFlow(getUserNameInternal())
    val userNameFlow: StateFlow<String> = _userNameFlow.asStateFlow()

    private val _fcmTokenFlow = MutableStateFlow(prefs.getString(KEY_FCM_TOKEN, "") ?: "")
    val fcmTokenFlow: StateFlow<String> = _fcmTokenFlow.asStateFlow()

    private fun getUserNameInternal(): String {
        val saved = prefs.getString(KEY_USER_NAME, null)
        if (!saved.isNullOrBlank()) return saved

        val defaultName = Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL
        prefs.edit().putString(KEY_USER_NAME, defaultName).apply()
        return defaultName
    }

    fun setUserName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) {
            prefs.edit().putString(KEY_USER_NAME, trimmed).apply()
            _userNameFlow.value = trimmed
        }
    }

    fun setFcmToken(token: String) {
        if (token.isNotBlank()) {
            prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
            _fcmTokenFlow.value = token
        }
    }

    fun getUserName(): String = _userNameFlow.value
    fun getFcmToken(): String = _fcmTokenFlow.value
}
