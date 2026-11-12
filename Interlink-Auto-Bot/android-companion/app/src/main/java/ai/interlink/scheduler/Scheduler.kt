package ai.interlink.scheduler

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

/** Durable reservation before dispatch. Never resubmit an accepted session after an ambiguous exit. */
object SessionStore {
    fun execution(mode:String,at:Long)=if(mode=="scheduled")Diagnostics.id(at) else "$mode-$at"
    fun duration(mode:String)=if(mode=="scheduled")480000L else 120000L
    @Synchronized fun reconcile(c:Context) {
        val p=Schedule.prefs(c); val id=p.getString("activeId",null) ?: return
        val boot=android.provider.Settings.Global.getInt(c.contentResolver,android.provider.Settings.Global.BOOT_COUNT,-1)
        val changed=p.getInt("activeBoot",boot)!=boot
        val wall=p.getLong("activeUntil",0); val elapsed=p.getLong("activeElapsedUntil",0)
        if(!changed && System.currentTimeMillis()<wall && (elapsed<=0 || SystemClock.elapsedRealtime()<elapsed)) return
        val at=p.getLong("runTime:$id",0); val mode=p.getString("runMode:$id","scheduled")!!
        val reason=if(changed) "boot_identity_changed" else "deadline_without_callback"
        Diagnostics.emit(c,"EXECUTION_FAILED",at,execution(mode,at),status="unknown",reason=reason,details=JSONObject().put("attemptId",id))
        if(mode=="scheduled") Diagnostics.outcome(c,at,"unknown",reason)
        else if(mode=="probe") p.edit().putString("probeStatus","Probe execution outcome unknown: $reason").commit()
        finish(c,id)
    }
    @Synchronized fun reserve(c:Context,at:Long,mode:String,id:String,hour:Int?=null,receiverAt:Long=0,receiverElapsed:Long=0):Boolean {
        reconcile(c)
        val p=Schedule.prefs(c); val now=System.currentTimeMillis(); val duration=duration(mode)
        if(at<=0 || now<at || now>=at+duration || p.getString("activeId",null)!=null) return false
        if(mode=="scheduled" && (hour !in ExecutionPolicy.hours || !Schedule.enabled(c) || p.getLong("accepted:$hour",0)==at)) return false
        val old=p.getString("runIds","").orEmpty().split(',').filter { it.isNotEmpty() }
        val edit=p.edit()
        for(key in old.dropLast(39)) for(prefix in listOf("runMode:","runTime:","runExecution:","runService:","runReceiver:","runReceiverElapsed:","runServiceAt:")) edit.remove(prefix+key)
        edit.putString("runIds",(old.takeLast(39)+id).joinToString(",")).putString("runMode:$id",mode).putLong("runTime:$id",at)
            .putString("runExecution:$id",execution(mode,at)).putLong("runReceiver:$id",receiverAt).putLong("runReceiverElapsed:$id",receiverElapsed)
            .putLong("activeUntil",at+duration).putLong("activeElapsedUntil",SystemClock.elapsedRealtime()+at+duration-now)
            .putInt("activeBoot",android.provider.Settings.Global.getInt(c.contentResolver,android.provider.Settings.Global.BOOT_COUNT,-1))
            .putString("activeId",id).putString("activeMode",mode)
        if(mode=="scheduled") edit.putLong("accepted:$hour",at)
        if(mode in listOf("test","claim")) edit.putString("lastManualId",id).putString("manualResult","Starting ${ExecutionPolicy.label(mode)}…")
        check(edit.commit()) { "Execution reservation persistence failed" }
        return true
    }
    @Synchronized fun finish(c:Context,id:String) {
        val p=Schedule.prefs(c)
        if(p.getString("activeId",null)==id) check(p.edit().remove("activeId").remove("activeUntil").remove("activeElapsedUntil").remove("activeBoot").remove("activeMode").remove("active").commit()) { "Completion persistence failed" }
    }
}

