package com.arnavgames.tycoon.ui.collection

import com.arnavgames.tycoon.core.engine.GameEngine
import com.arnavgames.tycoon.core.luxury.LuxuryEngine
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.state.GameState

data class AuctionUi(
    val itemId: String, val name: String, val maker: String, val image: String?, val icon: String, val bid: Money, val nextBid: Money,
    val leader: String, val playerLeads: Boolean, val closesInMs: Long, val log: List<String>, val canBid: Boolean, val bidders: Int,
    val estimate: Money,
    val playerMax: Money,
)

data class ShowroomUi(val id: String, val name: String, val icon: String, val displayed: Int, val owned: Int, val capacity: Int, val upgradeCost: Money, val previews: List<Pair<String?, String>>)

data class CategoryUi(val id: String, val name: String, val icon: String, val unlocked: Boolean, val chapter: Int, val owned: Int, val total: Int)

data class SetUi(val id: String, val name: String, val description: String, val have: Int, val total: Int, val complete: Boolean)

data class CollectionUi(
    val unlocked: Boolean, val unlockChapter: Int, val status: Double, val tier: String, val nextTier: String?, val nextTierPoints: Int?,
    val tierProgress: Float, val discount: Double, val value: Money, val auction: AuctionUi?, val nextAuctionInMs: Long?, val auctionsUnlocked: Boolean,
    val auctionsChapter: Int, val showrooms: List<ShowroomUi>, val categories: List<CategoryUi>, val sets: List<SetUi>, val cash: Money,
)

data class ItemRowUi(val id: String, val name: String, val maker: String, val image: String?, val icon: String, val price: Money, val status: Double, val owned: Boolean, val rarity: String)

data class ItemUi(
    val id: String, val name: String, val maker: String, val category: String, val image: String?, val icon: String, val description: String,
    val location: String, val rarity: String, val price: Money, val buyCost: Money, val status: Double, val owned: Boolean, val value: Money?,
    val paid: Money?, val sellQuote: Money?, val atAuction: Boolean, val unlocked: Boolean, val lockedReason: String?, val sets: List<String>,
    val credit: String?, val upkeepPerDay: Money?, val cash: Money,
)

/** Maps engine state to the Collection UI. */
class CollectionMapper(private val engine: GameEngine, private val hasAsset: (String) -> Boolean = { false }) {
    /** A piece's picture: its museum photo, else its painting (if bundled), else null (the icon tile). */
    private fun img(i: com.arnavgames.tycoon.core.content.LuxuryItemDef): String? = i.image ?: "img/luxury/${i.id}.webp".takeIf(hasAsset)

    private val lux = engine.luxury
    private val c = engine.content.luxury

    private fun iconOf(itemCategory: String) = c.categoryById.getValue(itemCategory).icon

    fun auction(s: GameState): AuctionUi? {
        val a = s.luxury.auction ?: return null
        val item = c.itemById.getValue(a.item)
        val next = lux.nextBidIncrement(a, item)
        return AuctionUi(
            a.item, item.name, item.maker, img(item), iconOf(item.category), a.bid, next,
            when (a.leader) { "" -> "No bids yet"; LuxuryEngine.PLAYER -> "You"; else -> c.bidderById[a.leader]?.name ?: a.leader },
            a.leader == LuxuryEngine.PLAYER, (a.closeSim - s.simTimeMs).coerceAtLeast(0), a.log.reversed(),
            lux.isUnlocked(s, "auctions") && a.leader != LuxuryEngine.PLAYER && s.cash >= next, a.bidders.count { !it.out },
            lux.marketPrice(item, s.simTimeMs), a.playerMax,
        )
    }

