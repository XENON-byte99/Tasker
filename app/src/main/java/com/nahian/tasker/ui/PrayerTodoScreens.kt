@file:OptIn(ExperimentalMaterial3Api::class)

package com.nahian.tasker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nahian.tasker.core.PRAYERS
import com.nahian.tasker.core.WEEKDAYS
import com.nahian.tasker.core.date
import com.nahian.tasker.core.fmtDate
import com.nahian.tasker.core.fmtTime
import com.nahian.tasker.core.parseDateOrNull
import com.nahian.tasker.core.parseTimeOrNull
import com.nahian.tasker.core.time24
import com.nahian.tasker.core.today
import com.nahian.tasker.data.Task
import com.nahian.tasker.data.TaskCategory
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Prayer

private val PRAYER_STATUS = listOf("PRAYED" to "Prayed", "JAMAAT" to "With jamaat", "LATE" to "Late (qaza)", "MISSED" to "Missed")

@Composable
fun PrayerScreen() {
    val app = taskerApp()
    val scope = app.ui
    val day = today()
    val weekAgo = date(day).minusDays(6).toString()
    val configs by rememberFlow { app.dao.flowPrayerConfigs() }
    val logs by rememberFlow(weekAgo) { app.dao.flowPrayerLogs(weekAgo) }
    var picking by remember { mutableStateOf<String?>(null) }
    var editTimes by remember { mutableStateOf(false) }

    Screen("Prayer", actions = { TextButton(onClick = { editTimes = !editTimes }) { Text(if (editTimes) "Close" else "Times and alerts") } }) {
        Text(fmtDate(day), color = Muted)

        if (editTimes) {
            val times = remember { mutableStateMapOf<String, String>() }
            val enabled = remember { mutableStateMapOf<String, Boolean>() }
            LaunchedEffect(configs) {
                configs.forEach { if (it.prayer !in times) { times[it.prayer] = time24(it.timeMin); enabled[it.prayer] = it.enabled } }
            }
            SectionCard("Times and alerts") {
                Text("Type each time in 24-hour form. A reminder fires at that time.", style = MaterialTheme.typography.bodySmall, color = Muted)
                configs.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(c.prayer, times[c.prayer] ?: "", { times[c.prayer] = it }, Modifier.weight(1f))
                        Switch(checked = enabled[c.prayer] ?: true, onCheckedChange = { enabled[c.prayer] = it })
                    }
                }
                Button(onClick = {
                    scope.launch {
                        app.repo.savePrayerConfigs(configs.map { c ->
                            c.copy(timeMin = parseTimeOrNull(times[c.prayer] ?: "") ?: c.timeMin, enabled = enabled[c.prayer] ?: c.enabled)
                        })
                        editTimes = false
                    }
                }) { Text("Save times") }
            }
        }

        SectionCard("Today") {
            configs.forEach { c ->
                val status = logs.firstOrNull { it.date == day && it.prayer == c.prayer }?.status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Line(c.prayer, fmtTime(c.timeMin) + if (c.enabled) "" else " · alert off", modifier = Modifier.weight(1f))
                    if (status == null) {
                        Button(onClick = { scope.launch { app.repo.setPrayer(day, c.prayer, "PRAYED") } }) { Text("Prayed") }
                        TextButton(onClick = { picking = c.prayer }) { Text("More") }
                    } else {
                        OutlinedButton(onClick = { picking = c.prayer }) {
                            Text(PRAYER_STATUS.firstOrNull { it.first == status }?.second ?: status)
                        }
                    }
                }
                HorizontalDivider()
            }
        }

        SectionCard("Last 7 days") {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                (6 downTo 0).forEach { back ->
                    val d = date(day).minusDays(back.toLong())
                    val n = logs.count { it.date == d.toString() && it.status != "MISSED" }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (back == 0) "Today" else WEEKDAYS[d.dayOfWeek.value - 1], style = MaterialTheme.typography.bodySmall, color = Muted)
                        Text("$n/5", fontWeight = FontWeight.Bold, color = if (n == 5) Teal else if (n >= 3) MaterialTheme.colorScheme.onSurface else Warn)
                    }
                }
            }
            val missed = logs.count { it.status == "MISSED" }
            if (missed > 0) Text("$missed marked missed this week, to make up.", color = Warn)
        }
    }

    picking?.let { prayer ->
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(prayer) },
            text = {
                Column {
                    PRAYER_STATUS.forEach { (value, label) ->
                        TextButton(onClick = { scope.launch { app.repo.setPrayer(day, prayer, value) }; picking = null }) { Text(label) }
                    }
                    TextButton(onClick = { scope.launch { app.repo.setPrayer(day, prayer, null) }; picking = null }) { Text("Clear") }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text("Close") } },
        )
    }
}

