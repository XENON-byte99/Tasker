package com.nahian.tasker.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") // index + 1 = ISO weekday
val PRAYERS = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

fun today(): String = LocalDate.now().toString()
fun nowMinutes(): Int = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
fun date(s: String): LocalDate = LocalDate.parse(s)

fun parseDateOrNull(s: String): LocalDate? =
    try { LocalDate.parse(s.trim()) } catch (e: Exception) { null }

/** Parses "17:30" (24-hour) into minutes from midnight. */
fun parseTimeOrNull(s: String): Int? {
    val p = s.trim().split(":")
    if (p.size != 2) return null
    val h = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

fun time24(min: Int): String = String.format(Locale.US, "%02d:%02d", min / 60, min % 60)

fun fmtTime(min: Int): String {
    val h = min / 60
    val m = min % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return String.format(Locale.US, "%d:%02d %s", h12, m, if (h < 12) "AM" else "PM")
}

fun fmtDate(s: String): String =
    try { date(s).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)) } catch (e: Exception) { s }

fun fmtMillis(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US))

fun millisOf(dateStr: String, min: Int): Long =
    date(dateStr).atTime(min / 60, min % 60).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Money is stored in poisha. */
fun money(p: Long): String {
    val sign = if (p < 0) "-" else ""
    val abs = Math.abs(p)
    val taka = String.format(Locale.US, "%,d", abs / 100)
    val rest = abs % 100
    return if (rest == 0L) "$sign৳$taka" else "$sign৳$taka." + String.format(Locale.US, "%02d", rest)
}

fun parseMoney(s: String): Long? =
    s.replace(",", "").replace("৳", "").trim().toDoubleOrNull()?.takeIf { it > 0 }?.let { Math.round(it * 100) }

fun weekdaysLabel(days: String): String =
    days.split(",").mapNotNull { it.toIntOrNull() }.sorted().joinToString(", ") { WEEKDAYS[it - 1] }
