package com.pokerlanka.pokie_talkie.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PhoneForwarded
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerlanka.pokie_talkie.ui.components.Material3SettingsGroup
import com.pokerlanka.pokie_talkie.ui.components.Material3SettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingingSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val callRingtoneEnabled by viewModel.callRingtoneEnabled.collectAsState()
    val callVibrationEnabled by viewModel.callVibrationEnabled.collectAsState()
    val ringbackToneEnabled by viewModel.ringbackToneEnabled.collectAsState()
    val callFeedbackTonesEnabled by viewModel.callFeedbackTonesEnabled.collectAsState()
    val messageSoundEnabled by viewModel.messageSoundEnabled.collectAsState()
    val messageVibrationEnabled by viewModel.messageVibrationEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ringing & Sounds",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Incoming & Outgoing Calls Group
            item {
                Material3SettingsGroup(
                    title = "Voice & Video Calls",
                    items = listOf(
                        Material3SettingsItem(
                            icon = Icons.Default.RingVolume,
                            title = { Text("Incoming Call Ringtone") },
                            description = { Text("Play system default ringtone for incoming calls") },
                            trailingContent = {
                                Switch(
                                    checked = callRingtoneEnabled,
                                    onCheckedChange = { viewModel.setCallRingtoneEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            },
                            onClick = { viewModel.setCallRingtoneEnabled(!callRingtoneEnabled) }
                        ),
                        Material3SettingsItem(
                            icon = Icons.Default.Vibration,
                            title = { Text("Call Vibration") },
                            description = { Text("Vibrate phone with rhythmic call pattern") },
                            trailingContent = {
                                Switch(
                                    checked = callVibrationEnabled,
                                    onCheckedChange = { viewModel.setCallVibrationEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            },
                            onClick = { viewModel.setCallVibrationEnabled(!callVibrationEnabled) }
                        ),
                        Material3SettingsItem(
                            icon = Icons.AutoMirrored.Filled.PhoneForwarded,
                            title = { Text("Outgoing Ringback Tone") },
                            description = { Text("Audible telephony tone while waiting for receiver") },
                            trailingContent = {
                                Switch(
                                    checked = ringbackToneEnabled,
                                    onCheckedChange = { viewModel.setRingbackToneEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            },
                            onClick = { viewModel.setRingbackToneEnabled(!ringbackToneEnabled) }
                        ),
                        Material3SettingsItem(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            title = { Text("Connect & Disconnect Tones") },
                            description = { Text("Soft audio cues when a call connects or ends") },
                            trailingContent = {
                                Switch(
                                    checked = callFeedbackTonesEnabled,
                                    onCheckedChange = { viewModel.setCallFeedbackTonesEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            },
                            onClick = { viewModel.setCallFeedbackTonesEnabled(!callFeedbackTonesEnabled) }
                        )
                    )
                )
            }

            // Chat Messages Group
            item {
                Material3SettingsGroup(
                    title = "Chat & Notifications",
                    items = listOf(
                        Material3SettingsItem(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            title = { Text("Chat Message Sound") },
                            description = { Text("Short alert chime when a new message arrives") },
                            trailingContent = {
                                Switch(
                                    checked = messageSoundEnabled,
                                    onCheckedChange = { viewModel.setMessageSoundEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            },
                            onClick = { viewModel.setMessageSoundEnabled(!messageSoundEnabled) }
                        ),
                        Material3SettingsItem(
                            icon = Icons.Default.NotificationsActive,
                            title = { Text("Message Vibration") },
                            description = { Text("Brief haptic pulse on incoming message") },
                            trailingContent = {
                                Switch(
                                    checked = messageVibrationEnabled,
                                    onCheckedChange = { viewModel.setMessageVibrationEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            },
                            onClick = { viewModel.setMessageVibrationEnabled(!messageVibrationEnabled) }
                        )
                    )
                )
            }
        }
    }
}
