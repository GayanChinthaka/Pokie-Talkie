package com.pokerlanka.pokie_talkie.telecom

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * Turns off the display and disables touch input while the phone is held up to the ear
 * during a voice call routed to the earpiece.
 */
class ProximityLock(context: Context) {
    private val tag = "ProximityLock"
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private fun ensureWakeLock(): PowerManager.WakeLock? {
        if (wakeLock != null) return wakeLock
        val pm = powerManager ?: return null
        return try {
            if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                Log.d(tag, "PROXIMITY_SCREEN_OFF_WAKE_LOCK not supported on this device")
                return null
            }
            pm.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "pokie_talkie:ear_proximity_lock"
            ).apply {
                setReferenceCounted(false)
            }.also { wakeLock = it }
        } catch (e: Exception) {
            Log.w(tag, "Failed to create proximity WakeLock: ${e.message}")
            null
        }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (enabled) acquire() else release()
    }

    @Synchronized
    fun acquire() {
        val lock = ensureWakeLock() ?: return
        try {
            if (!lock.isHeld) {
                lock.acquire()
                Log.d(tag, "Proximity screen-off lock acquired")
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to acquire proximity WakeLock: ${e.message}")
        }
    }

    @Synchronized
    fun release() {
        val lock = wakeLock ?: return
        try {
            if (lock.isHeld) {
                lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
                Log.d(tag, "Proximity screen-off lock released")
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to release proximity WakeLock: ${e.message}")
        }
    }
}
