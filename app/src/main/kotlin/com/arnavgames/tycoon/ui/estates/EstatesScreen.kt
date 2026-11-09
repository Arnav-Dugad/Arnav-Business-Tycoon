package com.arnavgames.tycoon.ui.estates

import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arnavgames.tycoon.designsystem.components.AurumChip
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arnavgames.tycoon.core.engine.Command
import com.arnavgames.tycoon.core.money.Money
import com.arnavgames.tycoon.core.money.MoneyFormat
import com.arnavgames.tycoon.designsystem.components.AbtIcon
import com.arnavgames.tycoon.designsystem.components.AurumCard
import com.arnavgames.tycoon.designsystem.components.BottomBarReserve
import com.arnavgames.tycoon.designsystem.components.HairlineDivider
import com.arnavgames.tycoon.designsystem.components.IconTile
import com.arnavgames.tycoon.designsystem.components.PillAction
import com.arnavgames.tycoon.designsystem.components.PillStyle
import com.arnavgames.tycoon.designsystem.components.SectionHeader
import com.arnavgames.tycoon.designsystem.icons.AbtIcons
import com.arnavgames.tycoon.designsystem.theme.Aurum
import com.arnavgames.tycoon.designsystem.theme.AurumShapes
import com.arnavgames.tycoon.designsystem.theme.GlassLayer
import com.arnavgames.tycoon.designsystem.theme.GlassWeight
import com.arnavgames.tycoon.designsystem.theme.Space
import com.arnavgames.tycoon.designsystem.theme.glass
import com.arnavgames.tycoon.designsystem.theme.glassSource
import com.arnavgames.tycoon.ui.GameViewModel
import com.arnavgames.tycoon.ui.locked.ComingSoonScreen

/** The Estates tab (docs/GDD.md §7): portfolio header, tenant decisions, the map of cities and your properties. */
@Composable
fun EstatesScreen(vm: GameViewModel, onOpenCity: (String) -> Unit, onOpenProperty: (Long) -> Unit) {
    val ui by vm.estates.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notation = vm.notation(settings)
    val fmt: (Money) -> String = { MoneyFormat.compact(it, notation) }
    val e = ui ?: return
    if (!e.unlocked) {
        ComingSoonScreen(
            icon = AbtIcons.Estates, title = "Estates", chapter = e.unlockChapter,
            lines = listOf("22 real cities, from Lisbon to Riyadh to Tokyo, on a living market", "Rent, renovate and flip properties", "Build your own skyscraper"),
        )
        return
    }
    var headerPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val c = Aurum.colors
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.reselect.collect { if (it == "estates") listState.animateScrollToItem(0) } }
    var propSort by com.arnavgames.tycoon.ui.common.rememberPref("estates.sort", "attention")
    val props = when (propSort) {
        "value" -> e.properties.sortedByDescending { it.value }
        "rent" -> e.properties.sortedByDescending { it.rent6h }
        "name" -> e.properties.sortedBy { it.title }
        else -> e.properties.sortedByDescending { it.needsAttention }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = with(density) { headerPx.toDp() } + Space.md, bottom = BottomBarReserve + navInset + Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
            modifier = Modifier.fillMaxSize().glassSource(GlassLayer.Content),
        ) {
            item(key = "intro") { com.arnavgames.tycoon.ui.common.FirstVisitTip(com.arnavgames.tycoon.ui.common.Intro.Estates, AbtIcons.Estates) }
            if (e.decisions.isNotEmpty()) {
                item(key = "dec-h") { SectionHeader("Decisions") { Text("${e.decisions.size}", style = Aurum.type.label, color = c.textTertiary) } }
                e.decisions.forEach { d -> item(key = "dec-${d.id}") { DecisionCard(d, onChoose = { i -> vm.dispatch(Command.ResolveTenantEvent(d.id, i)) }, onOpen = { onOpenProperty(d.propertyId) }) } }
            }
            item(key = "map-h") { SectionHeader("The map") }
            item(key = "map") { EstateMap(e.cities, onOpenCity) }
            item(key = "cities") {
                AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xs)) {
                    e.cities.forEachIndexed { i, city ->
                        if (i > 0) HairlineDivider(Modifier.padding(start = 64.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenCity(city.id) }.heightIn(min = Space.touch + 8.dp)
                                .padding(horizontal = Space.md, vertical = Space.sm).semantics(mergeDescendants = true) {},
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            com.arnavgames.tycoon.ui.common.Illustration(com.arnavgames.tycoon.ui.common.Art.city(city.id), Modifier.size(56.dp, 40.dp).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.small).alpha(if (city.unlocked) 1f else 0.45f))
                            Spacer(Modifier.width(Space.md))
                            Column(Modifier.weight(1f)) {
                                Text(city.name, style = Aurum.type.titleSmall, color = if (city.unlocked) c.textPrimary else c.textTertiary)
                                Text(if (city.unlocked) "${city.districts} neighbourhoods" + (if (city.owned > 0) " · ${city.owned} owned" else "") else "Opens in chapter ${city.chapter}", style = Aurum.type.bodySmall, color = c.textTertiary)
                            }
                            AbtIcon(AbtIcons.ChevronRight, null, tint = c.textTertiary, size = 16.dp)
                        }
                    }
                }
            }
            item(key = "props-h") {
                SectionHeader("Your properties (${e.properties.size})") {
                    if (e.properties.size > 1) com.arnavgames.tycoon.ui.common.SortToggle(listOf(com.arnavgames.tycoon.ui.common.Choice("attention", "Needs you"), com.arnavgames.tycoon.ui.common.Choice("value", "Value"), com.arnavgames.tycoon.ui.common.Choice("rent", "Rent"), com.arnavgames.tycoon.ui.common.Choice("name", "A to Z")), propSort, { propSort = it })
                }
            }
            if (e.properties.isEmpty()) {
                item(key = "empty") {
                    AurumCard(Modifier.fillMaxWidth()) {
                        Text("No properties yet. Lisbon's listings refresh every six hours, and distressed ones are cheap to flip.", style = Aurum.type.body, color = c.textSecondary)
                    }
                }
            } else {
                item(key = "props") {
                    AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xs)) {
                        props.forEachIndexed { i, p ->
                            if (i > 0) HairlineDivider(Modifier.padding(start = 64.dp))
                            PropertyRow(p, fmt) { onOpenProperty(p.id) }
                        }
                    }
                }
            }
        }
        EstatesHeader(e, fmt, Modifier.align(Alignment.TopCenter).onSizeChanged { headerPx = it.height })
    }
}

