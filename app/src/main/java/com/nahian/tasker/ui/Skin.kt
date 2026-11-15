package com.nahian.tasker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/** One complete look for the whole app. Every page reads its colours and shapes from here. */
class Skin(
    val id: String,
    val label: String,
    val dark: Boolean,
    val bg: Brush,
    val bgColor: Color,
    val card: Color,
    val border: Color,
    val divider: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val hero: Brush,
    val onHero: Color,
    val heroInk: Color,
    val warn: Color,
    val warnBg: Color,
    val accentBg: Color,
    val chipBg: Color,
    val goodBg: Color,
    val offBg: Color,
    val navBg: Color,
    val radius: Int,
    val elevation: Int,
)

object Skins {
    /** A: dark navy with cyan and violet glow. */
    val MIDNIGHT = Skin(
        id = "MIDNIGHT", label = "Midnight", dark = true,
        bg = Brush.verticalGradient(listOf(Color(0xFF13204A), Color(0xFF070B18), Color(0xFF1A0B33))),
        bgColor = Color(0xFF070B18),
        card = Color(0xFF151D33), border = Color(0xFF34405C), divider = Color(0xFF28324A),
        text = Color(0xFFF1F5F9), muted = Color(0xFFA3B1C6),
        accent = Color(0xFF22D3EE), onAccent = Color(0xFF070B18),
        hero = Brush.linearGradient(listOf(Color(0xFF0E7490), Color(0xFF6D28D9))),
        onHero = Color(0xFFFFFFFF), heroInk = Color(0xFF4C1D95),
        warn = Color(0xFFFBBF24), warnBg = Color(0xFF3A2A12), accentBg = Color(0xFF0F3140),
        chipBg = Color(0xFF222C45), goodBg = Color(0xFF12403D), offBg = Color(0xFF2D2450),
        navBg = Color(0xFF111829), radius = 24, elevation = 0,
    )

    /** B: light with an indigo-to-pink gradient. */
    val AURORA = Skin(
        id = "AURORA", label = "Aurora", dark = false,
        bg = SolidColor(Color(0xFFF4F5FB)),
        bgColor = Color(0xFFF4F5FB),
        card = Color(0xFFFFFFFF), border = Color(0xFFE2E4EE), divider = Color(0xFFE5E7EB),
        text = Color(0xFF111827), muted = Color(0xFF4B5563),
        accent = Color(0xFF4F46E5), onAccent = Color(0xFFFFFFFF),
        hero = Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF9333EA), Color(0xFFDB2777))),
        onHero = Color(0xFFFFFFFF), heroInk = Color(0xFF4F46E5),
        warn = Color(0xFFB45309), warnBg = Color(0xFFFFF3E6), accentBg = Color(0xFFEEF0FF),
        chipBg = Color(0xFFE9EBF5), goodBg = Color(0xFFD5F2E3), offBg = Color(0xFFEFEDFF),
        navBg = Color(0xFFFFFFFF), radius = 28, elevation = 3,
    )

    /** C: black with one electric lime accent. */
    val MONO = Skin(
        id = "MONO", label = "Mono", dark = true,
        bg = SolidColor(Color(0xFF0A0A0A)),
        bgColor = Color(0xFF0A0A0A),
        card = Color(0xFF161616), border = Color(0xFF2E2E2E), divider = Color(0xFF2A2A2A),
        text = Color(0xFFFAFAFA), muted = Color(0xFFA3A3A3),
        accent = Color(0xFFC6FF3D), onAccent = Color(0xFF0A0A0A),
        hero = SolidColor(Color(0xFFC6FF3D)),
        onHero = Color(0xFF0A0A0A), heroInk = Color(0xFFC6FF3D),
        warn = Color(0xFFFF9A5C), warnBg = Color(0xFF2E1B10), accentBg = Color(0xFF222E0C),
        chipBg = Color(0xFF222222), goodBg = Color(0xFF26330F), offBg = Color(0xFF262626),
        navBg = Color(0xFF161616), radius = 18, elevation = 0,
    )

    val all = listOf(AURORA, MIDNIGHT, MONO)
    var current by mutableStateOf(AURORA)
    fun byId(id: String): Skin = all.firstOrNull { it.id == id } ?: AURORA
}

// Shorthand used across the screens. These follow whichever look is selected.
val Teal: Color get() = Skins.current.accent
val Warn: Color get() = Skins.current.warn
val Muted: Color get() = Skins.current.muted
val CardBg: Color get() = Skins.current.card
val CardBorder: Color get() = Skins.current.border
val WarnBg: Color get() = Skins.current.warnBg
val AccentBg: Color get() = Skins.current.accentBg

/** One colour per kind of item; brighter versions on the dark looks so they stay readable. */
fun moduleColor(module: String): Color = if (Skins.current.dark) when (module) {
    "TUITION" -> Color(0xFF2DD4BF)
    "CLASS" -> Color(0xFF60A5FA)
    "PRAYER" -> Color(0xFFC084FC)
    "TASK" -> Color(0xFFFBBF24)
    else -> Color(0xFFF472B6)
} else when (module) {
    "TUITION" -> Color(0xFF0F766E)
    "CLASS" -> Color(0xFF1D4ED8)
    "PRAYER" -> Color(0xFF7C3AED)
    "TASK" -> Color(0xFFB45309)
    else -> Color(0xFFBE185D)
}
