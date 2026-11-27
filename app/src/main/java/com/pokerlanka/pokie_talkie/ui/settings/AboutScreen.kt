package com.pokerlanka.pokie_talkie.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pokerlanka.pokie_talkie.BuildConfig
import com.pokerlanka.pokie_talkie.ui.components.Material3SettingsGroup
import com.pokerlanka.pokie_talkie.ui.components.Material3SettingsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentVersion = BuildConfig.VERSION_NAME

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About",
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // App Branding Header
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(96.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Pokie-Talkie Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Pokie-Talkie",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Version $currentVersion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Private home & proximity walkie-talkie, HD calling, and messaging over Wi-Fi LAN & Bluetooth.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // Developer & Organization Group
            item {
                Material3SettingsGroup(
                    title = "Developer & Organization",
                    items = listOf(
                        Material3SettingsItem(
                            icon = Icons.Default.Person,
                            title = { Text("Developer") },
                            description = { Text("Gayan Chinthaka") }
                        ),
                        Material3SettingsItem(
                            icon = Icons.Default.Business,
                            title = { Text("Company") },
                            description = { Text("Pokerlanka") }
                        )
                    )
                )
            }

            // Architecture & Protocol Details Group
            item {
                Material3SettingsGroup(
                    title = "Architecture & Protocols",
                    items = listOf(
                        Material3SettingsItem(
                            icon = Icons.Default.Wifi,
                            title = { Text("Local Wi-Fi Mesh") },
                            description = {
                                Text("mDNS (NSD) peer discovery with direct TCP sockets for chat & media sharing")
                            }
                        ),
                        Material3SettingsItem(
                            icon = Icons.Default.Bluetooth,
                            title = { Text("Bluetooth Proximity") },
                            description = {
                                Text("Offline RFCOMM SPP channel for router-free chat & duplex walkie-talkie audio")
                            }
                        ),
                        Material3SettingsItem(
                            icon = Icons.Default.Sensors,
                            title = { Text("Cloud Wake-Up & Signaling") },
                            description = {
                                Text("Firebase Cloud Messaging (FCM) HTTP v1 over internet for peer wake-up & call signaling")
                            }
                        ),
                        Material3SettingsItem(
                            icon = Icons.Default.PhoneInTalk,
                            title = { Text("Calling Engine") },
                            description = {
                                Text("WebRTC (1080p H.264/VP8 video, screen share, Opus audio & LAN/STUN ICE) + Bluetooth PCM voice")
                            }
                        ),
                        Material3SettingsItem(
                            icon = Icons.Default.Lock,
                            title = { Text("On-Device Storage") },
                            description = {
                                Text("Chat history, contacts & media stay locally on-device in Room SQLite")
                            }
                        )
                    )
                )
            }

            // Copyright Footer
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)
                ) {
                    Text(
                        text = "© 2026 Pokerlanka. All rights reserved.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
