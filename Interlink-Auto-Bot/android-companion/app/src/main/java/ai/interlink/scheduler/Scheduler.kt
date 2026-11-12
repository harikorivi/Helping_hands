package ai.interlink.scheduler

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executors

/** Durable six-slot plan. AlarmManager is used only to cancel alarms from earlier releases. */
object Schedule {
    fun prefs(c:Context)=c.getSharedPreferences("schedule",Context.MODE_PRIVATE)
    fun time(at:Long)=Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM h:mm:ss a z"))
    fun enabled(c:Context)=prefs(c).getBoolean("enabled",false)
    fun record(c:Context,message:String)=Diagnostics.work {
        val p=prefs(c)
        p.edit().putString("events",LogRetention.retain(p.getString("events","")+"\n"+Instant.now()+" "+message.replace('\n',' ')).joinToString("\n")).commit()
    }
    @Synchronized fun migrate(c:Context) {
        val p=prefs(c)
        if(p.getInt("servicePlanVersion",0)==1) return
        val am=c.getSystemService(AlarmManager::class.java)
        for((code,receiver,action) in listOf(Triple(1,"AlarmReceiver","ai.interlink.scheduler.RUN"),Triple(900,"BackgroundTestReceiver","ai.interlink.scheduler.TEST_ALARM"))+
            ExecutionPolicy.hours.map { Triple(100+it,"AlarmReceiver","ai.interlink.scheduler.RUN") }) {
            val intent=Intent().setClassName(c.packageName,"${c.packageName}.$receiver").setAction(action)
            PendingIntent.getBroadcast(c,code,intent,PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { am.cancel(it); it.cancel() }
        }
        val edit=p.edit().putInt("servicePlanVersion",1).remove("continuousMonitor").remove("wakeLockCycling").remove("monitorProbeAt").remove("probeAt").remove("probeLatest")
        val zone=ZoneId.systemDefault().id
        if(p.getString("planZone",zone)==zone) {
            edit.putString("servicePlanZone",zone)
            for(h in ExecutionPolicy.hours) {
                val at=p.getLong("alarm:$h",0)
                if(at>0) {
                    edit.putLong("slot:$h",at)
                    if(p.getLong("delivered:$h",0)==at) edit.putLong("accepted:$h",at)
                }
            }
        }
        for(h in ExecutionPolicy.hours) edit.remove("alarm:$h")
        edit.commit()
        Diagnostics.emit(c,"SCHEDULER_MIGRATED",reason="single_foreground_service")
    }
    fun ensurePlan(c:Context,reason:String,reset:Boolean=false) {
        val p=prefs(c); val zone=ZoneId.systemDefault().id
        val changed=reset || p.getString("servicePlanZone","")!=zone
        if(!changed && ExecutionPolicy.hours.all { p.getLong("slot:$it",0)>0 }) return
        val edit=p.edit().putString("servicePlanZone",zone)
        for(h in ExecutionPolicy.hours) {
            val old=p.getLong("slot:$h",0)
            if(changed || old<=0) {
                val at=ExecutionPolicy.next(h,ZonedDateTime.now())
                if(old>0 && old!=at) { Diagnostics.emit(c,"SCHEDULE_CANCELLED",old,reason=reason); Diagnostics.outcome(c,old,"cancelled",reason) }
                edit.putLong("slot:$h",at)
                Diagnostics.registered(c,at)
                Diagnostics.emit(c,"SCHEDULE_PLANNED",at,status="planned",reason=reason)
            }
        }
        edit.commit(); updateNext(c)
    }
    fun updateNext(c:Context) {
        val p=prefs(c)
        val next=ExecutionPolicy.hours.map { p.getLong("slot:$it",0) }.filter { it>0 }.minOrNull() ?: 0
        if(p.getLong("next",0)!=next) p.edit().putLong("next",next).commit()
    }
    fun advance(c:Context,h:Int,old:Long) {
        val next=ExecutionPolicy.next(h,Instant.ofEpochMilli(old).atZone(ZoneId.systemDefault()).plusNanos(1))
        check(prefs(c).edit().putLong("slot:$h",next).commit()) { "Slot persistence failed" }
        Diagnostics.registered(c,next); Diagnostics.emit(c,"SCHEDULE_PLANNED",next,status="planned",reason="next_local_day")
        updateNext(c)
    }
    fun enable(c:Context) { prefs(c).edit().putBoolean("enabled",true).commit(); RunService.request(c,"user_enabled") }
    fun disable(c:Context) {
        val p=prefs(c); val edit=p.edit().putBoolean("enabled",false).remove("next").remove("serviceProbeAt")
        for(h in ExecutionPolicy.hours) {
            val at=p.getLong("slot:$h",0)
            if(at>0) { Diagnostics.emit(c,"SCHEDULE_CANCELLED",at,reason="user_paused"); Diagnostics.outcome(c,at,"cancelled","user_paused") }
            edit.remove("slot:$h")
        }
        edit.commit(); RunService.pause(); Diagnostics.emit(c,"SCHEDULER_PAUSED",reason="user_requested")
    }
    fun pruneHistory(c:Context) {
        val p=prefs(c); val old=p.getString("events","").orEmpty(); val kept=LogRetention.retain(old).joinToString("\n")
        if(old!=kept) p.edit().putString("events",kept).commit()
    }
    fun checkOnOpen(c:Context) {
        Diagnostics.emit(c,"APP_OPENED")
        Diagnostics.work { migrate(c); pruneHistory(c); Diagnostics.prune(c); Diagnostics.processHistory(c) }
        if(enabled(c)) RunService.request(c,"app_opened")
    }
}

class RecoveryReceiver:BroadcastReceiver() {
    override fun onReceive(c:Context,i:Intent) {
        val reason=when(i.action) {
            Intent.ACTION_BOOT_COMPLETED -> "reboot"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "app_update"
            Intent.ACTION_TIME_CHANGED -> "clock_change"
            Intent.ACTION_TIMEZONE_CHANGED -> "timezone_change"
            else -> return
        }
        Diagnostics.emit(c,"SERVICE_RECOVERY_REQUESTED",reason=reason)
        if(Schedule.enabled(c)) RunService.request(c,reason,reason in listOf("clock_change","timezone_change"))
        else Diagnostics.work { Schedule.migrate(c) }
    }
}

/** One foreground service owns the clock, the session lease and every Termux command. */
class RunService:Service() {
    companion object {
        const val ENSURE="ai.interlink.scheduler.SERVICE_ENSURE"
        const val EXECUTE="ai.interlink.scheduler.SERVICE_EXECUTE"
        const val PROBE="ai.interlink.scheduler.SERVICE_PROBE"
        const val PAUSE="ai.interlink.scheduler.SERVICE_PAUSE"
        @Volatile private var instance:RunService?=null
        @Volatile var running=false; private set
        val wakeHeld:Boolean get()=instance?.wake?.isHeld==true
        // Internal dispatch seam for isolated emulator tests; never exported and null in normal use.
        @Volatile var dispatchOverride:((Intent)->ComponentName?)?=null
        fun request(c:Context,reason:String,reset:Boolean=false) {
            try { c.startForegroundService(Intent(c,RunService::class.java).setAction(ENSURE).putExtra("reason",reason).putExtra("reset",reset)) }
            catch(e:RuntimeException) { Schedule.prefs(c).edit().putString("serviceError","Start rejected: ${e.javaClass.simpleName}").commit(); Diagnostics.emit(c,"SERVICE_START_REJECTED",reason=e.javaClass.simpleName) }
        }
        fun manual(c:Context,mode:String) { c.startForegroundService(Intent(c,RunService::class.java).setAction(EXECUTE).putExtra("mode",mode).putExtra("scheduled",System.currentTimeMillis())) }
        fun probe(c:Context,delay:Long) {
            require(delay in 1000L..8L*3600000)
            c.startForegroundService(Intent(c,RunService::class.java).setAction(PROBE).putExtra("delay",delay))
        }
        fun pause() { instance?.let { s -> s.engine.post { s.pulse() } } }
        fun completed(id:String) { instance?.let { s -> s.engine.post { if(s.lease?.id==id) { s.lease=null; s.pulse() } } } }
    }
    private lateinit var thread:HandlerThread
    private lateinit var engine:Handler
    private val bridge=Executors.newSingleThreadExecutor()
    @Volatile private var wake:PowerManager.WakeLock?=null
    private var indefiniteWake=false
    private var lease:ExecutionLease?=null
    private var lastHeartbeat=0L
    private var lastPrune=0L
    private val serviceId=UUID.randomUUID().toString()
    private var taskRecoveryRequested=false
    @Volatile private var stopReason:String?=null
    private val pulseTask=Runnable { pulse() }
    private val clocks=object:BroadcastReceiver() {
        override fun onReceive(c:Context,i:Intent) { engine.post { if(Schedule.enabled(this@RunService)) Schedule.ensurePlan(this@RunService,i.action ?: "clock",true); pulse() } }
    }
    override fun onCreate() {
        super.onCreate()
        thread=HandlerThread("InterlinkScheduler").apply { start() }; engine=Handler(thread.looper); instance=this
        Diagnostics.emit(this,"SERVICE_CREATED",details=JSONObject().put("serviceId",serviceId).put("pidAtEvent",Process.myPid()))
    }
    override fun onBind(i:Intent?)=null
    override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int {
        val entered=SystemClock.elapsedRealtime()
        // Promote before preferences, SQLite, diagnostic sampling, receiver registration or IPC dispatch.
        try { promote("Starting foreground scheduler") }
        catch(e:RuntimeException) { Diagnostics.emit(this,"FOREGROUND_PROMOTION_FAILED",reason=e.javaClass.simpleName); stopOwned("foreground_promotion_failed"); return START_NOT_STICKY }
        running=true
        taskRecoveryRequested=false
        Diagnostics.emit(this,"FOREGROUND_PROMOTED",reason=i?.getStringExtra("reason") ?: "start_command",
            details=JSONObject().put("serviceId",serviceId).put("pidAtEvent",Process.myPid()).put("startId",startId).put("startFlags",flags)
                .put("onStartEnteredElapsed",entered).put("promotedElapsed",SystemClock.elapsedRealtime()))
        engine.post {
            try {
                Schedule.migrate(this)
                if(i?.getStringExtra("reason")=="reboot") {
                    val p=Schedule.prefs(this); val previous=p.getString("activeId",null)
                    if(previous!=null) {
                        val at=p.getLong("runTime:$previous",0)
                        Diagnostics.emit(this,"EXECUTION_FAILED",at,status="unknown",reason="reboot_during_prior_execution")
                        if(p.getString("runMode:$previous",null)=="scheduled") Diagnostics.outcome(this,at,"unknown","reboot_during_prior_execution")
                        clearActive(previous); lease=null
                    }
                }
                if(!initialized) initialize()
                if(i?.getBooleanExtra("reset",false)==true && Schedule.enabled(this)) Schedule.ensurePlan(this,i.getStringExtra("reason") ?: "clock",true)
                when(i?.action) {
                    PAUSE -> Schedule.disable(this)
                    EXECUTE -> {
                        val mode=i.getStringExtra("mode")
                        if(mode in listOf("test","claim")) begin(i.getLongExtra("scheduled",0),mode!!)
                    }
                    PROBE -> if(Schedule.enabled(this)) {
                        val delay=i.getLongExtra("delay",120000)
                        if(delay in 1000L..8L*3600000) {
                            val at=System.currentTimeMillis()+delay
                            Schedule.prefs(this).edit().putLong("serviceProbeAt",at).putString("probeStatus","Waiting for service timer at ${Schedule.time(at)}").commit()
                            Diagnostics.emit(this,"SERVICE_PROBE_PLANNED",at,"probe-$at",status="planned")
                        }
                    }
                }
                pulse()
            } catch(e:RuntimeException) { failService(e) }
        }
        return START_STICKY
    }
    private var initialized=false
    private fun initialize() {
        initialized=true
        val filter=IntentFilter().apply { addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED) }
        if(Build.VERSION.SDK_INT>=33) registerReceiver(clocks,filter,Context.RECEIVER_NOT_EXPORTED) else registerReceiver(clocks,filter)
        val p=Schedule.prefs(this)
        val id=p.getString("activeId",null)
        if(id!=null) {
            val at=p.getLong("runTime:$id",0); val mode=p.getString("runMode:$id","scheduled") ?: "scheduled"
            val previousBoot=p.getInt("activeBoot",-1)
            val boot=android.provider.Settings.Global.getInt(contentResolver,android.provider.Settings.Global.BOOT_COUNT,-1)
            if(previousBoot>=0 && boot>=0 && previousBoot!=boot) {
                Diagnostics.emit(this,"EXECUTION_FAILED",at,status="unknown",reason="boot_identity_changed")
                if(mode=="scheduled") Diagnostics.outcome(this,at,"unknown","boot_identity_changed")
                clearActive(id)
            } else {
                lease=ExecutionLease(id,at,mode,p.getLong("activeUntil",at+if(mode=="scheduled")480000 else 120000),p.getLong("activeElapsedUntil",0))
                Diagnostics.emit(this,"SESSION_RECOVERED_UNKNOWN",at,reason="waiting_for_prior_callback_no_resubmission")
            }
        }
        if(Schedule.enabled(this)) Schedule.ensurePlan(this,"service_start")
        Diagnostics.emit(this,"SCHEDULER_SERVICE_STARTED",reason="single_owner")
    }
    private fun pulse() {
        engine.removeCallbacks(pulseTask)
        try {
            val now=System.currentTimeMillis(); val elapsed=SystemClock.elapsedRealtime(); val p=Schedule.prefs(this)
            lease?.let { current ->
                if(p.getString("activeId",null)!=current.id) lease=null
                else if(current.expired(now,elapsed)) {
                    Diagnostics.emit(this,"EXECUTION_FAILED",current.scheduled,reason="deadline_without_callback",status="unknown",details=JSONObject().put("attemptId",current.id))
                    if(current.mode=="scheduled") Diagnostics.outcome(this,current.scheduled,"unknown","deadline_without_callback; submitted outcome unknown")
                    else p.edit().putString("manualResult","Deadline reached without a completion report; outcome unknown.").commit()
                    clearActive(current.id); lease=null
                }
            }
            if(Schedule.enabled(this)) {
                Schedule.ensurePlan(this,"service_clock")
                for(h in ExecutionPolicy.hours.sortedBy { p.getLong("slot:$it",Long.MAX_VALUE) }) {
                    var at=p.getLong("slot:$h",0); var count=0
                    while(at>0 && at<=now && count++<32) {
                        if(p.getLong("accepted:$h",0)==at) { Schedule.advance(this,h,at) }
                        else if(!ExecutionPolicy.inWindow(at,now)) {
                            Diagnostics.emit(this,"SCHEDULE_MISSED",at,status="missed",reason="original_deadline_passed")
                            Diagnostics.outcome(this,at,"missed","original_deadline_passed")
                            Schedule.advance(this,h,at)
                        } else {
                            if(lease!=null) break // defer only within the same original window
                            Diagnostics.emit(this,"SERVICE_TIMER_FIRED",at,status="triggered",details=JSONObject().put("delayMs",now-at))
                            Diagnostics.outcome(this,at,"triggered","service_timer",delivered=now)
                            begin(at,"scheduled",h)
                            Schedule.advance(this,h,at)
                        }
                        at=p.getLong("slot:$h",0)
                    }
                }
            }
            val probe=p.getLong("serviceProbeAt",0)
            if(probe>0 && now>=probe) {
                p.edit().remove("serviceProbeAt").putString("probeStatus",if(now-probe<15000) "Service timer fired; delay ${now-probe}ms. No claim submitted by probe." else "Service timer late by ${now-probe}ms; no claim submitted by probe.").commit()
                Diagnostics.emit(this,"SERVICE_PROBE_FIRED",probe,"probe-$probe",status=if(now-probe<15000) "delivered" else "late",details=JSONObject().put("delayMs",now-probe))
                getSystemService(NotificationManager::class.java).notify(905,notification("Service timer test fired; delay ${now-probe}ms",false))
            }
            if(!Schedule.enabled(this) && lease==null) { stopOwned("schedule_paused_no_active_execution"); return }
            ensureWake(now,elapsed)
            if(lastHeartbeat==0L || elapsed-lastHeartbeat>=60000) {
                lastHeartbeat=elapsed
                p.edit().putLong("serviceHeartbeat",now).putString("serviceError","").apply()
                Diagnostics.emit(this,"SERVICE_HEARTBEAT",details=JSONObject().put("wakeHeld",wakeHeld).put("sessionActive",lease!=null))
                updateNotification()
            }
            if(elapsed-lastPrune>=3600000) { lastPrune=elapsed; Diagnostics.work { Schedule.pruneHistory(this); Diagnostics.prune(this) } }
            val next=listOfNotNull(if(Schedule.enabled(this)) p.getLong("next",0).takeIf { it>now } else null,
                p.getLong("serviceProbeAt",0).takeIf { it>now },lease?.wallDeadline).minOrNull()
            val delay=if(lease!=null) 1000L else minOf(30000L,next?.minus(now)?.coerceAtLeast(1) ?: 30000L)
            engine.postDelayed(pulseTask,delay)
        } catch(e:RuntimeException) { failService(e) }
    }
    private fun begin(at:Long,mode:String,hour:Int?=null) {
        val p=Schedule.prefs(this); val now=System.currentTimeMillis()
        if(lease!=null) { if(mode!="scheduled") p.edit().putString("manualResult","Another execution is active. Wait for completion.").commit(); return }
        if(mode=="scheduled" && !Schedule.enabled(this)) return
        val duration=if(mode=="scheduled")480000L else 120000L
        if(at<=0 || now<at || now>=at+duration) { Diagnostics.emit(this,"EXECUTION_REJECTED",at,reason="outside_original_deadline"); return }
        val id=UUID.randomUUID().toString(); val execution=if(mode=="scheduled") Diagnostics.id(at) else "$mode-$at"
        val session=ExecutionLease(id,at,mode,at+duration,SystemClock.elapsedRealtime()+(at+duration-now)); lease=session
        val old=p.getString("runIds","").orEmpty().split(',').filter { it.isNotEmpty() }
        val edit=p.edit()
        for(key in old.dropLast(39)) for(prefix in listOf("runMode:","runTime:","runExecution:")) edit.remove(prefix+key)
        edit.putString("runIds",(old.takeLast(39)+id).joinToString(",")).putString("runMode:$id",mode).putLong("runTime:$id",at).putString("runExecution:$id",execution)
            .putInt("activeBoot",android.provider.Settings.Global.getInt(contentResolver,android.provider.Settings.Global.BOOT_COUNT,-1))
            .putString("activeId",id).putLong("activeUntil",session.wallDeadline).putLong("activeElapsedUntil",session.elapsedDeadline).putLong("active",now).putString("activeMode",mode)
        if(mode=="scheduled") { require(hour in ExecutionPolicy.hours); edit.putLong("accepted:$hour",at) }
        if(mode!="scheduled") edit.putString("lastManualId",id).putString("manualResult","Starting ${ExecutionPolicy.label(mode)}…")
        check(edit.commit()) { "Execution lease persistence failed" }; ensureWake(now,SystemClock.elapsedRealtime()); updateNotification()
        Diagnostics.emit(this,"EXECUTION_DISPATCH_REQUESTED",at,execution,details=JSONObject().put("attemptId",id))
        val base="/data/data/com.termux/files"; val work="$base/home/Helping_hands/Interlink-Auto-Bot"
        val callback=PendingIntent.getBroadcast(this,2,Intent(this,ResultReceiver::class.java).setData(Uri.parse("interlink-result://run/$id")),PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        bridge.execute {
            try {
                if(session.expired(System.currentTimeMillis(),SystemClock.elapsedRealtime())) throw IllegalStateException("Dispatch deadline expired")
                val args=if(mode=="scheduled") arrayOf("-c",assets.open("termux-bootstrap.sh").bufferedReader().use { it.readText() },"interlink-bootstrap",work,at.toString(),execution,id) else arrayOf("$work/run-on-demand.sh",mode)
                val command=Intent("com.termux.RUN_COMMAND").setClassName("com.termux","com.termux.app.RunCommandService")
                    .putExtra("com.termux.RUN_COMMAND_PATH","$base/usr/bin/bash").putExtra("com.termux.RUN_COMMAND_ARGUMENTS",args)
                    .putExtra("com.termux.RUN_COMMAND_WORKDIR",work).putExtra("com.termux.RUN_COMMAND_BACKGROUND",true).putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback)
                val result=dispatchOverride?.invoke(command) ?: if(dispatchOverride==null) startService(command) else null
                if(result==null) throw IllegalStateException("Termux did not accept dispatch")
                Diagnostics.emit(this,"TERMUX_DISPATCHED",at,execution,status="requested_not_node_confirmed",details=JSONObject().put("attemptId",id))
            } catch(e:Exception) {
                callback.cancel()
                Diagnostics.emit(this,"EXECUTION_FAILED",at,execution,status="dispatch_failed",reason="termux_${e.javaClass.simpleName}")
                if(mode=="scheduled") Diagnostics.outcome(this,at,"failed","termux_dispatch_failed")
                else p.edit().putString("manualResult","Could not dispatch Termux: ${e.javaClass.simpleName}").commit()
                clearActive(id)
                engine.post { if(lease?.id==id) lease=null; pulse() }
            }
        }
    }
    private fun clearActive(id:String) {
        val p=Schedule.prefs(this)
        if(p.getString("activeId",null)==id) p.edit().remove("activeId").remove("activeUntil").remove("activeElapsedUntil").remove("activeBoot").remove("active").remove("activeMode").commit()
    }
    private fun ensureWake(now:Long,elapsed:Long) {
        val indefinite=Schedule.enabled(this)
        if(wake?.isHeld==true && indefiniteWake==indefinite) return
        if(wake?.isHeld==true) wake?.release()
        if(!indefinite && lease==null) { return }
        val lock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Interlink:foreground-scheduler")
        if(indefinite) lock.acquire() else {
            val remaining=lease!!.remaining(now,elapsed)
            if(remaining<=0) { return }
            lock.acquire(remaining)
        }
        wake=lock; indefiniteWake=indefinite
        Diagnostics.emit(this,"SERVICE_WAKE_LOCK_ACQUIRED",reason=if(indefinite) "continuous_schedule" else "bounded_manual_session")
    }
    private fun notification(text:String,ongoing:Boolean=true):Notification {
        val open=PendingIntent.getActivity(this,903,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val pause=PendingIntent.getService(this,904,Intent(this,RunService::class.java).setAction(PAUSE),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this,"scheduler_service").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Interlink Scheduler service").setContentText(text).setContentIntent(open).setOngoing(ongoing)
            .apply { if(ongoing) addAction(android.R.drawable.ic_delete,"Pause schedule",pause) else setAutoCancel(true) }.build()
    }
    private fun promote(text:String) {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("scheduler_service","Foreground scheduling",NotificationManager.IMPORTANCE_LOW))
        startForeground(3,notification(text))
    }
    private fun updateNotification() {
        val next=Schedule.prefs(this).getLong("next",0)
        val text=lease?.let { "${ExecutionPolicy.label(it.mode)} until ${Schedule.time(it.wallDeadline)}" }
            ?: if(Schedule.enabled(this) && next>0) "Running continuously • next ${Schedule.time(next)}" else "Schedule paused"
        getSystemService(NotificationManager::class.java).notify(3,notification(text))
    }
    private fun failService(e:RuntimeException) {
        Schedule.prefs(this).edit().putString("serviceError","Service stopped: ${e.javaClass.simpleName}").commit()
        Diagnostics.emit(this,"SCHEDULER_SERVICE_FAILED",reason=e.javaClass.simpleName)
        stopOwned("service_runtime_failure")
    }
    private fun stopOwned(reason:String) {
        stopReason=reason
        Diagnostics.emit(this,"SERVICE_STOP_REQUESTED",reason=reason,details=JSONObject().put("serviceId",serviceId).put("pidAtEvent",Process.myPid()))
        stopSelf()
    }
    override fun onTaskRemoved(rootIntent:Intent?) {
        // Some devices recreate a Service solely to deliver task removal, without onStartCommand.
        // A healthy started service needs no restart; an enabled cold instance needs a real start.
        val desired=Schedule.enabled(this)
        val started=running && instance===this
        val decision=when {
            !desired -> "keep_paused"
            started -> "keep_running"
            taskRecoveryRequested -> "start_already_requested"
            else -> "request_foreground_start"
        }
        Diagnostics.emit(this,"SERVICE_TASK_REMOVED",reason=decision,details=JSONObject().put("serviceId",serviceId)
            .put("pidAtEvent",Process.myPid()).put("scheduleEnabledAtCallback",desired)
            .put("foregroundStartedAtCallback",started).put("wakeHeldAtCallback",wake?.isHeld==true))
        if(desired && started) engine.post { pulse() }
        else if(desired && !taskRecoveryRequested) {
            taskRecoveryRequested=true
            Diagnostics.emit(this,"RECOVERY_ATTEMPTED",reason="task_removed_without_start",details=JSONObject().put("serviceId",serviceId))
            request(applicationContext,"task_removed_without_start")
        }
        super.onTaskRemoved(rootIntent)
    }
    override fun onDestroy() {
        val wasRunning=running && instance===this
        val held=wake?.isHeld==true
        running=false; instance=null
        engine.removeCallbacksAndMessages(null)
        engine.post {
            try { unregisterReceiver(clocks) } catch(_:RuntimeException) {}
            if(wake?.isHeld==true) wake?.release()
            wake=null; bridge.shutdownNow(); thread.quitSafely()
        }
        Diagnostics.emit(this,"SCHEDULER_SERVICE_STOPPED",reason=stopReason ?: "framework_destroy_no_app_stop_request",
            details=JSONObject().put("serviceId",serviceId).put("pidAtEvent",Process.myPid())
                .put("foregroundStartedBeforeDestroy",wasRunning).put("wakeHeldBeforeDestroy",held))
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
}

class ResultReceiver:BroadcastReceiver() {
    override fun onReceive(c:Context,i:Intent) {
        val id=i.data?.lastPathSegment ?: return; val p=Schedule.prefs(c)
        val mode=p.getString("runMode:$id",null) ?: return
        val at=p.getLong("runTime:$id",0); val execution=p.getString("runExecution:$id",null) ?: if(mode=="scheduled")Diagnostics.id(at) else "$mode-$at"
        val result=i.getBundleExtra("result"); val exit=if(result?.containsKey("exitCode")==true) result.getInt("exitCode") else -1
        val err=if(result?.containsKey("err")==true) result.getInt("err") else -1
        val parsed=if(mode=="scheduled") ExecutionReport.parse(result?.getString("stdout"),execution,id,at) else null
        val message=if(mode=="scheduled") parsed?.summary ?: "Command exit=$exit; no valid Node report. Window results UNKNOWN." else if(err!=-1) "Termux reported internal error $err; completion is uncertain. Inspect diagnostics and the private bot log." else ExecutionPolicy.outcome(mode,exit)
        if(err!=-1) Diagnostics.emit(c,"TERMUX_COMMAND_FAILED",at,execution,reason="internal_error",details=JSONObject().put("errorCode",err))
        if(mode=="scheduled") {
            parsed?.events?.forEach { e -> Diagnostics.emit(c,e.getString("event"),at,execution,status=e.optString("result","observed"),reason=e.optString("reason"),details=e,actual=e.getLong("actualAt"),source="node") }
            if(parsed?.startupReason!=null) Diagnostics.emit(c,"TERMUX_STARTUP_FAILED",at,execution,reason=parsed.startupReason,details=JSONObject().put("exitCode",exit))
            Diagnostics.outcome(c,at,if(parsed?.successful==2) "success" else if(parsed?.complete==true) "incomplete" else "unknown",parsed?.startupReason ?: if(parsed==null) "node_report_missing_or_invalid" else "window_results",message)
        }
        Diagnostics.emit(c,if(err==-1 && exit==0 && (mode!="scheduled" || parsed?.successful==2)) "EXECUTION_COMPLETED" else "EXECUTION_FAILED",at,execution,reason=if(parsed?.startupReason!=null)parsed.startupReason else "see_result",details=JSONObject().put("attemptId",id).put("exitCode",exit))
        Schedule.record(c,"$message Intended start ${Schedule.time(at)}.")
        val edit=p.edit().remove("runMode:$id").remove("runTime:$id").remove("runExecution:$id")
        if(mode!="scheduled" && p.getString("lastManualId",null)==id) edit.putString("manualResult",message)
        if(p.getString("activeId",null)==id) edit.remove("activeId").remove("activeUntil").remove("activeElapsedUntil").remove("activeBoot").remove("active").remove("activeMode")
        edit.commit(); RunService.completed(id)
    }
}
