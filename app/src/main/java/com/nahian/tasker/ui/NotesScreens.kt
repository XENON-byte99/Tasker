@file:OptIn(ExperimentalMaterial3Api::class)

package com.nahian.tasker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import com.nahian.tasker.core.fmtMillis
import com.nahian.tasker.data.Note
import kotlinx.coroutines.launch

val NoteColor = Color(0xFF475569)

@Composable
fun NotesScreen(nav: NavController) {
    val app = taskerApp()
    val notes by rememberFlow { app.notes.flowNotes() }
    var category by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    val categories = notes.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()

    Screen("Notes") {
        Button(onClick = { nav.navigate("noteEdit/0") }, modifier = Modifier.fillMaxWidth()) { Text("New note") }
        Field("Search notes", search, { search = it })
        if (categories.isNotEmpty()) Choice(listOf("" to "All") + categories.map { it to it }, category) { category = it }

        val shown = notes.filter {
            (category.isEmpty() || it.category == category) &&
                (search.isBlank() || it.title.contains(search.trim(), true) || it.body.contains(search.trim(), true))
        }
        if (shown.isEmpty()) Text(if (notes.isEmpty()) "No notes yet. Write your first one." else "No notes match.", color = Muted)
        shown.forEach { n ->
            SectionCard(accent = NoteColor) {
                Column(Modifier.fillMaxWidth().clickable { nav.navigate("noteEdit/${n.id}") }) {
                    Text((if (n.pinned) "★ " else "") + n.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (n.body.isNotBlank()) Text(n.body, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(n.category, fmtMillis(n.updatedAt)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Muted,
                    )
                }
            }
        }
    }
}

@Composable
fun NoteEditScreen(nav: NavController, id: Long) {
    val app = taskerApp()
    val scope = app.ui
    val notes by rememberFlow { app.notes.flowNotes() }
    var existing by remember { mutableStateOf<Note?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val categories = notes.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()

    LaunchedEffect(id) {
        if (id != 0L) app.notes.get(id)?.let { n ->
            existing = n
            title = n.title; body = n.body; category = n.category; pinned = n.pinned
        }
    }

    Screen(if (id == 0L) "New note" else "Edit note", onBack = { nav.popBackStack() }) {
        Field("Title", title, { title = it })
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("Note") },
            minLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        Field("Category (optional), e.g. University", category, { category = it })
        if (categories.isNotEmpty()) Choice(categories.map { it to it }, category) { category = it }
        SwitchRow("Pin to the top", pinned) { pinned = it }
        if (error.isNotEmpty()) Text(error, color = Warn)

        Button(
            onClick = {
                val finalTitle = title.trim().ifEmpty { body.trim().lineSequence().firstOrNull()?.take(40) ?: "" }
                if (finalTitle.isEmpty()) error = "Write a title or some text first." else scope.launch {
                    val n = (existing ?: Note(title = "")).copy(
                        title = finalTitle, body = body.trim(), category = category.trim(), pinned = pinned, updatedAt = System.currentTimeMillis(),
                    )
                    if (n.id == 0L) app.notes.insert(n) else app.notes.update(n)
                    nav.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }

        existing?.let { n ->
            ConfirmButton("Delete note", "Delete \"${n.title}\"? This cannot be undone.", Modifier.fillMaxWidth(), outlined = true) {
                scope.launch { app.notes.delete(n); nav.popBackStack() }
            }
        }
    }
}
