package com.pokerlanka.pokie_talkie.webrtc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    DisposableEffect(videoTrack, renderer) {
        val currentRenderer = renderer
        val currentTrack = videoTrack
        if (currentRenderer != null && currentTrack != null) {
            try {
                currentTrack.addSink(currentRenderer)
            } catch (_: IllegalStateException) {}
        }
        onDispose {
            if (currentRenderer != null && currentTrack != null) {
                try {
                    currentTrack.removeSink(currentRenderer)
                } catch (_: IllegalStateException) {}
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                init(eglBaseContext, null)
                setScalingType(scalingType)
                setMirror(isMirror)
                setEnableHardwareScaler(true)
                setZOrderMediaOverlay(isMirror) // Bring local preview to top if PIP
                renderer = this
            }
        },
        update = { r ->
            r.setMirror(isMirror)
            r.setScalingType(scalingType)
        },
        onRelease = { r ->
            renderer = null
            r.release()
        }
    )
}

