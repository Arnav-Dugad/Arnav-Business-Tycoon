package com.arnavgames.tycoon.ui.estates

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
            lines = listOf("Six cities and forty districts on a living market cycle", "Rent, renovate and flip properties", "Build your own skyscraper"),
        )
        return
    }
    var headerPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val c = Aurum.colors
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
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
                            IconTile(if (city.unlocked) AbtIcons.Estates else AbtIcons.Lock, size = 36.dp, highlighted = city.owned > 0, tint = if (city.unlocked) c.goldText else c.textTertiary)
                            Spacer(Modifier.width(Space.md))
                            Column(Modifier.weight(1f)) {
                                Text(city.name, style = Aurum.type.titleSmall, color = if (city.unlocked) c.textPrimary else c.textTertiary)
                                Text(if (city.unlocked) "${city.districts} districts" + (if (city.owned > 0) " · ${city.owned} owned" else "") else "Opens in chapter ${city.chapter}", style = Aurum.type.bodySmall, color = c.textTertiary)
                            }
                            AbtIcon(AbtIcons.ChevronRight, null, tint = c.textTertiary, size = 16.dp)
                        }
                    }
                }
            }
            item(key = "props-h") { SectionHeader("Your properties") { Text("${e.properties.size}", style = Aurum.type.label, color = c.textTertiary) } }
            if (e.properties.isEmpty()) {
                item(key = "empty") {
                    AurumCard(Modifier.fillMaxWidth()) {
                        Text("No properties yet. Lisbon's listings refresh every six hours, and distressed ones are cheap to flip.", style = Aurum.type.body, color = c.textSecondary)
                    }
                }
            } else {
                item(key = "props") {
                    AurumCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xs)) {
                        e.properties.forEachIndexed { i, p ->
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
        IconTile(AbtIcons.named(p.icon), size = 36.dp, highlighted = p.needsAttention)
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
    AurumCard(Modifier.fillMaxWidth(), highlighted = true) {
        Row(verticalAlignment = Alignment.Top) {
            AbtIcon(AbtIcons.Key, null, tint = c.goldText, size = 18.dp)
            Spacer(Modifier.width(Space.sm))
            Text(d.text, style = Aurum.type.body, color = c.textPrimary, modifier = Modifier.weight(1f).then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier))
        }
        Spacer(Modifier.height(Space.sm))
        Text("Rent is 20% lower until you decide.", style = Aurum.type.bodySmall, color = c.textTertiary)
        Spacer(Modifier.height(Space.sm))
        d.choices.forEachIndexed { i, label ->
            PillAction(label, enabled = true, onClick = { onChoose(i) }, modifier = Modifier.fillMaxWidth().padding(top = Space.xxs), style = if (i == 0) PillStyle.Gold else PillStyle.Glass)
            d.details.getOrNull(i)?.let {
                Text(it, style = Aurum.type.label, color = c.textTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = Space.xs))
            }
        }
    }
}

/**
 * A stylised world map drawn in code: simplified coastlines (authored as low-detail longitude/latitude outlines)
 * projected over the window the cities use (longitude −90…85, latitude 5…62), with a pin per city.
 * Locked cities are dimmed and labelled with the chapter that opens them.
 */
