package com.pokerlanka.pokie_talkie.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import org.webrtc.CapturerObserver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer

/**
 * Wraps WebRTC's [ScreenCapturerAndroid] to capture the device screen at up to 720p / 15 FPS
 * while preserving the current display aspect ratio and even encoder dimensions.
 */
class ScreenShareCapturer(
    private val context: Context,
    private val mediaProjectionPermissionData: Intent,
    private val onSystemStopped: () -> Unit
) {
    private val tag = "ScreenShareCapturer"
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var isCapturing = false

    fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper,
        observer: CapturerObserver
    ) {
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(tag, "MediaProjection stopped by system")
                isCapturing = false
                onSystemStopped()
            }
        }
        val capturer = ScreenCapturerAndroid(mediaProjectionPermissionData, callback)
        capturer.initialize(surfaceTextureHelper, context.applicationContext, observer)
        screenCapturer = capturer
    }

    fun startCapture() {
        val capturer = screenCapturer ?: return
        val (width, height) = computeCaptureDimensions()
        try {
            capturer.startCapture(width, height, TARGET_FPS)
            isCapturing = true
            Log.i(tag, "Screen share capture started at ${width}x${height} @ ${TARGET_FPS}fps")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start screen share capture: ${e.message}", e)
            throw e
        }
    }

    fun stopAndDispose() {
        val capturer = screenCapturer
        screenCapturer = null
        isCapturing = false
        try {
            capturer?.stopCapture()
        } catch (e: Exception) {
            Log.w(tag, "ScreenCapturer stopCapture error: ${e.message}")
        }
        try {
            capturer?.dispose()
        } catch (e: Exception) {
            Log.w(tag, "ScreenCapturer dispose error: ${e.message}")
        }
    }

    private fun computeCaptureDimensions(): Pair<Int, Int> {
        var rawWidth = 720
        var rawHeight = 1280
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = windowManager.currentWindowMetrics.bounds
                if (bounds.width() > 0 && bounds.height() > 0) {
                    rawWidth = bounds.width()
                    rawHeight = bounds.height()
                }
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay?.getRealMetrics(metrics)
                if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                    rawWidth = metrics.widthPixels
                    rawHeight = metrics.heightPixels
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not query display dimensions, using 720x1280: ${e.message}")
        }

        return computeScaledDimensions(rawWidth, rawHeight, MAX_DIMENSION)
    }

    fun asVideoCapturer(): VideoCapturer? = screenCapturer

    companion object {
        private const val MAX_DIMENSION = 1280
        private const val TARGET_FPS = 15

        internal fun computeScaledDimensions(
            rawWidth: Int,
            rawHeight: Int,
            maxDimension: Int = MAX_DIMENSION
        ): Pair<Int, Int> {
            val safeW = maxOf(2, rawWidth)
            val safeH = maxOf(2, rawHeight)
            val maxSide = maxOf(safeW, safeH)
            val scale = if (maxSide > maxDimension) maxDimension.toFloat() / maxSide else 1f
            val scaledW = ((safeW * scale).toInt() / 2) * 2
            val scaledH = ((safeH * scale).toInt() / 2) * 2
            return Pair(maxOf(2, scaledW), maxOf(2, scaledH))
        }
    }
}
