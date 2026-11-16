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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.nahian.tasker.data.Reminder
import kotlinx.coroutines.delay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.filled.Person
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
import com.nahian.tasker.core.Vault
import androidx.compose.material.icons.filled.Lock
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
        setContent {
            val skin = Skins.current
            SideEffect {
                window.statusBarColor = skin.bgColor.toArgb()
                window.navigationBarColor = skin.bgColor.toArgb()
                val bars = WindowCompat.getInsetsController(window, window.decorView)
                bars.isAppearanceLightStatusBars = !skin.dark
                bars.isAppearanceLightNavigationBars = !skin.dark
            }
            TaskerTheme {
                // With protection on, nothing of the app is drawn until the password is entered.
                if (Vault.configured.value && Vault.lockOnStart.value && !Vault.unlocked.value) LockScreen() else AppRoot()
            }
        }
    }

    private var stoppedAt = 0L

    override fun onStop() {
        super.onStop()
        stoppedAt = android.os.SystemClock.elapsedRealtime()
    }

    /** Lock again after a minute in the background. */
    override fun onStart() {
        super.onStart()
        if (stoppedAt != 0L && android.os.SystemClock.elapsedRealtime() - stoppedAt > 60_000L) Vault.lock(applicationContext)
    }

    /** Look for a newer build every time the app comes to the front, not only on a cold start. */
    override fun onResume() {
        super.onResume()
        val app = application as com.nahian.tasker.TaskerApp
        app.ui.launch { Updater.check(this@MainActivity, quiet = true) }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val color: Color)

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
        Tab("home", "Home", Icons.Filled.Home, Teal),
        Tab("schedule", "Schedule", Icons.Filled.DateRange, moduleColor("CLASS")),
        Tab("money", "Money", Icons.Filled.ShoppingCart, moduleColor("MONEY")),
        Tab("prayer", "Prayer", Icons.Filled.Star, moduleColor("PRAYER")),
        Tab("todo", "To-do", Icons.Filled.CheckCircle, moduleColor("TASK")),
        Tab("notes", "Notes", Icons.Filled.Edit, NoteColor),
    )
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    Scaffold(
        containerColor = Skins.current.bgColor,
        bottomBar = { if (tabs.any { it.route == route }) FloatingBar(tabs, route, nav) },
    ) { pad ->
        NavHost(
            nav, startDestination = "home", modifier = Modifier.padding(pad),
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 8 } },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 8 } },
            popExitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { it / 8 } },
        ) {
            composable("home") { HomeScreen(nav) }
            composable("schedule") { ScheduleScreen(nav) }
            composable("money") { MoneyScreen(nav) }
            composable("prayer") { PrayerScreen() }
            composable("todo") { TodoScreen(nav) }
            composable("notes") { NotesScreen(nav) }
            composable("vault") { VaultScreen(nav) }
            composable("secretEdit/{id}") { SecretEditScreen(nav, it.longArg("id")) }
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
        accent = Teal,
        icon = Icons.Filled.Home,
        actions = {
            IconButton(onClick = { nav.navigate("vault") }) { Icon(Icons.Filled.Lock, contentDescription = "Vault: passwords and files") }
            IconButton(onClick = { nav.navigate("analytics") }) { Icon(Icons.Filled.Info, contentDescription = "Analytics") }
            IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        },
    ) {
        // The single most important thing right now, with a live countdown.
        val tick by produceState(0) { while (true) { delay(30_000); value = value + 1 } }
        val nowMs = remember(tick, reminders) { System.currentTimeMillis() }
        val soon = reminders.filter { it.fireAt < nowMs + 24 * 60 * 60_000L }
        fun openReminder(r: Reminder) {
            when (r.module) {
                "TUITION" -> openT = tSessions.firstOrNull { it.id == r.sourceId }
                "CLASS" -> openC = cSessions.firstOrNull { it.id == r.sourceId }
                "TASK" -> nav.navigate("taskEdit/${r.sourceId}")
                "PRAYER" -> nav.navigate("prayer")
                else -> nav.navigate("money")
            }
        }
        fun finishReminder(r: Reminder) {
            // A to-do asks for an optional note or follow-up; everything else is done at once.
            scope.launch {
                val task = if (r.module == "TASK") app.dao.getTask(r.sourceId) else null
                if (task != null) doneTask = task else app.repo.reminderDone(r)
            }
        }
        val tToday = tSessions.filter { it.date == day && it.status != "MOVED" }
        val cToday = cSessions.filter { it.date == day && it.status != "MOVED" }
        val doneCount = tToday.count { it.status == "DONE" } + cToday.count { it.status == "ATTENDED" } + logs.count { it.date == day && it.status != "MISSED" }
        HeroCard(
            r = soon.firstOrNull(), nowMs = nowMs, doneCount = doneCount, total = tToday.size + cToday.size + PRAYERS.size,
            onOpen = { openReminder(it) }, onDone = { finishReminder(it) }, onLater = { r -> scope.launch { app.repo.snooze(r) } },
        )

        if (Updater.available.intValue > 0) {
            SectionCard("Update available", container = AccentBg) {
                Text("Build ${Updater.available.intValue} of Tasker is ready. Your data is kept.")
                Button(onClick = { scope.launch { Updater.downloadAndInstall(ctx) } }, enabled = !Updater.busy.value) { Text(if (Updater.busy.value) "Downloading…" else "Update now") }
                if (Updater.status.value.isNotEmpty()) Text(Updater.status.value, color = Muted)
            }
        }

        if (!androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            SectionCard("Notifications are off", container = WarnBg, accent = Warn) {
                Text("Tasker cannot remind you until notifications are allowed for it.")
                Button(onClick = { nav.navigate("settings") }) { Text("Fix in Settings") }
            }
        }

        SectionCard("Today and the next 6 days") {
            DaysAgenda({ openT = it }, { openC = it }, { nav.navigate("taskEdit/${it.id}") })
        }

        if (soon.size > 1) {
            SectionCard("Later") {
                soon.drop(1).take(5).forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { openReminder(r) }) {
                            Tag(r.module)
                            Line(r.title, fmtMillis(r.fireAt) + if (r.body.isNotEmpty()) " · " + r.body else "")
                        }
                        OutlinedButton(onClick = { finishReminder(r) }) { Text("Done") }
                    }
                    RowDivider()
                }
            }
        }

        val nowMin = nowMinutes()
        val dueTuition = tSessions.filter { it.status == "PLANNED" && (it.date < day || (it.date == day && it.startMin <= nowMin)) }
        val dueClass = cSessions.filter { it.status == "PLANNED" && (it.date < day || (it.date == day && it.startMin <= nowMin)) }
        if (dueTuition.isNotEmpty() || dueClass.isNotEmpty()) {
            SectionCard("Needs marking", container = WarnBg, accent = Warn) {
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
        val build = remember { Updater.currentBuild(ctx) }
        val balance = txns.sumOf { if (it.type in listOf("INCOME", "LOAN_IN", "REPAY_IN")) it.amount else -it.amount }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Stat("Spent today", money(spent), Modifier.weight(1f).clickable { nav.navigate("money") })
            Stat("Balance", money(balance), Modifier.weight(1f).clickable { nav.navigate("money") })
        }
        Text("Design", style = MaterialTheme.typography.labelLarge, color = Muted)
        Choice(Skins.all.map { it.id to it.label }, Skins.current.id) { app.prefs.setSkin(it) }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Tasker build $build", color = Muted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = { scope.launch { Updater.check(ctx) } }) { Text("Check for update") }
        }
        if (Updater.available.intValue == 0 && Updater.status.value.isNotEmpty()) Text(Updater.status.value, color = Muted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
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

/** A floating rounded bar: three tabs, one round + button that can add anything, three tabs. */
@Composable
private fun FloatingBar(tabs: List<Tab>, route: String?, nav: NavController) {
    val skin = Skins.current
    var adding by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().background(skin.bgColor).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Surface(
            shape = RoundedCornerShape(32.dp), color = skin.navBg, contentColor = skin.text,
            border = BorderStroke(1.dp, skin.border), shadowElevation = 10.dp, modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.take(3).forEach { TabIcon(it, route == it.route, nav, Modifier.weight(1f)) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(skin.hero).clickable { adding = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, contentDescription = "Add something", tint = skin.onHero) }
                    DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                        listOf(
                            "Expense" to "expenseAdd", "To-do" to "taskEdit/0", "Note" to "noteEdit/0",
                            "Tuition" to "tuitionEdit/0", "Class" to "courseEdit/0", "Loan entry" to "loanAdd",
                            "Password" to "secretEdit/0", "Secure file" to "vault",
                        ).forEach { (label, target) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { adding = false; nav.navigate(target) })
                        }
                    }
                }
                tabs.drop(3).forEach { TabIcon(it, route == it.route, nav, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TabIcon(t: Tab, selected: Boolean, nav: NavController, modifier: Modifier) {
    val skin = Skins.current
    val tint = if (selected) skin.accent else skin.muted
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).clickable {
            nav.navigate(t.route) {
                popUpTo("home")
                launchSingleTop = true
            }
        }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(t.icon, contentDescription = null, tint = tint)
        Text(t.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, color = tint, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun countdown(at: Long, now: Long): String {
    val min = ((at - now) / 60_000L).toInt()
    return when {
        min <= 0 -> "now"
        min < 60 -> "in $min min"
        else -> "in ${min / 60} h ${min % 60} min"
    }
}

/** The big card at the top of Home: what is next, how long until it, and today's progress. */
@Composable
private fun HeroCard(
    r: Reminder?, nowMs: Long, doneCount: Int, total: Int,
    onOpen: (Reminder) -> Unit, onDone: (Reminder) -> Unit, onLater: (Reminder) -> Unit,
) {
    val skin = Skins.current
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape((skin.radius + 4).dp)).background(skin.hero)
            .clickable(enabled = r != null) { if (r != null) onOpen(r) }.padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NEXT UP", color = skin.onHero, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (r != null) {
                    Text(
                        countdown(r.fireAt, nowMs), color = skin.onHero, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(skin.onHero.copy(alpha = 0.18f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(r?.title ?: "You are all caught up", color = skin.onHero, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                if (r == null) "Nothing is due in the next 24 hours." else fmtMillis(r.fireAt) + if (r.body.isNotEmpty()) " · " + r.body else "",
                color = skin.onHero, style = MaterialTheme.typography.bodyMedium,
            )
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { (doneCount.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = skin.onHero, trackColor = skin.onHero.copy(alpha = 0.28f),
                )
                Text("$doneCount of $total done today", color = skin.onHero, style = MaterialTheme.typography.bodySmall)
            }
            if (r != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onDone(r) }, colors = ButtonDefaults.buttonColors(containerColor = skin.onHero, contentColor = skin.heroInk)) { Text("Done") }
                    OutlinedButton(
                        onClick = { onLater(r) }, border = BorderStroke(1.dp, skin.onHero),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = skin.onHero),
                    ) { Text("Later") }
                }
            }
        }
    }
}
