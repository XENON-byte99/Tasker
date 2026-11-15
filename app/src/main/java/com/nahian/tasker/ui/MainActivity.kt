@file:OptIn(ExperimentalMaterial3Api::class)

package com.nahian.tasker.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nahian.tasker.core.PRAYERS
import com.nahian.tasker.core.Planner
import com.nahian.tasker.core.Updater
import com.nahian.tasker.core.fmtMillis
import com.nahian.tasker.core.fmtTime
import com.nahian.tasker.core.money
import com.nahian.tasker.core.nowMinutes
import com.nahian.tasker.core.today
import com.nahian.tasker.data.ClassSession
import com.nahian.tasker.data.TuitionSession
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TaskerTheme { AppRoot() } }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private fun NavBackStackEntry.longArg(name: String): Long = arguments?.getString(name)?.toLongOrNull() ?: 0L

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        Planner.sync(ctx)
        Updater.check(ctx, quiet = true)
    }
    val tabs = listOf(
        Tab("home", "Home", Icons.Filled.Home),
        Tab("schedule", "Schedule", Icons.Filled.DateRange),
        Tab("money", "Money", Icons.Filled.ShoppingCart),
        Tab("prayer", "Prayer", Icons.Filled.Star),
        Tab("todo", "To-do", Icons.Filled.CheckCircle),
        Tab("notes", "Notes", Icons.Filled.Edit),
    )
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar(containerColor = Color.White) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo("home")
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label, maxLines = 1, softWrap = false, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(nav) }
            composable("schedule") { ScheduleScreen(nav) }
            composable("money") { MoneyScreen(nav) }
            composable("prayer") { PrayerScreen() }
            composable("todo") { TodoScreen(nav) }
            composable("notes") { NotesScreen(nav) }
            composable("noteEdit/{id}") { NoteEditScreen(nav, it.longArg("id")) }
            composable("analytics") { AnalyticsScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("calendar/{kind}") { CalendarScreen(nav, it.arguments?.getString("kind") ?: "TUITION") }
            composable("tuition/{id}") { TuitionDetailScreen(nav, it.longArg("id")) }
            composable("tuitionEdit/{id}") { TuitionEditScreen(nav, it.longArg("id")) }
            composable("courseEdit/{id}") { CourseEditScreen(nav, it.longArg("id")) }
            composable("expenseAdd") { ExpenseAddScreen(nav) }
            composable("expenseEdit/{id}") { ExpenseAddScreen(nav, it.longArg("id")) }
            composable("loanAdd") { LoanAddScreen(nav) }
            composable("taskEdit/{id}") { TaskEditScreen(nav, it.longArg("id")) }
        }
    }
}

