package com.pokerlanka.pokie_talkie.telecom

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.pokerlanka.pokie_talkie.R
import com.pokerlanka.pokie_talkie.data.model.CallSession
import com.pokerlanka.pokie_talkie.data.model.CallState
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.repository.CallRepository
import com.pokerlanka.pokie_talkie.ui.call.IncomingCallActivity

import com.pokerlanka.pokie_talkie.service.PokieLocalService
import com.pokerlanka.pokie_talkie.webrtc.WebRtcManager

class TelecomCallManager(
    private val context: Context,
    private val callRepository: CallRepository,
    private val webRtcManager: WebRtcManager,
    val ringtoneVibrationManager: RingtoneVibrationManager
) {
    private val tag = "TelecomCallManager"
    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val PHONE_ACCOUNT_ID = "pokie_talkie_phone_account"
        const val EXTRA_PEER_ID = "extra_peer_id"
        const val EXTRA_PEER_NAME = "extra_peer_name"
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_CALL_TYPE = "extra_call_type"

        const val CHANNEL_CALLS_ID = "pokie_incoming_calls"
        const val NOTIFICATION_CALL_ID = 9999
    }

    val phoneAccountHandle: PhoneAccountHandle by lazy {
        val componentName = ComponentName(context, PokieConnectionService::class.java)
        PhoneAccountHandle(componentName, PHONE_ACCOUNT_ID)
    }

    var onBluetoothCallInitiated: ((recipientId: String, recipientName: String, callId: String) -> Unit)? = null

    fun registerPhoneAccount() {
        try {
            val builder = PhoneAccount.builder(phoneAccountHandle, context.getString(R.string.phone_account_label))
                .setCapabilities(
                    PhoneAccount.CAPABILITY_SELF_MANAGED or
                    PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING
                )
                .setShortDescription("Pokie Talkie LAN Calling")
                .setHighlightColor(context.getColor(R.color.pokie_primary))

            val phoneAccount = builder.build()
            telecomManager.registerPhoneAccount(phoneAccount)
            Log.i(tag, "PhoneAccount registered successfully with TelecomManager")
        } catch (e: Exception) {
            Log.e(tag, "Error registering PhoneAccount: ${e.message}")
        }
    }

    fun handleIncomingCall(
        callerId: String,
        callerName: String,
        callType: CallType,
        callId: String,
        isBluetoothVoice: Boolean = false
    ) {
        Log.i(tag, "Reporting incoming call from $callerName ($callerId) [Type: $callType, ID: $callId, isBt: $isBluetoothVoice]")

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val isScreenOffOrLocked = (keyguardManager?.isKeyguardLocked == true) || (powerManager?.isInteractive == false)
        val isForeground = com.pokerlanka.pokie_talkie.PokieApp.isAppInForeground

        PokieLocalService.notifyCallStarted(context)
        ringtoneVibrationManager.startIncomingRing()

        if (isScreenOffOrLocked) {
            // Wake screen & CPU so lockscreen full-screen activity can be displayed immediately
            wakeUpScreen()
            try {
                val directIntent = Intent(context, IncomingCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NO_USER_ACTION
                    putExtra(EXTRA_PEER_ID, callerId)
                    putExtra(EXTRA_PEER_NAME, callerName)
                    putExtra(EXTRA_CALL_TYPE, callType.name)
                    putExtra(EXTRA_CALL_ID, callId)
                }
                context.startActivity(directIntent)
                Log.i(tag, "Directly launched IncomingCallActivity on lockscreen/screen off")
            } catch (e: Exception) {
                Log.w(tag, "Direct startActivity for lockscreen incoming call failed: ${e.message}")
            }
        }

        val session = CallSession(
            callId = callId,
            peerId = callerId,
            peerName = callerName,
            callType = callType,
            isIncoming = true,
            state = CallState.RINGING_INCOMING,
            isBluetoothVoice = isBluetoothVoice
        )
        callRepository.setCallSession(session)

        val extras = Bundle().apply {
            putString(EXTRA_PEER_ID, callerId)
            putString(EXTRA_PEER_NAME, callerName)
            putString(EXTRA_CALL_ID, callId)
            putString(EXTRA_CALL_TYPE, callType.name)
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
            if (callType == CallType.VIDEO) {
                putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL)
            }
        }

        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.MANAGE_OWN_CALLS) == PackageManager.PERMISSION_GRANTED) {
                telecomManager.addNewIncomingCall(phoneAccountHandle, extras)
                Log.d(tag, "addNewIncomingCall dispatched to TelecomManager")
            }
        } catch (e: Exception) {
            Log.e(tag, "TelecomManager addNewIncomingCall failed: ${e.message}")
        }

        // - In-App: App is in foreground -> Compose UI navigates to full-screen CallScreen without top notification banner
        // - Minimized / Locked: Heads-up notification or Full-screen lockscreen activity
        if (!isForeground) {
            showIncomingCallNotification(
                callerId = callerId,
                callerName = callerName,
                callType = callType,
                callId = callId,
                isScreenOffOrLocked = isScreenOffOrLocked
            )
        }
    }

    private fun wakeUpScreen() {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val wakeLock = powerManager?.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                android.os.PowerManager.ON_AFTER_RELEASE,
                "pokie_talkie:incoming_call_wake"
            )
            wakeLock?.acquire(15000L) // 15 seconds
        } catch (e: Exception) {
            Log.w(tag, "Could not acquire WakeLock: ${e.message}")
        }
    }

    fun startOutgoingCall(
        recipientId: String,
        recipientName: String,
        callType: CallType,
        callId: String,
        isBluetoothVoice: Boolean = false
    ) {
        Log.i(tag, "Starting outgoing call to $recipientName ($recipientId), isBt: $isBluetoothVoice")

        val session = CallSession(
            callId = callId,
            peerId = recipientId,
            peerName = recipientName,
            callType = callType,
            isIncoming = false,
            state = CallState.RINGING_OUTGOING,
            isBluetoothVoice = isBluetoothVoice
        )
        PokieLocalService.notifyCallStarted(context)
        callRepository.setCallSession(session)
        ringtoneVibrationManager.startRingbackTone()

        if (!isBluetoothVoice) {
            // Initialize WebRTC pipeline directly for immediate local media capture and Offer creation
            try {
                webRtcManager.startOutgoingCall(callType)
                Log.i(tag, "Direct WebRTC outgoing media & offer pipeline initialized for $callType")
            } catch (e: Exception) {
                Log.e(tag, "Error starting outgoing WebRTC media pipeline: ${e.message}", e)
            }
        } else {
            // Bluetooth voice call: trigger direct Bluetooth call offer without WebRTC
            Log.i(tag, "Triggering Bluetooth voice call offer to $recipientId")
            onBluetoothCallInitiated?.invoke(recipientId, recipientName, callId)
        }

        val extras = Bundle().apply {
            putString(EXTRA_PEER_ID, recipientId)
            putString(EXTRA_PEER_NAME, recipientName)
            putString(EXTRA_CALL_ID, callId)
            putString(EXTRA_CALL_TYPE, callType.name)
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
            if (callType == CallType.VIDEO) {
                putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL)
            }
        }

        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.MANAGE_OWN_CALLS) == PackageManager.PERMISSION_GRANTED) {
                val uri = Uri.fromParts("tel", recipientId, null)
                telecomManager.placeCall(uri, extras)
            }
        } catch (e: Exception) {
            Log.w(tag, "Optional TelecomManager placeCall skipped or failed: ${e.message}")
        }
    }

    fun showIncomingCallNotification(
        callerId: String,
        callerName: String,
        callType: CallType,
        callId: String,
        @Suppress("UNUSED_PARAMETER") isScreenOffOrLocked: Boolean = true
    ) {
        val fullscreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PEER_ID, callerId)
            putExtra(EXTRA_PEER_NAME, callerName)
            putExtra(EXTRA_CALL_TYPE, callType.name)
            putExtra(EXTRA_CALL_ID, callId)
        }

        val pendingFullscreenIntent = PendingIntent.getActivity(
            context,
            101,
            fullscreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Action PendingIntent
        val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE_CALL
            putExtra(EXTRA_PEER_ID, callerId)
            putExtra(EXTRA_CALL_ID, callId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Answer Action PendingIntent
        val answerIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_ANSWER_CALL
            putExtra(EXTRA_PEER_ID, callerId)
            putExtra(EXTRA_PEER_NAME, callerName)
            putExtra(EXTRA_CALL_TYPE, callType.name)
            putExtra(EXTRA_CALL_ID, callId)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context,
            103,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val builder = NotificationCompat.Builder(context, CHANNEL_CALLS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Incoming ${if (callType == CallType.VIDEO) "Video" else "Voice"} Call")
            .setContentText("$callerName is calling...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSound(ringtoneUri)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setContentIntent(pendingFullscreenIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Decline", declinePendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Answer", answerPendingIntent)

        // Always set full-screen intent so Android displays a heads-up floating banner
        // when device is in use, or launches the full-screen IncomingCallActivity on lockscreen
        builder.setFullScreenIntent(pendingFullscreenIntent, true)

        val notification = builder.build()
        notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT

        notificationManager.notify(NOTIFICATION_CALL_ID, notification)
    }

    fun dismissCallNotification() {
        notificationManager.cancel(NOTIFICATION_CALL_ID)
        ringtoneVibrationManager.stopAll()
        PokieLocalService.notifyCallEnded(context)
    }
}
