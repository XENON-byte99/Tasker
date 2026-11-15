@file:OptIn(ExperimentalMaterial3Api::class)

package com.nahian.tasker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nahian.tasker.core.date
import com.nahian.tasker.core.fmtDate
import com.nahian.tasker.core.money
import com.nahian.tasker.core.parseDateOrNull
import com.nahian.tasker.core.parseMoney
import com.nahian.tasker.core.today
import com.nahian.tasker.data.ExpenseCategory
import com.nahian.tasker.data.Loan
import com.nahian.tasker.data.Txn
import kotlinx.coroutines.launch

@Composable
fun MoneyScreen(nav: NavController) {
    var tab by rememberSaveable { mutableStateOf("EXPENSES") }
    Screen("Money") {
        Choice(listOf("EXPENSES" to "Expenses", "LOANS" to "Loans", "INCOME" to "Income"), tab) { tab = it }
        when (tab) {
            "EXPENSES" -> ExpensesTab(nav)
            "LOANS" -> LoansTab(nav)
            else -> IncomeTab()
        }
    }
}

@Composable
private fun ExpensesTab(nav: NavController) {
    val app = taskerApp()
    val scope = app.ui
    val txns by rememberFlow { app.dao.flowTxns() }
    val categories by rememberFlow { app.dao.flowCategories() }
    var day by rememberSaveable { mutableStateOf(today()) }
    val month = day.take(7)
    val expenses = txns.filter { it.type == "EXPENSE" }
    val monthItems = expenses.filter { it.date.startsWith(month) }
    val monthTotal = monthItems.sumOf { it.amount }

    Button(onClick = { nav.navigate("expenseAdd") }, modifier = Modifier.fillMaxWidth()) { Text("Add expense") }

    SectionCard("$month · by category", accent = moduleColor("MONEY")) {
        Text(money(monthTotal), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        val byCat = monthItems.groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.amount } }.toList().sortedByDescending { it.second }
        val top = byCat.firstOrNull()?.second ?: 0L
        if (byCat.isEmpty()) Text("No expenses this month yet.", color = Muted)
        byCat.forEach { (catId, sum) ->
            Line(categories.firstOrNull { it.id == catId }?.name ?: "Uncategorised", trailing = money(sum))
            LinearProgressIndicator(progress = { if (top == 0L) 0f else sum.toFloat() / top }, modifier = Modifier.fillMaxWidth())
        }
    }

    SectionCard("By date", accent = moduleColor("MONEY")) {
        val items = expenses.filter { it.date == day }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { day = date(day).minusDays(1).toString() }) { Text("‹ Earlier") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fmtDate(day), fontWeight = FontWeight.Bold)
                Text("Total ${money(items.sumOf { it.amount })}", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            TextButton(onClick = { day = date(day).plusDays(1).toString() }) { Text("Later ›") }
        }
        HorizontalDivider()
        if (items.isEmpty()) Text("Nothing recorded on this day.", color = Muted)
        items.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Line(
                    t.note.ifBlank { categories.firstOrNull { it.id == t.categoryId }?.name ?: "Expense" },
                    categories.firstOrNull { it.id == t.categoryId }?.name ?: "",
                    money(t.amount),
                    modifier = Modifier.weight(1f).clickable { nav.navigate("expenseEdit/${t.id}") },
                )
                TextButton(onClick = { nav.navigate("expenseEdit/${t.id}") }) { Text("Edit") }
                ConfirmButton("Delete", "Delete this expense of ${money(t.amount)}? This cannot be undone.") { scope.launch { app.dao.deleteTxn(t) } }
            }
            RowDivider()
        }
    }
}

