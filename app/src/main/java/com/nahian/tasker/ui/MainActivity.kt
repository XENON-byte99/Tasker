@file:OptIn(ExperimentalMaterial3Api::class)

package com.nahian.tasker.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
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
import com.nahian.tasker.core.fmtMillis
import com.nahian.tasker.core.fmtTime
import com.nahian.tasker.core.money
import com.nahian.tasker.core.nowMinutes
import com.nahian.tasker.core.today
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
    }
    val tabs = listOf(
        Tab("home", "Home", Icons.Filled.Home),
        Tab("schedule", "Schedule", Icons.Filled.DateRange),
        Tab("money", "Money", Icons.Filled.ShoppingCart),
        Tab("prayer", "Prayer", Icons.Filled.Star),
        Tab("todo", "To-do", Icons.Filled.CheckCircle),
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
                            label = { Text(t.label) },
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
            composable("analytics") { AnalyticsScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("tuition/{id}") { TuitionDetailScreen(nav, it.longArg("id")) }
            composable("tuitionEdit/{id}") { TuitionEditScreen(nav, it.longArg("id")) }
            composable("courseEdit/{id}") { CourseEditScreen(nav, it.longArg("id")) }
            composable("expenseAdd") { ExpenseAddScreen(nav) }
            composable("loanAdd") { LoanAddScreen(nav) }
            composable("taskEdit/{id}") { TaskEditScreen(nav, it.longArg("id")) }
        }
    }
}

@Composable
fun HomeScreen(nav: NavController) {
    val app = taskerApp()
    val scope = rememberCoroutineScope()
    val day = today()
    val reminders by rememberFlow { app.dao.flowPendingReminders() }
    val tuitions by rememberFlow { app.dao.flowTuitions() }
    val tSessions by rememberFlow { app.dao.flowTuitionSessions() }
    val courses by rememberFlow { app.dao.flowCourses() }
    val cSessions by rememberFlow { app.dao.flowClassSessions() }
    val txns by rememberFlow { app.dao.flowTxns() }
    val logs by rememberFlow(day) { app.dao.flowPrayerLogs(day) }

    Screen(
        title = "Today",
        actions = {
            IconButton(onClick = { nav.navigate("analytics") }) { Icon(Icons.Filled.Info, contentDescription = "Analytics") }
            IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        },
    ) {
        SectionCard("Next up") {
            val soon = reminders.filter { it.fireAt < System.currentTimeMillis() + 24 * 60 * 60_000L }.take(5)
            if (soon.isEmpty()) Text("Nothing due in the next 24 hours.", color = Muted)
            soon.forEach { r ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Line(r.title, fmtMillis(r.fireAt) + if (r.body.isNotEmpty()) " · " + r.body else "", modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { scope.launch { app.repo.reminderDone(r) } }) { Text("Done") }
                }
            }
        }

        val nowMin = nowMinutes()
        val dueTuition = tSessions.filter { it.status == "PLANNED" && (it.date < day || (it.date == day && it.startMin <= nowMin)) }
        val dueClass = cSessions.filter { it.status == "PLANNED" && (it.date < day || (it.date == day && it.startMin <= nowMin)) }
        if (dueTuition.isNotEmpty() || dueClass.isNotEmpty()) {
            SectionCard("Needs marking", container = Color(0xFFFFF6EC)) {
                dueTuition.take(5).forEach { s ->
                    val t = tuitions.firstOrNull { it.id == s.tuitionId }
                    Line(t?.name ?: "Tuition", "Tuition · ${com.nahian.tasker.core.fmtDate(s.date)} · ${fmtTime(s.startMin)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scope.launch { app.repo.markTuitionSession(s.id, "DONE", s.note) } }) { Text("Done") }
                        OutlinedButton(onClick = { scope.launch { app.repo.markTuitionSession(s.id, "MISSED", s.note) } }) { Text("Missed") }
                    }
                }
                dueClass.take(5).forEach { s ->
                    val c = courses.firstOrNull { it.id == s.courseId }
                    Line(c?.code ?: "Class", "Class · ${com.nahian.tasker.core.fmtDate(s.date)} · ${fmtTime(s.startMin)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scope.launch { app.repo.markClassSession(s.id, "ATTENDED", s.note) } }) { Text("Attended") }
                        OutlinedButton(onClick = { scope.launch { app.repo.markClassSession(s.id, "MISSED", s.note) } }) { Text("Missed") }
                        OutlinedButton(onClick = { scope.launch { app.repo.markClassSession(s.id, "CANCELLED", s.note) } }) { Text("Off") }
                    }
                }
            }
        }

        val prayedToday = logs.filter { it.date == day && it.status != "MISSED" }.map { it.prayer }
        SectionCard("Prayers today · ${prayedToday.size} of ${PRAYERS.size}") {
            Text(PRAYERS.joinToString("   ") { (if (it in prayedToday) "✓ " else "○ ") + it })
            OutlinedButton(onClick = { nav.navigate("prayer") }) { Text("Mark prayers") }
        }

        val spent = txns.filter { it.type == "EXPENSE" && it.date == day }.sumOf { it.amount }
        val balance = txns.sumOf { if (it.type in listOf("INCOME", "LOAN_IN", "REPAY_IN")) it.amount else -it.amount }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Stat("Spent today", money(spent), Modifier.weight(1f))
            Stat("Balance", money(balance), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { nav.navigate("expenseAdd") }) { Text("Add expense") }
            OutlinedButton(onClick = { nav.navigate("taskEdit/0") }) { Text("Add to-do") }
        }
    }
}
