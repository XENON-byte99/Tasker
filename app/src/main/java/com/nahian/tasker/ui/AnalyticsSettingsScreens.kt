@file:OptIn(ExperimentalMaterial3Api::class)

package com.nahian.tasker.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.nahian.tasker.bubble.BubbleService
import com.nahian.tasker.core.ReminderScheduler
import com.nahian.tasker.core.Updater
import androidx.compose.material3.Button
import kotlinx.coroutines.launch
import com.nahian.tasker.core.date
import com.nahian.tasker.core.money
import com.nahian.tasker.core.today
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AnalyticsScreen(nav: NavController) {
    val app = taskerApp()
    val txns by rememberFlow { app.dao.flowTxns() }
    val categories by rememberFlow { app.dao.flowCategories() }
    val tSessions by rememberFlow { app.dao.flowTuitionSessions() }
    val sets by rememberFlow { app.dao.flowSets() }
    val courses by rememberFlow { app.dao.flowCourses() }
    val cSessions by rememberFlow { app.dao.flowClassSessions() }
    val tasks by rememberFlow { app.dao.flowTasks() }
    val loans by rememberFlow { app.dao.flowLoans() }
    val reps by rememberFlow { app.dao.flowRepayments() }
    var period by rememberSaveable { mutableStateOf("MONTH") }

    val now = LocalDate.now()
    val from = when (period) { "WEEK" -> now.minusDays(6); "MONTH" -> now.withDayOfMonth(1); else -> now.withDayOfYear(1) }.toString()
    val logs by rememberFlow(from) { app.dao.flowPrayerLogs(from) }
    val days = (date(today()).toEpochDay() - date(from).toEpochDay() + 1).toInt()

    Screen("Analytics", onBack = { nav.popBackStack() }) {
        Choice(listOf("WEEK" to "Last 7 days", "MONTH" to "This month", "YEAR" to "This year"), period) { period = it }

        val income = txns.filter { it.type == "INCOME" && it.date >= from }.sumOf { it.amount }
        val expense = txns.filter { it.type == "EXPENSE" && it.date >= from }.sumOf { it.amount }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Stat("Income", money(income), Modifier.weight(1f), Teal)
            Stat("Expenses", money(expense), Modifier.weight(1f))
        }
        Stat("Saved in this period", money(income - expense), Modifier.fillMaxWidth(), if (income >= expense) Teal else Warn)

        SectionCard("Income and expenses · last 6 months") {
            val months = (5 downTo 0).map { now.minusMonths(it.toLong()).toString().take(7) }
            val rows = months.map { m ->
                Triple(
                    m,
                    txns.filter { it.type == "INCOME" && it.date.startsWith(m) }.sumOf { it.amount },
                    txns.filter { it.type == "EXPENSE" && it.date.startsWith(m) }.sumOf { it.amount },
                )
            }
            val top = rows.maxOf { maxOf(it.second, it.third) }.coerceAtLeast(1L)
            rows.forEach { (m, inc, exp) ->
                val name = LocalDate.parse("$m-01").format(DateTimeFormatter.ofPattern("MMM", Locale.US))
                Text("$name · in ${money(inc)} · out ${money(exp)}", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { inc.toFloat() / top }, modifier = Modifier.fillMaxWidth(), color = Teal)
                LinearProgressIndicator(progress = { exp.toFloat() / top }, modifier = Modifier.fillMaxWidth(), color = Color(0xFFE0A15E))
            }
        }

        SectionCard("Spending by category") {
            val byCat = txns.filter { it.type == "EXPENSE" && it.date >= from }.groupBy { it.categoryId }
                .mapValues { e -> e.value.sumOf { it.amount } }.toList().sortedByDescending { it.second }
            val top = byCat.firstOrNull()?.second ?: 0L
            if (byCat.isEmpty()) Text("No expenses in this period.", color = Muted)
            byCat.forEach { (catId, sum) ->
                Line(categories.firstOrNull { it.id == catId }?.name ?: "Uncategorised", trailing = money(sum))
                LinearProgressIndicator(progress = { if (top == 0L) 0f else sum.toFloat() / top }, modifier = Modifier.fillMaxWidth())
            }
        }

        SectionCard("Tuition") {
            val done = tSessions.count { it.status == "DONE" && it.date >= from }
            val missed = tSessions.count { it.status == "MISSED" && it.date >= from }
            val due = sets.filter { it.status == "COMPLETE" }.sumOf { maxOf(0L, it.fee - it.paidAmount) }
            Line("Days taught", trailing = "$done")
            Line("Days missed", trailing = "$missed", trailingColor = if (missed > 0) Warn else Color.Unspecified)
            Line("Fees still to collect", trailing = money(due), trailingColor = if (due > 0) Warn else Color.Unspecified)
        }

        SectionCard("Class attendance") {
            if (courses.isEmpty()) Text("No courses yet.", color = Muted)
            courses.forEach { c ->
                val a = cSessions.count { it.courseId == c.id && it.status == "ATTENDED" && it.date >= from }
                val m = cSessions.count { it.courseId == c.id && it.status == "MISSED" && it.date >= from }
                val pct = if (a + m == 0) null else a * 100 / (a + m)
                Line(c.code, "$a attended · $m missed", if (pct == null) "No data" else "$pct%", if (pct != null && pct < c.minAttendance) Warn else Teal)
                if (pct != null) LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth(), color = if (pct < c.minAttendance) Color(0xFFB4530A) else Teal)
            }
        }

        SectionCard("Prayer") {
            val prayed = logs.count { it.status != "MISSED" }
            val total = days * 5
            Line("Prayers marked", "Out of $total in this period", "$prayed", Teal)
            Line("With jamaat", trailing = "${logs.count { it.status == "JAMAAT" }}")
            Line("Late", trailing = "${logs.count { it.status == "LATE" }}")
            Line("Missed", trailing = "${logs.count { it.status == "MISSED" }}", trailingColor = Warn)
        }

        SectionCard("To-do and loans") {
            val fromMs = date(from).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            Line("Tasks completed", trailing = "${tasks.count { (it.completedAt ?: 0L) >= fromMs }}")
            Line("Tasks open", trailing = "${tasks.count { it.completedAt == null }}")
            Line("Tasks overdue", trailing = "${tasks.count { it.completedAt == null && it.dueDate != null && it.dueDate < today() }}", trailingColor = Warn)
            val owe = loans.filter { it.direction == "TAKEN" }.sumOf { l -> maxOf(0L, l.principal - reps.filter { it.loanId == l.id }.sumOf { it.amount }) }
            val owed = loans.filter { it.direction == "GIVEN" }.sumOf { l -> maxOf(0L, l.principal - reps.filter { it.loanId == l.id }.sumOf { it.amount }) }
            Line("You owe", trailing = money(owe), trailingColor = if (owe > 0) Warn else Color.Unspecified)
            Line("Owed to you", trailing = money(owed), trailingColor = Teal)
        }
    }
}

