package com.nahian.tasker.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nahian.tasker.R
import com.nahian.tasker.TaskerApp
import com.nahian.tasker.bubble.BubbleService
import com.nahian.tasker.data.ClassSession
import com.nahian.tasker.data.Reminder
import com.nahian.tasker.data.TuitionSession
import com.nahian.tasker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

const val ACTION_FIRE = "com.nahian.tasker.FIRE"
const val ACTION_DONE = "com.nahian.tasker.DONE"
const val ACTION_SNOOZE = "com.nahian.tasker.SNOOZE"
const val ACTION_SYNC = "com.nahian.tasker.SYNC"
const val ACTION_MISSED = "com.nahian.tasker.MISSED"

private const val REPEAT_EVERY_MS = 5 * 60_000L
private const val SNOOZE_MS = 10 * 60_000L
private const val EXPIRE_AFTER_MS = 12 * 60 * 60_000L

/**
 * Creates the planned tuition days, class sessions and prayer alerts for the coming days,
 * then registers every pending reminder with the system. Safe to call as often as needed.
 */
object Planner {
    suspend fun sync(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as TaskerApp
        val dao = app.dao
        val now = System.currentTimeMillis()
        val start = LocalDate.now()

        for (t in dao.listActiveTuitions()) {
            val days = t.days.split(",").mapNotNull { it.toIntOrNull() }
            for (i in 0..6) {
                val d = start.plusDays(i.toLong())
                if (d.dayOfWeek.value !in days) continue
                val ds = d.toString()
                if (dao.countTuitionSessions(t.id, ds) > 0) continue
                val sid = dao.insertTuitionSession(TuitionSession(tuitionId = t.id, date = ds, startMin = t.startMin))
                val at = millisOf(ds, t.startMin) - t.remindBeforeMin * 60_000L
                if (at > now) {
                    dao.insertReminder(
                        Reminder(
                            rkey = "TUI:$sid", module = "TUITION", sourceId = sid, title = "Tuition: ${t.name}",
                            body = "${t.subject} at ${fmtTime(t.startMin)}".trim(), fireAt = at,
                            persistent = t.persistent, repeatsLeft = if (t.persistent) 6 else 0,
                        )
                    )
                }
            }
        }

        // When a tuition ends and is still unmarked, ask "did you teach?" with Done / Missed buttons.
        for (s in dao.listPlannedTuitionSessions(start.toString())) {
            val t = dao.getTuition(s.tuitionId) ?: continue
            if (!t.active) continue
            val end = millisOf(s.date, s.startMin) + t.durationMin * 60_000L
            if (end > now) {
                dao.insertReminder(
                    Reminder(
                        rkey = "TUIM:${s.id}", module = "TUITION", sourceId = s.id, title = "Did you teach ${t.name}?",
                        body = "Mark this tuition day as Done or Missed", fireAt = end,
                    )
                )
            }
        }

        val lead = app.prefs.classRemindMin.intValue
        for (slot in dao.listSlots()) {
            val course = dao.getCourse(slot.courseId) ?: continue
            for (i in 0..6) {
                val d = start.plusDays(i.toLong())
                if (d.dayOfWeek.value != slot.weekday) continue
                val ds = d.toString()
                if (dao.countClassSessions(slot.id, ds) > 0) continue
                val sid = dao.insertClassSession(ClassSession(courseId = course.id, slotId = slot.id, date = ds, startMin = slot.startMin))
                val at = millisOf(ds, slot.startMin) - lead * 60_000L
                if (at > now) {
                    val room = if (slot.room.isBlank()) "" else " · Room ${slot.room}"
                    dao.insertReminder(
                        Reminder(
                            rkey = "CLS:$sid", module = "CLASS", sourceId = sid, title = "Class: ${course.code}",
                            body = "${course.title} at ${fmtTime(slot.startMin)}$room".trim(), fireAt = at,
                        )
                    )
                }
            }
        }

        val slotsById = dao.listSlots().associateBy { it.id }
        for (s in dao.listPlannedClassSessions(start.toString())) {
            val c = dao.getCourse(s.courseId) ?: continue
            val endMin = minOf(slotsById[s.slotId]?.endMin ?: (s.startMin + 60), 23 * 60 + 59)
            val end = millisOf(s.date, endMin)
            if (end > now) {
                dao.insertReminder(
                    Reminder(
                        rkey = "CLSM:${s.id}", module = "CLASS", sourceId = s.id, title = "Did you attend ${c.code}?",
                        body = "Mark this class as Attended or Missed", fireAt = end,
                    )
                )
            }
        }

        for (cfg in dao.listPrayerConfigs()) {
            if (!cfg.enabled) continue
            for (i in 0..1) {
                val ds = start.plusDays(i.toLong()).toString()
                val at = millisOf(ds, cfg.timeMin)
                if (at > now) {
                    dao.insertReminder(
                        Reminder(
                            rkey = "PRAYER:$ds:${cfg.prayer}", module = "PRAYER", title = "${cfg.prayer} prayer",
                            body = "Time for ${cfg.prayer} · ${fmtTime(cfg.timeMin)}", fireAt = at,
                        )
                    )
                }
            }
        }

        ReminderScheduler.scheduleAll(context)
    }
}