// ---------------------------------------------------------------- To-do

private fun categoryLabel(c: TaskCategory, all: List<TaskCategory>): String {
    val parent = all.firstOrNull { it.id == c.parentId }
    return if (parent == null) c.name else parent.name + " › " + c.name
}

@Composable
private fun TaskRow(t: Task, sub: String, nav: NavController) {
    val app = taskerApp()
    val scope = app.ui
    val overdue = t.completedAt == null && t.dueDate != null && t.dueDate < today()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(
            checked = t.completedAt != null,
            onCheckedChange = { scope.launch { if (t.completedAt == null) app.repo.completeTask(t) else app.repo.reopenTask(t) } },
        )
        Column(Modifier.weight(1f).clickable { nav.navigate("taskEdit/${t.id}") }.padding(vertical = 6.dp)) {
            Text(
                t.title, fontWeight = FontWeight.SemiBold,
                textDecoration = if (t.completedAt != null) TextDecoration.LineThrough else null,
            )
            val due = when {
                t.dueDate == null -> "No date"
                else -> fmtDate(t.dueDate) + (t.dueMin?.let { " " + fmtTime(it) } ?: "")
            }
            val repeat = when (t.repeatRule) { "DAILY" -> " · daily"; "WEEKLY" -> " · weekly"; "MONTHLY" -> " · monthly"; else -> "" }
            Text(
                listOf(if (overdue) "Overdue · $due" else due + repeat, sub).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = if (overdue) Warn else Muted,
            )
        }
    }
}

