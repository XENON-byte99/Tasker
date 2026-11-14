package com.nahian.tasker.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        Tuition::class, TuitionSet::class, TuitionSession::class,
        Course::class, ClassSlot::class, ClassSession::class,
        Txn::class, ExpenseCategory::class, Person::class, Loan::class, LoanRepayment::class,
        PrayerConfig::class, PrayerLog::class,
        TaskCategory::class, Task::class, Reminder::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): AppDao
}

@Dao
interface AppDao {
    // ---- Tuition ----
    @Insert suspend fun insertTuition(t: Tuition): Long
    @Update suspend fun updateTuition(t: Tuition)
    @Query("SELECT * FROM tuition WHERE id = :id") suspend fun getTuition(id: Long): Tuition?
    @Query("SELECT * FROM tuition WHERE active = 1 ORDER BY name") fun flowTuitions(): Flow<List<Tuition>>
    @Query("SELECT * FROM tuition WHERE active = 1") suspend fun listActiveTuitions(): List<Tuition>

    @Insert suspend fun insertSet(s: TuitionSet): Long
    @Update suspend fun updateSet(s: TuitionSet)
    @Query("SELECT * FROM tuition_set WHERE id = :id") suspend fun getSet(id: Long): TuitionSet?
    @Query("SELECT * FROM tuition_set WHERE tuitionId = :tid AND status = 'OPEN' ORDER BY number DESC LIMIT 1")
    suspend fun openSet(tid: Long): TuitionSet?
    @Query("SELECT MAX(number) FROM tuition_set WHERE tuitionId = :tid") suspend fun maxSetNumber(tid: Long): Int?
    @Query("SELECT * FROM tuition_set ORDER BY number DESC") fun flowSets(): Flow<List<TuitionSet>>

    @Insert suspend fun insertTuitionSession(s: TuitionSession): Long
    @Update suspend fun updateTuitionSession(s: TuitionSession)
    @Query("SELECT * FROM tuition_session WHERE id = :id") suspend fun getTuitionSession(id: Long): TuitionSession?
    @Query("SELECT * FROM tuition_session ORDER BY date DESC, startMin DESC") fun flowTuitionSessions(): Flow<List<TuitionSession>>
    @Query("SELECT COUNT(*) FROM tuition_session WHERE tuitionId = :tid AND date = :date")
    suspend fun countTuitionSessions(tid: Long, date: String): Int
    @Query("SELECT COUNT(*) FROM tuition_session WHERE setId = :setId AND status = 'DONE'")
    suspend fun countDone(setId: Long): Int
    @Query("DELETE FROM reminder WHERE module = 'TUITION' AND sourceId IN (SELECT id FROM tuition_session WHERE tuitionId = :tid AND status = 'PLANNED' AND date >= :from)")
    suspend fun deletePlannedTuitionReminders(tid: Long, from: String)
    @Query("DELETE FROM tuition_session WHERE tuitionId = :tid AND status = 'PLANNED' AND date >= :from")
    suspend fun deletePlannedTuitionSessions(tid: Long, from: String)

    // ---- University ----
    @Insert suspend fun insertCourse(c: Course): Long
    @Update suspend fun updateCourse(c: Course)
    @Delete suspend fun deleteCourse(c: Course)
    @Query("SELECT * FROM course WHERE id = :id") suspend fun getCourse(id: Long): Course?
    @Query("SELECT * FROM course ORDER BY code") fun flowCourses(): Flow<List<Course>>

    @Insert suspend fun insertSlot(s: ClassSlot): Long
    @Query("SELECT * FROM class_slot ORDER BY weekday, startMin") fun flowSlots(): Flow<List<ClassSlot>>
    @Query("SELECT * FROM class_slot") suspend fun listSlots(): List<ClassSlot>
    @Query("SELECT * FROM class_slot WHERE courseId = :cid") suspend fun listSlotsOf(cid: Long): List<ClassSlot>
    @Query("DELETE FROM class_slot WHERE courseId = :cid") suspend fun deleteSlotsOf(cid: Long)

    @Insert suspend fun insertClassSession(s: ClassSession): Long
    @Update suspend fun updateClassSession(s: ClassSession)
    @Query("SELECT * FROM class_session WHERE id = :id") suspend fun getClassSession(id: Long): ClassSession?
    @Query("SELECT * FROM class_session ORDER BY date DESC, startMin") fun flowClassSessions(): Flow<List<ClassSession>>
    @Query("SELECT COUNT(*) FROM class_session WHERE slotId = :slotId AND date = :date")
    suspend fun countClassSessions(slotId: Long, date: String): Int
    @Query("DELETE FROM reminder WHERE module = 'CLASS' AND sourceId IN (SELECT id FROM class_session WHERE courseId = :cid AND status = 'PLANNED' AND date >= :from)")
    suspend fun deletePlannedClassReminders(cid: Long, from: String)
    @Query("DELETE FROM class_session WHERE courseId = :cid AND status = 'PLANNED' AND date >= :from")
    suspend fun deletePlannedClassSessions(cid: Long, from: String)
    @Query("DELETE FROM class_session WHERE courseId = :cid") suspend fun deleteClassSessionsOf(cid: Long)

