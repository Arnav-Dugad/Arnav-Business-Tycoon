package com.arnavgames.tycoon.ui.common

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.PillAction
import com.arnavgames.tycoon.designsystem.components.PillStyle
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space

/** Tab introductions shown once: what the place is, how it pays, the move that matters. */
enum class Intro(val key: String, val title: String, val body: String) {
    Markets(
        "markets", "How the markets work",
        "Shares pay you every day you hold them, and prices move with the news. Own 5% of a company and you get a seat on its board.",
    ),
    Estates(
        "estates", "How property works",
        "Tenants pay rent every few hours. Every neighbourhood has its own price cycle: buy low, sell high, renovate for more rent.",
    ),
    Collection(
        "collection", "How collecting works",
        "Pieces on display earn Status, and Status gets you discounts and better auctions. A new sale opens every six hours and stays open for most of them.",
    ),
}

/** A dismissible introduction card, shown the first time a tab is opened (remembered on this device). */
@Composable
fun FirstVisitTip(intro: Intro, icon: ImageVector) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("intros", Context.MODE_PRIVATE) }
    var seen by remember { mutableStateOf(prefs.getBoolean(intro.key, false)) }
    if (seen) return
    val c = Aurum.colors
    AurumCard(Modifier.fillMaxWidth().testTag("intro-${intro.key}"), highlighted = true) {
        Row(verticalAlignment = Alignment.Top) {
            AbtIcon(icon, null, tint = c.goldText, size = 22.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(intro.title, style = Aurum.type.titleSmall, color = c.textPrimary)
                Spacer(Modifier.height(Space.xxs))
                Text(intro.body, style = Aurum.type.bodySmall, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(Space.sm))
        PillAction("Got it", true, { prefs.edit().putBoolean(intro.key, true).apply(); seen = true }, style = PillStyle.Glass)
    }
}
