package com.pokerlanka.pokie_talkie.ui.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pokerlanka.pokie_talkie.data.model.CallSession
import com.pokerlanka.pokie_talkie.data.model.CallState
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.repository.CallRepository
import com.pokerlanka.pokie_talkie.fcm.FcmSignalingClient
import com.pokerlanka.pokie_talkie.telecom.PokieConnectionService
import com.pokerlanka.pokie_talkie.telecom.TelecomCallManager
import com.pokerlanka.pokie_talkie.webrtc.WebRtcManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.VideoTrack

class CallViewModel(
    private val callRepository: CallRepository,
    val webRtcManager: WebRtcManager,
    private val fcmSignalingClient: FcmSignalingClient,
    private val telecomCallManager: TelecomCallManager
) : ViewModel() {

    val currentCall: StateFlow<CallSession?> = callRepository.currentCall
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val localVideoTrack: StateFlow<VideoTrack?> = webRtcManager.localVideoTrackFlow
    val remoteVideoTrack: StateFlow<VideoTrack?> = webRtcManager.remoteVideoTrackFlow
    val isMuted: StateFlow<Boolean> = webRtcManager.isAudioMuted
    val isVideoEnabled: StateFlow<Boolean> = webRtcManager.isVideoEnabled
    val isSpeakerOn: StateFlow<Boolean> = webRtcManager.isSpeakerOn
    val isCallConnected: StateFlow<Boolean> = webRtcManager.isCallConnected

    val eglBaseContext: EglBase.Context = webRtcManager.eglBase.eglBaseContext

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            callRepository.currentCall.collect { session ->
                if (session?.state == CallState.CONNECTED) {
                    startTimer()
                } else if (session?.state == CallState.DISCONNECTED || session?.state == CallState.REJECTED) {
                    stopTimer()
                }
            }
        }

        viewModelScope.launch {
            webRtcManager.isCallConnected.collect { connected ->
                if (connected) {
                    callRepository.updateCallState(CallState.CONNECTED)
                }
            }
        }
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            var seconds = 0L
            while (isActive) {
                delay(1000)
                seconds++
                callRepository.updateDuration(seconds)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun answerCall() {
        val session = currentCall.value
        val callType = session?.callType ?: CallType.VOICE
        val activeConn = PokieConnectionService.activeConnection
        if (activeConn != null) {
            activeConn.onAnswer(0)
        } else {
            callRepository.updateCallState(CallState.CONNECTED)
            webRtcManager.answerIncomingCall(callType)
        }
        telecomCallManager.dismissCallNotification()
    }

    fun toggleMute() {
        webRtcManager.toggleAudioMute()
    }

    fun toggleVideo() {
        webRtcManager.toggleVideo()
    }

    fun toggleSpeaker() {
        webRtcManager.toggleSpeaker()
    }

    fun switchCamera() {
        webRtcManager.switchCamera()
    }

    fun hangupCall() {
        val session = currentCall.value
        if (session != null) {
            fcmSignalingClient.sendHangup(session.peerId, session.callId)
        }
        webRtcManager.endCall()
        PokieConnectionService.activeConnection?.markDisconnected()
        telecomCallManager.dismissCallNotification()
        viewModelScope.launch {
            callRepository.endCallAndLog()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }

    companion object {
        fun provideFactory(
            callRepository: CallRepository,
            webRtcManager: WebRtcManager,
            fcmSignalingClient: FcmSignalingClient,
            telecomCallManager: TelecomCallManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CallViewModel(callRepository, webRtcManager, fcmSignalingClient, telecomCallManager) as T
            }
        }
    }
}
