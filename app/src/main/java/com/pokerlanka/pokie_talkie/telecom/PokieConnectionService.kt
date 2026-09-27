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
        @Volatile
        var activeConnection: PokieConnection? = null
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
            callController = app.appContainer.callController
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
        Log.i(tag, "onCreateOutgoingConnection requested by TelecomManager, request.extras=${request?.extras}")
        val reqExtras = request?.extras ?: Bundle()
        val nestedExtras = reqExtras.getBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS) ?: reqExtras
        val peerId = nestedExtras.getString(TelecomCallManager.EXTRA_PEER_ID)
            ?: reqExtras.getString(TelecomCallManager.EXTRA_PEER_ID) ?: "unknown"
        val peerName = nestedExtras.getString(TelecomCallManager.EXTRA_PEER_NAME)
            ?: reqExtras.getString(TelecomCallManager.EXTRA_PEER_NAME) ?: "Family Member"
        val callId = nestedExtras.getString(TelecomCallManager.EXTRA_CALL_ID)
            ?: reqExtras.getString(TelecomCallManager.EXTRA_CALL_ID) ?: ""
        val callTypeStr = nestedExtras.getString(TelecomCallManager.EXTRA_CALL_TYPE)
            ?: reqExtras.getString(TelecomCallManager.EXTRA_CALL_TYPE) ?: CallType.VOICE.name
        val callType = try { CallType.valueOf(callTypeStr) } catch (e: Exception) { CallType.VOICE }

        Log.i(tag, "onCreateOutgoingConnection resolved: peerId=$peerId, peerName=$peerName, callId=$callId, callType=$callType")

        val app = application as PokieApp
        val connection = PokieConnection(
            context = this,
            callId = callId,
            peerId = peerId,
            peerName = peerName,
            callType = callType,
            callRepository = app.appContainer.callRepository,
            webRtcManager = app.appContainer.webRtcManager,
            callController = app.appContainer.callController
        )

        connection.setAddress(Uri.fromParts("tel", peerId, null), TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName(peerName, TelecomManager.PRESENTATION_ALLOWED)
        connection.setDialing()
        connection.extras = reqExtras

        activeConnection = connection
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
