package com.arnavgames.tycoon.core.content

import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.core.money.Multiplier
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Content JSON writes money as readable dollar strings ("4.00", "1250000"), converted exactly to cents. */
object DollarsSerializer : KSerializer<Money> {
    override val descriptor = PrimitiveSerialDescriptor("Dollars", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Money) {
        val c = value.cents
        val s = c.abs().toString().padStart(3, '0')
        encoder.encodeString((if (c.signum() < 0) "-" else "") + s.dropLast(2) + "." + s.takeLast(2))
    }
    override fun deserialize(decoder: Decoder): Money = MoneyFormat.parseDollars(decoder.decodeString())
}

typealias Dollars = @Serializable(with = DollarsSerializer::class) Money

/** Everything the rules engine needs, loaded once and immutable. */
@Serializable
data class ContentPack(
    val businesses: List<BusinessDef>,
    val balance: BalanceConfig,
    val chapters: List<ChapterDef> = emptyList(),
    val conglomerates: List<ConglomerateDef> = emptyList(),
) {
    @kotlinx.serialization.Transient
    val businessById: Map<String, BusinessDef> = businesses.associateBy { it.id }

    fun business(id: String): BusinessDef = businessById[id] ?: error("unknown business '$id'")
}

@Serializable
data class BusinessDef(
    val id: String,
    val name: String,
    val tagline: String,
    /** Name of the business before its first Scale Tier, e.g. "Corner Kiosk". */
    val starterName: String,
    val chapter: Int,
    val sector: String,
    val icon: String,
    /** Cost to open this business (uses a slot). */
    val unlockCost: Dollars,
    /** Cost of level 1 → 2 before growth. */
    val baseCost: Dollars,
    /** Cost growth per level, e.g. "1.085". */
    val costGrowth: Multiplier,
    /** Gross revenue per second per level at efficiency 1.0. */
    val baseRevenue: Dollars,
    /** Upkeep as a fraction of gross revenue, e.g. "0.12". */
    val upkeep: Multiplier,
    /** Unmanaged businesses hold at most this many seconds of output until collected. */
    val storageSeconds: Int,
    val tiers: List<TierDef>,
    val managers: List<ManagerDef>,
    val expansions: List<ExpansionDef>,
    val mechanic: MechanicDef,
)

@Serializable
data class TierDef(
    val level: Int,
    val multiplier: Multiplier,
    val name: String,
    val description: String,
)

@Serializable
data class ManagerDef(
    val id: String,
    val name: String,
    val title: String,
    val stars: Int,
    val cost: Dollars,
    val policy: ManagerPolicy,
    val trait: String,
    /** Portrait asset id (filled by the art pipeline in Phase 8; null = illustrated placeholder). */
    val portrait: String? = null,
)

@Serializable
enum class ManagerPolicy { Safe, Balanced, Aggressive }

@Serializable
data class ExpansionDef(
    val id: String,
    val name: String,
    val description: String,
    val requiresLevel: Int,
    val cost: Dollars,
    val durationSeconds: Long,
    /** Permanent revenue multiplier for this business once complete. */
    val multiplier: Multiplier,
)

/** Per-business mechanic parameters. Each subtype is handled by its own module in core.mechanics. */
@Serializable
sealed interface MechanicDef {
    /** Plain production: used by tests and as a fallback. */
    @Serializable @SerialName("simple")
    data object Simple : MechanicDef

    /** Retail: price slider along a demand curve; footfall by format. */
    @Serializable @SerialName("retail")
    data class Retail(
        /** Price elasticity of demand (> 1 means volume falls faster than price rises). */
        val elasticity: Double,
        /** Allowed price range as a percentage of the reference price. */
        val minPricePct: Int,
        val maxPricePct: Int,
        /** Seconds a "promotion" boost lasts and its footfall multiplier. */
        val promoSeconds: Long,
        val promoFootfall: Double,
        val promoCostSeconds: Long,
    ) : MechanicDef

    /** Restaurants: reputation stars drift toward a quality target; stars scale covers and prices. */
    @Serializable @SerialName("restaurant")
    data class Restaurant(
        val startStars: Double,
        /** Stars gained or lost per hour while moving toward the target. */
        val driftPerHour: Double,
        /** Quality tiers: each has a target star rating and an upkeep multiplier. */
        val qualityTiers: List<QualityTier>,
        /** Efficiency = base + perStar * stars. */
        val efficiencyBase: Double,
        val efficiencyPerStar: Double,
    ) : MechanicDef

    /** Logistics: assign vehicles to routes; throughput is min(capacity, demand) per route. */
    @Serializable @SerialName("logistics")
    data class Logistics(
        val routes: List<RouteDef>,
        /** Vehicles granted per business level. */
        val vehiclesPerLevel: Double,
        /** Capacity units each vehicle moves. */
        val vehicleCapacity: Double,
    ) : MechanicDef