/** Exists only during one original execution deadline; no periodic scheduling or task-removal restart. */
class RunService:Service() {
    companion object {
        const val EXECUTE="ai.interlink.scheduler.BOUNDED_EXECUTE"
        @Volatile private var instance:RunService?=null
        @Volatile var running=false; private set
        val wakeHeld:Boolean get()=instance?.wake?.isHeld==true
        @Volatile var dispatchOverride:((Intent)->ComponentName?)?=null
        fun launch(c:Context,at:Long,mode:String,id:String,receiverAt:Long=0,receiverElapsed:Long=0) {
            c.startForegroundService(Intent(c,RunService::class.java).setAction(EXECUTE).putExtra("scheduled",at).putExtra("mode",mode)
                .putExtra("elapsedDeadline",if(receiverElapsed>0) receiverElapsed+(at+SessionStore.duration(mode)-receiverAt) else SystemClock.elapsedRealtime()+(at+SessionStore.duration(mode)-System.currentTimeMillis()))
                .putExtra("attemptId",id).putExtra("receiverAt",receiverAt).putExtra("receiverElapsedMs",receiverElapsed))
        }
        fun manual(c:Context,mode:String) { require(mode in listOf("test","claim")); launch(c,System.currentTimeMillis(),mode,UUID.randomUUID().toString()) }
        fun completed(id:String) { instance?.let { s -> s.engine.post { if(s.lease?.id==id) s.stopOwned("matching_execution_completed") } } }
    }
    private lateinit var thread:HandlerThread
    private lateinit var engine:Handler
    private val bridge=Executors.newSingleThreadExecutor()
    @Volatile private var wake:PowerManager.WakeLock?=null
    private var lease:ExecutionLease?=null
    private val serviceId=UUID.randomUUID().toString()
    private var stopReason="framework_destroy_no_app_stop_request"
    private var heartbeat=0L
    private val tick=object:Runnable {
        override fun run() {
            val session=lease ?: return
            val now=System.currentTimeMillis(); val elapsed=SystemClock.elapsedRealtime()
            if(session.expired(now,elapsed)) {
                event("EXECUTION_FAILED",session,status="unknown",reason="original_deadline_without_callback")
                if(session.mode=="scheduled") Diagnostics.outcome(this@RunService,session.scheduled,"unknown","deadline_without_callback; claim outcome unknown")
                else if(session.mode=="probe") Schedule.prefs(this@RunService).edit().putString("probeStatus","Probe service expired without completion.").commit()
                else Schedule.prefs(this@RunService).edit().putString("manualResult","Deadline reached without callback; outcome unknown.").commit()
                SessionStore.finish(this@RunService,session.id); stopOwned("original_deadline_reached"); return
            }
            if(elapsed-heartbeat>=60000) { heartbeat=elapsed; event("EXECUTION_HEARTBEAT",session) }
            engine.postDelayed(this,minOf(1000L,session.remaining(now,elapsed)))
        }
    }
    override fun onCreate() {
        super.onCreate(); thread=HandlerThread("InterlinkBoundedExecution").apply { start() }; engine=Handler(thread.looper); instance=this
    }
    override fun onBind(i:Intent?)=null
    override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int {
        val entered=System.currentTimeMillis(); val elapsed=SystemClock.elapsedRealtime()
        val at=i?.getLongExtra("scheduled",0) ?: 0; val id=i?.getStringExtra("attemptId").orEmpty(); val mode=i?.getStringExtra("mode").orEmpty()
        try {
            // No preferences/SQLite/device sampling before promotion.
            promote()
            running=true
            val remaining=minOf(at+SessionStore.duration(mode)-entered,(i?.getLongExtra("elapsedDeadline",elapsed+SessionStore.duration(mode)) ?: elapsed)-elapsed).coerceIn(1,480000)
            if(wake?.isHeld!=true) wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Interlink:bounded-execution").apply { acquire(remaining) }
            Handoff.release(id)
        } catch(e:RuntimeException) {
            Handoff.release(id)
            Diagnostics.emit(this,"EXECUTION_FAILED",at,SessionStore.execution(mode,at),reason="foreground_${e.javaClass.simpleName}",details=JSONObject().put("attemptId",id).put("serviceId",serviceId))
            if(id.isNotEmpty()) SessionStore.finish(this,id)
            stopOwned("foreground_promotion_failed"); return START_NOT_STICKY
        }
        Diagnostics.emit(this,"FOREGROUND_PROMOTED",at,SessionStore.execution(mode,at),details=JSONObject().put("attemptId",id).put("serviceId",serviceId)
            .put("pidAtEvent",Process.myPid()).put("receiverAt",i?.getLongExtra("receiverAt",0)).put("serviceAt",entered)
            .put("serviceElapsedMs",elapsed).put("promotedAt",System.currentTimeMillis()).put("promotedElapsedMs",SystemClock.elapsedRealtime()).put("mode",mode))
        engine.post {
            try {
                if(i?.action!=EXECUTE || mode !in listOf("scheduled","test","claim","probe") || id.isEmpty()) { if(lease==null) stopOwned("invalid_start"); return@post }
                if(lease!=null) {
                    if(mode in listOf("test","claim")) Schedule.prefs(this).edit().putString("manualResult","Another execution is active. Wait for completion.").commit()
                    Diagnostics.emit(this,"EXECUTION_REJECTED",at,SessionStore.execution(mode,at),reason="service_already_active",details=JSONObject().put("attemptId",id).put("serviceId",serviceId))
                    return@post
                }
                val p=Schedule.prefs(this)
                if(mode in listOf("test","claim") && !SessionStore.reserve(this,at,mode,id)) {
                    p.edit().putString("manualResult","Another execution is active or the original deadline expired.").commit()
                    stopOwned("manual_reservation_rejected"); return@post
                }
                if(p.getString("activeId",null)!=id || p.getString("runMode:$id",null)!=mode || p.getLong("runTime:$id",0)!=at) { stopOwned("reservation_missing_or_stale"); return@post }
                val session=ExecutionLease(id,at,mode,p.getLong("activeUntil",0),p.getLong("activeElapsedUntil",0))
                lease=session
                p.edit().putString("runService:$id",serviceId).putLong("runServiceAt:$id",entered).commit()
                if(session.expired(System.currentTimeMillis(),SystemClock.elapsedRealtime())) { tick.run(); return@post }
                getSystemService(NotificationManager::class.java).notify(3,notification("${if(mode=="probe") "Alarm probe" else ExecutionPolicy.label(mode)} • ends ${Schedule.time(session.wallDeadline)}"))
                if(mode=="probe") {
                    val delay=entered-at
                    p.edit().putString("probeStatus","Alarm and foreground service delivered; receiver delay ${p.getLong("runReceiver:$id",entered)-at}ms; service delay ${delay}ms. No Node or claim.").commit()
                    event("EXECUTION_COMPLETED",session,reason="alarm_probe_no_node_no_claim")
                    getSystemService(NotificationManager::class.java).notify(905,Notification.Builder(this,"bounded_execution").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle("Interlink alarm test completed").setContentText("Service delay ${delay}ms; no claim.").setContentIntent(open()).setAutoCancel(true).build())
                    SessionStore.finish(this,id); stopOwned("alarm_probe_completed"); return@post
                }
                event("EXECUTION_DISPATCH_REQUESTED",session,reason="termux_command")
                dispatch(session)
                tick.run()
            } catch(e:RuntimeException) {
                Diagnostics.emit(this,"EXECUTION_FAILED",at,SessionStore.execution(mode,at),reason="service_${e.javaClass.simpleName}",details=JSONObject().put("attemptId",id).put("serviceId",serviceId))
                SessionStore.finish(this,id); stopOwned("execution_initialization_failed")
            }
        }
        // A killed session remains unknown in durable state. Never re-dispatch it on a sticky restart.
        return START_NOT_STICKY
    }
    private fun event(name:String,s:ExecutionLease,status:String="observed",reason:String="") {
        val p=Schedule.prefs(this)
        Diagnostics.emit(this,name,s.scheduled,SessionStore.execution(s.mode,s.scheduled),status=status,reason=reason,details=JSONObject()
            .put("attemptId",s.id).put("serviceId",serviceId).put("pidAtEvent",Process.myPid()).put("mode",s.mode)
            .put("receiverAt",p.getLong("runReceiver:${s.id}",0)).put("serviceAt",p.getLong("runServiceAt:${s.id}",0)))
    }
    private fun dispatch(session:ExecutionLease) {
        val at=session.scheduled; val id=session.id; val mode=session.mode; val execution=SessionStore.execution(mode,at)
        val callback=PendingIntent.getBroadcast(this,2,Intent(this,ResultReceiver::class.java).setData(Uri.parse("interlink-result://run/$id")),PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        bridge.execute {
            try {
                check(!session.expired(System.currentTimeMillis(),SystemClock.elapsedRealtime())) { "Dispatch deadline expired" }
                val base="/data/data/com.termux/files"; val work="$base/home/Helping_hands/Interlink-Auto-Bot"
                val args=if(mode=="scheduled") arrayOf("-c",assets.open("termux-bootstrap.sh").bufferedReader().use { it.readText() },"interlink-bootstrap",work,at.toString(),execution,id) else arrayOf("$work/run-on-demand.sh",mode)
                val command=Intent("com.termux.RUN_COMMAND").setClassName("com.termux","com.termux.app.RunCommandService")
                    .putExtra("com.termux.RUN_COMMAND_PATH","$base/usr/bin/bash").putExtra("com.termux.RUN_COMMAND_ARGUMENTS",args)
                    .putExtra("com.termux.RUN_COMMAND_WORKDIR",work).putExtra("com.termux.RUN_COMMAND_BACKGROUND",true).putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",callback)
                val override=dispatchOverride
                check((if(override!=null) override(command) else startService(command))!=null) { "Termux did not accept command" }
                event("TERMUX_DISPATCHED",session,status="requested_not_node_confirmed")
            } catch(e:Exception) {
                callback.cancel(); event("EXECUTION_FAILED",session,status="dispatch_failed",reason="termux_${e.javaClass.simpleName}")
                if(mode=="scheduled") Diagnostics.outcome(this,at,"failed","termux_dispatch_failed")
                else Schedule.prefs(this).edit().putString("manualResult","Termux dispatch failed: ${e.javaClass.simpleName}").commit()
                SessionStore.finish(this,id); engine.post { if(lease?.id==id) stopOwned("termux_dispatch_failed") }
            }
        }
    }
    private fun open()=PendingIntent.getActivity(this,903,Intent(this,MainActivity::class.java).setAction(Intent.ACTION_MAIN),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun notification(text:String)=Notification.Builder(this,"bounded_execution").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("Interlink execution in progress").setContentText(text).setContentIntent(open()).setOngoing(true).build()
    private fun promote() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("bounded_execution","Bounded Interlink executions",NotificationManager.IMPORTANCE_LOW))
        startForeground(3,notification("Starting bounded execution"))
    }
    private fun stopOwned(reason:String) { stopReason=reason; engine.removeCallbacks(tick); stopSelf() }
    override fun onTaskRemoved(rootIntent:Intent?) {
        Diagnostics.emit(this,"SERVICE_TASK_REMOVED",lease?.scheduled ?: 0,reason="bounded_execution_no_restart",details=JSONObject().put("serviceId",serviceId).put("attemptId",lease?.id).put("wakeHeldAtCallback",wake?.isHeld==true))
        if(lease==null && !running) stopOwned("task_removal_without_active_execution")
        super.onTaskRemoved(rootIntent)
    }
    override fun onDestroy() {
        val session=lease
        engine.removeCallbacksAndMessages(null)
        if(wake?.isHeld==true) wake?.release()
        wake=null; running=false; if(instance===this) instance=null
        bridge.shutdownNow(); thread.quitSafely()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Diagnostics.emit(this,"EXECUTION_RESOURCES_RELEASED",session?.scheduled ?: 0,session?.let { SessionStore.execution(it.mode,it.scheduled) } ?: "system",reason=stopReason,
            details=JSONObject().put("attemptId",session?.id).put("serviceId",serviceId).put("pidAtEvent",Process.myPid()).put("wakeHeldAfterRelease",false))
        super.onDestroy()
    }
}

