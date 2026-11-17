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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
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
    Screen("Money", onAdd = { nav.navigate(if (tab == "LOANS") "loanAdd" else "expenseAdd") }) {
        Segmented(listOf("EXPENSES" to "Expenses", "LOANS" to "Loans", "INCOME" to "Income"), tab) { tab = it }
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

    val saveAction = remember { Ref<() -> Unit>({}) }
    FormScreen(if (id == 0L) "New expense" else "Edit expense", onCancel = { nav.popBackStack() }, save = saveAction) {
        BigAmount(amount) { amount = it }
        Text("Category", style = MaterialTheme.typography.labelLarge)
        OptionGrid(categories.map { it.id as Long? to it.name }, catId ?: categories.firstOrNull()?.id, 3) { catId = it }
        TextButton(onClick = { newCat = "" }) { Text("New category") }
        Text("Date", style = MaterialTheme.typography.labelLarge)
        OptionGrid(
            listOf(today() to "Today", date(today()).minusDays(1).toString() to "Yesterday"),
            dateTxt, 2,
        ) { dateTxt = it }
        DateField("Or pick another date", dateTxt, { dateTxt = it })
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
        saveAction.value = { save(true) }
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

    Button(onClick = { nav.navigate("loanAdd") }, modifier = Modifier.fillMaxWidth()) { Text("Add loan entry") }

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
        SectionCard("Repayment suggestion", container = AccentBg) {
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

    // Open loans only. Settled ones are kept apart, further down, behind a button.
    val active = persons.filter { p -> loans.any { it.personId == p.id && outstanding(it.id, it.principal) > 0 } }
    val settled = loans.filter { outstanding(it.id, it.principal) == 0L }
    SectionCard("Open loans", accent = moduleColor("MONEY")) {
        if (active.isEmpty()) Text(if (loans.isEmpty()) "No loans recorded yet." else "Nothing is owed either way.", color = Muted)
        active.forEachIndexed { index, p ->
            val mine = loans.filter { it.personId == p.id && outstanding(it.id, it.principal) > 0 }
            val iOwe = mine.filter { it.direction == "TAKEN" }.sumOf { outstanding(it.id, it.principal) }
            val theyOwe = mine.filter { it.direction == "GIVEN" }.sumOf { outstanding(it.id, it.principal) }
            val net = theyOwe - iOwe
            Line(
                p.name, "",
                when { net < 0 -> "You owe ${money(-net)}"; net > 0 -> "Owes you ${money(net)}"; else -> "Even" },
                when { net < 0 -> Warn; net > 0 -> Teal; else -> Muted },
            )
            mine.forEach { l ->
                val left = outstanding(l.id, l.principal)
                val taken = l.direction == "TAKEN"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Line(
                        (if (taken) "Borrowed " else "Lent ") + money(l.principal),
                        listOf(
                            fmtDate(l.date),
                            (if (taken) "paid back " else "got back ") + money(l.principal - left),
                            "left " + money(left),
                            if (l.dueDate != null) "due " + fmtDate(l.dueDate) else "",
                            l.note,
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        modifier = Modifier.weight(1f),
                    )
                    ConfirmButton("Delete", "Delete this loan of ${money(l.principal)} and its repayments? Use this for a wrong entry.") { scope.launch { app.repo.deleteLoan(l) } }
                }
            }
            if (index < active.size - 1) RowDivider()
        }
    }

    if (settled.isNotEmpty()) {
        var showSettled by rememberSaveable { mutableStateOf(false) }
        OutlinedButton(onClick = { showSettled = !showSettled }, modifier = Modifier.fillMaxWidth()) {
            Text((if (showSettled) "Hide settled loans" else "Show settled loans") + " (${settled.size})  " + (if (showSettled) "▴" else "▾"))
        }
        if (showSettled) {
            SectionCard("Settled") {
                settled.forEachIndexed { index, l ->
                    val p = persons.firstOrNull { it.id == l.personId }
                    val taken = l.direction == "TAKEN"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Line(
                            (p?.name ?: "Someone") + " · " + (if (taken) "borrowed " else "lent ") + money(l.principal),
                            fmtDate(l.date) + " · fully " + (if (taken) "paid back" else "returned to you"),
                            "Settled", Muted, Modifier.weight(1f),
                        )
                        ConfirmButton("Delete", "Delete this settled loan of ${money(l.principal)} and its repayments?") { scope.launch { app.repo.deleteLoan(l) } }
                    }
                    if (index < settled.size - 1) RowDivider()
                }
            }
        }
    }
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

    val saveAction = remember { Ref<() -> Unit>({}) }
    FormScreen("New loan entry", onCancel = { nav.popBackStack() }, save = saveAction) {
        Text("What happened", style = MaterialTheme.typography.labelLarge)
        OptionGrid(listOf("BORROWED" to "I borrowed", "LENT" to "I lent", "PAID_BACK" to "I paid back", "GOT_BACK" to "I got paid back"), kind, 2) { kind = it }
        Field("Person", person, { person = it })
        if (persons.isNotEmpty()) Choice(persons.map { it.name to it.name }, person) { person = it }
        Field("Amount (৳)", amount, { amount = it }, number = true)
        DateField("Date", dateTxt, { dateTxt = it })
        if (kind == "BORROWED" || kind == "LENT") DateField("Pay back by (optional)", due, { due = it }, optional = true)
        Field("Note (optional)", note, { note = it })
        if (error.isNotEmpty()) Text(error, color = Warn)
        saveAction.value = {
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
        }
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
