package com.nahian.tasker.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.nahian.tasker.core.PRAYERS
import com.nahian.tasker.core.Planner
import com.nahian.tasker.core.ReminderScheduler
import com.nahian.tasker.core.fmtDate
import com.nahian.tasker.core.fmtTime
import com.nahian.tasker.core.millisOf
import com.nahian.tasker.core.parseDateOrNull
import com.nahian.tasker.core.today
import java.time.LocalDate

/** Small settings store. Values are Compose state so screens update when they change. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("tasker", Context.MODE_PRIVATE)

    val repayShare = mutableIntStateOf(sp.getInt("repayShare", 30))
    val repayPool = mutableLongStateOf(sp.getLong("repayPool", 0))
    val classRemindMin = mutableIntStateOf(sp.getInt("classRemindMin", 15))
    val bubbleOn = mutableStateOf(sp.getBoolean("bubbleOn", false))

    fun setRepayShare(v: Int) { repayShare.intValue = v; sp.edit().putInt("repayShare", v).apply() }
    fun setRepayPool(v: Long) { repayPool.longValue = v; sp.edit().putLong("repayPool", v).apply() }
    fun setClassRemindMin(v: Int) { classRemindMin.intValue = v; sp.edit().putInt("classRemindMin", v).apply() }
    fun setBubbleOn(v: Boolean) { bubbleOn.value = v; sp.edit().putBoolean("bubbleOn", v).apply() }

    val skinId: String get() = sp.getString("skin", "AURORA") ?: "AURORA"
    fun setSkin(id: String) {
        sp.edit().putString("skin", id).apply()
        com.nahian.tasker.ui.Skins.current = com.nahian.tasker.ui.Skins.byId(id)
    }

    var bubbleX: Int
        get() = sp.getInt("bubbleX", 0)
        set(v) { sp.edit().putInt("bubbleX", v).apply() }
    var bubbleY: Int
        get() = sp.getInt("bubbleY", 400)
        set(v) { sp.edit().putInt("bubbleY", v).apply() }
}

/** All the rules of the app live here; screens only call these functions. */
class Repo(private val ctx: Context, private val dao: AppDao, private val prefs: Prefs) {

    suspend fun seed() {
        if (dao.countCategories() == 0) {
            listOf("Food", "Transport", "Mobile and internet", "Study", "Rent", "Family", "Other")
                .forEach { dao.insertCategory(ExpenseCategory(name = it)) }
        }
        if (dao.countPrayerConfigs() == 0) {
            val defaults = listOf(5 * 60, 12 * 60 + 15, 15 * 60 + 45, 17 * 60 + 20, 19 * 60)
            PRAYERS.forEachIndexed { i, p -> dao.upsertPrayerConfig(PrayerConfig(p, defaults[i], true, i)) }
        }
    }

    // ---------------- Tuition ----------------

    suspend fun saveTuition(t: Tuition): Long {
        val id = if (t.id == 0L) dao.insertTuition(t) else {
            dao.updateTuition(t)
            // Schedule changed: drop future planned days and let the planner rebuild them.
            dao.deletePlannedTuitionReminders(t.id, today())
            dao.deletePlannedTuitionSessions(t.id, today())
            t.id
        }
        Planner.sync(ctx)
        return id
    }

    suspend fun archiveTuition(t: Tuition) {
        dao.updateTuition(t.copy(active = false))
        dao.deletePlannedTuitionReminders(t.id, today())
        dao.deletePlannedTuitionSessions(t.id, today())
    }

    private suspend fun openSetFor(t: Tuition, forDate: String): TuitionSet {
        var s = dao.openSet(t.id)
        if (s != null && t.payModel == "MONTHLY" && s.startDate.take(7) != forDate.take(7)) {
            dao.updateSet(s.copy(status = "COMPLETE"))
            s = null
        }
        if (s == null) {
            val target = when (t.payModel) { "SET" -> t.daysPerSet; "PER_DAY" -> 1; else -> 0 }
            val fresh = TuitionSet(
                tuitionId = t.id, number = (dao.maxSetNumber(t.id) ?: 0) + 1,
                startDate = forDate, targetDays = target, fee = t.fee,
            )
            s = fresh.copy(id = dao.insertSet(fresh))
        }
        return s
    }

