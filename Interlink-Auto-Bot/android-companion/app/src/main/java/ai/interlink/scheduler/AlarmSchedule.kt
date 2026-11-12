package ai.interlink.scheduler

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

/** OS-owned, user-visible alarms. Preferences describe accepted registrations, not delivery proof. */
object Schedule {
    const val ACTION="ai.interlink.scheduler.ALARM_SLOT"
    const val PROBE="ai.interlink.scheduler.ALARM_PROBE"
    fun prefs(c:Context)=c.getSharedPreferences("schedule",Context.MODE_PRIVATE)
    fun time(at:Long)=Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM h:mm:ss a z"))
    fun enabled(c:Context)=prefs(c).getBoolean("enabled",false)
    fun allowed(c:Context)=Build.VERSION.SDK_INT<31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun record(c:Context,message:String)=Diagnostics.work {
        val p=prefs(c)
        p.edit().putString("events",LogRetention.retain(p.getString("events","")+"\n"+Instant.now()+" "+message.replace('\n',' ')).joinToString("\n")).commit()
    }
    fun operation(c:Context,hour:Int,at:Long=0,generation:String="",flags:Int=PendingIntent.FLAG_UPDATE_CURRENT):PendingIntent? =
        PendingIntent.getBroadcast(c,2000+hour,Intent(c,SlotAlarmReceiver::class.java)
            .setAction(if(hour==0) PROBE else ACTION).setData(Uri.parse("interlink-alarm://slot/$hour"))
            .putExtra("hour",hour).putExtra("scheduled",at).putExtra("generation",generation),flags or PendingIntent.FLAG_IMMUTABLE)
    private fun cancel(c:Context,hour:Int) {
        operation(c,hour,flags=PendingIntent.FLAG_NO_CREATE)?.let { c.getSystemService(AlarmManager::class.java).cancel(it); it.cancel() }
    }
    @Synchronized fun migrate(c:Context) {
        val p=prefs(c)
        if(p.getInt("alarmPlanVersion",0)==3) return
        val am=c.getSystemService(AlarmManager::class.java)
        for((code,receiver,action) in listOf(Triple(1,"AlarmReceiver","ai.interlink.scheduler.RUN"),Triple(900,"BackgroundTestReceiver","ai.interlink.scheduler.TEST_ALARM"))+
            ExecutionPolicy.hours.map { Triple(100+it,"AlarmReceiver","ai.interlink.scheduler.RUN") }) {
            PendingIntent.getBroadcast(c,code,Intent().setClassName(c.packageName,"${c.packageName}.$receiver").setAction(action),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { am.cancel(it); it.cancel() }
        }
        val edit=p.edit().putString("alarmZone",p.getString("servicePlanZone",p.getString("planZone",null)) ?: ZoneId.systemDefault().id).putInt("alarmPlanVersion",3).remove("serviceProbeAt").remove("serviceHeartbeat").remove("continuousMonitor").remove("wakeLockCycling")
        for(h in ExecutionPolicy.hours) {
            if(p.getLong("slot:$h",0)<=0 && p.getLong("alarm:$h",0)>0) edit.putLong("slot:$h",p.getLong("alarm:$h",0))
            if(p.getLong("delivered:$h",0)>0 && !p.contains("accepted:$h")) edit.putLong("accepted:$h",p.getLong("delivered:$h",0))
            edit.remove("alarm:$h")
        }
        check(edit.commit()) { "Migration persistence failed" }
        Diagnostics.emit(c,"SCHEDULER_MIGRATED",reason="six_alarm_clocks_bounded_service")
    }
    private fun register(c:Context,hour:Int,at:Long) {
        val p=prefs(c); val gen=UUID.randomUUID().toString()
        val key=if(hour==0) "probe" else "slot:$hour"
        check(p.edit().putLong(key,at).putString("generation:$hour",gen).putBoolean("armed:$hour",false).commit()) { "Plan persistence failed" }
        try {
            val show=PendingIntent.getActivity(c,2200+hour,Intent(c,MainActivity::class.java).setAction(Intent.ACTION_MAIN),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            c.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(at,show),operation(c,hour,at,gen)!!)
            check(p.edit().putBoolean("armed:$hour",true).putString("alarmError","").commit()) { "Registration persistence failed" }
            if(hour!=0) Diagnostics.registered(c,at)
            Diagnostics.emit(c,"ALARM_SCHEDULED",at,if(hour==0) "probe-$at" else Diagnostics.id(at),status="registered",details=JSONObject().put("hour",hour).put("method","setAlarmClock").put("generation",gen))
        } catch(e:RuntimeException) {
            cancel(c,hour)
            p.edit().putBoolean("armed:$hour",false).remove("generation:$hour").putString("alarmError","Registration failed: ${e.javaClass.simpleName}").commit()
            Diagnostics.emit(c,"ALARM_SCHEDULE_FAILED",at,reason=e.javaClass.simpleName)
            throw e
        }
    }
    @Synchronized fun restore(c:Context,reason:String,reset:Boolean=false):Boolean {
        migrate(c); SessionStore.reconcile(c)
        val p=prefs(c); val now=System.currentTimeMillis(); val zone=ZoneId.systemDefault().id
        val probe=p.getLong("probe",0)
        if(probe>0 && now>=probe+120000) {
            cancel(c,0); p.edit().remove("probe").remove("generation:0").putBoolean("armed:0",false).putString("probeStatus","No alarm-probe receiver delivery recorded by ${time(now)}; not replayed.").commit()
            Diagnostics.emit(c,"ALARM_MISSED",probe,"probe-$probe",reason="probe_overdue_during_reconciliation")
        }
        if(!enabled(c)) {
            if(allowed(c) && p.getString("alarmError","").orEmpty().startsWith("Exact-alarm capability missing")) p.edit().putString("alarmError","").commit()
            return false
        }
        val available=allowed(c)
        Diagnostics.emit(c,"ALARM_PERMISSION_CHECKED",status=if(available) "available" else "unavailable",reason=reason)
        if(!available) {
            val edit=prefs(c).edit().putString("alarmError","Exact-alarm capability missing. Open alarm access and repair.")
            for(h in ExecutionPolicy.hours) { cancel(c,h); edit.putBoolean("armed:$h",false).remove("generation:$h") }
            edit.commit(); updateNext(c); return false
        }
        val changed=reset || p.getString("alarmZone",zone)!=zone
        try {
            for(h in ExecutionPolicy.hours) {
                val old=p.getLong("slot:$h",0)
                // Opening settings/UI a few seconds after :26 must not invalidate an in-flight alarm.
                // Preserve its generation; only the real receiver may start that original session.
                if(!changed && reason in listOf("app_opened","manual_repair","alarm_delivery") &&
                    ExecutionPolicy.inWindow(old,now) && p.getBoolean("armed:$h",false) &&
                    !p.getString("generation:$h",null).isNullOrEmpty() && p.getLong("accepted:$h",0)!=old) {
                    Diagnostics.emit(c,"ALARM_RECONCILIATION_DEFERRED",old,status="awaiting_receiver",reason="preserve_pending_original_window",
                        details=JSONObject().put("hour",h).put("recoverySource",reason).put("remainingMs",old+480000-now))
                    continue
                }
                val rebuild=changed || old<=now || !p.getBoolean("armed:$h",false) || reason in listOf("reboot","app_update","manual_repair","permission_granted","app_opened")
                if(!rebuild) continue
                if(old>0 && old<=now && p.getLong("accepted:$h",0)!=old) {
                    Diagnostics.emit(c,"ALARM_MISSED",old,status="no_delivery_evidence",reason="overdue_during_$reason")
                    Diagnostics.outcome(c,old,"missed","no_receiver_delivery_recorded; no app-open replay")
                }
                val target=if(!changed && old>now) old else ExecutionPolicy.next(h,ZonedDateTime.now())
                if(old>now && old!=target) Diagnostics.outcome(c,old,"cancelled",reason)
                register(c,h,target)
            }
            p.edit().putString("alarmZone",zone).commit(); updateNext(c)
            return true
        } catch(_:RuntimeException) { updateNext(c); return false }
    }
    @Synchronized fun advance(c:Context,h:Int) {
        // Compute from the current local calendar: no fixed 24-hour arithmetic or expired rearming.
        if(enabled(c) && allowed(c)) register(c,h,ExecutionPolicy.next(h,ZonedDateTime.now()))
        else prefs(c).edit().putBoolean("armed:$h",false).commit()
        updateNext(c)
    }
    fun updateNext(c:Context) {
        val p=prefs(c)
        p.edit().putLong("next",ExecutionPolicy.hours.filter { p.getBoolean("armed:$it",false) }.map { p.getLong("slot:$it",0) }.filter { it>0 }.minOrNull() ?: 0).commit()
    }
    fun enable(c:Context):Boolean {
        check(prefs(c).edit().putBoolean("enabled",true).commit()) { "Enable persistence failed" }
        return restore(c,"manual_repair")
    }
    @Synchronized fun disable(c:Context) {
        val p=prefs(c); val edit=p.edit().putBoolean("enabled",false).remove("next").remove("probe")
        for(h in ExecutionPolicy.hours) {
            cancel(c,h)
            val at=p.getLong("slot:$h",0)
            if(at>0) Diagnostics.outcome(c,at,"cancelled","user_paused")
            edit.remove("slot:$h").remove("generation:$h").putBoolean("armed:$h",false)
        }
        cancel(c,0); edit.remove("generation:0").putBoolean("armed:0",false).commit()
        Diagnostics.emit(c,"SCHEDULER_PAUSED",reason="user_requested; active run may finish")
    }
    @Synchronized fun probe(c:Context,delay:Long) {
        require(delay in 1000L..8L*3600000)
        check(allowed(c)) { "Exact alarm access missing" }
        val at=System.currentTimeMillis()+delay
        register(c,0,at)
        prefs(c).edit().putString("probeStatus","Alarm-clock probe waiting for ${time(at)}. No claim.").commit()
    }
    fun pruneHistory(c:Context) {
        val p=prefs(c); val old=p.getString("events","").orEmpty(); val kept=LogRetention.retain(old).joinToString("\n")
        if(old!=kept) p.edit().putString("events",kept).commit()
    }
    fun checkOnOpen(c:Context) {
        Diagnostics.emit(c,"APP_OPENED")
        Diagnostics.work { migrate(c); pruneHistory(c); Diagnostics.prune(c); Diagnostics.processHistory(c); restore(c,"app_opened") }
    }
}

/** Brief, bounded handoff ownership; service releases only after acquiring its own timed lock. */
object Handoff {
    private val locks=mutableMapOf<String,PowerManager.WakeLock>()
    @Synchronized fun acquire(c:Context,id:String,remaining:Long) {
        locks.entries.removeAll { !it.value.isHeld }
        locks.remove(id)?.let { if(it.isHeld) it.release() }
        locks[id]=c.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Interlink:alarm-handoff").apply { acquire(remaining.coerceIn(1,15000)) }
    }
    @Synchronized fun release(id:String) { locks.remove(id)?.let { if(it.isHeld) it.release() } }
    @Synchronized fun held()=locks.values.any { it.isHeld }
}

class SlotAlarmReceiver:BroadcastReceiver() {
    override fun onReceive(c:Context,i:Intent) {
        val received=System.currentTimeMillis(); val elapsed=SystemClock.elapsedRealtime()
        val h=i.getIntExtra("hour",-1); val at=i.getLongExtra("scheduled",0); val gen=i.getStringExtra("generation") ?: ""
        val attempt=UUID.randomUUID().toString(); val mode=if(h==0) "probe" else "scheduled"
        val execution=if(h==0) "probe-$at" else Diagnostics.id(at)
        val details=JSONObject().put("attemptId",attempt).put("pidAtEvent",Process.myPid()).put("receiverAt",received).put("receiverElapsedMs",elapsed).put("delayMs",received-at).put("hour",h).put("mode",mode)
        Diagnostics.emit(c,"ALARM_RECEIVED",at,execution,status="delivered",details=details,actual=received)
        if((h !in ExecutionPolicy.hours && h!=0) || i.action!=(if(h==0) Schedule.PROBE else Schedule.ACTION)) return
        val p=Schedule.prefs(c); val key=if(h==0) "probe" else "slot:$h"
        synchronized(Schedule) {
            if(gen.isEmpty() || p.getString("generation:$h",null)!=gen || p.getLong(key,0)!=at || (h!=0 && !Schedule.enabled(c))) {
                Diagnostics.emit(c,"ALARM_IGNORED",at,execution,reason="stale_cancelled_or_disabled",details=details); return
            }
            p.edit().putBoolean("armed:$h",false).commit()
            val duration=if(h==0)120000L else 480000L
            if(h!=0 && p.getLong("accepted:$h",0)==at) {
                Diagnostics.emit(c,"ALARM_IGNORED",at,execution,reason="duplicate_accepted_slot",details=details)
                try { Schedule.advance(c,h) } catch(_:RuntimeException) {}
                return
            }
            if(received<at || received>=at+duration) {
                val reason="outside_original_deadline"
                Diagnostics.emit(c,"ALARM_MISSED",at,execution,status="skipped",reason=reason,details=details)
                if(h!=0) { Diagnostics.outcome(c,at,"missed",reason); try { Schedule.advance(c,h) } catch(_:RuntimeException) {} }
                else p.edit().remove("probe").putString("probeStatus","Alarm received outside probe deadline; no work launched.").commit()
                return
            }
            if(!SessionStore.reserve(c,at,mode,attempt,h.takeIf { it!=0 },received,elapsed)) {
                Diagnostics.emit(c,"EXECUTION_FAILED",at,execution,status="skipped",reason="another_execution_active",details=details)
                if(h!=0) { Diagnostics.outcome(c,at,"failed","another_execution_active"); try { Schedule.advance(c,h) } catch(_:RuntimeException) {} }
                else p.edit().remove("probe").putString("probeStatus","Probe skipped: another execution is active.").commit()
                return
            }
            if(h!=0) Diagnostics.outcome(c,at,"triggered","alarm_clock_receiver",delivered=received)
            else p.edit().remove("probe").putString("probeStatus","Alarm delivered; awaiting bounded service. Delay ${received-at}ms.").commit()
            try {
                Handoff.acquire(c,attempt,at+duration-System.currentTimeMillis())
                Diagnostics.emit(c,"EXECUTION_DISPATCH_REQUESTED",at,execution,details=details)
                RunService.launch(c,at,mode,attempt,received,elapsed)
            } catch(e:RuntimeException) {
                Handoff.release(attempt); SessionStore.finish(c,attempt)
                Diagnostics.emit(c,"EXECUTION_FAILED",at,execution,status="start_rejected",reason=e.javaClass.simpleName,details=details)
                if(h!=0) Diagnostics.outcome(c,at,"failed","foreground_start_${e.javaClass.simpleName}")
                else p.edit().putString("probeStatus","Service start rejected: ${e.javaClass.simpleName}").commit()
            } finally {
                if(h!=0) try { Schedule.advance(c,h) } catch(_:RuntimeException) {}
                // Also repair independently missed/expired slots at the next real alarm, without a resident loop.
                RecoveryWorker.execute { Schedule.restore(c,"alarm_delivery") }
            }
        }
    }
}

class RecoveryReceiver:BroadcastReceiver() {
    override fun onReceive(c:Context,i:Intent) {
        val reason=when(i.action) {
            Intent.ACTION_BOOT_COMPLETED -> "reboot"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "app_update"
            Intent.ACTION_TIME_CHANGED -> "clock_change"
            Intent.ACTION_TIMEZONE_CHANGED -> "timezone_change"
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> "permission_granted"
            else -> return
        }
        Diagnostics.emit(c,"RECOVERY_ATTEMPTED",reason=reason)
        // CE/Termux credentials are deliberately unavailable before first unlock.
        val pending=goAsync()
        RecoveryWorker.execute { try { Schedule.restore(c,reason,reason in listOf("clock_change","timezone_change")); Schedule.pruneHistory(c); Diagnostics.prune(c); Diagnostics.processHistory(c) }
            catch(e:RuntimeException) { Diagnostics.emit(c,"RECOVERY_FAILED",reason=e.javaClass.simpleName); Schedule.prefs(c).edit().putString("alarmError","Recovery failed: ${e.javaClass.simpleName}").commit() }
            finally { pending?.finish() } }
    }
}
object RecoveryWorker {
    private val executor=java.util.concurrent.Executors.newSingleThreadExecutor()
    fun execute(work:()->Unit) { executor.execute { try { work() } catch(_:RuntimeException) {} } }
}
