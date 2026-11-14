@file:OptIn(ExperimentalMaterial3Api::class)

package com.nahian.tasker.bubble

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color as AColor
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import com.nahian.tasker.R
import com.nahian.tasker.TaskerApp
import com.nahian.tasker.core.Pending
import com.nahian.tasker.core.PendingItem
import com.nahian.tasker.core.fmtMillis
import com.nahian.tasker.core.parseMoney
import com.nahian.tasker.core.today
import com.nahian.tasker.data.Task
import com.nahian.tasker.ui.Dropdown
import com.nahian.tasker.ui.Field
import com.nahian.tasker.ui.Line
import com.nahian.tasker.ui.RescheduleDialog
import com.nahian.tasker.ui.RowDivider
import com.nahian.tasker.ui.Tag
import com.nahian.tasker.ui.moduleColor
import com.nahian.tasker.ui.moduleLabel
import com.nahian.tasker.ui.MainActivity
import com.nahian.tasker.ui.TaskerTheme
import com.nahian.tasker.ui.rememberFlow
import com.nahian.tasker.ui.taskerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Keeps a small draggable bubble on top of other apps. Under it, one coloured badge per kind
 * of item (tuition, class, prayer, to-do) shows how many are waiting for an answer.
 * Tapping the bubble opens the quick panel; tapping a badge opens only that kind.
 */
