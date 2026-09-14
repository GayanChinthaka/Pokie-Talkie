package com.pokerlanka.pokie_talkie.webrtc

import android.content.Context
import android.util.Log
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.IceCandidateModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class WebRtcManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val tag = "WebRtcManager"

    val eglBase: EglBase = EglBase.create()
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    val audioDeviceManager = WebRtcAudioDeviceManager(context)

    private val _localVideoTrackFlow = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackFlow: StateFlow<VideoTrack?> = _localVideoTrackFlow.asStateFlow()

    private val _remoteVideoTrackFlow = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrackFlow: StateFlow<VideoTrack?> = _remoteVideoTrackFlow.asStateFlow()

    private val _isCallConnected = MutableStateFlow(false)
    val isCallConnected: StateFlow<Boolean> = _isCallConnected.asStateFlow()

    private val _isAudioMuted = MutableStateFlow(false)
    val isAudioMuted: StateFlow<Boolean> = _isAudioMuted.asStateFlow()

    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()

    private val _isRemoteVideoEnabled = MutableStateFlow(true)
    val isRemoteVideoEnabled: StateFlow<Boolean> = _isRemoteVideoEnabled.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    var onLocalSdpCreated: ((SessionDescription) -> Unit)? = null
    var onLocalIceCandidate: ((IceCandidateModel) -> Unit)? = null
    var onCallDisconnected: (() -> Unit)? = null

    var pendingRemoteOfferSdp: String? = null
    private val pendingIceCandidates = mutableListOf<IceCandidateModel>()

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                networkIgnoreMask = 0
            })
            .createPeerConnectionFactory()
    }

    fun startLocalMedia(callType: CallType) {
        val factory = peerConnectionFactory ?: return

        if (localAudioTrack != null && (callType == CallType.VOICE || localVideoTrack != null)) {
            Log.d(tag, "Local media is already running for $callType")
            return
        }

        // Audio Constraints (Echo cancellation, High pass filter, Auto gain)
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }

        if (localAudioTrack == null) {
            localAudioSource = factory.createAudioSource(audioConstraints)
            localAudioTrack = factory.createAudioTrack("ARDAMSa0", localAudioSource).apply {
                setEnabled(true)
            }
        }

        if (callType == CallType.VIDEO && localVideoTrack == null) {
            try {
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                val capturer = createCameraCapturer()
                if (capturer != null) {
                    videoCapturer = capturer
                    localVideoSource = factory.createVideoSource(capturer.isScreencast)
                    capturer.initialize(surfaceTextureHelper, context.applicationContext, localVideoSource!!.capturerObserver)
                    try {
                        capturer.startCapture(1280, 720, 30)
                    } catch (e: Exception) {
                        Log.w(tag, "Failed 720p capture, falling back to 640x480: ${e.message}")
                        capturer.startCapture(640, 480, 30)
                    }

                    localVideoTrack = factory.createVideoTrack("ARDAMSv0", localVideoSource).apply {
                        setEnabled(true)
                    }
                    _localVideoTrackFlow.value = localVideoTrack
                    Log.i(tag, "Local video track created and started successfully")
                } else {
                    Log.w(tag, "Camera capturer returned null (check CAMERA permission or camera availability)")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error setting up local video track: ${e.message}", e)
            }
        }

        audioDeviceManager.startCallAudio(enableSpeaker = (callType == CallType.VIDEO))
        _isSpeakerOn.value = audioDeviceManager.isSpeakerphoneOn()
    }

    fun createPeerConnection() {
        if (peerConnection != null) {
            Log.d(tag, "PeerConnection already exists, skipping recreate")
            return
        }
        val factory = peerConnectionFactory ?: return

        // Configure ICE Servers with Google STUN + LAN direct
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(tag, "onSignalingChange: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(tag, "onIceConnectionChange: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        _isCallConnected.value = true
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> {
                        _isCallConnected.value = false
                        onCallDisconnected?.invoke()
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(tag, "onIceGatheringChange: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    Log.d(tag, "Local ICE candidate generated: ${candidate.sdp}")
                    val model = IceCandidateModel(
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex,
                        candidate = candidate.sdp
                    )
                    onLocalIceCandidate?.invoke(model)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                Log.d(tag, "onAddStream with ${stream?.videoTracks?.size ?: 0} video tracks")
                if (stream != null && stream.videoTracks.isNotEmpty()) {
                    val remoteTrack = stream.videoTracks[0]
                    _remoteVideoTrackFlow.value = remoteTrack
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {
                _remoteVideoTrackFlow.value = null
            }

            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {
                Log.d(tag, "onRenegotiationNeeded")
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.d(tag, "onAddTrack")
                val track = receiver?.track()
                if (track is VideoTrack) {
                    _remoteVideoTrackFlow.value = track
                }
            }
        }

        peerConnection = factory.createPeerConnection(rtcConfig, observer)

        // Add local tracks
        localAudioTrack?.let {
            peerConnection?.addTrack(it, listOf("ARDAMS"))
        }
        localVideoTrack?.let {
            peerConnection?.addTrack(it, listOf("ARDAMS"))
        }

        // Drain any pending ICE candidates received before peerConnection creation
        synchronized(pendingIceCandidates) {
            for (pending in pendingIceCandidates) {
                val candidate = IceCandidate(pending.sdpMid, pending.sdpMLineIndex, pending.candidate)
                peerConnection?.addIceCandidate(candidate)
            }
            pendingIceCandidates.clear()
        }
    }

    fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { sdp ->
                    Log.d(tag, "Created local offer SDP")
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(tag, "Set local description success for Offer")
                            onLocalSdpCreated?.invoke(sdp)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(error: String?) {
                            Log.e(tag, "Set local description failure: $error")
                        }
                    }, sdp)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(tag, "Create offer failure: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { sdp ->
                    Log.d(tag, "Created local answer SDP")
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(tag, "Set local description success for Answer")
                            onLocalSdpCreated?.invoke(sdp)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(error: String?) {
                            Log.e(tag, "Set local description failure for answer: $error")
                        }
                    }, sdp)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(tag, "Create answer failure: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    fun setRemoteDescription(sdpString: String, type: SessionDescription.Type) {
        val sessionDescription = SessionDescription(type, sdpString)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(tag, "Set remote description success for $type")
                // Drain any pending ICE candidates now that remote description is set
                synchronized(pendingIceCandidates) {
                    for (pending in pendingIceCandidates) {
                        val candidate = IceCandidate(pending.sdpMid, pending.sdpMLineIndex, pending.candidate)
                        peerConnection?.addIceCandidate(candidate)
                        Log.d(tag, "Flushed buffered ICE candidate: ${pending.candidate}")
                    }
                    pendingIceCandidates.clear()
                }
                if (type == SessionDescription.Type.OFFER) {
                    createAnswer()
                }
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(tag, "Set remote description failure: $error")
            }
        }, sessionDescription)
    }

    fun startOutgoingCall(callType: CallType) {
        Log.i(tag, "startOutgoingCall initiated for $callType")
        startLocalMedia(callType)
        createPeerConnection()
        createOffer()
    }

    fun answerIncomingCall(callType: CallType) {
        Log.i(tag, "answerIncomingCall initiated for $callType")
        startLocalMedia(callType)
        createPeerConnection()
        pendingRemoteOfferSdp?.let { sdp ->
            Log.d(tag, "Applying stored pending remote offer SDP on answer")
            setRemoteDescription(sdp, SessionDescription.Type.OFFER)
        }
    }

    fun addRemoteIceCandidate(model: IceCandidateModel) {
        val pc = peerConnection
        if (pc != null && pc.remoteDescription != null) {
            val candidate = IceCandidate(model.sdpMid, model.sdpMLineIndex, model.candidate)
            pc.addIceCandidate(candidate)
            Log.d(tag, "Added remote ICE candidate directly: ${model.candidate}")
        } else {
            Log.d(tag, "Buffering remote ICE candidate as PeerConnection or remoteDescription is not ready yet")
            synchronized(pendingIceCandidates) {
                pendingIceCandidates.add(model)
            }
        }
    }

    fun toggleAudioMute(): Boolean {
        val newMute = !_isAudioMuted.value
        _isAudioMuted.value = newMute
        localAudioTrack?.setEnabled(!newMute)
        audioDeviceManager.setMicrophoneMute(newMute)
        return newMute
    }

    fun toggleVideo(): Boolean {
        val newVideo = !_isVideoEnabled.value
        _isVideoEnabled.value = newVideo
        localVideoTrack?.setEnabled(newVideo)
        return newVideo
    }

    fun setRemoteVideoEnabled(enabled: Boolean) {
        _isRemoteVideoEnabled.value = enabled
        Log.i(tag, "Remote video enabled set to: $enabled")
    }

    fun upgradeToVideo() {
        Log.i(tag, "Upgrading active call to VIDEO")
        val factory = peerConnectionFactory ?: return

        // 1. Start local camera capturer and video track if not already running
        if (localVideoTrack == null) {
            try {
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                val capturer = createCameraCapturer()
                if (capturer != null) {
                    videoCapturer = capturer
                    localVideoSource = factory.createVideoSource(capturer.isScreencast)
                    capturer.initialize(surfaceTextureHelper, context.applicationContext, localVideoSource!!.capturerObserver)
                    try {
                        capturer.startCapture(1280, 720, 30)
                    } catch (e: Exception) {
                        Log.w(tag, "Failed 720p capture, falling back to 640x480: ${e.message}")
                        capturer.startCapture(640, 480, 30)
                    }

                    localVideoTrack = factory.createVideoTrack("ARDAMSv0", localVideoSource).apply {
                        setEnabled(true)
                    }
                    _localVideoTrackFlow.value = localVideoTrack
                    _isVideoEnabled.value = true
                    Log.i(tag, "Local video track created and started for video upgrade")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error setting up local video track on upgrade: ${e.message}", e)
            }
        } else {
            localVideoTrack?.setEnabled(true)
            _isVideoEnabled.value = true
        }

        // 2. Attach local video track to existing PeerConnection if not attached
        peerConnection?.let { pc ->
            localVideoTrack?.let { track ->
                val senders = pc.senders
                val hasVideoSender = senders.any { it.track()?.kind() == "video" }
                if (!hasVideoSender) {
                    pc.addTrack(track, listOf("ARDAMS"))
                    Log.i(tag, "Added localVideoTrack to active PeerConnection on upgrade")
                }
            }
        }

        // 3. Switch to speakerphone for video call
        audioDeviceManager.setSpeakerphoneOn(true)
        _isSpeakerOn.value = true
    }

    fun createRenegotiationOffer(onReady: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { sdp ->
                    Log.d(tag, "Created renegotiation offer SDP")
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(tag, "Set local description success for Renegotiation Offer")
                            onReady(sdp)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(error: String?) {
                            Log.e(tag, "Set local description failure for renegotiation offer: $error")
                        }
                    }, sdp)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(tag, "Create renegotiation offer failure: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    fun createRenegotiationAnswer(onReady: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { sdp ->
                    Log.d(tag, "Created renegotiation answer SDP")
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(tag, "Set local description success for Renegotiation Answer")
                            onReady(sdp)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(error: String?) {
                            Log.e(tag, "Set local description failure for renegotiation answer: $error")
                        }
                    }, sdp)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(tag, "Create renegotiation answer failure: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    fun toggleSpeaker(): Boolean {
        val newSpeaker = !_isSpeakerOn.value
        _isSpeakerOn.value = newSpeaker
        audioDeviceManager.setSpeakerphoneOn(newSpeaker)
        return newSpeaker
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // Try front camera first
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }

        // Fallback to back camera
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }
        return null
    }

    fun endCall() {
        try {
            audioDeviceManager.stopCallAudio()

            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            localVideoTrack?.dispose()
            localVideoTrack = null
            _localVideoTrackFlow.value = null

            localAudioTrack?.dispose()
            localAudioTrack = null

            localVideoSource?.dispose()
            localVideoSource = null

            localAudioSource?.dispose()
            localAudioSource = null

            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null

            _remoteVideoTrackFlow.value = null
            _isCallConnected.value = false
            _isAudioMuted.value = false
            _isVideoEnabled.value = true
            _isRemoteVideoEnabled.value = true
        } catch (e: Exception) {
            Log.e(tag, "Error closing WebRTC session: ${e.message}")
        }
    }
}
