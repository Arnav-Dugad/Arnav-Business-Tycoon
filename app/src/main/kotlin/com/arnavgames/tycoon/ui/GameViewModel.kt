package com.arnavgames.tycoon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arnavgames.tycoon.core.content.BusinessDef
import com.arnavgames.tycoon.core.content.GoalDef
import com.arnavgames.tycoon.core.content.MechanicDef
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.engine.CommandResult
import com.arnavgames.tycoon.core.engine.GameError
import com.arnavgames.tycoon.core.engine.GameEvent
import com.arnavgames.tycoon.core.engine.Mechanics
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.core.money.Rate
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.MechanicState
import com.arnavgames.tycoon.core.state.TimerKind
import com.arnavgames.tycoon.data.settings.Settings
import com.arnavgames.tycoon.data.settings.SettingsStore
import com.arnavgames.tycoon.game.GameSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BuyQty(val label: String, val count: Int) { One("×1", 1), Ten("×10", 10), TwentyFive("×25", 25), Max("Max", -1) }

data class HeaderUi(val netWorth: Money, val cash: Money, val income: Rate, val bullion: Long, val favor: Long)

data class HustleUi(val perTap: Money, val combo: Int, val maxCombo: Int, val comboMultiplier: Double, val frenzyActive: Boolean, val frenzyEndsAtSim: Long, val simNow: Long, val frenzyCharges: Int, val totalTaps: Long)

data class MissionUi(
    val id: String, val title: String, val description: String, val progress: Float, val claimable: Boolean,
    val reward: String, val chapterNumber: Int, val chapterTitle: String, val done: Int, val total: Int,
    /** Where "Show me" goes: "business:<id>", "open:<id>", "tab:<markets|estates|collection>", "counter", "hq", "list" or null. */
    val goTo: String? = null,
)

data class TimerUi(val id: Long, val label: String, val startSim: Long, val endSim: Long, val rushFavor: Long)

data class BusinessUi(
    val id: String, val name: String, val icon: String, val level: Int, val tierName: String, val nextTierLevel: Int?,
    val tierProgress: Float, val netRate: Rate, val managerName: String?, val stored: Money, val storageCap: Money,
    val buyCount: Int, val buyCost: Money, val canAfford: Boolean, val efficiency: Double, val timer: TimerUi?, val mothballed: Boolean,
    val hint: String?,
    /** Tiers reached (0 = still the starter business): picks the business's painting. */
    val tiersReached: Int = 0,
    /** Set on the business whose next purchase pays back fastest: "Best value · pays back in 4 min". */
    val bestValue: String? = null,
    /** What the Buy button adds to income per second (shown before the player commits). */
    val buyGain: Money = Money.ZERO,
)

data class OpenableUi(val id: String, val name: String, val tagline: String, val icon: String, val cost: Money, val canAfford: Boolean, val lockedReason: String?)

data class MemberUi(val name: String, val ready: Boolean, val note: String, val id: String = "")

data class MergerUi(
    val id: String, val name: String, val description: String, val synergy: String, val members: List<MemberUi>,
    val fee: Money, val formed: Boolean, val blocker: String?, val canAfford: Boolean,
)

data class HoldingUi(val name: String?, val unlocked: Boolean, val fee: Money, val canAfford: Boolean, val conglomerates: Int)

data class HqUi(val level: Int, val name: String, val nextName: String?, val nextCost: Money?, val canAfford: Boolean, val slotsUsed: Int, val slots: Int, val offlineCapHours: Double)

data class EmpireUi(
    val header: HeaderUi,
    val hustle: HustleUi,
    val mission: MissionUi?,
    val businesses: List<BusinessUi>,
    val openable: List<OpenableUi>,
    val hq: HqUi,
    val onboardingStep: Int,
    val chapter: Int,
    val claimableCount: Int,
    val mergers: List<MergerUi> = emptyList(),
    val holding: HoldingUi? = null,
    /** Every chapter is done and the crown can pass: the Empire tab's next move. */
    val crown: CrownReadyUi? = null,
)