class BubbleService : Service() {
    private var container: LinearLayout? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, "bubble")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Tasker bubble is on")
            .setContentText("Tap to open Tasker. Turn the bubble off in Settings.")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(9001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(9001, n)
        if (Settings.canDrawOverlays(this)) addBubble() else stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun openQuick(module: String?) {
        val i = Intent(this, QuickActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (module != null) i.putExtra("module", module)
        startActivity(i)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        val app = application as TaskerApp
        val prefs = app.prefs
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = (56 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val main = TextView(this).apply {
            text = "T"
            gravity = Gravity.CENTER
            setTextColor(AColor.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            contentDescription = "Tasker quick actions"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF0F6E66.toInt())
                setStroke((2 * resources.displayMetrics.density).toInt(), AColor.WHITE)
            }
        }
        box.addView(main, LinearLayout.LayoutParams(size, size))
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.bubbleX
            y = prefs.bubbleY
        }
        main.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f
            var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x; startY = lp.y; touchX = e.rawX; touchY = e.rawY; moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - touchX).toInt()
                        val dy = (e.rawY - touchY).toInt()
                        if (abs(dx) > 12 || abs(dy) > 12) moved = true
                        lp.x = startX + dx
                        lp.y = startY + dy
                        wm.updateViewLayout(box, lp)
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            openQuick(null)
                        } else {
                            // Snap to the nearest side edge and remember the position.
                            val w = resources.displayMetrics.widthPixels
                            lp.x = if (lp.x + size / 2 < w / 2) 0 else w - size
                            wm.updateViewLayout(box, lp)
                            prefs.bubbleX = lp.x
                            prefs.bubbleY = lp.y
                        }
                    }
                }
                return true
            }
        })
        wm.addView(box, lp)
        container = box

        // Refresh the badges whenever the data changes, and once a minute as time passes.
        scope.launch {
            val dao = app.dao
            val ticker = flow { while (true) { emit(Unit); delay(60_000) } }
            merge(
                dao.flowTuitionSessions().map { }, dao.flowClassSessions().map { }, dao.flowTasks().map { },
                dao.flowPrayerConfigs().map { }, dao.flowPrayerLogs(today()).map { }, ticker,
            ).collect {
                try { renderBadges(Pending.load(dao)) } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun renderBadges(items: List<PendingItem>) {
        val box = container ?: return
        while (box.childCount > 1) box.removeViewAt(1)
        val d = resources.displayMetrics.density
        val s = (30 * d).toInt()
        listOf("TUITION", "CLASS", "PRAYER", "TASK").forEach { m ->
            val n = items.count { it.module == m }
            if (n > 0) {
                val badge = TextView(this).apply {
                    text = if (n > 9) "9+" else n.toString()
                    gravity = Gravity.CENTER
                    setTextColor(AColor.WHITE)
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    contentDescription = "$n ${moduleLabel(m)} waiting"
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(moduleColor(m).toArgb())
                        setStroke((2 * d).toInt(), AColor.WHITE)
                    }
                    setOnClickListener { openQuick(m) }
                }
                box.addView(badge, LinearLayout.LayoutParams(s, s).apply { topMargin = (4 * d).toInt() })
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        container?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        container = null
        super.onDestroy()
    }
}

/** The small panel that opens over whatever app is on screen. */
class QuickActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TaskerTheme {
                Box(
                    Modifier.fillMaxSize().background(Color(0x66000000)).clickable { finish() }.imePadding().padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth().clickable { },
                    ) {
                        val module = intent.getStringExtra("module")
                        if (module != null) FocusPanel(module) { finish() } else QuickPanel(
                            onOpenApp = {
                                startActivity(Intent(this@QuickActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                finish()
                            },
                            onClose = { finish() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPanel(onOpenApp: () -> Unit, onClose: () -> Unit) {
    val app = taskerApp()
    val scope = app.ui
    val reminders by rememberFlow { app.dao.flowPendingReminders() }
    val categories by rememberFlow { app.dao.flowCategories() }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var catId by remember { mutableStateOf<Long?>(null) }
    var todo by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tasker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenApp) { Text("Open app") }
            TextButton(onClick = onClose) { Text("Close") }
        }

        val soon = reminders.filter { it.fireAt < System.currentTimeMillis() + 24 * 60 * 60_000L }.take(3)
        if (soon.isEmpty()) Text("Nothing due in the next 24 hours.", style = MaterialTheme.typography.bodyMedium)
        soon.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.title, fontWeight = FontWeight.SemiBold)
                    Text(fmtMillis(r.fireAt), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { scope.launch { app.repo.reminderDone(r) } }) { Text("Done") }
            }
        }

        Text("QUICK EXPENSE", style = MaterialTheme.typography.labelMedium)
        Field("Amount (৳)", amount, { amount = it }, number = true)
        Dropdown("Category", categories.map { it.id as Long? to it.name }, catId) { catId = it }
        Field("Note (optional)", note, { note = it })
        Button(
            onClick = {
                val a = parseMoney(amount)
                if (a == null) message = "Enter an amount first." else scope.launch {
                    app.repo.addExpense(a, catId ?: categories.firstOrNull()?.id, today(), note)
                    amount = ""; note = ""; message = "Expense saved."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add expense") }

        Text("QUICK TO-DO", style = MaterialTheme.typography.labelMedium)
        Field("What to do", todo, { todo = it })
        Button(
            onClick = {
                if (todo.isBlank()) message = "Type the to-do first." else scope.launch {
                    app.repo.saveTask(Task(title = todo.trim(), dueDate = today()))
                    todo = ""; message = "To-do saved for today."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add to-do") }

        if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.primary)
    }
}

/** Opened from a coloured badge: shows only that kind of waiting item, with its answer buttons. */
@Composable
private fun FocusPanel(module: String, onClose: () -> Unit) {
    val app = taskerApp()
    val scope = app.ui
    val tuitions by rememberFlow { app.dao.flowTuitions() }
    val tSessions by rememberFlow { app.dao.flowTuitionSessions() }
    val courses by rememberFlow { app.dao.flowCourses() }
    val cSessions by rememberFlow { app.dao.flowClassSessions() }
    val configs by rememberFlow { app.dao.flowPrayerConfigs() }
    val logs by rememberFlow { app.dao.flowPrayerLogs(today()) }
    val tasks by rememberFlow { app.dao.flowTasks() }
    val tick by produceState(0) { while (true) { delay(30_000); value = value + 1 } }
    val items = remember(tuitions, tSessions, courses, cSessions, configs, logs, tasks, tick) {
        Pending.compute(tuitions, tSessions, courses, cSessions, configs, logs, tasks)
    }.filter { it.module == module }
    var move by remember { mutableStateOf<PendingItem?>(null) }
    val day = today()

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Tag(module)
                Text(
                    when (module) { "TUITION" -> "Tuitions to mark"; "CLASS" -> "Classes to mark"; "PRAYER" -> "Prayers to mark"; else -> "To-dos due" },
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                )
            }
            TextButton(onClick = onClose) { Text("Close") }
        }
        RowDivider()
        if (items.isEmpty()) Text("Nothing is waiting here. All caught up.")
        items.forEach { item ->
            Line(item.title, item.sub)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (module) {
                    "TUITION" -> {
                        Button(onClick = { scope.launch { app.repo.markTuitionSession(item.id, "DONE", "") } }) { Text("Done") }
                        OutlinedButton(onClick = { scope.launch { app.repo.markTuitionSession(item.id, "MISSED", "") } }) { Text("Missed") }
                        OutlinedButton(onClick = { move = item }) { Text("Reschedule") }
                    }
                    "CLASS" -> {
                        Button(onClick = { scope.launch { app.repo.markClassSession(item.id, "ATTENDED", "") } }) { Text("Attended") }
                        OutlinedButton(onClick = { scope.launch { app.repo.markClassSession(item.id, "MISSED", "") } }) { Text("Missed") }
                        OutlinedButton(onClick = { scope.launch { app.repo.markClassSession(item.id, "CANCELLED", "") } }) { Text("Off") }
                        OutlinedButton(onClick = { move = item }) { Text("Reschedule") }
                    }
                    "PRAYER" -> {
                        Button(onClick = { scope.launch { app.repo.setPrayer(day, item.key, "PRAYED") } }) { Text("Prayed") }
                        OutlinedButton(onClick = { scope.launch { app.repo.setPrayer(day, item.key, "JAMAAT") } }) { Text("Jamaat") }
                        OutlinedButton(onClick = { scope.launch { app.repo.setPrayer(day, item.key, "LATE") } }) { Text("Late") }
                        OutlinedButton(onClick = { scope.launch { app.repo.setPrayer(day, item.key, "MISSED") } }) { Text("Missed") }
                    }
                    else -> {
                        Button(onClick = { scope.launch { app.dao.getTask(item.id)?.let { app.repo.completeTask(it) } } }) { Text("Done") }
                    }
                }
            }
            RowDivider()
        }
    }

    move?.let { item ->
        RescheduleDialog(if (module == "TUITION") "Reschedule tuition day" else "Reschedule class", item.startMin, { move = null }) { d, m ->
            scope.launch { if (module == "TUITION") app.repo.moveTuitionSession(item.id, d, m, "") else app.repo.moveClassSession(item.id, d, m) }
        }
    }
}
