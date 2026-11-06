package com.arnavgames.tycoon.core.content

import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.Multiplier
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Loads content JSON from a file source (assets on Android, the repo's /content dir on the JVM),
 * computes a content hash (stored in saves) and validates it.
 */
object ContentLoader {
    val json = Json {
        ignoreUnknownKeys = false // typos in content must fail loudly
        classDiscriminator = "type"
        prettyPrint = false
    }

    /** Files that make up the pack, relative to the content root. Order matters for the hash. */
    val FILES = listOf("data/businesses.json", "data/chapters.json", "data/conglomerates.json", "balance/balance.json")

    data class Loaded(val pack: ContentPack, val hash: String, val issues: List<ContentIssue>)

    fun load(read: (String) -> String): Loaded {
        val texts = FILES.associateWith(read)
        val businesses = json.decodeFromString<List<BusinessDef>>(texts.getValue("data/businesses.json"))
        val chapters = json.decodeFromString<List<ChapterDef>>(texts.getValue("data/chapters.json"))
        val conglomerates = json.decodeFromString<List<ConglomerateDef>>(texts.getValue("data/conglomerates.json"))
        val balance = json.decodeFromString<BalanceConfig>(texts.getValue("balance/balance.json"))
        val pack = ContentPack(businesses, balance, chapters, conglomerates)
        val digest = MessageDigest.getInstance("SHA-256")
        FILES.forEach { digest.update(it.toByteArray()); digest.update(texts.getValue(it).replace("\r\n", "\n").toByteArray()) }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }.take(16)
        return Loaded(pack, hash, ContentValidator.validate(pack))
    }

    fun loadFromDirectory(dir: File): Loaded = load { File(dir, it).readText() }

    /** JVM helper: the repo content dir, passed by Gradle as the `abt.contentDir` system property. */
    fun loadFromRepo(): Loaded {
        val dir = System.getProperty("abt.contentDir") ?: error("abt.contentDir system property not set")
        return loadFromDirectory(File(dir))
    }
}

data class ContentIssue(val path: String, val message: String) {
    override fun toString() = "$path: $message"
}

/** Structural and balance-sanity validation of all content (docs/TEST_PLAN.md §3). */
object ContentValidator {
    private val ID = Regex("^[a-z][a-z0-9_]{1,40}$")
    private val ICONS = setOf(
        "Empire", "Markets", "Estates", "Collection", "Crown", "Cash", "Bullion", "Coin", "Favor", "Briefcase", "Store",
        "Factory", "Map", "Expand", "Timer", "Manager", "Chart", "News", "Shield", "Rivals", "Trophy", "Star", "Bolt",
        "Chef", "Fleet", "Route", "Tag", "Megaphone", "Wheat", "Hanger", "Pickaxe", "Flame", "Bed", "Bank", "Crane", "Film",
        "Chip", "Antenna", "Plane", "Flask", "Ball", "Rocket", "Buildings",
    )

    /** Fragments of real brand/company names that must never appear in content names. Extended as content grows. */
    val BRAND_BLOCKLIST = listOf(
        "walmart", "starbucks", "mcdonalds", "amazon", "fedex", "dhl", "tesla", "apple", "google", "microsoft",
        "nike", "adidas", "gucci", "prada", "rolex", "ferrari", "lamborghini", "porsche", "boeing", "airbus", "exxon",
        "pfizer", "spacex", "disney", "netflix", "ikea", "zara", "costco", "maersk", "goldman sachs",
        "jpmorgan", "hilton", "marriott", "emirates", "nestle", "coca-cola", "pepsi", "samsung", "sony", "louis vuitton",
    )

    private val brandRegexes by lazy { BRAND_BLOCKLIST.map { it to Regex("\\b" + Regex.escape(it) + "\\b") } }

    /** Returns the first blocked brand found as a whole word in [text], or null. */
    fun findBlockedBrand(text: String): String? = brandRegexes.firstOrNull { it.second.containsMatchIn(text.lowercase()) }?.first

