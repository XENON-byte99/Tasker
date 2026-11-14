package com.nahian.tasker.core

import com.nahian.tasker.data.AppDao
import com.nahian.tasker.data.ClassSession
import com.nahian.tasker.data.Course
import com.nahian.tasker.data.PrayerConfig
import com.nahian.tasker.data.PrayerLog
import com.nahian.tasker.data.Task
import com.nahian.tasker.data.Tuition
import com.nahian.tasker.data.TuitionSession
import kotlinx.coroutines.flow.first

/** One thing whose time has come and that the user has not handled yet. */
data class PendingItem(
    val module: String,     // TUITION, CLASS, PRAYER, TASK
    val id: Long,
    val title: String,
    val sub: String,
    val startMin: Int = 0,
    val key: String = "",   // prayer name for PRAYER items
)

/** Works out what is waiting for an answer right now. Used by the bubble badges and its panels. */
object Pending {
    fun compute(
        tuitions: List<Tuition>, tSessions: List<TuitionSession>,
        courses: List<Course>, cSessions: List<ClassSession>,
        configs: List<PrayerConfig>, logs: List<PrayerLog>, tasks: List<Task>,
    ): List<PendingItem> {
        val day = today()
        val now = nowMinutes()
        val out = ArrayList<PendingItem>()

        tSessions.filter { it.status == "PLANNED" && (it.date < day || (it.date == day && it.startMin <= now)) }
            .sortedWith(compareBy<TuitionSession>({ it.date }, { it.startMin }))
            .forEach { s ->
                val t = tuitions.firstOrNull { it.id == s.tuitionId } ?: return@forEach
                out.add(PendingItem("TUITION", s.id, listOf(t.name, t.subject).filter { it.isNotBlank() }.joinToString(" · "), fmtDate(s.date) + " · " + fmtTime(s.startMin), s.startMin))
            }

        cSessions.filter { it.status == "PLANNED" && (it.date < day || (it.date == day && it.startMin <= now)) }
            .sortedWith(compareBy<ClassSession>({ it.date }, { it.startMin }))
            .forEach { s ->
                val c = courses.firstOrNull { it.id == s.courseId } ?: return@forEach
                out.add(PendingItem("CLASS", s.id, listOf(c.code, c.title).filter { it.isNotBlank() }.joinToString(" · "), fmtDate(s.date) + " · " + fmtTime(s.startMin), s.startMin))
            }

        configs.filter { c -> c.enabled && c.timeMin <= now && logs.none { it.date == day && it.prayer == c.prayer } }
            .forEach { c -> out.add(PendingItem("PRAYER", c.sortOrder.toLong(), c.prayer, "Today · " + fmtTime(c.timeMin), c.timeMin, c.prayer)) }

        tasks.forEach { t ->
            val due = t.dueDate ?: return@forEach
            if (t.completedAt != null) return@forEach
            if (due < day) out.add(PendingItem("TASK", t.id, t.title, "Overdue · " + fmtDate(due)))
            else if (due == day && (t.dueMin ?: 0) <= now) out.add(PendingItem("TASK", t.id, t.title, "Due today" + (t.dueMin?.let { " " + fmtTime(it) } ?: "")))
        }
        return out
    }

    suspend fun load(dao: AppDao): List<PendingItem> = compute(
        dao.flowTuitions().first(), dao.flowTuitionSessions().first(),
        dao.flowCourses().first(), dao.flowClassSessions().first(),
        dao.listPrayerConfigs(), dao.flowPrayerLogs(today()).first(), dao.flowTasks().first(),
    )
}
