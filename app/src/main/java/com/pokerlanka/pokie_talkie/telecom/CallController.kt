package com.pokerlanka.pokie_talkie.telecom

import android.telecom.DisconnectCause
import android.util.Log
import com.pokerlanka.pokie_talkie.bluetooth.BluetoothVoiceManager
import com.pokerlanka.pokie_talkie.data.model.CallState
import com.pokerlanka.pokie_talkie.data.repository.CallRepository
import com.pokerlanka.pokie_talkie.fcm.FcmSignalingClient
import com.pokerlanka.pokie_talkie.webrtc.WebRtcManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class EndReason {
    LOCAL_HANGUP,
    REMOTE_HANGUP,
    REJECTED,
    MISSED,
    CONNECTION_FAILED
}

class CallController(
    private val callRepository: CallRepository,
    private val webRtcManager: WebRtcManager,
    private val bluetoothVoiceManager: BluetoothVoiceManager,
    private val telecomCallManager: TelecomCallManager,
    private val fcmSignalingClient: FcmSignalingClient,
    private val scope: CoroutineScope
) {
    private val tag = "CallController"
    private val isEndingCall = AtomicBoolean(false)
    @Volatile
    private var lastEndedCallId: String? = null
    private var ringTimeoutJob: Job? = null
    private val isStarted = AtomicBoolean(false)

    /** Starts the ring-timeout watcher. Called from AppContainer.initialize(), since this class is created lazily. */
    fun start() {
        if (!isStarted.compareAndSet(false, true)) return
        scope.launch {
            callRepository.currentCall.collect { session ->
                if (session == null) {
                    ringTimeoutJob?.cancel()
                    ringTimeoutJob = null
                    return@collect
                }
                when (session.state) {
                    CallState.RINGING_INCOMING -> {
                        startRingTimeout(session.callId, isIncoming = true)
                    }
                    CallState.RINGING_OUTGOING -> {
                        startRingTimeout(session.callId, isIncoming = false)
                    }
                    else -> {
                        ringTimeoutJob?.cancel()
                        ringTimeoutJob = null
                    }
                }
            }
        }
    }

    private fun startRingTimeout(callId: String, isIncoming: Boolean) {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = scope.launch(Dispatchers.Default) {
            delay(45_000)
            val current = callRepository.currentCall.value
            if (current != null && current.callId == callId &&
                (current.state == CallState.RINGING_INCOMING || current.state == CallState.RINGING_OUTGOING)
            ) {
                Log.w(tag, "Ring timeout expired for call $callId, ending call (incoming=$isIncoming)")
                if (isIncoming) {
                    endCall(EndReason.MISSED, notifyPeer = false, expectedCallId = callId)
                } else {
                    endCall(EndReason.LOCAL_HANGUP, notifyPeer = true, expectedCallId = callId)
                }
            }
        }
    }

    fun onCallRinging(callId: String, isIncoming: Boolean) {
        startRingTimeout(callId, isIncoming)
    }

    fun onCallConnected() {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null
    }

    fun endCall(reason: EndReason, notifyPeer: Boolean, expectedCallId: String? = null) {
        val current = callRepository.currentCall.value
        if (current == null || current.state == CallState.IDLE || current.state == CallState.DISCONNECTED ||
            current.state == CallState.REJECTED || current.state == CallState.MISSED
        ) {
            return
        }
        if (expectedCallId != null && current.callId != expectedCallId) {
            return
        }
        if (current.callId == lastEndedCallId) {
            return
        }
        if (!isEndingCall.compareAndSet(false, true)) {
            return
        }

        try {
            val callId = current.callId
            val peerId = current.peerId
            lastEndedCallId = callId

            ringTimeoutJob?.cancel()
            ringTimeoutJob = null

            Log.i(tag, "endCall executing: callId=$callId, reason=$reason, notifyPeer=$notifyPeer")

            // 2. If notifyPeer, send HANGUP to peer
            if (notifyPeer && peerId.isNotBlank() && callId.isNotBlank()) {
                fcmSignalingClient.sendHangup(peerId, callId)
            }

            // 3. Always end WebRTC and Bluetooth voice audio
            try {
                webRtcManager.endCall()
            } catch (e: Exception) {
                Log.e(tag, "Error ending WebRTC: ${e.message}")
            }
            try {
                bluetoothVoiceManager.stopDuplexAudio()
            } catch (e: Exception) {
                Log.e(tag, "Error stopping Bluetooth voice: ${e.message}")
            }

            // 4. Mark Telecom connection disconnected & destroy
            val disconnectCauseCode = when (reason) {
                EndReason.LOCAL_HANGUP -> DisconnectCause.LOCAL
                EndReason.REMOTE_HANGUP -> DisconnectCause.REMOTE
                EndReason.REJECTED -> DisconnectCause.REJECTED
                EndReason.MISSED -> DisconnectCause.MISSED
                EndReason.CONNECTION_FAILED -> DisconnectCause.ERROR
            }
            val activeConn = PokieConnectionService.activeConnection
            if (activeConn != null) {
                try {
                    activeConn.setDisconnected(DisconnectCause(disconnectCauseCode))
                    activeConn.destroy()
                } catch (e: Exception) {
                    Log.e(tag, "Error destroying Telecom connection: ${e.message}")
                }
                PokieConnectionService.activeConnection = null
            }

            // 5. Dismiss UI & cleanup
            telecomCallManager.dismissIncomingCallUi()
            telecomCallManager.cleanupAfterCall()

            // 6. End call in repository and log
            val finalCallState = when (reason) {
                EndReason.REJECTED -> CallState.REJECTED
                EndReason.MISSED -> CallState.MISSED
                else -> CallState.DISCONNECTED
            }
            scope.launch(Dispatchers.IO) {
                callRepository.endCallAndLog(finalCallState)
            }
        } finally {
            isEndingCall.set(false)
        }
    }
}