    private suspend fun recomputeSet(setId: Long) {
        val set = dao.getSet(setId) ?: return
        if (set.targetDays <= 0) return
        val status = if (dao.countDone(setId) >= set.targetDays) "COMPLETE" else "OPEN"
        if (status != set.status) dao.updateSet(set.copy(status = status))
    }

    suspend fun markTuitionSession(sessionId: Long, status: String, note: String) {
        val s = dao.getTuitionSession(sessionId) ?: return
        val t = dao.getTuition(s.tuitionId) ?: return
        var setId = s.setId
        if (status == "DONE" && s.status != "DONE") {
            val set = openSetFor(t, s.date)
            // A past day added later can be earlier than the set's start; keep the start honest.
            if (s.date < set.startDate) dao.updateSet(set.copy(startDate = s.date))
            setId = set.id
        }
        dao.updateTuitionSession(s.copy(status = status, note = note, setId = setId))
        if (setId != 0L) recomputeSet(setId)
        if (s.setId != 0L && s.setId != setId) recomputeSet(s.setId)
        if (status != "PLANNED") {
            dao.completeReminderByKey("TUI:$sessionId")
            dao.completeReminderByKey("TUIM:$sessionId")
            com.nahian.tasker.core.Notifier.cancelByKey(ctx, dao, "TUIM:$sessionId")
        }
    }

    /** Moves one day to another date or time without touching the weekly schedule. */
    suspend fun moveTuitionSession(sessionId: Long, newDate: String, newMin: Int, note: String) {
        val s = dao.getTuitionSession(sessionId) ?: return
        val t = dao.getTuition(s.tuitionId) ?: return
        dao.updateTuitionSession(s.copy(status = "MOVED"))
        dao.deleteReminderByKey("TUI:$sessionId")
        dao.deleteReminderByKey("TUIM:$sessionId")
        val moved = if (note.isBlank()) "Rescheduled from ${fmtDate(s.date)}" else note
        val nid = dao.insertTuitionSession(TuitionSession(tuitionId = s.tuitionId, date = newDate, startMin = newMin, note = moved))
        val at = millisOf(newDate, newMin) - t.remindBeforeMin * 60_000L
        if (at > System.currentTimeMillis()) {
            dao.insertReminder(
                Reminder(
                    rkey = "TUI:$nid", module = "TUITION", sourceId = nid, title = "Tuition: ${t.name}",
                    body = "${t.subject} at ${fmtTime(newMin)} (moved)".trim(), fireAt = at,
                    persistent = t.persistent, repeatsLeft = if (t.persistent) 6 else 0,
                )
            )
        }
        ReminderScheduler.scheduleAll(ctx)
    }

    suspend fun addExtraTuitionDay(tuitionId: Long, date: String, note: String) {
        val t = dao.getTuition(tuitionId) ?: return
        val id = dao.insertTuitionSession(TuitionSession(tuitionId = tuitionId, date = date, startMin = t.startMin))
        markTuitionSession(id, "DONE", note)
    }

    /**
     * For teaching that began before the app was installed: adds every scheduled weekday from
     * [from] until yesterday as a Done day, oldest first, so sets and day counts fill up correctly.
     */
    suspend fun backfillTuition(tuitionId: Long, from: String): Int {
        val t = dao.getTuition(tuitionId) ?: return 0
        val days = t.days.split(",").mapNotNull { it.toIntOrNull() }
        var d = parseDateOrNull(from) ?: return 0
        val today = LocalDate.now()
        var added = 0
        while (d.isBefore(today)) {
            if (d.dayOfWeek.value in days && dao.countTuitionSessions(t.id, d.toString()) == 0) {
                val sid = dao.insertTuitionSession(TuitionSession(tuitionId = t.id, date = d.toString(), startMin = t.startMin))
                markTuitionSession(sid, "DONE", "")
                added++
            }
            d = d.plusDays(1)
        }
        return added
    }

    /** The same for classes: every class from [from] until yesterday is added as Attended. */
    suspend fun backfillCourse(courseId: Long, from: String): Int {
        val slots = dao.listSlotsOf(courseId)
        var d = parseDateOrNull(from) ?: return 0
        val today = LocalDate.now()
        var added = 0
        while (d.isBefore(today)) {
            for (slot in slots) {
                if (slot.weekday == d.dayOfWeek.value && dao.countClassSessions(slot.id, d.toString()) == 0) {
                    dao.insertClassSession(ClassSession(courseId = courseId, slotId = slot.id, date = d.toString(), startMin = slot.startMin, status = "ATTENDED"))
                    added++
                }
            }
            d = d.plusDays(1)
        }
        return added
    }

