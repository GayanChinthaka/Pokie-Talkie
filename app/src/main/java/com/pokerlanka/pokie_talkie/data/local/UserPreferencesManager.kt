package com.pokerlanka.pokie_talkie.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class UserPreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pokie_user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "key_user_id"
        private const val KEY_USER_NAME = "key_user_name"
        private const val KEY_FCM_TOKEN = "key_fcm_token"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_AVATAR_COLOR = "key_avatar_color"
        private const val KEY_PROFILE_IMAGE_PATH = "key_profile_image_path"
        private const val KEY_FCM_SERVER_KEY = "key_fcm_server_key"
        const val DEFAULT_AVATAR_COLOR = 0xFF00A884
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

    private val _fcmServerKeyFlow = MutableStateFlow(prefs.getString(KEY_FCM_SERVER_KEY, null))
    val fcmServerKeyFlow: StateFlow<String?> = _fcmServerKeyFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(getThemeModeInternal())
    val themeModeFlow: StateFlow<AppThemeMode> = _themeModeFlow.asStateFlow()

    private val _avatarColorFlow = MutableStateFlow(prefs.getLong(KEY_AVATAR_COLOR, DEFAULT_AVATAR_COLOR))
    val avatarColorFlow: StateFlow<Long> = _avatarColorFlow.asStateFlow()

    private val _profileImagePathFlow = MutableStateFlow(prefs.getString(KEY_PROFILE_IMAGE_PATH, null))
    val profileImagePathFlow: StateFlow<String?> = _profileImagePathFlow.asStateFlow()

    private fun getUserNameInternal(): String {
        val saved = prefs.getString(KEY_USER_NAME, null)
        if (!saved.isNullOrBlank()) return saved

        val defaultName = Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL
        prefs.edit().putString(KEY_USER_NAME, defaultName).apply()
        return defaultName
    }

    private fun getThemeModeInternal(): AppThemeMode {
        val saved = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        return try {
            AppThemeMode.valueOf(saved ?: AppThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
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

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeModeFlow.value = mode
    }

    fun setAvatarColor(colorHex: Long) {
        prefs.edit().putLong(KEY_AVATAR_COLOR, colorHex).apply()
        _avatarColorFlow.value = colorHex
    }

    fun setProfileImagePath(path: String?) {
        if (path != null) {
            prefs.edit().putString(KEY_PROFILE_IMAGE_PATH, path).apply()
        } else {
            prefs.edit().remove(KEY_PROFILE_IMAGE_PATH).apply()
        }
        _profileImagePathFlow.value = path
    }

    fun setFcmServerKey(key: String?) {
        val trimmed = key?.trim()
        if (!trimmed.isNullOrBlank()) {
            prefs.edit().putString(KEY_FCM_SERVER_KEY, trimmed).apply()
            _fcmServerKeyFlow.value = trimmed
        } else {
            prefs.edit().remove(KEY_FCM_SERVER_KEY).apply()
            _fcmServerKeyFlow.value = null
        }
    }

    fun getUserName(): String = _userNameFlow.value
    fun getFcmToken(): String = _fcmTokenFlow.value
    fun getFcmServerKey(): String? = _fcmServerKeyFlow.value
    fun getThemeMode(): AppThemeMode = _themeModeFlow.value
    fun getAvatarColor(): Long = _avatarColorFlow.value
    fun getProfileImagePath(): String? = _profileImagePathFlow.value
}

