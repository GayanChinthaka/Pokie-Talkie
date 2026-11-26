package com.pokerlanka.pokie_talkie

import com.google.gson.Gson
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.SignalingPayload
import com.pokerlanka.pokie_talkie.data.model.SignalingType
import com.pokerlanka.pokie_talkie.data.model.isValid
import com.pokerlanka.pokie_talkie.webrtc.AudioRoute
import com.pokerlanka.pokie_talkie.webrtc.AudioRouteOption
import com.pokerlanka.pokie_talkie.webrtc.ScreenShareCapturer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallFeaturesUnitTest {

    @Test
    fun screenShareDimensions_preserveAspectRatioAndEvenBounds() {
        val (w1, h1) = ScreenShareCapturer.computeScaledDimensions(1080, 2400)
        assertEquals(1280, h1)
        assertEquals(0, w1 % 2)
        assertEquals(0, h1 % 2)
        assertEquals(576, w1)

        val (w2, h2) = ScreenShareCapturer.computeScaledDimensions(2400, 1080)
        assertEquals(1280, w2)
        assertEquals(576, h2)

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
}