@Composable
fun ExpenseAddScreen(nav: NavController, id: Long = 0L) {
    val app = taskerApp()
    val scope = app.ui
    val categories by rememberFlow { app.dao.flowCategories() }
    var amount by remember { mutableStateOf("") }
    var catId by remember { mutableStateOf<Long?>(null) }
    var dateTxt by remember { mutableStateOf(today()) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var newCat by remember { mutableStateOf<String?>(null) }
    var existing by remember { mutableStateOf<Txn?>(null) }

    LaunchedEffect(id) {
        if (id != 0L) app.dao.getTxn(id)?.let { t ->
            existing = t
            amount = if (t.amount % 100 == 0L) (t.amount / 100).toString() else (t.amount / 100.0).toString()
            catId = t.categoryId; dateTxt = t.date; note = t.note
        }
    }

    Screen(if (id == 0L) "New expense" else "Edit expense", onBack = { nav.popBackStack() }) {
        Field("Amount (৳)", amount, { amount = it }, number = true)
        Text("Category", style = MaterialTheme.typography.labelLarge)
        Choice(categories.map { it.id as Long? to it.name }, catId ?: categories.firstOrNull()?.id) { catId = it }
        TextButton(onClick = { newCat = "" }) { Text("New category") }
        Text("Date", style = MaterialTheme.typography.labelLarge)
        Choice(
            listOf(today() to "Today", date(today()).minusDays(1).toString() to "Yesterday"),
            dateTxt,
        ) { dateTxt = it }
        Field("Or type a date (YYYY-MM-DD)", dateTxt, { dateTxt = it })
        Field("Note (optional)", note, { note = it })
        if (error.isNotEmpty()) Text(error, color = Warn)

        fun save(thenClose: Boolean) {
            val a = parseMoney(amount)
            val d = parseDateOrNull(dateTxt)
            error = when {
                a == null -> "Enter an amount."
                d == null -> "Date must look like 2026-11-15."
                else -> ""
            }
            if (a != null && d != null) scope.launch {
                val old = existing
                val cat = catId ?: categories.firstOrNull()?.id
                if (old != null) app.dao.updateTxn(old.copy(amount = a, categoryId = cat, date = d.toString(), note = note.trim()))
                else app.repo.addExpense(a, cat, d.toString(), note.trim())
                if (thenClose) nav.popBackStack() else { amount = ""; note = "" }
            }
        }
        Button(onClick = { save(true) }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        if (id == 0L) OutlinedButton(onClick = { save(false) }, modifier = Modifier.fillMaxWidth()) { Text("Save and add another") }
    }

    newCat?.let { text ->
        AlertDialog(
            onDismissRequest = { newCat = null },
            title = { Text("New category") },
            text = { Field("Name", text, { newCat = it }) },
            confirmButton = {
                TextButton(onClick = {
                    val n = text.trim()
                    if (n.isNotEmpty()) scope.launch { catId = app.dao.insertCategory(ExpenseCategory(name = n)) }
                    newCat = null
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { newCat = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LoansTab(nav: NavController) {
    val app = taskerApp()
    val scope = app.ui
    val persons by rememberFlow { app.dao.flowPersons() }
    val loans by rememberFlow { app.dao.flowLoans() }
    val reps by rememberFlow { app.dao.flowRepayments() }
    val pool = app.prefs.repayPool.longValue
    val share = app.prefs.repayShare.intValue

    fun outstanding(loanId: Long, principal: Long): Long = maxOf(0L, principal - reps.filter { it.loanId == loanId }.sumOf { it.amount })
    val owe = loans.filter { it.direction == "TAKEN" }.sumOf { outstanding(it.id, it.principal) }
    val owed = loans.filter { it.direction == "GIVEN" }.sumOf { outstanding(it.id, it.principal) }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Stat("You owe", money(owe), Modifier.weight(1f), color = if (owe > 0) Warn else Color.Unspecified)
        Stat("Owed to you", money(owed), Modifier.weight(1f), color = Teal)
    }

    // Earnings-based suggestion: the pool grows by the chosen share each time income is recorded.
    val target = loans.filter { it.direction == "TAKEN" && outstanding(it.id, it.principal) > 0 }
        .sortedWith(compareBy<Loan>({ it.dueDate == null }, { it.dueDate ?: "" }, { it.date })).firstOrNull()
    if (pool > 0 && target != null) {
        val person = persons.firstOrNull { it.id == target.personId }
        val pay = minOf(pool, outstanding(target.id, target.principal))
        SectionCard("Repayment suggestion", container = Color(0xFFEAF4F2)) {
            Text("$share% of your recent income is set aside for loans: ${money(pool)}.")
            Line("Pay ${person?.name ?: "lender"}", if (target.dueDate != null) "Due ${fmtDate(target.dueDate)}" else "No due date", money(pay))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        app.repo.addLoanEntry("PAID_BACK", person?.name ?: "", pay, today(), null, "From earnings")
                        app.prefs.setRepayPool(pool - pay)
                    }
                }) { Text("Confirm") }
                OutlinedButton(onClick = { app.prefs.setRepayPool(0L) }) { Text("Skip") }
            }
        }
    }

    SectionCard("People", accent = moduleColor("MONEY")) {
        if (persons.isEmpty()) Text("No loans recorded yet.", color = Muted)
        persons.forEach { p ->
            val mine = loans.filter { it.personId == p.id }
            val iOwe = mine.filter { it.direction == "TAKEN" }.sumOf { outstanding(it.id, it.principal) }
            val theyOwe = mine.filter { it.direction == "GIVEN" }.sumOf { outstanding(it.id, it.principal) }
            val net = theyOwe - iOwe
            val nextDue = mine.filter { it.dueDate != null && outstanding(it.id, it.principal) > 0 }.minByOrNull { it.dueDate ?: "" }?.dueDate
            Line(
                p.name,
                listOf(
                    "Taken ${money(mine.filter { it.direction == "TAKEN" }.sumOf { it.principal })}",
                    "given ${money(mine.filter { it.direction == "GIVEN" }.sumOf { it.principal })}",
                    if (nextDue != null) "due ${fmtDate(nextDue)}" else "",
                ).filter { it.isNotEmpty() }.joinToString(" · "),
                when {
                    net < 0 -> "You owe ${money(-net)}"
                    net > 0 -> "Owes you ${money(net)}"
                    else -> "Settled"
                },
                when { net < 0 -> Warn; net > 0 -> Teal; else -> Muted },
            )
            mine.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Line(
                        (if (l.direction == "TAKEN") "Borrowed " else "Lent ") + money(l.principal),
                        fmtDate(l.date) + " · left " + money(outstanding(l.id, l.principal)) + if (l.note.isBlank()) "" else " · " + l.note,
                        modifier = Modifier.weight(1f),
                    )
                    ConfirmButton("Delete", "Delete this loan of ${money(l.principal)} and its repayments? Use this for a wrong entry.") { scope.launch { app.repo.deleteLoan(l) } }
                }
            }
            HorizontalDivider()
        }
    }
    Button(onClick = { nav.navigate("loanAdd") }, modifier = Modifier.fillMaxWidth()) { Text("Add loan entry") }
}

@Composable
fun LoanAddScreen(nav: NavController) {
    val app = taskerApp()
    val scope = app.ui
    val persons by rememberFlow { app.dao.flowPersons() }
    var kind by remember { mutableStateOf("BORROWED") }
    var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dateTxt by remember { mutableStateOf(today()) }
    var due by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Screen("New loan entry", onBack = { nav.popBackStack() }) {
        Text("What happened", style = MaterialTheme.typography.labelLarge)
        Choice(listOf("BORROWED" to "I borrowed", "LENT" to "I lent", "PAID_BACK" to "I paid back", "GOT_BACK" to "I got paid back"), kind) { kind = it }
        Field("Person", person, { person = it })
        if (persons.isNotEmpty()) Choice(persons.map { it.name to it.name }, person) { person = it }
        Field("Amount (৳)", amount, { amount = it }, number = true)
        Field("Date (YYYY-MM-DD)", dateTxt, { dateTxt = it })
        if (kind == "BORROWED" || kind == "LENT") Field("Pay back by (optional, YYYY-MM-DD)", due, { due = it })
        Field("Note (optional)", note, { note = it })
        if (error.isNotEmpty()) Text(error, color = Warn)
        Button(
            onClick = {
                val a = parseMoney(amount)
                val d = parseDateOrNull(dateTxt)
                val dueDate = if (due.isBlank()) null else parseDateOrNull(due)
                error = when {
                    person.isBlank() -> "Enter the person's name."
                    a == null -> "Enter an amount."
                    d == null -> "Date must look like 2026-11-15."
                    due.isNotBlank() && dueDate == null -> "Due date must look like 2026-11-30."
                    else -> ""
                }
                if (error.isEmpty() && a != null && d != null) scope.launch {
                    app.repo.addLoanEntry(kind, person, a, d.toString(), dueDate?.toString(), note.trim())
                    nav.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
    }
}

@Composable
private fun IncomeTab() {
    val app = taskerApp()
    val scope = app.ui
    val txns by rememberFlow { app.dao.flowTxns() }
    val income = txns.filter { it.type == "INCOME" }
    val month = today().take(7)
    var adding by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Stat("This month", money(income.filter { it.date.startsWith(month) }.sumOf { it.amount }), Modifier.weight(1f))
        Stat("All time", money(income.sumOf { it.amount }), Modifier.weight(1f))
    }
    Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Add other income") }
    SectionCard("Income history", accent = moduleColor("MONEY")) {
        if (income.isEmpty()) Text("Tuition payments you record appear here.", color = Muted)
        income.take(60).forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Line(t.note.ifBlank { "Income" }, fmtDate(t.date), money(t.amount), Teal, Modifier.weight(1f))
                ConfirmButton("Delete", "Delete this income of ${money(t.amount)}? If it is a tuition payment, that set goes back to unpaid.") { scope.launch { app.repo.deleteIncome(t) } }
            }
            RowDivider()
        }
    }

    if (adding) {
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Other income") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Amount (৳)", amount, { amount = it }, number = true)
                    Field("From what", note, { note = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val a = parseMoney(amount)
                    if (a != null) scope.launch { app.repo.addIncome(a, note.trim().ifEmpty { "Other income" }) }
                    adding = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}
