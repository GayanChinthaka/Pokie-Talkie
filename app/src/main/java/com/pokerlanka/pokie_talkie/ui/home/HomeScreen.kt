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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkBg
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkCard
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkInput
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkSurface
import com.pokerlanka.pokie_talkie.ui.theme.PokieEmerald
import com.pokerlanka.pokie_talkie.ui.theme.PokieLanActiveBadge
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextPrimary
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToChat: (String) -> Unit,
    onNavigateToCall: (String, CallType) -> Unit,
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
                                color = PokieTextPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PokieLanActiveBadge.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Wifi,
                                        contentDescription = null,
                                        tint = PokieLanActiveBadge,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "LAN P2P",
                                        color = PokieLanActiveBadge,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            text = "You: ${state.myDeviceName} (${state.myUserId})",
                            color = PokieTextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::openEditProfileDialog) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile Name",
                            tint = PokieEmerald
                        )
                    }
                    IconButton(onClick = viewModel::openAddContactDialog) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Family Member",
                            tint = PokieEmerald
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PokieDarkSurface
                )
            )
        },
        containerColor = PokieDarkBg,
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
                    Text("Search family members...", color = PokieTextSecondary, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = PokieTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = PokieTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = PokieDarkInput,
                    unfocusedContainerColor = PokieDarkInput,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = PokieTextPrimary,
                    unfocusedTextColor = PokieTextPrimary
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
                            color = PokieEmerald.copy(alpha = 0.15f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = PokieEmerald,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Scanning Local WiFi Subnet...",
                            color = PokieTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Open Pokie Talkie on other family phones connected to the same WiFi. They will appear here automatically in real time!",
                            color = PokieTextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = viewModel::openAddContactDialog,
                            colors = ButtonDefaults.buttonColors(containerColor = PokieEmerald)
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
                        ContactItemCard(
                            contact = contact,
                            onClick = { onNavigateToChat(contact.id) },
                            onVoiceCall = {
                                viewModel.initiateCall(contact, CallType.VOICE)
                                onNavigateToCall(contact.id, CallType.VOICE)
                            },
                            onVideoCall = {
                                viewModel.initiateCall(contact, CallType.VIDEO)
                                onNavigateToCall(contact.id, CallType.VIDEO)
                            },
                            onDelete = {
                                viewModel.deleteContact(contact.id)
                            }
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
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        placeholder = { Text("e.g. Dad, Mom, Living Room") },
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
                    onClick = { viewModel.saveProfileName(editNameInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = PokieEmerald)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeEditProfileDialog) {
                    Text("Cancel", color = PokieTextSecondary)
                }
            },
            containerColor = PokieDarkSurface
        )
    }

    // Add Contact Dialog
    if (state.showAddContactDialog) {
        AlertDialog(
            onDismissRequest = viewModel::closeAddContactDialog,
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
                        value = addContactName,
                        onValueChange = { addContactName = it },
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
                        value = addContactIp,
                        onValueChange = { addContactIp = it },
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
                        if (addContactName.isNotBlank() && addContactIp.isNotBlank()) {
                            viewModel.addContact(addContactName, addContactIp)
                            addContactName = ""
                            addContactIp = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PokieEmerald)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeAddContactDialog) {
                    Text("Cancel", color = PokieTextSecondary)
                }
            },
            containerColor = PokieDarkSurface
        )
    }
}

@Composable
fun ContactItemCard(
    contact: Contact,
    onClick: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit,
    onDelete: () -> Unit,
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
                    color = PokieTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LanStatusBadge(isOnlineLan = contact.isOnlineLan)
                    if (contact.isOnlineLan && contact.ipAddress.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = contact.ipAddress,
                            color = PokieTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Quick Call Actions
            Row {
                IconButton(
                    onClick = onVoiceCall,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(PokieEmerald.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Voice Call",
                        tint = PokieEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onVideoCall,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(PokieEmerald.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Video Call",
                        tint = PokieEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (!contact.isOnlineLan) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Offline Device",
                            tint = PokieTextSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
