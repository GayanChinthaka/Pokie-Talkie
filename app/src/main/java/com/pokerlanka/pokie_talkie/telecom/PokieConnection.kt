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
    private val callController: CallController
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
        com.pokerlanka.pokie_talkie.service.PokieLocalService.notifyCallStarted(context, isVideo = (callType == CallType.VIDEO))
        webRtcManager.answerIncomingCall(callType)
    }

    override fun onReject() {
        Log.i(tag, "Telecom onReject called for call $callId")
        callController.endCall(EndReason.REJECTED, notifyPeer = true, expectedCallId = callId)
    }

    override fun onDisconnect() {
        Log.i(tag, "Telecom onDisconnect called for call $callId")
        callController.endCall(EndReason.LOCAL_HANGUP, notifyPeer = true, expectedCallId = callId)
    }

    override fun onAbort() {
        Log.i(tag, "Telecom onAbort called for call $callId")
        callController.endCall(EndReason.LOCAL_HANGUP, notifyPeer = true, expectedCallId = callId)
    }

    override fun onCallAudioStateChanged(state: CallAudioState?) {
        Log.d(tag, "Telecom audio state changed: isMuted=${state?.isMuted}, route=${state?.route}")
        state?.let {
            webRtcManager.setAudioMuted(it.isMuted)
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
