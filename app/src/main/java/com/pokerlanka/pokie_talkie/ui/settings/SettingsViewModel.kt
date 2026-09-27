package com.pokerlanka.pokie_talkie.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pokerlanka.pokie_talkie.data.local.AppThemeMode
import com.pokerlanka.pokie_talkie.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    val messageSoundEnabled: StateFlow<Boolean> = appContainer.userPreferencesManager.messageSoundEnabledFlow
    val messageVibrationEnabled: StateFlow<Boolean> = appContainer.userPreferencesManager.messageVibrationEnabledFlow

    // Updater
    val autoCheckUpdates: StateFlow<Boolean> = appContainer.userPreferencesManager.autoCheckUpdatesFlow
    val lastUpdateCheckTime: StateFlow<Long> = appContainer.userPreferencesManager.lastUpdateCheckTimeFlow

    val userId: String get() = appContainer.currentUserId

    fun updateName(name: String) {
        if (name.isNotBlank()) {
            appContainer.updateMyProfileName(name.trim())
        }
    }

    fun updateAvatarColor(color: Long) {
        appContainer.userPreferencesManager.setAvatarColor(color)
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

    fun saveProfileImageFromUri(androidContext: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = androidContext.contentResolver.openInputStream(uri) ?: return@launch
                val filename = "profile_photo_${System.currentTimeMillis()}.jpg"
                val file = java.io.File(androidContext.filesDir, filename)
                val outputStream = java.io.FileOutputStream(file)
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                // Delete old profile image if exists
                val oldPath = appContainer.userPreferencesManager.getProfileImagePath()
                if (!oldPath.isNullOrBlank()) {
                    try {
                        val oldFile = java.io.File(oldPath)
                        if (oldFile.exists()) oldFile.delete()
                    } catch (ignored: Exception) {}
                }
                appContainer.userPreferencesManager.setProfileImagePath(file.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeProfileImage(androidContext: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val oldPath = appContainer.userPreferencesManager.getProfileImagePath()
                if (!oldPath.isNullOrBlank()) {
                    val file = java.io.File(oldPath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                appContainer.userPreferencesManager.setProfileImagePath(null)
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
