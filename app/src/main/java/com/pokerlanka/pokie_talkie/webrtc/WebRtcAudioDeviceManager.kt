package com.pokerlanka.pokie_talkie.webrtc

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioRoute {
    EARPIECE,
    SPEAKER,
    BLUETOOTH,
    WIRED_HEADSET
}

data class AudioRouteOption(
    val route: AudioRoute,
    val label: String,
    val deviceName: String? = null
)

class WebRtcAudioDeviceManager(private val context: Context) {
    private val tag = "WebRtcAudioDeviceManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isCallActive = false
    private var preferSpeakerFallback = false
    private var userSelectedRoute: AudioRoute? = null
    private var activeFocusRequest: AudioFocusRequest? = null
    private var pendingResetRunnable: Runnable? = null

    private val _activeRoute = MutableStateFlow(AudioRoute.EARPIECE)
    val activeRoute: StateFlow<AudioRoute> = _activeRoute.asStateFlow()

    private val _availableRoutes = MutableStateFlow<List<AudioRouteOption>>(defaultFallbackRoutes())
    val availableRoutes: StateFlow<List<AudioRouteOption>> = _availableRoutes.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var commDeviceListener: Any? = null

    fun startCallAudio(enableSpeaker: Boolean = false) {
        cancelPendingReset()
        preferSpeakerFallback = enableSpeaker

        if (isCallActive) {
            refreshAvailableRoutes()
            if (userSelectedRoute == null && enableSpeaker &&
                _activeRoute.value == AudioRoute.EARPIECE
            ) {
                applyRoute(AudioRoute.SPEAKER, userInitiated = false)
            }
            return
        }

        isCallActive = true
        userSelectedRoute = null

        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = false
            requestCallAudioFocus()
            registerCallbacks()
            refreshAvailableRoutes()

            val initialRoute = resolveAutoRoute(preferSpeaker = enableSpeaker)
            applyRoute(initialRoute, userInitiated = false)
            Log.d(tag, "Call audio session started (initialRoute=$initialRoute, preferSpeaker=$enableSpeaker)")
        } catch (e: Exception) {
            Log.e(tag, "Error starting call audio: ${e.message}")
        }
    }

    /**
     * Upgrades audio routing when a Voice call switches to Video/Screen share:
     * keeps Bluetooth or Wired Headset if active, otherwise switches Earpiece -> Speaker.
     */
    fun onUpgradeToVideo() {
        preferSpeakerFallback = true
        if (!isCallActive) return
        val current = _activeRoute.value
        if (current == AudioRoute.EARPIECE) {
            applyRoute(AudioRoute.SPEAKER, userInitiated = false)
        }
    }

    fun selectAudioRoute(route: AudioRoute) {
        if (!isCallActive) {
            preferSpeakerFallback = (route == AudioRoute.SPEAKER)
            _activeRoute.value = route
            _isSpeakerOn.value = (route == AudioRoute.SPEAKER)
            return
        }
        userSelectedRoute = route
        applyRoute(route, userInitiated = true)
    }

    fun setSpeakerphoneOn(on: Boolean) {
        if (on) {
            selectAudioRoute(AudioRoute.SPEAKER)
        } else {
            val fallback = when {
                hasRoute(AudioRoute.BLUETOOTH) -> AudioRoute.BLUETOOTH
                hasRoute(AudioRoute.WIRED_HEADSET) -> AudioRoute.WIRED_HEADSET
                else -> AudioRoute.EARPIECE
            }
            selectAudioRoute(fallback)
        }
    }

    fun isSpeakerphoneOn(): Boolean = _activeRoute.value == AudioRoute.SPEAKER

    fun setMicrophoneMute(mute: Boolean) {
        try {
            audioManager.isMicrophoneMute = mute
            Log.d(tag, "Microphone mute set to $mute")
        } catch (e: Exception) {
            Log.e(tag, "Error setting microphone mute: ${e.message}")
        }
    }

    fun isMicrophoneMute(): Boolean = audioManager.isMicrophoneMute

    fun stopCallAudio(delayResetMs: Long = 0L) {
        if (!isCallActive) return
        isCallActive = false
        userSelectedRoute = null
        preferSpeakerFallback = false
        unregisterCallbacks()

        if (delayResetMs > 0L) {
            cancelPendingReset()
            val runnable = Runnable {
                if (!isCallActive) {
                    performAudioReset()
                }
            }
            pendingResetRunnable = runnable
            mainHandler.postDelayed(runnable, delayResetMs)
        } else {
            cancelPendingReset()
            performAudioReset()
        }
        Log.d(tag, "Call audio session stopped (delayResetMs=$delayResetMs)")
    }

    fun resetToNormal(delayResetMs: Long = 0L) {
        isCallActive = false
        userSelectedRoute = null
        preferSpeakerFallback = false
        unregisterCallbacks()

        if (delayResetMs > 0L) {
            cancelPendingReset()
            val runnable = Runnable {
                if (!isCallActive) {
                    performAudioReset()
                }
            }
            pendingResetRunnable = runnable
            mainHandler.postDelayed(runnable, delayResetMs)
        } else {
            cancelPendingReset()
            performAudioReset()
        }
        Log.d(tag, "Audio reset to MODE_NORMAL (delayResetMs=$delayResetMs)")
    }

    private fun cancelPendingReset() {
        pendingResetRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingResetRunnable = null
    }

    private fun requestCallAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.d(tag, "Audio focus changed: $focusChange")
                }
                .build()
            activeFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun registerCallbacks() {
        unregisterCallbacks()
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                if (!isCallActive) return
                refreshAvailableRoutes()
                val added = addedDevices.orEmpty()
                val addedBt = added.any { isBluetoothCommType(it.type) }
                val addedWired = added.any { isWiredHeadsetType(it.type) }
                when {
                    addedBt -> {
                        userSelectedRoute = null
                        applyRoute(AudioRoute.BLUETOOTH, userInitiated = false)
                    }
                    addedWired && _activeRoute.value != AudioRoute.BLUETOOTH -> {
                        userSelectedRoute = null
                        applyRoute(AudioRoute.WIRED_HEADSET, userInitiated = false)
                    }
                }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                if (!isCallActive) return
                refreshAvailableRoutes()
                val current = _activeRoute.value
                if (!hasRoute(current)) {
                    userSelectedRoute = null
                    val fallback = resolveAutoRoute(preferSpeaker = preferSpeakerFallback)
                    applyRoute(fallback, userInitiated = false)
                }
            }
        }
        audioDeviceCallback = callback
        audioManager.registerAudioDeviceCallback(callback, mainHandler)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerApi31CommunicationListener()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerApi31CommunicationListener() {
        val listener = AudioManager.OnCommunicationDeviceChangedListener { device ->
            if (!isCallActive || device == null) return@OnCommunicationDeviceChangedListener
            val mapped = mapDeviceTypeToRoute(device.type)
            if (mapped != null && mapped != _activeRoute.value) {
                _activeRoute.value = mapped
                _isSpeakerOn.value = (mapped == AudioRoute.SPEAKER)
                Log.d(tag, "Communication device changed by system: $mapped (${device.productName})")
            }
        }
        commDeviceListener = listener
        audioManager.addOnCommunicationDeviceChangedListener(context.mainExecutor, listener)
    }

    private fun unregisterCallbacks() {
        audioDeviceCallback?.let {
            try {
                audioManager.unregisterAudioDeviceCallback(it)
            } catch (ignored: Exception) {}
        }
        audioDeviceCallback = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (commDeviceListener as? AudioManager.OnCommunicationDeviceChangedListener)?.let { listener ->
                try {
                    audioManager.removeOnCommunicationDeviceChangedListener(listener)
                } catch (ignored: Exception) {}
            }
        }
        commDeviceListener = null
    }

    private fun refreshAvailableRoutes() {
        val options = mutableListOf<AudioRouteOption>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val btDevice = findPreferredBluetoothDevice(devices)
            val wiredDevice = devices.firstOrNull { isWiredHeadsetType(it.type) }
            val hasEarpiece = devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            val hasSpeaker = devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

            if (btDevice != null) {
                val name = btDevice.productName?.toString()?.takeIf { it.isNotBlank() }
                options.add(AudioRouteOption(AudioRoute.BLUETOOTH, name ?: "Bluetooth", name))
            }
            if (wiredDevice != null) {
                val name = wiredDevice.productName?.toString()?.takeIf { it.isNotBlank() }
                options.add(AudioRouteOption(AudioRoute.WIRED_HEADSET, name ?: "Headset", name))
            }
            if (hasSpeaker || options.isEmpty()) {
                options.add(AudioRouteOption(AudioRoute.SPEAKER, "Speaker"))
            }
            if (hasEarpiece) {
                options.add(AudioRouteOption(AudioRoute.EARPIECE, "Phone Earpiece"))
            }
        } else {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val btDevice = findPreferredBluetoothDevice(outputs.toList())
            val wiredDevice = outputs.firstOrNull { isWiredHeadsetType(it.type) }
            val hasEarpiece = outputs.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }

            if (btDevice != null) {
                val name = btDevice.productName?.toString()?.takeIf { it.isNotBlank() }
                options.add(AudioRouteOption(AudioRoute.BLUETOOTH, name ?: "Bluetooth", name))
            }
            if (wiredDevice != null) {
                val name = wiredDevice.productName?.toString()?.takeIf { it.isNotBlank() }
                options.add(AudioRouteOption(AudioRoute.WIRED_HEADSET, name ?: "Headset", name))
            }
            options.add(AudioRouteOption(AudioRoute.SPEAKER, "Speaker"))
            if (hasEarpiece) {
                options.add(AudioRouteOption(AudioRoute.EARPIECE, "Phone Earpiece"))
            }
        }

        _availableRoutes.value = options
    }

    private fun hasRoute(route: AudioRoute): Boolean =
        _availableRoutes.value.any { it.route == route }

    private fun resolveAutoRoute(preferSpeaker: Boolean): AudioRoute {
        userSelectedRoute?.let { chosen ->
            if (hasRoute(chosen)) return chosen
        }
        return when {
            hasRoute(AudioRoute.BLUETOOTH) -> AudioRoute.BLUETOOTH
            hasRoute(AudioRoute.WIRED_HEADSET) -> AudioRoute.WIRED_HEADSET
            preferSpeaker && hasRoute(AudioRoute.SPEAKER) -> AudioRoute.SPEAKER
            hasRoute(AudioRoute.EARPIECE) -> AudioRoute.EARPIECE
            else -> AudioRoute.SPEAKER
        }
    }

    @SuppressLint("MissingPermission")
    private fun applyRoute(route: AudioRoute, userInitiated: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val targetDevice = when (route) {
                    AudioRoute.BLUETOOTH -> findPreferredBluetoothDevice(devices)
                    AudioRoute.WIRED_HEADSET -> devices.firstOrNull { isWiredHeadsetType(it.type) }
                    AudioRoute.SPEAKER -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    AudioRoute.EARPIECE -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                }

                if (targetDevice != null) {
                    val result = audioManager.setCommunicationDevice(targetDevice)
                    Log.d(tag, "API 31+ setCommunicationDevice($route -> ${targetDevice.productName}, type=${targetDevice.type}) = $result (user=$userInitiated)")
                    if (result) {
                        _activeRoute.value = route
                        _isSpeakerOn.value = (route == AudioRoute.SPEAKER)
                        return
                    }
                }
                Log.w(tag, "Target communication device for $route not available or failed, falling back")
            }

            // API 26-30 or fallback
            @Suppress("DEPRECATION")
            when (route) {
                AudioRoute.BLUETOOTH -> {
                    audioManager.isSpeakerphoneOn = false
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                }
                AudioRoute.SPEAKER -> {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = true
                }
                AudioRoute.WIRED_HEADSET, AudioRoute.EARPIECE -> {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                }
            }
            _activeRoute.value = route
            _isSpeakerOn.value = (route == AudioRoute.SPEAKER)
            Log.d(tag, "Legacy audio route applied: $route (user=$userInitiated)")
        } catch (e: Exception) {
            Log.e(tag, "Error applying audio route $route: ${e.message}")
        }
    }

    private fun performAudioReset() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeFocusRequest?.let {
                    audioManager.abandonAudioFocusRequest(it)
                }
                activeFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                run {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                }
            }
            audioManager.isMicrophoneMute = false
            audioManager.mode = AudioManager.MODE_NORMAL
            _activeRoute.value = AudioRoute.EARPIECE
            _isSpeakerOn.value = false
            _availableRoutes.value = defaultFallbackRoutes()
        } catch (e: Exception) {
            Log.e(tag, "Error performing audio reset: ${e.message}")
        }
    }

    private fun findPreferredBluetoothDevice(devices: List<AudioDeviceInfo>): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }?.let { return it }
            devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_HEARING_AID }?.let { return it }
        }
        return devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
    }

    private fun isBluetoothCommType(type: Int): Boolean {
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_HEARING_AID) return true
        }
        return false
    }

    private fun isWiredHeadsetType(type: Int): Boolean {
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && type == AudioDeviceInfo.TYPE_USB_HEADSET) return true
        return false
    }

    private fun mapDeviceTypeToRoute(type: Int): AudioRoute? = when {
        isBluetoothCommType(type) -> AudioRoute.BLUETOOTH
        isWiredHeadsetType(type) -> AudioRoute.WIRED_HEADSET
        type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioRoute.SPEAKER
        type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioRoute.EARPIECE
        else -> null
    }

    private fun defaultFallbackRoutes(): List<AudioRouteOption> = listOf(
        AudioRouteOption(AudioRoute.SPEAKER, "Speaker"),
        AudioRouteOption(AudioRoute.EARPIECE, "Phone Earpiece")
    )
}
