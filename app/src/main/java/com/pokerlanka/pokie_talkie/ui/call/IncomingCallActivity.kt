package com.pokerlanka.pokie_talkie.ui.call

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.pokerlanka.pokie_talkie.PokieApp
import com.pokerlanka.pokie_talkie.data.model.CallState
import com.pokerlanka.pokie_talkie.ui.theme.PokieTalkieTheme

class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOnLockScreenAndWake()

        val app = application as PokieApp
        val container = app.appContainer

        val viewModel = ViewModelProvider(
            this,
            CallViewModel.provideFactory(
                callRepository = container.callRepository,
                webRtcManager = container.webRtcManager,
                fcmSignalingClient = container.fcmSignalingClient,
                telecomCallManager = container.telecomCallManager,
                bluetoothVoiceManager = container.bluetoothVoiceManager,
                ringtoneVibrationManager = container.ringtoneVibrationManager
            )
        )[CallViewModel::class.java]

        val autoAnswer = intent.getBooleanExtra("auto_answer", false)
        if (autoAnswer) {
            viewModel.answerCall()
        }

        setContent {
            PokieTalkieTheme {
                CallScreen(
                    viewModel = viewModel,
                    onCallEnded = { finish() }
                )
            }
        }
    }

    private fun showOnLockScreenAndWake() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
    }
}
