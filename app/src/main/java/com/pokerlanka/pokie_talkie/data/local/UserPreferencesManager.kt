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

enum class ConnectionMode {
    WIFI,
    BLUETOOTH
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
        private const val KEY_PROFILE_IMAGE_HASH = "key_profile_image_hash"
        private const val KEY_CALL_RINGTONE_ENABLED = "key_call_ringtone_enabled"
        private const val KEY_CALL_VIBRATION_ENABLED = "key_call_vibration_enabled"
        private const val KEY_RINGBACK_TONE_ENABLED = "key_ringback_tone_enabled"
        private const val KEY_CALL_FEEDBACK_TONES_ENABLED = "key_call_feedback_tones_enabled"
        private const val KEY_MESSAGE_SOUND_ENABLED = "key_message_sound_enabled"
        private const val KEY_MESSAGE_VIBRATION_ENABLED = "key_message_vibration_enabled"
        private const val KEY_AUTO_CHECK_UPDATES = "key_auto_check_updates"
        private const val KEY_LAST_UPDATE_CHECK_TIME = "key_last_update_check_time"
        private const val KEY_THEME_ACCENT_COLOR = "key_theme_accent_color"
        private const val KEY_CONNECTION_MODE = "key_connection_mode"
        private const val KEY_GOD_MODE_ENABLED = "key_god_mode_enabled"
        private const val KEY_GRANTED_GOD_PEERS = "key_granted_god_peers"
        private const val KEY_AUTHORIZED_REMOTE_PEERS = "key_authorized_remote_peers"
        private const val KEY_SHARED_FOLDER_URI = "key_shared_folder_uri"
        const val DEFAULT_AVATAR_COLOR = 0xFF00A884
        const val DEFAULT_ACCENT_COLOR = 0xFF00A884
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

    private val _themeModeFlow = MutableStateFlow(getThemeModeInternal())
    val themeModeFlow: StateFlow<AppThemeMode> = _themeModeFlow.asStateFlow()

    private val _avatarColorFlow = MutableStateFlow(prefs.getLong(KEY_AVATAR_COLOR, DEFAULT_AVATAR_COLOR))
    val avatarColorFlow: StateFlow<Long> = _avatarColorFlow.asStateFlow()

    private val _themeAccentColorFlow = MutableStateFlow(prefs.getLong(KEY_THEME_ACCENT_COLOR, DEFAULT_ACCENT_COLOR))
    val themeAccentColorFlow: StateFlow<Long> = _themeAccentColorFlow.asStateFlow()

    private val _profileImagePathFlow = MutableStateFlow(prefs.getString(KEY_PROFILE_IMAGE_PATH, null))
    val profileImagePathFlow: StateFlow<String?> = _profileImagePathFlow.asStateFlow()

    private val _profileImageHashFlow = MutableStateFlow(prefs.getString(KEY_PROFILE_IMAGE_HASH, "") ?: "")
    val profileImageHashFlow: StateFlow<String> = _profileImageHashFlow.asStateFlow()

    // Ringing & Sounds
    private val _callRingtoneEnabledFlow = MutableStateFlow(prefs.getBoolean(KEY_CALL_RINGTONE_ENABLED, true))
    val callRingtoneEnabledFlow: StateFlow<Boolean> = _callRingtoneEnabledFlow.asStateFlow()

    private val _callVibrationEnabledFlow = MutableStateFlow(prefs.getBoolean(KEY_CALL_VIBRATION_ENABLED, true))
    val callVibrationEnabledFlow: StateFlow<Boolean> = _callVibrationEnabledFlow.asStateFlow()

    private val _ringbackToneEnabledFlow = MutableStateFlow(prefs.getBoolean(KEY_RINGBACK_TONE_ENABLED, true))
    val ringbackToneEnabledFlow: StateFlow<Boolean> = _ringbackToneEnabledFlow.asStateFlow()

    private val _callFeedbackTonesEnabledFlow = MutableStateFlow(prefs.getBoolean(KEY_CALL_FEEDBACK_TONES_ENABLED, true))
    val callFeedbackTonesEnabledFlow: StateFlow<Boolean> = _callFeedbackTonesEnabledFlow.asStateFlow()