object ReminderScheduler {
    private fun intent(ctx: Context, action: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, id.toInt(),
            Intent(ctx, AlarmReceiver::class.java).setAction(action).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun actionIntent(ctx: Context, action: String, id: Long): PendingIntent = intent(ctx, action, id)

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    fun set(ctx: Context, id: Long, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = intent(ctx, ACTION_FIRE, id)
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(intent(ctx, ACTION_FIRE, id))
    }

    suspend fun scheduleAll(context: Context) = withContext(Dispatchers.IO) {
        val ctx = context.applicationContext
        val dao = (ctx as TaskerApp).dao
        val now = System.currentTimeMillis()
        for (r in dao.pendingReminders()) {
            if (r.fireAt < now - EXPIRE_AFTER_MS) dao.updateReminder(r.copy(state = "EXPIRED"))
            else set(ctx, r.id, maxOf(r.fireAt, now + 1000))
        }
        // Once a day, shortly after midnight, plan the next days even if the app is never opened.
        val next = LocalDate.now().plusDays(1).atTime(0, 5).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, intent(ctx, ACTION_SYNC, -1))
    }
}

object Notifier {
    private val channels = listOf(
        "TUITION" to "Tuition", "CLASS" to "Classes", "PRAYER" to "Prayer", "TASK" to "To-do", "MONEY" to "Money",
    )

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        channels.forEach { (id, name) -> nm.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)) }
        nm.createNotificationChannel(NotificationChannel("bubble", "Floating bubble", NotificationManager.IMPORTANCE_LOW))
    }

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, r: Reminder) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val doneLabel = if (r.module == "TUITION" || r.module == "CLASS") "OK" else "Done"
        val builder = NotificationCompat.Builder(ctx, if (channels.any { it.first == r.module }) r.module else "TASK")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(r.title)
            .setContentText(r.body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (r.rkey.startsWith("TUIM:") || r.rkey.startsWith("CLSM:")) {
            builder.addAction(0, if (r.rkey.startsWith("TUIM:")) "Done" else "Attended", ReminderScheduler.actionIntent(ctx, ACTION_DONE, r.id))
            builder.addAction(0, "Missed", ReminderScheduler.actionIntent(ctx, ACTION_MISSED, r.id))
        } else {
            builder.addAction(0, doneLabel, ReminderScheduler.actionIntent(ctx, ACTION_DONE, r.id))
            builder.addAction(0, "Snooze 10 min", ReminderScheduler.actionIntent(ctx, ACTION_SNOOZE, r.id))
        }
        NotificationManagerCompat.from(ctx).notify(r.id.toInt(), builder.build())
    }

    /** Lets the user check from Settings that notifications really pop up on this phone. */
    @SuppressLint("MissingPermission")
    fun test(ctx: Context): Boolean {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        val n = NotificationCompat.Builder(ctx, "TASK")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Tasker test notification")
            .setContentText("If this popped up on top, your reminders will too.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()
        return try { NotificationManagerCompat.from(ctx).notify(999_999, n); true } catch (e: SecurityException) { false }
    }

    suspend fun cancelByKey(ctx: Context, dao: com.nahian.tasker.data.AppDao, key: String) {
        dao.reminderIdByKey(key)?.let { cancel(ctx, it) }
    }

    fun cancel(ctx: Context, id: Long) {
        NotificationManagerCompat.from(ctx).cancel(id.toInt())
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val ctx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try { handle(ctx, intent) } catch (e: Exception) { e.printStackTrace() } finally { pending.finish() }
        }
    }

    private suspend fun handle(ctx: Context, intent: Intent) {
        val app = ctx as TaskerApp
        val dao = app.dao
        val id = intent.getLongExtra("id", 0)
        val now = System.currentTimeMillis()
        when (intent.action) {
            ACTION_SYNC -> Planner.sync(ctx)
            ACTION_FIRE -> {
                val r = dao.getReminder(id) ?: return
                if (r.state != "SCHEDULED" && r.state != "SNOOZED") return
                // A "did you teach / attend?" question is pointless once the session is already marked.
                val answered = (r.rkey.startsWith("TUIM:") && dao.getTuitionSession(r.sourceId)?.status != "PLANNED") ||
                    (r.rkey.startsWith("CLSM:") && dao.getClassSession(r.sourceId)?.status != "PLANNED")
                if (answered) { dao.updateReminder(r.copy(state = "DONE")); return }
                Notifier.show(ctx, r)
                if (r.persistent && r.repeatsLeft > 0) {
                    // Keep popping up until the user answers.
                    val next = now + REPEAT_EVERY_MS
                    dao.updateReminder(r.copy(fireAt = next, repeatsLeft = r.repeatsLeft - 1, state = "SCHEDULED"))
                    ReminderScheduler.set(ctx, r.id, next)
                } else {
                    dao.updateReminder(r.copy(state = "FIRED"))
                }
            }
            ACTION_DONE -> {
                val r = dao.getReminder(id) ?: return
                app.repo.reminderDone(r)
            }
            ACTION_MISSED -> {
                val r = dao.getReminder(id) ?: return
                dao.updateReminder(r.copy(state = "DONE"))
                if (r.rkey.startsWith("TUIM:")) dao.getTuitionSession(r.sourceId)?.let { app.repo.markTuitionSession(it.id, "MISSED", it.note) }
                if (r.rkey.startsWith("CLSM:")) dao.getClassSession(r.sourceId)?.let { app.repo.markClassSession(it.id, "MISSED", it.note) }
                Notifier.cancel(ctx, r.id)
            }
            ACTION_SNOOZE -> {
                val r = dao.getReminder(id) ?: return
                val next = now + SNOOZE_MS
                dao.updateReminder(r.copy(fireAt = next, state = "SNOOZED"))
                ReminderScheduler.set(ctx, r.id, next)
                Notifier.cancel(ctx, r.id)
            }
        }
    }
}

/** Restores alarms (and the bubble) after a restart, an app update or a clock change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val ctx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Planner.sync(ctx)
                val app = ctx as TaskerApp
                if (app.prefs.bubbleOn.value && Settings.canDrawOverlays(ctx)) {
                    ContextCompat.startForegroundService(ctx, Intent(ctx, BubbleService::class.java))
                }
            } catch (e: Exception) { e.printStackTrace() } finally { pending.finish() }
        }
    }
}
