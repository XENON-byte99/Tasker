package com.nahian.tasker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Conventions: money is Long in poisha (1 taka = 100), dates are ISO text "2026-11-15",
// times are minutes from midnight, weekdays are 1 (Monday) .. 7 (Sunday).

@Entity(tableName = "tuition")
data class Tuition(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subject: String = "",
    val days: String = "",              // comma separated weekdays, e.g. "1,3,6"
    val startMin: Int = 17 * 60,
    val durationMin: Int = 90,
    val payModel: String = "SET",       // SET, MONTHLY, PER_DAY
    val fee: Long = 0,
    val daysPerSet: Int = 12,
    val remindBeforeMin: Int = 30,
    val persistent: Boolean = true,
    val note: String = "",
    val active: Boolean = true,
)

@Entity(tableName = "tuition_set")
data class TuitionSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tuitionId: Long,
    val number: Int,
    val startDate: String,
    val targetDays: Int,                // 0 = no fixed count (monthly)
    val fee: Long,
    val paidAmount: Long = 0,
    val status: String = "OPEN",        // OPEN, COMPLETE
    val note: String = "",
)

@Entity(tableName = "tuition_session")
data class TuitionSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tuitionId: Long,
    val setId: Long = 0,
    val date: String,
    val startMin: Int,
    val status: String = "PLANNED",     // PLANNED, DONE, MISSED, MOVED
    val note: String = "",
)

@Entity(tableName = "course")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val title: String = "",
    val teacher: String = "",
    val minAttendance: Int = 75,
    val note: String = "",
)

@Entity(tableName = "class_slot")
data class ClassSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val weekday: Int,
    val startMin: Int,
    val endMin: Int,
    val room: String = "",
    val type: String = "THEORY",        // THEORY, LAB
)

@Entity(tableName = "class_session")
data class ClassSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val slotId: Long,
    val date: String,
    val startMin: Int,
    val status: String = "PLANNED",     // PLANNED, ATTENDED, MISSED, CANCELLED
    val note: String = "",
)

@Entity(tableName = "txn")
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val amount: Long,
    val type: String,                   // INCOME, EXPENSE, LOAN_IN, LOAN_OUT, REPAY_IN, REPAY_OUT
    val categoryId: Long? = null,
    val note: String = "",
    val sourceType: String = "",
    val sourceId: Long = 0,
)

@Entity(tableName = "expense_category")
data class ExpenseCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,         // null = main category, otherwise a sub-category of it
)

@Entity(tableName = "person")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "loan")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val direction: String,              // GIVEN, TAKEN
    val principal: Long,
    val date: String,
    val dueDate: String? = null,
    val note: String = "",
    val status: String = "OPEN",        // OPEN, CLEARED
)

@Entity(tableName = "loan_repayment")
data class LoanRepayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val amount: Long,
    val date: String,
    val note: String = "",
)

@Entity(tableName = "prayer_config")
data class PrayerConfig(
    @PrimaryKey val prayer: String,
    val timeMin: Int,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)

@Entity(tableName = "prayer_log", primaryKeys = ["date", "prayer"])
data class PrayerLog(
    val date: String,
    val prayer: String,
    val status: String,                 // PRAYED, JAMAAT, LATE, MISSED
)

@Entity(tableName = "task_category")
data class TaskCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,         // null = main category
)

@Entity(tableName = "task")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long? = null,
    val title: String,
    val notes: String = "",
    val dueDate: String? = null,
    val dueMin: Int? = null,
    val priority: Int = 1,              // 0 low, 1 normal, 2 high
    val repeatRule: String = "NONE",    // NONE, DAILY, WEEKLY, MONTHLY
    val persistent: Boolean = false,
    val completedAt: Long? = null,
)

@Entity(tableName = "reminder", indices = [Index(value = ["rkey"], unique = true)])
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rkey: String,                   // unique, e.g. "TUI:12", "PRAYER:2026-11-15:Fajr"
    val module: String,                 // TUITION, CLASS, PRAYER, TASK, MONEY
    val sourceId: Long = 0,
    val title: String,
    val body: String = "",
    val fireAt: Long,
    val persistent: Boolean = false,
    val repeatsLeft: Int = 0,
    val state: String = "SCHEDULED",    // SCHEDULED, SNOOZED, FIRED, DONE, EXPIRED
)
