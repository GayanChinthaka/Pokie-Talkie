package com.pokerlanka.pokie_talkie.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokerlanka.pokie_talkie.data.model.Sticker
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkCard
import com.pokerlanka.pokie_talkie.ui.theme.PokieDarkSurface
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextPrimary
import com.pokerlanka.pokie_talkie.ui.theme.PokieTextSecondary

val defaultStickerPacks = mapOf(
    "Family Joy" to listOf(
        Sticker("s1", "👨‍👩‍👧‍👦", "Family Love", "Family Joy"),
        Sticker("s2", "🍲", "Dinner Ready!", "Family Joy"),
        Sticker("s3", "☕", "Coffee Time", "Family Joy"),
        Sticker("s4", "🏠", "I'm Home!", "Family Joy"),
        Sticker("s5", "🛒", "Need groceries?", "Family Joy"),
        Sticker("s6", "❤️", "Love You", "Family Joy")
    ),
    "Fun Reactions" to listOf(
        Sticker("r1", "🎉", "Party", "Fun Reactions"),
        Sticker("r2", "🚀", "On My Way", "Fun Reactions"),
        Sticker("r3", "🔥", "Awesome", "Fun Reactions"),
        Sticker("r4", "😂", "Hilarious", "Fun Reactions"),
        Sticker("r5", "👍", "Got it", "Fun Reactions"),
        Sticker("r6", "😴", "Good night", "Fun Reactions")
    ),
    "Walkie Alerts" to listOf(
        Sticker("w1", "📻", "Over & Out", "Walkie Alerts"),
        Sticker("w2", "📶", "WiFi Strong", "Walkie Alerts"),
        Sticker("w3", "🚨", "Urgent!", "Walkie Alerts"),
        Sticker("w4", "📞", "Call Me", "Walkie Alerts"),
        Sticker("w5", "🍕", "Food Arrived", "Walkie Alerts"),
        Sticker("w6", "💡", "Good Idea", "Walkie Alerts")
    )
)

@Composable
fun StickerPicker(
    onStickerSelected: (Sticker) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = remember { defaultStickerPacks.keys.toList() }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val currentCategory = categories[selectedCategoryIndex]
    val stickers = defaultStickerPacks[currentCategory] ?: emptyList()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(PokieDarkSurface)
    ) {
        TabRow(
            selectedTabIndex = selectedCategoryIndex,
            containerColor = PokieDarkSurface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            categories.forEachIndexed { index, title ->
                Tab(
                    selected = selectedCategoryIndex == index,
                    onClick = { selectedCategoryIndex = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedCategoryIndex == index) MaterialTheme.colorScheme.primary else PokieTextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(stickers) { sticker ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(PokieDarkCard)
                        .clickable { onStickerSelected(sticker) }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = sticker.emojiRepresentation,
                            fontSize = 38.sp
                        )
                        Text(
                            text = sticker.title,
                            color = PokieTextPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