@Composable
fun SettingsScreen(nav: NavController) {
    val app = taskerApp()
    val ctx = LocalContext.current
    val prefs = app.prefs
    var share by remember { mutableStateOf(prefs.repayShare.intValue.toString()) }
    var classLead by remember { mutableStateOf(prefs.classRemindMin.intValue.toString()) }
    var hint by remember { mutableStateOf("") }

    Screen("Settings", onBack = { nav.popBackStack() }) {
        SectionCard("Floating bubble") {
            SwitchRow("Show bubble over other apps", prefs.bubbleOn.value, "Tap it anywhere for quick expense and to-do entry") { on ->
                if (on) {
                    if (!Settings.canDrawOverlays(ctx)) {
                        hint = "Allow \"Display over other apps\" for Tasker, then switch this on again."
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)))
                    } else {
                        prefs.setBubbleOn(true)
                        ContextCompat.startForegroundService(ctx, Intent(ctx, BubbleService::class.java))
                        hint = ""
                    }
                } else {
                    prefs.setBubbleOn(false)
                    ctx.stopService(Intent(ctx, BubbleService::class.java))
                }
            }
            if (hint.isNotEmpty()) Text(hint, color = Warn)
        }

        SectionCard("Reminders that arrive on time") {
            Text(
                if (ReminderScheduler.canExact(ctx)) "Exact alarms are allowed." else "Exact alarms are off, so reminders can be late.",
                color = if (ReminderScheduler.canExact(ctx)) Teal else Warn, fontWeight = FontWeight.SemiBold,
            )
            if (Build.VERSION.SDK_INT >= 31) {
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
                }) { Text("Open alarm permission") }
            }
            OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) {
                Text("Open battery settings")
            }
            Text("Set Tasker to \"Unrestricted\" or \"Don't optimise\" so the phone does not stop its reminders.", style = MaterialTheme.typography.bodySmall, color = Muted)
            Field("Class reminder, minutes before", classLead, {
                classLead = it
                it.toIntOrNull()?.let { v -> prefs.setClassRemindMin(v.coerceIn(0, 240)) }
            }, number = true)
        }

        SectionCard("Loans") {
            Field("Share of each income set aside for loans (%)", share, {
                share = it
                it.toIntOrNull()?.let { v -> prefs.setRepayShare(v.coerceIn(0, 100)) }
            }, number = true)
            Text("Set aside now: ${money(prefs.repayPool.longValue)}", color = Muted)
        }

        SectionCard("App updates") {
            Text("Installed: build ${Updater.currentBuild(ctx)}", fontWeight = FontWeight.SemiBold)
            Text("Tasker checks for a new build each time it opens. Updating needs internet; everything else works offline.", style = MaterialTheme.typography.bodySmall, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { app.ui.launch { Updater.check(ctx) } }) { Text("Check now") }
                if (Updater.available.intValue > 0) {
                    Button(onClick = { app.ui.launch { Updater.downloadAndInstall(ctx) } }, enabled = !Updater.busy.value) { Text("Update now") }
                }
            }
            if (Updater.status.value.isNotEmpty()) Text(Updater.status.value, color = Muted)
        }

        SectionCard("Account and sync") {
            Text("Everything is stored on this phone and works without internet.")
            Text("Sign-in, backup and sync are planned for a later version.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}
