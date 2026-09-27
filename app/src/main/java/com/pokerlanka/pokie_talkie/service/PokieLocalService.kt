package com.pokerlanka.pokie_talkie.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pokerlanka.pokie_talkie.MainActivity
import com.pokerlanka.pokie_talkie.PokieApp
import com.pokerlanka.pokie_talkie.R

class PokieLocalService : Service() {

    private val tag = "PokieLocalService"

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isCallActive = false

    companion object {
        const val CHANNEL_SERVICE_ID = "pokie_proximity_service"
        const val NOTIFICATION_ID = 52220

        const val ACTION_START = "com.pokerlanka.pokie_talkie.service.ACTION_START"
        const val ACTION_STOP = "com.pokerlanka.pokie_talkie.service.ACTION_STOP"
        const val ACTION_CALL_STARTED = "com.pokerlanka.pokie_talkie.service.ACTION_CALL_STARTED"
        const val ACTION_CALL_ENDED = "com.pokerlanka.pokie_talkie.service.ACTION_CALL_ENDED"

        fun startService(context: Context) {
            val intent = Intent(context, PokieLocalService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("PokieLocalService", "Failed to start PokieLocalService: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, PokieLocalService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("PokieLocalService", "Failed to stop PokieLocalService: ${e.message}")
            }
        }

        const val EXTRA_IS_VIDEO = "extra_is_video"

        fun notifyCallStarted(context: Context, isVideo: Boolean = false) {
            val intent = Intent(context, PokieLocalService::class.java).apply {
                action = ACTION_CALL_STARTED
                putExtra(EXTRA_IS_VIDEO, isVideo)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w("PokieLocalService", "Failed to notify call started: ${e.message}")
            }
        }

        fun notifyCallEnded(context: Context) {
            val intent = Intent(context, PokieLocalService::class.java).apply {
                action = ACTION_CALL_ENDED
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w("PokieLocalService", "Failed to notify call ended: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "PokieLocalService onCreate")
        createServiceNotificationChannel()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(tag, "PokieLocalService onStartCommand action: $action")

        when (action) {
            ACTION_STOP -> {
                releaseLocks()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CALL_STARTED -> {
                isCallActive = true
                val isVideo = intent?.getBooleanExtra(EXTRA_IS_VIDEO, false) ?: false
                acquireCallWakeLock()
                updateForegroundNotification(inCall = true, isVideo = isVideo)
            }
            ACTION_CALL_ENDED -> {
                isCallActive = false
                releaseCallWakeLock()
                updateForegroundNotification(inCall = false, isVideo = false)
            }
            ACTION_START -> {
                updateForegroundNotification(inCall = isCallActive, isVideo = false)
            }
        }

        // Ensure listeners are active
        val app = applicationContext as? PokieApp
        app?.appContainer?.ensureListenersActive()

        return START_STICKY
    }

    private fun updateForegroundNotification(inCall: Boolean, isVideo: Boolean = false) {
        val notification = buildServiceNotification(inCall)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasBtConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                val connectedDeviceType = if (hasBtConnect) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                } else {
                    0
                }

                if (inCall) {
                    var callServiceType = connectedDeviceType or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    if (isVideo) {
                        callServiceType = callServiceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    }

                    try {
                        startForeground(NOTIFICATION_ID, notification, callServiceType)
                        return
                    } catch (e: Exception) {
                        Log.w(tag, "startForeground with callServiceType failed ($e), falling back to CONNECTED_DEVICE")
                    }
                }

                // Fallback or not in call:
                if (connectedDeviceType != 0) {
                    startForeground(NOTIFICATION_ID, notification, connectedDeviceType)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(tag, "startForeground failed, falling back to simple startForeground: ${e.message}")
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Log.e(tag, "Failed to startForeground: ${e2.message}")
            }
        }
    }

    private fun buildServiceNotification(inCall: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            52001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (inCall) "Pokie-Talkie Active Call" else "Pokie-Talkie Proximity Active"
        val message = if (inCall) "Call in progress on local network" else "Ready for LAN & Bluetooth family calls & messages"

        return NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createServiceNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Pokie Proximity Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps local WiFi and Bluetooth listeners active for incoming calls and messages"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun acquireLocks() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lockType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager.createWifiLock(lockType, "PokieLocalService:WifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d(tag, "WifiLock acquired")
        } catch (e: Exception) {
            Log.e(tag, "Error acquiring wifiLock: ${e.message}")
        }
    }

    private fun acquireCallWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PokieLocalService:CallCpuWakeLock").apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire()
                    Log.d(tag, "Call WakeLock acquired")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error acquiring call wakeLock: ${e.message}")
        }
    }

    private fun releaseCallWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(tag, "Call WakeLock released")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error releasing call wakeLock: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try {
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock = null
            releaseCallWakeLock()
            wakeLock = null
        } catch (e: Exception) {
            Log.e(tag, "Error releasing locks: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(tag, "PokieLocalService onDestroy")
        releaseLocks()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
