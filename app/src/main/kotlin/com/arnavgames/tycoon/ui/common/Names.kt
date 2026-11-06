package com.arnavgames.tycoon.ui.common

/** Short form of a person's name for chips and buttons: "Mira Okafor" → "Mira", "Dr. Imogen Fairweather" → "Dr. Fairweather". */
fun shortPersonName(full: String): String {
    val parts = full.trim().split(' ').filter { it.isNotEmpty() }
    if (parts.isEmpty()) return full
    return if (parts.first().endsWith('.') && parts.size > 1) "${parts.first()} ${parts.last()}" else parts.first()
}
