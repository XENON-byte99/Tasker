@file:OptIn(ExperimentalMaterial3Api::class)

package com.nahian.tasker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nahian.tasker.core.fmtTime
import com.nahian.tasker.core.parseDateOrNull
import com.nahian.tasker.core.parseTimeOrNull
import com.nahian.tasker.core.time24
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A date shown as a button; tapping it opens a calendar. The value stays in "2026-11-15" form. */
@Composable
fun DateField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    optional: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val parsed = parseDateOrNull(value)
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.weight(1f)) {
                Text(parsed?.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US)) ?: "Pick a date")
            }
            if (optional && parsed != null) TextButton(onClick = { onChange("") }) { Text("Clear") }
        }
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (parsed ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** A time shown as a button; tapping it opens a clock. The value stays in "17:30" form. */
@Composable
fun TimeField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    optional: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val min = parseTimeOrNull(value)
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.weight(1f)) { Text(min?.let { fmtTime(it) } ?: "Pick a time") }
            if (optional && min != null) TextButton(onClick = { onChange("") }) { Text("Clear") }
        }
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = (min ?: (17 * 60)) / 60, initialMinute = (min ?: 0) % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { onChange(time24(state.hour * 60 + state.minute)); open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}
