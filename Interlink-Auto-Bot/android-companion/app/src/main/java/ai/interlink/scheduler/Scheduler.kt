package ai.interlink.scheduler

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.json.JSONObject

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
    @Synchronized
    fun record(c: Context, message: String) {
        val p = prefs(c)
        val lines = LogRetention.retain(p.getString("events", "") + "\n" + Instant.now() + " " + message.replace("\n"," | "))
        p.edit().putString("events",lines.joinToString("\n")).commit()
    }
    private fun updateNext(c: Context) {
        val p = prefs(c)
        val next = ExecutionPolicy.hours.map { p.getLong("alarm:$it",0) }.filter { it > 0 }.minOrNull() ?: 0
        p.edit().putLong("next",next).commit()
    }
    private fun armHour(c: Context, hour: Int) {
        val next = ExecutionPolicy.next(hour,ZonedDateTime.now())
        Diagnostics.emit(c,"ALARM_PERMISSION_CHECKED",next,status=if(allowed(c)) "available" else "unavailable")
        try { manager(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pending(c,hour,next)) }
        catch(error:RuntimeException) { Diagnostics.emit(c,"ALARM_SCHEDULE_FAILED",next,status="failed",reason=error.javaClass.simpleName); throw error }
        prefs(c).edit().putLong("alarm:$hour",next).commit()
        Diagnostics.registered(c,next)
        Diagnostics.emit(c,"ALARM_SCHEDULED",next,status="registered",details=JSONObject().put("requestCode",100+hour))
        record(c,"Alarm registration accepted: hour $hour; intended ${time(next)}; RTC_WAKEUP / setExactAndAllowWhileIdle. Registration is not delivery.")
    }
    fun recover(c: Context, source: String = "manual repair", launch: (Intent) -> Unit = { c.startForegroundService(it) }) {
        val p = prefs(c)
        if (!p.getBoolean("enabled",false)) return
        cancelLegacy(c)
        if (!allowed(c)) { Diagnostics.emit(c,"ALARM_PERMISSION_CHECKED",status="unavailable",reason="capability_unavailable"); record(c,"Exact-alarm permission missing. Open Alarms & reminders and repair schedule."); return }
        val now = System.currentTimeMillis()
        Diagnostics.emit(c,"RECOVERY_ATTEMPTED",reason=source)
        for (hour in ExecutionPolicy.hours) {
            val expected = p.getLong("alarm:$hour",0)
            if (expected > 0 && expected <= now && p.getLong("delivered:$hour",0) != expected) {
                if (ExecutionPolicy.inWindow(expected,now) && source in listOf("app opened","manual repair","monitor restart")) {
                    Diagnostics.emit(c,"RECOVERY_ATTEMPTED",expected,status="within_original_window",reason=source)
                    record(c,"Recovering remaining windows during $source: intended ${time(expected)}; checked ${time(now)}. Original deadline retained; this is not alarm delivery.")
                    AlarmReceiver().receive(c,Intent(c,AlarmReceiver::class.java).setAction(ACTION)
                        .putExtra("hour",hour).putExtra("scheduled",expected).putExtra("recovery",true),launch)
                } else {
                    Diagnostics.emit(c,"ALARM_MISSED",expected,status="no_delivery_evidence",reason="overdue_at_reconciliation")
                    Diagnostics.outcome(c,expected,"missed","no_receiver_delivery_evidence_at_reconciliation")
                    record(c,"Overdue alarm detected during $source: intended ${time(expected)}; checked ${time(now)}. No execution launched outside foreground recovery; no expired claims replayed.")
                }
            }
        }
        try {
            for (hour in ExecutionPolicy.hours) {
                val old=p.getLong("alarm:$hour",0)
                val rebuild=source in listOf("reboot","app update","clock change","timezone change","alarm permission granted","manual repair")
                if(old<=now || rebuild) {
                    val target=ExecutionPolicy.next(hour,ZonedDateTime.now())
                    if(old>now && old!=target) { Diagnostics.emit(c,"ALARM_CANCELLED",old,reason=source); Diagnostics.outcome(c,old,"cancelled",source) }
                    armHour(c,hour)
                }
            }
            p.edit().putInt("planVersion",2).putString("planZone",ZoneId.systemDefault().id).commit()
            updateNext(c)
            record(c,"Six independent alarms registered ($source). Next: ${time(p.getLong("next",0))}.")
            PersistentMonitorService.refresh(c)
        } catch (_: SecurityException) { record(c,"Alarm registration rejected. Check exact-alarm permission.") }
    }
    @Synchronized
    fun pruneHistory(c: Context) {
        val p = prefs(c)
        val old = p.getString("events", "").orEmpty()
        val retained = LogRetention.retain(old).joinToString("\n")
        if (retained != old) p.edit().putString("events",retained).commit()
    }
    fun checkOnOpen(c: Context) {
        pruneHistory(c)
        Diagnostics.prune(c)
        Diagnostics.reconcileActive(c)
        Diagnostics.processHistory(c)
        Diagnostics.emit(c,"APP_OPENED")
        BackgroundTest.check(c)
        val p = prefs(c)
        if (!p.getBoolean("enabled",false)) return
        if (p.getInt("planVersion",0) != 2 || p.getString("planZone","") != ZoneId.systemDefault().id ||
            ExecutionPolicy.hours.any { p.getLong("alarm:$it",0) <= System.currentTimeMillis() }) recover(c,"app opened")
        if (PersistentMonitorService.desired(c) && !PersistentMonitorService.active) PersistentMonitorService.start(c,"app_opened")
    }
    fun rearmDelivered(c: Context, hour: Int) {
        try { if (allowed(c)) { armHour(c,hour); updateNext(c) } }
        catch (_: SecurityException) { record(c,"Could not rearm hour $hour. Exact-alarm permission changed.") }
    }
    fun disable(c: Context) {
        PersistentMonitorService.stop(c)
        prefs(c).edit().putBoolean("enabled",false).remove("next").commit()
        cancelLegacy(c)
        BackgroundTest.cancel(c)
        for (hour in ExecutionPolicy.hours) { val at=prefs(c).getLong("alarm:$hour",0); if(at>0) { Diagnostics.emit(c,"ALARM_CANCELLED",at,reason="user_paused"); Diagnostics.outcome(c,at,"cancelled","user_paused") }; manager(c).cancel(pending(c,hour)); prefs(c).edit().remove("alarm:$hour").commit() }
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
        if(PersistentMonitorService.desired(c)) PersistentMonitorService.start(c,source)
    }
}
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = receive(c,i)
    fun receive(c: Context, i: Intent, launch: (Intent) -> Unit = { c.startForegroundService(it) }) {
        val entered=System.currentTimeMillis()
        val receivedAt=i.getLongExtra("scheduled",0)
        val recovered=i.getBooleanExtra("recovery",false)
        Diagnostics.emit(c,if(recovered) "RECOVERY_ATTEMPTED" else "RECEIVER_STARTED",receivedAt,actual=entered)
        if (i.action != Schedule.ACTION) { Diagnostics.emit(c,"RECEIVER_REJECTED",receivedAt,reason="wrong_action"); return }
        val p = Schedule.prefs(c)
        if (!p.getBoolean("enabled",false)) { Diagnostics.emit(c,"RECEIVER_REJECTED",receivedAt,reason="schedule_disabled"); return }
        val hour = i.getIntExtra("hour",-1)
        val expected = i.getLongExtra("scheduled",0)
        if (hour !in ExecutionPolicy.hours || expected <= 0) { Diagnostics.emit(c,"RECEIVER_REJECTED",receivedAt,reason="invalid_payload"); Schedule.recover(c,"legacy alarm migration"); return }
        val actual = System.currentTimeMillis()
        if (p.getLong("delivered:$hour",0) == expected || p.getLong("alarm:$hour",0) != expected) {
            Diagnostics.emit(c,"RECEIVER_REJECTED",expected,reason="duplicate_or_replaced");
            Schedule.record(c,"Duplicate or replaced alarm ignored: intended ${Schedule.time(expected)}."); return
        }
        p.edit().putLong("delivered:$hour",expected).commit()
        val recovery = i.getBooleanExtra("recovery",false)
        if(i.getBooleanExtra("monitor",false)) {
            Diagnostics.emit(c,"MONITOR_SLOT_ACCEPTED",expected,status="delivered_by_monitor",reason="continuous_clock")
            Diagnostics.outcome(c,expected,"received_monitor","continuous_monitor_triggered")
        }
        if(!recovery) {
            Diagnostics.emit(c,"ALARM_RECEIVED",expected,status="delivered",details=JSONObject().put("delayMs",actual-expected),actual=actual)
            Diagnostics.outcome(c,expected,"received","",delivered=actual)
        }
        Schedule.record(c,"${if (i.getBooleanExtra("monitor",false)) "Continuous monitor triggered" else if (recovery) "Foreground recovery accepted" else "Alarm delivered"}: intended ${Schedule.time(expected)}; actual ${Schedule.time(actual)}; delay ${(actual-expected)/1000}s.")
        Schedule.rearmDelivered(c,hour) // Only this slot: do not replace other pending deliveries.
        if (!ExecutionPolicy.inWindow(expected,actual)) {
            Diagnostics.emit(c,"EXECUTION_FAILED",expected,status="skipped",reason="outside_original_window"); Diagnostics.outcome(c,expected,"failed","outside_original_window");
            Schedule.record(c,"Late/early alarm skipped. Intended ${Schedule.time(expected)}; delivery ${Schedule.time(actual)}. No claim launched."); return
        }
        Diagnostics.emit(c,"EXECUTION_DISPATCH_REQUESTED",expected,status="requested")
        try { launch(Intent(c,RunService::class.java).putExtra("scheduled",expected).putExtra("mode","scheduled")) }
        catch (error: RuntimeException) { Diagnostics.emit(c,"EXECUTION_FAILED",expected,status="dispatch_failed",reason=error.javaClass.simpleName); Diagnostics.outcome(c,expected,"failed","service_start_rejected"); Schedule.record(c,"Scheduled service start rejected: ${error.javaClass.simpleName}; intended ${Schedule.time(expected)}. No Termux dispatch.") }
    }
}

