package com.nahian.tasker.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import com.nahian.tasker.TaskerApp
import com.nahian.tasker.data.Doc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password protection and the encrypted vault.
 *
 * A random 256-bit data key encrypts every saved password and file (AES-GCM). That data key
 * is itself stored only in encrypted form, locked with a key derived from the master password
 * (PBKDF2-HMAC-SHA256). The master password is never stored. Without it the vault cannot be
 * read, by anyone, and there is no recovery.
 */
object Vault {
    private const val ITERATIONS = 150_000
    const val MAX_FILE_BYTES = 40L * 1024 * 1024

    val configured = mutableStateOf(false)   // a master password exists
    val lockOnStart = mutableStateOf(true)   // ask for it whenever the app opens
    val unlocked = mutableStateOf(false)

    private var key: SecretKey? = null
    private val random = SecureRandom()

    private fun sp(ctx: Context) = ctx.getSharedPreferences("tasker_lock", Context.MODE_PRIVATE)
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    fun init(ctx: Context) {
        val s = sp(ctx)
        configured.value = s.contains("wrapped")
        lockOnStart.value = s.getBoolean("lockOnStart", true)
        File(ctx.cacheDir, "vault_open").deleteRecursively()
    }

    private fun kek(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun seal(k: SecretKey, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, iv))
        return iv + c.doFinal(plain)
    }

    private fun unseal(k: SecretKey, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, data, 0, 12))
        return c.doFinal(data, 12, data.size - 12)
    }

    /** First-time setup. Returns false if a password already exists. */
    suspend fun create(ctx: Context, password: String): Boolean = withContext(Dispatchers.Default) {
        val s = sp(ctx)
        if (s.contains("wrapped")) return@withContext false
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val dk = ByteArray(32).also { random.nextBytes(it) }
        val wrapped = seal(kek(password, salt), dk)
        s.edit().putString("salt", b64(salt)).putString("wrapped", b64(wrapped)).putBoolean("lockOnStart", true).commit()
        key = SecretKeySpec(dk, "AES")
        lockOnStart.value = true
        configured.value = true
        unlocked.value = true
        true
    }

    /** Returns false when the password is wrong. */
    suspend fun unlock(ctx: Context, password: String): Boolean = withContext(Dispatchers.Default) {
        val s = sp(ctx)
        val salt = s.getString("salt", null) ?: return@withContext false
        val wrapped = s.getString("wrapped", null) ?: return@withContext false
        try {
            key = SecretKeySpec(unseal(kek(password, unb64(salt)), unb64(wrapped)), "AES")
            unlocked.value = true
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Re-locks the data key with a new password. Saved items do not need re-encrypting. */
    suspend fun changePassword(ctx: Context, old: String, new: String): Boolean = withContext(Dispatchers.Default) {
        val s = sp(ctx)
        val salt = s.getString("salt", null) ?: return@withContext false
        val wrapped = s.getString("wrapped", null) ?: return@withContext false
        try {
            val dk = unseal(kek(old, unb64(salt)), unb64(wrapped))
            val newSalt = ByteArray(16).also { random.nextBytes(it) }
            s.edit().putString("salt", b64(newSalt)).putString("wrapped", b64(seal(kek(new, newSalt), dk))).commit()
            key = SecretKeySpec(dk, "AES")
            unlocked.value = true
            true
        } catch (e: Exception) {
            false
        }
    }

    fun lock(ctx: Context) {
        key = null
        unlocked.value = false
        File(ctx.cacheDir, "vault_open").deleteRecursively()
    }

    fun setLockOnStart(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean("lockOnStart", on).apply()
        lockOnStart.value = on
    }

    private fun need(): SecretKey = key ?: throw IllegalStateException("The vault is locked")

    fun enc(plain: String): String = if (plain.isEmpty()) "" else b64(seal(need(), plain.toByteArray(Charsets.UTF_8)))

    fun dec(stored: String): String =
        if (stored.isEmpty()) "" else try { String(unseal(need(), unb64(stored)), Charsets.UTF_8) } catch (e: Exception) { "" }

    fun encryptFile(input: InputStream, out: File) {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, need(), GCMParameterSpec(128, iv))
        out.outputStream().use { fo ->
            fo.write(iv)
            CipherOutputStream(fo, c).use { co -> input.copyTo(co) }
        }
    }

    fun decryptFile(enc: File, out: File) {
        enc.inputStream().use { fi ->
            val iv = ByteArray(12)
            var read = 0
            while (read < 12) {
                val n = fi.read(iv, read, 12 - read)
                if (n < 0) throw java.io.IOException("File is damaged")
                read += n
            }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, need(), GCMParameterSpec(128, iv))
            CipherInputStream(fi, c).use { ci -> out.outputStream().use { ci.copyTo(it) } }
        }
    }

    fun generatePassword(length: Int = 18): String {
        val sets = listOf("abcdefghijkmnopqrstuvwxyz", "ABCDEFGHJKLMNPQRSTUVWXYZ", "23456789", "!@#$%&*?-_")
        val all = sets.joinToString("")
        val chars = sets.map { it[random.nextInt(it.length)] }.toMutableList()
        while (chars.size < length) chars.add(all[random.nextInt(all.length)])
        for (i in chars.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp
        }
        return chars.joinToString("")
    }

    // ---- files ----

    private fun dir(ctx: Context) = File(ctx.filesDir, "vault").apply { mkdirs() }

    /** Copies a picked file into the vault, encrypted. Returns an error message, or null on success. */
    suspend fun importFile(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        var name = "file"
        var size = 0L
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0) name = c.getString(ni) ?: name
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
            if (size > MAX_FILE_BYTES) return@withContext "\"$name\" is larger than 40 MB and was skipped."
            val fileName = UUID.randomUUID().toString() + ".bin"
            val out = File(dir(ctx), fileName)
            val input = ctx.contentResolver.openInputStream(uri) ?: return@withContext "Could not read \"$name\"."
            input.use { encryptFile(it, out) }
            (ctx.applicationContext as TaskerApp).vault.insertDoc(
                Doc(
                    name = name, mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream",
                    fileName = fileName, size = if (size > 0) size else out.length(), addedAt = System.currentTimeMillis(),
                )
            )
            null
        } catch (e: IllegalStateException) {
            "The vault locked itself while you were choosing. Unlock it and add the file again."
        } catch (e: Exception) {
            "Could not save \"$name\"."
        }
    }

    /** Decrypts a file into a private temporary folder so a viewer app can open it. */
    suspend fun openCopy(ctx: Context, doc: Doc): File = withContext(Dispatchers.IO) {
        val folder = File(ctx.cacheDir, "vault_open").apply { mkdirs() }
        val safe = doc.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "file" }
        val out = File(folder, safe)
        decryptFile(File(dir(ctx), doc.fileName), out)
        out
    }

    suspend fun deleteFile(ctx: Context, doc: Doc) = withContext(Dispatchers.IO) {
        File(dir(ctx), doc.fileName).delete()
        (ctx.applicationContext as TaskerApp).vault.deleteDoc(doc)
    }
}