    private val _messageSoundEnabledFlow = MutableStateFlow(prefs.getBoolean(KEY_MESSAGE_SOUND_ENABLED, true))
    val messageSoundEnabledFlow: StateFlow<Boolean> = _messageSoundEnabledFlow.asStateFlow()

    private val _messageVibrationEnabledFlow = MutableStateFlow(prefs.getBoolean(KEY_MESSAGE_VIBRATION_ENABLED, true))
    val messageVibrationEnabledFlow: StateFlow<Boolean> = _messageVibrationEnabledFlow.asStateFlow()

    // Updater
    private val _autoCheckUpdatesFlow = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true))
    val autoCheckUpdatesFlow: StateFlow<Boolean> = _autoCheckUpdatesFlow.asStateFlow()

    private val _lastUpdateCheckTimeFlow = MutableStateFlow(prefs.getLong(KEY_LAST_UPDATE_CHECK_TIME, 0L))
    val lastUpdateCheckTimeFlow: StateFlow<Long> = _lastUpdateCheckTimeFlow.asStateFlow()

    private val _connectionModeFlow = MutableStateFlow(getConnectionModeInternal())
    val connectionModeFlow: StateFlow<ConnectionMode> = _connectionModeFlow.asStateFlow()

    private val _godModeEnabledFlow = MutableStateFlow(prefs.getBoolean(KEY_GOD_MODE_ENABLED, false))
    val godModeEnabledFlow: StateFlow<Boolean> = _godModeEnabledFlow.asStateFlow()

    private val _grantedGodPeersFlow = MutableStateFlow(
        prefs.getStringSet(KEY_GRANTED_GOD_PEERS, emptySet())?.toSet() ?: emptySet()
    )
    val grantedGodPeersFlow: StateFlow<Set<String>> = _grantedGodPeersFlow.asStateFlow()

    private val _authorizedRemotePeersFlow = MutableStateFlow(
        prefs.getStringSet(KEY_AUTHORIZED_REMOTE_PEERS, emptySet())?.toSet() ?: emptySet()
    )
    val authorizedRemotePeersFlow: StateFlow<Set<String>> = _authorizedRemotePeersFlow.asStateFlow()

    private val _sharedFolderUriFlow = MutableStateFlow(prefs.getString(KEY_SHARED_FOLDER_URI, null))
    val sharedFolderUriFlow: StateFlow<String?> = _sharedFolderUriFlow.asStateFlow()

    private fun getConnectionModeInternal(): ConnectionMode {
        val saved = prefs.getString(KEY_CONNECTION_MODE, ConnectionMode.WIFI.name)
        return try {
            ConnectionMode.valueOf(saved ?: ConnectionMode.WIFI.name)
        } catch (e: Exception) {
            ConnectionMode.WIFI
        }
    }

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

    fun setProfileImagePath(path: String?, hash: String = "") {
        val editor = prefs.edit()
        if (path != null) {
            editor.putString(KEY_PROFILE_IMAGE_PATH, path)
            editor.putString(KEY_PROFILE_IMAGE_HASH, hash)
        } else {
            editor.remove(KEY_PROFILE_IMAGE_PATH)
            editor.putString(KEY_PROFILE_IMAGE_HASH, "")
        }
        editor.apply()
        _profileImagePathFlow.value = path
        _profileImageHashFlow.value = if (path != null) hash else ""
    }

    fun setThemeAccentColor(colorHex: Long) {
        prefs.edit().putLong(KEY_THEME_ACCENT_COLOR, colorHex).apply()
        _themeAccentColorFlow.value = colorHex
    }

    fun setCallRingtoneEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALL_RINGTONE_ENABLED, enabled).apply()
        _callRingtoneEnabledFlow.value = enabled
    }

    fun setCallVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALL_VIBRATION_ENABLED, enabled).apply()
        _callVibrationEnabledFlow.value = enabled
    }

    fun setRingbackToneEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RINGBACK_TONE_ENABLED, enabled).apply()
        _ringbackToneEnabledFlow.value = enabled
    }

    fun setCallFeedbackTonesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALL_FEEDBACK_TONES_ENABLED, enabled).apply()
        _callFeedbackTonesEnabledFlow.value = enabled
    }

    fun setMessageSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MESSAGE_SOUND_ENABLED, enabled).apply()
        _messageSoundEnabledFlow.value = enabled
    }

    fun setMessageVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MESSAGE_VIBRATION_ENABLED, enabled).apply()
        _messageVibrationEnabledFlow.value = enabled
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK_UPDATES, enabled).apply()
        _autoCheckUpdatesFlow.value = enabled
    }

    fun setLastUpdateCheckTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_TIME, timestamp).apply()
        _lastUpdateCheckTimeFlow.value = timestamp
    }

    fun setConnectionMode(mode: ConnectionMode) {
        prefs.edit().putString(KEY_CONNECTION_MODE, mode.name).apply()
        _connectionModeFlow.value = mode
    }

    fun setGodModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GOD_MODE_ENABLED, enabled).apply()
        _godModeEnabledFlow.value = enabled
    }

    fun setPeerConsentGranted(peerId: String, granted: Boolean) {
        if (peerId.isBlank()) return
        val updated = _grantedGodPeersFlow.value.toMutableSet()
        if (granted) updated.add(peerId) else updated.remove(peerId)
        prefs.edit().putStringSet(KEY_GRANTED_GOD_PEERS, updated).apply()
        _grantedGodPeersFlow.value = updated
    }

    fun setRemotePeerAuthorized(peerId: String, authorized: Boolean) {
        if (peerId.isBlank()) return
        val updated = _authorizedRemotePeersFlow.value.toMutableSet()
        if (authorized) updated.add(peerId) else updated.remove(peerId)
        prefs.edit().putStringSet(KEY_AUTHORIZED_REMOTE_PEERS, updated).apply()
        _authorizedRemotePeersFlow.value = updated
    }

    fun setSharedFolderUri(uri: String?) {
        val editor = prefs.edit()
        if (uri.isNullOrBlank()) {
            editor.remove(KEY_SHARED_FOLDER_URI)
        } else {
            editor.putString(KEY_SHARED_FOLDER_URI, uri)
        }
        editor.apply()
        _sharedFolderUriFlow.value = uri?.takeIf { it.isNotBlank() }
    }

    fun getUserName(): String = _userNameFlow.value
    fun getFcmToken(): String = _fcmTokenFlow.value
    fun getThemeMode(): AppThemeMode = _themeModeFlow.value
    fun getAvatarColor(): Long = _avatarColorFlow.value
    fun getThemeAccentColor(): Long = _themeAccentColorFlow.value
    fun getProfileImagePath(): String? = _profileImagePathFlow.value
    fun getProfileImageHash(): String = _profileImageHashFlow.value
    fun isCallRingtoneEnabled(): Boolean = _callRingtoneEnabledFlow.value
    fun isCallVibrationEnabled(): Boolean = _callVibrationEnabledFlow.value
    fun isRingbackToneEnabled(): Boolean = _ringbackToneEnabledFlow.value
    fun isCallFeedbackTonesEnabled(): Boolean = _callFeedbackTonesEnabledFlow.value
    fun isMessageSoundEnabled(): Boolean = _messageSoundEnabledFlow.value
    fun isMessageVibrationEnabled(): Boolean = _messageVibrationEnabledFlow.value
    fun isAutoCheckUpdates(): Boolean = _autoCheckUpdatesFlow.value
    fun getLastUpdateCheckTime(): Long = _lastUpdateCheckTimeFlow.value
    fun getConnectionMode(): ConnectionMode = _connectionModeFlow.value
    fun isGodModeEnabled(): Boolean = _godModeEnabledFlow.value
    fun isPeerConsentGranted(peerId: String): Boolean = _grantedGodPeersFlow.value.contains(peerId)
    fun isRemotePeerAuthorized(peerId: String): Boolean = _authorizedRemotePeersFlow.value.contains(peerId)
    fun getSharedFolderUri(): String? = _sharedFolderUriFlow.value
}

