package com.pokerlanka.pokie_talkie.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

class WebRtcAudioDeviceManager(private val context: Context) {
    private val tag = "WebRtcAudioDeviceManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isCallActive = false
    private var activeFocusRequest: AudioFocusRequest? = null

    fun startCallAudio(enableSpeaker: Boolean = false) {
        if (isCallActive) return
        isCallActive = true

        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            setSpeakerphoneOn(enableSpeaker)
            audioManager.isMicrophoneMute = false

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
            Log.d(tag, "Call audio session started (Speaker: $enableSpeaker)")
        } catch (e: Exception) {
            Log.e(tag, "Error starting call audio: ${e.message}")
        }
    }

    fun setSpeakerphoneOn(on: Boolean) {
        try {
            audioManager.isSpeakerphoneOn = on
            Log.d(tag, "Speakerphone set to $on")
        } catch (e: Exception) {
            Log.e(tag, "Error setting speakerphone: ${e.message}")
        }
    }

    fun isSpeakerphoneOn(): Boolean = audioManager.isSpeakerphoneOn

    fun setMicrophoneMute(mute: Boolean) {
        try {
            audioManager.isMicrophoneMute = mute
            Log.d(tag, "Microphone mute set to $mute")
        } catch (e: Exception) {
            Log.e(tag, "Error setting microphone mute: ${e.message}")
        }
    }

    fun isMicrophoneMute(): Boolean = audioManager.isMicrophoneMute

    fun stopCallAudio() {
        isCallActive = false

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

            audioManager.isSpeakerphoneOn = false
            audioManager.isMicrophoneMute = false
            audioManager.mode = AudioManager.MODE_NORMAL

            Log.d(tag, "Call audio session stopped, forced audio mode to MODE_NORMAL and abandoned focus")
        } catch (e: Exception) {
            Log.e(tag, "Error stopping call audio: ${e.message}")
        }
    }

    fun resetToNormal() {
        stopCallAudio()
    }
}
