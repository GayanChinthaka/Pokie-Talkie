package com.pokerlanka.pokie_talkie.webrtc

import android.util.Log
import org.webrtc.PeerConnection
import org.webrtc.RtpParameters
import org.webrtc.RtpSender

/**
 * Configures WebRTC video bitrate, degradation preference, and SDP hints for crisp HD
 * video and screen-sharing over local Wi-Fi networks instead of defaulting to 300 kbps.
 */
object WebRtcVideoQualityHelper {

    private const val TAG = "WebRtcVideoQuality"

    const val CAMERA_TARGET_WIDTH = 1920
    const val CAMERA_TARGET_HEIGHT = 1080
    const val CAMERA_TARGET_FPS = 30
    const val CAMERA_HD_FALLBACK_WIDTH = 1280
    const val CAMERA_HD_FALLBACK_HEIGHT = 720
    const val CAMERA_FALLBACK_WIDTH = 640
    const val CAMERA_FALLBACK_HEIGHT = 480

    const val MIN_VIDEO_BITRATE_BPS = 1_000_000
    const val START_VIDEO_BITRATE_BPS = 3_000_000
    const val MAX_VIDEO_BITRATE_BPS = 6_000_000

    const val MIN_VIDEO_BITRATE_KBPS = MIN_VIDEO_BITRATE_BPS / 1000
    const val START_VIDEO_BITRATE_KBPS = START_VIDEO_BITRATE_BPS / 1000
    const val MAX_VIDEO_BITRATE_KBPS = MAX_VIDEO_BITRATE_BPS / 1000

    private val VIDEO_CODEC_NAMES = setOf("H264", "VP8", "VP9", "AV1")
    private val RTPMAP_REGEX = Regex("^a=rtpmap:(\\d+)\\s+([A-Za-z0-9_-]+)/")
    private val FMTP_REGEX = Regex("^a=fmtp:(\\d+)\\s+(.*)$")
    private val APT_REGEX = Regex("\\bapt=(\\d+)\\b")
    private val GOOGLE_BITRATE_PARAM_REGEX =
        Regex("(?:^|;)\\s*x-google-(?:min|start|max)-bitrate=\\d+\\s*")

    /**
     * Enhances every `m=video` media section in [sdpDescription] to:
     * 1. Prefer hardware-accelerated H.264 payloads first while retaining VP8/VP9/AV1 fallbacks.
     * 2. Set `b=AS:` to [maxBitrateKbps].
     * 3. Inject `x-google-min-bitrate`, `x-google-start-bitrate`, and `x-google-max-bitrate`
     *    hints so WebRTC immediately ramps to HD quality over LAN without sitting at 300 kbps.
     */
    fun enhanceVideoSdp(
        sdpDescription: String,
        minBitrateKbps: Int = MIN_VIDEO_BITRATE_KBPS,
        startBitrateKbps: Int = START_VIDEO_BITRATE_KBPS,
        maxBitrateKbps: Int = MAX_VIDEO_BITRATE_KBPS
    ): String {
        if (!sdpDescription.contains("m=video ")) return sdpDescription

        val lineSeparator = if (sdpDescription.contains("\r\n")) "\r\n" else "\n"
        val lines = sdpDescription.split(Regex("\\r?\\n"))
        val output = ArrayList<String>(lines.size + 8)

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!line.startsWith("m=video ")) {
                output.add(line)
                i++
                continue
            }