    suspend fun saveSetNote(setId: Long, note: String) {
        val set = dao.getSet(setId) ?: return
        dao.updateSet(set.copy(note = note))
    }

    suspend fun payTuitionSet(setId: Long, amount: Long) {
        val set = dao.getSet(setId) ?: return
        val t = dao.getTuition(set.tuitionId)
        dao.updateSet(set.copy(paidAmount = set.paidAmount + amount))
        addIncome(amount, "Tuition: ${t?.name ?: ""} set ${set.number}", "TUITION_SET", setId)
    }

    // ---------------- University ----------------

    suspend fun saveCourse(c: Course, slots: List<ClassSlot>): Long {
        val id = if (c.id == 0L) dao.insertCourse(c) else { dao.updateCourse(c); c.id }
        dao.deletePlannedClassReminders(id, today())
        dao.deletePlannedClassSessions(id, today())
        dao.deleteSlotsOf(id)
        slots.forEach { dao.insertSlot(it.copy(id = 0, courseId = id)) }
        Planner.sync(ctx)
        return id
    }

    suspend fun deleteCourse(c: Course) {
        dao.deletePlannedClassReminders(c.id, "0000-00-00")
        dao.deleteClassSessionsOf(c.id)
        dao.deleteSlotsOf(c.id)
        dao.deleteCourse(c)
    }

    suspend fun markClassSession(sessionId: Long, status: String, note: String) {
        val s = dao.getClassSession(sessionId) ?: return
        dao.updateClassSession(s.copy(status = status, note = note))
        if (status != "PLANNED") {
            dao.completeReminderByKey("CLS:$sessionId")
            dao.completeReminderByKey("CLSM:$sessionId")
            com.nahian.tasker.core.Notifier.cancelByKey(ctx, dao, "CLSM:$sessionId")
        }
    }

    /** Moves one class to another day or time (a make-up class). The original is not counted. */
    suspend fun moveClassSession(sessionId: Long, newDate: String, newMin: Int) {
        val s = dao.getClassSession(sessionId) ?: return
        val c = dao.getCourse(s.courseId) ?: return
        dao.updateClassSession(s.copy(status = "MOVED"))
        dao.deleteReminderByKey("CLS:$sessionId")
        dao.deleteReminderByKey("CLSM:$sessionId")
        val nid = dao.insertClassSession(
            ClassSession(courseId = s.courseId, slotId = 0, date = newDate, startMin = newMin, note = "Rescheduled from ${fmtDate(s.date)}")
        )
        val at = millisOf(newDate, newMin) - prefs.classRemindMin.intValue * 60_000L
        if (at > System.currentTimeMillis()) {
            dao.insertReminder(
                Reminder(
                    rkey = "CLS:$nid", module = "CLASS", sourceId = nid, title = "Class: ${c.code}",
                    body = "${c.title} at ${fmtTime(newMin)} (rescheduled)".trim(), fireAt = at,
                )
            )
        }
        ReminderScheduler.scheduleAll(ctx)
    }

    // ---------------- Money ----------------

    suspend fun addExpense(amount: Long, categoryId: Long?, date: String, note: String) {
        dao.insertTxn(Txn(date = date, amount = amount, type = "EXPENSE", categoryId = categoryId, note = note))
    }

    suspend fun addIncome(amount: Long, note: String, sourceType: String = "", sourceId: Long = 0) {
        dao.insertTxn(Txn(date = today(), amount = amount, type = "INCOME", note = note, sourceType = sourceType, sourceId = sourceId))
        val share = prefs.repayShare.intValue
        if (share > 0) prefs.setRepayPool(prefs.repayPool.longValue + amount * share / 100)
    }

    /** Makes the recorded balance match the user's real money by adding one correction line. */
    suspend fun correctBalance(current: Long, target: Long) {
        val diff = target - current
        if (diff == 0L) return
        dao.insertTxn(Txn(date = today(), amount = Math.abs(diff), type = if (diff > 0) "ADJUST_IN" else "ADJUST_OUT", note = "Balance correction"))
    }

