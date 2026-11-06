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
    val FILES = listOf("data/businesses.json", "data/chapters.json", "data/conglomerates.json", "data/markets.json", "data/estates.json", "data/luxury.json", "balance/balance.json")

    data class Loaded(val pack: ContentPack, val hash: String, val issues: List<ContentIssue>)

    fun load(read: (String) -> String): Loaded {
        val texts = FILES.associateWith(read)
        val businesses = json.decodeFromString<List<BusinessDef>>(texts.getValue("data/businesses.json"))
        val chapters = json.decodeFromString<List<ChapterDef>>(texts.getValue("data/chapters.json"))
        val conglomerates = json.decodeFromString<List<ConglomerateDef>>(texts.getValue("data/conglomerates.json"))
        val markets = json.decodeFromString<MarketContent>(texts.getValue("data/markets.json"))
        val estates = json.decodeFromString<EstateContent>(texts.getValue("data/estates.json"))
        val luxury = json.decodeFromString<LuxuryContent>(texts.getValue("data/luxury.json"))
        val balance = json.decodeFromString<BalanceConfig>(texts.getValue("balance/balance.json"))
        val pack = ContentPack(businesses, balance, chapters, conglomerates, markets, estates, luxury)
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
        "Chip", "Antenna", "Plane", "Flask", "Ball", "Rocket", "Buildings", "House", "Villa", "Warehouse", "Plot", "Tower",
        "Car", "Watch", "Wine", "Gem", "Heli", "Yacht", "Frame", "Bust", "Island", "Mansion",
    )
    private val TICKER = Regex("^[A-Z][A-Z0-9]{2,4}$")

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
        validateMarkets(pack, ids, ::check)
        validateEstates(pack, ids, ::check)
        validateLuxury(pack, ids, ::check)
        return out
    }

    private fun validateLuxury(pack: ContentPack, businessIds: List<String>, check: (Boolean, String, String) -> Unit) {
        val l = pack.luxury
        if (l.items.isEmpty()) return
        check(l.items.map { it.id }.toSet().size == l.items.size, "luxury", "duplicate item ids")
        check(l.items.size >= 246, "luxury", "at least 246 luxury items")
        check(l.sets.size >= 25, "luxury", "at least 25 collection sets")
        l.categories.forEach { c ->
            check(c.icon in ICONS, "luxury.${c.id}", "unknown icon '${c.icon}'")
            check(c.showroom in l.showroomById, "luxury.${c.id}", "unknown showroom")
        }
        l.showrooms.forEach { s -> check(s.icon in ICONS && s.baseCapacity > 0, "luxury.${s.id}", "icon/capacity") }
        l.items.forEach { i ->
            val p = "luxury.${i.id}"
            check(ID.matches(i.id), p, "bad id format")
            check(i.category in l.categoryById, p, "unknown category")
            check(i.price.isPositive && i.rarity in setOf("common", "rare", "legendary"), p, "price/rarity")
            if (i.category !in setOf("painting", "sculpture", "island", "mansion")) findBlockedBrand(i.maker + " " + i.name)?.let { check(false, p, "exact real brand '$it' (use a parody)") }
        }
        l.sets.forEach { s ->
            val p = "luxury.sets.${s.id}"
            check(s.items.size >= 3 && s.items.all { it in l.itemById }, p, "at least 3 known items")
            check(s.bonusType in setOf("status", "income", "dividends", "rent", "luxuryDiscount"), p, "unknown bonus type")
            if (s.bonusType == "income") check(s.bonusTarget in businessIds, p, "unknown business '${s.bonusTarget}'")
        }
        check(l.tiers.map { it.points } == l.tiers.map { it.points }.sorted(), "luxury.tiers", "tier thresholds must increase")
        check(l.bidders.size >= 6 && l.bidders.all { it.style in setOf("aggressive", "sniper", "steady", "collector") }, "luxury.bidders", "bidders/styles")
    }

    private fun validateEstates(pack: ContentPack, businessIds: List<String>, check: (Boolean, String, String) -> Unit) {
        val e = pack.estates
        if (e.districts.isEmpty()) return
        check(e.cities.map { it.id }.toSet().size == e.cities.size, "estates", "duplicate city ids")
        check(e.districts.map { it.id }.toSet().size == e.districts.size, "estates", "duplicate district ids")
        check(e.cities.size >= 6 && e.districts.size >= 36 && e.types.size >= 10, "estates", "at least 6 cities, 36 districts, 10 property types")
        e.cities.forEach { c ->
            check(c.chapter in 1..8 && c.priceScale.toBigDecimal().signum() > 0, "estates.${c.id}", "chapter/scale")
            findBlockedBrand(c.name)?.let { check(false, "estates.${c.id}", "contains blocked real brand '$it'") }
        }
        e.districts.forEach { d ->
            val p = "estates.${d.id}"
            check(ID.matches(d.id), p, "bad id format")
            check(d.city in e.cityById, p, "unknown city")
            check(d.types.isNotEmpty() && d.types.all { it in e.typeById }, p, "unknown property type")
            check(d.yieldPerDay in 0.01..0.05 && d.amplitude in 0.0..0.5 && d.periodDays in 1.0..10.0 && d.tier > 0, p, "cycle/yield out of range")
        }
        check(e.districts.any { it.cbd }, "estates", "at least one central business district")
        e.types.forEach { t ->
            check(t.icon in ICONS, "estates.${t.id}", "unknown icon '${t.icon}'")
            t.hosts.forEach { b -> check(b in businessIds, "estates.${t.id}", "unknown business '$b'") }
            check(t.renovationCost in 0.0..0.5 && t.renovationHours in 1..24 && t.saleHours in 1..24, "estates.${t.id}", "renovation/sale range")
        }
        e.towers.forEach { t ->
            check(t.stageCosts.size == 4 && t.stageHours.size == 4 && t.stageNames.size == 4, "estates.${t.id}", "towers have four stages")
            check(t.floors in 10..150 && t.landmarkBoost in 0.0..0.2, "estates.${t.id}", "floors/boost")
        }
        check(e.events.size >= 30, "estates", "at least 30 tenant events")
        e.events.forEach { ev -> check(ev.choices.size in 2..3, "estates.events.${ev.id}", "2–3 choices") }
        check(e.config.mortgageLtvOptions.all { it in 0.0..0.8 } && e.config.foreclosureLtv in 0.8..1.0, "estates.config", "LTV range")
    }

    private fun validateMarkets(pack: ContentPack, businessIds: List<String>, check: (Boolean, String, String) -> Unit) {
        val m = pack.markets
        if (m.stocks.isEmpty()) return // markets are optional for tests that build tiny packs
        val ids = m.instruments.map { it.id }
        check(ids.size == ids.toSet().size, "markets", "duplicate instrument ids")
        val tickers = m.instruments.map { it.ticker }
        check(tickers.size == tickers.toSet().size, "markets", "duplicate tickers")
        m.instruments.forEach { ins ->
            val p = "markets.${ins.id}"
            check(ID.matches(ins.id), p, "bad id format")
            check(TICKER.matches(ins.ticker), p, "ticker must be 3-5 capitals or digits, starting with a letter")
            findBlockedBrand(ins.name)?.let { check(false, p, "contains blocked real brand '$it'") }
        }
        val sectorIds = m.sectors.map { it.id }.toSet()
        m.sectors.forEach { s ->
            check(s.icon in ICONS, "markets.sectors.${s.id}", "unknown icon '${s.icon}'")
            s.businesses.forEach { b -> check(b in businessIds, "markets.sectors.${s.id}", "unknown business '$b'") }
        }
        val bands = setOf("defensive", "core", "growth", "speculative")
        m.stocks.forEach { s ->
            val p = "markets.${s.id}"
            check(s.sector in sectorIds, p, "unknown sector '${s.sector}'")
            check(s.band in bands, p, "unknown band '${s.band}'")
            check(s.volPerDay in 0.005..0.12 && s.driftPerDay in -0.01..0.05, p, "drift/vol out of range")
            check(s.dividendPerDay in 0.0..0.005 && s.beta in 0.0..3.0 && s.baseCapBillions > 0.0, p, "dividend/beta/cap out of range")
            check(s.startPrice.toDouble() > 0.0, p, "startPrice must be positive")
        }
        check(m.stocks.size >= 100, "markets", "at least 100 stocks")
        m.funds.forEach { f -> check(f.style in setOf("total", "growth", "defensive", "global"), "markets.${f.id}", "unknown fund style") }
        m.bonds.forEach { b -> check(b.duration in 0.5..30.0 && b.defaultChance in 0.0..0.5, "markets.${b.id}", "bond duration/default out of range") }
        m.coins.forEach { c ->
            val p = "markets.${c.id}"
            check(c.tier in 1..4, p, "tier 1..4")
            check(c.crashDepthMin <= c.crashDepthMax && c.crashDepthMax < 1.0, p, "crash depth")
            check(c.risk.isNotBlank() && c.startPrice.toDouble() > 0.0, p, "risk text and price required")
        }
        m.commodities.forEach { c ->
            val p = "markets.${c.id}"
            check(c.anchorPrice.toDouble() > 0.0 && c.reversionPerDay > 0.0, p, "anchor/reversion")
            c.effects.forEach { e ->
                check(e.business in businessIds, p, "unknown business '${e.business}'")
                check(e.mode in setOf("revenue", "upkeep") && e.elasticity in 0.0..0.5, p, "effect mode/elasticity")
            }
        }
        val kinds = setOf("company", "sector", "hint", "commodity", "crypto")
        m.news.forEach { n ->
            check(n.kind in kinds, "markets.news.${n.id}", "unknown news kind")
            check(n.min <= n.max && n.weight > 0, "markets.news.${n.id}", "min <= max, weight > 0")
            n.only.forEach { id -> check(id in m.commodityById, "markets.news.${n.id}", "unknown commodity '$id'") }
        }
        val macroIds = m.macro.map { it.id }.toSet()
        m.macro.forEach { ph ->
            check(ph.minDays in 0.5..ph.maxDays, "markets.macro.${ph.id}", "phase length")
            ph.next.keys.forEach { n -> check(n in macroIds, "markets.macro.${ph.id}", "unknown next phase '$n'") }
        }
        val c = m.config
        check(c.depthScaleByChapter.size >= pack.chapters.size.coerceAtLeast(1), "markets.config", "depth scale needed for every chapter")
        check(c.shortMaintenanceMargin > 1.0 && c.shortInitialMargin > c.shortMaintenanceMargin, "markets.config", "short margins")
        check(c.maxStake in 0.05..0.5 && c.boardSeatStake < c.divisionStake && c.divisionStake <= c.maxStake, "markets.config", "stake thresholds")
        check(c.capitalGainsTax in 0.0..0.5 && c.feeRate in 0.0..0.02, "markets.config", "tax/fee range")
    }
}
