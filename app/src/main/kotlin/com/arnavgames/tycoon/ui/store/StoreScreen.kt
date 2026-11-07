package com.arnavgames.tycoon.ui.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.content.ProductDef
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.AurumDialog
import com.arnavgames.tycoon.designsystem.components.GhostButton
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.PillAction
import com.arnavgames.tycoon.designsystem.components.PillStyle
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.store.SimulatedPurchaseProvider
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.common.GlassScaffold
import com.arnavgames.tycoon.ui.common.durationText

/**
 * The demo store (docs/GDD.md §15). Every price is a demo label; confirming opens no payment flow. No random rewards,
 * no countdowns, no "only N left", and nothing here ever interrupts play.
 */
@Composable
fun StoreScreen(vm: GameViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val c = Aurum.colors
    val content = vm.engine.content.store
    val store = vm.engine.economy.store
    var demo by remember { mutableStateOf<ProductDef?>(null) }
    val bullion = s.ledger.bullion.cents.toLong()
    Box(Modifier.fillMaxSize()) {
        GlassScaffold(title = "Store", onBack = onBack) {
            item(key = "demo-banner") {
                AurumCard(Modifier.fillMaxWidth(), highlighted = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AbtIcon(AbtIcons.Info, null, tint = c.goldText, size = 22.dp)
                        Spacer(Modifier.width(Space.sm))
                        Text("DEMO STORE", style = Aurum.type.overline, color = c.goldText)
                    }
                    Spacer(Modifier.height(Space.xs))
                    Text("Every purchase here is a demo. No real money is ever charged and no payment details are ever asked for. Using demo purchases shows a Boosted badge on leaderboards; Bullion you earn in play can buy most items too.",
                        style = Aurum.type.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(Space.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AurumChip("$bullion Bullion", icon = AbtIcons.Bullion, tint = c.goldText)
                        if (s.store.boosted) { Spacer(Modifier.width(Space.xs)); AurumChip("Boosted", icon = AbtIcons.Bolt, tint = c.gain, modifier = Modifier.testTag("boosted-badge")) }
                        if (store.vipActive(s)) { Spacer(Modifier.width(Space.xs)); AurumChip("VIP · ${durationText(s.store.vipUntil - s.simTimeMs)}", icon = AbtIcons.Crown, tint = c.goldText) }
                    }
                }
            }
            content.sections.forEach { sec ->
                val products = content.products.filter { it.section == sec.id }
                if (products.isEmpty()) return@forEach
                item(key = "sec-${sec.id}") { SectionHeader(sec.name) }
                items(products, key = { it.id }) { p ->
                    val blocker = store.blocker(s, p)
                    val owned = store.owned(s, p.id)
                    val cosmetic = p.grants.cosmetic
                    AurumCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = Aurum.type.titleSmall, color = c.textPrimary)
                                Text(p.description, style = Aurum.type.bodySmall, color = c.textSecondary)
                                if (owned > 0 && p.limit != 1) Text("Owned ×$owned", style = Aurum.type.label, color = c.textTertiary)
                            }
                        }
                        Spacer(Modifier.height(Space.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
                            when {
                                cosmetic != null && cosmetic in s.store.cosmetics -> {
                                    val kind = content.cosmeticById[cosmetic]?.kind ?: "title"
                                    val equipped = s.store.title == cosmetic || s.store.frame == cosmetic || s.store.avatar == cosmetic
                                    PillAction(if (equipped) "Equipped" else "Equip", !equipped, { vm.dispatch(Command.EquipCosmetic(cosmetic, kind)) }, style = PillStyle.Glass)
                                }
                                blocker != null -> Text(blocker, style = Aurum.type.label, color = c.textTertiary)
                                else -> {
                                    // Bullion earned in play leads (gold when affordable); the demo price is the quiet option.
                                    val canBullion = p.bullionPrice?.let { bullion >= it } == true
                                    p.bullionPrice?.let { price ->
                                        PillAction("$price Bullion", canBullion, { vm.dispatch(Command.BuyWithBullion(p.id)) }, style = if (canBullion) PillStyle.Gold else PillStyle.Glass)
                                    }
                                    p.demoPrice?.let { price ->
                                        PillAction("Demo $price", true, { demo = p }, style = if (canBullion) PillStyle.Glass else PillStyle.Gold, modifier = Modifier.testTag("demo-${p.id}"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "foot") {
                Text("No random rewards, no countdown offers and no limited stock: everything here is always available at the same price.",
                    style = Aurum.type.label, color = c.textTertiary)
            }
        }
        demo?.let { p ->
            AurumDialog(title = "Demo purchase", onDismiss = { demo = null }) {
                Text(SimulatedPurchaseProvider.DEMO_LABEL, style = Aurum.type.headline, color = c.goldText, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("demo-label"))
                Spacer(Modifier.height(Space.sm))
                Text(p.name, style = Aurum.type.titleSmall, color = c.textPrimary)
                Text(p.description, style = Aurum.type.body, color = c.textSecondary)
                Spacer(Modifier.height(Space.xs))
                Text("Shown price: ${p.demoPrice} (demo only). Nothing will be charged and you won't be asked for payment details. Your profile will show a Boosted badge.",
                    style = Aurum.type.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(Space.md))
                GoldButton("Confirm demo purchase", onClick = { demo = null; vm.demoPurchase(p.id) }, modifier = Modifier.fillMaxWidth().testTag("demo-confirm"))
                GhostButton("Cancel", onClick = { demo = null }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
