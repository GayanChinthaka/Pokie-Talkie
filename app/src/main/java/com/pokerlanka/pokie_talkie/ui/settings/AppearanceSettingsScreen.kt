package com.pokerlanka.pokie_talkie.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerlanka.pokie_talkie.data.local.AppThemeMode
import com.pokerlanka.pokie_talkie.ui.components.Material3SettingsGroup
import com.pokerlanka.pokie_talkie.ui.components.Material3SettingsItem

val THEME_ACCENT_PALETTES = listOf(
    Pair("Emerald Green", 0xFF00A884),
    Pair("Ocean Blue", 0xFF0284C7),
    Pair("Royal Violet", 0xFF8B5CF6),
    Pair("Sunset Amber", 0xFFF59E0B),
    Pair("Crimson Red", 0xFFEF4444),
    Pair("Teal Proximity", 0xFF14B8A6)
)

val AVATAR_COLOR_PALETTES = listOf(
    0xFF00A884, // Emerald
    0xFF0284C7, // Sky Blue
    0xFF8B5CF6, // Purple
    0xFFEC4899, // Pink
    0xFFF59E0B, // Amber
    0xFFEF4444, // Red
    0xFF6366F1, // Indigo
    0xFF14B8A6  // Teal
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val avatarColor by viewModel.avatarColor.collectAsState()
    val profileImagePath by viewModel.profileImagePath.collectAsState()
    val accentColor by viewModel.themeAccentColor.collectAsState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.saveProfileImageFromUri(context, uri)
            Toast.makeText(context, "Profile photo updated!", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Appearance",
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
            // Theme Mode Group
            item {
                Material3SettingsGroup(
                    title = "Theme Mode",
                    items = listOf(
                        Material3SettingsItem(
                            icon = Icons.Default.BrightnessAuto,
                            title = { Text("System Default") },
                            description = { Text("Follows your Android system light/dark theme") },
                            trailingContent = {
                                RadioButton(
                                    selected = themeMode == AppThemeMode.SYSTEM,
                                    onClick = { viewModel.updateThemeMode(AppThemeMode.SYSTEM) },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                            },
                            onClick = { viewModel.updateThemeMode(AppThemeMode.SYSTEM) }
                        ),
                        Material3SettingsItem(
                            icon = Icons.Default.LightMode,
                            title = { Text("Light Theme") },
                            description = { Text("High-visibility daytime theme") },
                            trailingContent = {
                                RadioButton(
                                    selected = themeMode == AppThemeMode.LIGHT,
                                    onClick = { viewModel.updateThemeMode(AppThemeMode.LIGHT) },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                            },
                            onClick = { viewModel.updateThemeMode(AppThemeMode.LIGHT) }
                        ),
                        Material3SettingsItem(
                            icon = Icons.Default.DarkMode,
                            title = { Text("Dark Theme") },
                            description = { Text("Battery-friendly OLED dark theme") },
                            trailingContent = {
                                RadioButton(
                                    selected = themeMode == AppThemeMode.DARK,
                                    onClick = { viewModel.updateThemeMode(AppThemeMode.DARK) },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                            },
                            onClick = { viewModel.updateThemeMode(AppThemeMode.DARK) }
                        )
                    )
                )
            }

            // Accent Color Group
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "THEME ACCENT COLOR",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Select your preferred primary brand color",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(THEME_ACCENT_PALETTES) { palette ->
                                    val isSelected = palette.second == accentColor
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.clickable {
                                            viewModel.updateThemeAccentColor(palette.second)
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(Color(palette.second))
                                                .then(
                                                    if (isSelected) {
                                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                                    } else Modifier
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = palette.first.split(" ").first(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Profile Avatar Accent Color Group
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "AVATAR ACCENT COLOR",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Sets the fallback background color for your avatar circle in chats & calls",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(AVATAR_COLOR_PALETTES) { colorHex ->
                                    val isSelected = colorHex == avatarColor
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color(colorHex))
                                            .clickable { viewModel.updateAvatarColor(colorHex) }
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                                } else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Photo Management
            item {
                val photoItems = buildList {
                    add(
                        Material3SettingsItem(
                            icon = Icons.Default.PhotoLibrary,
                            title = { Text(if (profileImagePath != null) "Change Profile Photo" else "Set Profile Photo") },
                            description = { Text("Choose a custom picture from your device gallery") },
                            onClick = { photoPickerLauncher.launch("image/*") }
                        )
                    )
                    if (profileImagePath != null) {
                        add(
                            Material3SettingsItem(
                                icon = Icons.Default.Delete,
                                title = { Text("Remove Photo", color = MaterialTheme.colorScheme.error) },
                                description = { Text("Revert to colored letter avatar") },
                                onClick = {
                                    viewModel.removeProfileImage(context)
                                    Toast.makeText(context, "Profile photo removed", Toast.LENGTH_SHORT).show()
                                }
                            )
                        )
                    }
                }

                Material3SettingsGroup(
                    title = "Profile Photo",
                    items = photoItems
                )
            }
        }
    }
}