    fun validate(pack: ContentPack): List<ContentIssue> {
        val out = mutableListOf<ContentIssue>()
        fun check(cond: Boolean, path: String, msg: String) { if (!cond) out += ContentIssue(path, msg) }

        val ids = pack.businesses.map { it.id }
        check(ids.size == ids.toSet().size, "businesses", "duplicate business ids")
        pack.businesses.forEach { b ->
            val p = "businesses.${b.id}"
            check(ID.matches(b.id), p, "bad id format")
            check(b.name.isNotBlank() && b.tagline.isNotBlank() && b.starterName.isNotBlank(), p, "name/tagline/starterName required")
            check(b.icon in ICONS, p, "unknown icon '${b.icon}'")
            check(b.chapter in 1..8, p, "chapter out of range")
            check(b.unlockCost >= Money.ZERO && b.baseCost > Money.ZERO && b.baseRevenue > Money.ZERO, p, "costs/revenue must be positive")
            check(b.costGrowth > Multiplier.of("1.01") && b.costGrowth < Multiplier.of("1.30"), p, "costGrowth outside (1.01, 1.30)")
            check(b.upkeep >= Multiplier.ZERO && b.upkeep < Multiplier.of("0.6"), p, "upkeep outside [0, 0.6)")
            check(b.storageSeconds in 5..3600, p, "storageSeconds outside 5..3600")
            check(b.tiers.map { it.level } == b.tiers.map { it.level }.sorted().distinct(), p, "tier levels must be strictly increasing")
            b.tiers.forEach { t -> check(t.multiplier > Multiplier.ONE, "$p.tiers.${t.level}", "tier multiplier must be > 1") }
            check(b.managers.isNotEmpty(), p, "at least one manager required")
            check(b.managers.map { it.id }.toSet().size == b.managers.size, p, "duplicate manager ids")
            b.managers.forEach { m ->
                check(m.stars in 1..5, "$p.managers.${m.id}", "stars 1..5")
                check(m.cost > Money.ZERO, "$p.managers.${m.id}", "cost must be positive")
            }
            check(b.expansions.map { it.id }.toSet().size == b.expansions.size, p, "duplicate expansion ids")
            b.expansions.forEach { e ->
                check(e.durationSeconds in 10..86_400, "$p.expansions.${e.id}", "duration 10s..24h")
                check(e.multiplier > Multiplier.ONE, "$p.expansions.${e.id}", "multiplier must be > 1")
            }
            val names = b.name + " " + b.tagline + " " + b.managers.joinToString(" ") { it.name } + " " + b.expansions.joinToString(" ") { it.name }
            findBlockedBrand(names)?.let { brand -> check(false, p, "contains blocked real brand '$brand'") }
            when (val m = b.mechanic) {
                is MechanicDef.Retail -> {
                    check(m.elasticity in 1.05..3.0, p, "retail elasticity 1.05..3.0")
                    check(m.minPricePct in 40..100 && m.maxPricePct in 100..250, p, "retail price range")
                }
                is MechanicDef.Restaurant -> {
                    check(m.startStars in 1.0..5.0, p, "startStars 1..5")
                    check(m.qualityTiers.isNotEmpty() && m.qualityTiers.all { it.targetStars in 1.0..5.0 }, p, "quality tiers")
                }
                is MechanicDef.Logistics -> {
                    check(m.routes.isNotEmpty(), p, "logistics needs routes")
                    check(m.routes.map { it.id }.toSet().size == m.routes.size, p, "duplicate route ids")
                    check(m.routes.any { it.unlockLevel <= 1 }, p, "at least one route at level 1")
                }
                is MechanicDef.Module -> {
                    val module = com.arnavgames.tycoon.core.engine.mechanics.MechanicRegistry.modules[m.kind]
                    check(module != null, p, "unknown mechanic module '${m.kind}'")
                    if (module != null) m.params.keys.filterNot { it in module.paramKeys }.forEach { check(false, p, "unknown param '$it' for ${m.kind}") }
                }
                MechanicDef.Simple -> Unit
            }
        }
        // Module kinds are used by exactly one business each (cross-business synergies refer to business ids).
        val kinds = pack.businesses.mapNotNull { (it.mechanic as? MechanicDef.Module)?.kind }
        check(kinds.size == kinds.toSet().size, "businesses", "a module kind is used twice")
        // Merger tree
        val congIds = pack.conglomerates.map { it.id }
        check(congIds.size == congIds.toSet().size, "conglomerates", "duplicate conglomerate ids")
        pack.conglomerates.forEach { c ->
            val p = "conglomerates.${c.id}"
            check(c.members.size >= 2 && c.members.toSet().size == c.members.size, p, "needs two or more distinct members")
            c.members.forEach { id -> check(id in ids, p, "unknown member '$id'") }
            check(c.requiredTier in 1..7, p, "requiredTier 1..7")
            check(c.incomeMultiplier > Multiplier.ONE, p, "income multiplier must be > 1")
            check(c.upkeepMultiplier <= Multiplier.ONE, p, "upkeep multiplier must be <= 1")
            findBlockedBrand(c.name)?.let { check(false, p, "contains blocked real brand '$it'") }
        }
        // Balance
        val bal = pack.balance
        check(bal.baseSlots in 1..10, "balance", "baseSlots 1..10")
        check(bal.offlineCapBaseSeconds in 600..bal.offlineCapMaxSeconds, "balance", "offline cap base")
        check(bal.offlineCapMaxSeconds <= 20 * 3600, "balance", "offline cap max must be <= 20h")
        check(bal.hqLevels.map { it.level } == (1..bal.hqLevels.size).toList(), "balance.hqLevels", "HQ levels must be 1..N")
        // Chapters
        val missionIds = pack.chapters.flatMap { c -> c.missions.map { it.id } }
        check(missionIds.size == missionIds.toSet().size, "chapters", "duplicate mission ids")
        pack.chapters.forEach { c ->
            c.unlocksBusinesses.forEach { id -> check(id in ids, "chapters.${c.number}", "unknown business '$id'") }
            c.missions.forEach { mi ->
                val ref = when (val g = mi.goal) {
                    is GoalDef.OwnBusiness -> g.businessId
                    is GoalDef.BusinessLevel -> g.businessId
                    is GoalDef.HireManager -> g.businessId
                    is GoalDef.ExpansionDone -> g.businessId.also { bid ->
                        check(pack.businessById[bid]?.expansions?.any { it.id == g.expansionId } == true, "missions.${mi.id}", "unknown expansion")
                    }
                    else -> null
                }
                if (ref != null) check(ref in ids, "missions.${mi.id}", "unknown business '$ref'")
            }
        }
        return out
    }
}
