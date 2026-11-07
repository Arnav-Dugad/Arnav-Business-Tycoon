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
    val FILES = listOf("data/businesses.json", "data/chapters.json", "data/conglomerates.json", "data/markets.json", "data/estates.json", "data/luxury.json", "data/world.json", "data/progression.json", "data/store.json", "data/mentor.json", "balance/balance.json")

    data class Loaded(val pack: ContentPack, val hash: String, val issues: List<ContentIssue>)

    fun load(read: (String) -> String): Loaded {
        val texts = FILES.associateWith(read)
        val businesses = json.decodeFromString<List<BusinessDef>>(texts.getValue("data/businesses.json"))
        val chapters = json.decodeFromString<List<ChapterDef>>(texts.getValue("data/chapters.json"))
        val conglomerates = json.decodeFromString<List<ConglomerateDef>>(texts.getValue("data/conglomerates.json"))
        val markets = json.decodeFromString<MarketContent>(texts.getValue("data/markets.json"))
        val estates = json.decodeFromString<EstateContent>(texts.getValue("data/estates.json"))
        val luxury = json.decodeFromString<LuxuryContent>(texts.getValue("data/luxury.json"))
        val world = json.decodeFromString<WorldContent>(texts.getValue("data/world.json"))
        val progression = json.decodeFromString<ProgressionContent>(texts.getValue("data/progression.json"))
        val store = json.decodeFromString<StoreContent>(texts.getValue("data/store.json"))
        val mentor = json.decodeFromString<MentorContent>(texts.getValue("data/mentor.json"))
        val balance = json.decodeFromString<BalanceConfig>(texts.getValue("balance/balance.json"))
        val pack = ContentPack(businesses, balance, chapters, conglomerates, markets, estates, luxury, world, progression, store, mentor)
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
        validateWorld(pack, ids, ::check)
        validateProgression(pack, ::check)
        validateStore(pack, ::check)
        validateMentor(pack, ::check)
        return out
    }

    private fun validateMentor(pack: ContentPack, check: (Boolean, String, String) -> Unit) {
        val m = pack.mentor
        check(m.lines.size >= 200, "mentor", "at least 200 lines (CONTENT.md)")
        check(m.lines.map { it.id }.toSet().size == m.lines.size, "mentor", "duplicate line ids")
        val missions = pack.chapters.flatMap { c -> c.missions.map { it.id } }.toSet()
        val chapters = pack.chapters.map { it.number.toString() }.toSet()
        m.lines.forEach { l ->
            val path = "mentor/${l.id}"
            check(ID.matches(l.id), path, "bad id")
            check(l.text.isNotBlank() && l.text.length <= 180, path, "text must be 1–180 characters")
            val (kind, arg) = l.trigger.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            check(when (kind) {
                "chapter", "done", "tip" -> arg in chapters
                "mission" -> arg in missions
                "open" -> arg in pack.businessById
                "event" -> arg in MentorContent.EVENTS
                else -> false
            }, path, "unknown trigger '${l.trigger}'")
        }
        pack.chapters.forEach { c -> check(m.byTrigger["tip:${c.number}"].orEmpty().size >= 5, "mentor", "chapter ${c.number} needs at least 5 tips") }
        missions.forEach { id -> check("mission:$id" in m.byTrigger, "mentor", "no line for mission $id") }
    }

    private fun validateStore(pack: ContentPack, check: (Boolean, String, String) -> Unit) {
        val st = pack.store
        if (st.products.isEmpty()) return
        check(st.products.size >= 20, "store", "at least 20 products")
        check(st.products.map { it.id }.toSet().size == st.products.size, "store", "duplicate product ids")
        val sections = st.sections.map { it.id }.toSet()
        st.products.forEach { p ->
            val path = "store.${p.id}"
            check(p.section in sections, path, "unknown section")
            check(p.demoPrice != null || p.bullionPrice != null, path, "needs a demo or Bullion price")
            check(p.demoPrice == null || Regex("^\\$\\d+\\.\\d\\d$").matches(p.demoPrice), path, "demo price label")
            p.grants.cosmetic?.let { check(it in st.cosmeticById, path, "unknown cosmetic") }
            // No paid random rewards (GDD §15): every grant is fixed.
            check(listOf(p.grants.bullion > 0, p.grants.favor > 0, p.grants.boostHours > 0, p.grants.permanentPct > 0, p.grants.slots > 0,
                p.grants.offlineHours > 0, p.grants.vipDays > 0, p.grants.seasonPass > 0, p.grants.cosmetic != null).any { it }, path, "grants nothing")
        }
    }

    private val METRICS = setOf(
        "levels", "taps", "earn_hours", "cards", "trades", "frenzies", "managers", "expansions", "properties", "luxury",
        "renovations", "businesses", "auctions", "returns", "payouts", "loans_repaid", "events", "towers", "phoenix", "sold",
        "dailies", "weeklies", "ladder",
    )
    private val BONUSES = setOf(
        "incomeAll", "offlineHours", "hustle", "slots", "startCash", "dividends", "rent", "taxCut", "creditScore",
        "luxuryDiscount", "status", "takeoverShield", "legacyBonus",
    )

    private fun validateProgression(pack: ContentPack, check: (Boolean, String, String) -> Unit) {
        val p = pack.progression
        if (p.daily.isEmpty()) return
        val sectors = pack.markets.sectors.map { it.id }.toSet()
        fun bonusOk(e: String) = e in BONUSES || (e.startsWith("incomeSector:") && e.removePrefix("incomeSector:") in sectors) ||
            (e.startsWith("sector:") && e.removePrefix("sector:") in sectors)
        check(p.daily.size >= 50 && p.weekly.size >= 25, "progression", "mission template counts")
        check(p.achievements.size >= 140, "progression", "at least 140 achievements")
        check(p.charter.size >= 54 && p.traits.size >= 18 && p.challenges.size >= 10, "progression", "dynasty content counts")
        check(p.ladder.size == 7, "progression", "7 ladder steps")
        check((p.daily + p.weekly).all { it.metric in METRICS && it.targets.size == 3 && it.targets.all { t -> t > 0 } }, "progression", "mission metric/targets")
        check(p.achievements.map { it.id }.toSet().size == p.achievements.size, "progression", "duplicate achievement ids")
        p.achievements.filter { it.goal.type == "metric" }.forEach { check(it.goal.metric in METRICS, "progression.${it.id}", "unknown metric") }
        p.charter.forEach { check(bonusOk(it.effect), "progression.charter.${it.id}", "unknown effect '${it.effect}'") }
        p.traits.forEach { check(bonusOk(it.effect), "progression.traits.${it.id}", "unknown effect '${it.effect}'") }
        p.weeklyEvents.forEach { e -> check(bonusOk(e.effect) && e.goals.all { it in METRICS }, "progression.events.${e.id}", "effect/goals") }
        check(p.charter.groupBy { it.branch to it.tier }.values.all { it.size in 2..3 }, "progression.charter", "2-3 choices per tier")
    }

    /**
     * Exact names of the real people the parody rivals are based on: a rival may pun on them but never use them
     * (GDD §1.1). Lower case, compared after trimming.
     */
    /** Real product model names (and makers) that luxury items must never use exactly: owner rule, GDD §1.1. */
    val REAL_MODEL_BLOCKLIST = setOf(
        "300 sl gullwing", "33 stradale", "356 speedster", "505", "507 roadster", "525 relentless",
        "750s", "911 carrera s", "918 spyder", "a6gcs '54", "ach160", "acj twotwenty",
        "amg one", "artura", "aw139 vip", "aw169", "bbj 787", "bel air '57",
        "bentayga", "blower '29", "carrera glassbox", "challenger 3500", "chiron sport", "citation cj4 gen2",
        "citation longitude", "classique tourbillon", "code 11.95", "continental gt", "corvette z06", "countach '74",
        "crash londres", "cullinan", "db12", "db5 '64", "dino 246 gts", "e-type '61",
        "elan '66", "elite ii", "elva", "emira", "evija", "f-type r",
        "f1 '94", "falcon 10x", "falcon 2000lxs", "g 63", "g280", "g700",
        "g800", "galactica 50m", "gemera", "ghost", "giulia gtam", "global 6500",
        "global 8000", "granturismo", "gt '05", "gt40 '66", "h145", "huayra r",
        "i7 executive", "jesko absolut", "m5 touring", "masseto", "mc20", "model s plaid",
        "mustang dark horse", "pc-24", "phantom drophead", "phenom 300e", "praetor 600", "r66",
        "rm 11", "rm 27-04", "roadster next", "roma gt", "s-76d", "silver ghost '09",
        "sl 63", "tank francaise", "taycan turbo", "type 35 '27", "vantage", "vega sicilia",
        "veyron grand", "xj220", "zonda cinque",
    )

    val REAL_PEOPLE_BLOCKLIST = setOf(
        "elon musk", "jeff bezos", "larry ellison", "mark zuckerberg", "bernard arnault", "larry page", "sergey brin",
        "steve ballmer", "warren buffett", "jensen huang", "mukesh ambani", "bill gates", "michael dell", "rob walton",
        "amancio ortega", "francoise bettencourt meyers", "françoise bettencourt meyers", "alice walton", "gautam adani",
        "carlos slim", "zhong shanshan", "ma huateng", "pony ma", "ken griffin", "phil knight", "oprah winfrey",
        "rupert murdoch", "richard branson", "jack ma", "masayoshi son", "li ka-shing", "lakshmi mittal", "ratan tata",
        "azim premji", "shiv nadar", "savitri jindal", "cyrus poonawalla", "dilip shanghvi", "kumar birla", "uday kotak",
        "radhakishan damani", "mark cuban", "steve cohen", "ray dalio", "mackenzie scott", "jack dorsey", "brian chesky",
        "travis kalanick", "reed hastings", "daniel ek", "dieter schwarz", "klaus-michael kühne", "klaus-michael kuhne",
        "gianluigi aponte", "giovanni ferrero", "alain wertheimer", "françois pinault", "francois pinault",
        "emmanuel besnier", "stefan persson", "susanne klatten", "tadashi yanai", "zhang yiming", "colin huang",
        "robin zeng", "lei jun", "he xiangjian", "jacqueline mars", "abigail johnson", "thomas peterffy",
        "stephen schwarzman", "len blavatnik", "dan gilbert", "aliko dangote", "johann rupert", "nassef sawiris",
        "strive masiyiwa", "iris fontbona", "jorge paulo lemann", "germán larrea", "german larrea", "gina rinehart",
        "andrew forrest", "anthony pratt", "lee jae-yong", "robert kuok", "hiroshi mikitani", "david geffen",
        "harold hamm", "kelcy warren", "jerry jones", "stan kroenke", "diane hendricks",
    )

    private fun validateWorld(pack: ContentPack, businessIds: List<String>, check: (Boolean, String, String) -> Unit) {
        val w = pack.world
        if (w.rivals.isEmpty()) return
        check(w.rivals.size >= 200, "world", "at least 200 rivals")
        check(w.cards.size >= 150, "world", "at least 150 decision cards")
        check(w.events.size >= 50, "world", "at least 50 world events")
        check(w.archetypes.size == 8, "world", "8 rival archetypes")
        check(w.rivals.map { it.id }.toSet().size == w.rivals.size, "world", "duplicate rival ids")
        check(w.rivals.map { it.name }.toSet().size == w.rivals.size, "world", "duplicate rival names")
        check(w.cards.map { it.id }.toSet().size == w.cards.size, "world", "duplicate card ids")
        check(w.events.map { it.id }.toSet().size == w.events.size, "world", "duplicate event ids")
        val sectors = pack.markets.sectors.map { it.id }.toSet()
        val cities = pack.estates.cities.map { it.id }.toSet()
        w.rivals.forEach { r ->
            val p = "world.rivals.${r.id}"
            check(ID.matches(r.id), p, "bad id")
            check(r.archetype in w.archetypeById, p, "unknown archetype '${r.archetype}'")
            check(r.sectors.isNotEmpty() && r.sectors.all { it in sectors }, p, "unknown sector")
            check(r.name.trim().lowercase() !in REAL_PEOPLE_BLOCKLIST, p, "uses a real person's exact name")
            findBlockedBrand(r.name)?.let { check(false, p, "contains blocked real brand '$it'") }
            check(r.base in 8.5..13.0 && r.growth in 0.2..1.5 && r.wobble in 0.0..0.6 && r.periodDays > 0.5, p, "net-worth curve out of range")
        }
        w.nemeses.forEach { n ->
            check(n.rival in w.rivalById, "world.nemeses.${n.chapter}", "unknown rival")
            check(n.beats.size == 3, "world.nemeses.${n.chapter}", "three beats")
        }
        val types = setOf("cash", "income", "credit", "relation", "donation", "rd", "sell")
        w.cards.forEach { c ->
            val p = "world.cards.${c.id}"
            check(c.kind in setOf("business", "world", "personal", "acquisition", "jv", "takeover", "rival", "nemesis"), p, "unknown kind")
            check(c.requires == "none" || c.requires == "biz:any" || c.requires.removePrefix("biz:") in businessIds, p, "bad requirement '${c.requires}'")
            check(c.options.size in 1..5, p, "1-5 options")
            val needsBusiness = c.requires.startsWith("biz:")
            check(!c.text.contains("{business}") || needsBusiness, p, "{business} without a business requirement")
            c.options.forEach { o ->
                check(kotlin.math.abs(o.outcomes.sumOf { it.p } - 1.0) < 1e-9, p, "outcome odds must sum to 1 ('${o.label}')")
                check(o.requires == null || o.requires == "ally", p, "unknown option requirement")
                o.outcomes.flatMap { it.effects }.forEach { e ->
                    check(e.type in types, p, "unknown effect '${e.type}'")
                    if (e.type == "income") check(e.target in setOf("business", "all", "sector") && e.hours in 1.0..96.0 && e.pct in -0.6..0.6, p, "income effect range")
                    if (e.type == "income" && e.target != "all") check(needsBusiness, p, "business income effect without a business")
                    if (e.type == "sell") check(needsBusiness && e.multiple in 1.0..4.0, p, "sell effect")
                    if (e.type == "cash" || e.type == "donation" || e.type == "rd") check(e.hours in -6.0..6.0, p, "cash hours range")
                }
            }
        }
        w.events.forEach { e ->
            val p = "world.events.${e.id}"
            check(e.scope in setOf("sector", "all", "city"), p, "unknown scope")
            if (e.scope == "sector") check(e.target in sectors, p, "unknown sector '${e.target}'")
            if (e.scope == "city") check(e.target in cities, p, "unknown city '${e.target}'")
            if (e.scope != "city") check(e.hours in 1..96 && e.pct in -0.5..0.5 && e.pct != 0.0, p, "event effect range")
        }
        val f = w.config.finance
        check(f.ratings.isNotEmpty() && f.ratings.map { it.min } == f.ratings.map { it.min }.sortedDescending(), "world.finance", "ratings must be ordered best first")
        check(f.taxBrackets.isNotEmpty() && f.taxBrackets.last().upToPerHour == null, "world.finance", "last tax bracket must be open-ended")
        check(f.rdBusinesses.all { it in businessIds } && f.greenBusinesses.all { it in businessIds }, "world.finance", "unknown deduction business")
    }

    private fun validateLuxury(pack: ContentPack, businessIds: List<String>, check: (Boolean, String, String) -> Unit) {
        val l = pack.luxury
        if (l.items.isEmpty()) return
        check(l.items.map { it.id }.toSet().size == l.items.size, "luxury", "duplicate item ids")
        check(l.items.size >= 246, "luxury", "at least 246 luxury items")
        l.items.forEach { i ->
            check(i.name.trim().lowercase() !in REAL_MODEL_BLOCKLIST && i.maker.trim().lowercase() !in REAL_MODEL_BLOCKLIST, "luxury/${i.id}", "uses a real model or maker name exactly")
        }
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
