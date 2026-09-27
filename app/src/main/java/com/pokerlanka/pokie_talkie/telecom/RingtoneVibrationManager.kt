package com.pokerlanka.pokie_talkie.telecom

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

import com.pokerlanka.pokie_talkie.data.local.UserPreferencesManager

/**
 * Centralised manager for call ringtone, vibration, outgoing ringback tone,
 * and message notification sound / vibration.
 *
 * All methods are idempotent — calling start twice is safe; calling stop when
 * nothing is playing is also safe.
 */
class RingtoneVibrationManager(
    private val context: Context,
    private val userPreferencesManager: UserPreferencesManager? = null
) {

    private val tag = "RingtoneVibration"

    // ---- Hardware handles ----
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // ---- Incoming call ring ----
    private var ringtone: Ringtone? = null
    private var ringtonePlayer: MediaPlayer? = null
    private var isRinging = false

    // ---- Outgoing ringback tone ----
    private var toneGenerator: ToneGenerator? = null
    private var isRingbackPlaying = false

    // ---- Message notification ----
    private var messagePlayer: MediaPlayer? = null

    // ===========================================
    //  INCOMING CALL — Ringtone + Vibration
    // ===========================================

    /**
     * Start playing the device's default ringtone and vibration pattern
     * for an incoming call. Respects user preferences and device ringer mode.
     */
    @Synchronized
    fun startIncomingRing() {
        if (isRinging) return
        isRinging = true

        val ringerMode = audioManager.ringerMode
        val allowVibrate = userPreferencesManager?.isCallVibrationEnabled() ?: true
        val allowRingtone = userPreferencesManager?.isCallRingtoneEnabled() ?: true

        // Start vibration in NORMAL and VIBRATE modes
        if (allowVibrate && ringerMode != AudioManager.RINGER_MODE_SILENT) {
            startCallVibration()
        }

        // Play audible ringtone only in NORMAL mode
        if (allowRingtone && ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            try {
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone = RingtoneManager.getRingtone(context, ringtoneUri)?.apply {
                        isLooping = true
                        audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        play()
                    }
                } else {
                    ringtonePlayer?.release()
                    ringtonePlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        setDataSource(context, ringtoneUri)
                        isLooping = true
                        prepare()
                        start()
                    }
                }
                Log.i(tag, "Incoming call ringtone started")
            } catch (e: Exception) {
                Log.w(tag, "Failed to start ringtone: ${e.message}")
            }
        }
    }

    /**
     * Stop the incoming-call ringtone and vibration.
     */
    @Synchronized
    fun stopIncomingRing() {
        if (!isRinging) return
        isRinging = false

        try {
            ringtone?.stop()
        } catch (ignored: Exception) {}
        ringtone = null

        try {
            ringtonePlayer?.stop()
            ringtonePlayer?.release()
        } catch (ignored: Exception) {}
        ringtonePlayer = null

        stopVibration()
        Log.i(tag, "Incoming call ringtone stopped")
    }

    // ===========================================
    //  OUTGOING CALL — Ringback Tone
    // ===========================================

    /**
     * Play an audible ringback tone to the caller while the remote phone is
     * ringing. Uses a standard telephony SUP_RINGTONE.
     */
    @Synchronized
    fun startRingbackTone() {
        if (isRingbackPlaying) return
        val allowRingback = userPreferencesManager?.isRingbackToneEnabled() ?: true
        if (!allowRingback) return

        isRingbackPlaying = true

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE)
            Log.i(tag, "Outgoing ringback tone started")
        } catch (e: Exception) {
            Log.w(tag, "Failed to start ringback tone: ${e.message}")
        }
    }

    /**
     * Stop the outgoing-call ringback tone.
     */
    @Synchronized
    fun stopRingbackTone() {
        isRingbackPlaying = false
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (ignored: Exception) {}
        toneGenerator = null
        Log.i(tag, "Outgoing ringback tone stopped")
    }

    // ===========================================
    //  MESSAGE NOTIFICATION — Short Sound + Buzz
    // ===========================================

    /**
     * Play a short notification sound and a brief vibration for an incoming
     * chat message. Respects user preferences and device ringer mode.
     */
    fun playMessageNotification() {
        val ringerMode = audioManager.ringerMode
        val allowVibrate = userPreferencesManager?.isMessageVibrationEnabled() ?: true
        val allowSound = userPreferencesManager?.isMessageSoundEnabled() ?: true

        // Short vibration in NORMAL and VIBRATE modes
        if (allowVibrate && ringerMode != AudioManager.RINGER_MODE_SILENT) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200)
                }
            } catch (e: Exception) {
                Log.w(tag, "Message vibration failed: ${e.message}")
            }
        }

        // Play notification sound only in NORMAL mode
        if (allowSound && ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            try {
                val notifUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                messagePlayer?.release()
                messagePlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, notifUri)
                    setOnCompletionListener { mp ->
                        mp.release()
                        if (messagePlayer == mp) messagePlayer = null
                    }
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.w(tag, "Message notification sound failed: ${e.message}")
            }
        }
    }

    // ===========================================
    //  CONVENIENCE
    // ===========================================

    /**
     * Stop all active tones and vibrations.
     */
    @Synchronized
    fun stopAll() {
        stopIncomingRing()
        stopRingbackTone()
        stopVibration()
        try {
            messagePlayer?.stop()
            messagePlayer?.release()
        } catch (ignored: Exception) {}
        messagePlayer = null
    }

    // ===========================================
    //  INTERNAL HELPERS
    // ===========================================

    private fun startCallVibration() {
        try {
            val pattern = longArrayOf(0, 1000, 500, 1000) // off-on-off-on
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(pattern, 0) // repeat from index 0
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.w(tag, "Call vibration failed: ${e.message}")
        }
    }

    private fun stopVibration() {
        try {
            vibrator.cancel()
        } catch (ignored: Exception) {}
    }
}
