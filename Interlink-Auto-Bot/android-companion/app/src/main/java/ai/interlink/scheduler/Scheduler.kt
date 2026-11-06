package ai.interlink.scheduler

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

object Schedule {
    const val ACTION = "ai.interlink.scheduler.RUN"
    const val CHANNEL = "executions"
    fun prefs(c: Context) = c.getSharedPreferences("schedule", Context.MODE_PRIVATE)
    fun manager(c: Context) = c.getSystemService(AlarmManager::class.java)
    fun allowed(c: Context) = Build.VERSION.SDK_INT < 31 || manager(c).canScheduleExactAlarms()
    fun time(epoch: Long) = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM h:mm:ss a z"))
    fun pending(c: Context, hour: Int, time: Long = 0): PendingIntent = PendingIntent.getBroadcast(c, 100 + hour,
        Intent(c,AlarmReceiver::class.java).setAction(ACTION).putExtra("hour",hour).putExtra("scheduled",time),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun cancelLegacy(c: Context) {
        val old = PendingIntent.getBroadcast(c,1,Intent(c,AlarmReceiver::class.java).setAction(ACTION),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (old != null) { manager(c).cancel(old); old.cancel() }
    }
    fun record(c: Context, message: String) {
        val p = prefs(c)
        val lines = (p.getString("events", "") + "\n" + Instant.now() + " " + message).lines().takeLast(100)
        p.edit().putString("events",lines.joinToString("\n")).commit()
    }
    private fun updateNext(c: Context) {
        val p = prefs(c)
        val next = ExecutionPolicy.hours.map { p.getLong("alarm:$it",0) }.filter { it > 0 }.minOrNull() ?: 0
        p.edit().putLong("next",next).commit()
    }
    private fun armHour(c: Context, hour: Int) {
        val next = ExecutionPolicy.next(hour,ZonedDateTime.now())
        manager(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pending(c,hour,next))
        prefs(c).edit().putLong("alarm:$hour",next).commit()
    }
    fun recover(c: Context, source: String = "manual repair") {
        val p = prefs(c)
        if (!p.getBoolean("enabled",false)) return
        cancelLegacy(c)
        if (!allowed(c)) { record(c,"Exact-alarm permission missing. Open Alarms & reminders and repair schedule."); return }
        val now = System.currentTimeMillis()
        for (hour in ExecutionPolicy.hours) {
            val expected = p.getLong("alarm:$hour",0)
            if (expected > 0 && expected <= now && p.getLong("delivered:$hour",0) != expected) {
                record(c,"Overdue alarm detected during $source: intended ${time(expected)}; checked ${time(now)}. This is recovery, not proof of alarm delivery. No catch-up claim.")
            }
        }
        try {
            for (hour in ExecutionPolicy.hours) armHour(c,hour)
            p.edit().putInt("planVersion",2).putString("planZone",ZoneId.systemDefault().id).commit()
            updateNext(c)
            record(c,"Six independent alarms registered ($source). Next: ${time(p.getLong("next",0))}.")
        } catch (_: SecurityException) { record(c,"Alarm registration rejected. Check exact-alarm permission.") }
    }
    fun checkOnOpen(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean("enabled",false)) return
        if (p.getInt("planVersion",0) != 2 || p.getString("planZone","") != ZoneId.systemDefault().id ||
            ExecutionPolicy.hours.any { p.getLong("alarm:$it",0) <= System.currentTimeMillis() }) recover(c,"app opened")
    }
    fun rearmDelivered(c: Context, hour: Int) {
        try { if (allowed(c)) { armHour(c,hour); updateNext(c) } }
        catch (_: SecurityException) { record(c,"Could not rearm hour $hour. Exact-alarm permission changed.") }
    }
    fun disable(c: Context) {
        prefs(c).edit().putBoolean("enabled",false).remove("next").commit()
        cancelLegacy(c)
        for (hour in ExecutionPolicy.hours) { manager(c).cancel(pending(c,hour)); prefs(c).edit().remove("alarm:$hour").commit() }
        record(c,"Schedule paused. Current execution, if any, may finish.")
    }
}
class RecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val source = when(i.action) {
            Intent.ACTION_BOOT_COMPLETED -> "reboot"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "app update"
            Intent.ACTION_TIME_CHANGED -> "clock change"
            Intent.ACTION_TIMEZONE_CHANGED -> "timezone change"
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> "alarm permission granted"
            else -> return
        }
        Schedule.recover(c,source)
    }
}
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Schedule.ACTION) return
        val p = Schedule.prefs(c)
        if (!p.getBoolean("enabled",false)) return
        val hour = i.getIntExtra("hour",-1)
        val expected = i.getLongExtra("scheduled",0)
        if (hour !in ExecutionPolicy.hours || expected <= 0) { Schedule.recover(c,"legacy alarm migration"); return }
        val actual = System.currentTimeMillis()
        if (p.getLong("delivered:$hour",0) == expected || p.getLong("alarm:$hour",0) != expected) {
            Schedule.record(c,"Duplicate or replaced alarm ignored: intended ${Schedule.time(expected)}."); return
        }
        p.edit().putLong("delivered:$hour",expected).commit()
        Schedule.record(c,"Alarm delivered: intended ${Schedule.time(expected)}; actual ${Schedule.time(actual)}; delay ${(actual-expected)/1000}s.")
        Schedule.rearmDelivered(c,hour) // Only this slot: do not replace other pending deliveries.
        if (!ExecutionPolicy.inWindow(expected,actual)) {
            Schedule.record(c,"Late/early alarm skipped. Intended ${Schedule.time(expected)}; delivery ${Schedule.time(actual)}. No claim launched."); return
        }
        try { c.startForegroundService(Intent(c,RunService::class.java).putExtra("scheduled",expected).putExtra("mode","scheduled")) }
        catch (_: RuntimeException) { Schedule.record(c,"Android rejected the scheduled service start for ${Schedule.time(expected)}. Check background restrictions.") }
    }
}