    /** kind: BORROWED, LENT, PAID_BACK, GOT_BACK */
    suspend fun addLoanEntry(kind: String, personName: String, amount: Long, date: String, due: String?, note: String) {
        val name = personName.trim()
        if (name.isEmpty()) return
        val p = dao.findPerson(name) ?: Person(name = name).let { it.copy(id = dao.insertPerson(it)) }
        when (kind) {
            "BORROWED" -> {
                val id = dao.insertLoan(Loan(personId = p.id, direction = "TAKEN", principal = amount, date = date, dueDate = due, note = note))
                dao.insertTxn(Txn(date = date, amount = amount, type = "LOAN_IN", note = "Borrowed from ${p.name}", sourceType = "LOAN", sourceId = id))
                addLoanReminder(id, due, "Pay back ${p.name}")
            }
            "LENT" -> {
                val id = dao.insertLoan(Loan(personId = p.id, direction = "GIVEN", principal = amount, date = date, dueDate = due, note = note))
                dao.insertTxn(Txn(date = date, amount = amount, type = "LOAN_OUT", note = "Lent to ${p.name}", sourceType = "LOAN", sourceId = id))
                addLoanReminder(id, due, "Collect from ${p.name}")
            }
            "PAID_BACK" -> repay(p, "TAKEN", amount, date, note)
            "GOT_BACK" -> repay(p, "GIVEN", amount, date, note)
        }
    }

    private suspend fun addLoanReminder(loanId: Long, due: String?, title: String) {
        if (due == null) return
        val at = millisOf(due, 9 * 60)
        if (at <= System.currentTimeMillis()) return
        dao.insertReminder(Reminder(rkey = "LOAN:$loanId", module = "MONEY", sourceId = loanId, title = title, body = "Loan due today", fireAt = at))
        ReminderScheduler.scheduleAll(ctx)
    }

    private suspend fun repay(p: Person, direction: String, amount: Long, date: String, note: String) {
        var left = amount
        val reps = dao.listRepayments()
        val out = direction == "TAKEN"
        for (l in dao.openLoans(p.id, direction)) {
            if (left <= 0) break
            val outstanding = l.principal - reps.filter { it.loanId == l.id }.sumOf { it.amount }
            if (outstanding <= 0) { dao.updateLoan(l.copy(status = "CLEARED")); continue }
            val pay = minOf(outstanding, left)
            dao.insertRepayment(LoanRepayment(loanId = l.id, amount = pay, date = date, note = note))
            // One ledger line per loan, linked to it, so deleting a wrong loan also removes its payments.
            dao.insertTxn(
                Txn(
                    date = date, amount = pay, type = if (out) "REPAY_OUT" else "REPAY_IN",
                    note = if (out) "Paid back ${p.name}" else "Got back from ${p.name}", sourceType = "LOAN", sourceId = l.id,
                )
            )
            if (pay == outstanding) {
                dao.updateLoan(l.copy(status = "CLEARED"))
                dao.completeReminderByKey("LOAN:${l.id}")
            }
            left -= pay
        }
    }

    /** Removes a wrongly entered loan together with its repayments and ledger lines. */
    suspend fun deleteLoan(l: Loan) {
        dao.deleteTxnsBySource("LOAN", l.id)
        dao.deleteRepaymentsOf(l.id)
        dao.deleteReminderByKey("LOAN:${l.id}")
        dao.deleteLoan(l)
    }

    /** Removes an income line. A tuition payment goes back to unpaid on its set. */
    suspend fun deleteIncome(t: Txn) {
        if (t.sourceType == "TUITION_SET") {
            dao.getSet(t.sourceId)?.let { dao.updateSet(it.copy(paidAmount = maxOf(0L, it.paidAmount - t.amount))) }
        }
        dao.deleteTxn(t)
    }

    // ---------------- Prayer ----------------

    suspend fun setPrayer(date: String, prayer: String, status: String?) {
        if (status == null) dao.deletePrayerLog(date, prayer) else {
            dao.upsertPrayerLog(PrayerLog(date, prayer, status))
            dao.completeReminderByKey("PRAYER:$date:$prayer")
        }
    }

    suspend fun savePrayerConfigs(list: List<PrayerConfig>) {
        list.forEach { dao.upsertPrayerConfig(it) }
        dao.deleteFuturePrayerReminders(System.currentTimeMillis())
        Planner.sync(ctx)
    }