@Composable
fun HomeScreen(nav: NavController) {
    val app = taskerApp()
    val scope = app.ui
    val ctx = LocalContext.current
    val day = today()
    val reminders by rememberFlow { app.dao.flowPendingReminders() }
    val tuitions by rememberFlow { app.dao.flowTuitions() }
    val tSessions by rememberFlow { app.dao.flowTuitionSessions() }
    val courses by rememberFlow { app.dao.flowCourses() }
    val cSessions by rememberFlow { app.dao.flowClassSessions() }
    val txns by rememberFlow { app.dao.flowTxns() }
    val logs by rememberFlow(day) { app.dao.flowPrayerLogs(day) }

    var moveT by remember { mutableStateOf<TuitionSession?>(null) }
    var moveC by remember { mutableStateOf<ClassSession?>(null) }
    var openT by remember { mutableStateOf<TuitionSession?>(null) }
    var openC by remember { mutableStateOf<ClassSession?>(null) }
    var doneTask by remember { mutableStateOf<com.nahian.tasker.data.Task?>(null) }

    Screen(
        title = "Today",
        actions = {
            IconButton(onClick = { nav.navigate("analytics") }) { Icon(Icons.Filled.Info, contentDescription = "Analytics") }
            IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        },
    ) {
        if (Updater.available.intValue > 0) {
            SectionCard("Update available", container = Color(0xFFEAF4F2)) {
                Text("Build ${Updater.available.intValue} of Tasker is ready. Your data is kept.")
                Button(onClick = { scope.launch { Updater.downloadAndInstall(ctx) } }, enabled = !Updater.busy.value) { Text(if (Updater.busy.value) "Downloading…" else "Update now") }
                if (Updater.status.value.isNotEmpty()) Text(Updater.status.value, color = Muted)
            }
        }

        if (!androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            SectionCard("Notifications are off", container = Color(0xFFFFF6EC), accent = Color(0xFFB4530A)) {
                Text("Tasker cannot remind you until notifications are allowed for it.")
                Button(onClick = { nav.navigate("settings") }) { Text("Fix in Settings") }
            }
        }

        SectionCard("Today and the next 6 days") {
            DaysAgenda({ openT = it }, { openC = it }, { nav.navigate("taskEdit/${it.id}") })
        }

        SectionCard("Next up") {
            val soon = reminders.filter { it.fireAt < System.currentTimeMillis() + 24 * 60 * 60_000L }.take(5)
            if (soon.isEmpty()) Text("Nothing due in the next 24 hours.", color = Muted)
            soon.forEach { r ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    // Tapping an item opens the place where it can be edited, noted or deleted.
                    Column(
                        Modifier.weight(1f).clickable {
                            when (r.module) {
                                "TUITION" -> openT = tSessions.firstOrNull { it.id == r.sourceId }
                                "CLASS" -> openC = cSessions.firstOrNull { it.id == r.sourceId }
                                "TASK" -> nav.navigate("taskEdit/${r.sourceId}")
                                "PRAYER" -> nav.navigate("prayer")
                                else -> nav.navigate("money")
                            }
                        }
                    ) {
                        Tag(r.module)
                        Line(r.title, fmtMillis(r.fireAt) + if (r.body.isNotEmpty()) " · " + r.body else "")
                    }
                    OutlinedButton(onClick = {
                        // A to-do asks for an optional note or follow-up; everything else is done at once.
                        scope.launch {
                            val task = if (r.module == "TASK") app.dao.getTask(r.sourceId) else null
                            if (task != null) doneTask = task else app.repo.reminderDone(r)
                        }
                    }) { Text("Done") }
                }
                RowDivider()
            }
        }

        val nowMin = nowMinutes()
        val dueTuition = tSessions.filter { it.status == "PLANNED" && (it.date < day || (it.date == day && it.startMin <= nowMin)) }
        val dueClass = cSessions.filter { it.status == "PLANNED" && (it.date < day || (it.date == day && it.startMin <= nowMin)) }
        if (dueTuition.isNotEmpty() || dueClass.isNotEmpty()) {
            SectionCard("Needs marking", container = Color(0xFFFFF6EC), accent = Color(0xFFB4530A)) {
                dueTuition.take(5).forEach { s ->
                    val t = tuitions.firstOrNull { it.id == s.tuitionId }
                    Tag("TUITION")
                    Line(t?.name ?: "Tuition", "Tuition · ${com.nahian.tasker.core.fmtDate(s.date)} · ${fmtTime(s.startMin)}" + if (s.note.isBlank()) "" else " · " + s.note, modifier = Modifier.clickable { nav.navigate("tuition/${s.tuitionId}") })
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scope.launch { app.repo.markTuitionSession(s.id, "DONE", s.note) } }) { Text("Done") }
                        OutlinedButton(onClick = { scope.launch { app.repo.markTuitionSession(s.id, "MISSED", s.note) } }) { Text("Missed") }
                        OutlinedButton(onClick = { moveT = s }) { Text("Reschedule") }
                        OutlinedButton(onClick = { openT = s }) { Text("Note") }
                    }
                    RowDivider()
                }
                dueClass.take(5).forEach { s ->
                    val c = courses.firstOrNull { it.id == s.courseId }
                    Tag("CLASS")
                    Line(c?.code ?: "Class", "Class · ${com.nahian.tasker.core.fmtDate(s.date)} · ${fmtTime(s.startMin)}" + if (s.note.isBlank()) "" else " · " + s.note, modifier = Modifier.clickable { openC = s })
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scope.launch { app.repo.markClassSession(s.id, "ATTENDED", s.note) } }) { Text("Attended") }
                        OutlinedButton(onClick = { scope.launch { app.repo.markClassSession(s.id, "MISSED", s.note) } }) { Text("Missed") }
                        OutlinedButton(onClick = { scope.launch { app.repo.markClassSession(s.id, "CANCELLED", s.note) } }) { Text("Off") }
                        OutlinedButton(onClick = { moveC = s }) { Text("Reschedule") }
                        OutlinedButton(onClick = { openC = s }) { Text("Note") }
                    }
                    RowDivider()
                }
            }
        }

        val prayedToday = logs.filter { it.date == day && it.status != "MISSED" }.map { it.prayer }
        SectionCard("Prayers today · ${prayedToday.size} of ${PRAYERS.size}", accent = moduleColor("PRAYER")) {
            Tag("PRAYER")
            Text(PRAYERS.joinToString("   ") { (if (it in prayedToday) "✓ " else "○ ") + it })
            OutlinedButton(onClick = { nav.navigate("prayer") }) { Text("Mark prayers") }
        }

        val spent = txns.filter { it.type == "EXPENSE" && it.date == day }.sumOf { it.amount }
        val balance = txns.sumOf { if (it.type in listOf("INCOME", "LOAN_IN", "REPAY_IN")) it.amount else -it.amount }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Stat("Spent today", money(spent), Modifier.weight(1f).clickable { nav.navigate("money") })
            Stat("Balance", money(balance), Modifier.weight(1f).clickable { nav.navigate("money") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { nav.navigate("expenseAdd") }) { Text("Add expense") }
            OutlinedButton(onClick = { nav.navigate("taskEdit/0") }) { Text("Add to-do") }
            OutlinedButton(onClick = { nav.navigate("noteEdit/0") }) { Text("Add note") }
        }
    }

    doneTask?.let { t -> CompleteTaskDialog(t) { doneTask = null } }
    openT?.let { s -> TuitionSessionDialog(s) { openT = null } }
    openC?.let { s -> ClassSessionDialog(s) { openC = null } }
    moveT?.let { s ->
        RescheduleDialog("Reschedule tuition day", s.startMin, { moveT = null }) { d, m -> scope.launch { app.repo.moveTuitionSession(s.id, d, m, "") } }
    }
    moveC?.let { s ->
        RescheduleDialog("Reschedule class", s.startMin, { moveC = null }) { d, m -> scope.launch { app.repo.moveClassSession(s.id, d, m) } }
    }
}
