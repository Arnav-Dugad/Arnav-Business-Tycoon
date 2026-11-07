package com.arnavgames.tycoon.ui.mentor

import android.content.Context
import com.arnavgames.tycoon.core.content.MentorContent
import com.arnavgames.tycoon.core.content.MentorLineDef
import com.arnavgames.tycoon.core.engine.GameEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Decides what The Chairman says (content/data/mentor.json). Reactions to story moments are shown once per
 * generation; reactions to moments in play once ever; tips rotate through the current chapter's advice when the
 * player opens the Empire tab. Which lines were seen is a UI preference, not game state.
 */
class MentorDirector(private val content: MentorContent, app: Context) {
    private val prefs by lazy { app.getSharedPreferences("mentor", Context.MODE_PRIVATE) }
    private val _line = MutableStateFlow<MentorLineDef?>(null)
    val line: StateFlow<MentorLineDef?> = _line.asStateFlow()
    val name get() = content.name

    private var generation = 1
    private var lastTipAt = 0L

    /** Maps a game event to a mentor trigger, or null if The Chairman stays quiet. */
    fun triggerFor(e: GameEvent): String? = when (e) {
        is GameEvent.BusinessOpened -> "open:${e.businessId}"
        is GameEvent.MissionClaimed -> "mission:${e.missionId}"
        is GameEvent.ChapterCompleted -> "done:${e.chapter}"
        is GameEvent.TierReached -> "event:tier"
        is GameEvent.ManagerHired -> "event:manager"
        is GameEvent.ExpansionCompleted -> "event:expansion"
        is GameEvent.HqUpgraded -> "event:hq"
        is GameEvent.ConglomerateFormed -> "event:conglomerate"
        is GameEvent.HoldingFormed -> "event:holding"
        is GameEvent.TradeExecuted -> "event:trade"
        is GameEvent.ShortCovered -> "event:short"
        is GameEvent.PropertyBought -> "event:property_bought"
        is GameEvent.PropertySold -> "event:property_sold"
        is GameEvent.RenovationCompleted -> "event:renovation"
        is GameEvent.TowerCompleted -> "event:tower"
        is GameEvent.Disaster -> "event:disaster"
        is GameEvent.Foreclosure -> "event:foreclosure"
        is GameEvent.LuxuryBought -> "event:luxury"
        is GameEvent.AuctionWon -> "event:auction_won"
        is GameEvent.AuctionLost -> "event:auction_lost"
        is GameEvent.WorldEventStarted -> "event:world_event"
        is GameEvent.BusinessSold -> "event:business_sold"
        is GameEvent.PaymentMissed -> "event:payment_missed"
        is GameEvent.LoanTaken -> "event:loan_taken"
        is GameEvent.LoanRepaid -> "event:loan_repaid"
        is GameEvent.TaxFiled -> if (e.audited) "event:tax_audited" else if (e.auto) null else "event:tax_filed"
        GameEvent.Restructured -> "event:restructured"
        GameEvent.PhoenixReborn -> "event:phoenix"
        is GameEvent.CrownPassed -> "event:crown_passed"
        is GameEvent.NemesisBeat -> "event:nemesis"
        is GameEvent.QuarterEnded -> "event:quarter"
        is GameEvent.IpoLaunched -> "event:ipo"
        else -> null
    }

    private fun story(trigger: String) = trigger.startsWith("mission:") || trigger.startsWith("open:") || trigger.startsWith("done:") || trigger.startsWith("chapter:")
    private fun seenKey(l: MentorLineDef) = if (story(l.trigger)) "g$generation:${l.id}" else l.id
    private fun seen(l: MentorLineDef) = prefs.getBoolean(seenKey(l), false)
    private fun markSeen(l: MentorLineDef) = prefs.edit().putBoolean(seenKey(l), true).apply()

    fun setGeneration(g: Int) { generation = g }

    /** A game event happened: say the first unseen line for it, if any. */
    fun onEvent(e: GameEvent) {
        if (e is GameEvent.CrownPassed) generation = e.generation
        val trigger = triggerFor(e) ?: return
        val line = content.byTrigger[trigger].orEmpty().firstOrNull { !seen(it) } ?: return
        markSeen(line)
        _line.value = line
    }

    /**
     * Opening the Empire tab: if nothing is being said, offer the chapter's opening words (once) or a rotating tip
     * (at most every 10 minutes of real time).
     */
    fun offerTip(chapter: Int, nowMs: Long = System.currentTimeMillis()) {
        if (_line.value != null || nowMs - lastTipAt < TIP_GAP_MS) return
        lastTipAt = nowMs
        val opening = content.byTrigger["chapter:$chapter"].orEmpty().firstOrNull { !seen(it) }
        if (opening != null) { markSeen(opening); _line.value = opening; return }
        val tips = content.byTrigger["tip:$chapter"].orEmpty()
        if (tips.isEmpty()) return
        val next = prefs.getInt("tip:$chapter", 0)
        prefs.edit().putInt("tip:$chapter", next + 1).apply()
        _line.value = tips[next % tips.size]
    }

    fun dismiss() { _line.value = null }

    companion object {
        const val TIP_GAP_MS = 10 * 60_000L
    }
}