class ResultReceiver:BroadcastReceiver() {
    override fun onReceive(c:Context,i:Intent) {
        val id=i.data?.lastPathSegment ?: return; val p=Schedule.prefs(c)
        val mode=p.getString("runMode:$id",null) ?: return
        val at=p.getLong("runTime:$id",0); val execution=p.getString("runExecution:$id",null) ?: if(mode=="scheduled")Diagnostics.id(at) else "$mode-$at"
        val service=p.getString("runService:$id",null)
        val correlation=JSONObject().put("attemptId",id).put("serviceId",service).put("receiverAt",p.getLong("runReceiver:$id",0)).put("serviceAt",p.getLong("runServiceAt:$id",0))
        val result=i.getBundleExtra("result"); val exit=if(result?.containsKey("exitCode")==true) result.getInt("exitCode") else -1
        val err=if(result?.containsKey("err")==true) result.getInt("err") else -1
        val parsed=if(mode=="scheduled") ExecutionReport.parse(result?.getString("stdout"),execution,id,at) else null
        val message=if(mode=="scheduled") parsed?.summary ?: "Command exit=$exit; no valid Node report. Window results UNKNOWN." else if(err!=-1) "Termux reported internal error $err; completion is uncertain. Inspect diagnostics and the private bot log." else ExecutionPolicy.outcome(mode,exit)
        if(err!=-1) Diagnostics.emit(c,"TERMUX_COMMAND_FAILED",at,execution,reason="internal_error",details=JSONObject().put("errorCode",err))
        if(mode=="scheduled") {
            parsed?.events?.forEach { e -> Diagnostics.emit(c,e.getString("event"),at,execution,status=e.optString("result","observed"),reason=e.optString("reason"),details=JSONObject(e.toString()).put("attemptId",id).put("serviceId",service).put("receiverAt",p.getLong("runReceiver:$id",0)).put("serviceAt",p.getLong("runServiceAt:$id",0)),actual=e.getLong("actualAt"),source="node") }
            if(parsed?.startupReason!=null) Diagnostics.emit(c,"TERMUX_STARTUP_FAILED",at,execution,reason=parsed.startupReason,details=JSONObject().put("exitCode",exit))
            Diagnostics.outcome(c,at,if(parsed?.successful==2) "success" else if(parsed?.complete==true) "incomplete" else "unknown",parsed?.startupReason ?: if(parsed==null) "node_report_missing_or_invalid" else "window_results",message)
        }
        Diagnostics.emit(c,if(err==-1 && exit==0 && (mode!="scheduled" || parsed?.successful==2)) "EXECUTION_COMPLETED" else "EXECUTION_FAILED",at,execution,reason=if(parsed?.startupReason!=null)parsed.startupReason else "see_result",details=correlation.put("exitCode",exit))
        Schedule.record(c,"$message Intended start ${Schedule.time(at)}.")
        val edit=p.edit().remove("runMode:$id").remove("runTime:$id").remove("runExecution:$id").remove("runService:$id").remove("runReceiver:$id").remove("runReceiverElapsed:$id").remove("runServiceAt:$id")
        if(mode!="scheduled" && p.getString("lastManualId",null)==id) edit.putString("manualResult",message)
        if(p.getString("activeId",null)==id) edit.remove("activeId").remove("activeUntil").remove("activeElapsedUntil").remove("activeBoot").remove("active").remove("activeMode")
        edit.commit(); RunService.completed(id)
    }
}
