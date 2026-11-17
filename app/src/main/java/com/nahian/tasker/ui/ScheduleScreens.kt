@file:OptIn(ExperimentalMaterial3Api::class)

package com.nahian.tasker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
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
    Screen("Schedule", onAdd = { nav.navigate(if (tab == "TUITIONS") "tuitionEdit/0" else "courseEdit/0") }) {
        Segmented(listOf("TUITIONS" to "Tuitions", "CLASSES" to "Classes"), tab) { tab = it }
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
    DayStrip(day, { d -> sessions.count { it.date == d && it.status != "MOVED" } }) { day = it }
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
                    if (t.daysPerSet <= 20) SegBar(done, t.daysPerSet, moduleColor("TUITION")) else LinearProgressIndicator(progress = { (done.toFloat() / t.daysPerSet).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
                if (unpaid > 0) Text("Collect ${money(unpaid)}", color = Warn, fontWeight = FontWeight.SemiBold)
                if (next != null) Text("Next: ${fmtDate(next.date)}, ${fmtTime(next.startMin)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
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
        title = if (t == null) "Tuition" else listOf(t.name, t.subject).filter { it.isNotBlank() }.joinToString(" · "),
        onBack = { nav.popBackStack() }, backLabel = "Tuitions",
        sub = if (t == null) null else "${weekdaysLabel(t.days)} · ${fmtTime(t.startMin)} · ${t.durationMin} min",
        actions = { OutlinedButton(onClick = { nav.navigate("tuitionEdit/$id") }) { Text("Edit times") } },
    ) {
        if (t == null) { Text("This tuition was removed."); return@Screen }
        val skin = Skins.current
        val open = sets.firstOrNull { it.status == "OPEN" }
        var noteEdit by remember { mutableStateOf(false) }
        val doneInOpen = if (open == null) 0 else sessions.count { it.setId == open.id && it.status == "DONE" }

        // Current set: header line, big day counter, one segment per day, set note.
        SectionCard {
            if (open == null) {
                Eyebrow("Current set")
                Text("A new set starts when you mark the next day as done.")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Set ${open.number} · started ${fmtDate(open.startDate)}", Modifier.weight(1f))
                    Text(
                        if (open.targetDays > 0) "${money(open.fee)} due at day ${open.targetDays}" else "${money(open.fee)} a month",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Muted,
                    )
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Day $doneInOpen", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
                    if (open.targetDays > 0) Text("of ${open.targetDays}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Muted, modifier = Modifier.padding(bottom = 6.dp))
                }
                if (open.targetDays in 1..20) SegBar(doneInOpen, open.targetDays, moduleColor("TUITION"))
                else if (open.targetDays > 20) LinearProgressIndicator(progress = { (doneInOpen.toFloat() / open.targetDays).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(skin.chipBg).clickable { noteEdit = true }.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Eyebrow("Set note")
                    Text(open.note.ifBlank { "Tap to write a note for this whole set" }, style = MaterialTheme.typography.bodyMedium, color = if (open.note.isBlank()) Muted else skin.text)
                }
                OutlinedButton(onClick = { paying = open }) { Text("Record payment") }
            }
        }
        if (noteEdit && open != null) {
            var note by remember(open.id) { mutableStateOf(open.note) }
            AlertDialog(
                onDismissRequest = { noteEdit = false },
                title = { Text("Note for set ${open.number}") },
                text = { Field("Note for this whole set", note, { note = it }) },
                confirmButton = { TextButton(onClick = { scope.launch { app.repo.saveSetNote(open.id, note) }; noteEdit = false }) { Text("Save") } },
                dismissButton = { TextButton(onClick = { noteEdit = false }) { Text("Cancel") } },
            )
        }

        val unpaid = sets.filter { it.status == "COMPLETE" && it.fee > it.paidAmount }
        if (unpaid.isNotEmpty()) {
            SectionCard(container = WarnBg) {
                Eyebrow("Payment due", color = Warn)
                unpaid.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Line("Set ${s.number}", "Fee ${money(s.fee)} · paid ${money(s.paidAmount)}", modifier = Modifier.weight(1f))
                        Button(onClick = { paying = s }) { Text("Collect ${money(s.fee - s.paidAmount)}") }
                    }
                }
            }
        }

        // Days: date on the left, status and that day's note on the right.
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Days", Modifier.weight(1f))
                TextButton(onClick = { addDay = true }) { Text("Add a day") }
                TextButton(onClick = { backfill = true }) { Text("Add past days") }
            }
            if (sessions.isEmpty()) Text("No days yet.", color = Muted)
            val shown = sessions.take(40)
            shown.forEachIndexed { index, s ->
                val number = if (s.status == "DONE") sessions.filter { it.setId == s.setId && it.status == "DONE" }.sortedBy { it.date }.indexOfFirst { it.id == s.id } + 1 else 0
                Row(Modifier.fillMaxWidth().clickable { editing = s }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(fmtDate(s.date), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(92.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            when (s.status) { "DONE" -> "Done · day $number"; "MISSED" -> "Missed · not counted"; else -> "Planned · " + fmtTime(s.startMin) },
                            fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
                            color = when (s.status) { "DONE" -> moduleColor("TUITION"); "MISSED" -> Warn; else -> Muted },
                        )
                        Text(s.note.ifBlank { "Add a note" }, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                if (index < shown.size - 1) RowDivider()
            }
        }

        // Two money tiles, as at the bottom of the design.
        val earned = if (open != null && open.targetDays > 0) open.fee * doneInOpen / open.targetDays else 0L
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Stat("Received so far", money(sets.sumOf { it.paidAmount }), Modifier.weight(1f))
            Stat("Earned in this set", money(earned), Modifier.weight(1f))
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

    val saveAction = remember { Ref<() -> Unit>({}) }
    FormScreen(if (id == 0L) "New tuition" else "Edit tuition", onCancel = { nav.popBackStack() }, save = saveAction) {
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
        Segmented(listOf("SET" to "Per set", "MONTHLY" to "Monthly", "PER_DAY" to "Per day"), payModel) { payModel = it }
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

        saveAction.value = {
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
        }

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
    DayStrip(day, { d -> sessions.count { it.date == d && it.status != "MOVED" } }) { day = it }

    val todays = sessions.filter { it.date == day && it.status != "MOVED" }.sortedBy { it.startMin }
    if (todays.isEmpty()) Text("No classes on this day.", color = Muted)
    todays.forEach { s ->
        val c = courses.firstOrNull { it.id == s.courseId }
        val slot = slots.firstOrNull { it.id == s.slotId }
        val attended = sessions.count { it.courseId == s.courseId && it.status == "ATTENDED" }
        val missedCount = sessions.count { it.courseId == s.courseId && it.status == "MISSED" }
        val pct = if (attended + missedCount == 0) null else attended * 100 / (attended + missedCount)
        val low = pct != null && c != null && pct < c.minAttendance
        val past = s.date < today() || (s.date == today() && s.startMin <= com.nahian.tasker.core.nowMinutes())
        SectionCard {
            // Time range on the left, status on the right, as in the routine design.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    fmtTime(s.startMin) + (slot?.let { " – " + fmtTime(it.endMin) } ?: ""),
                    color = moduleColor("CLASS"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                )
                Text(
                    when (s.status) { "ATTENDED" -> "Attended"; "MISSED" -> "Missed"; "CANCELLED" -> "Cancelled"; else -> if (past) "Needs marking" else "Planned" },
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    color = when (s.status) { "ATTENDED" -> moduleColor("TUITION"); "MISSED" -> Warn; "CANCELLED" -> Muted; else -> if (past) Warn else Muted },
                )
            }
            Text(listOf(c?.code ?: "", c?.title ?: "").filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "Class" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                listOf(
                    slot?.type?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Rescheduled",
                    if (slot != null && slot.room.isNotBlank()) "Room " + slot.room else "",
                    if (pct != null) "Attendance $pct%" + (if (low) ", below your ${c?.minAttendance}% limit" else "") else "",
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = if (low) Warn else Muted, fontWeight = if (low) FontWeight.SemiBold else FontWeight.Normal,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                Button(onClick = { scope.launch { app.repo.markClassSession(s.id, "ATTENDED", s.note) } }, contentPadding = pad, modifier = Modifier.weight(1f)) { Text("Attended", maxLines = 1, softWrap = false) }
                OutlinedButton(onClick = { scope.launch { app.repo.markClassSession(s.id, "MISSED", s.note) } }, contentPadding = pad, modifier = Modifier.weight(1f)) { Text("Missed", maxLines = 1, softWrap = false) }
                OutlinedButton(onClick = { scope.launch { app.repo.markClassSession(s.id, "CANCELLED", s.note) } }, contentPadding = pad, modifier = Modifier.weight(1f)) { Text("Cancelled", maxLines = 1, softWrap = false) }
            }
            if (s.note.isNotBlank()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Skins.current.chipBg).clickable { editing = s }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Eyebrow("Today's note")
                    Text(s.note, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row {
                TextButton(onClick = { editing = s }) { Text(if (s.note.isBlank()) "Add note" else "Edit note") }
                TextButton(onClick = { moving = s }) { Text("Reschedule") }
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

    val saveAction = remember { Ref<() -> Unit>({}) }
    FormScreen(if (id == 0L) "New course" else "Edit course", onCancel = { nav.popBackStack() }, save = saveAction) {
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
            Segmented(listOf("THEORY" to "Theory", "LAB" to "Lab"), type) { type = it }
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

        saveAction.value = {
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
        }

        existing?.let { c ->
            ConfirmButton(
                "Delete course and its history",
                "Delete \"${c.code}\" with all its class times, attendance and notes? This cannot be undone.",
                Modifier.fillMaxWidth(), outlined = true,
            ) { scope.launch { app.repo.deleteCourse(c); nav.popBackStack() } }
        }
    }
}
