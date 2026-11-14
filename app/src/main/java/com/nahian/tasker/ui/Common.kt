@file:OptIn(ExperimentalMaterial3Api::class)

package com.nahian.tasker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nahian.tasker.TaskerApp
import com.nahian.tasker.core.WEEKDAYS
import com.nahian.tasker.core.parseDateOrNull
import com.nahian.tasker.core.parseTimeOrNull
import com.nahian.tasker.core.time24
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

val Teal = Color(0xFF0F6E66)
val Warn = Color(0xFF8A3F06)
val Muted = Color(0xFF5B6472)

@Composable
fun TaskerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Teal,
            secondary = Teal,
            tertiary = Color(0xFFB4530A),
            background = Color(0xFFF6F7F9),
            surface = Color.White,
            surfaceVariant = Color(0xFFE7EAEE),
            secondaryContainer = Color(0xFFCFE6E2),
        ),
        content = content,
    )
}

@Composable
fun taskerApp(): TaskerApp = LocalContext.current.applicationContext as TaskerApp

/** Standard page: title row, optional back arrow and actions, scrolling content. */
@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            actions()
        }
        content()
    }
}

@Composable
fun SectionCard(title: String? = null, container: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Muted)
            content()
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    number: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
    )
}

/** A row of chips where exactly one is selected. */
@Composable
fun <T> Choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
fun <T> Dropdown(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label + ": " + (options.firstOrNull { it.first == selected }?.second ?: "Choose"))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false })
            }
        }
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, sub: String = "", onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun Line(title: String, sub: String = "", trailing: String = "", trailingColor: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        if (trailing.isNotEmpty()) Text(trailing, fontWeight = FontWeight.Bold, color = trailingColor)
    }
}

/** Collects a database query as state, creating the query once per set of keys. */
@Composable
fun <T> rememberFlow(vararg keys: Any?, block: () -> Flow<List<T>>): State<List<T>> =
    remember(*keys) { block() }.collectAsState(emptyList())

/** One colour per kind of item, so tuition, classes, prayer, to-do and money are easy to tell apart. */
fun moduleColor(module: String): Color = when (module) {
    "TUITION" -> Color(0xFF0F6E66)
    "CLASS" -> Color(0xFF1D4ED8)
    "PRAYER" -> Color(0xFF7C3AED)
    "TASK" -> Color(0xFFB4530A)
    else -> Color(0xFFBE185D)
}

fun moduleLabel(module: String): String = when (module) {
    "TUITION" -> "Tuition"
    "CLASS" -> "Class"
    "PRAYER" -> "Prayer"
    "TASK" -> "To-do"
    else -> "Money"
}

@Composable
fun Tag(module: String) {
    val c = moduleColor(module)
    Text(
        moduleLabel(module).uppercase(),
        color = c,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(c.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Asks for a new day and time. The next 7 days are one tap; any other date can be typed. */
@Composable
fun RescheduleDialog(title: String, min: Int, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    val start = LocalDate.now()
    var d by remember { mutableStateOf(start.plusDays(1).toString()) }
    var t by remember { mutableStateOf(time24(min)) }
    var err by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pick the new day. The original day will not be counted.", style = MaterialTheme.typography.bodySmall)
                Choice((0..7).map { i -> start.plusDays(i.toLong()).let { it.toString() to (if (i == 0) "Today" else WEEKDAYS[it.dayOfWeek.value - 1] + " " + it.dayOfMonth) } }, d) { d = it }
                Field("Or type a date (YYYY-MM-DD)", d, { d = it })
                Field("Time (24h, e.g. 17:30)", t, { t = it })
                if (err.isNotEmpty()) Text(err, color = Warn)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val pd = parseDateOrNull(d)
                val pm = parseTimeOrNull(t)
                if (pd == null || pm == null) err = "Use a date like 2026-11-18 and a time like 17:30." else { onSave(pd.toString(), pm); onDismiss() }
            }) { Text("Reschedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
