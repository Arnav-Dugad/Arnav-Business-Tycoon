package com.arnavgames.tycoon.ui.dynasty

import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.Multiplier
import com.arnavgames.tycoon.core.state.GameState
import com.arnavgames.tycoon.core.state.MissionState

data class MissionUi(val index: Int, val title: String, val description: String, val progress: Float, val done: Boolean, val claimed: Boolean, val reward: Money, val bullion: Long, val xp: Long)

data class LadderUi(val available: Boolean, val day: Int, val cashHours: Double, val bullion: Long, val favor: Long, val claimed: Long)

data class SeasonTierUi(val tier: Int, val xp: Long, val reached: Boolean, val claimed: Boolean, val reward: String, val premium: String)

data class SeasonUi(val name: String, val xp: Long, val nextTierXp: Long?, val endsInMs: Long, val tiers: List<SeasonTierUi>, val event: String?, val eventText: String?, val eventGoals: List<MissionUi>)

data class AchievementUi(val id: String, val category: String, val title: String, val description: String, val bullion: Long, val state: Int)

data class DynastyUi(
    val generation: Int, val legacy: Long, val multiplier: Double, val trait: String?, val traitText: String?, val challenge: String?,
    val canPrestige: Boolean, val legacyGain: Long, val ipoAvailable: Boolean, val ipoFloat: Double?, val ipoFloats: List<Double>,
    val ipoValuation: Money, val achievementsClaimable: Int, val achievementsDone: Int, val achievementsTotal: Int,
)

data class CharterChoiceUi(val id: String, val name: String, val description: String, val unlocked: Boolean, val requirement: String)

data class CharterTierUi(val branch: String, val tier: Int, val choices: List<CharterChoiceUi>)

data class HeirloomUi(val id: String, val name: String, val maker: String, val image: String?, val icon: String, val value: Money)

data class CrownUi(
    val legacyGain: Long, val newLegacy: Long, val multiplier: Double, val heirloomsAllowed: Int,
    val traits: List<Triple<String, String, String>>, val heirlooms: List<HeirloomUi>, val charter: List<CharterTierUi>,
    val challenges: List<Triple<String, String, String>>, val generation: Int,
)

/** Maps the meta and Dynasty state to the Dynasty tab (docs/GDD.md §12). */
class DynastyMapper(private val engine: GameEngine) {
    private val p = engine.content.progression
    private val prog = engine.progression

    private val metricNames = mapOf(
        "levels" to "Buy %d business levels", "taps" to "Tap the counter %d times", "earn_hours" to "Earn %d hours of income",
        "cards" to "Decide %d matters on the Desk", "trades" to "Make %d market trades", "frenzies" to "Start %d Frenzies",
        "managers" to "Hire %d managers", "expansions" to "Finish %d expansions", "properties" to "Buy %d properties",
        "luxury" to "Buy %d luxury pieces", "renovations" to "Finish %d renovations", "businesses" to "Open %d new businesses",
        "auctions" to "Win %d auctions", "returns" to "Self-file %d tax returns", "payouts" to "Receive %d market payouts",
        "loans_repaid" to "Repay %d loans", "events" to "Live through %d world events",
    )

    private fun describe(m: MissionState) = (metricNames[m.metric] ?: "%d").replace("%d", "%,d").let { String.format(java.util.Locale.US, it, m.target) }.let {
        if (m.target == 1L) it.replace(" 1 ", " a ").replace("Frenzies", "Frenzy").replace("managers", "manager").replace("expansions", "expansion")
            .replace("properties", "property").replace("pieces", "piece").replace("renovations", "renovation").replace("businesses", "business")
            .replace("auctions", "auction").replace("returns", "return").replace("payouts", "payout").replace("loans", "loan").replace("events", "event")
            .replace("matters", "matter").replace("trades", "trade").replace("levels", "level").replace("hours", "hour")
            .let { t -> Regex(" a ([aeiouAEIOU])").replace(t) { r -> " an ${r.groupValues[1]}" } }
        else it
    }

    private fun mission(s: GameState, i: Int, m: MissionState, title: String) =
        MissionUi(i, title, describe(m) + progressText(s, m), prog.progress(s, m).toFloat(), prog.done(s, m), m.claimed, m.rewardCash, m.bullion, m.seasonXp)

    /** " · 12 of 60" while a counted goal is under way, so the bar has a number. */
    private fun progressText(s: GameState, m: MissionState): String {
        if (m.target <= 1 || prog.done(s, m)) return ""
        val have = (prog.progress(s, m) * m.target).toLong().coerceIn(0, m.target)
        return if (have > 0) String.format(java.util.Locale.US, " · %,d of %,d", have, m.target) else ""
    }