@Composable
private fun EstatesHeader(e: EstatesUi, fmt: (Money) -> String, modifier: Modifier) {
    val c = Aurum.colors
    Column(modifier.fillMaxWidth().glass(RectangleShape, GlassWeight.Regular, edge = false)) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.gutter).padding(top = Space.md, bottom = Space.md)) {
            Text("PROPERTY", style = Aurum.type.overline, color = c.textTertiary)
            Text(fmt(e.value), style = Aurum.type.numberHero, color = c.textPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbtIcon(AbtIcons.Key, null, tint = c.goldText, size = 16.dp)
                Spacer(Modifier.width(Space.xs))
                Text("${fmt(e.rentPerDay)} rent a day", style = Aurum.type.bodySmall, color = c.textSecondary)
                Spacer(Modifier.weight(1f))
                if (e.debt.isPositive) Text("Debt ${fmt(e.debt)}", style = Aurum.type.bodySmall, color = c.warning)
                else Text("${fmt(e.cash)} cash", style = Aurum.type.bodySmall, color = c.textTertiary)
            }
        }
        HairlineDivider()
    }
}

@Composable
fun PropertyRow(p: PropertyRowUi, fmt: (Money) -> String, onClick: () -> Unit) {
    val c = Aurum.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = Space.touch + 12.dp).padding(horizontal = Space.md, vertical = Space.sm)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.arnavgames.tycoon.ui.common.Illustration(com.arnavgames.tycoon.ui.common.Art.pick(androidx.compose.ui.platform.LocalContext.current, com.arnavgames.tycoon.ui.common.Art.district(p.districtId), com.arnavgames.tycoon.ui.common.Art.property(p.typeId)), Modifier.size(56.dp, 40.dp).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.small))
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = Aurum.type.titleSmall, color = c.textPrimary, maxLines = 1)
            Text(p.subtitle, style = Aurum.type.bodySmall, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.status, style = Aurum.type.bodySmall, color = if (p.busy) c.goldText else c.textSecondary, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(fmt(p.value), style = Aurum.type.numberSmall, color = c.textPrimary)
            if (p.rent6h.isPositive) Text("${fmt(p.rent6h * 4L)}/day", style = Aurum.type.bodySmall, color = c.gain)
        }
    }
}

