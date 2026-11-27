package com.pokerlanka.pokie_talkie.telecom

import android.content.Context
import android.os.Build
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.util.Log
import com.pokerlanka.pokie_talkie.PokieApp
import com.pokerlanka.pokie_talkie.data.model.CallState
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.repository.CallRepository
import com.pokerlanka.pokie_talkie.webrtc.AudioRoute
import com.pokerlanka.pokie_talkie.webrtc.WebRtcManager

class PokieConnection(
    private val context: Context,
    val callId: String,
    val peerId: String,
    val peerName: String,
    val callType: CallType,
    private val callRepository: CallRepository,
    private val webRtcManager: WebRtcManager,
    private val callController: CallController
) : Connection() {

    private val tag = "PokieConnection"
    private var lastTelecomRoute: Int? = null

    init {
        connectionCapabilities = CAPABILITY_MUTE or CAPABILITY_SUPPORT_HOLD
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            connectionProperties = PROPERTY_SELF_MANAGED
        }
        audioModeIsVoip = true
    }

    override fun onAnswer(videoState: Int) {
        Log.i(tag, "Telecom onAnswer called for call $callId")
        val container = (context.applicationContext as PokieApp).appContainer
        val session = callRepository.currentCall.value
        if (session == null || session.callId != callId || session.state != CallState.RINGING_INCOMING) {
            Log.w(tag, "Ignoring onAnswer: call $callId is not ringing (current=${session?.callId}, state=${session?.state})")
            return
        }
        setActive()
        syncAudioRouteFromApp(webRtcManager.audioDeviceManager.activeRoute.value)
        callRepository.updateCallState(CallState.CONNECTED)
        if (session.isBluetoothVoice) {
            // Microphone foreground type, so AudioRecord keeps working when the app is backgrounded
            com.pokerlanka.pokie_talkie.service.PokieLocalService.notifyCallStarted(
                context = context,
                isVideo = false,
                peerName = session.peerName,
                callId = session.callId
            )
            container.fcmSignalingClient.sendBluetoothCallAnswer(session.peerId, session.callId)
            container.bluetoothVoiceManager.startVoiceServer()
        } else {
            com.pokerlanka.pokie_talkie.service.PokieLocalService.notifyCallStarted(
                context = context,
                isVideo = (callType == CallType.VIDEO),
                peerName = session.peerName,
                callId = session.callId
            )
            webRtcManager.answerIncomingCall(callType)
        }
        // Answering from a headset or watch skips CallViewModel, so stop the ring and close the incoming-call notification here
        container.telecomCallManager.dismissIncomingCallUi()
    }

    override fun onReject() {
        Log.i(tag, "Telecom onReject called for call $callId")
        callController.endCall(EndReason.REJECTED, notifyPeer = true, expectedCallId = callId)
    }

    override fun onDisconnect() {
        Log.i(tag, "Telecom onDisconnect called for call $callId")
        // A hangup from a headset, car kit, watch or the system UI: end the call in any ringing or connected state
        callController.endCall(EndReason.LOCAL_HANGUP, notifyPeer = true, expectedCallId = callId)
        // endCall skips stale connections whose callId is no longer current, so make sure this one is released
        release(DisconnectCause.LOCAL)
    }

    override fun onAbort() {
        // Telecom aborted the connection (not a user action): keep the app call running, release only this connection
        Log.w(tag, "Telecom onAbort called for call $callId (keeping app call active)")
        release(DisconnectCause.CANCELED)
    }

    private fun release(cause: Int) {
        if (state != STATE_DISCONNECTED) {
            setDisconnected(DisconnectCause(cause))
            destroy()
        }
        if (PokieConnectionService.activeConnection === this) {
            PokieConnectionService.activeConnection = null
        }
    }

    override fun onCallAudioStateChanged(state: CallAudioState?) {
        Log.d(tag, "Telecom audio state changed: isMuted=${state?.isMuted}, route=${state?.route}")
        state?.let {
            webRtcManager.setAudioMuted(it.isMuted)
            val mappedRoute = when (it.route) {
                CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
                CallAudioState.ROUTE_SPEAKER -> AudioRoute.SPEAKER
                CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
                CallAudioState.ROUTE_EARPIECE -> AudioRoute.EARPIECE
                else -> null
            }
            val prevRoute = lastTelecomRoute
            lastTelecomRoute = it.route

            val appRoute = webRtcManager.audioDeviceManager.activeRoute.value
            if (prevRoute == null) {
                // Initial Telecom audio callback: keep the app's chosen route instead of letting
                // Telecom's default earpiece override Video speaker or early user selection.
                if (mappedRoute != null && mappedRoute != appRoute) {
                    syncAudioRouteFromApp(appRoute)
                }
            } else if (it.route != prevRoute && mappedRoute != null && mappedRoute != appRoute) {
                if (webRtcManager.audioDeviceManager.availableRoutes.value.any { opt -> opt.route == mappedRoute }) {
                    webRtcManager.audioDeviceManager.onSystemTelecomRouteChanged(mappedRoute)
                }
            }
        }
    }

    fun syncAudioRouteFromApp(route: AudioRoute) {
        val telecomRoute = when (route) {
            AudioRoute.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            AudioRoute.SPEAKER -> CallAudioState.ROUTE_SPEAKER
            AudioRoute.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
            AudioRoute.EARPIECE -> CallAudioState.ROUTE_EARPIECE
        }
        try {
            if (callAudioState?.route != telecomRoute) {
                setAudioRoute(telecomRoute)
                Log.d(tag, "Synced Telecom audio route to $route ($telecomRoute)")
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to sync Telecom audio route $route: ${e.message}")
        }
    }

    override fun onSilence() {
        Log.d(tag, "Telecom onSilence called")
    }

    override fun onHold() {
        setOnHold()
    }

    override fun onUnhold() {
        setActive()
    }

    fun markConnected() {
        setActive()
        syncAudioRouteFromApp(webRtcManager.audioDeviceManager.activeRoute.value)
    }

    fun markDisconnected(reason: Int = DisconnectCause.REMOTE) {
        setDisconnected(DisconnectCause(reason))
        destroy()
    }
}
