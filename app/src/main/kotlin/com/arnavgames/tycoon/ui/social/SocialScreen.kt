package com.arnavgames.tycoon.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.cloud.BoardEntry
import com.arnavgames.tycoon.cloud.Friend
import com.arnavgames.tycoon.cloud.FriendRequest
import com.arnavgames.tycoon.cloud.Gift
import com.arnavgames.tycoon.cloud.LotResult
import com.arnavgames.tycoon.cloud.SealedLot
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.AurumChip
import com.arnavgames.tycoon.designsystem.components.GhostButton
import com.arnavgames.tycoon.designsystem.components.GoldButton
import com.arnavgames.tycoon.designsystem.components.HairlineDivider
import com.arnavgames.tycoon.designsystem.components.PillAction
import com.arnavgames.tycoon.designsystem.components.PillStyle
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.components.SegmentedSelector
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.common.GlassScaffold
import com.arnavgames.tycoon.ui.common.durationText
import kotlinx.coroutines.launch
import java.util.Locale

private enum class BoardTab(val label: String) { Global("Global"), Friends("Friends"), Fair("Fair play"), League("League") }

/** Friends, leaderboards, gifts and Sealed Lots (docs/GDD.md §14). Needs a verified account. */
@Composable
fun SocialScreen(vm: GameViewModel, onBack: () -> Unit, onOpenAccount: () -> Unit) {
    val social = vm.social
    val user by vm.cloud.user.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val fmt: (Money) -> String = { MoneyFormat.short(it, vm.notation(settings)) }
    val c = Aurum.colors
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(social.displayName) }
    var code by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(BoardTab.Global) }
    var board by remember { mutableStateOf<List<BoardEntry>>(emptyList()) }
    var league by remember { mutableStateOf<List<BoardEntry>>(emptyList()) }
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var requests by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }
    var gifts by remember { mutableStateOf<List<Gift>>(emptyList()) }
    var lot by remember { mutableStateOf<SealedLot?>(null) }
    var lastLot by remember { mutableStateOf<LotResult?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun reload() {
        busy = true
        scope.launch {
            social.publish().onFailure { vm.say(it.message ?: "Couldn't publish your profile") }
            social.board("global").onSuccess { board = it }
            social.board(social.leagueId()).onSuccess { league = it }
            social.incoming().onSuccess { requests = it }
            social.friends().onSuccess { friends = it }
            social.inbox().onSuccess { gifts = it }
            social.currentLot().onSuccess { lot = it }
            social.lastResult().onSuccess { lastLot = it }
            busy = false
        }
    }

    val ready = social.canUse()
    LaunchedEffect(ready) { if (ready) reload() }

    GlassScaffold(title = "Friends", onBack = onBack) {
        if (!vm.cloud.available || !ready) {
            item(key = "need") {
                AurumCard(Modifier.fillMaxWidth()) {
                    Text("Friends, leaderboards, gifts and Sealed Lots need a verified email account.", style = Aurum.type.body, color = c.textSecondary)
                    Spacer(Modifier.height(Space.sm))
                    Text(if (!vm.cloud.available) vm.cloud.offlineReason() else if (user == null) "You're not signed in." else if (user!!.guest) "Guests can back up their game but not join the social features." else "Verify your email, then come back.",
                        style = Aurum.type.bodySmall, color = c.textTertiary)
                    if (vm.cloud.available) { Spacer(Modifier.height(Space.sm)); GoldButton("Open account", onClick = onOpenAccount, modifier = Modifier.fillMaxWidth()) }
                }
            }
            return@GlassScaffold
        }
        item(key = "me") {
            AurumCard(Modifier.fillMaxWidth()) {
                Text("YOUR FRIEND CODE", style = Aurum.type.overline, color = c.goldText)
                Text(social.friendCode(), style = Aurum.type.numberHero, color = c.textPrimary, modifier = Modifier.testTag("friend-code"))
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(name, { name = it.take(24) }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    PillAction("Save name", name.trim().length >= 3 && !busy, { social.displayName = name; reload() })
                    PillAction("Refresh", !busy, { reload() }, style = PillStyle.Glass)
                }
            }
        }
        item(key = "board-h") { SectionHeader("Leaderboards") }
        item(key = "board-tabs") { SegmentedSelector(BoardTab.entries, tab, { it.label }, { tab = it }) }
        item(key = "board") {
            val friendIds = friends.map { it.uid }.toSet()
            val rows = when (tab) {
                BoardTab.Global -> board
                BoardTab.Friends -> board.filter { it.isMe || it.uid in friendIds }
                BoardTab.Fair -> board.filter { !it.boosted }.sortedByDescending { it.fairLnw }
                BoardTab.League -> league
            }
            AurumCard(Modifier.fillMaxWidth()) {
                if (rows.isEmpty()) Text(if (busy) "Loading…" else "Nobody here yet.", style = Aurum.type.bodySmall, color = c.textSecondary)
                rows.take(50).forEachIndexed { i, e ->
                    if (i > 0) HairlineDivider(Modifier.padding(vertical = Space.xxs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = Aurum.type.number, color = if (i < 3) c.goldText else c.textTertiary, modifier = Modifier.width(36.dp))
                        Text(e.name + if (e.isMe) " (you)" else "", style = Aurum.type.titleSmall, color = if (e.isMe) c.goldText else c.textPrimary, modifier = Modifier.weight(1f))
                        if (e.boosted) { AurumChip("Boosted", icon = AbtIcons.Bolt, tint = c.textSecondary); Spacer(Modifier.width(Space.xs)) }
                        Text(if (tab == BoardTab.League) "+" + String.format(Locale.US, "%.2f", e.lnw) else "10^" + String.format(Locale.US, "%.1f", if (tab == BoardTab.Fair) e.fairLnw else e.lnw),
                            style = Aurum.type.number, color = c.textPrimary)
                    }
                }
                Spacer(Modifier.height(Space.xs))
                Text(when (tab) {
                    BoardTab.League -> "This week's league ranks growth (orders of magnitude gained since Monday), so newcomers and veterans compete fairly."
                    BoardTab.Fair -> "Fair play leaves out everyone who used demo purchases."
                    else -> "Net worth as a power of ten. Boosted means demo purchases were used."
                }, style = Aurum.type.label, color = c.textTertiary)
            }
        }
        item(key = "friends-h") { SectionHeader("Friends") }
        item(key = "add") {
            AurumCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(code, { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(8) }, label = { Text("Friend's code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                GoldButton("Send friend request", enabled = code.length == 8 && !busy, onClick = {
                    scope.launch { social.sendRequest(code).onSuccess { vm.say("Request sent."); code = "" }.onFailure { vm.say(it.message ?: "Couldn't send") } }
                }, modifier = Modifier.fillMaxWidth())
                requests.forEach { r ->
                    HairlineDivider(Modifier.padding(vertical = Space.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${r.fromName} wants to be friends", style = Aurum.type.bodySmall, color = c.textPrimary, modifier = Modifier.weight(1f))
                        PillAction("Accept", !busy, { scope.launch { social.accept(r); reload() } })
                        Spacer(Modifier.width(Space.xs))
                        PillAction("Decline", !busy, { scope.launch { social.decline(r); reload() } }, style = PillStyle.Glass)
                    }
                }
                friends.forEach { f ->
                    HairlineDivider(Modifier.padding(vertical = Space.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = Aurum.type.titleSmall, color = c.textPrimary)
                            Text("10^" + String.format(Locale.US, "%.1f", f.nwLog10) + if (f.boosted) " · Boosted" else "", style = Aurum.type.bodySmall, color = c.textSecondary)
                        }
                        PillAction(if (f.giftSentToday) "Gift sent" else "Send 10 Favor", !f.giftSentToday && !busy, { scope.launch { social.sendGift(f.uid).onFailure { vm.say(it.message ?: "Couldn't send") }; reload() } }, style = PillStyle.Glass)
                    }
                }
                if (friends.isEmpty() && requests.isEmpty()) Text("Share your code with friends. Up to 100 friends.", style = Aurum.type.label, color = c.textTertiary)
            }
        }
        if (gifts.isNotEmpty()) item(key = "gifts") {
            AurumCard(Modifier.fillMaxWidth(), highlighted = true) {
                Text("Gifts", style = Aurum.type.titleSmall, color = c.textPrimary)
                gifts.forEach { g ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${g.amount} Favor from a friend", style = Aurum.type.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                        PillAction("Claim", !busy, { scope.launch { social.claim(g); reload() } })
                    }
                }
            }
        }
        item(key = "lot-h") { SectionHeader("Sealed Lot") }
        item(key = "lot") {
            val s = vm.state.collectAsStateWithLifecycle().value
            AurumCard(Modifier.fillMaxWidth()) {
                val l = lot
                if (l == null) { Text(if (busy) "Loading…" else "Unavailable right now.", style = Aurum.type.bodySmall, color = c.textSecondary); return@AurumCard }
                val item = vm.engine.content.luxury.itemById[l.itemId]
                Text(item?.name ?: l.itemId, style = Aurum.type.headline, color = c.textPrimary)
                Text("${item?.maker ?: ""} · estimate ${item?.let { fmt(it.price) } ?: ""} · closes in ${durationText(l.closesInMs)}", style = Aurum.type.bodySmall, color = c.textSecondary)
                Text("One sealed bid each among friends; the highest wins and pays at the close. You can change your bid until then.", style = Aurum.type.label, color = c.textTertiary)
                l.myBid?.let { Text("Your bid: ${fmt(it)}", style = Aurum.type.titleSmall, color = c.goldText) }
                if (item != null && s != null) {
                    Spacer(Modifier.height(Space.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        listOf("0.8", "1.0", "1.3").forEach { m ->
                            val amount = item.price * Multiplier.of(m)
                            PillAction(fmt(amount), s.cash >= amount && !busy, { scope.launch { social.bid(amount).onSuccess { vm.say("Sealed bid placed.") }; reload() } }, style = PillStyle.Glass)
                        }
                    }
                }
                lastLot?.let { r ->
                    Spacer(Modifier.height(Space.sm))
                    HairlineDivider()
                    Spacer(Modifier.height(Space.xs))
                    val name = vm.engine.content.luxury.itemById[r.itemId]?.name ?: r.itemId
                    Text(if (r.bids == 0) "Last week: nobody bid on $name." else if (r.iWon) "Last week you won $name for ${r.winningBid?.let(fmt)}." else "Last week ${r.winnerName ?: "a friend"} won $name for ${r.winningBid?.let(fmt)}.",
                        style = Aurum.type.bodySmall, color = c.textSecondary)
                }
            }
        }
        item(key = "note") { Row(verticalAlignment = Alignment.CenterVertically) { AbtIcon(AbtIcons.Info, null, tint = c.textTertiary, size = 14.dp); Spacer(Modifier.width(Space.xs)); Text("Only your display name, net worth, generation, title and showcase are public.", style = Aurum.type.label, color = c.textTertiary) } }
    }
}