@Composable
fun TodoScreen(nav: NavController) {
    val app = taskerApp()
    val scope = app.ui
    val tasks by rememberFlow { app.dao.flowTasks() }
    val cats by rememberFlow { app.dao.flowTaskCategories() }
    var filter by rememberSaveable { mutableStateOf("TODAY") }
    var addingCat by remember { mutableStateOf(false) }
    val day = today()
    val open = tasks.filter { it.completedAt == null }
    fun label(t: Task) = cats.firstOrNull { it.id == t.categoryId }?.let { categoryLabel(it, cats) } ?: ""

    Screen("To-do") {
        Choice(
            listOf(
                "TODAY" to "Today ${open.count { it.dueDate == day }}",
                "UPCOMING" to "Upcoming",
                "OVERDUE" to "Overdue ${open.count { it.dueDate != null && it.dueDate < day }}",
                "CATEGORY" to "By category",
                "DONE" to "Done",
            ),
            filter,
        ) { filter = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { nav.navigate("taskEdit/0") }) { Text("Add task") }
            OutlinedButton(onClick = { addingCat = true }) { Text("Add category") }
        }

        if (filter == "CATEGORY") {
            val mains = cats.filter { it.parentId == null }
            mains.forEach { main ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(main.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        ConfirmButton("Delete", "Delete the category \"${main.name}\"? Its tasks are kept without a category.") { scope.launch { app.repo.deleteTaskCategory(main) } }
                    }
                    open.filter { it.categoryId == main.id }.forEach { TaskRow(it, "", nav) }
                    cats.filter { it.parentId == main.id }.forEach { sub ->
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val n = open.count { it.categoryId == sub.id }
                            Text("${sub.name.uppercase()} · $n", style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.weight(1f))
                            ConfirmButton("Delete", "Delete the sub-category \"${sub.name}\"? Its tasks are kept without a category.") { scope.launch { app.repo.deleteTaskCategory(sub) } }
                        }
                        open.filter { it.categoryId == sub.id }.forEach { TaskRow(it, "", nav) }
                    }
                }
            }
            val loose = open.filter { it.categoryId == null }
            if (loose.isNotEmpty()) SectionCard("No category") { loose.forEach { TaskRow(it, "", nav) } }
            if (mains.isEmpty() && loose.isEmpty()) Text("Add a category such as University, then sub-categories under it.", color = Muted)
        } else {
            val list = when (filter) {
                "TODAY" -> open.filter { it.dueDate == day }
                "UPCOMING" -> open.filter { it.dueDate == null || it.dueDate > day }
                "OVERDUE" -> open.filter { it.dueDate != null && it.dueDate < day }
                else -> tasks.filter { it.completedAt != null }.sortedByDescending { it.completedAt }.take(60)
            }
            SectionCard {
                if (list.isEmpty()) Text("Nothing here.", color = Muted)
                list.forEach { TaskRow(it, label(it), nav) }
            }
        }
    }

    if (addingCat) {
        var name by remember { mutableStateOf("") }
        var parent by remember { mutableStateOf<Long?>(null) }
        AlertDialog(
            onDismissRequest = { addingCat = false },
            title = { Text("New category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Name", name, { name = it })
                    Dropdown(
                        "Under",
                        listOf<Pair<Long?, String>>(null to "Nothing (main category)") + cats.filter { it.parentId == null }.map { (it.id as Long?) to it.name },
                        parent,
                    ) { parent = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) scope.launch { app.dao.insertTaskCategory(TaskCategory(name = n, parentId = parent)) }
                    addingCat = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addingCat = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun TaskEditScreen(nav: NavController, id: Long) {
    val app = taskerApp()
    val scope = app.ui
    val cats by rememberFlow { app.dao.flowTaskCategories() }
    var existing by remember { mutableStateOf<Task?>(null) }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var catId by remember { mutableStateOf<Long?>(null) }
    var dueDate by remember { mutableStateOf(today()) }
    var dueTime by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("NONE") }
    var priority by remember { mutableStateOf(1) }
    var persistent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(id) {
        if (id != 0L) app.dao.getTask(id)?.let { t ->
            existing = t
            title = t.title; notes = t.notes; catId = t.categoryId
            dueDate = t.dueDate ?: ""; dueTime = t.dueMin?.let { time24(it) } ?: ""
            repeat = t.repeatRule; priority = t.priority; persistent = t.persistent
        }
    }

    Screen(if (id == 0L) "New task" else "Edit task", onBack = { nav.popBackStack() }) {
        Field("What to do", title, { title = it })
        Dropdown(
            "Category",
            listOf<Pair<Long?, String>>(null to "None") + cats.map { (it.id as Long?) to categoryLabel(it, cats) },
            catId,
        ) { catId = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Due date (YYYY-MM-DD)", dueDate, { dueDate = it }, Modifier.weight(1.4f))
            Field("Time (24h)", dueTime, { dueTime = it }, Modifier.weight(1f))
        }
        Text("Repeat", style = MaterialTheme.typography.labelLarge)
        Choice(listOf("NONE" to "Never", "DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly"), repeat) { repeat = it }
        Text("Priority", style = MaterialTheme.typography.labelLarge)
        Choice(listOf(0 to "Low", 1 to "Normal", 2 to "High"), priority) { priority = it }
        SwitchRow("Keep popping up until done", persistent, "Needs a due date and time") { persistent = it }
        Field("Notes (optional)", notes, { notes = it })
        if (error.isNotEmpty()) Text(error, color = Warn)

        Button(
            onClick = {
                val d = if (dueDate.isBlank()) null else parseDateOrNull(dueDate)
                val m = if (dueTime.isBlank()) null else parseTimeOrNull(dueTime)
                error = when {
                    title.isBlank() -> "Type what to do."
                    dueDate.isNotBlank() && d == null -> "Date must look like 2026-11-15."
                    dueTime.isNotBlank() && m == null -> "Time must look like 21:00."
                    else -> ""
                }
                if (error.isEmpty()) scope.launch {
                    app.repo.saveTask(
                        (existing ?: Task(title = "")).copy(
                            title = title.trim(), notes = notes.trim(), categoryId = catId, dueDate = d?.toString(),
                            dueMin = m, repeatRule = repeat, priority = priority, persistent = persistent,
                        )
                    )
                    nav.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }

        existing?.let { t ->
            ConfirmButton("Delete task", "Delete \"${t.title}\"? This cannot be undone.", Modifier.fillMaxWidth(), outlined = true) {
                scope.launch { app.repo.deleteTask(t); nav.popBackStack() }
            }
        }
    }
}