    fun daily(s: GameState) = s.meta.daily.mapIndexed { i, m -> mission(s, i, m, p.dailyById[m.template]?.title ?: "Daily") }
    fun weekly(s: GameState) = s.meta.weekly.mapIndexed { i, m -> mission(s, i, m, p.weeklyById[m.template]?.title ?: "Weekly") }

    fun ladder(s: GameState): LadderUi {
        val step = prog.nextLadderStep(s)
        return LadderUi(prog.ladderAvailable(s), step?.day ?: 1, step?.cashHours ?: 0.0, step?.bullion ?: 0, step?.favor ?: 0, s.meta.ladderClaimed)
    }

    fun season(s: GameState): SeasonUi {
        val d = engine.dynasty
        val idx = d.season(s.simTimeMs)
        val name = p.seasons.getOrNull((idx % p.seasons.size.coerceAtLeast(1)).toInt())?.name ?: "Season"
        val ends = (idx + 1) * p.config.seasonDays * 86_400_000L - s.simTimeMs
        fun reward(r: com.arnavgames.tycoon.core.content.SeasonRewardDef) = buildList {
            if (r.cashHours > 0) add("${"%.1f".format(r.cashHours)} h income")
            if (r.bullion > 0) add("${r.bullion} Bullion")
            if (r.favor > 0) add("${r.favor} Favor")
            r.cosmetic?.let { add("Title") }
        }.joinToString(" + ")
        val tiers = p.seasonTrack.map { t -> SeasonTierUi(t.tier, t.xp, s.meta.seasonXp >= t.xp, t.tier in s.meta.seasonClaimedFree, reward(t.free), reward(t.premium)) }
        val ev = d.weeklyEvent(s)
        val goals = s.meta.eventGoals.mapIndexed { i, g -> mission(s, i, g, ev?.name ?: "Event") }
        return SeasonUi(name, s.meta.seasonXp, p.seasonTrack.firstOrNull { it.xp > s.meta.seasonXp }?.xp, ends, tiers, ev?.name, ev?.description, goals)
    }

    fun achievements(s: GameState) = p.achievements.map { a ->
        AchievementUi(a.id, a.category, a.title, a.description, a.bullion, when (a.id) { in s.meta.achievements -> 2; in s.meta.achievementsClaimable -> 1; else -> 0 })
    }

    fun dynasty(s: GameState): DynastyUi {
        val dy = s.dynasty
        val d = engine.dynasty
        val ipoCfg = p.config.ipo
        return DynastyUi(
            dy.generation, dy.legacy, d.legacyMultiplier(dy.legacy), dy.trait?.let { p.traitById[it]?.name }, dy.trait?.let { p.traitById[it]?.description },
            dy.challenge?.let { p.challengeById[it]?.name }, d.canPrestige(s), d.legacyGain(s),
            s.holdingName != null && s.progress.chapter >= ipoCfg.minChapter && dy.ipo == null, dy.ipo?.float, ipoCfg.floats,
            engine.ipoValuation(s), s.meta.achievementsClaimable.size, s.meta.achievements.size, p.achievements.size,
        )
    }

    fun crown(s: GameState, imageOf: (String) -> String?): CrownUi {
        val pv = engine.crownPreview(s)
        val unlocked = pv.charter.toSet()
        val gen = s.dynasty.generation + 1
        val charter = p.charter.groupBy { it.branchName to it.tier }.map { (k, nodes) ->
            CharterTierUi(k.first, k.second, nodes.map { n ->
                CharterChoiceUi(n.id, n.name, n.description, n.id in unlocked,
                    if (n.id in unlocked) "" else "Needs ${"%,d".format(n.legacy)} Legacy" + if (gen < n.generation) " and generation ${n.generation}" else "")
            })
        }
        val lux = engine.content.luxury
        val heirlooms = s.luxury.owned.keys.mapNotNull { id -> lux.itemById[id] }.sortedByDescending { it.price }.map { item ->
            HeirloomUi(item.id, item.name, item.maker, item.image ?: imageOf(item.id), lux.categoryById.getValue(item.category).icon, engine.luxury.ownedValue(s, item.id))
        }
        return CrownUi(
            pv.legacyGain, pv.newLegacy, pv.multiplier, pv.heirlooms,
            pv.traits.mapNotNull { id -> p.traitById[id]?.let { Triple(it.id, it.name, it.description) } }, heirlooms, charter,
            p.challenges.map { Triple(it.id, it.name, "${it.description} (+${(it.legacyBonus * 100).toInt()}% Legacy)") }, gen,
        )
    }

    companion object {
        fun ipoRaise(valuation: Money, float: Double): Money = valuation * Multiplier.ofDouble(float)
    }
}
