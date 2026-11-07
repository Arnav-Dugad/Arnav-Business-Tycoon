package com.arnavgames.tycoon.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space

/** A profile avatar: an emblem from the game's own icon set on a metal-tinted badge (no photos, no third-party art). */
data class AvatarDef(val id: String, val name: String, val icon: ImageVector, val metal: Color)

private val GOLD = Color(0xFFD4AF37)
private val EMERALD = Color(0xFF2FA37A)
private val SAPPHIRE = Color(0xFF3D6FD6)
private val RUBY = Color(0xFFC2414B)
private val AMETHYST = Color(0xFF8E5BD1)
private val PLATINUM = Color(0xFFB8C2CC)

object Avatars {
    /** Ids match store.json: the 18 free avatars, then the 6 premium avatar_* cosmetics. */
    val all: List<AvatarDef> = listOf(
        AvatarDef("crown", "Crown", AbtIcons.Crown, GOLD), AvatarDef("store", "Merchant", AbtIcons.Store, EMERALD),
        AvatarDef("chef", "Chef", AbtIcons.Chef, RUBY), AvatarDef("fleet", "Fleet", AbtIcons.Fleet, SAPPHIRE),
        AvatarDef("wheat", "Harvest", AbtIcons.Wheat, GOLD), AvatarDef("factory", "Industry", AbtIcons.Factory, PLATINUM),
        AvatarDef("hanger", "Couture", AbtIcons.Hanger, AMETHYST), AvatarDef("pickaxe", "Miner", AbtIcons.Pickaxe, PLATINUM),
        AvatarDef("flame", "Power", AbtIcons.Flame, RUBY), AvatarDef("bed", "Hotelier", AbtIcons.Bed, SAPPHIRE),
        AvatarDef("bank", "Banker", AbtIcons.Bank, GOLD), AvatarDef("crane", "Builder", AbtIcons.Crane, EMERALD),
        AvatarDef("film", "Mogul", AbtIcons.Film, AMETHYST), AvatarDef("chip", "Engineer", AbtIcons.Chip, SAPPHIRE),
        AvatarDef("antenna", "Signal", AbtIcons.Antenna, EMERALD), AvatarDef("plane", "Aviator", AbtIcons.Plane, PLATINUM),
        AvatarDef("flask", "Scientist", AbtIcons.Flask, AMETHYST), AvatarDef("rocket", "Astronaut", AbtIcons.Rocket, RUBY),
        AvatarDef("avatar_yacht", "Yacht", AbtIcons.Yacht, SAPPHIRE), AvatarDef("avatar_heli", "Chopper", AbtIcons.Heli, PLATINUM),
        AvatarDef("avatar_island", "Island", AbtIcons.Island, EMERALD), AvatarDef("avatar_mansion", "Mansion", AbtIcons.Mansion, GOLD),
        AvatarDef("avatar_watch", "Watch", AbtIcons.Watch, GOLD), AvatarDef("avatar_wine", "Vintage", AbtIcons.Wine, RUBY),
    )
    private val byId = all.associateBy { it.id }
    fun of(id: String?): AvatarDef = byId[id ?: "crown"] ?: all.first()
    fun premium(id: String) = id.startsWith("avatar_")

    /** Ring colour of an equipped frame cosmetic (null = no frame). */
    fun frameColor(frame: String?): Color? = when (frame) {
        "frame_gold" -> GOLD; "frame_emerald" -> EMERALD; "frame_sapphire" -> SAPPHIRE; "frame_obsidian" -> Color(0xFF1B1B22)
        "frame_ruby" -> RUBY; "frame_amethyst" -> AMETHYST; "frame_rose_gold" -> Color(0xFFE0A899); "frame_platinum" -> PLATINUM
        else -> null
    }
}

@Composable
fun AvatarBadge(avatarId: String?, frame: String?, size: Dp, modifier: Modifier = Modifier) {
    val a = Avatars.of(avatarId)
    val ring = Avatars.frameColor(frame)
    // Light backgrounds need a deeper tint for the white emblem to stay readable.
    val (top, bottom) = if (Aurum.colors.isDark) 0.55f to 0.18f else 0.95f to 0.7f
    Box(
        modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(a.metal.copy(alpha = top), a.metal.copy(alpha = bottom))))
            .then(if (ring != null) Modifier.border(size * 0.06f, ring, CircleShape) else Modifier.border(1.dp, a.metal.copy(alpha = 0.6f), CircleShape))
            .semantics { contentDescription = "Avatar: ${a.name}" },
        contentAlignment = Alignment.Center,
    ) {
        AbtIcon(a.icon, null, tint = Color.White.copy(alpha = 0.92f), size = size * 0.5f)
    }
}

/** Grid of avatars; locked premium ones are dimmed and point to the Store. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarPicker(current: String?, owned: Set<String>, onPick: (String) -> Unit, onLocked: (String) -> Unit) {
    val c = Aurum.colors
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(Space.sm), maxItemsInEachRow = 4) {
        Avatars.all.forEach { a ->
            val locked = Avatars.premium(a.id) && a.id !in owned
            val isSelected = Avatars.of(current).id == a.id
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape)
                        .then(if (isSelected) Modifier.border(3.dp, c.goldText, CircleShape) else Modifier)
                        .clickable { if (locked) onLocked(a.id) else onPick(a.id) }
                        .semantics { selected = isSelected }
                        .testTag("avatar-${a.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    AvatarBadge(a.id, null, 48.dp, Modifier.alpha(if (locked) 0.35f else 1f))
                    if (locked) AbtIcon(AbtIcons.Lock, null, tint = c.textPrimary, size = 18.dp)
                }
                Text(a.name, style = Aurum.type.label, color = if (locked) c.textTertiary else c.textSecondary, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.width(72.dp))
            }
        }
    }
}
