package ai.interlink.scheduler

import android.app.*
import android.content.*
import android.os.*
import java.time.ZonedDateTime
import java.time.ZoneId

/** User-enabled continuous clock. CPU wake lock is intentional and visible in the notification. */
class PersistentMonitorService : Service() {
    companion object {
        private const val CHANNEL = "continuous_monitor"
        private const val ACTION_START = "ai.interlink.scheduler.MONITOR_START"
        private const val ACTION_STOP = "ai.interlink.scheduler.MONITOR_STOP"
        private const val ACTION_REFRESH = "ai.interlink.scheduler.MONITOR_REFRESH"
        @Volatile var active = false
            private set
        fun desired(c: Context) = Schedule.prefs(c).getBoolean("continuousMonitor",false)
        fun start(c: Context, reason: String) {
            if (!desired(c) || !Schedule.prefs(c).getBoolean("enabled",false)) return
            try {
                c.startForegroundService(Intent(c,PersistentMonitorService::class.java).setAction(ACTION_START).putExtra("reason",reason))
                Diagnostics.emit(c,"MONITOR_START_REQUESTED",reason=reason)
            } catch (e:RuntimeException) {
                Diagnostics.emit(c,"MONITOR_START_REJECTED",reason=e.javaClass.simpleName)
                Schedule.record(c,"Continuous monitor could not start: ${e.javaClass.simpleName}.")
            }
        }
        fun scheduleProbe(c: Context, delayMillis: Long = 120000L) {
            require(delayMillis in 1000L..3600000L)
            if(!active || !desired(c)) return
            val at=System.currentTimeMillis()+delayMillis
            Schedule.prefs(c).edit().putLong("monitorProbeAt",at).commit()
            Diagnostics.emit(c,"MONITOR_TEST_PLANNED",at,status="registered",reason="no_claim_continuous_test")
            refresh(c)
        }
        fun refresh(c: Context) {
            if(active) c.startService(Intent(c,PersistentMonitorService::class.java).setAction(ACTION_REFRESH))
        }
        fun stop(c: Context) {
            Schedule.prefs(c).edit().putBoolean("continuousMonitor",false).remove("monitorProbeAt").commit()
            c.stopService(Intent(c,PersistentMonitorService::class.java))
            Diagnostics.emit(c,"MONITOR_STOP_REQUESTED",reason="user_or_schedule_paused")
        }
    }
    private val handler=Handler(Looper.getMainLooper())
    private var wake:PowerManager.WakeLock?=null
    private var nextAt=0L
    private var timer:Runnable?=null
    private var probeTimer:Runnable?=null
    private val clockReceiver=object:BroadcastReceiver() {
        override fun onReceive(c:Context,i:Intent) { Diagnostics.emit(c,"MONITOR_CLOCK_CHANGED",reason=i.action ?: "unknown"); plan() }
    }
    override fun onBind(i:Intent?)=null
    override fun onCreate() {
        super.onCreate()
        active=true
        val filter=IntentFilter().apply { addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED) }
        if(Build.VERSION.SDK_INT>=33) registerReceiver(clockReceiver,filter,Context.RECEIVER_NOT_EXPORTED) else registerReceiver(clockReceiver,filter)
    }
    override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int {
        if(i?.action==ACTION_STOP) { stop(this); stopSelf(); return START_NOT_STICKY }
        if(!desired(this) || !Schedule.prefs(this).getBoolean("enabled",false)) { stopSelf(); return START_NOT_STICKY }
        val nm=getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL,"Continuous scheduling",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,903,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(this,904,Intent(this,PersistentMonitorService::class.java).setAction(ACTION_STOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            startForeground(3,Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Interlink continuous scheduling")
                .setContentText("Keeping the CPU awake for scheduled runs; uses battery")
                .setContentIntent(open).addAction(android.R.drawable.ic_delete,"Stop monitoring",stop).setOngoing(true).build())
            if(wake?.isHeld!=true) {
                wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Interlink:continuous-schedule").also { it.acquire() }
                Diagnostics.emit(this,"MONITOR_WAKE_LOCK_ACQUIRED",reason="continuous_user_enabled")
            }
            Diagnostics.emit(this,"MONITOR_STARTED",reason=i?.getStringExtra("reason") ?: if(i==null) "system_sticky_restart" else "timer_or_schedule_refresh")
            if(i == null) Schedule.recover(this,"monitor restart")
            plan()
            planProbe()
        } catch(e:RuntimeException) {
            Diagnostics.emit(this,"MONITOR_START_FAILED",reason=e.javaClass.simpleName)
            Schedule.prefs(this).edit().putBoolean("continuousMonitor",false).commit()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }
    private fun plan() {
        timer?.let(handler::removeCallbacks)
        if(!desired(this) || !Schedule.prefs(this).getBoolean("enabled",false)) { stopSelf(); return }
        val now=System.currentTimeMillis()
        val p=Schedule.prefs(this)
        val target=ExecutionPolicy.hours.mapNotNull { h ->
            val at=p.getLong("alarm:$h",0)
            if(at>now && p.getLong("delivered:$h",0)!=at) h to at else null
        }.minByOrNull { it.second }
        if(target==null) {
            Diagnostics.emit(this,"MONITOR_NO_FUTURE_SLOT",reason="repair_schedule_needed")
            // The app can be reopened to repair. Never synthesize an expired claim.
            nextAt=0
            stopSelf()
            return
        }
        nextAt=target.second
        Diagnostics.emit(this,"MONITOR_TIMER_PLANNED",nextAt,reason="continuous_clock")
        val task=Runnable {
            val at=target.second
            if(!desired(this) || !Schedule.prefs(this).getBoolean("enabled",false)) { stopSelf(); return@Runnable }
            val current=System.currentTimeMillis()
            if(current<at) { plan(); return@Runnable }
            if(Schedule.prefs(this).getLong("alarm:${target.first}",0)==at && Schedule.prefs(this).getLong("delivered:${target.first}",0)!=at) {
                Diagnostics.emit(this,"MONITOR_TIMER_FIRED",at,status="timer_fired",reason="continuous_clock",details=org.json.JSONObject().put("delayMs",current-at))
                AlarmReceiver().receive(this,Intent(this,AlarmReceiver::class.java).setAction(Schedule.ACTION)
                    .putExtra("hour",target.first).putExtra("scheduled",at).putExtra("recovery",true).putExtra("monitor",true))
            } else Diagnostics.emit(this,"MONITOR_TIMER_SKIPPED",at,reason="alarm_already_delivered_or_replaced")
            plan()
        }
        timer=task
        handler.postDelayed(task,(target.second-now).coerceAtLeast(1))
    }
    private fun planProbe() {
        probeTimer?.let(handler::removeCallbacks)
        val at=Schedule.prefs(this).getLong("monitorProbeAt",0)
        if(at<=0) return
        val now=System.currentTimeMillis()
        if(at<now-15000) {
            Diagnostics.emit(this,"MONITOR_TEST_MISSED",at,status="missed",reason="service_not_running_at_test_time")
            Schedule.prefs(this).edit().remove("monitorProbeAt").commit()
            return
        }
        val task=Runnable {
            if(Schedule.prefs(this).getLong("monitorProbeAt",0)!=at || !desired(this)) return@Runnable
            val actual=System.currentTimeMillis()
            if(actual<at) { planProbe(); return@Runnable }
            Schedule.prefs(this).edit().remove("monitorProbeAt").commit()
            Diagnostics.emit(this,"MONITOR_TEST_TIMER_FIRED",at,status="delivered",reason="no_claim_continuous_test",
                details=org.json.JSONObject().put("delayMs",actual-at))
            val nm=getSystemService(NotificationManager::class.java)
            val open=PendingIntent.getActivity(this,903,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(905,Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Continuous timer test fired")
                .setContentText("Delay ${(actual-at)/1000}s. No claim submitted.")
                .setContentIntent(open).setAutoCancel(true).build())
        }
        probeTimer=task
        handler.postDelayed(task,(at-now).coerceAtLeast(1))
    }
    override fun onTaskRemoved(rootIntent:Intent?) {
        Diagnostics.emit(this,"MONITOR_TASK_REMOVED",status=if(wake?.isHeld==true) "running_with_wake_lock" else "wake_lock_missing",reason="recents_task_removed")
        // Keep this started foreground service and its timers. START_STICKY permits OS restart.
        super.onTaskRemoved(rootIntent)
    }
    override fun onDestroy() {
        timer?.let(handler::removeCallbacks)
        probeTimer?.let(handler::removeCallbacks)
        try { unregisterReceiver(clockReceiver) } catch(_:RuntimeException) {}
        if(wake?.isHeld==true) wake?.release()
        wake=null
        active=false
        Diagnostics.emit(this,"MONITOR_STOPPED")
        super.onDestroy()
    }
}
