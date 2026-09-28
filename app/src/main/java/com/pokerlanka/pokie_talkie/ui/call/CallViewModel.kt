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
import com.pokerlanka.pokie_talkie.telecom.RingtoneVibrationManager
import com.pokerlanka.pokie_talkie.webrtc.WebRtcManager
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.VideoTrack

import com.pokerlanka.pokie_talkie.telecom.CallController
import com.pokerlanka.pokie_talkie.telecom.EndReason

class CallViewModel(
    private val callRepository: CallRepository,
    val webRtcManager: WebRtcManager,
    private val fcmSignalingClient: FcmSignalingClient,
    private val telecomCallManager: TelecomCallManager,
    private val ringtoneVibrationManager: RingtoneVibrationManager,
    private val callController: CallController
) : ViewModel() {

    val currentCall: StateFlow<CallSession?> = callRepository.currentCall
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val localVideoTrack: StateFlow<VideoTrack?> = webRtcManager.localVideoTrackFlow
    val remoteVideoTrack: StateFlow<VideoTrack?> = webRtcManager.remoteVideoTrackFlow

    val isMuted: StateFlow<Boolean> = webRtcManager.isAudioMuted

    val isVideoEnabled: StateFlow<Boolean> = webRtcManager.isVideoEnabled
    val isRemoteVideoEnabled: StateFlow<Boolean> = webRtcManager.isRemoteVideoEnabled
    val videoUpgradeRequest: StateFlow<String?> = callRepository.videoUpgradeRequest
    val uiMessage: SharedFlow<String> = callRepository.uiMessage

    val isSpeakerOn: StateFlow<Boolean> = webRtcManager.isSpeakerOn

    val isCallConnected: StateFlow<Boolean> = webRtcManager.isCallConnected

    val eglBaseContext: EglBase.Context = webRtcManager.eglBase.eglBaseContext

    init {
        viewModelScope.launch {
            callRepository.currentCall.collect { session ->
                if (session?.state == CallState.CONNECTED) {
                    ringtoneVibrationManager.stopAll()
                }
            }
        }

        viewModelScope.launch {
            webRtcManager.isCallConnected.collect { connected ->
                if (connected) {
                    ringtoneVibrationManager.stopAll()
                    callRepository.updateCallState(CallState.CONNECTED)
                }
            }
        }
    }

    fun answerCall() {
        val session = callRepository.currentCall.value
        if (session == null || session.state != CallState.RINGING_INCOMING) {
            return
        }
        ringtoneVibrationManager.stopAll()
        val callType = session.callType
        val activeConn = PokieConnectionService.activeConnection
        if (activeConn != null) {
            activeConn.onAnswer(0)
        } else {
            callRepository.updateCallState(CallState.CONNECTED)
            com.pokerlanka.pokie_talkie.service.PokieLocalService.notifyCallStarted(telecomCallManager.context, isVideo = (callType == CallType.VIDEO))
            webRtcManager.answerIncomingCall(callType)
        }
        telecomCallManager.dismissIncomingCallUi()
    }

    fun toggleMute() {
        webRtcManager.toggleAudioMute()
    }

    fun toggleVideo() {
        val newVideo = webRtcManager.toggleVideo()
        val session = callRepository.currentCall.value
        if (session != null) {
            fcmSignalingClient.sendCameraState(session.peerId, session.callId, newVideo)
        }
    }

    fun requestSwitchToVideo() {
        val session = callRepository.currentCall.value ?: return
        fcmSignalingClient.sendSwitchToVideoRequest(session.peerId, session.callId)
        callRepository.emitMessage("Requesting video call...")
    }

    fun acceptSwitchToVideo() {
        val session = callRepository.currentCall.value ?: return
        callRepository.setVideoUpgradeRequest(null)
        callRepository.updateCallType(CallType.VIDEO)
        webRtcManager.upgradeToVideo()
        fcmSignalingClient.sendSwitchToVideoAccept(session.peerId, session.callId)
        webRtcManager.createRenegotiationOffer { sdp ->
            fcmSignalingClient.sendRenegotiationOffer(session.peerId, session.callId, sdp)
        }
    }

    fun declineSwitchToVideo() {
        val session = callRepository.currentCall.value ?: return
        callRepository.setVideoUpgradeRequest(null)
        fcmSignalingClient.sendSwitchToVideoDecline(session.peerId, session.callId)
    }

    fun toggleSpeaker() {
        webRtcManager.toggleSpeaker()
    }

    fun switchCamera() {
        webRtcManager.switchCamera()
    }

    fun hangupCall() {
        val session = callRepository.currentCall.value
        callController.endCall(EndReason.LOCAL_HANGUP, notifyPeer = true, expectedCallId = session?.callId)
    }

    override fun onCleared() {
        super.onCleared()
    }

    companion object {
        fun provideFactory(
            callRepository: CallRepository,
            webRtcManager: WebRtcManager,
            fcmSignalingClient: FcmSignalingClient,
            telecomCallManager: TelecomCallManager,
            ringtoneVibrationManager: RingtoneVibrationManager,
            callController: CallController
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CallViewModel(
                    callRepository,
                    webRtcManager,
                    fcmSignalingClient,
                    telecomCallManager,
                    ringtoneVibrationManager,
                    callController
                ) as T
            }
        }
    }
}
