@file:OptIn(ExperimentalMaterial3Api::class)

package com.nahian.tasker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nahian.tasker.data.ExpenseCategory
import kotlinx.coroutines.launch

/** The main category an expense category belongs to (itself, if it is a main one). */
fun mainCategoryId(id: Long?, cats: List<ExpenseCategory>): Long? {
    val c = cats.firstOrNull { it.id == id } ?: return id
    return c.parentId ?: c.id
}

/** "Transport › Bus" for a sub-category, "Transport" for a main one. */
fun categoryName(id: Long?, cats: List<ExpenseCategory>): String {
    val c = cats.firstOrNull { it.id == id } ?: return "Uncategorised"
    val parent = cats.firstOrNull { it.id == c.parentId }
    return if (parent == null) c.name else parent.name + " › " + c.name
}

/** Main categories in a grid; when the chosen one has sub-categories, they appear underneath. */
@Composable
fun CategoryPicker(cats: List<ExpenseCategory>, selected: Long?, onSelect: (Long?) -> Unit, onManage: () -> Unit) {
    val mains = cats.filter { it.parentId == null }
    val mainId = mainCategoryId(selected, cats) ?: mains.firstOrNull()?.id
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Category", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onManage) { Text("Edit categories") }
    }
    OptionGrid(mains.map { it.id as Long? to it.name }, mainId, 3) { onSelect(it) }
    val subs = cats.filter { it.parentId == mainId }
    if (subs.isNotEmpty()) {
        val main = mains.firstOrNull { it.id == mainId }
        Text("Which ${main?.name?.lowercase() ?: "kind"}?", style = MaterialTheme.typography.labelLarge, color = Muted)
        OptionGrid(listOf((mainId) to "Any") + subs.map { it.id as Long? to it.name }, selected ?: mainId, 3) { onSelect(it) }
    }
}

/** Add, rename and delete expense categories and their sub-categories. */
@Composable
fun ExpenseCategoriesScreen(nav: NavController) {
    val app = taskerApp()
    val scope = app.ui
    val cats by rememberFlow { app.dao.flowCategories() }
    // What the add/rename window is doing: a new main category, a new sub-category, or a rename.
    var adding by remember { mutableStateOf<ExpenseCategory?>(null) }
    var renaming by remember { mutableStateOf<ExpenseCategory?>(null) }
    val mains = cats.filter { it.parentId == null }

    Screen(
        "Categories", onBack = { nav.popBackStack() }, backLabel = "Money",
        sub = "Sub-categories let you split one, like Transport into Bus, Uber and Metro.",
        onAdd = { adding = ExpenseCategory(name = "") },
    ) {
        if (mains.isEmpty()) Text("No categories yet. Tap + to add one.", color = Muted)
        mains.forEach { m ->
            val subs = cats.filter { it.parentId == m.id }
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { renaming = m }) { Text("Rename") }
                    DeleteCategoryButton(m, cats)
                }
                subs.forEach { s ->
                    RowDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                        Text("› " + s.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = { renaming = s }) { Text("Rename") }
                        DeleteCategoryButton(s, cats)
                    }
                }
                TextButton(onClick = { adding = ExpenseCategory(name = "", parentId = m.id) }) { Text("+ Add sub-category to ${m.name}") }
            }
        }
    }

    adding?.let { draft ->
        var name by remember(draft) { mutableStateOf("") }
        val parent = mains.firstOrNull { it.id == draft.parentId }
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text(if (parent == null) "New category" else "New sub-category of ${parent.name}") },
            text = { Field(if (parent == null) "Name, e.g. Health" else "Name, e.g. Bus", name, { name = it }) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) scope.launch { app.dao.insertCategory(ExpenseCategory(name = n, parentId = draft.parentId)) }
                    adding = null
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("Cancel") } },
        )
    }
    renaming?.let { c ->
        var name by remember(c.id) { mutableStateOf(c.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { Field("Name", name, { name = it }) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) scope.launch { app.dao.updateCategory(c.copy(name = n)) }
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DeleteCategoryButton(c: ExpenseCategory, cats: List<ExpenseCategory>) {
    val app = taskerApp()
    var count by remember(c.id) { mutableIntStateOf(-1) }
    androidx.compose.runtime.LaunchedEffect(c.id, cats) {
        count = app.dao.countExpensesIn(c.id) + cats.filter { it.parentId == c.id }.sumOf { app.dao.countExpensesIn(it.id) }
    }
    val parent = cats.firstOrNull { it.id == c.parentId }
    val subs = cats.count { it.parentId == c.id }
    val what = when {
        count <= 0 -> "It has no expenses."
        parent != null -> "Its $count expense(s) move to ${parent.name}."
        else -> "Its $count expense(s) stay saved but become Uncategorised."
    }
    val extra = if (subs > 0) " Its $subs sub-categor${if (subs == 1) "y is" else "ies are"} deleted too." else ""
    ConfirmButton("Delete", "Delete \"${c.name}\"? $what$extra No expense is deleted.") {
        app.ui.launch { app.repo.deleteExpenseCategory(c, cats) }
    }
}