@Composable
fun DecisionCard(d: DecisionUi, onChoose: (Int) -> Unit, onOpen: (() -> Unit)? = null) {
    val c = Aurum.colors
    // Compact: a thumbnail beside the situation, a warning chip, and each choice as a button whose second line is its
    // consequence (so the options can be compared at a glance).
    AurumCard(Modifier.fillMaxWidth(), highlighted = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.md)) {
        Row(verticalAlignment = Alignment.Top) {
            com.arnavgames.tycoon.ui.common.ArtOr(com.arnavgames.tycoon.ui.common.Art.tenant(d.kind), Modifier.size(72.dp).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.small)) {
                Box(Modifier.size(72.dp).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.small).background(c.separator), contentAlignment = Alignment.Center) { AbtIcon(AbtIcons.Key, null, tint = c.goldText, size = 24.dp) }
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(d.text, style = Aurum.type.body, color = c.textPrimary, modifier = if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
                Spacer(Modifier.height(Space.xs))
                AurumChip("Rent −20% until you decide", icon = AbtIcons.Warning, tint = c.warning)
            }
        }
        Spacer(Modifier.height(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            d.choices.forEachIndexed { i, label ->
                val detail = d.details.getOrNull(i)
                Column(
                    Modifier.weight(1f).clip(com.arnavgames.tycoon.designsystem.theme.AurumShapes.medium)
                        .then(if (i == 0) Modifier.background(c.gold.copy(alpha = 0.22f)) else Modifier.background(c.separator))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { onChoose(i) }
                        .heightIn(min = Space.touch).padding(horizontal = Space.sm, vertical = Space.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, style = Aurum.type.titleSmall, color = if (i == 0) c.goldText else c.textPrimary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (detail != null) Text(detail, style = Aurum.type.label, color = c.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

/**
 * A stylised world map drawn in code: simplified coastlines (authored as low-detail longitude/latitude outlines)
 * projected over the window the cities use (longitude −130…160, latitude −45…65), with a pin per city.
 * Locked cities are dimmed and labelled with the chapter that opens them.
 */
@Composable
fun EstateMap(cities: List<CityPinUi>, onOpenCity: (String) -> Unit) {
    val c = Aurum.colors
    val water = if (c.isDark) listOf(Color(0xFF0E2233), Color(0xFF0A1622)) else listOf(Color(0xFFCFE3EE), Color(0xFFB7D3E2))
    val land = if (c.isDark) Color(0xFF2B3A2F) else Color(0xFFE9E1CC)
    val landEdge = if (c.isDark) Color(0xFF4A5E4D) else Color(0xFFC9B98F)
    val grid = if (c.isDark) Color(0x14FFFFFF) else Color(0x14000000)
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(2.3f).clip(AurumShapes.medium)) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "World map with ${cities.size} cities" }) {
            drawRect(Brush.verticalGradient(water))
            fun p(lon: Float, lat: Float) = Offset((lon - LON0) / (LON1 - LON0) * size.width, (LAT1 - lat) / (LAT1 - LAT0) * size.height)
            var lon = -120f
            while (lon <= 150f) { drawLine(grid, p(lon, LAT0), p(lon, LAT1), strokeWidth = 1f); lon += 30f }
            var lat = -30f
            while (lat <= 60f) { drawLine(grid, p(LON0, lat), p(LON1, lat), strokeWidth = 1f); lat += 30f }
            for (shape in WORLD) {
                val path = Path()
                shape.forEachIndexed { i, pt -> val o = p(pt.first, pt.second); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                path.close()
                drawPath(path, land)
                drawPath(path, landEdge, style = Stroke(width = 1.2.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            for (sea in SEAS) {
                val path = Path()
                sea.forEachIndexed { i, pt -> val o = p(pt.first, pt.second); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                path.close()
                drawPath(path, water.first())
                drawPath(path, landEdge, style = Stroke(width = 1.dp.toPx()))
            }
        }
        cities.forEach { city ->
            val pinColor = if (city.unlocked) c.gold else c.textTertiary
            val desc = city.name + if (city.unlocked) "" else ", opens in chapter ${city.chapter}"
            // The pin sits exactly on the city; its label is kept inside the map frame.
            Box(
                Modifier.offset(x = w * city.x - 7.dp, y = h * city.y - 7.dp).size(14.dp).clip(AurumShapes.pill)
                    .glass(AurumShapes.pill, GlassWeight.Thin, blur = false),
                contentAlignment = Alignment.Center,
            ) { Canvas(Modifier.size(10.dp)) { drawCircle(pinColor) } }
            // With 22 cities only open ones carry a label (the list below names them all); every pin is tappable.
            if (!city.unlocked) {
                Box(Modifier.offset(x = w * city.x - 16.dp, y = h * city.y - 16.dp).size(32.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open ${city.name}") { onOpenCity(city.id) }
                    .semantics { contentDescription = desc })
                return@forEach
            }
            val labelX = (w * city.x - 50.dp).coerceIn(4.dp, w - 104.dp)
            val labelY = (h * city.y + 9.dp).coerceAtMost(h - 26.dp)
            Box(
                Modifier.offset(x = labelX, y = labelY).width(100.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open ${city.name}") { onOpenCity(city.id) }
                    .semantics(mergeDescendants = true) { contentDescription = desc },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    city.name, style = Aurum.type.label, color = if (city.unlocked) c.textPrimary else c.textTertiary, maxLines = 1,
                    modifier = Modifier.glass(AurumShapes.pill, GlassWeight.Thin, blur = false).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

// The map window (owner request 2026-11-09: cities on every continent, Riyadh to Sydney to Los Angeles).
private const val LON0 = -130f
private const val LON1 = 160f
private const val LAT0 = -45f
private const val LAT1 = 65f

/** Inland seas drawn over the land (none at this scale). */
private val SEAS: List<List<Pair<Float, Float>>> = emptyList()

/** Low-detail coastlines (longitude, latitude), authored for this map (tools/content/data/cities_real.json). */
private val WORLD: List<List<Pair<Float, Float>>> = listOf(
    // North America
    listOf(-130.0f to 65.0f, -130.0f to 55.0f, -127.5f to 50.5f, -124.5f to 48.5f, -124.0f to 46.0f, -124.5f to 42.0f, -123.5f to 38.5f, -121.0f to 35.0f, -118.5f to 34.0f, -117.0f to 32.5f, -97.5f to 26.0f, -94.0f to 29.5f, -89.0f to 30.5f, -84.0f to 30.0f, -82.5f to 28.0f, -81.5f to 25.5f, -80.5f to 25.0f, -80.0f to 25.9f, -80.0f to 27.0f, -81.5f to 30.5f, -76.0f to 35.0f, -74.5f to 39.5f, -73.5f to 40.5f, -70.0f to 41.5f, -66.0f to 45.0f, -61.0f to 45.5f, -64.5f to 49.0f, -56.0f to 52.0f, -61.5f to 56.5f, -64.5f to 60.5f, -69.5f to 58.5f, -78.0f to 62.5f, -76.5f to 56.0f, -79.5f to 51.5f, -85.0f to 55.5f, -92.5f to 57.0f, -94.0f to 61.0f, -87.0f to 65.0f, -130.0f to 65.0f),
    // Mexico & Central America
    listOf(-117.0f to 32.5f, -115.5f to 29.5f, -112.5f to 26.0f, -110.0f to 23.0f, -112.5f to 29.0f, -109.5f to 25.5f, -105.5f to 20.5f, -101.5f to 17.5f, -96.5f to 15.5f, -92.0f to 14.5f, -87.5f to 13.0f, -85.5f to 11.0f, -83.5f to 8.5f, -79.5f to 9.0f, -77.5f to 8.0f, -79.5f to 9.5f, -83.5f to 10.5f, -83.5f to 15.0f, -88.5f to 16.0f, -87.0f to 21.5f, -90.5f to 21.0f, -94.0f to 18.0f, -97.5f to 22.0f, -97.5f to 26.0f, -117.0f to 32.5f),
    // South America
    listOf(-77.5f to 8.0f, -75.0f to 11.0f, -72.0f to 12.0f, -68.0f to 10.5f, -61.0f to 10.5f, -57.0f to 6.0f, -52.5f to 5.0f, -50.0f to 1.5f, -48.5f to -1.0f, -44.0f to -2.5f, -39.5f to -3.0f, -35.0f to -5.5f, -35.0f to -9.0f, -37.5f to -12.5f, -39.0f to -15.0f, -39.5f to -19.0f, -41.0f to -22.0f, -44.5f to -23.0f, -48.0f to -25.5f, -48.5f to -28.5f, -51.0f to -31.0f, -53.5f to -34.0f, -57.0f to -35.0f, -57.5f to -38.0f, -62.0f to -39.0f, -65.0f to -41.0f, -64.0f to -42.5f, -65.5f to -45.0f, -74.0f to -45.0f, -73.5f to -40.0f, -71.5f to -32.0f, -70.5f to -23.0f, -70.5f to -18.5f, -76.0f to -14.0f, -79.5f to -7.5f, -81.0f to -5.5f, -80.0f to -3.0f, -80.0f to 1.0f, -77.5f to 4.0f, -77.5f to 8.0f),
    // Cuba
    listOf(-84.9f to 21.9f, -84.0f to 22.7f, -82.5f to 23.1f, -81.0f to 23.1f, -79.5f to 22.4f, -78.0f to 22.3f, -77.0f to 21.6f, -75.7f to 21.1f, -74.2f to 20.3f, -74.5f to 19.9f, -77.7f to 19.9f, -77.2f to 20.6f, -78.5f to 21.5f, -80.5f to 21.8f, -82.0f to 22.1f, -84.0f to 21.9f, -84.9f to 21.9f),
    // Iberia
    listOf(-1.8f to 43.4f, 3.2f to 42.4f, 3.2f to 42.0f, 1.0f to 41.0f, -0.3f to 39.5f, -0.5f to 38.0f, -2.0f to 36.7f, -4.5f to 36.7f, -5.5f to 36.0f, -6.3f to 36.6f, -7.5f to 37.1f, -9.0f to 37.0f, -9.5f to 39.0f, -8.8f to 42.0f, -9.2f to 43.2f, -7.5f to 43.7f, -4.0f to 43.4f, -1.8f to 43.4f),
    // Western Europe
    listOf(-1.8f to 43.4f, -1.3f to 44.5f, -1.2f to 46.0f, -2.3f to 47.2f, -4.7f to 48.3f, -1.5f to 48.7f, -1.5f to 49.7f, 0.0f to 49.7f, 1.6f to 50.2f, 2.5f to 51.0f, 4.0f to 51.5f, 5.0f to 53.0f, 7.0f to 53.5f, 8.7f to 54.0f, 8.0f to 55.5f, 8.5f to 57.0f, 10.5f to 57.7f, 10.5f to 56.3f, 10.0f to 55.0f, 11.0f to 54.0f, 14.0f to 53.9f, 16.0f to 54.3f, 18.5f to 54.8f, 17.5f to 43.0f, 15.0f to 44.5f, 13.7f to 45.6f, 13.7f to 46.5f, 10.5f to 46.8f, 7.0f to 46.0f, 8.5f to 44.3f, 7.7f to 43.8f, 6.2f to 43.1f, 4.5f to 43.4f, 3.0f to 43.3f, 3.2f to 42.4f, -1.8f to 43.4f),
    // Italy
    listOf(8.5f to 44.3f, 9.5f to 44.1f, 10.2f to 43.9f, 10.5f to 43.0f, 11.0f to 42.5f, 12.5f to 41.5f, 14.0f to 41.0f, 15.5f to 40.0f, 15.7f to 38.3f, 16.1f to 38.0f, 16.6f to 38.9f, 17.2f to 39.0f, 16.5f to 40.0f, 18.5f to 40.2f, 18.0f to 40.7f, 16.0f to 41.5f, 14.0f to 42.5f, 13.5f to 43.6f, 12.3f to 44.5f, 12.4f to 45.4f, 13.7f to 45.6f, 13.7f to 46.5f, 10.5f to 46.8f, 7.0f to 46.0f, 8.5f to 44.3f),
    // Eastern Europe
    listOf(18.5f to 54.8f, 20.0f to 54.6f, 21.0f to 55.3f, 21.0f to 57.0f, 24.0f to 57.0f, 23.5f to 58.3f, 23.5f to 59.2f, 28.0f to 59.5f, 30.0f to 60.5f, 30.0f to 65.0f, 40.0f to 65.0f, 40.0f to 47.0f, 38.5f to 47.0f, 36.5f to 45.3f, 33.0f to 44.5f, 33.5f to 46.0f, 30.5f to 46.5f, 29.7f to 45.2f, 28.6f to 44.0f, 27.7f to 43.0f, 28.0f to 42.0f, 29.2f to 41.3f, 29.2f to 41.0f, 26.5f to 40.6f, 24.0f to 40.5f, 23.0f to 39.5f, 24.0f to 38.0f, 23.0f to 38.0f, 22.5f to 36.5f, 21.0f to 38.0f, 19.5f to 40.0f, 19.5f to 41.8f, 17.5f to 43.0f, 18.5f to 54.8f),
    // Scandinavia
    listOf(30.0f to 60.5f, 28.0f to 60.5f, 25.0f to 60.2f, 22.5f to 60.0f, 21.5f to 61.0f, 21.5f to 63.0f, 24.5f to 64.8f, 25.5f to 65.5f, 24.0f to 65.8f, 22.0f to 65.5f, 21.0f to 64.5f, 19.0f to 63.5f, 17.5f to 62.5f, 17.3f to 61.0f, 18.8f to 60.0f, 18.5f to 59.3f, 16.5f to 58.0f, 16.5f to 56.5f, 14.0f to 55.4f, 12.8f to 55.6f, 12.5f to 56.5f, 11.5f to 58.5f, 10.5f to 59.5f, 9.5f to 59.0f, 8.0f to 58.0f, 6.0f to 58.1f, 5.0f to 59.0f, 5.0f to 61.0f, 5.5f to 62.5f, 8.0f to 63.5f, 10.5f to 64.5f, 12.5f to 66.0f, 15.0f to 68.0f, 17.0f to 69.5f, 21.0f to 70.2f, 26.0f to 71.0f, 30.0f to 70.0f, 30.0f to 65.0f, 30.0f to 60.5f),
    // Great Britain
    listOf(-5.7f to 50.1f, -3.5f to 50.3f, -1.0f to 50.7f, 1.4f to 51.2f, 1.7f to 52.6f, 0.3f to 53.1f, -0.2f to 54.0f, -1.5f to 55.0f, -2.0f to 55.9f, -3.0f to 56.0f, -1.8f to 57.5f, -3.5f to 57.7f, -3.0f to 58.6f, -5.0f to 58.6f, -5.8f to 57.5f, -5.6f to 56.3f, -5.0f to 55.5f, -4.8f to 54.8f, -3.3f to 54.9f, -3.0f to 53.9f, -3.0f to 53.3f, -4.5f to 53.4f, -4.8f to 52.8f, -4.2f to 52.3f, -5.2f to 51.7f, -3.2f to 51.5f, -4.2f to 51.2f, -5.7f to 50.1f),
    // Ireland
    listOf(-6.0f to 52.2f, -6.2f to 53.3f, -6.0f to 54.0f, -5.5f to 54.5f, -6.0f to 55.2f, -7.3f to 55.4f, -8.3f to 55.2f, -8.6f to 54.3f, -10.0f to 54.2f, -9.9f to 53.4f, -9.5f to 52.6f, -10.4f to 52.0f, -9.8f to 51.5f, -8.0f to 51.7f, -6.4f to 52.2f, -6.0f to 52.2f),
    // Africa (North)
    listOf(32.5f to 30.0f, 32.5f to 31.2f, 30.0f to 31.5f, 25.0f to 31.8f, 23.0f to 32.6f, 20.0f to 32.0f, 19.5f to 30.5f, 15.5f to 31.5f, 11.0f to 33.0f, 11.0f to 37.0f, 8.0f to 37.0f, 3.0f to 36.8f, -1.0f to 35.5f, -5.5f to 35.9f, -6.5f to 34.0f, -9.5f to 32.0f, -10.0f to 29.5f, -13.0f to 27.5f, -16.0f to 24.0f, -17.0f to 21.0f, -16.5f to 19.0f, -16.5f to 16.0f, -17.5f to 14.7f, -16.7f to 12.5f, -15.0f to 11.0f, 43.3f to 11.5f, 42.5f to 13.0f, 41.0f to 15.0f, 39.0f to 17.5f, 37.0f to 22.0f, 35.5f to 24.0f, 34.0f to 26.5f, 33.0f to 28.5f, 32.5f to 30.0f),
    // Africa (South)
    listOf(-15.0f to 11.0f, -13.0f to 9.0f, -11.0f to 7.0f, -7.5f to 4.4f, -4.0f to 5.2f, 0.0f to 5.5f, 2.5f to 6.3f, 5.0f to 5.5f, 8.5f to 4.5f, 9.8f to 3.0f, 9.5f to 1.0f, 9.0f to -1.0f, 11.5f to -4.0f, 12.5f to -6.0f, 13.3f to -9.0f, 12.5f to -13.0f, 11.8f to -17.0f, 14.5f to -22.5f, 15.3f to -27.0f, 17.5f to -30.5f, 18.2f to -33.4f, 18.5f to -34.4f, 20.0f to -34.8f, 22.0f to -34.0f, 25.5f to -34.0f, 28.0f to -32.5f, 30.5f to -30.5f, 32.5f to -28.5f, 32.8f to -26.0f, 35.5f to -24.0f, 35.5f to -22.0f, 34.8f to -20.0f, 40.5f to -15.0f, 40.5f to -11.0f, 39.5f to -7.0f, 39.5f to -4.5f, 41.5f to -1.5f, 43.5f to 1.0f, 46.5f to 3.0f, 49.5f to 8.0f, 51.2f to 11.8f, 45.0f to 10.5f, 43.3f to 11.5f, -15.0f to 11.0f),
    // Madagascar
    listOf(49.3f to -12.0f, 50.2f to -15.0f, 50.4f to -16.0f, 49.6f to -17.5f, 49.4f to -18.5f, 48.5f to -21.0f, 47.6f to -23.8f, 47.0f to -25.0f, 45.2f to -25.6f, 44.0f to -24.9f, 43.6f to -23.5f, 43.3f to -22.0f, 43.8f to -21.0f, 44.4f to -19.5f, 44.0f to -17.2f, 44.5f to -16.2f, 46.3f to -15.7f, 47.8f to -14.3f, 48.8f to -12.5f, 49.3f to -12.0f),
    // Arabian Peninsula
    listOf(32.5f to 30.0f, 34.5f to 28.0f, 35.0f to 28.5f, 36.5f to 26.0f, 39.0f to 21.5f, 41.0f to 18.0f, 42.5f to 15.0f, 43.5f to 12.5f, 45.0f to 13.0f, 48.5f to 14.0f, 52.0f to 15.5f, 55.0f to 17.0f, 57.0f to 18.5f, 58.5f to 20.5f, 59.8f to 22.5f, 58.5f to 23.7f, 56.5f to 24.5f, 56.2f to 26.2f, 55.5f to 25.6f, 54.2f to 24.3f, 52.0f to 24.0f, 51.5f to 25.8f, 50.5f to 25.5f, 50.0f to 26.5f, 48.5f to 28.0f, 48.0f to 30.0f, 44.0f to 33.0f, 36.0f to 36.5f, 35.8f to 35.0f, 35.0f to 33.0f, 34.3f to 31.3f, 32.5f to 30.0f),
    // Asia (West)
    listOf(40.0f to 65.0f, 92.0f to 65.0f, 92.0f to 45.0f, 92.0f to 28.0f, 75.0f to 35.0f, 61.5f to 30.0f, 61.5f to 25.2f, 57.5f to 25.8f, 56.5f to 27.0f, 54.0f to 26.5f, 51.0f to 28.0f, 49.5f to 30.0f, 48.0f to 30.0f, 44.0f to 33.0f, 36.0f to 36.5f, 34.5f to 36.8f, 32.5f to 36.1f, 30.5f to 36.3f, 28.5f to 36.6f, 27.5f to 37.0f, 26.5f to 38.5f, 26.2f to 40.0f, 27.0f to 40.4f, 29.0f to 40.8f, 29.2f to 41.2f, 31.0f to 41.2f, 33.0f to 42.0f, 36.0f to 41.7f, 39.0f to 41.0f, 41.5f to 41.5f, 40.0f to 43.5f, 38.0f to 44.5f, 37.5f to 45.5f, 39.0f to 47.0f, 40.0f to 47.0f, 40.0f to 65.0f),
    // India
    listOf(61.5f to 25.2f, 66.5f to 25.5f, 68.5f to 23.5f, 70.0f to 22.5f, 72.6f to 21.0f, 72.5f to 19.0f, 73.5f to 16.0f, 75.0f to 12.5f, 76.5f to 9.0f, 77.5f to 8.0f, 78.2f to 9.0f, 79.5f to 10.5f, 80.0f to 13.0f, 80.5f to 15.5f, 82.5f to 17.0f, 85.0f to 19.5f, 87.0f to 21.5f, 88.5f to 22.0f, 90.5f to 22.5f, 92.0f to 21.0f, 92.0f to 28.0f, 75.0f to 35.0f, 61.5f to 30.0f, 61.5f to 25.2f),
    // Sri Lanka
    listOf(80.2f to 9.8f, 80.8f to 9.3f, 81.3f to 8.5f, 81.9f to 7.5f, 81.8f to 6.6f, 81.2f to 6.2f, 80.6f to 5.9f, 80.1f to 6.1f, 79.8f to 6.8f, 79.8f to 7.6f, 79.8f to 8.3f, 79.9f to 9.0f, 80.0f to 9.6f, 80.1f to 9.8f, 80.2f to 9.8f),
    // Southeast Asia
    listOf(92.0f to 21.0f, 94.0f to 18.0f, 94.5f to 16.0f, 97.5f to 16.5f, 98.5f to 13.0f, 98.5f to 8.0f, 100.3f to 6.0f, 101.0f to 3.0f, 103.5f to 1.0f, 104.3f to 1.2f, 103.5f to 4.0f, 102.2f to 6.2f, 100.5f to 7.5f, 99.5f to 10.0f, 99.2f to 12.0f, 100.0f to 13.5f, 101.0f to 12.5f, 103.0f to 11.0f, 104.5f to 10.5f, 105.0f to 8.6f, 106.5f to 9.5f, 107.5f to 10.5f, 109.0f to 11.5f, 109.3f to 13.0f, 108.5f to 15.5f, 106.5f to 17.5f, 105.7f to 19.0f, 107.0f to 21.0f, 108.0f to 21.6f, 108.0f to 25.0f, 92.0f to 28.0f, 92.0f to 21.0f),
    // Sumatra
    listOf(95.3f to 5.6f, 97.5f to 5.2f, 98.7f to 3.8f, 100.4f to 2.2f, 101.4f to 1.7f, 103.0f to 0.5f, 103.8f to -1.0f, 104.5f to -1.8f, 105.8f to -3.0f, 106.0f to -5.8f, 104.6f to -5.9f, 103.4f to -4.8f, 102.0f to -3.6f, 100.9f to -2.0f, 100.4f to -0.8f, 99.2f to 0.6f, 98.6f to 1.8f, 97.6f to 2.9f, 96.4f to 4.2f, 95.3f to 5.6f),
    // Borneo
    listOf(109.6f to 1.9f, 110.5f to 1.7f, 111.4f to 2.4f, 113.0f to 3.2f, 114.0f to 4.5f, 115.4f to 5.0f, 116.1f to 6.1f, 117.3f to 6.9f, 118.5f to 5.6f, 119.2f to 5.2f, 118.3f to 4.4f, 117.7f to 3.0f, 118.9f to 1.1f, 117.5f to 0.1f, 116.5f to -1.8f, 116.4f to -3.5f, 114.6f to -4.1f, 113.0f to -3.1f, 111.5f to -3.0f, 110.2f to -2.9f, 110.0f to -1.3f, 109.0f to 0.0f, 109.6f to 1.9f),
    // East Asia
    listOf(92.0f to 45.0f, 130.6f to 42.4f, 129.5f to 41.0f, 127.5f to 39.8f, 128.5f to 38.5f, 129.5f to 36.5f, 129.3f to 35.2f, 127.5f to 34.5f, 126.3f to 34.5f, 126.5f to 36.0f, 126.5f to 37.5f, 125.0f to 38.0f, 124.5f to 40.0f, 121.5f to 41.0f, 119.5f to 40.0f, 117.7f to 39.0f, 118.5f to 38.0f, 120.5f to 37.8f, 122.5f to 37.2f, 120.5f to 36.2f, 119.5f to 35.0f, 120.5f to 34.0f, 121.5f to 32.5f, 122.0f to 30.8f, 121.5f to 28.5f, 120.0f to 27.0f, 119.5f to 25.5f, 117.5f to 23.8f, 116.5f to 23.0f, 114.5f to 22.2f, 113.5f to 22.0f, 111.0f to 21.5f, 110.5f to 20.3f, 109.5f to 21.5f, 108.0f to 21.6f, 108.0f to 25.0f, 92.0f to 28.0f, 92.0f to 45.0f),
    // Siberia
    listOf(92.0f to 65.0f, 160.0f to 65.0f, 160.0f to 54.0f, 158.5f to 52.8f, 156.5f to 51.0f, 156.0f to 57.0f, 154.0f to 59.5f, 148.0f to 59.3f, 143.0f to 59.3f, 140.5f to 57.7f, 135.5f to 54.7f, 137.5f to 54.0f, 141.5f to 53.0f, 140.5f to 51.5f, 140.5f to 48.5f, 138.0f to 46.0f, 135.5f to 43.8f, 132.0f to 43.2f, 130.6f to 42.4f, 92.0f to 45.0f, 92.0f to 65.0f),
    // Japan
    listOf(130.0f to 31.0f, 131.0f to 31.5f, 131.5f to 33.0f, 133.0f to 33.0f, 134.5f to 33.5f, 135.5f to 33.5f, 136.5f to 34.3f, 137.0f to 34.6f, 138.5f to 34.6f, 139.0f to 35.0f, 139.8f to 35.0f, 140.0f to 35.0f, 140.8f to 35.7f, 140.6f to 36.8f, 141.0f to 38.0f, 141.5f to 38.5f, 142.0f to 39.5f, 141.5f to 41.4f, 140.5f to 41.2f, 140.0f to 40.0f, 140.0f to 39.0f, 139.5f to 38.3f, 138.5f to 37.5f, 137.3f to 36.8f, 136.8f to 37.3f, 136.0f to 36.0f, 135.5f to 35.5f, 133.0f to 35.5f, 131.5f to 34.5f, 131.0f to 34.3f, 130.0f to 33.5f, 129.8f to 32.5f, 130.2f to 31.3f, 130.0f to 31.0f),
    // Hokkaido
    listOf(139.9f to 41.6f, 140.5f to 41.7f, 141.2f to 41.8f, 142.5f to 42.2f, 143.3f to 42.0f, 144.5f to 42.9f, 145.6f to 43.3f, 145.2f to 43.7f, 144.3f to 44.1f, 143.2f to 44.5f, 142.0f to 45.5f, 141.6f to 45.2f, 141.8f to 44.3f, 141.3f to 43.3f, 140.4f to 43.3f, 139.9f to 42.6f, 139.9f to 41.6f),
    // Australia
    listOf(113.5f to -22.0f, 114.0f to -26.0f, 115.0f to -30.0f, 115.0f to -33.5f, 117.0f to -35.0f, 120.0f to -34.0f, 123.5f to -34.0f, 126.0f to -32.3f, 131.0f to -31.5f, 134.0f to -32.5f, 136.0f to -34.8f, 138.0f to -35.0f, 140.0f to -37.5f, 143.5f to -38.8f, 146.3f to -39.0f, 148.0f to -37.8f, 150.0f to -37.5f, 150.2f to -35.7f, 151.0f to -34.6f, 151.9f to -32.9f, 153.0f to -31.0f, 153.6f to -28.0f, 153.0f to -25.0f, 150.8f to -22.5f, 149.0f to -20.5f, 146.3f to -19.0f, 145.5f to -15.0f, 143.5f to -14.0f, 142.5f to -10.7f, 141.6f to -13.0f, 141.5f to -17.0f, 140.0f to -17.7f, 137.0f to -16.0f, 136.5f to -12.0f, 132.5f to -11.5f, 130.0f to -12.5f, 129.5f to -15.0f, 126.5f to -14.0f, 124.0f to -16.3f, 122.0f to -18.0f, 121.0f to -19.5f, 117.0f to -20.6f, 113.5f to -22.0f),
    // Tasmania
    listOf(144.6f to -40.7f, 145.3f to -40.8f, 146.6f to -41.1f, 147.5f to -40.9f, 148.3f to -40.9f, 148.3f to -42.0f, 148.0f to -42.6f, 147.8f to -43.2f, 147.0f to -43.6f, 146.2f to -43.6f, 145.5f to -43.0f, 145.2f to -42.2f, 144.8f to -41.6f, 144.6f to -41.0f, 144.6f to -40.7f),
)
