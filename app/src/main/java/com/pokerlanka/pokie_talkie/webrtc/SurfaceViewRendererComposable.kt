package com.pokerlanka.pokie_talkie.webrtc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun WebRtcVideoView(
    videoTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    modifier: Modifier = Modifier,
    isMirror: Boolean = false,
    scalingType: RendererCommon.ScalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(eglBaseContext, null)
                setScalingType(scalingType)
                setMirror(isMirror)
                setEnableHardwareScaler(true)
                setZOrderMediaOverlay(isMirror) // Bring local preview to top if PIP
            }
        },
        update = { renderer ->
            renderer.setMirror(isMirror)
            renderer.setScalingType(scalingType)
            videoTrack?.addSink(renderer)
        },
        onRelease = { renderer ->
            videoTrack?.removeSink(renderer)
            renderer.release()
        }
    )
}
