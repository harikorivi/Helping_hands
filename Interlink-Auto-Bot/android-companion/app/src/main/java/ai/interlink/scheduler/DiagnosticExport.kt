package ai.interlink.scheduler

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.annotation.TargetApi
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Exports only the companion's diagnostic data, never Termux files or credentials. */
object DiagnosticExport {
    fun fileName(): String = "Interlink-diagnostics-" +
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")) +
        "-${UUID.randomUUID().toString().take(8)}.txt"

    fun snapshot(c: Context): String {
        val p = Schedule.prefs(c)
        val version = c.packageManager.getPackageInfo(c.packageName, 0).versionName
        return "Interlink Scheduler $version\nExported: ${java.time.Instant.now()}\n" +
            "Mode: six user-visible alarm clocks; bounded execution service\nService running: ${RunService.running}; wake lock: ${RunService.wakeHeld}\nEnabled: ${p.getBoolean("enabled", false)}\nLive debug logging: ${Diagnostics.liveDebug(c)}\n" +
            "48-hour trial evidence: ${Diagnostics.trialEvidence(c)}\nCurrent device state (not historical): ${Diagnostics.device(c)}\n" +
            "Background test: ${p.getString("probeStatus", "Not run yet")}\n\n" +
            ExecutionPolicy.hours.joinToString("\n") { hour ->
                val at = p.getLong("slot:$hour", 0)
                "Hour $hour: ${if (at > 0) Schedule.time(at) else "not planned"}; last registration accepted: ${p.getBoolean("armed:$hour",false)}"
            } + "\n\n${Diagnostics.dashboard(c)}\n\nRecent activity:\n" +
            p.getString("events", "").orEmpty() + "\n\nStructured events:\n" + Diagnostics.report(c)
    }

    fun write(c: Context, uri: Uri, report: String) {
        val stream = c.contentResolver.openOutputStream(uri, "wt")
            ?: throw java.io.IOException("Output unavailable")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(report) }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    fun saveToDownloads(c: Context, name: String, report: String): Uri {
        val resolver = c.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Interlink Scheduler")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw java.io.IOException("Downloads unavailable")
        try {
            write(c, uri, report)
            val published = resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            if (published != 1) throw java.io.IOException("Could not publish export")
            return uri
        } catch (error: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) { }
            throw error
        }
    }
}