    // ---- Money ----
    @Insert suspend fun insertTxn(t: Txn): Long
    @Delete suspend fun deleteTxn(t: Txn)
    @Query("SELECT * FROM txn ORDER BY date DESC, id DESC") fun flowTxns(): Flow<List<Txn>>

    @Insert suspend fun insertCategory(c: ExpenseCategory): Long
    @Query("SELECT * FROM expense_category ORDER BY id") fun flowCategories(): Flow<List<ExpenseCategory>>
    @Query("SELECT COUNT(*) FROM expense_category") suspend fun countCategories(): Int

    @Insert suspend fun insertPerson(p: Person): Long
    @Query("SELECT * FROM person ORDER BY name") fun flowPersons(): Flow<List<Person>>
    @Query("SELECT * FROM person WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun findPerson(name: String): Person?

    @Insert suspend fun insertLoan(l: Loan): Long
    @Update suspend fun updateLoan(l: Loan)
    @Query("SELECT * FROM loan ORDER BY date DESC") fun flowLoans(): Flow<List<Loan>>
    @Query("SELECT * FROM loan WHERE personId = :pid AND direction = :dir AND status = 'OPEN' ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate, date")
    suspend fun openLoans(pid: Long, dir: String): List<Loan>

    @Insert suspend fun insertRepayment(r: LoanRepayment): Long
    @Query("SELECT * FROM loan_repayment ORDER BY date DESC") fun flowRepayments(): Flow<List<LoanRepayment>>
    @Query("SELECT * FROM loan_repayment") suspend fun listRepayments(): List<LoanRepayment>

    // ---- Prayer ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPrayerConfig(c: PrayerConfig)
    @Query("SELECT * FROM prayer_config ORDER BY sortOrder") fun flowPrayerConfigs(): Flow<List<PrayerConfig>>
    @Query("SELECT * FROM prayer_config ORDER BY sortOrder") suspend fun listPrayerConfigs(): List<PrayerConfig>
    @Query("SELECT COUNT(*) FROM prayer_config") suspend fun countPrayerConfigs(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPrayerLog(l: PrayerLog)
    @Query("DELETE FROM prayer_log WHERE date = :date AND prayer = :prayer") suspend fun deletePrayerLog(date: String, prayer: String)
    @Query("SELECT * FROM prayer_log WHERE date >= :from") fun flowPrayerLogs(from: String): Flow<List<PrayerLog>>
    @Query("DELETE FROM reminder WHERE module = 'PRAYER' AND state IN ('SCHEDULED', 'SNOOZED') AND fireAt > :now")
    suspend fun deleteFuturePrayerReminders(now: Long)

    // ---- To-do ----
    @Insert suspend fun insertTaskCategory(c: TaskCategory): Long
    @Delete suspend fun deleteTaskCategory(c: TaskCategory)
    @Query("SELECT * FROM task_category ORDER BY name") fun flowTaskCategories(): Flow<List<TaskCategory>>
    @Query("UPDATE task SET categoryId = NULL WHERE categoryId = :cid") suspend fun clearTaskCategory(cid: Long)
    @Query("UPDATE task_category SET parentId = NULL WHERE parentId = :cid") suspend fun promoteSubCategories(cid: Long)

    @Insert suspend fun insertTask(t: Task): Long
    @Update suspend fun updateTask(t: Task)
    @Delete suspend fun deleteTask(t: Task)
    @Query("SELECT * FROM task WHERE id = :id") suspend fun getTask(id: Long): Task?
    @Query("SELECT * FROM task ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate, dueMin, id") fun flowTasks(): Flow<List<Task>>

    // ---- Reminders ----
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertReminder(r: Reminder): Long
    @Update suspend fun updateReminder(r: Reminder)
    @Query("SELECT * FROM reminder WHERE id = :id") suspend fun getReminder(id: Long): Reminder?
    @Query("SELECT * FROM reminder WHERE state IN ('SCHEDULED', 'SNOOZED') ORDER BY fireAt") suspend fun pendingReminders(): List<Reminder>
    @Query("SELECT * FROM reminder WHERE state IN ('SCHEDULED', 'SNOOZED') ORDER BY fireAt") fun flowPendingReminders(): Flow<List<Reminder>>
    @Query("DELETE FROM reminder WHERE rkey = :key") suspend fun deleteReminderByKey(key: String)
    @Query("UPDATE reminder SET state = 'DONE' WHERE rkey = :key") suspend fun completeReminderByKey(key: String)
}
