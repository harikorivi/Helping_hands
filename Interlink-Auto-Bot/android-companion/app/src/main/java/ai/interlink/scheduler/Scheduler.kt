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
        if (running) return START_NOT_STICKY
        val scheduled = i?.getLongExtra("scheduled", 0) ?: 0
        val remaining = scheduled + 480_000 - System.currentTimeMillis()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(Schedule.CHANNEL,"Scheduled claims",NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this,0,Intent(this, MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this,Schedule.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync).setContentTitle("Interlink scheduled execution")
            .setContentText("Two claim windows; stops by minute 34").setContentIntent(open).build())
        if (remaining !in 1..480_000) { stopSelf(); return START_NOT_STICKY }
        running = true
        Schedule.prefs(this).edit().putLong("active",scheduled).commit()
        lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Interlink:execution")
        lock!!.acquire(remaining)
        handler.postDelayed({ Schedule.record(this,"EXECUTION_DEADLINE"); stopSelf() },remaining)
        val base = "/data/data/com.termux/files"
        val work = "$base/home/Helping_hands/Interlink-Auto-Bot"
        val result = PendingIntent.getBroadcast(this,2,
            Intent(this,ResultReceiver::class.java).putExtra("scheduled",scheduled),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        val command = Intent("com.termux.RUN_COMMAND").setClassName("com.termux","com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH","$base/usr/bin/bash")
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS",arrayOf("$work/run-scheduled.sh"))
            .putExtra("com.termux.RUN_COMMAND_WORKDIR",work)
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
            .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",result)
        try {
            if (startService(command) == null) throw IllegalStateException()
            Schedule.record(this,"TERMUX_DISPATCHED; not a claim result")
        } catch (_: RuntimeException) { Schedule.record(this,"TERMUX_DISPATCH_FAILED; check permission and battery settings"); stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (lock?.isHeld == true) lock?.release()
        Schedule.prefs(this).edit().remove("active").commit()
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
        // Never copy stdout/stderr, which may include account information.
        Schedule.record(c,"TERMUX_COMPLETED exit=$exit; see private Node ledger for window results")
        c.stopService(Intent(c,RunService::class.java))
    }
}

class MainActivity : Activity() {
    private lateinit var status: TextView
    private fun button(layout: LinearLayout, title: String, action: () -> Unit) {
        layout.addView(Button(this).apply { text = title; setOnClickListener { action(); refresh() } })
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        layout.addView(TextView(this).apply { text = "Six local-time runs: 01,05,09,13,17,21 at :26. Requires Termux at the documented path. Allow exact alarms, Termux commands and notifications. For Doze execution, exempt BOTH apps from battery optimization. No force-stop recovery is promised." })
        button(layout,"Grant commands / notifications") {
            val permissions = mutableListOf("com.termux.permission.RUN_COMMAND")
            if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(permissions.toTypedArray(),1)
        }
        button(layout,"Alarms and reminders access") {
            if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
        button(layout,"Battery settings (both apps)") { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        button(layout,"Enable / repair schedule") {
            if (Schedule.allowed(this) && checkSelfPermission("com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED) {
                Schedule.prefs(this).edit().putBoolean("enabled",true).commit(); Schedule.recover(this)
            } else Schedule.record(this,"GRANT_PERMISSIONS_FIRST")
        }
        button(layout,"Disable future runs") {
            Schedule.prefs(this).edit().putBoolean("enabled",false).remove("next").commit()
            Schedule.manager(this).cancel(Schedule.pending(this))
            Schedule.record(this,"DISABLED; current run may finish")
        }
        status = TextView(this); layout.addView(status)
        setContentView(ScrollView(this).apply { addView(layout) })
    }
    override fun onResume() { super.onResume(); refresh() }
    private fun refresh() {
        val p = Schedule.prefs(this)
        status.text = "Enabled: ${p.getBoolean("enabled",false)}\nExact access: ${Schedule.allowed(this)}\nNext: ${p.getLong("next",0).let { if (it == 0L) "none" else Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toString() }}\n${p.getString("events","")}"
    }
}