class RunService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lock: PowerManager.WakeLock? = null
    private var runId: String? = null
    private var wallDeadline = 0L
    private var elapsedDeadline = 0L
    private var mode = "scheduled"
    private fun expired() = ExecutionPolicy.expired(System.currentTimeMillis(),SystemClock.elapsedRealtime(),wallDeadline,elapsedDeadline)
    private fun expire() {
        Schedule.record(this,"${ExecutionPolicy.label(mode)} deadline reached: intended stop ${Schedule.time(wallDeadline)}; handled ${Schedule.time(System.currentTimeMillis())}. Termux must stop further requests on resume.")
        if (mode != "scheduled") Schedule.prefs(this).edit().putString("manualResult","Execution deadline reached. Check the on-demand log; submitted claims may be uncertain.").commit()
        cleanup()
    }
    private val tick = object : Runnable {
        override fun run() { if (expired()) { expire(); stopSelf() } else handler.postDelayed(this,1000) }
    }
    override fun onBind(i: Intent?) = null
    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (runId != null) {
            if (expired()) expire()
            else { Schedule.record(this,"Execution busy: ${ExecutionPolicy.label(mode)} already active; duplicate launch skipped."); return START_NOT_STICKY }
        }
        val expected = i?.getLongExtra("scheduled",0) ?: 0
        mode = i?.getStringExtra("mode")?.takeIf { it == "test" || it == "claim" } ?: "scheduled"
        val actual = System.currentTimeMillis()
        wallDeadline = expected + if (mode == "scheduled") 480_000 else 120_000
        val remaining = wallDeadline - actual
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(Schedule.CHANNEL,"Executions",NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        startForeground(1,Notification.Builder(this,Schedule.CHANNEL).setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(ExecutionPolicy.label(mode)).setContentText("Execution active until ${Schedule.time(wallDeadline)}")
            .setContentIntent(open).build())
        if (expected <= 0 || actual < expected || remaining !in 1..480_000) {
            Schedule.record(this,"${ExecutionPolicy.label(mode)} rejected before dispatch: original deadline already passed or timestamp invalid."); stopSelf(); return START_NOT_STICKY
        }
        elapsedDeadline = SystemClock.elapsedRealtime() + remaining
        val id = UUID.randomUUID().toString(); runId = id
        val p = Schedule.prefs(this)
        val previous = p.getString("runIds","").orEmpty().split(',').filter { it.isNotEmpty() }
        val edit = p.edit()
        for (old in previous.dropLast(39)) edit.remove("runMode:$old").remove("runTime:$old")
        edit.putString("runIds",(previous.takeLast(39)+id).joinToString(","))
            .putString("runMode:$id",mode).putLong("runTime:$id",expected)
            .putString("activeId",id).putLong("active",actual).putLong("activeUntil",wallDeadline).putString("activeMode",mode)
        if (mode != "scheduled") edit.putString("lastManualId",id).putString("manualResult","Starting ${ExecutionPolicy.label(mode).lowercase()}…")
        edit.commit()
        val power = getSystemService(PowerManager::class.java)
        Schedule.record(this,"${ExecutionPolicy.label(mode)} starting: intended ${Schedule.time(expected)}; actual ${Schedule.time(actual)}; battery exemptions scheduler=${power.isIgnoringBatteryOptimizations(packageName)}, Termux=${power.isIgnoringBatteryOptimizations("com.termux")}.")
        lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Interlink:execution").also { it.acquire(remaining) }
        handler.postDelayed(tick,1000)
        val base = "/data/data/com.termux/files"
        val work = "$base/home/Helping_hands/Interlink-Auto-Bot"
        val callback = PendingIntent.getBroadcast(this,2,Intent(this,ResultReceiver::class.java)
            .setData(Uri.parse("interlink-result://run/$id")),PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        val command = Intent("com.termux.RUN_COMMAND").setClassName("com.termux","com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH","$base/usr/bin/bash")
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", if (mode == "scheduled") arrayOf("$work/run-scheduled.sh","--scheduled-at",expected.toString()) else arrayOf("$work/run-on-demand.sh",mode))
            .putExtra("com.termux.RUN_COMMAND_WORKDIR",work).putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
            .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback)
        try {
            if (startService(command) == null) throw IllegalStateException()
            Schedule.record(this,"${ExecutionPolicy.label(mode)} command sent to Termux; awaiting completion. Intended start ${Schedule.time(expected)}.")
        } catch (_: RuntimeException) {
            callback.cancel()
            if (mode != "scheduled") p.edit().putString("manualResult","Could not start Termux. Check permissions and background settings.").commit()
            Schedule.record(this,"${ExecutionPolicy.label(mode)} dispatch failed. Check Termux command permission and background settings."); stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        if (lock?.isHeld == true) lock?.release()
        lock = null
        val p = Schedule.prefs(this)
        if (p.getString("activeId",null) == runId) p.edit().remove("activeId").remove("active").remove("activeUntil").remove("activeMode").commit()
        runId = null
    }
    override fun onDestroy() { cleanup(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }
    override fun onTimeout(startId: Int, fgsType: Int) { expire(); stopSelf() }
}
class ResultReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.data?.lastPathSegment ?: return
        val p = Schedule.prefs(c)
        val mode = p.getString("runMode:$id",null) ?: return
        val result = i.getBundleExtra("result")
        val exit = if (result?.containsKey("exitCode") == true) result.getInt("exitCode") else -1
        val active = p.getString("activeId",null) == id
        val expected = p.getLong("runTime:$id",0)
        val message = ExecutionPolicy.outcome(mode,exit)
        Schedule.record(c,"$message Intended start ${Schedule.time(expected)}.${if (!active) " Completion arrived after service ended; this is not a new run." else ""}")
        if (mode != "scheduled" && p.getString("lastManualId",null) == id) p.edit().putString("manualResult",message).commit()
        p.edit().remove("runMode:$id").remove("runTime:$id").commit()
        if (active) c.stopService(Intent(c,RunService::class.java))
    }
}
