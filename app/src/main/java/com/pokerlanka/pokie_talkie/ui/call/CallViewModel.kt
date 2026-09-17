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
import com.pokerlanka.pokie_talkie.bluetooth.BluetoothVoiceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.VideoTrack

class CallViewModel(
    private val callRepository: CallRepository,
    val webRtcManager: WebRtcManager,
    private val fcmSignalingClient: FcmSignalingClient,
    private val telecomCallManager: TelecomCallManager,
    val bluetoothVoiceManager: BluetoothVoiceManager,
    private val ringtoneVibrationManager: RingtoneVibrationManager
) : ViewModel() {

    val currentCall: StateFlow<CallSession?> = callRepository.currentCall
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _btMuted = MutableStateFlow(false)
    private val _btSpeakerOn = MutableStateFlow(false)

    val localVideoTrack: StateFlow<VideoTrack?> = webRtcManager.localVideoTrackFlow
    val remoteVideoTrack: StateFlow<VideoTrack?> = webRtcManager.remoteVideoTrackFlow

    val isMuted: StateFlow<Boolean> = combine(
        webRtcManager.isAudioMuted,
        _btMuted,
        callRepository.currentCall
    ) { webrtcMuted, btMuted, session ->
        if (session?.isBluetoothVoice == true) btMuted else webrtcMuted
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isVideoEnabled: StateFlow<Boolean> = webRtcManager.isVideoEnabled
    val isRemoteVideoEnabled: StateFlow<Boolean> = webRtcManager.isRemoteVideoEnabled
    val videoUpgradeRequest: StateFlow<String?> = callRepository.videoUpgradeRequest
    val uiMessage: SharedFlow<String> = callRepository.uiMessage

    val isSpeakerOn: StateFlow<Boolean> = combine(
        webRtcManager.isSpeakerOn,
        _btSpeakerOn,
        callRepository.currentCall
    ) { webrtcSpeaker, btSpeaker, session ->
        if (session?.isBluetoothVoice == true) btSpeaker else webrtcSpeaker
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isCallConnected: StateFlow<Boolean> = webRtcManager.isCallConnected

    val eglBaseContext: EglBase.Context = webRtcManager.eglBase.eglBaseContext

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            callRepository.currentCall.collect { session ->
                if (session?.state == CallState.CONNECTED) {
                    ringtoneVibrationManager.stopAll()
                    startTimer()
                } else if (session?.state == CallState.DISCONNECTED || session?.state == CallState.REJECTED) {
                    ringtoneVibrationManager.stopAll()
                    stopTimer()
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
        ringtoneVibrationManager.stopAll()
        val session = currentCall.value
        val isBt = session?.isBluetoothVoice == true
        if (isBt) {
            callRepository.updateCallState(CallState.CONNECTED)
            session?.peerId?.let { peerId ->
                fcmSignalingClient.sendBluetoothCallAnswer(peerId, session.callId)
            }
            bluetoothVoiceManager.startVoiceServer()
        } else {
            val callType = session?.callType ?: CallType.VOICE
            val activeConn = PokieConnectionService.activeConnection
            if (activeConn != null) {
                activeConn.onAnswer(0)
            } else {
                callRepository.updateCallState(CallState.CONNECTED)
                webRtcManager.answerIncomingCall(callType)
            }
        }
        telecomCallManager.dismissCallNotification()
    }

    fun toggleMute() {
        val session = currentCall.value
        if (session?.isBluetoothVoice == true) {
            bluetoothVoiceManager.isMuted = !bluetoothVoiceManager.isMuted
            _btMuted.value = bluetoothVoiceManager.isMuted
        } else {
            webRtcManager.toggleAudioMute()
        }
    }

    fun toggleVideo() {
        val newVideo = webRtcManager.toggleVideo()
        val session = currentCall.value
        if (session != null && !session.isBluetoothVoice) {
            fcmSignalingClient.sendCameraState(session.peerId, session.callId, newVideo)
        }
    }

    fun requestSwitchToVideo() {
        val session = currentCall.value ?: return
        if (session.isBluetoothVoice) {
            callRepository.emitMessage("Video calls require Wi-Fi LAN connection")
            return
        }
        fcmSignalingClient.sendSwitchToVideoRequest(session.peerId, session.callId)
        callRepository.emitMessage("Requesting video call...")
    }

    fun acceptSwitchToVideo() {
        val session = currentCall.value ?: return
        callRepository.setVideoUpgradeRequest(null)
        callRepository.updateCallType(CallType.VIDEO)
        webRtcManager.upgradeToVideo()
        fcmSignalingClient.sendSwitchToVideoAccept(session.peerId, session.callId)
        webRtcManager.createRenegotiationOffer { sdp ->
            fcmSignalingClient.sendRenegotiationOffer(session.peerId, session.callId, sdp)
        }
    }

    fun declineSwitchToVideo() {
        val session = currentCall.value ?: return
        callRepository.setVideoUpgradeRequest(null)
        fcmSignalingClient.sendSwitchToVideoDecline(session.peerId, session.callId)
    }

    fun toggleSpeaker() {
        val session = currentCall.value
        if (session?.isBluetoothVoice == true) {
            val nextSpeaker = !_btSpeakerOn.value
            bluetoothVoiceManager.setSpeakerphone(nextSpeaker)
            _btSpeakerOn.value = nextSpeaker
        } else {
            webRtcManager.toggleSpeaker()
        }
    }

    fun switchCamera() {
        webRtcManager.switchCamera()
    }

    fun hangupCall() {
        val session = currentCall.value
        if (session != null) {
            fcmSignalingClient.sendHangup(session.peerId, session.callId)
        }
        if (session?.isBluetoothVoice == true) {
            bluetoothVoiceManager.stopDuplexAudio()
        } else {
            webRtcManager.endCall()
        }
        PokieConnectionService.activeConnection?.markDisconnected()
        telecomCallManager.dismissCallNotification()
        viewModelScope.launch {
            callRepository.endCallAndLog()
        }
        ringtoneVibrationManager.stopAll()
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
        ringtoneVibrationManager.stopAll()
    }

    companion object {
        fun provideFactory(
            callRepository: CallRepository,
            webRtcManager: WebRtcManager,
            fcmSignalingClient: FcmSignalingClient,
            telecomCallManager: TelecomCallManager,
            bluetoothVoiceManager: BluetoothVoiceManager,
            ringtoneVibrationManager: RingtoneVibrationManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CallViewModel(callRepository, webRtcManager, fcmSignalingClient, telecomCallManager, bluetoothVoiceManager, ringtoneVibrationManager) as T
            }
        }
    }
}
