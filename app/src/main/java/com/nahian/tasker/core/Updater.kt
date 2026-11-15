package com.nahian.tasker.core

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the GitHub "latest" release for a newer build and installs it.
 * Only this feature uses the internet; everything else in the app works offline.
 */
object Updater {
    private const val API = "https://api.github.com/repos/XENON-byte99/Tasker/releases/tags/latest"
    private const val VERSION = "https://github.com/XENON-byte99/Tasker/releases/download/latest/version.txt"
    private const val APK = "https://github.com/XENON-byte99/Tasker/releases/download/latest/Tasker.apk"

    /** Build number of a newer version, or 0 when there is none (or it has not been checked). */
    val available = mutableIntStateOf(0)
    val status = mutableStateOf("")
    val busy = mutableStateOf(false)

    fun currentBuild(ctx: Context): Int =
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0)).toInt()

    private fun fetch(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.useCaches = false
        c.setRequestProperty("Cache-Control", "no-cache")
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        if (c.responseCode != 200) throw java.io.IOException("HTTP " + c.responseCode)
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    /**
     * Reads the newest build number. A small text file published beside the APK is tried first,
     * because GitHub's API limits how often one network address may ask, and mobile networks
     * share addresses between many phones. The API is only the fallback.
     */
    private fun latestBuild(): Int {
        try {
            val n = fetch("$VERSION?t=" + System.currentTimeMillis()).trim().toIntOrNull()
            if (n != null) return n
        } catch (e: Exception) { /* fall through to the API */ }
        return Regex("build=(\\d+)").find(fetch(API))?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    suspend fun check(ctx: Context, quiet: Boolean = false): Int = withContext(Dispatchers.IO) {
        val mine = currentBuild(ctx)
        try {
            val latest = latestBuild()
            val newer = if (latest > mine) latest else 0
            available.intValue = newer
            if (newer > 0) status.value = "Build $newer is available (you have build $mine)."
            else if (!quiet) status.value = "You have the latest version (build $mine)."
            newer
        } catch (e: Exception) {
            if (!quiet) status.value = "Could not check for updates (" + (e.message ?: "no connection") + "). You have build $mine."
            0
        }
    }

    suspend fun downloadAndInstall(ctx: Context) {
        if (busy.value) return
        busy.value = true
        status.value = "Downloading the update…"
        try {
            val file = withContext(Dispatchers.IO) {
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                val f = File(dir, "Tasker.apk")
                val c = URL(APK).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = true
                c.connectTimeout = 15_000
                c.readTimeout = 60_000
                c.inputStream.use { input -> f.outputStream().use { out -> input.copyTo(out) } }
                f
            }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            status.value = "Tap Update on the screen that opened. Your data is kept."
        } catch (e: Exception) {
            status.value = "The download failed. Check the internet and try again."
        } finally {
            busy.value = false
        }
    }
}
