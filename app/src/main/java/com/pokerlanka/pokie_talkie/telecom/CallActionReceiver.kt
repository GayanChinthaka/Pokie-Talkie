package com.pokerlanka.pokie_talkie.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pokerlanka.pokie_talkie.PokieApp

class CallActionReceiver : BroadcastReceiver() {

    private val tag = "CallActionReceiver"

    companion object {
        const val ACTION_DECLINE_CALL = "com.pokerlanka.pokie_talkie.ACTION_DECLINE_CALL"
        const val ACTION_ANSWER_CALL = "com.pokerlanka.pokie_talkie.ACTION_ANSWER_CALL"
        const val ACTION_HANGUP_CALL = "com.pokerlanka.pokie_talkie.ACTION_HANGUP_CALL"
        const val ACTION_STOP_SCREEN_SHARE = "com.pokerlanka.pokie_talkie.ACTION_STOP_SCREEN_SHARE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PokieApp ?: return
        val container = app.appContainer
        val action = intent.action
        val peerId = intent.getStringExtra(TelecomCallManager.EXTRA_PEER_ID) ?: ""
        val callId = intent.getStringExtra(TelecomCallManager.EXTRA_CALL_ID) ?: ""

        Log.i(tag, "Received call action: $action for call $callId from $peerId")

        when (action) {
            ACTION_DECLINE_CALL -> {
                container.callController.endCall(
                    EndReason.REJECTED,
                    notifyPeer = true,
                    expectedCallId = callId.ifBlank { null }
                )
            }
            ACTION_HANGUP_CALL -> {
                container.callController.endCall(
                    EndReason.LOCAL_HANGUP,
                    notifyPeer = true,
                    expectedCallId = callId.ifBlank { null }
                )
            }
            ACTION_STOP_SCREEN_SHARE -> {
                container.stopScreenShare()
            }
        }
    }
}
