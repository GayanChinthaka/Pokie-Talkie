package com.pokerlanka.pokie_talkie.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pokerlanka.pokie_talkie.BuildConfig
import com.pokerlanka.pokie_talkie.data.local.AppThemeMode
import com.pokerlanka.pokie_talkie.di.AppContainer
import com.pokerlanka.pokie_talkie.utils.MediaStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal fun isGodModePasswordValid(input: String, expected: String): Boolean {
    return expected.isNotBlank() && input == expected
}

class SettingsViewModel(
    private val appContainer: AppContainer
) : ViewModel() {

    val userName: StateFlow<String> = appContainer.userPreferencesManager.userNameFlow
    val avatarColor: StateFlow<Long> = appContainer.userPreferencesManager.avatarColorFlow
    val themeMode: StateFlow<AppThemeMode> = appContainer.userPreferencesManager.themeModeFlow
    val themeAccentColor: StateFlow<Long> = appContainer.userPreferencesManager.themeAccentColorFlow
    val profileImagePath: StateFlow<String?> = appContainer.userPreferencesManager.profileImagePathFlow

    // Ringing & Sounds
    val callRingtoneEnabled: StateFlow<Boolean> = appContainer.userPreferencesManager.callRingtoneEnabledFlow
    val callVibrationEnabled: StateFlow<Boolean> = appContainer.userPreferencesManager.callVibrationEnabledFlow
    val ringbackToneEnabled: StateFlow<Boolean> = appContainer.userPreferencesManager.ringbackToneEnabledFlow
    val callFeedbackTonesEnabled: StateFlow<Boolean> = appContainer.userPreferencesManager.callFeedbackTonesEnabledFlow
    val messageSoundEnabled: StateFlow<Boolean> = appContainer.userPreferencesManager.messageSoundEnabledFlow
    val messageVibrationEnabled: StateFlow<Boolean> = appContainer.userPreferencesManager.messageVibrationEnabledFlow

    // Updater
    val autoCheckUpdates: StateFlow<Boolean> = appContainer.userPreferencesManager.autoCheckUpdatesFlow
    val lastUpdateCheckTime: StateFlow<Long> = appContainer.userPreferencesManager.lastUpdateCheckTimeFlow

    // God Mode
    val isGodModeEnabled: StateFlow<Boolean> = appContainer.userPreferencesManager.godModeEnabledFlow

    val userId: String get() = appContainer.currentUserId

    fun updateName(name: String) {
        if (name.isNotBlank()) {
            appContainer.updateMyProfileName(name.trim())
        }
    }

    fun updateThemeAccentColor(color: Long) {
        appContainer.userPreferencesManager.setThemeAccentColor(color)
    }

    fun updateThemeMode(mode: AppThemeMode) {
        appContainer.userPreferencesManager.setThemeMode(mode)
    }

    fun setCallRingtoneEnabled(enabled: Boolean) {
        appContainer.userPreferencesManager.setCallRingtoneEnabled(enabled)
    }

    fun setCallVibrationEnabled(enabled: Boolean) {
        appContainer.userPreferencesManager.setCallVibrationEnabled(enabled)
    }

    fun setRingbackToneEnabled(enabled: Boolean) {
        appContainer.userPreferencesManager.setRingbackToneEnabled(enabled)
    }

    fun setCallFeedbackTonesEnabled(enabled: Boolean) {
        appContainer.userPreferencesManager.setCallFeedbackTonesEnabled(enabled)
    }

    fun setMessageSoundEnabled(enabled: Boolean) {
        appContainer.userPreferencesManager.setMessageSoundEnabled(enabled)
    }

    fun setMessageVibrationEnabled(enabled: Boolean) {
        appContainer.userPreferencesManager.setMessageVibrationEnabled(enabled)
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        appContainer.userPreferencesManager.setAutoCheckUpdates(enabled)
    }

    fun setLastUpdateCheckTime(timestamp: Long) {
        appContainer.userPreferencesManager.setLastUpdateCheckTime(timestamp)
    }

    fun unlockGodMode(password: String): Boolean {
        if (isGodModePasswordValid(password, BuildConfig.GOD_MODE_PASSWORD)) {
            appContainer.userPreferencesManager.setGodModeEnabled(true)
            return true
        }
        return false
    }

    fun setGodModeEnabled(enabled: Boolean) {
        appContainer.userPreferencesManager.setGodModeEnabled(enabled)
    }

    fun saveProfileImageFromUri(androidContext: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val saved = MediaStorageHelper.compressAndSaveMyAvatar(androidContext, uri) ?: return@launch
                val (path, hash) = saved
                appContainer.userPreferencesManager.setProfileImagePath(path, hash)
                appContainer.onMyProfileUpdated(pushAvatarToPeers = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeProfileImage() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                MediaStorageHelper.deleteMyAvatarFiles(appContainer.context)
                appContainer.userPreferencesManager.setProfileImagePath(null, "")
                appContainer.onMyProfileUpdated(pushAvatarToPeers = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(appContainer) as T
                }
            }
    }
}