    /**
     * A business mechanic implemented by a module in core.engine.mechanics (farmland, factory, fashion, …).
     * [kind] selects the module; [params] are its tunable numbers (each module documents its keys and defaults).
     */
    @Serializable @SerialName("module")
    data class Module(
        val kind: String,
        val params: Map<String, Double> = emptyMap(),
    ) : MechanicDef {
        fun p(key: String, default: Double): Double = params[key] ?: default
    }
}

/** Merger tree node: businesses that combine into a conglomerate for a shared synergy (GDD §5.3). */
@Serializable
data class ConglomerateDef(
    val id: String,
    val name: String,
    val description: String,
    val members: List<String>,
    /** Every member must have reached at least this Scale Tier (1 = first tier). */
    val requiredTier: Int,
    /** The merger tree opens in this story chapter. */
    val requiredChapter: Int = 4,
    /** Fee to form, paid from cash. */
    val fee: Dollars,
    /** Income multiplier applied to every member. */
    val incomeMultiplier: Multiplier,
    /** Upkeep multiplier applied to every member (e.g. inputs at cost). */
    val upkeepMultiplier: Multiplier = Multiplier.ONE,
    /** Optional extra requirement: number of other businesses owned (e.g. a property portfolio later). */
    val synergyText: String,
)

@Serializable
data class QualityTier(val id: String, val name: String, val targetStars: Double, val upkeepMultiplier: Double)

@Serializable
data class RouteDef(
    val id: String,
    val name: String,
    /** Demand in capacity units at business level 1; grows with sqrt(level). */
    val baseDemand: Double,
    /** Revenue weight per unit moved on this route. */
    val margin: Double,
    val unlockLevel: Int,
)

@Serializable
data class BalanceConfig(
    val startingCash: Dollars,
    val baseSlots: Int,
    val offlineCapBaseSeconds: Long,
    val offlineCapMaxSeconds: Long,
    val hustle: HustleConfig,
    val hqLevels: List<HqLevelDef>,
    val managerSkillFloor: Double,
    val managerSkillPerStar: Double,
    /** Cost of rushing a timer: Favor per remaining minute. */
    val favorPerRushMinute: Double,
    /** Merging three conglomerates into a holding company: fee and income multiplier for every business. */
    val holdingFee: Dollars,
    val holdingIncomeMultiplier: Multiplier,
    /** Managers may spend at most this share of cash per minute on their business's upkeep decisions… */
    val managerBudgetCashShare: Double,
    /** …and never more than this many seconds of the business's base income. */
    val managerBudgetIncomeSeconds: Double,
)

@Serializable
data class HustleConfig(
    /** Cash per tap at HQ level 1 before income scaling. */
    val basePerTap: Dollars,
    /** Each tap also pays this many seconds of the current managed income (keeps tapping relevant later). */
    val incomeSecondsPerTap: Double,
    /** Combo: taps within this window build the combo meter. */
    val comboWindowMs: Long,
    val maxCombo: Int,
    /** Extra multiplier per combo step. */
    val comboStep: Double,
    val frenzyChargesPerDay: Int,
    val frenzySeconds: Long,
    val frenzyMultiplier: Double,
)

@Serializable
data class HqLevelDef(
    val level: Int,
    val cost: Dollars,
    val extraSlots: Int,
    val offlineCapBonusSeconds: Long,
    val hustleMultiplier: Multiplier,
    val name: String,
)

@Serializable
data class ChapterDef(
    val number: Int,
    val title: String,
    val summary: String,
    val missions: List<MissionDef>,
    val unlocksBusinesses: List<String>,
)

@Serializable
data class MissionDef(
    val id: String,
    val title: String,
    val description: String,
    val goal: GoalDef,
    val rewardCash: Dollars = Money.ZERO,
    val rewardBullion: Long = 0,
    val rewardFavor: Long = 0,
)

/** Mission goals the engine can evaluate from state. */
@Serializable
sealed interface GoalDef {
    @Serializable @SerialName("ownBusiness") data class OwnBusiness(val businessId: String) : GoalDef
    @Serializable @SerialName("businessLevel") data class BusinessLevel(val businessId: String, val level: Int) : GoalDef
    @Serializable @SerialName("hireManager") data class HireManager(val businessId: String) : GoalDef
    @Serializable @SerialName("netWorth") data class NetWorth(val amount: Dollars) : GoalDef
    @Serializable @SerialName("incomePerSecond") data class IncomePerSecond(val amount: Dollars) : GoalDef
    @Serializable @SerialName("hustleTaps") data class HustleTaps(val count: Long) : GoalDef
    @Serializable @SerialName("expansionDone") data class ExpansionDone(val businessId: String, val expansionId: String) : GoalDef
    @Serializable @SerialName("hqLevel") data class HqLevel(val level: Int) : GoalDef
    @Serializable @SerialName("conglomerates") data class Conglomerates(val count: Int) : GoalDef
    @Serializable @SerialName("businessesOwned") data class BusinessesOwned(val count: Int) : GoalDef
}
