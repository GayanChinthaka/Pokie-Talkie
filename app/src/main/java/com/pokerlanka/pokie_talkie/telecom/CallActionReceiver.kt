package com.pokerlanka.pokie_talkie.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pokerlanka.pokie_talkie.PokieApp
import com.pokerlanka.pokie_talkie.ui.call.IncomingCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallActionReceiver : BroadcastReceiver() {

    private val tag = "CallActionReceiver"

    companion object {
        const val ACTION_DECLINE_CALL = "com.pokerlanka.pokie_talkie.ACTION_DECLINE_CALL"
        const val ACTION_ANSWER_CALL = "com.pokerlanka.pokie_talkie.ACTION_ANSWER_CALL"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PokieApp ?: return
        val container = app.appContainer
        val action = intent.action
        val peerId = intent.getStringExtra(TelecomCallManager.EXTRA_PEER_ID) ?: ""
        val peerName = intent.getStringExtra(TelecomCallManager.EXTRA_PEER_NAME) ?: "Family Member"
        val callId = intent.getStringExtra(TelecomCallManager.EXTRA_CALL_ID) ?: ""
        val callType = intent.getStringExtra(TelecomCallManager.EXTRA_CALL_TYPE) ?: "VOICE"

        Log.i(tag, "Received call action: $action for call $callId from $peerId")

        when (action) {
            ACTION_DECLINE_CALL -> {
                container.telecomCallManager.dismissCallNotification()
                container.webRtcManager.endCall()
                PokieConnectionService.activeConnection?.markDisconnected()
                container.fcmSignalingClient.sendHangup(peerId, callId)
                CoroutineScope(Dispatchers.IO).launch {
                    container.callRepository.endCallAndLog()
                }
            }

            ACTION_ANSWER_CALL -> {
                container.telecomCallManager.dismissCallNotification()
                val answerIntent = Intent(context, IncomingCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(TelecomCallManager.EXTRA_PEER_ID, peerId)
                    putExtra(TelecomCallManager.EXTRA_PEER_NAME, peerName)
                    putExtra(TelecomCallManager.EXTRA_CALL_ID, callId)
                    putExtra(TelecomCallManager.EXTRA_CALL_TYPE, callType)
                    putExtra("auto_answer", true)
                }
                context.startActivity(answerIntent)
            }
        }
    }
}