class RunService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lock: PowerManager.WakeLock? = null
    private var runId: String? = null
    private var wallDeadline = 0L
    private var elapsedDeadline = 0L
    private var mode = "scheduled"
    private var scheduledAt = 0L
    private var executionId = ""
    private fun expired() = ExecutionPolicy.expired(System.currentTimeMillis(),SystemClock.elapsedRealtime(),wallDeadline,elapsedDeadline)
    private fun expire() {
        Diagnostics.emit(this,"EXECUTION_FAILED",scheduledAt,executionId,status="unknown",reason="deadline_without_completion",details=JSONObject().put("attemptId",runId))
        if(mode=="scheduled") Diagnostics.outcome(this,scheduledAt,"unknown","deadline_without_completion; claim outcome unknown")
        Schedule.record(this,"${ExecutionPolicy.label(mode)} deadline reached: intended stop ${Schedule.time(wallDeadline)}; handled ${Schedule.time(System.currentTimeMillis())}. Termux must stop further requests on resume.")
        if (mode != "scheduled") Schedule.prefs(this).edit().putString("manualResult","Execution deadline reached. Check the on-demand log; submitted claims may be uncertain.").commit()
        if (Schedule.prefs(this).getBoolean("runProbe:$runId",false) && Schedule.prefs(this).getLong("probeLatest",0)==Schedule.prefs(this).getLong("runTime:$runId",-1)) Schedule.prefs(this).edit().putString("probeStatus","Alarm delivered; connection execution deadline reached.").commit()
        cleanup()
    }
    private val tick = object : Runnable {
        override fun run() { if (expired()) { expire(); stopSelf() } else handler.postDelayed(this,1000) }
    }
    override fun onBind(i: Intent?) = null
    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (runId != null) {
            if (expired()) expire()
            else { Diagnostics.emit(this,"EXECUTION_FAILED",i?.getLongExtra("scheduled",0) ?: 0,status="busy",reason="service_already_active"); Schedule.record(this,"Execution busy: ${ExecutionPolicy.label(mode)} already active; duplicate launch skipped."); return START_NOT_STICKY }
        }
        val expected = i?.getLongExtra("scheduled",0) ?: 0
        scheduledAt=expected
        mode = i?.getStringExtra("mode")?.takeIf { it == "test" || it == "claim" } ?: "scheduled"
        val actual = System.currentTimeMillis()
        executionId=if(mode=="scheduled") Diagnostics.id(expected) else "$mode-$expected"
        Diagnostics.emit(this,"SERVICE_STARTED",expected,executionId)
        Schedule.record(this,"Run service entered: mode=$mode; intended=${Schedule.time(expected)}; actual=${Schedule.time(actual)}.")
        wallDeadline = expected + if (mode == "scheduled") 480_000 else 120_000
        val remaining = wallDeadline - actual
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(Schedule.CHANNEL,"Executions",NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        try { startForeground(1,Notification.Builder(this,Schedule.CHANNEL).setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(ExecutionPolicy.label(mode)).setContentText("Execution active until ${Schedule.time(wallDeadline)}")
            .setContentIntent(open).build()) } catch(error:RuntimeException) {
            Diagnostics.emit(this,"EXECUTION_FAILED",expected,executionId,status="failed",reason="foreground_promotion_${error.javaClass.simpleName}")
            if(mode=="scheduled") Diagnostics.outcome(this,expected,"failed","foreground_promotion_rejected")
            stopSelf(); return START_NOT_STICKY
        }
        if (expected <= 0 || actual < expected || remaining !in 1..480_000) {
            Diagnostics.emit(this,"EXECUTION_FAILED",expected,executionId,status="skipped",reason="service_started_outside_deadline")
            Schedule.record(this,"${ExecutionPolicy.label(mode)} rejected before dispatch: original deadline already passed or timestamp invalid."); stopSelf(); return START_NOT_STICKY
        }
        elapsedDeadline = SystemClock.elapsedRealtime() + remaining
        val id = UUID.randomUUID().toString(); runId = id
        val p = Schedule.prefs(this)
        val previous = p.getString("runIds","").orEmpty().split(',').filter { it.isNotEmpty() }
        val edit = p.edit()
        for (old in previous.dropLast(39)) edit.remove("runExecution:$old").remove("runMode:$old").remove("runTime:$old").remove("runProbe:$old")
        edit.putString("runIds",(previous.takeLast(39)+id).joinToString(","))
            .putString("runExecution:$id",executionId).putString("runMode:$id",mode).putLong("runTime:$id",expected).putBoolean("runProbe:$id",i?.getBooleanExtra("probe",false) == true)
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
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", if (mode == "scheduled") arrayOf("$work/run-scheduled.sh","--scheduled-at",expected.toString(),"--execution-id",executionId,"--attempt-id",id) else arrayOf("$work/run-on-demand.sh",mode))
            .putExtra("com.termux.RUN_COMMAND_WORKDIR",work).putExtra("com.termux.RUN_COMMAND_BACKGROUND",true)
            .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback)
        try {
            Diagnostics.emit(this,"EXECUTION_DISPATCH_REQUESTED",expected,executionId,status="termux_requested",details=JSONObject().put("attemptId",id))
            if (startService(command) == null) throw IllegalStateException()
            Diagnostics.emit(this,"TERMUX_DISPATCHED",expected,executionId,status="requested_not_node_confirmed",details=JSONObject().put("attemptId",id))
            Schedule.record(this,"${ExecutionPolicy.label(mode)} command sent to Termux; awaiting completion. Intended start ${Schedule.time(expected)}.")
        } catch (error: RuntimeException) {
            Diagnostics.emit(this,"EXECUTION_FAILED",expected,executionId,status="dispatch_failed",reason="termux_${error.javaClass.simpleName}")
            if(mode=="scheduled") Diagnostics.outcome(this,expected,"failed","termux_dispatch_failed")
            callback.cancel()
            if (mode != "scheduled") p.edit().putString("manualResult","Could not start Termux. Check permissions and background settings.").commit()
            if (p.getBoolean("runProbe:$id",false) && p.getLong("probeLatest",0)==expected) p.edit().putString("probeStatus","Alarm delivered; Termux dispatch failed. Check command permission and Termux installation.").commit()
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
        val termuxError=if(result?.containsKey("err")==true) result.getInt("err") else -1
        if(termuxError!=-1) Diagnostics.emit(c,"TERMUX_COMMAND_FAILED",p.getLong("runTime:$id",0),reason="termux_internal_error",details=JSONObject().put("errorCode",termuxError))
        val active = p.getString("activeId",null) == id
        val expected = p.getLong("runTime:$id",0)
        val executionId=p.getString("runExecution:$id",null) ?: if(mode=="scheduled") Diagnostics.id(expected) else "$mode-$expected"
        val parsed=if(mode=="scheduled") ExecutionReport.parse(result?.getString("stdout"),executionId,id,expected) else null
        var message = ExecutionPolicy.outcome(mode,exit)
        if(mode=="scheduled") {
            message=parsed?.summary ?: "Termux callback exit=$exit. No valid correlated Node report; window results UNKNOWN. Update bot source and inspect private log."
            if(parsed!=null) for(e in parsed.events) Diagnostics.emit(c,e.getString("event"),expected,executionId,status=e.optString("result","observed"),reason=e.optString("reason"),details=e,actual=e.getLong("actualAt"),source="node")
            val status=if(parsed?.successful==2) "success" else if(parsed?.complete==true) "incomplete" else "unknown"
            Diagnostics.outcome(c,expected,status,if(parsed==null) "node_report_missing_or_invalid" else "window_results",message)
        }
        Diagnostics.emit(c,if(mode=="scheduled" && parsed?.successful!=2 || exit!=0) "EXECUTION_FAILED" else "EXECUTION_COMPLETED",expected,executionId,
            status=if(exit==0) "callback_received" else "nonzero_exit",reason=if(mode=="scheduled" && parsed==null) "node_report_missing_or_invalid" else "see_window_results",details=JSONObject().put("attemptId",id).put("exitCode",exit))
        if (p.getBoolean("runProbe:$id",false) && p.getLong("probeLatest",0)==expected) p.edit().putString("probeStatus","Alarm delivered. $message").commit()
        Schedule.record(c,"$message Intended start ${Schedule.time(expected)}.${if (!active) " Completion arrived after service ended; this is not a new run." else ""}")
        if (mode != "scheduled" && p.getString("lastManualId",null) == id) p.edit().putString("manualResult",message).commit()
        p.edit().remove("runExecution:$id").remove("runMode:$id").remove("runTime:$id").remove("runProbe:$id").commit()
        if (active) c.stopService(Intent(c,RunService::class.java))
    }
}