            var sectionEnd = i + 1
            while (sectionEnd < lines.size && !lines[sectionEnd].startsWith("m=")) {
                sectionEnd++
            }
            val section = lines.subList(i, sectionEnd)
            output.addAll(
                enhanceVideoMediaSection(
                    section = section,
                    minBitrateKbps = minBitrateKbps,
                    startBitrateKbps = startBitrateKbps,
                    maxBitrateKbps = maxBitrateKbps
                )
            )
            i = sectionEnd
        }

        return output.joinToString(lineSeparator)
    }

    private fun enhanceVideoMediaSection(
        section: List<String>,
        minBitrateKbps: Int,
        startBitrateKbps: Int,
        maxBitrateKbps: Int
    ): List<String> {
        val codecByPt = HashMap<String, String>()
        val rtxByAptPt = HashMap<String, MutableList<String>>()
        val ptsWithFmtp = HashSet<String>()

        for (line in section) {
            RTPMAP_REGEX.find(line)?.let { match ->
                codecByPt[match.groupValues[1]] = match.groupValues[2].uppercase()
            }
            FMTP_REGEX.find(line)?.let { match ->
                val pt = match.groupValues[1]
                val params = match.groupValues[2]
                ptsWithFmtp.add(pt)
                APT_REGEX.find(params)?.let { aptMatch ->
                    rtxByAptPt.getOrPut(aptMatch.groupValues[1]) { mutableListOf() }.add(pt)
                }
            }
        }

        val bitrateFmtpSuffix =
            "x-google-min-bitrate=$minBitrateKbps;" +
                "x-google-start-bitrate=$startBitrateKbps;" +
                "x-google-max-bitrate=$maxBitrateKbps"

        val result = ArrayList<String>(section.size + 4)
        var bandwidthInserted = false

        for ((index, line) in section.withIndex()) {
            if (index == 0) {
                result.add(reorderVideoPayloads(line, codecByPt, rtxByAptPt))
                continue
            }

            // Strip any previous bandwidth cap inside the m=video section; we'll insert our own before a= lines.
            if (line.startsWith("b=AS:") || line.startsWith("b=TIAS:")) {
                continue
            }

            if (!bandwidthInserted && (line.startsWith("a=") || line.startsWith("k=") || line.isEmpty())) {
                result.add("b=AS:$maxBitrateKbps")
                bandwidthInserted = true
            }

            val fmtpMatch = FMTP_REGEX.find(line)
            if (fmtpMatch != null) {
                val pt = fmtpMatch.groupValues[1]
                val codec = codecByPt[pt]
                if (codec != null && codec in VIDEO_CODEC_NAMES) {
                    val existingParams = fmtpMatch.groupValues[2]
                        .replace(GOOGLE_BITRATE_PARAM_REGEX, "")
                        .trim()
                        .trim(';')
                    val combinedParams = if (existingParams.isNotEmpty()) {
                        "$existingParams;$bitrateFmtpSuffix"
                    } else {
                        bitrateFmtpSuffix
                    }
                    result.add("a=fmtp:$pt $combinedParams")
                    continue
                }
            }

            result.add(line)

            val rtpmapMatch = RTPMAP_REGEX.find(line)
            if (rtpmapMatch != null) {
                val pt = rtpmapMatch.groupValues[1]
                val codec = rtpmapMatch.groupValues[2].uppercase()
                if (codec in VIDEO_CODEC_NAMES && pt !in ptsWithFmtp) {
                    result.add("a=fmtp:$pt $bitrateFmtpSuffix")
                }
            }
        }

        if (!bandwidthInserted) {
            result.add("b=AS:$maxBitrateKbps")
        }

        return result
    }

    private fun reorderVideoPayloads(
        mLine: String,
        codecByPt: Map<String, String>,
        rtxByAptPt: Map<String, List<String>>
    ): String {
        val parts = mLine.trim().split(Regex("\\s+"))
        if (parts.size <= 4) return mLine

        val header = parts.take(3)
        val payloadTypes = parts.drop(3)
        val payloadSet = payloadTypes.toSet()

        val prioritized = ArrayList<String>(payloadTypes.size)
        val added = HashSet<String>(payloadTypes.size)

        for (pt in payloadTypes) {
            if (codecByPt[pt] == "H264" && added.add(pt)) {
                prioritized.add(pt)
                rtxByAptPt[pt]?.forEach { rtxPt ->
                    if (rtxPt in payloadSet && added.add(rtxPt)) {
                        prioritized.add(rtxPt)
                    }
                }
            }
        }

        for (pt in payloadTypes) {
            if (added.add(pt)) {
                prioritized.add(pt)
            }
        }

        return (header + prioritized).joinToString(" ")
    }

    /**
     * Raises PeerConnection BWE bitrate bounds and updates the video [RtpSender] so WebRTC
     * keeps full HD resolution instead of downscaling on Wi-Fi micro-jitter.
     */
    fun applySenderBitrateAndDegradation(
        peerConnection: PeerConnection?,
        videoSender: RtpSender?,
        maxFramerate: Int = CAMERA_TARGET_FPS
    ) {
        if (peerConnection == null && videoSender == null) return

        try {
            peerConnection?.setBitrate(
                MIN_VIDEO_BITRATE_BPS,
                START_VIDEO_BITRATE_BPS,
                MAX_VIDEO_BITRATE_BPS
            )
        } catch (e: Exception) {
            Log.w(TAG, "PeerConnection.setBitrate skipped: ${e.message}")
        }

        if (videoSender == null) return
        try {
            val params = videoSender.parameters ?: return
            var updated = false

            if (params.degradationPreference != RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION) {
                params.degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION
                updated = true
            }

            params.encodings?.forEach { encoding ->
                if (encoding.minBitrateBps != MIN_VIDEO_BITRATE_BPS) {
                    encoding.minBitrateBps = MIN_VIDEO_BITRATE_BPS
                    updated = true
                }
                if (encoding.maxBitrateBps != MAX_VIDEO_BITRATE_BPS) {
                    encoding.maxBitrateBps = MAX_VIDEO_BITRATE_BPS
                    updated = true
                }
                if (encoding.maxFramerate != maxFramerate) {
                    encoding.maxFramerate = maxFramerate
                    updated = true
                }
                if (encoding.scaleResolutionDownBy != 1.0) {
                    encoding.scaleResolutionDownBy = 1.0
                    updated = true
                }
            }

            if (updated) {
                val applied = videoSender.setParameters(params)
                Log.d(
                    TAG,
                    "Applied HD video sender parameters (min=${MIN_VIDEO_BITRATE_BPS / 1000}k, " +
                        "max=${MAX_VIDEO_BITRATE_BPS / 1000}k, fps=$maxFramerate, applied=$applied)"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "RtpSender.setParameters skipped: ${e.message}")
        }
    }
}
