package com.pokerlanka.pokie_talkie.ui.settings

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerlanka.pokie_talkie.ui.components.Material3SettingsGroup
import com.pokerlanka.pokie_talkie.ui.components.Material3SettingsItem

private val GodGold = Color(0xFFFFD54F)
private val GodAmber = Color(0xFFFFA000)
private val GodDeepAmber = Color(0xFFFF8F00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GodModeScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGodModeEnabled by viewModel.isGodModeEnabled.collectAsState()
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "godHeroGlow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    fun deactivateGodMode() {
        viewModel.setGodModeEnabled(false)
        Toast.makeText(context, "God Mode deactivated", Toast.LENGTH_SHORT).show()
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "God Mode",
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
            // God Mode Hero Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, GodAmber.copy(alpha = 0.45f)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .drawBehind {
                                    val radius = (this.size.minDimension / 2f) * 1.45f * glowPulse
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colorStops = arrayOf(
                                                0.3f to GodGold.copy(alpha = 0.55f),
                                                0.7f to GodAmber.copy(alpha = 0.25f),
                                                1.0f to Color.Transparent
                                            ),
                                            center = center,
                                            radius = radius
                                        ),
                                        radius = radius,
                                        center = center
                                    )
                                }
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(GodGold, GodAmber, GodDeepAmber)
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "God Mode",
                                tint = Color(0xFF1A1200),
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = GodAmber.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, GodGold.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (isGodModeEnabled) "GOD MODE ACTIVE" else "INACTIVE",
                                color = GodGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "You're a God",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Exclusive premium features & elevated capabilities will live here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Premium Features Placeholder Section
            item {
                Material3SettingsGroup(
                    title = "Premium Features",
                    items = listOf(
                        Material3SettingsItem(
                            icon = Icons.Default.WorkspacePremium,
                            isHighlighted = true,
                            title = { Text("Premium Suite") },
                            description = {
                                Text("Upcoming God Mode perks and experimental premium tools will be configured here.")
                            }
                        )
                    )
                )
            }

            // God Mode Controls
            item {
                Material3SettingsGroup(
                    title = "Controls",
                    items = listOf(
                        Material3SettingsItem(
                            icon = Icons.Default.PowerSettingsNew,
                            title = { Text("God Mode") },
                            description = {
                                Text("Turn off to remove the profile glow and lock God Mode")
                            },
                            trailingContent = {
                                Switch(
                                    checked = isGodModeEnabled,
                                    onCheckedChange = { enabled ->
                                        if (!enabled) {
                                            deactivateGodMode()
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF1A1200),
                                        checkedTrackColor = GodAmber
                                    )
                                )
                            },
                            onClick = {
                                if (isGodModeEnabled) {
                                    deactivateGodMode()
                                }
                            }
                        )
                    )
                )
            }
        }
    }
}
