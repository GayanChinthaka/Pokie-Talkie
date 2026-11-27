package com.pokerlanka.pokie_talkie.ui.dev

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerlanka.pokie_talkie.data.model.CallSession
import com.pokerlanka.pokie_talkie.data.model.CallState
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.Contact
import com.pokerlanka.pokie_talkie.data.model.MessageStatus
import com.pokerlanka.pokie_talkie.data.model.MessageType
import com.pokerlanka.pokie_talkie.di.AppContainer
import com.pokerlanka.pokie_talkie.ui.call.IncomingCallActivity
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkBg
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkCard
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkSurface
import com.pokerlanka.pokie_talkie.ui.theme.PokieLanActiveBadge
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextPrimary
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextSecondary
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevPreviewHubScreen(
    appContainer: AppContainer,
    onBack: () -> Unit,
    onOpenMockCall: (peerName: String, callType: CallType, callState: CallState) -> Unit,
    onOpenChat: (peerId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var testProfileName by remember { mutableStateOf("Test User (Living Room)") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "🧪 UI Test & Preview Hub",
                            color = PokieTextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Test every screen & state on a single phone",
                            color = PokieTextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PokieTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PokieDarkSurface)
            )
        },
        containerColor = PokieDarkBg,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Notice Banner
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F2E22),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Single-Device UI Sandbox",
                                color = PokieTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Preview all layouts, call states, chat bubbles, and lock-screen overlays instantly without network setup.",
                                color = PokieTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Section 1: Quick Data Seeder
            item {
                SectionHeader(title = "1. Sample Data & Quick Setup")
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DevActionCard(
                        title = "Seed Demo Family",
                        subtitle = "Adds Mom, Dad, Sister & messages to Home screen",
                        icon = Icons.Default.PersonAdd,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            coroutineScope.launch {
                                seedDemoData(appContainer)
                                Toast.makeText(context, "Demo family & chat data injected!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    DevActionCard(
                        title = "Clear Test Data",
                        subtitle = "Wipes contacts & chat history",
                        icon = Icons.Default.DeleteSweep,
                        accentColor = Color(0xFFEF4444),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            coroutineScope.launch {
                                appContainer.contactRepository.clearAllContacts()
                                Toast.makeText(context, "All contacts & chats cleared!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // Section 2: Voice Call Screen States
            item {
                SectionHeader(title = "2. Voice Call UI States")
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevRowCard(
                        title = "Voice Call - Active / Connected",
                        subtitle = "Duration timer ticking, pulse animation, speaker/mute buttons",
                        icon = Icons.Default.PhoneInTalk,
                        badge = "Connected",
                        badgeColor = PokieLanActiveBadge,
                        onClick = {
                            onOpenMockCall("Amma (Mom)", CallType.VOICE, CallState.CONNECTED)
                        }
                    )

                    DevRowCard(
                        title = "Voice Call - Outgoing Ringing",
                        subtitle = "'Ringing over LAN...' screen with avatar pulse",
                        icon = Icons.Default.Call,
                        badge = "Outgoing",
                        badgeColor = Color(0xFF38BDF8),
                        onClick = {
                            onOpenMockCall("Thaththa (Dad)", CallType.VOICE, CallState.RINGING_OUTGOING)
                        }
                    )

                    DevRowCard(
                        title = "Voice Call - Incoming Ringing",
                        subtitle = "Accept & Decline buttons (clicking Accept answers the call)",
                        icon = Icons.Default.CallReceived,
                        badge = "Incoming",
                        badgeColor = Color(0xFFF59E0B),
                        onClick = {
                            onOpenMockCall("Nangi (Sister)", CallType.VOICE, CallState.RINGING_INCOMING)
                        }
                    )
                }
            }

            // Section 3: Video Call Screen States
            item {
                SectionHeader(title = "3. Video Call UI States")
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevRowCard(
                        title = "Video Call - Active / Connected",
                        subtitle = "Simulated remote feed + local PIP thumbnail with camera flip",
                        icon = Icons.Default.Videocam,
                        badge = "Connected",
                        badgeColor = PokieLanActiveBadge,
                        onClick = {
                            onOpenMockCall("Living Room Tab", CallType.VIDEO, CallState.CONNECTED)
                        }
                    )

                    DevRowCard(
                        title = "Video Call - Outgoing Ringing",
                        subtitle = "Video call outgoing call overlay",
                        icon = Icons.Default.Videocam,
                        badge = "Outgoing",
                        badgeColor = Color(0xFF38BDF8),
                        onClick = {
                            onOpenMockCall("Amma (Mom)", CallType.VIDEO, CallState.RINGING_OUTGOING)
                        }
                    )

                    DevRowCard(
                        title = "Video Call - Incoming Ringing",
                        subtitle = "Accept & Decline buttons for incoming video call",
                        icon = Icons.Default.CallReceived,
                        badge = "Incoming",
                        badgeColor = Color(0xFFF59E0B),
                        onClick = {
                            onOpenMockCall("Thaththa (Dad)", CallType.VIDEO, CallState.RINGING_INCOMING)
                        }
                    )
                }
            }

            // Section 4: Lock Screen & Native Incoming Call Activity
            item {
                SectionHeader(title = "4. Native Lock-Screen Incoming Call")
            }

            item {
                DevRowCard(
                    title = "Launch Real IncomingCallActivity",
                    subtitle = "Tests the full-screen lock screen bypass and ringing activity on your phone",
                    icon = Icons.Default.Lock,
                    badge = "Native Activity",
                    badgeColor = Color(0xFFA855F7),
                    onClick = {
                        // Set incoming call session in repository
                        val mockIncomingSession = CallSession(
                            callId = UUID.randomUUID().toString(),
                            peerId = "demo_mom",
                            peerName = "Amma (Mom)",
                            callType = CallType.VOICE,
                            isIncoming = true,
                            state = CallState.RINGING_INCOMING
                        )
                        appContainer.callRepository.setCallSession(mockIncomingSession)

                        // Start IncomingCallActivity directly
                        val intent = Intent(context, IncomingCallActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // Section 5: Chat UI Sandbox
            item {
                SectionHeader(title = "5. Chat Screen Sandbox")
            }

            item {
                DevRowCard(
                    title = "Open Demo Chat with Amma (Mom)",
                    subtitle = "Preview chat bubbles (Sent, LAN, Read), stickers, timestamps, typing bar",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    badge = "Chat UI",
                    badgeColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        coroutineScope.launch {
                            seedDemoData(appContainer)
                            onOpenChat("demo_mom")
                        }
                    }
                )
            }

            // Section 6: Dialogs
            item {
                SectionHeader(title = "6. Dialogs")
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DevActionCard(
                        title = "Edit Name Dialog",
                        subtitle = "Profile name popup",
                        icon = Icons.Default.Edit,
                        accentColor = Color(0xFF60A5FA),
                        modifier = Modifier.weight(1f),
                        onClick = { showEditProfileDialog = true }
                    )

                    DevActionCard(
                        title = "Add Device Dialog",
                        subtitle = "Manual IP entry popup",
                        icon = Icons.Default.Sensors,
                        accentColor = Color(0xFF34D399),
                        modifier = Modifier.weight(1f),
                        onClick = { showAddContactDialog = true }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Edit Name Dialog Preview
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Your Profile Name", color = PokieTextPrimary) },
            text = {
                Column {
                    Text(
                        "This name will be broadcast to family members on your WiFi network.",
                        color = PokieTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = testProfileName,
                        onValueChange = { testProfileName = it },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = PokieTextPrimary,
                            unfocusedTextColor = PokieTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditProfileDialog = false
                        Toast.makeText(context, "Saved name: $testProfileName", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel", color = PokieTextSecondary)
                }
            },
            containerColor = PokieDarkSurface
        )
    }

    // Add Contact Dialog Preview
    if (showAddContactDialog) {
        var name by remember { mutableStateOf("") }
        var ip by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            title = { Text("Add Device / Contact", color = PokieTextPrimary) },
            text = {
                Column {
                    Text(
                        "Enter the name and local IP address (e.g. 192.168.1.50) of the device.",
                        color = PokieTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Device Name (e.g. Mom's Phone)") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = PokieTextPrimary,
                            unfocusedTextColor = PokieTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it },
                        placeholder = { Text("Local IP Address (e.g. 192.168.1.100)") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = PokieTextPrimary,
                            unfocusedTextColor = PokieTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAddContactDialog = false
                        Toast.makeText(context, "Device added: $name ($ip)", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddContactDialog = false }) {
                    Text("Cancel", color = PokieTextSecondary)
                }
            },
            containerColor = PokieDarkSurface
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun DevRowCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String,
    badgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PokieDarkCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = badgeColor.copy(alpha = 0.15f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = PokieTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = PokieTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeColor.copy(alpha = 0.2f)
            ) {
                Text(
                    text = badge,
                    color = badgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun DevActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PokieDarkCard)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                color = PokieTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = PokieTextSecondary,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
        }
    }
}

private suspend fun seedDemoData(appContainer: AppContainer) {
    val contactRepo = appContainer.contactRepository
    val chatRepo = appContainer.chatRepository
    val myId = appContainer.currentUserId

    // 1. Insert Contacts
    val mom = Contact(
        id = "demo_mom",
        name = "Amma (Mom)",
        ipAddress = "192.168.1.101",
        isOnlineLan = true,
        avatarColor = 0xFFE91E63
    )
    val dad = Contact(
        id = "demo_dad",
        name = "Thaththa (Dad)",
        ipAddress = "192.168.1.102",
        isOnlineLan = true,
        avatarColor = 0xFF2196F3
    )
    val sister = Contact(
        id = "demo_sister",
        name = "Nangi (Sister)",
        ipAddress = "192.168.1.105",
        isOnlineLan = false,
        avatarColor = 0xFFFF9800
    )
    val livingRoom = Contact(
        id = "demo_tv",
        name = "Living Room TV Tab",
        ipAddress = "192.168.1.120",
        isOnlineLan = true,
        avatarColor = 0xFF9C27B0
    )

    contactRepo.saveOrUpdateContact(mom)
    contactRepo.saveOrUpdateContact(dad)
    contactRepo.saveOrUpdateContact(sister)
    contactRepo.saveOrUpdateContact(livingRoom)

    // 2. Insert Sample Messages for Mom
    chatRepo.saveMessage(
        senderId = "demo_mom",
        receiverId = myId,
        content = "Putha, gedara enawa neda dinner ekata?",
        type = MessageType.TEXT,
        isFromMe = false,
        status = MessageStatus.DELIVERED
    )
    chatRepo.saveMessage(
        senderId = myId,
        receiverId = "demo_mom",
        content = "Ow amma, mama dan office eken pitath una! 🚗",
        type = MessageType.TEXT,
        isFromMe = true,
        status = MessageStatus.READ
    )
    chatRepo.saveMessage(
        senderId = "demo_mom",
        receiverId = myId,
        content = "🍲",
        type = MessageType.STICKER,
        isFromMe = false,
        status = MessageStatus.DELIVERED
    )
    chatRepo.saveMessage(
        senderId = myId,
        receiverId = "demo_mom",
        content = "❤️",
        type = MessageType.STICKER,
        isFromMe = true,
        status = MessageStatus.SENT_LAN
    )
}
