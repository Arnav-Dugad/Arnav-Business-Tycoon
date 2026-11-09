package com.arnavgames.tycoon.ui.empire

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.common.Art
import com.arnavgames.tycoon.ui.common.GlassScaffold
import com.arnavgames.tycoon.ui.common.Illustration

/**
 * Your company as a whole: the headquarters (which sets how many businesses you can run and how long you earn while
 * away) and the merger tree (conglomerates and the holding company). Moved off the Empire tab so that tab is only your
 * businesses.
 */
@Composable
fun CompanyScreen(vm: GameViewModel, onBack: () -> Unit) {
    val ui by vm.empire.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val e = ui ?: return
    val c = Aurum.colors
    val fmt: (Money) -> String = { MoneyFormat.compact(it, vm.notation(settings)) }
    GlassScaffold("Your company", onBack) {
        item(key = "art") { Illustration(Art.hq(e.hq.level), Modifier.fillMaxWidth().height(160.dp).clip(AurumShapes.large), fallback = Art.property("office_floor")) }
        item(key = "hq-h") { SectionHeader("Headquarters") }
        item(key = "hq") { HqCard(e.hq, fmt, onUpgrade = { vm.dispatch(Command.UpgradeHq) }) }
        item(key = "slots") {
            Text(
                "You run ${e.hq.slotsUsed} of ${e.hq.slots} businesses. A bigger headquarters adds room for more and keeps you earning longer while you're away.",
                style = Aurum.type.bodySmall, color = c.textSecondary, modifier = Modifier.padding(horizontal = Space.xs),
            )
        }
        item(key = "merger-h") { SectionHeader("Merger tree") }
        if (e.mergers.isEmpty()) item(key = "merger-none") {
            Text("Own businesses from the same family to form a conglomerate: their combined output grows.", style = Aurum.type.body, color = c.textSecondary, modifier = Modifier.padding(horizontal = Space.xs))
        }
        e.mergers.forEach { m -> item(key = "merger-" + m.id) { MergerCard(m, fmt, onForm = { vm.dispatch(Command.FormConglomerate(m.id)) }) } }
        e.holding?.let { h -> item(key = "holding") { HoldingCard(h, fmt, onForm = { name -> vm.dispatch(Command.FormHolding(name)) }) } }
    }
}
