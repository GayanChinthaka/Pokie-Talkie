package com.pokerlanka.pokie_talkie.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pokerlanka.pokie_talkie.data.local.AppThemeMode
import com.pokerlanka.pokie_talkie.di.AppContainer
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val appContainer: AppContainer
) : ViewModel() {

    val userName: StateFlow<String> = appContainer.userPreferencesManager.userNameFlow
    val avatarColor: StateFlow<Long> = appContainer.userPreferencesManager.avatarColorFlow
    val themeMode: StateFlow<AppThemeMode> = appContainer.userPreferencesManager.themeModeFlow
    val profileImagePath: StateFlow<String?> = appContainer.userPreferencesManager.profileImagePathFlow
    val fcmToken: StateFlow<String> = appContainer.userPreferencesManager.fcmTokenFlow
    val fcmServerKey: StateFlow<String?> = appContainer.userPreferencesManager.fcmServerKeyFlow

    val userId: String get() = appContainer.currentUserId

    fun updateName(name: String) {
        if (name.isNotBlank()) {
            appContainer.updateMyProfileName(name.trim())
        }
    }

    fun updateFcmServerKey(key: String?) {
        appContainer.userPreferencesManager.setFcmServerKey(key)
    }

    fun updateAvatarColor(color: Long) {
        appContainer.userPreferencesManager.setAvatarColor(color)
    }

    fun updateThemeMode(mode: AppThemeMode) {
        appContainer.userPreferencesManager.setThemeMode(mode)
    }

    fun saveProfileImageFromUri(androidContext: android.content.Context, uri: android.net.Uri) {
        try {
            val inputStream = androidContext.contentResolver.openInputStream(uri) ?: return
            val file = java.io.File(androidContext.filesDir, "my_profile_photo.jpg")
            val outputStream = java.io.FileOutputStream(file)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            appContainer.userPreferencesManager.setProfileImagePath(file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeProfileImage(androidContext: android.content.Context) {
        try {
            val file = java.io.File(androidContext.filesDir, "my_profile_photo.jpg")
            if (file.exists()) {
                file.delete()
            }
            appContainer.userPreferencesManager.setProfileImagePath(null)
        } catch (e: Exception) {
            e.printStackTrace()
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
