@file:OptIn(ExperimentalMaterial3Api::class)

package com.nahian.tasker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nahian.tasker.core.WEEKDAYS
import com.nahian.tasker.core.fmtTime
import com.nahian.tasker.core.today
import com.nahian.tasker.data.ClassSession
import com.nahian.tasker.data.Task
import com.nahian.tasker.data.TuitionSession
import java.time.LocalDate

private class AgendaRow(val min: Int, val module: String, val title: String, val sub: String, val onClick: () -> Unit)

/**
 * A strip of today and the next 6 days. A dot with a number marks days that have something,
 * and picking a day lists its tuitions, classes and to-dos. Used on Home and in the bubble panel.
 */
@Composable
fun DaysAgenda(onTuition: (TuitionSession) -> Unit, onClass: (ClassSession) -> Unit, onTask: (Task) -> Unit) {
    val app = taskerApp()
    val tuitions by rememberFlow { app.dao.flowTuitions() }
    val tSessions by rememberFlow { app.dao.flowTuitionSessions() }
    val courses by rememberFlow { app.dao.flowCourses() }
    val cSessions by rememberFlow { app.dao.flowClassSessions() }
    val tasks by rememberFlow { app.dao.flowTasks() }
    var day by rememberSaveable { mutableStateOf(today()) }
    val start = LocalDate.now()

    fun tuitionsOn(d: String) = tSessions.filter { it.date == d && it.status != "MOVED" && tuitions.any { t -> t.id == it.tuitionId } }
    fun classesOn(d: String) = cSessions.filter { it.date == d && it.status != "MOVED" }
    fun tasksOn(d: String) = tasks.filter { it.completedAt == null && it.dueDate == d }

    Choice(
        (0..6).map { i ->
            val d = start.plusDays(i.toLong())
            val ds = d.toString()
            val n = tuitionsOn(ds).size + classesOn(ds).size + tasksOn(ds).size
            ds to ((if (i == 0) "Today" else WEEKDAYS[d.dayOfWeek.value - 1] + " " + d.dayOfMonth) + if (n > 0) "  ● $n" else "")
        },
        day,
    ) { day = it }

    val rows = ArrayList<AgendaRow>()
    tuitionsOn(day).forEach { s ->
        val t = tuitions.first { it.id == s.tuitionId }
        val status = when (s.status) { "DONE" -> "done"; "MISSED" -> "missed"; else -> "planned" }
        rows.add(AgendaRow(s.startMin, "TUITION", listOf(t.name, t.subject).filter { it.isNotBlank() }.joinToString(" · "), fmtTime(s.startMin) + " · " + status) { onTuition(s) })
    }
    classesOn(day).forEach { s ->
        val c = courses.firstOrNull { it.id == s.courseId }
        val status = when (s.status) { "ATTENDED" -> "attended"; "MISSED" -> "missed"; "CANCELLED" -> "cancelled"; else -> "planned" }
        rows.add(AgendaRow(s.startMin, "CLASS", listOf(c?.code ?: "Class", c?.title ?: "").filter { it.isNotBlank() }.joinToString(" · "), fmtTime(s.startMin) + " · " + status) { onClass(s) })
    }
    tasksOn(day).forEach { t ->
        rows.add(AgendaRow(t.dueMin ?: (24 * 60), "TASK", t.title, t.dueMin?.let { fmtTime(it) } ?: "Any time") { onTask(t) })
    }

    if (rows.isEmpty()) Text("Nothing planned on this day.", color = Muted)
    rows.sortedBy { it.min }.forEach { r ->
        Column(Modifier.fillMaxWidth().clickable { r.onClick() }) {
            Tag(r.module)
            Line(r.title, r.sub)
        }
        RowDivider()
    }
}