    fun toUi(s: GameState): CollectionUi {
        val status = lux.statusPoints(s)
        val ti = lux.tierIndex(s)
        val next = c.tiers.getOrNull(ti + 1)
        val cur = c.tiers.getOrNull(ti)
        val displayed = lux.displayed(s)
        return CollectionUi(
            unlocked = lux.isUnlocked(s, "collection"), unlockChapter = lux.unlockChapter("collection"),
            status = status, tier = cur?.name ?: "Newcomer", nextTier = next?.name, nextTierPoints = next?.points,
            tierProgress = if (next == null || cur == null) 1f else ((status - cur.points) / (next.points - cur.points)).toFloat().coerceIn(0f, 1f),
            discount = lux.dealerDiscount(s), value = lux.collectionValue(s), auction = auction(s),
            nextAuctionInMs = if (s.luxury.auction == null && lux.isUnlocked(s, "auctions")) lux.nextAuctionAt(s) - s.simTimeMs else null,
            auctionsUnlocked = lux.isUnlocked(s, "auctions"), auctionsChapter = lux.unlockChapter("auctions"),
            showrooms = c.showrooms.map { room ->
                val ownedHere = s.luxury.owned.keys.mapNotNull { c.itemById[it] }.filter { c.categoryById.getValue(it.category).showroom == room.id }
                ShowroomUi(room.id, room.name, room.icon, ownedHere.count { it.id in displayed }, ownedHere.size, lux.capacity(s, room.id), lux.upgradeCost(s, room.id),
                    // Only what's on display: stored pieces earn no Status, so they're called out separately.
                    ownedHere.filter { it.id in displayed }.sortedByDescending { it.statusPoints }.take(4).map { img(it) to iconOf(it.category) })
            },
            categories = c.categories.map { cat ->
                CategoryUi(cat.id, cat.name, cat.icon, lux.categoryUnlocked(s, cat.id), cat.chapter,
                    s.luxury.owned.keys.count { c.itemById[it]?.category == cat.id }, c.items.count { it.category == cat.id })
            },
            sets = c.sets.map { set -> SetUi(set.id, set.name, set.description, set.items.count { it in s.luxury.owned }, set.items.size, set.items.all { it in s.luxury.owned }) }
                .sortedWith(compareByDescending<SetUi> { it.complete }.thenByDescending { it.have.toDouble() / it.total }),
            cash = s.cash,
        )
    }

    fun category(s: GameState, id: String): List<ItemRowUi> = c.items.filter { it.category == id }.sortedBy { it.price }.map { i ->
        ItemRowUi(i.id, i.name, i.maker, img(i), iconOf(i.category), lux.dealerPrice(s, i), i.statusPoints, i.id in s.luxury.owned, i.rarity)
    }

    fun categoryName(id: String) = c.categoryById[id]?.name ?: id

    private val credits: Map<String, String> by lazy { emptyMap() }

    fun item(s: GameState, id: String, credit: String?): ItemUi? {
        val i = c.itemById[id] ?: return null
        val cat = c.categoryById.getValue(i.category)
        val owned = s.luxury.owned[id]
        val value = owned?.let { lux.ownedValue(s, id) }
        return ItemUi(
            id = id, name = i.name, maker = i.maker, category = cat.name, image = img(i), icon = cat.icon, description = i.description,
            location = i.location, rarity = i.rarity, price = lux.dealerPrice(s, i), buyCost = lux.buyCost(s, i), status = i.statusPoints,
            owned = owned != null, value = value, paid = owned?.paid, sellQuote = owned?.let { lux.sellQuote(s, id) },
            atAuction = s.luxury.auction?.item == id, unlocked = lux.categoryUnlocked(s, i.category),
            lockedReason = if (lux.categoryUnlocked(s, i.category)) null else "${cat.name} open in chapter ${cat.chapter}",
            sets = c.sets.filter { id in it.items }.map { "${it.name}: ${it.description}" },
            credit = credit, upkeepPerDay = if (cat.upkeepPerDay > 0) (value ?: lux.marketPrice(i, s.simTimeMs)) * com.arnavgames.tycoon.core.money.Multiplier.ofDouble(cat.upkeepPerDay) else null,
            cash = s.cash,
        )
    }
}
