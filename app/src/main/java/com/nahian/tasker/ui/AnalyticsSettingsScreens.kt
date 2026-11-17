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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings as SettingsIcon
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
import com.nahian.tasker.core.Notifier
import com.nahian.tasker.core.ReminderScheduler
import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
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

    Screen("Analytics", onBack = { nav.popBackStack() }, backLabel = "Home") {
        Segmented(listOf("WEEK" to "Last 7 days", "MONTH" to "This month", "YEAR" to "This year"), period) { period = it }

        SectionCard {
            Eyebrow("Income and expenses")
            val months = (5 downTo 0).map { now.minusMonths(it.toLong()).toString().take(7) }
            val rows = months.map { m ->
                Triple(
                    m,
                    txns.filter { it.type == "INCOME" && it.date.startsWith(m) }.sumOf { it.amount },
                    txns.filter { it.type == "EXPENSE" && it.date.startsWith(m) }.sumOf { it.amount },
                )
            }
            val top = rows.maxOf { maxOf(it.second, it.third) }.coerceAtLeast(1L)
            val inColor = moduleColor("TUITION")
            val outColor = Color(0xFFE0A15E)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(inColor, RoundedCornerShape(2.dp))); Text("In", style = MaterialTheme.typography.labelMedium)
                Box(Modifier.size(10.dp).background(outColor, RoundedCornerShape(2.dp))); Text("Out", style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                rows.forEach { (_, inc, exp) ->
                    Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f).fillMaxHeight((inc.toFloat() / top).coerceIn(0.02f, 1f)).background(inColor, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
                        Box(Modifier.weight(1f).fillMaxHeight((exp.toFloat() / top).coerceIn(0.02f, 1f)).background(outColor, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEachIndexed { i, (m, _, _) ->
                    Text(
                        LocalDate.parse("$m-01").format(DateTimeFormatter.ofPattern("MMM", Locale.US)),
                        style = MaterialTheme.typography.labelSmall, color = if (i == rows.size - 1) Skins.current.text else Muted,
                        fontWeight = if (i == rows.size - 1) FontWeight.Bold else FontWeight.Normal,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                }
            }
            val last = rows.last()
            Line("This month: in ${money(last.second)}, out ${money(last.third)}", trailing = money(last.second - last.third), trailingColor = if (last.second >= last.third) Teal else Warn)
        }

        // Two tiles, the attendance card, then two more tiles, as in the Analytics design.
        val doneT = tSessions.count { it.status == "DONE" && it.date >= from }
        val missedT = tSessions.count { it.status == "MISSED" && it.date >= from }
        val due = sets.filter { it.status == "COMPLETE" }.sumOf { maxOf(0L, it.fee - it.paidAmount) }
        val owe = loans.filter { it.direction == "TAKEN" }.sumOf { l -> maxOf(0L, l.principal - reps.filter { it.loanId == l.id }.sumOf { it.amount }) }
        val owed = loans.filter { it.direction == "GIVEN" }.sumOf { l -> maxOf(0L, l.principal - reps.filter { it.loanId == l.id }.sumOf { it.amount }) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Stat("Tuition days", "$doneT taught", Modifier.weight(1f), sub = "$missedT missed · ${money(due)} due", subColor = if (missedT > 0 || due > 0) Warn else Muted)
            Stat("Loans you owe", money(owe), Modifier.weight(1f), sub = "Owed to you ${money(owed)}", subColor = Teal)
        }

        SectionCard {
            Eyebrow("Class attendance")
            if (courses.isEmpty()) Text("No courses yet.", color = Muted)
            courses.forEach { c ->
                val a = cSessions.count { it.courseId == c.id && it.status == "ATTENDED" && it.date >= from }
                val m = cSessions.count { it.courseId == c.id && it.status == "MISSED" && it.date >= from }
                val pct = if (a + m == 0) null else a * 100 / (a + m)
                val low = pct != null && pct < c.minAttendance
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(c.code, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.width(84.dp))
                    LinearProgressIndicator(
                        progress = { (pct ?: 0) / 100f }, modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = if (low) Warn else moduleColor("TUITION"),
                    )
                    Text(if (pct == null) "–" else "$pct%", fontWeight = FontWeight.Bold, color = if (low) Warn else Skins.current.text, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.width(48.dp))
                }
            }
        }

        val prayed = logs.count { it.status != "MISSED" }
        val prayerPct = if (days <= 0) 0 else (prayed * 100 / (days * 5)).coerceAtMost(100)
        val fromMs = date(from).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val tasksDone = tasks.count { (it.completedAt ?: 0L) >= fromMs }
        val tasksOpen = tasks.count { it.completedAt == null }
        val overdue = tasks.count { it.completedAt == null && it.dueDate != null && it.dueDate < today() }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Stat("Prayers marked", "$prayerPct%", Modifier.weight(1f), sub = "${logs.count { it.status == "MISSED" }} missed · ${logs.count { it.status == "JAMAAT" }} with jamaat")
            Stat("Tasks done", "$tasksDone of ${tasksDone + tasksOpen}", Modifier.weight(1f), sub = if (overdue > 0) "$overdue overdue" else "none overdue", subColor = if (overdue > 0) Warn else Muted)
        }

        SectionCard {
            Eyebrow("Spending by category")
            val byCat = txns.filter { it.type == "EXPENSE" && it.date >= from }.groupBy { it.categoryId }
                .mapValues { e -> e.value.sumOf { it.amount } }.toList().sortedByDescending { it.second }
            val top = byCat.firstOrNull()?.second ?: 0L
            if (byCat.isEmpty()) Text("No expenses in this period.", color = Muted)
            byCat.forEach { (catId, sum) ->
                Line(categories.firstOrNull { it.id == catId }?.name ?: "Uncategorised", trailing = money(sum))
                LinearProgressIndicator(progress = { if (top == 0L) 0f else sum.toFloat() / top }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = moduleColor("MONEY"))
            }
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

    Screen("Settings", onBack = { nav.popBackStack() }, backLabel = "Home") {
        // Profile card, as at the top of the Settings design.
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Skins.current.hero), contentAlignment = Alignment.Center) {
                    Text("T", color = Skins.current.onHero, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                }
                Line(
                    "Tasker",
                    "Build ${Updater.currentBuild(ctx)} · ${Skins.current.label} design · " + (if (com.nahian.tasker.core.Vault.configured.value) "password on" else "no password yet"),
                    modifier = Modifier.weight(1f),
                )
            }
            Text("Everything is stored on this phone and works without internet.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }

        SectionCard("Design") {
            Text("Pick the look of the whole app. You can change it any time.", style = MaterialTheme.typography.bodySmall, color = Muted)
            Segmented(Skins.all.map { it.id to it.label }, Skins.current.id) { prefs.setSkin(it) }
        }

        ProtectionSettings(nav)

        SectionCard("Notifications") {
            val notifOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
            val exact = ReminderScheduler.canExact(ctx)
            val batteryOk = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
            Text(if (notifOn) "✓ Notifications are allowed" else "✗ Notifications are blocked for Tasker", color = if (notifOn) Teal else Warn, fontWeight = FontWeight.SemiBold)
            Text(if (exact) "✓ Exact timing is allowed" else "✗ Exact timing is off, so reminders can be late", color = if (exact) Teal else Warn, fontWeight = FontWeight.SemiBold)
            Text(if (batteryOk) "✓ Battery saving will not stop reminders" else "✗ Battery saving can stop reminders while the app is closed", color = if (batteryOk) Teal else Warn, fontWeight = FontWeight.SemiBold)
            var testMsg by remember { mutableStateOf("") }
            Button(onClick = { testMsg = if (Notifier.test(ctx)) "Sent. It should pop up at the top now." else "Blocked. Allow notifications for Tasker first." }) { Text("Send a test notification") }
            if (testMsg.isNotEmpty()) Text(testMsg, color = Muted)
            OutlinedButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }) { Text("Open Tasker notification settings") }
            Text("If the test only appears in the notification list and does not pop up on top, turn on \"Pop on screen\" (also called floating or banner notifications) for Tasker on that settings page.", style = MaterialTheme.typography.bodySmall, color = Muted)
            Text("These reminders work whether the bubble is on or off.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }

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
            // A fallback that does not depend on the in-app check at all.
            OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.APK))) }) { Text("Download latest in browser") }
            Text("If the check ever fails, this downloads the newest Tasker directly. Open the downloaded file to install it over this one; your data is kept.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }

        SectionCard("Account and sync") {
            Text("Everything is stored on this phone and works without internet.")
            Text("Sign-in, backup and sync are planned for a later version.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}
