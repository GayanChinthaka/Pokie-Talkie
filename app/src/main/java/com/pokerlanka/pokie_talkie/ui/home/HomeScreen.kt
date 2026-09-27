package com.pokerlanka.pokie_talkie.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import com.pokerlanka.pokie_talkie.lan.ActiveTransportMode
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
import com.pokerlanka.pokie_talkie.ui.components.LanStatusBadge
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
    val state by viewModel.uiState.collectAsState()

    var editNameInput by remember(state.myDeviceName) { mutableStateOf(state.myDeviceName) }
    var addContactName by remember { mutableStateOf("") }
    var addContactIp by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Pokie Talkie",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            val transportText = when (state.activeTransport) {
                                ActiveTransportMode.WIFI -> "Wi-Fi LAN"
                                ActiveTransportMode.WIFI_DIRECT -> "Wi-Fi Direct"
                                ActiveTransportMode.BLUETOOTH -> "Bluetooth"
                                ActiveTransportMode.OFFLINE -> "Offline"
                            }
                            val transportColor = when (state.activeTransport) {
                                ActiveTransportMode.WIFI -> PokieLanActiveBadge
                                ActiveTransportMode.WIFI_DIRECT -> Color(0xFFAB47BC)
                                ActiveTransportMode.BLUETOOTH -> Color(0xFF2979FF)
                                ActiveTransportMode.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            val transportIcon = when (state.activeTransport) {
                                ActiveTransportMode.WIFI -> Icons.Default.Wifi
                                ActiveTransportMode.WIFI_DIRECT -> Icons.Default.Wifi
                                ActiveTransportMode.BLUETOOTH -> Icons.Default.Bluetooth
                                ActiveTransportMode.OFFLINE -> Icons.Default.Sensors
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = transportColor.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = transportIcon,
                                        contentDescription = null,
                                        tint = transportColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = transportText,
                                        color = transportColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
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
                                .padding(end = 4.dp)
                                .clickable(onClick = onNavigateToDevHub)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🧪 UI Test Hub",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

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
                contentColor = Color.White,
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
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Scanning Local WiFi Subnet...",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Open Pokie Talkie on other family phones connected to the same WiFi. They will appear here automatically in real time!",
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
                            onClick = { onNavigateToChat(contact.id) }
                        )
                    }
                }
            }
        }
    }

    // Edit Profile Name Dialog
    if (state.showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = viewModel::closeEditProfileDialog,
            title = { Text("Your Profile Name", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    Text(
                        "This name will be broadcast to family members on your WiFi network.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        placeholder = { Text("e.g. Dad, Mom, Living Room") },
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
                    onClick = { viewModel.saveProfileName(editNameInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeEditProfileDialog) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
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
}

@Composable
fun ContactItemCard(
    contact: Contact,
    unreadCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LanStatusBadge(
                        isOnlineLan = contact.isOnlineLan,
                        bluetoothAddress = contact.bluetoothAddress
                    )
                    if (contact.isOnlineLan && contact.ipAddress.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = contact.ipAddress,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
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
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
