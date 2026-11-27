package com.pokerlanka.pokie_talkie

import com.google.gson.Gson
import com.pokerlanka.pokie_talkie.data.local.ConnectionMode
import com.pokerlanka.pokie_talkie.data.local.PokieTypeConverters
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.MessageType
import com.pokerlanka.pokie_talkie.data.model.SignalingPayload
import com.pokerlanka.pokie_talkie.data.model.SignalingType
import com.pokerlanka.pokie_talkie.data.model.isValid
import com.pokerlanka.pokie_talkie.lan.LanMediaServer
import com.pokerlanka.pokie_talkie.lan.MediaTransferHeader
import com.pokerlanka.pokie_talkie.lan.MediaTransferKind
import com.pokerlanka.pokie_talkie.utils.MediaStorageHelper
import com.pokerlanka.pokie_talkie.webrtc.AudioRoute
import com.pokerlanka.pokie_talkie.webrtc.AudioRouteOption
import com.pokerlanka.pokie_talkie.webrtc.ScreenShareCapturer
import com.pokerlanka.pokie_talkie.webrtc.WebRtcVideoQualityHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallFeaturesUnitTest {

    @Test
    fun screenShareDimensions_preserveAspectRatioAndEvenBounds() {
        val (w1, h1) = ScreenShareCapturer.computeScaledDimensions(1080, 2400)
        assertEquals(1920, h1)
        assertEquals(0, w1 % 2)
        assertEquals(0, h1 % 2)
        assertEquals(864, w1)

        val (w2, h2) = ScreenShareCapturer.computeScaledDimensions(2400, 1080)
        assertEquals(1920, w2)
        assertEquals(864, h2)

        val (w3, h3) = ScreenShareCapturer.computeScaledDimensions(721, 1279)
        assertEquals(720, w3)
        assertEquals(1278, h3)
    }

    @Test
    fun signalingPayload_preservesScreenShareFlag() {
        val gson = Gson()
        val payload = SignalingPayload(
            type = SignalingType.RENEGOTIATION_OFFER,
            senderId = "user_1",
            senderName = "Amma",
            targetId = "user_2",
            callId = "call_123",
            callType = CallType.VIDEO,
            sdp = "v=0...",
            isCameraOn = true,
            isScreenShare = true
        )
        assertTrue(payload.isValid())

        val json = gson.toJson(payload)
        val parsed = gson.fromJson(json, SignalingPayload::class.java)
        assertEquals(SignalingType.RENEGOTIATION_OFFER, parsed.type)
        assertEquals(true, parsed.isScreenShare)
        assertEquals(true, parsed.isCameraOn)
    }

    @Test
    fun audioRouteOptions_holdExpectedValues() {
        val option = AudioRouteOption(AudioRoute.BLUETOOTH, "Galaxy Buds2", "Galaxy Buds2")
        assertEquals(AudioRoute.BLUETOOTH, option.route)
        assertEquals("Galaxy Buds2", option.label)
    }

    @Test
    fun connectionMode_hasWifiAndBluetooth() {
        assertEquals(listOf(ConnectionMode.WIFI, ConnectionMode.BLUETOOTH), ConnectionMode.entries)
    }

    @Test
    fun mediaTransferHeader_validatesBoundsByKind() {
        val validAvatar = MediaTransferHeader(
            kind = MediaTransferKind.AVATAR,
            senderId = "user_1",
            senderName = "Amma",
            fileSize = 15_000L
        )
        assertTrue(LanMediaServer.validateHeader(validAvatar))

        val oversizedAvatar = validAvatar.copy(fileSize = 500_000L)
        assertFalse(LanMediaServer.validateHeader(oversizedAvatar))

        val validDoc = MediaTransferHeader(
            kind = MediaTransferKind.MEDIA,
            senderId = "user_1",
            senderName = "Amma",
            messageType = MessageType.FILE,
            fileName = "invoice.pdf",
            fileSize = 5_000_000L
        )
        assertTrue(LanMediaServer.validateHeader(validDoc))

        val emptyFile = validDoc.copy(fileSize = 0L)
        assertFalse(LanMediaServer.validateHeader(emptyFile))
    }

    @Test
    fun signalingPayload_preservesEditUnsendAndAvatarMeta() {
        val gson = Gson()
        val edit = SignalingPayload(
            type = SignalingType.MESSAGE_EDIT,
            senderId = "u1",
            senderName = "Dad",
            targetId = "u2",
            messageId = "m1",
            messageContent = "Updated text"
        )
        val parsedEdit = gson.fromJson(gson.toJson(edit), SignalingPayload::class.java)
        assertEquals(SignalingType.MESSAGE_EDIT, parsedEdit.type)
        assertEquals("Updated text", parsedEdit.messageContent)

        val unsend = SignalingPayload(
            type = SignalingType.MESSAGE_UNSEND,
            senderId = "u1",
            senderName = "Dad",
            targetId = "u2",
            messageId = "m1",
            senderAvatarColor = 0xFF0284C7L,
            senderAvatarHash = "a1b2c3d4e5f6"
        )
        val parsedUnsend = gson.fromJson(gson.toJson(unsend), SignalingPayload::class.java)
        assertEquals(SignalingType.MESSAGE_UNSEND, parsedUnsend.type)
        assertEquals("a1b2c3d4e5f6", parsedUnsend.senderAvatarHash)
        assertEquals(0xFF0284C7L, parsedUnsend.senderAvatarColor)
    }

    @Test
    fun typeConverters_handleLegacyEmojiAsText() {
        val converters = PokieTypeConverters()
        assertEquals(MessageType.TEXT, converters.toMessageType("EMOJI"))
        assertEquals(MessageType.GIF, converters.toMessageType("GIF"))
        assertEquals(MessageType.FILE, converters.toMessageType("FILE"))
    }

    @Test
    fun mediaStorageHelper_sanitizesFileNamesAndFormatsSizes() {
        assertEquals("my_doc__final_.pdf", MediaStorageHelper.sanitizeFileName("../my doc (final).pdf"))
        assertEquals("512 B", MediaStorageHelper.formatFileSize(512L))
        assertEquals("2 KB", MediaStorageHelper.formatFileSize(2048L))
    }

    @Test
    fun enhanceVideoSdp_prioritizesH264AndInjectsBitrateHints() {
        val sampleSdp = listOf(
            "v=0",
            "o=- 12345 2 IN IP4 127.0.0.1",
            "s=-",
            "t=0 0",
            "m=audio 9 UDP/TLS/RTP/SAVPF 111",
            "c=IN IP4 0.0.0.0",
            "a=rtpmap:111 opus/48000/2",
            "m=video 9 UDP/TLS/RTP/SAVPF 96 97 102 103",
            "c=IN IP4 0.0.0.0",
            "b=AS:1000",
            "a=rtpmap:96 VP8/90000",
            "a=rtpmap:97 rtx/90000",
            "a=fmtp:97 apt=96",
            "a=rtpmap:102 H264/90000",
            "a=fmtp:102 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42001f",
            "a=rtpmap:103 rtx/90000",
            "a=fmtp:103 apt=102"
        ).joinToString("\r\n")

        val enhanced = WebRtcVideoQualityHelper.enhanceVideoSdp(sampleSdp)
        val lines = enhanced.split("\r\n")

        // Audio section remains untouched
        assertTrue(lines.contains("m=audio 9 UDP/TLS/RTP/SAVPF 111"))

        // H264 (102) + its RTX (103) are prioritized ahead of VP8 (96) + RTX (97)
        assertTrue(lines.contains("m=video 9 UDP/TLS/RTP/SAVPF 102 103 96 97"))

        // Video bandwidth raised to 6000 kbps and placed before a= lines
        val cIndex = lines.lastIndexOf("c=IN IP4 0.0.0.0")
        val bIndex = lines.indexOf("b=AS:6000")
        val firstVideoAttr = lines.indexOf("a=rtpmap:96 VP8/90000")
        assertTrue(bIndex in (cIndex + 1) until firstVideoAttr)
        assertFalse(lines.contains("b=AS:1000"))

        // Both VP8 (which lacked fmtp) and H264 receive Google bitrate parameters; RTX does not
        assertTrue(
            lines.contains(
                "a=fmtp:96 x-google-min-bitrate=1000;x-google-start-bitrate=3000;x-google-max-bitrate=6000"
            )
        )
        assertTrue(
            lines.contains(
                "a=fmtp:102 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42001f;" +
                    "x-google-min-bitrate=1000;x-google-start-bitrate=3000;x-google-max-bitrate=6000"
            )
        )
        assertTrue(lines.contains("a=fmtp:97 apt=96"))
        assertTrue(lines.contains("a=fmtp:103 apt=102"))
    }
}
