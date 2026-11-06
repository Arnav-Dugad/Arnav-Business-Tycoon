package com.arnavgames.tycoon.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The game's original icon set. Every glyph is hand-authored path data on a 24-unit grid,
 * 1.75 stroke, round caps and joins. Emoji and third-party icon packs are never used.
 * Icons are tinted at the call site (see AbtIcon).
 */
object AbtIcons {
    private const val STROKE = 1.75f

    private fun icon(name: String, vararg strokes: String, filled: List<String> = emptyList()): ImageVector {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        filled.forEach { d -> b.addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black)) }
        strokes.forEach { d ->
            b.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} $cy A$r $r 0 1 0 ${cx + r} $cy A$r $r 0 1 0 ${cx - r} $cy Z"

    // ---- Navigation ----
    val Empire = icon("Empire", "M4 20V9.5L9 7v13", "M9 20V3.5l7 2.8V20", "M16 20v-8.5h4V20", "M2.5 20h19", "M11.5 9h2M11.5 12h2M11.5 15h2")
    val Markets = icon("Markets", "M3 17.5l6-6 4 3.5 8-8", "M15.5 7H21v5.5")
    val Estates = icon("Estates", "M12 21s-7-6.4-7-11.5a7 7 0 0 1 14 0C19 14.6 12 21 12 21Z", "M9.5 12V9.4L12 7.6l2.5 1.8V12Z")
    val Collection = icon("Collection", "M6.5 4h11L21 9l-9 11.5L3 9Z", "M3 9h18", "M9.5 4L8 9l4 11.5L16 9l-1.5-5")
    val Crown = icon("Crown", "M3.5 8.5L7.5 12 12 5l4.5 7 4-3.5L19 17H5Z", "M5 20h14")

    // ---- Currencies ----
    val Cash = icon("Cash", "M2.5 6.5h19v11h-19Z", circle(12f, 12f, 2.5f), "M6 12h.01M18 12h.01")
    val Bullion = icon("Bullion", "M3 18l3-7h12l3 7Z", "M6 11l2.5-4h7l2.5 4", "M9 14.5h6")
    val Coin = icon("Coin", circle(12f, 12f, 8.5f), circle(12f, 12f, 4.5f))
    val Favor = icon("Favor", "M12 20.5S3.5 15.3 3.5 9.2A4.4 4.4 0 0 1 12 7a4.4 4.4 0 0 1 8.5 2.2C20.5 15.3 12 20.5 12 20.5Z")

    // ---- Actions & UI ----
    val ChevronRight = icon("ChevronRight", "M9.5 5.5L16 12l-6.5 6.5")
    val ChevronLeft = icon("ChevronLeft", "M14.5 5.5L8 12l6.5 6.5")
    val ChevronDown = icon("ChevronDown", "M5.5 9.5L12 16l6.5-6.5")
    val ChevronUp = icon("ChevronUp", "M5.5 14.5L12 8l6.5 6.5")
    val Close = icon("Close", "M6 6l12 12M18 6L6 18")
    val Plus = icon("Plus", "M12 5v14M5 12h14")
    val Minus = icon("Minus", "M5 12h14")
    val Check = icon("Check", "M5 12.5l5 5L19 7")
    val ArrowUp = icon("ArrowUp", "M12 19V5M6 11l6-6 6 6")
    val ArrowDown = icon("ArrowDown", "M12 5v14M6 13l6 6 6-6")
    val Refresh = icon("Refresh", "M19.5 9A8 8 0 0 0 5 7.5M4.5 15A8 8 0 0 0 19 16.5", "M19.5 4v5h-5M4.5 20v-5h5")
    val Sliders = icon("Sliders", "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f))
    val Info = icon("Info", circle(12f, 12f, 9f), "M12 11v5.5M12 7.6v-.1")
    val Warning = icon("Warning", "M12 3.5l9.5 16h-19Z", "M12 9.5v4M12 16.6v-.1")
    val Lock = icon("Lock", "M5.5 10.5h13v10h-13Z", "M8 10.5v-3a4 4 0 0 1 8 0v3", "M12 14.5v2")
    val Eye = icon("Eye", "M2.5 12C5 7 8.5 5 12 5s7 2 9.5 7c-2.5 5-6 7-9.5 7s-7-2-9.5-7Z", circle(12f, 12f, 3f))
    val EyeOff = icon("EyeOff", "M4 4l16 16", "M9.9 5.3A9 9 0 0 1 12 5c3.5 0 7 2 9.5 7a14 14 0 0 1-2.6 3.6M6.4 6.9A13.5 13.5 0 0 0 2.5 12c2.5 5 6 7 9.5 7a9 9 0 0 0 4.3-1.1", "M9.9 9.9a3 3 0 0 0 4.2 4.2")
    val Mail = icon("Mail", "M3 5.5h18v13H3Z", "M3.5 6L12 13l8.5-7")
    val Bell = icon("Bell", "M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15Z", "M10 20.5a2 2 0 0 0 4 0")
    val User = icon("User", circle(12f, 8f, 4f), "M4.5 20.5a7.5 7.5 0 0 1 15 0")
    val Rivals = icon("Rivals", circle(8.5f, 8.5f, 3.2f), circle(16.5f, 9.5f, 2.6f), "M2.5 19.5a6 6 0 0 1 12 0", "M14.8 14.2a5 5 0 0 1 6.7 4.8")
    val Clock = icon("Clock", circle(12f, 12f, 9f), "M12 7.5V12l3 2")
    val Bolt = icon("Bolt", "M13 2.5L5 13.5h6l-1 8 9-11.5h-6Z")
    val Sparkle = icon("Sparkle", "M12 3l1.8 7.2L21 12l-7.2 1.8L12 21l-1.8-7.2L3 12l7.2-1.8Z")
    val Star = icon("Star", "M12 3l2.7 5.6 6.1.7-4.5 4.2 1.2 6.1-5.5-3-5.5 3 1.2-6.1-4.5-4.2 6.1-.7Z")
    val Trophy = icon("Trophy", "M7.5 4h9v5a4.5 4.5 0 0 1-9 0Z", "M7.5 6h-3v1.5a3 3 0 0 0 3.3 3M16.5 6h3v1.5a3 3 0 0 1-3.3 3", "M12 13.5V17M8.5 20.5h7M10 17h4l.5 3.5h-5Z")
    val Briefcase = icon("Briefcase", "M3 8h18v11H3Z", "M8.5 8V5.5h7V8", "M3 13h18")
    val Store = icon("Store", "M5 8h14l-1 12.5H6Z", "M9 10.5V7a3 3 0 0 1 6 0v3.5")
    val Gift = icon("Gift", "M4 10h16v10.5H4Z", "M3 7h18v3H3Z", "M12 7v13.5", "M12 7C10 3.5 6.5 4.5 7.5 7M12 7c2-3.5 5.5-2.5 4.5 0")
    val Shield = icon("Shield", "M12 3l7.5 3v5.5c0 4.5-3.2 8.1-7.5 9.5-4.3-1.4-7.5-5-7.5-9.5V6Z", "M8.8 12l2.2 2.2 4.2-4.4")
    val Factory = icon("Factory", "M2.5 20.5h19", "M4 20.5V11l5 3v-3l5 3V5h5v15.5", "M16.5 5V3")
    val News = icon("News", "M4 5h13v14a1.5 1.5 0 0 1-1.5 1.5h-10A1.5 1.5 0 0 1 4 19Z", "M17 9h3v10a1.5 1.5 0 0 1-3 0", "M7 8.5h7M7 12h7M7 15.5h4")
    val Map = icon("Map", "M3.5 6.5L9 4.5l6 2 5.5-2v13l-5.5 2-6-2-5.5 2Z", "M9 4.5v13M15 6.5v13")
    val Hand = icon("Hand", "M9 11V5.5a1.5 1.5 0 0 1 3 0V11M12 10a1.5 1.5 0 0 1 3 0v1.5M15 11a1.5 1.5 0 0 1 3 0v4.5a6 6 0 0 1-6 6h-.5a6 6 0 0 1-4.9-2.6l-2.3-3.3a1.5 1.5 0 0 1 2.4-1.8L9 16")
    val Sun = icon("Sun", circle(12f, 12f, 4f), "M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4")
    val Moon = icon("Moon", "M20 14.5A8.5 8.5 0 1 1 9.5 4a7 7 0 0 0 10.5 10.5Z")
    val Timer = icon("Timer", circle(12f, 13.5f, 7.5f), "M12 9.5v4l2.5 1.5", "M9.5 2.5h5M12 2.5V6")
    val Manager = icon("Manager", circle(12f, 7.5f, 3.5f), "M5 20.5a7 7 0 0 1 14 0", "M12 12.5l-1.2 2.3L12 19l1.2-4.2Z")
    val Expand = icon("Expand", "M4 20V10l8-5.5L20 10v10", "M9 20v-6h6v6", "M2.5 20h19")
    val Inbox = icon("Inbox", "M3 13.5l2.5-8h13l2.5 8v6H3Z", "M3 13.5h5l1.5 2.5h5l1.5-2.5h5")
    val Chart = icon("Chart", "M4 20V4", "M4 20h16", "M8 16v-4M12 16V8M16 16v-6")
    val Volume = icon("Volume", "M4 9.5h3.5L12 5.5v13l-4.5-4H4Z", "M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11")
    val Music = icon("Music", "M9 18V5.5l11-2V16", circle(6.5f, 18f, 2.5f), circle(17.5f, 16f, 2.5f))
    val Vibrate = icon("Vibrate", "M8 3.5h8v17H8Z", "M4.5 8v8M19.5 8v8M2 10v4M22 10v4")
    val Logout = icon("Logout", "M14 4.5H6.5v15H14", "M10.5 12h10M17 8.5l3.5 3.5-3.5 3.5")
    val Trash = icon("Trash", "M4 6.5h16", "M9 6.5V4h6v2.5", "M6 6.5l1 14h10l1-14", "M10 10.5v6M14 10.5v6")
    val Download = icon("Download", "M12 4v11M7 10.5l5 5 5-5", "M4.5 19.5h15")
    val Cloud = icon("Cloud", "M7 18.5a4.5 4.5 0 0 1-.6-9 6 6 0 0 1 11.4 1.5A3.8 3.8 0 0 1 17.5 18.5Z")
    val Upload = icon("Upload", "M12 15V4M7 8.5l5-5 5 5", "M4.5 19.5h15")
    val Book = icon("Book", "M4 5.5A1.5 1.5 0 0 1 5.5 4H11v15.5H5.5A1.5 1.5 0 0 0 4 21Z", "M20 5.5A1.5 1.5 0 0 0 18.5 4H13v15.5h5.5A1.5 1.5 0 0 1 20 21Z")
    val Fleet = icon("Fleet", "M2.5 7h11v9h-11Z", "M13.5 10h4l3 3v3h-7", circle(6f, 17.5f, 1.7f), circle(17f, 17.5f, 1.7f))
    val Route = icon("Route", circle(6f, 6f, 2.5f), circle(18f, 18f, 2.5f), "M8.5 6H15a3 3 0 0 1 0 6H9a3 3 0 0 0 0 6h6.5")
    val Tag = icon("Tag", "M3.5 12.5V4h8.5l8.5 8.5-8.5 8.5Z", circle(8f, 8.5f, 1.4f))
    val Megaphone = icon("Megaphone", "M3.5 10v4h3l9 4.5V5.5l-9 4.5Z", "M6.5 14l1.5 5.5h2.5L9.5 15", "M19 9.5a3.5 3.5 0 0 1 0 5")
    val Chef = icon("Chef", "M7 13.5V20h10v-6.5", "M7 13.5a4 4 0 0 1-.5-7.9 5 5 0 0 1 11 0 4 4 0 0 1-.5 7.9Z", "M7 17h10")
    val StarFilled = icon("StarFilled", "M12 3l2.7 5.6 6.1.7-4.5 4.2 1.2 6.1-5.5-3-5.5 3 1.2-6.1-4.5-4.2 6.1-.7Z", filled = listOf("M12 3l2.7 5.6 6.1.7-4.5 4.2 1.2 6.1-5.5-3-5.5 3 1.2-6.1-4.5-4.2 6.1-.7Z"))
    val Pause = icon("Pause", "M8.5 5v14M15.5 5v14")
    val Play = icon("Play", "M7 4.5v15l12-7.5Z")

    // ---- Business glyphs (Chapters 2–7) ----
    val Wheat = icon(
        "Wheat", "M12 21V7",
        "M12 9c-2.5 0-4-1.5-4-4 2.5 0 4 1.5 4 4Z", "M12 9c2.5 0 4-1.5 4-4-2.5 0-4 1.5-4 4Z",
        "M12 14c-2.5 0-4-1.5-4-4 2.5 0 4 1.5 4 4Z", "M12 14c2.5 0 4-1.5 4-4-2.5 0-4 1.5-4 4Z",
        "M12 19c-2.5 0-4-1.5-4-4 2.5 0 4 1.5 4 4Z", "M12 19c2.5 0 4-1.5 4-4-2.5 0-4 1.5-4 4Z",
    )
    val Hanger = icon("Hanger", "M10 5.5a2 2 0 1 1 2 2v1.5", "M12 9 3.6 15.4a1.2 1.2 0 0 0 .7 2.1h15.4a1.2 1.2 0 0 0 .7-2.1Z")
    val Pickaxe = icon("Pickaxe", "M4 8.5c3.5-3.5 9.5-4 14.5-1.5", "M11.5 6.8 18 20.5", "M17.2 5.5l1.8 2.2")
    val Flame = icon(
        "Flame",
        "M12 21c-4 0-6.5-2.7-6.5-6.2 0-3.6 3.2-5.6 3.8-9.3 2.3 1.4 3.4 3.3 3.7 5 .9-.8 1.5-2 1.6-3.2 2.4 2 3.9 4.6 3.9 7.5 0 3.5-2.5 6.2-6.5 6.2Z",
        "M12 21c-1.7 0-2.8-1.2-2.8-2.8 0-1.8 1.6-2.7 2.8-4.2 1.2 1.5 2.8 2.4 2.8 4.2 0 1.6-1.1 2.8-2.8 2.8Z",
    )
    val Bed = icon("Bed", "M3 19V6.5", "M3 14.5h18V19", "M21 14.5V12a2.5 2.5 0 0 0-2.5-2.5H11v5", circle(7f, 11.8f, 1.8f))
    val Bank = icon("Bank", "M3.5 9.5 12 4l8.5 5.5Z", "M5.5 9.5V17M9.8 9.5V17M14.2 9.5V17M18.5 9.5V17", "M4 17h16", "M3 20h18")
    val Crane = icon("Crane", "M6 21V4.5", "M3 21h6", "M6 4.5h14.5", "M6 8.5 10 4.5", "M17.5 4.5v6", "M17.5 10.5v2.3a1.6 1.6 0 1 1-3.2 0")
    val Film = icon("Film", "M3.5 5.5h17v13h-17Z", "M7.5 5.5v13M16.5 5.5v13", "M3.5 9.5h4M3.5 14.5h4M16.5 9.5h4M16.5 14.5h4")
    val Chip = icon("Chip", "M7 7h10v10H7Z", "M10 10h4v4h-4Z", "M9.5 3.5V7M14.5 3.5V7M9.5 17v3.5M14.5 17v3.5M3.5 9.5H7M3.5 14.5H7M17 9.5h3.5M17 14.5h3.5")
    val Antenna = icon("Antenna", "M12 10.5 7.5 21M12 10.5 16.5 21M9 17.5h6", circle(12f, 8.8f, 1.6f), "M8.3 4.8a5.4 5.4 0 0 0 0 8M15.7 4.8a5.4 5.4 0 0 1 0 8")
    val Plane = icon("Plane", "M10.5 13.5 3.5 11l1.5-1.5 7.5 1L17 6a2 2 0 0 1 2.8 2.8L15.3 13.3l1 7.5-1.5 1.5-2.5-7", "M7 17.5l-3 .5.5-3 2.5-.5")
    val Flask = icon("Flask", "M9.5 3.5h5", "M10.5 3.5v6L5 18.5a1.5 1.5 0 0 0 1.3 2.2h11.4a1.5 1.5 0 0 0 1.3-2.2L13.5 9.5v-6", "M7.4 14.5h9.2")
    val Ball = icon("Ball", circle(12f, 12f, 8.5f), "M3.5 12h17M12 3.5v17", "M6 6a11.5 11.5 0 0 1 0 12M18 6a11.5 11.5 0 0 0 0 12")
    val Rocket = icon(
        "Rocket", "M12 2.5c3 2.5 4.5 6 4.5 10v3h-9v-3c0-4 1.5-7.5 4.5-10Z", circle(12f, 9.5f, 1.6f),
        "M7.5 12.5 4.5 16v3l3-1.5M16.5 12.5l3 3.5v3l-3-1.5", "M10 18.5l2 3 2-3",
    )
    val Buildings = icon("Buildings", "M4 21V8h7v13", "M11 21V3.5h9V21", "M2.5 21h19", "M6.5 11.5h2M6.5 15h2M14 7h3M14 10.5h3M14 14h3")

    // ---- Real estate ----
    val House = icon("House", "M3.5 11 12 4l8.5 7", "M5.5 9.5V20h13V9.5", "M10 20v-5.5h4V20")
    val Villa = icon("Villa", "M2.5 12 9 6.5l6.5 5.5", "M4.5 10.5V18h9v-7.5", "M15.5 12H21v6h-7.5", "M2 21c2 0 2-1.2 4-1.2s2 1.2 4 1.2 2-1.2 4-1.2 2 1.2 4 1.2 2-1.2 4-1.2", "M7.5 18v-3h3v3")
    val Warehouse = icon("Warehouse", "M2.5 9 12 4l9.5 5v11.5h-19Z", "M6.5 20.5v-8h11v8", "M6.5 15h11M6.5 17.8h11")
    val Plot = icon("Plot", "M3 19.5 7 9.5h10l4 10Z", "M12 9.5v10M5 14.5h14", "M9.5 6.5V3.5l3 1.5-3 1.5")
    val Tower = icon("Tower", "M8 21V6.5L12 3l4 3.5V21", "M5 21h14", "M10.5 9h3M10.5 12h3M10.5 15h3M10.5 18h3")
    val Key = icon("Key", circle(8f, 15.5f, 4.5f), "M11.3 12.2 20 3.5", "M16.5 7l2.5 2.5M14.3 9.2l1.8 1.8")
    val Wrench = icon("Wrench", "M14.5 4.2a4.5 4.5 0 0 0-5.3 6L3.8 15.6a2 2 0 0 0 2.8 2.8L12 13a4.5 4.5 0 0 0 6-5.3l-2.8 2.8-2.5-.4-.4-2.5Z")
    val Umbrella = icon("Umbrella", "M3 12a9 9 0 0 1 18 0Z", "M12 12v6.5a2 2 0 0 1-4 0", "M12 3v-.5")

    // ---- Luxury ----
    val Car = icon("Car", "M3.5 15.5V12l2-4.5h13l2 4.5v3.5Z", "M3.5 12h17", circle(7.5f, 15.8f, 1.8f), circle(16.5f, 15.8f, 1.8f), "M8 9.8h8")
    val Watch = icon("Watch", circle(12f, 12f, 5.5f), "M9 6.8 9.6 3h4.8l.6 3.8M9 17.2l.6 3.8h4.8l.6-3.8", "M12 9.5V12l1.8 1.2")
    val Wine = icon("Wine", "M8 3h8l-.6 6a3.4 3.4 0 0 1-6.8 0Z", "M12 12.4V20", "M8.5 20.5h7", "M8.4 7h7.2")
    val Gem = icon("Gem", "M7 4h10l4 5-9 11L3 9Z", "M3 9h18", "M10 4 8.5 9 12 20l3.5-11L14 4")
    val Heli = icon("Heli", "M3 5.5h18", "M12 5.5V8", "M6 12.5a5 4.5 0 0 1 5-4.5h3a3.5 3.5 0 0 1 3.5 3.5v2.5H7.5", "M17.5 11h4M21.5 9v4", "M8 16.5v2M13.5 16.5v2M6 18.5h9.5")
    val Yacht = icon("Yacht", "M2.5 15.5h19l-2.5 4h-14Z", "M6 15.5 8 11h9l2 4.5", "M10 11V8h5.5l1.5 3", "M12 8V5")
    val Frame = icon("Frame", "M4 4h16v16H4Z", "M7 7h10v10H7Z", "M7 15l3-3 2 2 2-2.5 3 3.5")
    val Bust = icon("Bust", circle(12f, 7.5f, 3.5f), "M7.5 20.5c0-3.5 1.8-6 4.5-6s4.5 2.5 4.5 6", "M6 20.5h12", "M9.5 11.5l-1 2.5M14.5 11.5l1 2.5")
    val Island = icon("Island", "M2.5 19c3-2 6-2 9.5-2s6.5 0 9.5 2", "M12 17V9", "M12 9c-1.5-2.5-4-3-6-2M12 9c1.5-2.5 4-3 6-2M12 9c-.5-2.5.5-4.5 2.5-5.5", "M12 9c-2 0-3.5 1.5-4 3.5")
    val Mansion = icon("Mansion", "M2.5 20.5h19", "M4 20.5V11l8-5.5 8 5.5v9.5", "M9.5 20.5v-5h5v5", "M7 12.5h2M15 12.5h2", "M12 9.5h.01")

    private val byName: Map<String, ImageVector> by lazy {
        listOf(
            Empire, Markets, Estates, Collection, Crown, Cash, Bullion, Coin, Favor, ChevronRight, ChevronLeft, ChevronDown,
            ChevronUp, Close, Plus, Minus, Check, ArrowUp, ArrowDown, Refresh, Sliders, Info, Warning, Lock, Eye, EyeOff,
            Mail, Bell, User, Rivals, Clock, Bolt, Sparkle, Star, Trophy, Briefcase, Store, Gift, Shield, Factory, News, Map,
            Hand, Sun, Moon, Timer, Manager, Expand, Inbox, Chart, Volume, Music, Vibrate, Logout, Trash, Download, Cloud,
            Upload, Book, Fleet, Route, Tag, Megaphone, Chef, Pause, Play, StarFilled, House, Villa, Warehouse, Plot, Tower, Key, Wrench, Umbrella, Car, Watch, Wine, Gem, Heli, Yacht, Frame, Bust, Island, Mansion,
            Wheat, Hanger, Pickaxe, Flame, Bed, Bank, Crane, Film, Chip, Antenna, Plane, Flask, Ball, Rocket, Buildings,
        ).associateBy { it.name }
    }

    /** Looks up an icon by the name used in content JSON (falls back to Briefcase). */
    fun named(name: String): ImageVector = byName[name] ?: Briefcase
}
