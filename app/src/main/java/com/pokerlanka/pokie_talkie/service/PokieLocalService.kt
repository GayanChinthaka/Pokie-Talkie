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
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.pokerlanka.pokie_talkie.MainActivity
import com.pokerlanka.pokie_talkie.PokieApp
import com.pokerlanka.pokie_talkie.R
import com.pokerlanka.pokie_talkie.telecom.CallActionReceiver
import com.pokerlanka.pokie_talkie.telecom.TelecomCallManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PokieLocalService : Service() {

    private val tag = "PokieLocalService"

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isCallActive = false
    private var isVideoCallActive = false
    private var isScreenSharingActive = false
    private var activePeerName = "Family Member"
    private var activeCallId = ""
    private var callStartTimeMs = System.currentTimeMillis()

    companion object {
        const val CHANNEL_SERVICE_ID = "pokie_proximity_service"
        const val CHANNEL_ONGOING_CALL_ID = "pokie_ongoing_calls_v1"
        const val NOTIFICATION_ID = 52220

        const val ACTION_START = "com.pokerlanka.pokie_talkie.service.ACTION_START"
        const val ACTION_STOP = "com.pokerlanka.pokie_talkie.service.ACTION_STOP"
        const val ACTION_CALL_STARTED = "com.pokerlanka.pokie_talkie.service.ACTION_CALL_STARTED"
        const val ACTION_CALL_ENDED = "com.pokerlanka.pokie_talkie.service.ACTION_CALL_ENDED"
        const val ACTION_SCREEN_SHARE_CHANGED = "com.pokerlanka.pokie_talkie.service.ACTION_SCREEN_SHARE_CHANGED"

        private val _isMediaProjectionForegroundActive = MutableStateFlow(false)
        val isMediaProjectionForegroundActive: StateFlow<Boolean> = _isMediaProjectionForegroundActive.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, PokieLocalService::class.java).apply {
                action = ACTION_START
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.d("PokieLocalService", "startService skipped: ${e.message}")
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
        const val EXTRA_PEER_NAME = "extra_peer_name"
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_IS_SCREEN_SHARING = "extra_is_screen_sharing"

        fun notifyCallStarted(
            context: Context,
            isVideo: Boolean = false,
            peerName: String = "Family Member",
            callId: String = ""
        ) {
            val intent = Intent(context, PokieLocalService::class.java).apply {
                action = ACTION_CALL_STARTED
                putExtra(EXTRA_IS_VIDEO, isVideo)
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_CALL_ID, callId)
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

        fun notifyScreenShareChanged(context: Context, active: Boolean) {
            if (!active) {
                _isMediaProjectionForegroundActive.value = false
            }
            val intent = Intent(context, PokieLocalService::class.java).apply {
                action = ACTION_SCREEN_SHARE_CHANGED
                putExtra(EXTRA_IS_SCREEN_SHARING, active)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w("PokieLocalService", "Failed to notify screen share changed: ${e.message}")
            }
        }

        fun notifyCallEnded(context: Context) {
            _isMediaProjectionForegroundActive.value = false
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(tag, "PokieLocalService onStartCommand action: $action")

        when (action) {
            ACTION_STOP -> {
                isCallActive = false
                isVideoCallActive = false
                isScreenSharingActive = false
                _isMediaProjectionForegroundActive.value = false
                releaseLocks()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CALL_STARTED -> {
                isCallActive = true
                isVideoCallActive = intent?.getBooleanExtra(EXTRA_IS_VIDEO, false) ?: false
                activePeerName = intent?.getStringExtra(EXTRA_PEER_NAME)?.takeIf { it.isNotBlank() } ?: "Family Member"
                activeCallId = intent?.getStringExtra(EXTRA_CALL_ID) ?: ""
                callStartTimeMs = System.currentTimeMillis()
                acquireLocks()
                acquireCallWakeLock()
                updateForegroundNotification(inCall = true, isVideo = isVideoCallActive, isScreenSharing = isScreenSharingActive)
            }
            ACTION_SCREEN_SHARE_CHANGED -> {
                isScreenSharingActive = intent?.getBooleanExtra(EXTRA_IS_SCREEN_SHARING, false) ?: false
                isCallActive = true
                updateForegroundNotification(inCall = true, isVideo = isVideoCallActive, isScreenSharing = isScreenSharingActive)
            }
            ACTION_CALL_ENDED -> {
                isCallActive = false
                isVideoCallActive = false
                isScreenSharingActive = false
                _isMediaProjectionForegroundActive.value = false
                releaseCallWakeLock()
                releaseLocks()
                // Downgrade back to idle proximity notification instead of killing the service
                updateForegroundNotification(inCall = false, isVideo = false, isScreenSharing = false)
            }
            ACTION_START -> {
                if (isCallActive) {
                    updateForegroundNotification(inCall = true, isVideo = isVideoCallActive, isScreenSharing = isScreenSharingActive)
                }
            }
        }

        // Ensure listeners are active
        val app = applicationContext as? PokieApp
        app?.appContainer?.ensureListenersActive()

        return START_STICKY
    }

    private fun updateForegroundNotification(
        inCall: Boolean,
        isVideo: Boolean = false,
        isScreenSharing: Boolean = false
    ) {
        val notification = buildServiceNotification(inCall, isVideo, isScreenSharing)

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
                    if (isScreenSharing) {
                        callServiceType = callServiceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    }

                    try {
                        startForeground(NOTIFICATION_ID, notification, callServiceType)
                        _isMediaProjectionForegroundActive.value = isScreenSharing
                        return
                    } catch (e: Exception) {
                        Log.w(tag, "startForeground with callServiceType failed ($e), falling back")
                        if (isScreenSharing) {
                            try {
                                startForeground(
                                    NOTIFICATION_ID,
                                    notification,
                                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                                )
                                _isMediaProjectionForegroundActive.value = true
                                return
                            } catch (e2: Exception) {
                                Log.e(tag, "Fallback MEDIA_PROJECTION startForeground failed: ${e2.message}")
                            }
                        }
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
                _isMediaProjectionForegroundActive.value = isScreenSharing
            }
        } catch (e: Exception) {
            Log.w(tag, "startForeground failed, falling back to simple startForeground: ${e.message}")
            try {
                startForeground(NOTIFICATION_ID, notification)
                _isMediaProjectionForegroundActive.value = isScreenSharing
            } catch (e2: Exception) {
                Log.e(tag, "Failed to startForeground: ${e2.message}")
            }
        }
    }

    private fun buildServiceNotification(
        inCall: Boolean,
        isVideo: Boolean = false,
        isScreenSharing: Boolean = false
    ): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            52001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (inCall) {
            val hangUpIntent = Intent(this, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_HANGUP_CALL
                putExtra(TelecomCallManager.EXTRA_CALL_ID, activeCallId)
            }
            val hangUpPendingIntent = PendingIntent.getBroadcast(
                this,
                52002,
                hangUpIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val person = Person.Builder()
                .setName(activePeerName)
                .setImportant(true)
                .build()

            val builder = NotificationCompat.Builder(this, CHANNEL_ONGOING_CALL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(activePeerName)
                .setContentText(
                    when {
                        isScreenSharing -> "Sharing your screen"
                        isVideo -> "Video call in progress"
                        else -> "Voice call in progress"
                    }
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setUsesChronometer(true)
                .setWhen(callStartTimeMs)
                .setContentIntent(pendingIntent)
                .setStyle(
                    NotificationCompat.CallStyle.forOngoingCall(person, hangUpPendingIntent)
                        .setIsVideo(isVideo || isScreenSharing)
                )

            if (isScreenSharing) {
                val stopShareIntent = Intent(this, CallActionReceiver::class.java).apply {
                    action = CallActionReceiver.ACTION_STOP_SCREEN_SHARE
                }
                val stopSharePendingIntent = PendingIntent.getBroadcast(
                    this,
                    52003,
                    stopShareIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    R.drawable.ic_launcher_foreground,
                    "Stop Sharing",
                    stopSharePendingIntent
                )
            }

            return builder.build()
        }

        return NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Pokie-Talkie Proximity Active")
            .setContentText("Ready for LAN & Bluetooth family calls & messages")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createServiceNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Pokie Proximity Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps local WiFi and Bluetooth listeners active for incoming calls and messages"
                setShowBadge(false)
            }
            val ongoingCallChannel = NotificationChannel(
                CHANNEL_ONGOING_CALL_ID,
                getString(R.string.channel_ongoing_calls_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.channel_ongoing_calls_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannels(listOf(serviceChannel, ongoingCallChannel))
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
