package com.pokerlanka.pokie_talkie.webrtc

import android.content.Context
import android.content.Intent
import android.util.Log
import com.pokerlanka.pokie_talkie.BuildConfig
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.IceCandidateModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
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

    val eglBase: EglBase by lazy { EglBase.create() }
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var screenShareCapturer: ScreenShareCapturer? = null
    private var screenSurfaceTextureHelper: SurfaceTextureHelper? = null
    private var screenVideoSource: VideoSource? = null
    private var screenVideoTrack: VideoTrack? = null
    private var wasCameraEnabledBeforeScreenShare: Boolean = true
    private var iceDisconnectTimerJob: Job? = null

    val audioDeviceManager = WebRtcAudioDeviceManager(context)

    val activeAudioRoute: StateFlow<AudioRoute> = audioDeviceManager.activeRoute
    val availableAudioRoutes: StateFlow<List<AudioRouteOption>> = audioDeviceManager.availableRoutes
    val isSpeakerOn: StateFlow<Boolean> = audioDeviceManager.isSpeakerOn

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

    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    private val _isRemoteScreenSharing = MutableStateFlow(false)
    val isRemoteScreenSharing: StateFlow<Boolean> = _isRemoteScreenSharing.asStateFlow()

    var onLocalSdpCreated: ((SessionDescription) -> Unit)? = null
    var onLocalIceCandidate: ((IceCandidateModel) -> Unit)? = null
    var onCallDisconnected: (() -> Unit)? = null

    var pendingRemoteOfferSdp: String? = null
    private val pendingIceCandidates = mutableListOf<IceCandidateModel>()

    @Synchronized
    private fun ensurePeerConnectionFactory(): PeerConnectionFactory? {
        peerConnectionFactory?.let { return it }
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(BuildConfig.DEBUG)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                networkIgnoreMask = 0
            })
            .createPeerConnectionFactory()
            .also { peerConnectionFactory = it }
    }

    @Synchronized
    fun startLocalMedia(callType: CallType) {
        val factory = ensurePeerConnectionFactory() ?: return

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
                surfaceTextureHelper?.dispose()
                surfaceTextureHelper = null
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
    }

    @Synchronized
    fun createPeerConnection() {
        if (peerConnection != null) {
            Log.d(tag, "PeerConnection already exists, skipping recreate")
            return
        }
        val factory = ensurePeerConnectionFactory() ?: return

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
                        iceDisconnectTimerJob?.cancel()
                        iceDisconnectTimerJob = null
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        iceDisconnectTimerJob?.cancel()
                        iceDisconnectTimerJob = scope.launch(Dispatchers.Default) {
                            delay(10_000)
                            if (peerConnection?.iceConnectionState() == PeerConnection.IceConnectionState.DISCONNECTED) {
                                Log.w(tag, "ICE disconnected for >10s, treating as FAILED")
                                _isCallConnected.value = false
                                onCallDisconnected?.invoke()
                            }
                        }
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        iceDisconnectTimerJob?.cancel()
                        iceDisconnectTimerJob = null
                        _isCallConnected.value = false
                        // Leave the libwebrtc signaling thread before tearing down: endCall() takes
                        // this manager's lock and disposes the PeerConnection that is invoking us.
                        scope.launch(Dispatchers.Default) {
                            onCallDisconnected?.invoke()
                        }
                    }
                    PeerConnection.IceConnectionState.CLOSED -> {
                        // Ignore connection closed
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
    }

    fun createOffer(callType: CallType = CallType.VOICE) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (callType == CallType.VIDEO) "true" else "false"))
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

    @Synchronized
    fun setRemoteDescription(
        sdpString: String,
        type: SessionDescription.Type,
        autoAnswer: Boolean = (type == SessionDescription.Type.OFFER)
    ) {
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
                if (autoAnswer && type == SessionDescription.Type.OFFER) {
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
        createOffer(callType)
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

    @Synchronized
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

    @Synchronized
    fun toggleAudioMute(): Boolean {
        val newMute = !_isAudioMuted.value
        setAudioMuted(newMute)
        return newMute
    }

    @Synchronized
    fun setAudioMuted(muted: Boolean) {
        _isAudioMuted.value = muted
        localAudioTrack?.setEnabled(!muted)
        audioDeviceManager.setMicrophoneMute(muted)
        Log.d(tag, "Audio mute explicitly set to: $muted")
    }

    @Synchronized
    fun toggleVideo(): Boolean {
        val newVideo = !_isVideoEnabled.value
        _isVideoEnabled.value = newVideo
        localVideoTrack?.setEnabled(newVideo)
        return newVideo
    }

    fun setRemoteVideoEnabled(enabled: Boolean, isScreenShare: Boolean? = null) {
        _isRemoteVideoEnabled.value = enabled
        if (isScreenShare != null) {
            _isRemoteScreenSharing.value = isScreenShare && enabled
        } else if (!enabled) {
            _isRemoteScreenSharing.value = false
        }
        Log.i(tag, "Remote video enabled set to: $enabled (screenShare=$isScreenShare)")
    }

    fun prepareRemoteVideoOnly() {
        audioDeviceManager.onUpgradeToVideo()
        _isRemoteVideoEnabled.value = true
    }

    private fun findVideoSender(pc: PeerConnection): RtpSender? {
        pc.senders.firstOrNull { it.track()?.kind() == "video" }?.let { return it }
        return try {
            pc.transceivers.firstOrNull {
                it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO
            }?.sender
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun startScreenShare(
        mediaProjectionPermissionData: Intent,
        onNeedRenegotiation: () -> Unit,
        onSystemStopped: () -> Unit
    ): Boolean {
        if (_isScreenSharing.value) return true
        val factory = ensurePeerConnectionFactory() ?: return false

        return try {
            wasCameraEnabledBeforeScreenShare = _isVideoEnabled.value
            try {
                videoCapturer?.stopCapture()
            } catch (e: Exception) {
                Log.w(tag, "Error pausing camera for screen share: ${e.message}")
            }
            localVideoTrack?.setEnabled(false)

            screenSurfaceTextureHelper?.dispose()
            screenSurfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBase.eglBaseContext)

            val source = factory.createVideoSource(true)
            val capturer = ScreenShareCapturer(
                context = context,
                mediaProjectionPermissionData = mediaProjectionPermissionData,
                onSystemStopped = onSystemStopped
            )
            capturer.initialize(screenSurfaceTextureHelper!!, source.capturerObserver)
            capturer.startCapture()

            val track = factory.createVideoTrack("ARDAMSs0", source).apply {
                setEnabled(true)
            }

            screenShareCapturer = capturer
            screenVideoSource = source
            screenVideoTrack = track

            val pc = peerConnection
            val existingVideoSender = if (pc != null) findVideoSender(pc) else null

            if (existingVideoSender != null) {
                existingVideoSender.setTrack(track, false)
                Log.i(tag, "Swapped existing video RtpSender track to ScreenShare track")
            } else if (pc != null) {
                pc.addTrack(track, listOf("ARDAMS"))
                Log.i(tag, "Added new ScreenShare video track to PeerConnection; requesting renegotiation")
                onNeedRenegotiation()
            }

            _isScreenSharing.value = true
            _localVideoTrackFlow.value = track
            audioDeviceManager.onUpgradeToVideo()
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start screen share: ${e.message}", e)
            restoreCameraAfterFailedOrStoppedScreenShare()
            cleanupScreenShareResources()
            false
        }
    }

    @Synchronized
    fun stopScreenShare() {
        if (!_isScreenSharing.value && screenShareCapturer == null) return
        Log.i(tag, "Stopping screen share")

        val currentScreenTrack = screenVideoTrack
        val pc = peerConnection
        val videoSender = pc?.senders?.firstOrNull {
            it.track() === currentScreenTrack || it.track()?.kind() == "video"
        } ?: if (pc != null) findVideoSender(pc) else null

        val cameraTrack = localVideoTrack
        if (videoSender != null) {
            try {
                videoSender.setTrack(cameraTrack, false)
            } catch (e: Exception) {
                Log.w(tag, "Error restoring sender track on stopScreenShare: ${e.message}")
            }
        }

        // Update UI/state BEFORE disposing screenVideoTrack so Compose never binds a disposed track
        restoreCameraAfterFailedOrStoppedScreenShare()
        cleanupScreenShareResources()
    }

    private fun restoreCameraAfterFailedOrStoppedScreenShare() {
        _isScreenSharing.value = false
        val cameraTrack = localVideoTrack
        if (cameraTrack != null) {
            if (wasCameraEnabledBeforeScreenShare) {
                try {
                    videoCapturer?.startCapture(1280, 720, 30)
                } catch (e: Exception) {
                    try {
                        videoCapturer?.startCapture(640, 480, 30)
                    } catch (e2: Exception) {
                        Log.w(tag, "Failed to restart camera after screen share: ${e2.message}")
                    }
                }
            }
            cameraTrack.setEnabled(wasCameraEnabledBeforeScreenShare)
            _isVideoEnabled.value = wasCameraEnabledBeforeScreenShare
            _localVideoTrackFlow.value = cameraTrack
        } else {
            _localVideoTrackFlow.value = null
        }
    }

    private fun cleanupScreenShareResources() {
        _isScreenSharing.value = false
        val sc = screenShareCapturer
        screenShareCapturer = null
        sc?.stopAndDispose()

        val svt = screenVideoTrack
        screenVideoTrack = null
        try {
            svt?.dispose()
        } catch (e: Exception) {
            Log.w(tag, "screenVideoTrack dispose error: ${e.message}")
        }

        val svs = screenVideoSource
        screenVideoSource = null
        try {
            svs?.dispose()
        } catch (e: Exception) {
            Log.w(tag, "screenVideoSource dispose error: ${e.message}")
        }

        val ssth = screenSurfaceTextureHelper
        screenSurfaceTextureHelper = null
        try {
            ssth?.dispose()
        } catch (e: Exception) {
            Log.w(tag, "screenSurfaceTextureHelper dispose error: ${e.message}")
        }
    }

    @Synchronized
    fun upgradeToVideo() {
        Log.i(tag, "Upgrading active call to VIDEO")
        val factory = ensurePeerConnectionFactory() ?: return

        // 1. Start local camera capturer and video track if not already running
        if (localVideoTrack == null) {
            try {
                surfaceTextureHelper?.dispose()
                surfaceTextureHelper = null
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
        val pc = peerConnection
        val track = localVideoTrack
        if (pc != null && track != null) {
            val hasVideoSender = pc.senders.any { it.track()?.kind() == "video" }
            val sender = findVideoSender(pc)
            if (sender != null && !hasVideoSender) {
                sender.setTrack(track, false)
                Log.i(tag, "Reattached localVideoTrack to existing RtpSender on upgrade")
            } else if (!hasVideoSender) {
                pc.addTrack(track, listOf("ARDAMS"))
                Log.i(tag, "Added localVideoTrack to active PeerConnection on upgrade")
            }
        }

        // 3. Upgrade audio routing (preserves Bluetooth/Wired headset, switches Earpiece -> Speaker)
        audioDeviceManager.onUpgradeToVideo()
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

    fun selectAudioRoute(route: AudioRoute) {
        audioDeviceManager.selectAudioRoute(route)
    }

    fun toggleSpeaker(): Boolean {
        val newSpeaker = !audioDeviceManager.isSpeakerphoneOn()
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

    @Synchronized
    fun endCall(delayAudioResetMs: Long = 0L) {
        try {
            iceDisconnectTimerJob?.cancel()
            iceDisconnectTimerJob = null

            cleanupScreenShareResources()

            try {
                audioDeviceManager.stopCallAudio(delayResetMs = delayAudioResetMs)
            } catch (e: Exception) {
                Log.w(tag, "stopCallAudio error: ${e.message}")
            }

            val vc = videoCapturer
            videoCapturer = null
            try {
                vc?.stopCapture()
                vc?.dispose()
            } catch (e: Exception) {
                Log.w(tag, "videoCapturer dispose error: ${e.message}")
            }

            val sth = surfaceTextureHelper
            surfaceTextureHelper = null
            try {
                sth?.dispose()
            } catch (e: Exception) {
                Log.w(tag, "surfaceTextureHelper dispose error: ${e.message}")
            }

            val lvt = localVideoTrack
            localVideoTrack = null
            _localVideoTrackFlow.value = null
            try {
                lvt?.dispose()
            } catch (e: Exception) {
                Log.w(tag, "localVideoTrack dispose error: ${e.message}")
            }

            val lat = localAudioTrack
            localAudioTrack = null
            try {
                lat?.dispose()
            } catch (e: Exception) {
                Log.w(tag, "localAudioTrack dispose error: ${e.message}")
            }

            val lvs = localVideoSource
            localVideoSource = null
            try {
                lvs?.dispose()
            } catch (e: Exception) {
                Log.w(tag, "localVideoSource dispose error: ${e.message}")
            }

            val las = localAudioSource
            localAudioSource = null
            try {
                las?.dispose()
            } catch (e: Exception) {
                Log.w(tag, "localAudioSource dispose error: ${e.message}")
            }

            val pc = peerConnection
            peerConnection = null
            try {
                pc?.close()
                pc?.dispose()
            } catch (e: Exception) {
                Log.w(tag, "peerConnection dispose error: ${e.message}")
            }

            pendingRemoteOfferSdp = null
            synchronized(pendingIceCandidates) {
                pendingIceCandidates.clear()
            }
            _remoteVideoTrackFlow.value = null
            _isCallConnected.value = false
            _isAudioMuted.value = false
            _isVideoEnabled.value = true
            _isRemoteVideoEnabled.value = true
            _isScreenSharing.value = false
            _isRemoteScreenSharing.value = false
            Log.d(tag, "WebRTC endCall teardown completed successfully")
        } catch (e: Exception) {
            Log.e(tag, "Error closing WebRTC session: ${e.message}")
        }
    }
}

