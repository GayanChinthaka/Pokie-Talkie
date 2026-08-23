package com.pokerlanka.pokie_talkie.telecom

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.pokerlanka.pokie_talkie.PokieApp
import com.pokerlanka.pokie_talkie.data.model.CallType

class PokieConnectionService : ConnectionService() {
    private val tag = "PokieConnectionService"

    companion object {
        var activeConnection: PokieConnection? = null
            private set
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.i(tag, "onCreateIncomingConnection requested by TelecomManager")
        val extras = request?.extras ?: Bundle()
        val callerId = extras.getString(TelecomCallManager.EXTRA_PEER_ID) ?: "unknown"
        val callerName = extras.getString(TelecomCallManager.EXTRA_PEER_NAME) ?: "Family Member"
        val callId = extras.getString(TelecomCallManager.EXTRA_CALL_ID) ?: ""
        val callTypeStr = extras.getString(TelecomCallManager.EXTRA_CALL_TYPE) ?: CallType.VOICE.name
        val callType = try { CallType.valueOf(callTypeStr) } catch (e: Exception) { CallType.VOICE }

        val app = application as PokieApp
        val connection = PokieConnection(
            context = this,
            callId = callId,
            peerId = callerId,
            peerName = callerName,
            callType = callType,
            callRepository = app.appContainer.callRepository,
            webRtcManager = app.appContainer.webRtcManager,
            onEndCallRequested = { peer, cId ->
                app.appContainer.fcmSignalingClient.sendHangup(peer, cId)
            }
        )

        connection.setAddress(Uri.fromParts("tel", callerId, null), TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName(callerName, TelecomManager.PRESENTATION_ALLOWED)
        connection.setRinging()
        connection.extras = extras

        activeConnection = connection
        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(tag, "onCreateIncomingConnectionFailed")
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.i(tag, "onCreateOutgoingConnection requested by TelecomManager")
        val extras = request?.extras ?: Bundle()
        val peerId = extras.getString(TelecomCallManager.EXTRA_PEER_ID) ?: "unknown"
        val peerName = extras.getString(TelecomCallManager.EXTRA_PEER_NAME) ?: "Family Member"
        val callId = extras.getString(TelecomCallManager.EXTRA_CALL_ID) ?: ""
        val callTypeStr = extras.getString(TelecomCallManager.EXTRA_CALL_TYPE) ?: CallType.VOICE.name
        val callType = try { CallType.valueOf(callTypeStr) } catch (e: Exception) { CallType.VOICE }

        val app = application as PokieApp
        val connection = PokieConnection(
            context = this,
            callId = callId,
            peerId = peerId,
            peerName = peerName,
            callType = callType,
            callRepository = app.appContainer.callRepository,
            webRtcManager = app.appContainer.webRtcManager,
            onEndCallRequested = { peer, cId ->
                app.appContainer.fcmSignalingClient.sendHangup(peer, cId)
            }
        )

        connection.setAddress(Uri.fromParts("tel", peerId, null), TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName(peerName, TelecomManager.PRESENTATION_ALLOWED)
        connection.setDialing()
        connection.extras = extras

        activeConnection = connection

        // Initialize WebRTC for outgoing call and create SDP offer
        try {
            app.appContainer.webRtcManager.startLocalMedia(callType)
            app.appContainer.webRtcManager.createPeerConnection()
            app.appContainer.webRtcManager.createOffer()
            Log.i(tag, "Outgoing WebRTC offer pipeline initiated for call $callId")
        } catch (e: Exception) {
            Log.e(tag, "Error initiating outgoing WebRTC offer: ${e.message}")
        }

        return connection
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.e(tag, "onCreateOutgoingConnectionFailed")
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
    }

    override fun onDestroy() {
        super.onDestroy()
        activeConnection = null
    }
}
