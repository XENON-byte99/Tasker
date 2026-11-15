@file:OptIn(ExperimentalMaterial3Api::class)

package com.nahian.tasker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nahian.tasker.core.fmtDate
import com.nahian.tasker.core.fmtTime
import com.nahian.tasker.core.today
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class CalItem(val id: Long, val ownerId: Long, val date: String, val startMin: Int, val status: String, val note: String)

private val GoodBg: Color get() = Skins.current.goodBg
private val MissedBg: Color get() = Skins.current.warnBg
private val PlannedBg: Color get() = Skins.current.chipBg
private val OffBg: Color get() = Skins.current.offBg

/** Month calendar for tuitions or classes: all together, or one student / one course. */
@Composable
fun CalendarScreen(nav: NavController, startKind: String) {
    val app = taskerApp()
    val scope = app.ui
    val tuitions by rememberFlow { app.dao.flowTuitions() }
    val tSessions by rememberFlow { app.dao.flowTuitionSessions() }
    val courses by rememberFlow { app.dao.flowCourses() }
    val cSessions by rememberFlow { app.dao.flowClassSessions() }
    var kind by rememberSaveable { mutableStateOf(startKind) }
    var filterId by rememberSaveable { mutableStateOf(0L) }
    var month by rememberSaveable { mutableStateOf(today().take(7)) }
    var selected by rememberSaveable { mutableStateOf(today()) }
    var moveId by rememberSaveable { mutableStateOf(0L) }

    val isTuition = kind == "TUITION"
    val items: List<CalItem> = if (isTuition) {
        tSessions.filter { it.status != "MOVED" && (filterId == 0L || it.tuitionId == filterId) && tuitions.any { t -> t.id == it.tuitionId } }
            .map { CalItem(it.id, it.tuitionId, it.date, it.startMin, it.status, it.note) }
    } else {
        cSessions.filter { it.status != "MOVED" && (filterId == 0L || it.courseId == filterId) }
            .map { CalItem(it.id, it.courseId, it.date, it.startMin, it.status, it.note) }
    }
    fun nameOf(ownerId: Long): String =
        if (isTuition) tuitions.firstOrNull { it.id == ownerId }?.let { t -> listOf(t.name, t.subject).filter { it.isNotBlank() }.joinToString(" · ") } ?: "Tuition"
        else courses.firstOrNull { it.id == ownerId }?.let { c -> listOf(c.code, c.title).filter { it.isNotBlank() }.joinToString(" · ") } ?: "Class"
    fun good(s: String) = s == "DONE" || s == "ATTENDED"

    Screen("Calendar", onBack = { nav.popBackStack() }, accent = moduleColor(kind.let { if (it == "TUITION") "TUITION" else "CLASS" }), icon = Icons.Filled.DateRange) {
        Choice(listOf("TUITION" to "Tuitions", "CLASS" to "Classes"), kind) { kind = it; filterId = 0L }
        Dropdown(
            "Show",
            listOf(0L to (if (isTuition) "All tuitions together" else "All classes together")) +
                (if (isTuition) tuitions.map { it.id to it.name } else courses.map { it.id to it.code }),
            filterId,
        ) { filterId = it }

        val first = LocalDate.parse("$month-01")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { month = first.minusMonths(1).toString().take(7) }) { Text("‹ Earlier") }
            Text(
                first.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { month = first.plusMonths(1).toString().take(7) }) { Text("Later ›") }
        }

        SectionCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            val offset = (first.dayOfWeek.value + 1) % 7   // week starts on Saturday
            val len = first.lengthOfMonth()
            val rows = (offset + len + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0..6) {
                        val dayNum = r * 7 + c - offset + 1
                        if (dayNum !in 1..len) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val ds = first.withDayOfMonth(dayNum).toString()
                            val dayItems = items.filter { it.date == ds }
                            val bg = when {
                                dayItems.isEmpty() -> Color.Transparent
                                dayItems.any { it.status == "MISSED" } -> MissedBg
                                dayItems.any { it.status == "PLANNED" } -> PlannedBg
                                dayItems.any { good(it.status) } -> GoodBg
                                else -> OffBg
                            }
                            Box(
                                Modifier.weight(1f).aspectRatio(1f)
                                    .background(bg, RoundedCornerShape(8.dp))
                                    .border(if (ds == selected) 2.dp else 0.dp, if (ds == selected) Teal else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { selected = ds },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$dayNum", fontWeight = if (ds == today()) FontWeight.Bold else FontWeight.Normal, color = if (ds == today()) Teal else Color.Unspecified)
                                    if (dayItems.isNotEmpty()) {
                                        Text("${dayItems.count { good(it.status) }}/${dayItems.size}", style = MaterialTheme.typography.labelSmall, color = Muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "Green: all " + (if (isTuition) "done" else "attended") + " · Orange: something missed · Grey: still planned. The small number is " +
                    (if (isTuition) "done" else "attended") + " out of total that day.",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
        }

        val monthItems = items.filter { it.date.startsWith(month) }
        val nGood = monthItems.count { good(it.status) }
        val nMissed = monthItems.count { it.status == "MISSED" }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Stat(if (isTuition) "Done" else "Attended", "$nGood", Modifier.weight(1f), Teal)
            Stat("Missed", "$nMissed", Modifier.weight(1f), if (nMissed > 0) Warn else Color.Unspecified)
            Stat("Planned", "${monthItems.count { it.status == "PLANNED" }}", Modifier.weight(1f))
        }
        if (!isTuition) {
            val off = monthItems.count { it.status == "CANCELLED" }
            val pct = if (nGood + nMissed == 0) null else nGood * 100 / (nGood + nMissed)
            Text("Attendance this month: " + (if (pct == null) "no data yet" else "$pct%") + " · cancelled classes: $off", color = Muted)
        }

        SectionCard(fmtDate(selected)) {
            val dayItems = items.filter { it.date == selected }.sortedBy { it.startMin }
            if (dayItems.isEmpty()) Text(if (isTuition) "No tuition on this day." else "No classes on this day.", color = Muted)
            dayItems.forEach { s ->
                Line(
                    nameOf(s.ownerId),
                    fmtTime(s.startMin) + if (s.note.isBlank()) "" else " · " + s.note,
                    when (s.status) { "DONE" -> "Done"; "ATTENDED" -> "Attended"; "MISSED" -> "Missed"; "CANCELLED" -> "Cancelled"; else -> "Planned" },
                    when { good(s.status) -> Teal; s.status == "MISSED" -> Warn; else -> Muted },
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isTuition) {
                        OutlinedButton(onClick = { scope.launch { app.repo.markTuitionSession(s.id, "DONE", s.note) } }) { Text("Done") }
                        OutlinedButton(onClick = { scope.launch { app.repo.markTuitionSession(s.id, "MISSED", s.note) } }) { Text("Missed") }
                    } else {
                        OutlinedButton(onClick = { scope.launch { app.repo.markClassSession(s.id, "ATTENDED", s.note) } }) { Text("Attended") }
                        OutlinedButton(onClick = { scope.launch { app.repo.markClassSession(s.id, "MISSED", s.note) } }) { Text("Missed") }
                        OutlinedButton(onClick = { scope.launch { app.repo.markClassSession(s.id, "CANCELLED", s.note) } }) { Text("Off") }
                    }
                    TextButton(onClick = { moveId = s.id }) { Text("Reschedule") }
                }
            }
        }
    }

    items.firstOrNull { it.id == moveId }?.let { s ->
        RescheduleDialog(if (isTuition) "Reschedule tuition day" else "Reschedule class", s.startMin, { moveId = 0L }) { d, m ->
            scope.launch { if (isTuition) app.repo.moveTuitionSession(s.id, d, m, "") else app.repo.moveClassSession(s.id, d, m) }
        }
    }
}
