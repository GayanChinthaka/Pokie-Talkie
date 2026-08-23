package com.pokerlanka.pokie_talkie.telecom

import android.content.Context
import android.os.Build
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.util.Log
import com.pokerlanka.pokie_talkie.data.model.CallState
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.repository.CallRepository
import com.pokerlanka.pokie_talkie.webrtc.WebRtcManager

class PokieConnection(
    private val context: Context,
    val callId: String,
    val peerId: String,
    val peerName: String,
    val callType: CallType,
    private val callRepository: CallRepository,
    private val webRtcManager: WebRtcManager,
    private val onEndCallRequested: (String, String) -> Unit
) : Connection() {

    private val tag = "PokieConnection"

    init {
        connectionCapabilities = CAPABILITY_MUTE or CAPABILITY_SUPPORT_HOLD
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            connectionProperties = PROPERTY_SELF_MANAGED
        }
        audioModeIsVoip = true
    }

    override fun onAnswer(videoState: Int) {
        Log.i(tag, "Telecom onAnswer called for call $callId")
        setActive()
        callRepository.updateCallState(CallState.CONNECTED)
        webRtcManager.answerIncomingCall(callType)
    }

    override fun onReject() {
        Log.i(tag, "Telecom onReject called for call $callId")
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        callRepository.updateCallState(CallState.REJECTED)
        onEndCallRequested(peerId, callId)
        destroy()
    }

    override fun onDisconnect() {
        Log.i(tag, "Telecom onDisconnect called for call $callId")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        callRepository.updateCallState(CallState.DISCONNECTED)
        onEndCallRequested(peerId, callId)
        destroy()
    }

    override fun onCallAudioStateChanged(state: CallAudioState?) {
        Log.d(tag, "Telecom audio state changed: isMuted=${state?.isMuted}, route=${state?.route}")
        state?.let {
            if (it.isMuted != webRtcManager.isAudioMuted.value) {
                webRtcManager.toggleAudioMute()
            }
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
    }

    fun markDisconnected(reason: Int = DisconnectCause.REMOTE) {
        setDisconnected(DisconnectCause(reason))
        destroy()
    }
}
