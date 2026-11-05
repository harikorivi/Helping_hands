package ai.interlink.scheduler

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.*
import java.time.*

object Schedule {
    const val ACTION = "ai.interlink.scheduler.RUN"
    const val CHANNEL = "executions"
    fun prefs(c: Context) = c.getSharedPreferences("schedule", Context.MODE_PRIVATE)
    fun manager(c: Context) = c.getSystemService(AlarmManager::class.java)
    fun allowed(c: Context) = Build.VERSION.SDK_INT < 31 || manager(c).canScheduleExactAlarms()
    fun pending(c: Context, time: Long = 0): PendingIntent = PendingIntent.getBroadcast(c, 1,
        Intent(c, AlarmReceiver::class.java).setAction(ACTION).putExtra("scheduled", time),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun record(c: Context, message: String) {
        val p = prefs(c)
        val lines = (p.getString("events", "") + "\n" + Instant.now() + " " + message).lines().takeLast(50)
        p.edit().putString("events", lines.joinToString("\n")).commit()
    }
    fun arm(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean("enabled", false)) return
        if (!allowed(c)) { record(c, "EXACT_ALARM_PERMISSION_REQUIRED"); return }
        val now = ZonedDateTime.now()
        val next = (0L..2L).flatMap { day -> listOf(1,5,9,13,17,21).map { hour ->
            now.toLocalDate().plusDays(day).atTime(hour,26).atZone(now.zone)
        }}.filter { it.isAfter(now) }.minBy { it.toInstant() }.toInstant().toEpochMilli()
        try {
            manager(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending(c,next))
            p.edit().putLong("next", next).commit()
        } catch (_: SecurityException) { record(c, "ALARM_PERMISSION_REVOKED") }
    }
    fun recover(c: Context) {
        val expected = prefs(c).getLong("next", 0)
        if (expected > 0 && expected < System.currentTimeMillis()) record(c, "MISSED_SCHEDULE since=$expected; no catch-up claims")
        arm(c)
    }
}

class RecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Schedule.recover(c) }
}
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (!Schedule.prefs(c).getBoolean("enabled", false)) return
        val scheduled = i.getLongExtra("scheduled", 0)
        Schedule.arm(c) // Persist next alarm before handoff, including on a failed launch.
        val now = System.currentTimeMillis()
        if (scheduled <= 0 || now < scheduled || now >= scheduled + 480_000) {
            Schedule.record(c,"MISSED_SCHEDULE"); return
        }
        try {
            c.startForegroundService(Intent(c, RunService::class.java).putExtra("scheduled", scheduled))
        } catch (_: RuntimeException) { Schedule.record(c,"BACKGROUND_START_REJECTED") }
    }
}

class RunService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lock: PowerManager.WakeLock? = null
    private var running = false
    override fun onBind(i: Intent?) = null
    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (running) { Schedule.record(this,"EXECUTION_BUSY"); return START_NOT_STICKY }
        val scheduled = i?.getLongExtra("scheduled", 0) ?: 0
        val mode = i?.getStringExtra("mode")?.takeIf { it == "test" || it == "claim" } ?: "scheduled"
        val remaining = scheduled + (if (mode == "scheduled") 480_000 else 120_000) - System.currentTimeMillis()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(Schedule.CHANNEL,"Scheduled claims",NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this,0,Intent(this, MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this,Schedule.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync).setContentTitle(if (mode == "test") "Testing Termux connection" else if (mode == "claim") "Checking one eligible claim" else "Interlink scheduled execution")
            .setContentText(if (mode == "scheduled") "Two claim windows; stops by minute 34" else "On-demand action; stops within two minutes").setContentIntent(open).build())
        if (remaining !in 1..480_000) { stopSelf(); return START_NOT_STICKY }
        running = true
        Schedule.prefs(this).edit().putLong("active",scheduled).putString("activeMode",mode).commit()
        lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Interlink:execution")
        lock!!.acquire(remaining)
        handler.postDelayed({
            if (mode != "scheduled") Schedule.prefs(this).edit().putString("manualResult","Timed out. Check the Termux on-demand log; a submitted claim may have an unknown outcome.").commit()
            Schedule.record(this,"EXECUTION_DEADLINE"); stopSelf()
        },remaining)
        val base = "/data/data/com.termux/files"
        val work = "$base/home/Helping_hands/Interlink-Auto-Bot"
        val result = PendingIntent.getBroadcast(this,2,
            Intent(this,ResultReceiver::class.java).putExtra("scheduled",scheduled),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        val command = Intent("com.termux.RUN_COMMAND").setClassName("com.termux","com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH","$base/usr/bin/bash")
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS",if (mode == "scheduled") arrayOf("$work/run-scheduled.sh") else arrayOf("$work/run-on-demand.sh",mode))
            .putExtra("com.termux.RUN_COMMAND_WORKDIR",work)
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
            .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",result)
        try {
            if (startService(command) == null) throw IllegalStateException()
            if (mode != "scheduled") Schedule.prefs(this).edit().putString("manualResult","Running… waiting for Termux to finish.").commit()
            Schedule.record(this,"TERMUX_DISPATCHED; not a claim result")
        } catch (_: RuntimeException) {
            if (mode != "scheduled") Schedule.prefs(this).edit().putString("manualResult","Could not start Termux. Check command permission and setup.").commit()
            Schedule.record(this,"TERMUX_DISPATCH_FAILED; check permission and battery settings"); stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (lock?.isHeld == true) lock?.release()
        Schedule.prefs(this).edit().remove("active").remove("activeMode").commit()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }
}
class ResultReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.getLongExtra("scheduled",-1) != Schedule.prefs(c).getLong("active",0)) return
        val result = i.getBundleExtra("result")
        val exit = if (result?.containsKey("exitCode") == true) result.getInt("exitCode") else -1
        val mode = Schedule.prefs(c).getString("activeMode","scheduled")
        if (mode != "scheduled") {
            val message = when (exit) {
                0 -> if (mode == "test") "Connection test passed. Authentication and API reads worked. No claim was submitted." else "One claim confirmed by the API and updated claim timestamp."
                10 -> "Server reports not eligible. No claim submitted."
                11 -> "Authentication failed or device credentials are missing. Check the private log; manual login may be required."
                12 -> "A request failed. Check the on-demand log for the request stage and HTTP status."
                13 -> "The API response was not in the expected format. Check the on-demand log."
                14 -> "Another bot run is active. Wait for it to finish."
                20 -> "Too close to a scheduled window or its deadline. Let the scheduled run finish first."
                21 -> "Claim outcome could not be confirmed. Do not retry; review the on-demand log. Further manual claims are blocked until the outcome is resolved."
                22 -> "Please wait at least one minute between manual claim attempts."
                23 -> "This scheduled claim window was already attempted. No duplicate claim submitted."
                124, 137 -> "Execution timed out. Check the log; any submitted claim may have an unknown outcome."
                126, 127 -> "Bot files or commands are missing. Update the Termux project, including run-on-demand.sh and on-demand.js."
                else -> "Termux returned exit $exit. Update the bot files and inspect the private on-demand log."
            }
            Schedule.prefs(c).edit().putString("manualResult",message).commit()
        }
        // Never copy stdout/stderr, which may include account information.
        Schedule.record(c,"TERMUX_COMPLETED exit=$exit; see private Node ledger for window results")
        c.stopService(Intent(c,RunService::class.java))
    }
}