    // ---------------- To-do ----------------

    suspend fun saveTask(t: Task): Long {
        val id = if (t.id == 0L) dao.insertTask(t) else { dao.updateTask(t); t.id }
        refreshTaskReminder(t.copy(id = id))
        return id
    }

    suspend fun deleteTask(t: Task) {
        dao.deleteReminderByKey("TASK:${t.id}")
        dao.deleteTask(t)
    }

    /**
     * Marks a task done. An optional short note is kept with the task, and an optional
     * follow-up task is created in the same category, due today.
     */
    suspend fun completeTask(task: Task, note: String = "", followUp: String = "") {
        val t = if (note.isBlank()) task else task.copy(notes = (task.notes + "\nDone: " + note.trim()).trim())
        if (followUp.isNotBlank()) saveTask(Task(title = followUp.trim(), categoryId = task.categoryId, dueDate = today()))
        val due = t.dueDate?.let { parseDateOrNull(it) }
        val next = if (t.repeatRule != "NONE" && due != null) {
            // Repeating task: move to the next occurrence after today instead of closing it.
            var d: LocalDate = due
            val now = LocalDate.now()
            do {
                d = when (t.repeatRule) { "DAILY" -> d.plusDays(1); "WEEKLY" -> d.plusWeeks(1); else -> d.plusMonths(1) }
            } while (!d.isAfter(now))
            t.copy(dueDate = d.toString())
        } else t.copy(completedAt = System.currentTimeMillis())
        dao.updateTask(next)
        refreshTaskReminder(next)
    }

    suspend fun reopenTask(t: Task) {
        val nt = t.copy(completedAt = null)
        dao.updateTask(nt)
        refreshTaskReminder(nt)
    }

    private suspend fun refreshTaskReminder(t: Task) {
        dao.deleteReminderByKey("TASK:${t.id}")
        val due = t.dueDate
        if (t.completedAt == null && due != null && parseDateOrNull(due) != null) {
            val at = millisOf(due, t.dueMin ?: (9 * 60))
            if (at > System.currentTimeMillis()) {
                dao.insertReminder(
                    Reminder(
                        rkey = "TASK:${t.id}", module = "TASK", sourceId = t.id, title = t.title, body = "To-do due now",
                        fireAt = at, persistent = t.persistent, repeatsLeft = if (t.persistent) 6 else 0,
                    )
                )
            }
        }
        ReminderScheduler.scheduleAll(ctx)
    }

    suspend fun deleteTaskCategory(c: TaskCategory) {
        dao.clearTaskCategory(c.id)
        dao.promoteSubCategories(c.id)
        dao.deleteTaskCategory(c)
    }

    // ---------------- Reminders ----------------

    /** "Later": ask again in 10 minutes. */
    suspend fun snooze(r: Reminder) {
        val next = System.currentTimeMillis() + 10 * 60_000L
        dao.updateReminder(r.copy(fireAt = next, state = "SNOOZED"))
        ReminderScheduler.set(ctx, r.id, next)
        com.nahian.tasker.core.Notifier.cancel(ctx, r.id)
    }

    /** "Done" pressed on a notification, in the bubble panel, or on the home screen. */
    suspend fun reminderDone(r: Reminder) {
        dao.updateReminder(r.copy(state = "DONE"))
        // "Did you teach / attend?" reminders: Done marks the session itself.
        if (r.rkey.startsWith("TUIM:")) dao.getTuitionSession(r.sourceId)?.let { if (it.status == "PLANNED") markTuitionSession(it.id, "DONE", it.note) }
        if (r.rkey.startsWith("CLSM:")) dao.getClassSession(r.sourceId)?.let { if (it.status == "PLANNED") markClassSession(it.id, "ATTENDED", it.note) }
        when (r.module) {
            "TASK" -> dao.getTask(r.sourceId)?.let { completeTask(it) }
            "PRAYER" -> {
                val parts = r.rkey.split(":")
                if (parts.size == 3) dao.upsertPrayerLog(PrayerLog(parts[1], parts[2], "PRAYED"))
            }
        }
        ReminderScheduler.cancel(ctx, r.id)
        com.nahian.tasker.core.Notifier.cancel(ctx, r.id)
    }
}
