@file:OptIn(ExperimentalMaterial3Api::class)

package com.nahian.tasker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nahian.tasker.core.WEEKDAYS
import com.nahian.tasker.core.date
import com.nahian.tasker.core.fmtDate
import com.nahian.tasker.core.fmtTime
import com.nahian.tasker.core.money
import com.nahian.tasker.core.parseDateOrNull
import com.nahian.tasker.core.parseMoney
import com.nahian.tasker.core.parseTimeOrNull
import com.nahian.tasker.core.time24
import com.nahian.tasker.core.today
import com.nahian.tasker.core.weekdaysLabel
import com.nahian.tasker.data.ClassSession
import com.nahian.tasker.data.ClassSlot
import com.nahian.tasker.data.Course
import com.nahian.tasker.data.Tuition
import com.nahian.tasker.data.TuitionSession
import com.nahian.tasker.data.TuitionSet
import kotlinx.coroutines.launch

private fun payLabel(t: Tuition): String = when (t.payModel) {
    "SET" -> "per ${t.daysPerSet} days ${money(t.fee)}"
    "MONTHLY" -> "monthly ${money(t.fee)}"
    else -> "per day ${money(t.fee)}"
}

@Composable
fun ScheduleScreen(nav: NavController) {
    var tab by rememberSaveable { mutableStateOf("TUITIONS") }
    Screen("Schedule", accent = moduleColor("CLASS"), icon = Icons.Filled.DateRange) {
        Choice(listOf("TUITIONS" to "Tuitions", "CLASSES" to "Classes"), tab) { tab = it }
        OutlinedButton(onClick = { nav.navigate("calendar/" + if (tab == "TUITIONS") "TUITION" else "CLASS") }, modifier = Modifier.fillMaxWidth()) { Text("Calendar view") }
        if (tab == "TUITIONS") TuitionsTab(nav) else ClassesTab(nav)
    }
}