@Composable
fun EstateMap(cities: List<CityPinUi>, onOpenCity: (String) -> Unit) {
    val c = Aurum.colors
    val water = if (c.isDark) listOf(Color(0xFF0E2233), Color(0xFF0A1622)) else listOf(Color(0xFFCFE3EE), Color(0xFFB7D3E2))
    val land = if (c.isDark) Color(0xFF2B3A2F) else Color(0xFFE9E1CC)
    val landEdge = if (c.isDark) Color(0xFF4A5E4D) else Color(0xFFC9B98F)
    val grid = if (c.isDark) Color(0x14FFFFFF) else Color(0x14000000)
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1.9f).clip(AurumShapes.medium)) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "World map with ${cities.size} cities" }) {
            drawRect(Brush.verticalGradient(water))
            fun p(lon: Float, lat: Float) = Offset((lon - LON0) / (LON1 - LON0) * size.width, (LAT1 - lat) / (LAT1 - LAT0) * size.height)
            var lon = -90f
            while (lon <= 85f) { drawLine(grid, p(lon, LAT0), p(lon, LAT1), strokeWidth = 1f); lon += 15f }
            var lat = 15f
            while (lat <= 60f) { drawLine(grid, p(LON0, lat), p(LON1, lat), strokeWidth = 1f); lat += 15f }
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

private const val LON0 = -90f
private const val LON1 = 85f
private const val LAT0 = 5f
private const val LAT1 = 62f

/** Inland seas drawn as water over Eurasia (Black Sea, Caspian Sea). */
private val SEAS: List<List<Pair<Float, Float>>> = listOf(
    listOf(27.5f to 42.1f, 28.1f to 41.3f, 31f to 41.2f, 33.5f to 42f, 36f to 41.7f, 38.5f to 40.9f, 41.5f to 41.5f, 41.7f to 42.6f, 39.5f to 44.2f, 37.5f to 44.8f,
        35.3f to 45f, 33.6f to 44.5f, 32.6f to 45.5f, 31.2f to 46.6f, 30f to 45.8f, 29.6f to 45f, 28.6f to 44.2f),
    listOf(49.1f to 46.6f, 47.2f to 45.3f, 47.6f to 43.2f, 49.2f to 41.6f, 50.4f to 40.3f, 49.1f to 38.6f, 50.3f to 37.1f, 53.9f to 36.9f, 53.9f to 38.9f, 53f to 40f,
        54.2f to 41.6f, 52.6f to 42.4f, 51.3f to 45.1f, 53.1f to 45.4f, 52.3f to 46.7f, 51f to 47f),
)

/** Low-detail coastlines (longitude, latitude), simplified by hand for a stylised look. */
private val WORLD: List<List<Pair<Float, Float>>> = listOf(
    // North America (eastern part)
    listOf(-95f to 64f, -80f to 63f, -78f to 58f, -68f to 59f, -61f to 56f, -56f to 52f, -59f to 47.5f, -64f to 47f, -66f to 44.5f, -70f to 43.5f, -70.5f to 41.6f,
        -74f to 40.5f, -75.5f to 38.5f, -76f to 36.8f, -75.6f to 35.2f, -78f to 33.8f, -81f to 31.5f, -81.4f to 30f, -80.1f to 26.5f, -80.4f to 25.2f, -81.8f to 25.9f,
        -82.8f to 28f, -83.6f to 29.9f, -85.5f to 30f, -88f to 30.4f, -89.6f to 29.2f, -91f to 29.5f, -94f to 29.6f, -97f to 27f, -97.5f to 20f, -95f to 18.5f,
        -91f to 18.7f, -90.3f to 21f, -87f to 21.5f, -87.8f to 16f, -84f to 15.8f, -83.3f to 10f, -79f to 9f, -77f to 8f, -97f to 8f, -97f to 64f),
    // Cuba
    listOf(-84.9f to 21.9f, -82f to 23.1f, -78f to 22.4f, -74.2f to 20.2f, -77.5f to 19.9f, -80.5f to 21.6f),
    // Hispaniola
    listOf(-74.4f to 19.8f, -70f to 19.7f, -68.4f to 18.5f, -71.5f to 17.7f, -74.4f to 18.3f),
    // Europe, the Middle East and South Asia
    listOf(-9.5f to 43.2f, -8.8f to 42f, -8.9f to 38.8f, -8.9f to 37f, -6.4f to 36.8f, -5.6f to 36f, -2.2f to 36.7f, -0.5f to 38.3f, 0.2f to 40f, 3.2f to 41.9f,
        3.1f to 43.1f, 6f to 43.1f, 8.5f to 44.4f, 10.3f to 43.5f, 12.4f to 41.8f, 15.6f to 40.1f, 15.9f to 38.2f, 17.1f to 39.1f, 18.5f to 40.2f, 16.1f to 41.9f,
        13.6f to 43.6f, 12.3f to 45.3f, 13.7f to 45.7f, 15.5f to 44.1f, 19.3f to 42f, 19.6f to 40f, 21.6f to 37.4f, 23f to 36.5f, 24f to 38.1f, 23f to 39.7f,
        26.2f to 40.5f, 28.9f to 41f, 26.9f to 39.4f, 27.4f to 37.1f, 30.6f to 36.5f, 36.1f to 36.6f, 35.9f to 34.6f, 34.6f to 31.6f, 32.6f to 29.9f, 34.9f to 29.5f,
        35.4f to 27.5f, 39f to 21.9f, 42.6f to 15.4f, 43.4f to 12.7f, 45f to 12.8f, 51.7f to 15.3f, 55.4f to 17.5f, 57.8f to 19.1f, 59.8f to 22.5f, 58.5f to 23.6f,
        56.3f to 24.9f, 56.4f to 26.4f, 54.6f to 24.3f, 51.6f to 24.6f, 51.3f to 26.1f, 50.1f to 26.2f, 48.4f to 28.5f, 48f to 30f, 50.2f to 30.2f, 53.9f to 27f,
        56.7f to 27.1f, 57.5f to 25.7f, 61.6f to 25.2f, 66.6f to 25.4f, 68.7f to 23.3f, 70.3f to 20.9f, 72.8f to 21.1f, 72.9f to 19f, 73.4f to 16f, 74.5f to 12.9f,
        76.3f to 9.5f, 77.5f to 8.1f, 79.1f to 9.2f, 80.2f to 13f, 80.3f to 15.8f, 82.2f to 16.6f, 85f to 19.3f, 87f to 21.5f, 87f to 62f, 60f to 62f, 30f to 62f,
        21f to 62f, 22.5f to 65f, 16f to 65f, 12f to 62f, 10.6f to 59.9f, 8.1f to 58.1f, 5.6f to 59f, 5f to 62f, -0.5f to 62f, 4.7f to 60.5f, 6f to 58f,
        8f to 56.5f, 8.6f to 54.9f, 4.8f to 52.9f, 3.7f to 51.6f, 1.6f to 50.9f, -1.6f to 49.6f, -1.9f to 48.6f, -4.7f to 48.4f, -2.2f to 47.3f, -1.2f to 45.6f,
        -1.6f to 43.4f),
    // Great Britain
    listOf(-5.7f to 50.1f, -3f to 50.6f, 1.4f to 51.2f, 1.7f to 52.7f, 0.3f to 53.5f, -0.4f to 54.5f, -1.6f to 55.6f, -2.1f to 56.9f, -1.8f to 57.6f, -3.3f to 58.6f,
        -5f to 58.6f, -5.7f to 57.3f, -6.2f to 56.4f, -5.1f to 55.4f, -4.9f to 54.8f, -3.2f to 54.6f, -3f to 53.4f, -4.6f to 53.3f, -4.1f to 52.6f, -5.2f to 51.8f,
        -3.3f to 51.4f),
    // Ireland
    listOf(-10.3f to 51.7f, -8f to 51.6f, -6.2f to 52.2f, -6f to 53.5f, -5.6f to 54.6f, -7.3f to 55.3f, -8.5f to 54.8f, -10f to 54.2f, -9.9f to 53.3f),
    // Africa (northern part)
    listOf(-17.5f to 14.7f, -16.5f to 19f, -17.1f to 21f, -14.5f to 25.5f, -13.2f to 27.6f, -9.8f to 30f, -9.6f to 32.6f, -6.8f to 34.1f, -5.9f to 35.8f,
        -1.9f to 35.1f, 3f to 36.8f, 9.8f to 37.3f, 11.1f to 35.2f, 10.2f to 33.9f, 11.5f to 33.1f, 15.2f to 32.3f, 19.9f to 30.6f, 20.1f to 32.4f, 25f to 32f,
        29f to 30.9f, 32.3f to 31.3f, 32.6f to 29.9f, 33.6f to 27.5f, 35.6f to 23.9f, 37.4f to 19.3f, 39.2f to 15.7f, 42.4f to 12.6f, 43.3f to 11.7f, 44.6f to 10.4f,
        51.2f to 11.8f, 50.8f to 9.5f, 48.5f to 5f, 8f to 4f, 5f to 5.5f, 1.2f to 6.1f, -2f to 4.8f, -7.5f to 4.4f, -11.5f to 6.9f, -13.3f to 9f, -15f to 10.9f,
        -16.7f to 12.4f),
    // Sri Lanka
    listOf(79.8f to 9.8f, 81.2f to 8.6f, 81.9f to 7.4f, 80.6f to 5.9f, 79.8f to 7f),
    // Sicily, Sardinia, Crete, Cyprus
    listOf(12.4f to 37.9f, 15.6f to 38.3f, 15.1f to 36.7f),
    listOf(8.2f to 40.9f, 9.8f to 41.1f, 9.6f to 39.1f, 8.4f to 39f),
    listOf(23.5f to 35.5f, 26.3f to 35.2f, 24.8f to 34.9f),
    listOf(32.3f to 35.1f, 34.6f to 35.6f, 33.9f to 34.6f),
)
