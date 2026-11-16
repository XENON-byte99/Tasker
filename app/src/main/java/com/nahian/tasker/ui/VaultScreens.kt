@file:OptIn(ExperimentalMaterial3Api::class)

package com.nahian.tasker.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.nahian.tasker.core.Vault
import com.nahian.tasker.core.fmtMillis
import com.nahian.tasker.data.Doc
import com.nahian.tasker.data.Secret
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val VaultColor: Color get() = if (Skins.current.dark) Color(0xFF34D399) else Color(0xFF047857)

@Composable
fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") } },
    )
}

/** First-time creation of the master password. */
@Composable
fun PasswordSetup() {
    val app = taskerApp()
    val ctx = LocalContext.current
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    SectionCard("Create your master password", accent = VaultColor) {
        Text("This one password opens Tasker and unlocks your saved passwords and files.")
        Text(
            "Important: it is not stored anywhere and cannot be reset. If you forget it, the saved passwords and files are gone for good.",
            color = Warn, fontWeight = FontWeight.SemiBold,
        )
        PasswordField("Master password (8 or more characters)", p1) { p1 = it }
        PasswordField("Type it again", p2) { p2 = it }
        if (error.isNotEmpty()) Text(error, color = Warn)
        Button(
            enabled = !busy,
            onClick = {
                error = when {
                    p1.length < 8 -> "Use at least 8 characters."
                    p1 != p2 -> "The two passwords do not match."
                    else -> ""
                }
                if (error.isEmpty()) {
                    busy = true
                    app.ui.launch {
                        if (!Vault.create(ctx.applicationContext, p1)) error = "A password already exists."
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Setting up…" else "Create password") }
    }
}

@Composable
fun UnlockForm() {
    val app = taskerApp()
    val ctx = LocalContext.current
    var p by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    SectionCard("Locked", accent = VaultColor) {
        Text("Enter your master password to continue.")
        PasswordField("Master password", p) { p = it }
        if (error.isNotEmpty()) Text(error, color = Warn)
        Button(
            enabled = !busy && p.isNotEmpty(),
            onClick = {
                busy = true
                app.ui.launch {
                    if (!Vault.unlock(ctx.applicationContext, p)) error = "Wrong password. Try again."
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Checking…" else "Unlock") }
    }
}

/** Shown instead of the whole app when it is locked. */
@Composable
fun LockScreen() {
    Screen("Tasker is locked", accent = VaultColor, icon = Icons.Filled.Lock) {
        UnlockForm()
        Text("Reminders still arrive while Tasker is locked.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

/** Wraps vault pages: hides them from screenshots and asks for the password when needed. */
@Composable
fun VaultGate(nav: NavController, title: String, content: @Composable ColumnScope.() -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val window = (ctx as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    Screen(title, onBack = { nav.popBackStack() }, accent = VaultColor, icon = Icons.Filled.Lock) {
        when {
            !Vault.configured.value -> PasswordSetup()
            !Vault.unlocked.value -> UnlockForm()
            else -> content()
        }
    }
}

private fun copySecret(ctx: Context, label: String, value: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value)
    if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    cm.setPrimaryClip(clip)
}

private fun sizeLabel(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

@Composable
fun VaultScreen(nav: NavController) {
    var tab by rememberSaveable { mutableStateOf("PASSWORDS") }
    VaultGate(nav, "Vault") {
        Choice(listOf("PASSWORDS" to "Passwords", "FILES" to "Files"), tab) { tab = it }
        if (tab == "PASSWORDS") PasswordsTab(nav) else FilesTab()
        val ctx = LocalContext.current
        OutlinedButton(onClick = { Vault.lock(ctx.applicationContext) }, modifier = Modifier.fillMaxWidth()) { Text("Lock now") }
    }
}

@Composable
private fun PasswordsTab(nav: NavController) {
    val app = taskerApp()
    val ctx = LocalContext.current
    val secrets by rememberFlow { app.vault.flowSecrets() }
    var search by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Button(onClick = { nav.navigate("secretEdit/0") }, modifier = Modifier.fillMaxWidth()) { Text("Add password") }
    if (secrets.size > 4) Field("Search", search, { search = it })
    if (message.isNotEmpty()) Text(message, color = VaultColor)
    val shown = secrets.filter { search.isBlank() || it.title.contains(search.trim(), true) || it.url.contains(search.trim(), true) || it.category.contains(search.trim(), true) }
    if (shown.isEmpty()) Text(if (secrets.isEmpty()) "No passwords saved yet." else "Nothing matches.", color = Muted)
    shown.forEach { s ->
        val user = remember(s.id, s.usernameEnc, Vault.unlocked.value) { Vault.dec(s.usernameEnc) }
        SectionCard(accent = VaultColor) {
            Column(Modifier.fillMaxWidth().clickable { nav.navigate("secretEdit/${s.id}") }) {
                Text(s.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (user.isNotEmpty()) Text(user)
                Text(listOf(s.url, s.category).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "Tap to view or edit" }, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val pw = Vault.dec(s.passwordEnc)
                    copySecret(ctx, "password", pw)
                    message = "Password for ${s.title} copied. It is cleared in 30 seconds."
                    app.ui.launch {
                        delay(30_000)
                        // Only clear if the clipboard still holds this password.
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val now = try { cm.primaryClip?.getItemAt(0)?.text?.toString() } catch (e: Exception) { null }
                        if (now == pw) cm.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }) { Text("Copy password") }
                if (user.isNotEmpty()) TextButton(onClick = { copySecret(ctx, "username", user); message = "Username copied." }) { Text("Copy username") }
            }
        }
    }
}

@Composable
fun SecretEditScreen(nav: NavController, id: Long) {
    val app = taskerApp()
    val scope = app.ui
    var existing by remember { mutableStateOf<Secret?>(null) }
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(id, Vault.unlocked.value) {
        if (id != 0L && Vault.unlocked.value && existing == null) app.vault.getSecret(id)?.let { s ->
            existing = s
            title = s.title; url = s.url; category = s.category
            username = Vault.dec(s.usernameEnc); password = Vault.dec(s.passwordEnc); notes = Vault.dec(s.notesEnc)
        }
    }

    VaultGate(nav, if (id == 0L) "New password" else "Edit password") {
        Field("Name (for example Gmail, bKash, University portal)", title, { title = it })
        Field("Username, email or phone", username, { username = it })
        PasswordField("Password", password) { password = it }
        OutlinedButton(onClick = { password = Vault.generatePassword() }) { Text("Generate a strong password") }
        Field("Website or app (optional)", url, { url = it })
        Field("Category (optional)", category, { category = it })
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes, PIN, recovery codes (optional)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        if (error.isNotEmpty()) Text(error, color = Warn)
        Button(
            onClick = {
                if (title.isBlank()) error = "Give it a name." else scope.launch {
                    try {
                        val s = (existing ?: Secret(title = "")).copy(
                            title = title.trim(), usernameEnc = Vault.enc(username.trim()), passwordEnc = Vault.enc(password),
                            url = url.trim(), notesEnc = Vault.enc(notes.trim()), category = category.trim(), updatedAt = System.currentTimeMillis(),
                        )
                        if (s.id == 0L) app.vault.insertSecret(s) else app.vault.updateSecret(s)
                        nav.popBackStack()
                    } catch (e: IllegalStateException) {
                        error = "The vault is locked. Unlock it and save again."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
        existing?.let { s ->
            Text("Last changed ${fmtMillis(s.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = Muted)
            ConfirmButton("Delete this password", "Delete the saved password for \"${s.title}\"? This cannot be undone.", Modifier.fillMaxWidth(), outlined = true) {
                scope.launch { app.vault.deleteSecret(s); nav.popBackStack() }
            }
        }
    }
}

@Composable
private fun FilesTab() {
    val app = taskerApp()
    val ctx = LocalContext.current
    val scope = app.ui
    val docs by rememberFlow { app.vault.flowDocs() }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Doc?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            busy = true
            scope.launch {
                val problems = uris.mapNotNull { Vault.importFile(ctx.applicationContext, it) }
                message = if (problems.isEmpty()) "${uris.size} file(s) added and encrypted." else problems.joinToString(" ")
                busy = false
            }
        }
    }

    Button(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Encrypting…" else "Add files (PDF, images, documents)") }
    Text("Files are copied into Tasker and encrypted. The original stays where it was; delete it yourself if you want only the protected copy. Up to 40 MB each.", style = MaterialTheme.typography.bodySmall, color = Muted)
    if (message.isNotEmpty()) Text(message, color = VaultColor)
    if (docs.isEmpty()) Text("No files yet.", color = Muted)
    docs.forEach { d ->
        SectionCard(accent = VaultColor) {
            Text(d.name, fontWeight = FontWeight.Bold)
            Text(
                listOf(d.category, sizeLabel(d.size), "added " + fmtMillis(d.addedAt)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    scope.launch {
                        try {
                            val file = Vault.openCopy(ctx.applicationContext, d)
                            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW).setDataAndType(uri, d.mime)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: ActivityNotFoundException) {
                            message = "No app on this phone can open this kind of file."
                        } catch (e: Exception) {
                            message = "Could not open \"${d.name}\"."
                        }
                    }
                }) { Text("Open") }
                TextButton(onClick = { editing = d }) { Text("Rename") }
                ConfirmButton("Delete", "Delete \"${d.name}\" from the vault? This cannot be undone.") { scope.launch { Vault.deleteFile(ctx.applicationContext, d) } }
            }
        }
    }

    editing?.let { d ->
        var name by remember(d.id) { mutableStateOf(d.name) }
        var category by remember(d.id) { mutableStateOf(d.category) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("File details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Name", name, { name = it })
                    Field("Category (for example ID, Certificates, Bank)", category, { category = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) scope.launch { app.vault.updateDoc(d.copy(name = name.trim(), category = category.trim())) }
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

/** The "Password protection" card in Settings. */
@Composable
fun ProtectionSettings(nav: NavController) {
    val app = taskerApp()
    val ctx = LocalContext.current
    var changing by remember { mutableStateOf(false) }
    if (!Vault.configured.value) {
        PasswordSetup()
        return
    }
    SectionCard("Password protection", accent = VaultColor) {
        SwitchRow("Ask for the password when Tasker opens", Vault.lockOnStart.value, "Also locks again after a minute in the background") {
            Vault.setLockOnStart(ctx.applicationContext, it)
        }
        Text("The vault (saved passwords and files) always needs the password, even with this off.", style = MaterialTheme.typography.bodySmall, color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { nav.navigate("vault") }) { Text("Open vault") }
            OutlinedButton(onClick = { changing = true }) { Text("Change password") }
        }
        Text("There is no way to reset a forgotten password.", style = MaterialTheme.typography.bodySmall, color = Warn)
    }
    if (changing) {
        var old by remember { mutableStateOf("") }
        var p1 by remember { mutableStateOf("") }
        var p2 by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { changing = false },
            title = { Text("Change master password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PasswordField("Current password", old) { old = it }
                    PasswordField("New password (8 or more characters)", p1) { p1 = it }
                    PasswordField("New password again", p2) { p2 = it }
                    if (error.isNotEmpty()) Text(error, color = Warn)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    error = when {
                        p1.length < 8 -> "Use at least 8 characters."
                        p1 != p2 -> "The new passwords do not match."
                        else -> ""
                    }
                    if (error.isEmpty()) app.ui.launch {
                        if (Vault.changePassword(ctx.applicationContext, old, p1)) changing = false else error = "The current password is wrong."
                    }
                }) { Text("Change") }
            },
            dismissButton = { TextButton(onClick = { changing = false }) { Text("Cancel") } },
        )
    }
}
