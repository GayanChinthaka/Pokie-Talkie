package com.pokerlanka.pokie_talkie.ui.call

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
                ringtoneVibrationManager = container.ringtoneVibrationManager,
                callController = container.callController,
                contactRepository = container.contactRepository
            )
        )[CallViewModel::class.java]

        if (savedInstanceState == null) {
            val autoAnswer = intent.getBooleanExtra("auto_answer", false)
            if (autoAnswer) {
                viewModel.answerCall()
            }
        }

        setContent {
            val themeMode by container.userPreferencesManager.themeModeFlow.collectAsState()
            val accentColor by container.userPreferencesManager.themeAccentColorFlow.collectAsState()
            val currentCall by container.callRepository.currentCall.collectAsState()
            val isScreenSharing by container.webRtcManager.isScreenSharing.collectAsState()

            LaunchedEffect(currentCall?.state, currentCall?.callType, isScreenSharing) {
                val keepScreenOn = currentCall?.state == CallState.RINGING_INCOMING ||
                    (currentCall?.state == CallState.CONNECTED &&
                        (currentCall?.callType == com.pokerlanka.pokie_talkie.data.model.CallType.VIDEO || isScreenSharing))
                if (keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            PokieTalkieTheme(themeMode = themeMode, accentColor = accentColor) {
                CallScreen(
                    viewModel = viewModel,
                    onCallEnded = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val autoAnswer = intent.getBooleanExtra("auto_answer", false)
        if (autoAnswer) {
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
                    ringtoneVibrationManager = container.ringtoneVibrationManager,
                    callController = container.callController,
                    contactRepository = container.contactRepository
                )
            )[CallViewModel::class.java]
            viewModel.answerCall()
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