data class CrownReadyUi(val legacyGain: Long, val multiplierNow: Double, val multiplierAfter: Double, val generation: Int)

@HiltViewModel
class GameViewModel @Inject constructor(
    private val session: GameSession,
    private val settingsStore: SettingsStore,
    private val services: com.arnavgames.tycoon.game.AppServices,
) : ViewModel() {

    val status = session.status
    val offline = session.offline
    /** True once after an old save was converted to real-world money (1.8.0). */
    val converted = session.converted
    fun dismissConverted() = session.dismissConverted()
    val events: SharedFlow<GameEvent> = session.events
    val settings: StateFlow<Settings> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    /** Small per-device UI choices (sort orders, the watchlist, the buy amount); never part of the save. */
    private val uiPrefs by lazy { session.appContext.getSharedPreferences("ui_prefs", android.content.Context.MODE_PRIVATE) }

    /** How many levels a Buy button buys; remembered between sessions. */
    val buyQty = MutableStateFlow(runCatching { BuyQty.valueOf(session.appContext.getSharedPreferences("ui_prefs", android.content.Context.MODE_PRIVATE).getString("buyQty", null) ?: "One") }.getOrDefault(BuyQty.One))

    /** Instruments the player starred (Markets → Watchlist). */
    val watchlist = MutableStateFlow<Set<String>>(session.appContext.getSharedPreferences("ui_prefs", android.content.Context.MODE_PRIVATE).getStringSet("watchlist", emptySet()).orEmpty().toSet())

    fun toggleWatch(id: String) {
        val next = if (id in watchlist.value) watchlist.value - id else watchlist.value + id
        watchlist.value = next
        uiPrefs.edit().putStringSet("watchlist", next).apply()
        say(if (id in next) "Added to your watchlist" else "Removed from your watchlist")
    }

    /** Tapping the tab you are already on: that tab scrolls back to the top. */
    private val _reselect = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val reselect: SharedFlow<String> = _reselect.asSharedFlow()
    fun reselectTab(tab: String) { _reselect.tryEmit(tab) }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val state: StateFlow<GameState?> = session.state

    val empire: StateFlow<EmpireUi?> = combine(session.state.filterNotNull(), buyQty, settings) { s, qty, st -> toEmpireUi(s, qty, st) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val marketsMapper by lazy { com.arnavgames.tycoon.ui.markets.MarketsMapper(session.engine) }

    val markets: StateFlow<com.arnavgames.tycoon.ui.markets.MarketsUi?> = session.state.filterNotNull()
        .map { s -> marketsMapper.toUi(s) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun instrument(s: GameState, id: String) = marketsMapper.detail(s, id)

    private val estatesMapper by lazy { com.arnavgames.tycoon.ui.estates.EstatesMapper(session.engine) }

    val estates: StateFlow<com.arnavgames.tycoon.ui.estates.EstatesUi?> = session.state.filterNotNull()
        .map { s -> estatesMapper.toUi(s) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun city(s: GameState, id: String) = estatesMapper.city(s, id)

    private val collectionMapper by lazy { com.arnavgames.tycoon.ui.collection.CollectionMapper(session.engine) { com.arnavgames.tycoon.ui.common.Art.has(session.appContext, it) } }

    val collection: StateFlow<com.arnavgames.tycoon.ui.collection.CollectionUi?> = session.state.filterNotNull()
        .map { s -> collectionMapper.toUi(s) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val worldMapper by lazy { com.arnavgames.tycoon.ui.world.WorldMapper(session.engine, session.rivalPortraitIds()) }

    val desk: StateFlow<com.arnavgames.tycoon.ui.world.DeskUi?> = session.state.filterNotNull()
        .map { s -> worldMapper.desk(s) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** (unlocked, player rank) for the Empire strip; recomputed at most once a game minute. */
    val fortuneRank: StateFlow<Pair<Boolean, Int>?> = session.state.filterNotNull()
        .map { s -> s.simTimeMs / 60_000 to s }
        .distinctUntilChanged { a, b -> a.first == b.first }
        .map { (_, s) -> session.engine.world.isUnlocked(s, "fortune") to session.engine.world.playerRank(s, session.engine.economy.netWorth(s)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** (loans unlocked, rating name) for the Empire strip. */
    val financeSummary: StateFlow<Pair<Boolean, String>?> = session.state.filterNotNull()
        .map { s -> session.engine.world.isUnlocked(s, "loans") to session.engine.credit.rating(s).name }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dynastyMapper by lazy { com.arnavgames.tycoon.ui.dynasty.DynastyMapper(session.engine) }

    /** Something to claim on the Dynasty tab (missions, daily reward, achievements, season tiers). */
    val dynastyBadge: StateFlow<Boolean> = session.state.filterNotNull()
        .map { s ->
            val p = session.engine.progression
            s.progress.claimableMissions.isNotEmpty() || s.meta.achievementsClaimable.isNotEmpty() || p.ladderAvailable(s) ||
                (s.meta.daily + s.meta.weekly).any { !it.claimed && p.done(s, it) } || session.engine.dynasty.canPrestige(s)
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Accounts, cloud save and (Phase 13) social features. */
    val cloud get() = services.cloud
    /** A background sync found a conflict (both this device and the cloud changed). */
    val cloudConflict get() = services.cloudConflict
    fun clearCloudConflict() = services.clearCloudConflict()

    val sounds by lazy { com.arnavgames.tycoon.audio.SoundPlayer(session.appContext) }
    /** Automatic updates from GitHub releases. */
    val updates get() = services.updates
    val music get() = services.music

    /** The Chairman, the player's mentor (built on first use: content is loaded by then). */
    val mentor by lazy { com.arnavgames.tycoon.ui.mentor.MentorDirector(session.engine.content.mentor, session.appContext) }
    private var mentorListening = false

    /** Called by the Empire tab: starts listening for story moments and offers a tip for the current chapter. */
    fun mentorTip(chapter: Int) {
        session.state.value?.let { mentor.setGeneration(it.dynasty.generation) }
        if (!mentorListening) {
            mentorListening = true
            viewModelScope.launch { session.events.collect { mentor.onEvent(it) } }
        }
        mentor.offerTip(chapter)
    }

    fun checkForUpdate(force: Boolean = false) {
        if (force) viewModelScope.launch {
            updates.check(force = true)
            if (updates.state.value is com.arnavgames.tycoon.update.UpdateState.Idle) {
                say(if (updates.lastCheckFailed) "Couldn't reach GitHub just now. The game will try again later." else "You have the latest version.")
            }
        }
        else updates.onForeground()
    }

    val social by lazy { com.arnavgames.tycoon.cloud.SocialService(cloud, session, session.appContext) }

    fun say(message: String) { _messages.tryEmit(message) }

    /** The only purchase provider shipped: simulated, labelled DEMO, never charges (docs/ARCHITECTURE.md §8). */
    val purchases: com.arnavgames.tycoon.store.PurchaseProvider by lazy { com.arnavgames.tycoon.store.SimulatedPurchaseProvider(session) }

    fun demoPurchase(productId: String) {
        viewModelScope.launch {
            when (val r = purchases.purchase(productId)) {
                com.arnavgames.tycoon.store.PurchaseResult.Granted -> _messages.tryEmit("Demo purchase complete. No money was charged.")
                is com.arnavgames.tycoon.store.PurchaseResult.Refused -> _messages.tryEmit("Not available: ${r.reason}")
            }
        }
    }

    fun fortune(s: GameState) = worldMapper.fortune(s)
    fun rival(s: GameState, id: String) = worldMapper.rival(s, id)
    fun finance(s: GameState) = worldMapper.finance(s)
    fun rivalPortrait(id: String) = worldMapper.portrait(id)

    /** Resolves a decision card and shows its outcome. */
    fun resolveCard(cardId: Long, option: Int, edge: Double = 0.0) = dispatch(Command.ResolveCard(cardId, option, edge)) {
        session.state.value?.world?.log?.lastOrNull()?.let { _messages.tryEmit(it.text) }
    }

    fun luxuryCategory(s: GameState, id: String) = collectionMapper.category(s, id)
    fun luxuryCategoryName(id: String) = collectionMapper.categoryName(id)
    fun luxuryItem(s: GameState, id: String) = collectionMapper.item(s, id, artCredits[id])
    fun auction(s: GameState) = collectionMapper.auction(s)

    /** Museum credit lines from the bundled art credits (assets/img/art/credits.json). */
    private val artCredits: Map<String, String> by lazy { session.readArtCredits() }

    fun artCreditLines(): List<String> = artCredits.values.sorted()
    fun portraitCount(): Int = session.portraitIds().size + session.mentorPortraitCount()
    fun rivalPortraitCount(): Int = session.rivalPortraitIds().size
    fun portraitPath(managerId: String): String? = if (managerId in portraitSet) "img/portraits/$managerId.webp" else null
    private val portraitSet: Set<String> by lazy { session.portraitIds() }
    fun district(s: GameState, id: String) = estatesMapper.district(s, id)
    fun property(s: GameState, id: Long) = estatesMapper.property(s, id)

    fun previewOrder(s: GameState, id: String, side: com.arnavgames.tycoon.ui.markets.OrderSide, fraction: Double, fmt: (Money) -> String) =
        marketsMapper.preview(s, id, side, fraction, fmt)

    fun dispatch(cmd: Command, onOk: () -> Unit = {}) {
        viewModelScope.launch {
            when (val r = session.dispatch(cmd)) {
                is CommandResult.Ok -> onOk()
                is CommandResult.Rejected -> _messages.tryEmit(errorText(r.error))
            }
        }
    }

    // ---- Save slots ----
    val slot: StateFlow<Int> = session.slot
    suspend fun slotInfos() = session.slotInfos()
    fun switchSlot(n: Int, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            session.switchSlot(n).onSuccess { say("Now playing slot ${n + 1}"); onDone() }.onFailure { say(it.message ?: "That slot couldn't be opened") }
        }
    }
    fun deleteSlot(n: Int, onDone: () -> Unit = {}) {
        viewModelScope.launch { session.deleteSlot(n).onSuccess { say("Slot ${n + 1} deleted"); onDone() }.onFailure { say(it.message ?: "Couldn't delete that slot") } }
    }

    // ---- Sandbox cheats ----
    /** The last cheat used ("+$1.2B"), shown with an Undo until the next one or until it is undone. */
    val lastCheat = MutableStateFlow<String?>(null)
    private var beforeCheat: GameState? = null
    fun cheat(kind: String, done: String) {
        val before = state.value ?: return
        viewModelScope.launch {
            when (val r = session.dispatch(Command.Cheat(kind))) {
                is CommandResult.Ok -> { beforeCheat = before; lastCheat.value = done }
                is CommandResult.Rejected -> _messages.tryEmit(errorText(r.error))
            }
        }
    }
    fun undoCheat() {
        val back = beforeCheat ?: return
        viewModelScope.launch { session.restore(back); beforeCheat = null; lastCheat.value = null; say("Undone") }
    }
    fun clearLastCheat() { lastCheat.value = null; beforeCheat = null }

    /** Runs several commands in order ("Claim all"), then says [done] if at least one went through. */
    fun dispatchAll(cmds: List<Command>, done: String) {
        viewModelScope.launch {
            var ok = 0
            cmds.forEach { if (session.dispatch(it) is CommandResult.Ok) ok++ }
            if (ok > 0) say(done)
        }
    }

    fun setBuyQty(q: BuyQty) { buyQty.value = q; uiPrefs.edit().putString("buyQty", q.name).apply() }

    suspend fun exportSave(ctx: android.content.Context, uri: android.net.Uri) {
        val result = runCatching {
            val bytes = session.exportBytes() ?: error("No save yet")
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not open file")
            }
        }
        _messages.tryEmit(if (result.isSuccess) "Save exported." else "Export failed: ${result.exceptionOrNull()?.message}")
    }

    suspend fun importSave(ctx: android.content.Context, uri: android.net.Uri) {
        val result = runCatching {
            val bytes = kotlinx.coroutines.withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not open file")
            }
            session.importBytes(bytes)
        }
        _messages.tryEmit(if (result.isSuccess) "Save imported." else "That file isn't a valid save: ${result.exceptionOrNull()?.message}")
    }
    fun dismissOffline() = session.dismissOffline()
    fun debugScenario(name: String) = session.debugLoadScenario(name)
    fun startOver() = session.startNewGameAfterRecovery()
    /** A screen outside the tabs (the Store) asking the tab bar to show a tab: "dynasty", "businesses", ... */
    private val _tabRequest = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val tabRequest: StateFlow<String?> = _tabRequest
    fun requestTab(target: String) { _tabRequest.value = target }
    fun consumeTabRequest() { _tabRequest.value = null }

    /** The 6-hourly payment for a new loan, exactly as the engine will charge it. */
    fun loanInstallment(amount: Money, ratePerDay: Double, days: Int): Money = session.engine.credit.installment(amount, ratePerDay, days)

    fun updateSettings(f: (Settings) -> Settings) { viewModelScope.launch { settingsStore.update(f) } }

    val engine get() = session.engine

    fun notation(st: Settings) = if (st.notation == com.arnavgames.tycoon.data.settings.NotationPref.Scientific) MoneyFormat.Notation.Scientific else MoneyFormat.Notation.Suffix

    // ------------------------------------------------------------------ mapping

    private fun toEmpireUi(s: GameState, qty: BuyQty, st: Settings): EmpireUi {
        val eng = session.engine
        val eco = eng.economy
        val content = eng.content
        val header = HeaderUi(
            netWorth = eco.netWorth(s),
            cash = s.cash,
            income = eco.totalNetRate(s, managedOnly = true),
            bullion = s.ledger.bullion.centsOrNull ?: 0,
            favor = s.ledger.favor.centsOrNull ?: 0,
        )
        val cfg = content.balance.hustle
        val perTap = cfg.basePerTap * eco.hustleMultiplier(s) + eco.totalNetRate(s, managedOnly = true).over(1) * com.arnavgames.tycoon.core.money.Multiplier.ofDouble(cfg.incomeSecondsPerTap)
        val combo = if (s.simTimeMs - s.hustle.lastTapSim <= cfg.comboWindowMs) s.hustle.combo else 0
        val hustle = HustleUi(
            perTap = perTap, combo = combo, maxCombo = cfg.maxCombo, comboMultiplier = 1.0 + combo * cfg.comboStep,
            frenzyActive = s.hustle.frenzyUntil > s.simTimeMs, frenzyEndsAtSim = s.hustle.frenzyUntil, simNow = s.simTimeMs,
            frenzyCharges = eng.frenzyChargesLeft(s), totalTaps = s.hustle.totalTaps,
        )
        val chapter = content.chapters.firstOrNull { it.number == s.progress.chapter }
        val mission = chapter?.let { ch ->
            val claimable = ch.missions.firstOrNull { it.id in s.progress.claimableMissions }
            val next = claimable ?: ch.missions.firstOrNull { it.id !in s.progress.completedMissions }
            next?.let { m ->
                MissionUi(
                    id = m.id, title = m.title, description = m.description,
                    progress = if (m.id in s.progress.completedMissions) 1f else goalProgress(s, m.goal).toFloat(),
                    claimable = m.id in s.progress.claimableMissions,
                    reward = rewardText(m.rewardCash, m.rewardBullion, m.rewardFavor),
                    chapterNumber = ch.number, chapterTitle = ch.title,
                    done = ch.missions.count { it.id in s.progress.completedMissions }, total = ch.missions.size,
                    goTo = goToFor(s, m.goal),
                )
            }
        }
        val businesses = content.businesses.filter { it.id in s.businesses }.map { def -> businessUi(s, def, qty) }
            .let { list -> bestValue(s)?.let { (id, text) -> list.map { if (it.id == id) it.copy(bestValue = text) else it } } ?: list }
        val openable = content.businesses.filter { it.id !in s.businesses }.map { def ->
            val locked = !eng.isUnlocked(s, def.id)
            OpenableUi(def.id, def.name, def.tagline, def.icon, def.unlockCost, s.cash >= def.unlockCost && !locked, if (locked) "Unlocks in chapter ${def.chapter}" else null)
        }
        val hqDef = content.balance.hqLevels.first { it.level == s.hq.level }
        val hqNext = content.balance.hqLevels.firstOrNull { it.level == s.hq.level + 1 }
        val hq = HqUi(
            level = s.hq.level, name = hqDef.name, nextName = hqNext?.name, nextCost = hqNext?.cost,
            canAfford = hqNext != null && s.cash >= hqNext.cost, slotsUsed = eco.usedSlots(s), slots = eco.slots(s),
            offlineCapHours = eco.offlineCapMs(s) / 3_600_000.0,
        )
        // Merger tree: show conglomerates where the player owns at least one member (formed ones first).
        val mergers = content.conglomerates.filter { c -> c.members.any { it in s.businesses } }.map { c ->
            val blocker = eco.conglomerateBlocker(s, c)
            MergerUi(
                id = c.id, name = c.name, description = c.description, synergy = c.synergyText,
                members = c.members.map { id ->
                    val d = content.business(id)
                    val b = s.businesses[id]
                    val tiers = b?.let { eco.tiersReached(d, it.level) } ?: 0
                    MemberUi(d.name, b != null && tiers >= c.requiredTier && !b.mothballed, when { b == null -> "not owned"; b.mothballed -> "mothballed"; tiers >= c.requiredTier -> "tier ${c.requiredTier} reached"; else -> d.tiers.sortedBy { it.level }.getOrNull(c.requiredTier - 1)?.let { t -> "needs level ${t.level} (now ${b.level})" } ?: "needs tier ${c.requiredTier}" }, id = id)
                },
                fee = c.fee, formed = c.id in s.conglomerates, blocker = blocker?.takeIf { c.id !in s.conglomerates },
                canAfford = s.cash >= c.fee,
            )
        }.sortedWith(compareByDescending<MergerUi> { it.formed }.thenBy { it.blocker != null }.thenBy { it.fee })
        val holding = if (s.conglomerates.isNotEmpty()) HoldingUi(s.holdingName, s.conglomerates.size >= 3, content.balance.holdingFee, s.cash >= content.balance.holdingFee, s.conglomerates.size) else null
        val dyn = session.engine.dynasty
        val crown = if (mission == null && dyn.canPrestige(s)) {
            val pv = session.engine.crownPreview(s)
            CrownReadyUi(pv.legacyGain, dyn.legacyMultiplier(s.dynasty.legacy), pv.multiplier, s.dynasty.generation + 1)
        } else null
        return EmpireUi(header, hustle, mission, businesses, openable, hq, s.progress.onboardingStep, s.progress.chapter, s.progress.claimableMissions.size, mergers, holding, crown)
    }

    fun businessUi(s: GameState, def: BusinessDef, qty: BuyQty): BusinessUi {
        val eco = session.engine.economy
        val b = s.businesses.getValue(def.id)
        val rate = eco.businessRate(s, def.id)
        val count = if (qty == BuyQty.Max) eco.maxAffordable(def, b.level, s.cash).coerceAtLeast(1) else qty.count
        val cost = eco.levelCost(def, b.level, count)
        val tier = def.tiers.lastOrNull { b.level >= it.level }
        val next = eco.nextTier(def, b.level)
        val prevLevel = tier?.level ?: 1
        val tierProgress = if (next == null) 1f else ((b.level - prevLevel).toFloat() / (next.level - prevLevel)).coerceIn(0f, 1f)
        val timer = s.timers.firstOrNull { it.kind == TimerKind.Expansion && it.targetId == def.id }?.let { t ->
            TimerUi(t.id, def.expansions.first { it.id == t.detailId }.name, t.startSim, t.endSim, session.engine.rushCostFavor(s, t))
        }
        return BusinessUi(
            id = def.id, name = def.name, icon = def.icon, level = b.level,
            tierName = tier?.name ?: def.starterName, nextTierLevel = next?.level, tierProgress = tierProgress,
            netRate = rate.net, managerName = def.managers.firstOrNull { it.id == b.managerId }?.name,
            stored = b.stored, storageCap = if (b.managerId == null) eco.storageCap(s, def.id) else Money.ZERO,
            buyCount = count, buyCost = cost, canAfford = s.cash >= cost, efficiency = rate.efficiency,
            timer = timer, mothballed = b.mothballed, hint = hintFor(s, def),
            tiersReached = eco.tiersReached(def, b.level),
            buyGain = if (b.mothballed) Money.ZERO else runCatching {
                val after = eco.businessRate(s.copy(businesses = s.businesses + (def.id to b.copy(level = b.level + count))), def.id).net.perSecond()
                after - rate.net.perSecond()
            }.getOrDefault(Money.ZERO),
        )
    }

    /**
     * The Advisor (docs/PLAN_2.0.md A3), kept subtle: which business's next purchase pays back fastest, either one
     * more level or buying up to the next tier (where output jumps). Returns (business id, chip text).
     */
    private fun bestValue(s: GameState): Pair<String, String>? {
        val eco = session.engine.economy
        val content = session.engine.content
        fun rateAt(id: String, level: Int) = eco.businessRate(s.copy(businesses = s.businesses + (id to s.businesses.getValue(id).copy(level = level))), id).net.perSecond()
        var best: Triple<String, Double, String>? = null
        for ((id, b) in s.businesses) {
            if (b.mothballed) continue
            val def = content.business(id)
            val now = rateAt(id, b.level)
            val routes = buildList {
                add(1 to "")
                eco.nextTier(def, b.level)?.let { t -> if (t.level - b.level in 2..40) add((t.level - b.level) to " (to level ${t.level})") }
            }
            for ((n, label) in routes) {
                val gain = rateAt(id, b.level + n) - now
                if (!gain.isPositive) continue
                val cost = eco.levelCost(def, b.level, n)
                val seconds = cost.ratioTo(gain)
                if (best == null || seconds < best.second) best = Triple(id, seconds, label)
            }
        }
        val (id, seconds, label) = best ?: return null
        // Only a genuinely good deal earns the chip: under a day to pay back.
        if (!seconds.isFinite() || seconds > 86_400) return null
        return id to "Best value$label · pays back in ${paybackText(seconds)}"
    }

    private fun paybackText(sec: Double): String = when {
        sec < 90 -> "${sec.toInt().coerceAtLeast(1)} s"
        sec < 5_400 -> "${(sec / 60).toInt()} min"
        sec < 172_800 -> "${(sec / 3600).toInt()} h"
        sec < 365.0 * 86_400 -> "${(sec / 86_400).toInt()} days"
        else -> "over a year"
    }

    /** Where the Next Move card's "Show me" should take the player for this goal. */
    private fun goToFor(s: GameState, g: com.arnavgames.tycoon.core.content.GoalDef): String? = when (g) {
        is com.arnavgames.tycoon.core.content.GoalDef.OwnBusiness -> if (g.businessId in s.businesses) "business:${g.businessId}" else "open:${g.businessId}"
        is com.arnavgames.tycoon.core.content.GoalDef.BusinessLevel -> if (g.businessId in s.businesses) "business:${g.businessId}" else "open:${g.businessId}"
        is com.arnavgames.tycoon.core.content.GoalDef.HireManager -> if (g.businessId in s.businesses) "business:${g.businessId}" else "open:${g.businessId}"
        is com.arnavgames.tycoon.core.content.GoalDef.ExpansionDone -> "business:${g.businessId}"
        is com.arnavgames.tycoon.core.content.GoalDef.MarketTrades, is com.arnavgames.tycoon.core.content.GoalDef.BoardSeats -> "tab:markets"
        is com.arnavgames.tycoon.core.content.GoalDef.PropertiesOwned -> "tab:estates"
        is com.arnavgames.tycoon.core.content.GoalDef.LuxuryOwned, is com.arnavgames.tycoon.core.content.GoalDef.AuctionsWon -> "tab:collection"
        is com.arnavgames.tycoon.core.content.GoalDef.HustleTaps -> "counter"
        is com.arnavgames.tycoon.core.content.GoalDef.HqLevel -> "hq"
        is com.arnavgames.tycoon.core.content.GoalDef.BusinessesOwned, is com.arnavgames.tycoon.core.content.GoalDef.Conglomerates,
        is com.arnavgames.tycoon.core.content.GoalDef.IncomePerSecond -> "list"
        else -> null
    }

    /** A short, actionable hint when the player is leaving money on the table. */
    private fun hintFor(s: GameState, def: BusinessDef): String? {
        val b = s.businesses.getValue(def.id)
        if (b.managerId != null) return null
        return when (val m = def.mechanic) {
            is MechanicDef.Logistics -> {
                val st = b.mechanic as? MechanicState.Logistics ?: return null
                val plan = Mechanics.logisticsPlan(m, b.level, 1.0)
                val now = Mechanics.logisticsEfficiency(m, b.level, st)
                val best = Mechanics.logisticsEfficiency(m, b.level, MechanicState.Logistics(plan))
                if (best > now * 1.08) "Routes could earn ${"%.0f".format((best / now - 1) * 100)}% more — open to re-plan" else null
            }
            is MechanicDef.Retail -> {
                val st = b.mechanic as? MechanicState.Retail ?: return null
                if (st.pricePct == 100 && b.level >= 5) "Try adjusting your prices" else null
            }
            else -> null
        }
    }

    fun goalProgress(s: GameState, goal: GoalDef): Double = session.engine.goalProgress(s, goal)

    companion object {
        fun rewardText(cash: Money, bullion: Long, favor: Long): String = buildList {
            if (cash.isPositive) add(MoneyFormat.compact(cash))
            if (bullion > 0) add("$bullion Bullion")
            if (favor > 0) add("$favor Favor")
        }.joinToString(" · ")

        fun errorText(e: GameError): String = when (e) {
            is GameError.InsufficientFunds -> "You need ${MoneyFormat.compact(e.needed - e.available)} more."
            is GameError.InsufficientFavor -> "You need ${e.needed - e.available} more Favor. Earn it from missions."
            GameError.NoFreeSlot -> "All business slots are in use. Upgrade your HQ or mothball a business."
            is GameError.Locked -> e.reason
            is GameError.NotFound -> "That isn't available."
            is GameError.Invalid -> e.reason.replaceFirstChar { it.uppercase() }
            GameError.AlreadyDone -> "Already done."
        }
    }
}