@Composable
private fun TuitionsTab(nav: NavController) {
    val app = taskerApp()
    val scope = app.ui
    val tuitions by rememberFlow { app.dao.flowTuitions() }
    val sets by rememberFlow { app.dao.flowSets() }
    val sessions by rememberFlow { app.dao.flowTuitionSessions() }
    val txns by rememberFlow { app.dao.flowTxns() }
    val month = today().take(7)

    val received = txns.filter { it.type == "INCOME" && it.sourceType == "TUITION_SET" && it.date.startsWith(month) }.sumOf { it.amount }
    val due = sets.filter { it.status == "COMPLETE" }.sumOf { maxOf(0L, it.fee - it.paidAmount) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Stat("Received this month", money(received), Modifier.weight(1f))
        Stat("Due to collect", money(due), Modifier.weight(1f), color = if (due > 0) Warn else Color.Unspecified)
    }

    // Day tabs, the same as for classes: pick a day and mark its tuitions.
    var day by rememberSaveable { mutableStateOf(today()) }
    var editing by remember { mutableStateOf<TuitionSession?>(null) }
    var moving by remember { mutableStateOf<TuitionSession?>(null) }
    val start = date(today())
    Choice((0..6).map { i -> start.plusDays(i.toLong()).let { it.toString() to (WEEKDAYS[it.dayOfWeek.value - 1] + " " + it.dayOfMonth) } }, day) { day = it }
    val todays = sessions.filter { it.date == day && it.status != "MOVED" && tuitions.any { t -> t.id == it.tuitionId } }.sortedBy { it.startMin }
    if (todays.isEmpty()) Text("No tuition on this day.", color = Muted)
    todays.forEach { s ->
        val t = tuitions.first { it.id == s.tuitionId }
        SectionCard(accent = moduleColor("TUITION")) {
            Line(
                listOf(t.name, t.subject).filter { it.isNotBlank() }.joinToString(" · "),
                fmtTime(s.startMin) + " · " + t.durationMin + " min",
                when (s.status) { "DONE" -> "Done"; "MISSED" -> "Missed"; else -> "Planned" },
                when (s.status) { "DONE" -> Teal; "MISSED" -> Warn; else -> Muted },
            )
            if (s.note.isNotBlank()) Text(s.note, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { scope.launch { app.repo.markTuitionSession(s.id, "DONE", s.note) } }) { Text("Done") }
                OutlinedButton(onClick = { scope.launch { app.repo.markTuitionSession(s.id, "MISSED", s.note) } }) { Text("Missed") }
                TextButton(onClick = { moving = s }) { Text("Reschedule") }
                TextButton(onClick = { editing = s }) { Text("Note") }
            }
        }
    }
    editing?.let { s -> TuitionSessionDialog(s) { editing = null } }
    moving?.let { s ->
        RescheduleDialog("Reschedule tuition day", s.startMin, { moving = null }) { d, m -> scope.launch { app.repo.moveTuitionSession(s.id, d, m, "") } }
    }

    Text("All tuitions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

    if (tuitions.isEmpty()) Text("No tuitions yet. Add your first one to start getting reminders.", color = Muted)
    tuitions.forEach { t ->
        val open = sets.firstOrNull { it.tuitionId == t.id && it.status == "OPEN" }
        val done = if (open == null) 0 else sessions.count { it.setId == open.id && it.status == "DONE" }
        val unpaid = sets.filter { it.tuitionId == t.id && it.status == "COMPLETE" }.sumOf { maxOf(0L, it.fee - it.paidAmount) }
        val next = sessions.filter { it.tuitionId == t.id && it.status == "PLANNED" && it.date >= today() }.minByOrNull { it.date }
        SectionCard(container = if (unpaid > 0) WarnBg else CardBg, accent = moduleColor("TUITION")) {
            Column(Modifier.fillMaxWidth().clickable { nav.navigate("tuition/${t.id}") }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Line(
                    listOf(t.name, t.subject).filter { it.isNotBlank() }.joinToString(" · "),
                    "${weekdaysLabel(t.days)} · ${fmtTime(t.startMin)} · ${payLabel(t)}",
                    if (t.payModel == "SET") "Day $done of ${t.daysPerSet}" else "$done days",
                    Teal,
                )
                if (t.payModel == "SET" && t.daysPerSet > 0) {
                    LinearProgressIndicator(progress = { (done.toFloat() / t.daysPerSet).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
                if (unpaid > 0) Text("Collect ${money(unpaid)}", color = Warn, fontWeight = FontWeight.SemiBold)
                if (next != null) Text("Next: ${fmtDate(next.date)}, ${fmtTime(next.startMin)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Button(onClick = { nav.navigate("tuitionEdit/0") }, modifier = Modifier.fillMaxWidth()) { Text("Add tuition") }
}

@Composable
fun TuitionDetailScreen(nav: NavController, id: Long) {
    val app = taskerApp()
    val scope = app.ui
    val tuitions by rememberFlow { app.dao.flowTuitions() }
    val allSets by rememberFlow { app.dao.flowSets() }
    val allSessions by rememberFlow { app.dao.flowTuitionSessions() }
    val t = tuitions.firstOrNull { it.id == id }
    val sets = allSets.filter { it.tuitionId == id }
    val sessions = allSessions.filter { it.tuitionId == id && it.status != "MOVED" }
    var editing by remember { mutableStateOf<TuitionSession?>(null) }
    var paying by remember { mutableStateOf<TuitionSet?>(null) }
    var addDay by remember { mutableStateOf(false) }
    var backfill by remember { mutableStateOf(false) }

    Screen(
        title = t?.name ?: "Tuition",
        onBack = { nav.popBackStack() },
        actions = { TextButton(onClick = { nav.navigate("tuitionEdit/$id") }) { Text("Edit times") } },
    ) {
        if (t == null) { Text("This tuition was removed."); return@Screen }
        Text("${weekdaysLabel(t.days)} · ${fmtTime(t.startMin)} · ${t.durationMin} min · ${payLabel(t)}", color = Muted)

        val open = sets.firstOrNull { it.status == "OPEN" }
        SectionCard(if (open != null) "Set ${open.number} · started ${fmtDate(open.startDate)}" else "Current set", accent = moduleColor("TUITION")) {
            if (open == null) {
                Text("A new set starts when you mark the next day as done.")
            } else {
                val done = sessions.count { it.setId == open.id && it.status == "DONE" }
                Text(
                    if (open.targetDays > 0) "Day $done of ${open.targetDays}" else "$done days this month",
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                )
                if (open.targetDays > 0) {
                    LinearProgressIndicator(progress = { (done.toFloat() / open.targetDays).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
                var note by remember(open.id) { mutableStateOf(open.note) }
                Field("Note for this whole set", note, { note = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scope.launch { app.repo.saveSetNote(open.id, note) } }) { Text("Save note") }
                    OutlinedButton(onClick = { paying = open }) { Text("Record payment") }
                }
            }
        }

        val unpaid = sets.filter { it.status == "COMPLETE" && it.fee > it.paidAmount }
        if (unpaid.isNotEmpty()) {
            SectionCard("Payment due", container = WarnBg) {
                unpaid.forEach { s ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Line("Set ${s.number}", "Fee ${money(s.fee)} · paid ${money(s.paidAmount)}", modifier = Modifier.weight(1f))
                        Button(onClick = { paying = s }) { Text("Collect ${money(s.fee - s.paidAmount)}") }
                    }
                }
            }
        }

        SectionCard("Days", accent = moduleColor("TUITION")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { addDay = true }) { Text("Add a day") }
                OutlinedButton(onClick = { backfill = true }) { Text("Add past days") }
            }
            if (sessions.isEmpty()) Text("No days yet.", color = Muted)
            sessions.take(40).forEach { s ->
                val label = when (s.status) { "DONE" -> "Done"; "MISSED" -> "Missed · not counted"; else -> "Planned" }
                Line(
                    fmtDate(s.date) + " · " + fmtTime(s.startMin),
                    if (s.note.isBlank()) "Tap to mark, move or add a note" else s.note,
                    label,
                    when (s.status) { "DONE" -> Teal; "MISSED" -> Warn; else -> Muted },
                    Modifier.clickable { editing = s },
                )
                HorizontalDivider()
            }
        }

        SectionCard("Income from this tuition", accent = moduleColor("TUITION")) {
            val total = sets.sumOf { it.paidAmount }
            Text(money(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            sets.filter { it.paidAmount > 0 }.forEach { Line("Set ${it.number}", "Started ${fmtDate(it.startDate)}", money(it.paidAmount)) }
        }
    }

    editing?.let { s -> TuitionSessionDialog(s) { editing = null } }
    if (addDay) {
        var d by remember { mutableStateOf(today()) }
        AlertDialog(
            onDismissRequest = { addDay = false },
            title = { Text("Add a day") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Adds one taught day on the date you pick and counts it as Done.", style = MaterialTheme.typography.bodySmall)
                    DateField("Date", d, { d = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    parseDateOrNull(d)?.let { pd -> scope.launch { app.repo.addExtraTuitionDay(id, pd.toString(), "") } }
                    addDay = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addDay = false }) { Text("Cancel") } },
        )
    }
    if (backfill) {
        var d by remember { mutableStateOf(today().take(8) + "01") }
        AlertDialog(
            onDismissRequest = { backfill = false },
            title = { Text("Add past days") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pick the day you started teaching. Every scheduled day from then until yesterday is added as Done and counted. Afterwards, tap any day you missed and change it.", style = MaterialTheme.typography.bodySmall)
                    DateField("Started teaching on", d, { d = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    parseDateOrNull(d)?.let { pd -> scope.launch { app.repo.backfillTuition(id, pd.toString()) } }
                    backfill = false
                }) { Text("Add days") }
            },
            dismissButton = { TextButton(onClick = { backfill = false }) { Text("Cancel") } },
        )
    }
    paying?.let { s ->
        var amount by remember(s.id) { mutableStateOf(((s.fee - s.paidAmount).coerceAtLeast(0L) / 100).toString()) }
        AlertDialog(
            onDismissRequest = { paying = null },
            title = { Text("Payment received") },
            text = { Field("Amount (৳)", amount, { amount = it }, number = true) },
            confirmButton = {
                TextButton(onClick = {
                    val a = parseMoney(amount)
                    if (a != null) scope.launch { app.repo.payTuitionSet(s.id, a) }
                    paying = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { paying = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun TuitionSessionDialog(s: TuitionSession, onDismiss: () -> Unit) {
    val app = taskerApp()
    val scope = app.ui
    var status by remember { mutableStateOf(s.status) }
    var note by remember { mutableStateOf(s.note) }
    var moving by remember { mutableStateOf(false) }
    if (moving) {
        RescheduleDialog("Reschedule ${fmtDate(s.date)}", s.startMin, onDismiss) { d, m ->
            scope.launch { app.repo.moveTuitionSession(s.id, d, m, note) }
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fmtDate(s.date)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(listOf("PLANNED" to "Planned", "DONE" to "Done", "MISSED" to "Missed"), status) { status = it }
                Field("Note for this day", note, { note = it })
                OutlinedButton(onClick = { moving = true }) { Text("Reschedule to another day") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch { app.repo.markTuitionSession(s.id, status, note) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Everything for one class in one window: status, note for that day, and reschedule. */
@Composable
fun ClassSessionDialog(s: ClassSession, onDismiss: () -> Unit) {
    val app = taskerApp()
    val scope = app.ui
    var status by remember { mutableStateOf(s.status) }
    var note by remember { mutableStateOf(s.note) }
    var moving by remember { mutableStateOf(false) }
    if (moving) {
        RescheduleDialog("Reschedule class", s.startMin, onDismiss) { d, m -> scope.launch { app.repo.moveClassSession(s.id, d, m) } }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Class on ${fmtDate(s.date)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(listOf("PLANNED" to "Planned", "ATTENDED" to "Attended", "MISSED" to "Missed", "CANCELLED" to "Cancelled"), status) { status = it }
                Field("Note for this class", note, { note = it })
                OutlinedButton(onClick = { moving = true }) { Text("Reschedule to another day") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch { app.repo.markClassSession(s.id, status, note) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TuitionEditScreen(nav: NavController, id: Long) {
    val app = taskerApp()
    val scope = app.ui
    var existing by remember { mutableStateOf<Tuition?>(null) }
    var name by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    val days = remember { mutableStateListOf<Int>() }
    var start by remember { mutableStateOf("17:00") }
    var duration by remember { mutableStateOf("90") }
    var payModel by remember { mutableStateOf("SET") }
    var daysPerSet by remember { mutableStateOf("12") }
    var fee by remember { mutableStateOf("") }
    var remind by remember { mutableStateOf("30") }
    var persistent by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    var since by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(id) {
        if (id != 0L) app.dao.getTuition(id)?.let { t ->
            existing = t
            name = t.name; subject = t.subject
            days.clear(); days.addAll(t.days.split(",").mapNotNull { it.toIntOrNull() })
            start = time24(t.startMin); duration = t.durationMin.toString(); payModel = t.payModel
            daysPerSet = t.daysPerSet.toString(); fee = (t.fee / 100).toString(); remind = t.remindBeforeMin.toString()
            persistent = t.persistent; note = t.note
        }
    }

    Screen(if (id == 0L) "New tuition" else "Edit tuition", onBack = { nav.popBackStack() }) {
        Field("Student or batch", name, { name = it })
        Field("Subject", subject, { subject = it })
        Text("Weekly days", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            // Week shown Saturday first.
            listOf(6, 7, 1, 2, 3, 4, 5).forEach { d ->
                FilterChip(
                    selected = d in days,
                    onClick = { if (d in days) days.remove(d) else days.add(d) },
                    label = { Text(WEEKDAYS[d - 1].take(2)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeField("Start time", start, { start = it }, Modifier.weight(1f))
            Field("Minutes long", duration, { duration = it }, Modifier.weight(1f), number = true)
        }
        Text("Payment", style = MaterialTheme.typography.labelLarge)
        Choice(listOf("SET" to "Per set of days", "MONTHLY" to "Monthly", "PER_DAY" to "Per day"), payModel) { payModel = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (payModel == "SET") Field("Days in a set", daysPerSet, { daysPerSet = it }, Modifier.weight(1f), number = true)
            Field("Fee (৳)", fee, { fee = it }, Modifier.weight(1f), number = true)
        }
        Field("Remind me this many minutes before", remind, { remind = it }, number = true)
        SwitchRow("Keep popping up until I respond", persistent, "Every 5 minutes, up to 6 times") { persistent = it }
        DateField("Already teaching since (optional)", since, { since = it }, optional = true)
        Text("If you pick a date, every scheduled day from then until yesterday is added as Done. You can change any day you missed afterwards.", style = MaterialTheme.typography.bodySmall, color = Muted)
        Field("Address, phone or note (optional)", note, { note = it })
        if (error.isNotEmpty()) Text(error, color = Warn)

        Button(
            onClick = {
                val startMin = parseTimeOrNull(start)
                error = when {
                    name.isBlank() -> "Enter the student's name."
                    days.isEmpty() -> "Pick at least one weekly day."
                    startMin == null -> "Start time must look like 17:00."
                    else -> ""
                }
                if (error.isEmpty() && startMin != null) scope.launch {
                    val savedId = app.repo.saveTuition(
                        (existing ?: Tuition(name = "")).copy(
                            name = name.trim(), subject = subject.trim(), days = days.sorted().joinToString(","),
                            startMin = startMin, durationMin = duration.toIntOrNull() ?: 90, payModel = payModel,
                            fee = parseMoney(fee) ?: 0, daysPerSet = (daysPerSet.toIntOrNull() ?: 12).coerceAtLeast(1),
                            remindBeforeMin = remind.toIntOrNull() ?: 30, persistent = persistent, note = note,
                        )
                    )
                    parseDateOrNull(since)?.let { app.repo.backfillTuition(savedId, it.toString()) }
                    nav.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }

        existing?.let { t ->
            ConfirmButton(
                "Stop this tuition (history is kept)",
                "Stop the tuition \"${t.name}\"? It leaves your list and stops reminding you. Past days and income are kept.",
                Modifier.fillMaxWidth(), outlined = true, confirmLabel = "Stop tuition",
            ) { scope.launch { app.repo.archiveTuition(t); nav.popBackStack("schedule", false) } }
        }
    }
}

// ---------------------------------------------------------------- Classes

@Composable
private fun ClassesTab(nav: NavController) {
    val app = taskerApp()
    val scope = app.ui
    val courses by rememberFlow { app.dao.flowCourses() }
    val slots by rememberFlow { app.dao.flowSlots() }
    val sessions by rememberFlow { app.dao.flowClassSessions() }
    var day by rememberSaveable { mutableStateOf(today()) }
    var editing by remember { mutableStateOf<ClassSession?>(null) }
    var moving by remember { mutableStateOf<ClassSession?>(null) }

    val start = date(today())
    Choice((0..6).map { i -> start.plusDays(i.toLong()).let { it.toString() to (WEEKDAYS[it.dayOfWeek.value - 1] + " " + it.dayOfMonth) } }, day) { day = it }

    val todays = sessions.filter { it.date == day && it.status != "MOVED" }.sortedBy { it.startMin }
    if (todays.isEmpty()) Text("No classes on this day.", color = Muted)
    todays.forEach { s ->
        val c = courses.firstOrNull { it.id == s.courseId }
        val slot = slots.firstOrNull { it.id == s.slotId }
        SectionCard(accent = moduleColor("CLASS")) {
            Line(
                listOf(c?.code ?: "", c?.title ?: "").filter { it.isNotBlank() }.joinToString(" · "),
                fmtTime(s.startMin) + (slot?.let { " – " + fmtTime(it.endMin) + (if (it.room.isBlank()) "" else " · Room " + it.room) + " · " + it.type.lowercase() } ?: ""),
                when (s.status) { "ATTENDED" -> "Attended"; "MISSED" -> "Missed"; "CANCELLED" -> "Cancelled"; else -> "" },
                if (s.status == "MISSED") Warn else Teal,
            )
            if (s.note.isNotBlank()) Text(s.note, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { scope.launch { app.repo.markClassSession(s.id, "ATTENDED", s.note) } }) { Text("Attended") }
                OutlinedButton(onClick = { scope.launch { app.repo.markClassSession(s.id, "MISSED", s.note) } }) { Text("Missed") }
                TextButton(onClick = { moving = s }) { Text("Reschedule") }
                TextButton(onClick = { editing = s }) { Text("Note") }
            }
        }
    }

    SectionCard("Courses and attendance", accent = moduleColor("CLASS")) {
        if (courses.isEmpty()) Text("No courses yet.", color = Muted)
        courses.forEach { c ->
            val attended = sessions.count { it.courseId == c.id && it.status == "ATTENDED" }
            val missed = sessions.count { it.courseId == c.id && it.status == "MISSED" }
            val pct = if (attended + missed == 0) null else attended * 100 / (attended + missed)
            Line(
                listOf(c.code, c.title).filter { it.isNotBlank() }.joinToString(" · "),
                slots.filter { it.courseId == c.id }.joinToString(", ") { WEEKDAYS[it.weekday - 1] + " " + fmtTime(it.startMin) },
                if (pct == null) "No data" else "$pct%",
                if (pct != null && pct < c.minAttendance) Warn else Teal,
                Modifier.clickable { nav.navigate("courseEdit/${c.id}") },
            )
            if (pct != null && pct < c.minAttendance) Text("Below your ${c.minAttendance}% limit", color = Warn, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
        }
    }
    Button(onClick = { nav.navigate("courseEdit/0") }, modifier = Modifier.fillMaxWidth()) { Text("Add course") }

    moving?.let { s ->
        RescheduleDialog("Reschedule class", s.startMin, { moving = null }) { d, m -> scope.launch { app.repo.moveClassSession(s.id, d, m) } }
    }

    editing?.let { s ->
        var note by remember(s.id) { mutableStateOf(s.note) }
        var status by remember(s.id) { mutableStateOf(s.status) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Class on ${fmtDate(s.date)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice(listOf("PLANNED" to "Planned", "ATTENDED" to "Attended", "MISSED" to "Missed", "CANCELLED" to "Cancelled"), status) { status = it }
                    Field("Note for this class", note, { note = it })
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { app.repo.markClassSession(s.id, status, note) }; editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun CourseEditScreen(nav: NavController, id: Long) {
    val app = taskerApp()
    val scope = app.ui
    var existing by remember { mutableStateOf<Course?>(null) }
    var code by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var minAtt by remember { mutableStateOf("75") }
    val slots = remember { mutableStateListOf<ClassSlot>() }
    var weekday by remember { mutableStateOf(7) }
    var start by remember { mutableStateOf("10:00") }
    var end by remember { mutableStateOf("11:20") }
    var room by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("THEORY") }
    var since by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(id) {
        if (id != 0L) app.dao.getCourse(id)?.let { c ->
            existing = c
            code = c.code; title = c.title; teacher = c.teacher; minAtt = c.minAttendance.toString()
            slots.clear(); slots.addAll(app.dao.listSlotsOf(id))
        }
    }

    Screen(if (id == 0L) "New course" else "Edit course", onBack = { nav.popBackStack() }) {
        Field("Course code", code, { code = it })
        Field("Course title", title, { title = it })
        Field("Teacher (optional)", teacher, { teacher = it })
        Field("Warn if attendance falls below (%)", minAtt, { minAtt = it }, number = true)

        SectionCard("Weekly class times", accent = moduleColor("CLASS")) {
            if (slots.isEmpty()) Text("Add at least one class time below.", color = Muted)
            slots.toList().forEach { s ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Line(WEEKDAYS[s.weekday - 1] + " " + fmtTime(s.startMin) + " – " + fmtTime(s.endMin), (if (s.room.isBlank()) "" else "Room ${s.room} · ") + s.type.lowercase(), modifier = Modifier.weight(1f))
                    TextButton(onClick = { slots.remove(s) }) { Text("Remove") }
                }
            }
            HorizontalDivider()
            Choice(listOf(6, 7, 1, 2, 3, 4, 5).map { it to WEEKDAYS[it - 1] }, weekday) { weekday = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField("Starts", start, { start = it }, Modifier.weight(1f))
                TimeField("Ends", end, { end = it }, Modifier.weight(1f))
            }
            Field("Room", room, { room = it })
            Choice(listOf("THEORY" to "Theory", "LAB" to "Lab"), type) { type = it }
            OutlinedButton(onClick = {
                val s = parseTimeOrNull(start)
                val e = parseTimeOrNull(end)
                if (s == null || e == null) error = "Times must look like 10:00." else {
                    error = ""
                    slots.add(ClassSlot(courseId = id, weekday = weekday, startMin = s, endMin = e, room = room.trim(), type = type))
                }
            }) { Text("Add this class time") }
        }
        DateField("Classes already running since (optional)", since, { since = it }, optional = true)
        Text("If you pick a date, every class from then until yesterday is added as Attended. Change the ones you missed in the calendar.", style = MaterialTheme.typography.bodySmall, color = Muted)
        if (error.isNotEmpty()) Text(error, color = Warn)

        Button(
            onClick = {
                error = when {
                    code.isBlank() -> "Enter the course code."
                    slots.isEmpty() -> "Add at least one weekly class time."
                    else -> ""
                }
                if (error.isEmpty()) scope.launch {
                    val savedId = app.repo.saveCourse(
                        (existing ?: Course(code = "")).copy(code = code.trim(), title = title.trim(), teacher = teacher.trim(), minAttendance = minAtt.toIntOrNull() ?: 75),
                        slots.toList(),
                    )
                    parseDateOrNull(since)?.let { app.repo.backfillCourse(savedId, it.toString()) }
                    nav.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }

        existing?.let { c ->
            ConfirmButton(
                "Delete course and its history",
                "Delete \"${c.code}\" with all its class times, attendance and notes? This cannot be undone.",
                Modifier.fillMaxWidth(), outlined = true,
            ) { scope.launch { app.repo.deleteCourse(c); nav.popBackStack() } }
        }
    }
}
