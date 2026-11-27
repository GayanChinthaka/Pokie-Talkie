package com.pokerlanka.pokie_talkie.ui.home

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.platform.LocalContext
import com.pokerlanka.pokie_talkie.data.local.ConnectionMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerlanka.pokie_talkie.data.model.CallType
import com.pokerlanka.pokie_talkie.data.model.Contact
import com.pokerlanka.pokie_talkie.ui.components.AvatarView
import com.pokerlanka.pokie_talkie.ui.dev.DevConfig
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkCard
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkInput
import com.pokerlanka.pokie_talkie.ui.theme.PokieLanActiveBadge
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextPrimary
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToChat: (String) -> Unit,
    onNavigateToCall: (String, CallType) -> Unit = { _, _ -> },
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDevHub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    var addContactName by remember { mutableStateOf("") }
    var addContactIp by remember { mutableStateOf("") }
    var selectedContactForMenu by remember { mutableStateOf<Contact?>(null) }
    var contactPendingDelete by remember { mutableStateOf<Contact?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Pokie Talkie",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "You: ${state.myDeviceName}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    if (DevConfig.IS_DEV_MODE) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clickable(onClick = onNavigateToDevHub)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🧪 Test",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    ConnectionModeSwitch(
                        mode = state.connectionMode,
                        onToggle = viewModel::toggleConnectionMode,
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openAddContactDialog,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 12.dp, end = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Family Member / Device",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = {
                    Text("Search family members...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Family members list
            if (state.contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (state.connectionMode == ConnectionMode.BLUETOOTH) Icons.Default.Bluetooth else Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (state.connectionMode == ConnectionMode.BLUETOOTH) {
                                "No Paired Bluetooth Devices Found"
                            } else {
                                "Scanning Local WiFi Subnet..."
                            },
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (state.connectionMode == ConnectionMode.BLUETOOTH) {
                                "Pair your family phones in Android Bluetooth settings to chat & call over Bluetooth with zero internet."
                            } else {
                                "Open Pokie Talkie on other family phones connected to the same WiFi. They will appear here automatically in real time!"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = viewModel::openAddContactDialog,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Device by IP")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.contacts, key = { it.id }) { contact ->
                        val unreadCount = state.unreadCounts[contact.id] ?: 0
                        ContactItemCard(
                            contact = contact,
                            unreadCount = unreadCount,
                            onClick = {
                                if (contact.id.isNotBlank()) {
                                    onNavigateToChat(contact.id)
                                }
                            },
                            onLongClick = { selectedContactForMenu = contact }
                        )
                    }
                }
            }
        }
    }

    // Add Contact Dialog
    if (state.showAddContactDialog) {
        AlertDialog(
            onDismissRequest = viewModel::closeAddContactDialog,
            title = { Text("Add Device / Contact", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    Text(
                        "Enter the name and local IP address (e.g. 192.168.1.50) of the device.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = addContactName,
                        onValueChange = { addContactName = it },
                        placeholder = { Text("Device Name (e.g. Mom's Phone)") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addContactIp,
                        onValueChange = { addContactIp = it },
                        placeholder = { Text("Local IP Address (e.g. 192.168.1.100)") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (addContactName.isNotBlank() && addContactIp.isNotBlank()) {
                            viewModel.addContact(addContactName, addContactIp)
                            addContactName = ""
                            addContactIp = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeAddContactDialog) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Contact Long-Press Options Dialog
    val menuContact = selectedContactForMenu
    if (menuContact != null) {
        AlertDialog(
            onDismissRequest = { selectedContactForMenu = null },
            title = {
                Text(
                    text = menuContact.name,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HomeContactActionRow(
                        icon = Icons.Default.Call,
                        label = "Voice Call",
                        onClick = {
                            selectedContactForMenu = null
                            if (viewModel.initiateCall(menuContact, CallType.VOICE)) {
                                onNavigateToCall(menuContact.id, CallType.VOICE)
                            }
                        }
                    )
                    if (state.connectionMode == ConnectionMode.WIFI && menuContact.isOnlineLan) {
                        HomeContactActionRow(
                            icon = Icons.Default.Videocam,
                            label = "Video Call",
                            onClick = {
                                selectedContactForMenu = null
                                if (viewModel.initiateCall(menuContact, CallType.VIDEO)) {
                                    onNavigateToCall(menuContact.id, CallType.VIDEO)
                                }
                            }
                        )
                    }
                    HomeContactActionRow(
                        icon = Icons.Default.DeleteSweep,
                        label = "Clear Chat History",
                        onClick = {
                            viewModel.clearChatHistory(menuContact.id)
                            selectedContactForMenu = null
                            Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                        }
                    )
                    HomeContactActionRow(
                        icon = Icons.Default.Delete,
                        label = "Delete Chat & Contact",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = {
                            selectedContactForMenu = null
                            contactPendingDelete = menuContact
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedContactForMenu = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Confirm Delete Contact & Chat
    val deleteTarget = contactPendingDelete
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { contactPendingDelete = null },
            title = { Text("Delete ${deleteTarget.name}?", fontWeight = FontWeight.Bold) },
            text = { Text("This removes the contact and deletes all messages & downloaded media from this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteContact(deleteTarget.id)
                        contactPendingDelete = null
                        Toast.makeText(context, "Deleted ${deleteTarget.name}", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { contactPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HomeContactActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactItemCard(
    contact: Contact,
    unreadCount: Int = 0,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarView(
                name = contact.name,
                backgroundColor = Color(contact.avatarColor),
                imagePath = contact.profileImagePath,
                isOnlineLan = contact.isOnlineLan,
                size = 50.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                if (contact.isOnlineLan && contact.ipAddress.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = contact.ipAddress,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            if (unreadCount > 0) {
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionModeSwitch(
    mode: ConnectionMode,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBluetooth = mode == ConnectionMode.BLUETOOTH
    val trackWidth = 88.dp
    val trackHeight = 34.dp
    val thumbSize = 28.dp
    val padding = 3.dp

    val softWifiAccent = Color(0xFF4F9D85)
    val softBtAccent = Color(0xFF5B86C5)

    val thumbOffset by animateDpAsState(
        targetValue = if (isBluetooth) trackWidth - thumbSize - padding else padding,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "modeThumbOffset"
    )
    val trackColor by animateColorAsState(
        targetValue = if (isBluetooth) {
            softBtAccent.copy(alpha = 0.14f)
        } else {
            softWifiAccent.copy(alpha = 0.14f)
        },
        label = "modeTrackColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isBluetooth) {
            softBtAccent.copy(alpha = 0.30f)
        } else {
            softWifiAccent.copy(alpha = 0.30f)
        },
        label = "modeBorderColor"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (isBluetooth) softBtAccent else softWifiAccent,
        label = "modeThumbColor"
    )
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(CircleShape)
            .background(trackColor)
            .border(1.5.dp, borderColor, CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.CenterStart
    ) {
        // Mode label inside track opposite to the sliding thumb
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isBluetooth) Arrangement.Start else Arrangement.End
        ) {
            Text(
                text = if (isBluetooth) "BT" else "WI-FI",
                color = labelColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
        }

        // Sliding circular icon thumb
        Surface(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize),
            shape = CircleShape,
            color = thumbColor,
            shadowElevation = 3.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isBluetooth) Icons.Default.Bluetooth else Icons.Default.Wifi,
                    contentDescription = if (isBluetooth) "Bluetooth Mode" else "Wi-Fi Mode",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
