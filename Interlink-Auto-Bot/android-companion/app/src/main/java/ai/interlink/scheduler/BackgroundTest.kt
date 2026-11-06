package ai.interlink.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** One explicitly requested diagnostic alarm. Never submits a claim. */
object BackgroundTest {
    const val ACTION = "ai.interlink.scheduler.TEST_ALARM"
    private fun pending(c: Context, at: Long = 0) = PendingIntent.getBroadcast(c,900,
        Intent(c,BackgroundTestReceiver::class.java).setAction(ACTION).putExtra("scheduled",at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(c: Context, delayMillis: Long = 120000, alarmClock: Boolean = false) {
        val p = Schedule.prefs(c)
        require(delayMillis > 0)
        val at = System.currentTimeMillis()+delayMillis
        try {
            val operation = pending(c,at)
            if (alarmClock) {
                val show = PendingIntent.getActivity(c,901,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                Schedule.manager(c).setAlarmClock(AlarmManager.AlarmClockInfo(at,show),operation)
            } else Schedule.manager(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,operation)
            p.edit().putString("probeMethod",if(alarmClock) "alarm_clock" else "exact_while_idle").putLong("probeAt",at).putLong("probeLatest",at).putString("probeStatus","${if(alarmClock) "Alarm-clock" else "Standard"} test waiting for ${Schedule.time(at)}").commit()
            Diagnostics.emit(c,"ALARM_SCHEDULED",at,"test-$at",status="registered",reason=if(alarmClock) "alarm_clock_probe" else "connection_probe")
            Schedule.record(c,"${if(alarmClock) "Alarm-clock" else "Standard"} background test alarm registered: intended ${Schedule.time(at)}. No claim; registration is not delivery.")
        } catch (error: RuntimeException) {
            p.edit().putString("probeStatus","Registration failed: ${error.javaClass.simpleName}").commit()
            Schedule.record(c,"Background test registration failed: ${error.javaClass.simpleName}.")
        }
    }
    fun cancel(c: Context) {
        Schedule.manager(c).cancel(pending(c))
        Schedule.prefs(c).edit().remove("probeAt").remove("probeLatest").putString("probeStatus","Cancelled; an already dispatched connection test may finish.").commit()
    }
    fun check(c: Context) {
        val p = Schedule.prefs(c)
        val at = p.getLong("probeAt",0)
        if (at > 0 && System.currentTimeMillis() > at+120000 && p.getLong("probeReported",0)!=at) {
            p.edit().putLong("probeReported",at).putString("probeStatus","No receiver delivery recorded by ${Schedule.time(System.currentTimeMillis())}").commit()
            Schedule.record(c,"Background test overdue: intended ${Schedule.time(at)}; no receiver delivery recorded. Not replayed on opening.")
        }
    }
}
class BackgroundTestReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val alarmClock = Schedule.prefs(c).getString("probeMethod","") == "alarm_clock"
        val method = if(alarmClock) "alarm_clock_probe" else "connection_probe"
        Diagnostics.emit(c,"RECEIVER_STARTED",i.getLongExtra("scheduled",0),"test-${i.getLongExtra("scheduled",0)}",reason=method)
        if (i.action != BackgroundTest.ACTION) return
        val p = Schedule.prefs(c)
        val at = i.getLongExtra("scheduled",0)
        if (at <= 0 || p.getLong("probeAt",0)!=at) return
        val now = System.currentTimeMillis()
        Diagnostics.emit(c,"ALARM_RECEIVED",at,"test-$at",status="delivered",reason=method,details=org.json.JSONObject().put("delayMs",now-at))
        if(alarmClock) {
            try {
                val nm=c.getSystemService(android.app.NotificationManager::class.java)
                nm.createNotificationChannel(android.app.NotificationChannel("alarm_test","Alarm-clock test results",android.app.NotificationManager.IMPORTANCE_DEFAULT))
                val open=PendingIntent.getActivity(c,901,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                nm.notify(902,android.app.Notification.Builder(c,"alarm_test").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("Alarm-clock test delivered").setContentText("Delay ${(now-at)/1000}s. No claim submitted by this test.")
                    .setContentIntent(open).setAutoCancel(true).build())
            } catch(error:RuntimeException) { Diagnostics.emit(c,"TEST_NOTIFICATION_FAILED",at,"test-$at",reason=error.javaClass.simpleName) }
        }
        p.edit().remove("probeAt").putString("probeStatus","Alarm delivered; awaiting connection test.").commit()
        Schedule.record(c,"Background test alarm delivered: intended ${Schedule.time(at)}; actual ${Schedule.time(now)}; delay ${(now-at)/1000}s.")
        if (now < at || now >= at+120000) {
            p.edit().putString("probeStatus","Alarm delivered outside its 2-minute execution deadline; no command launched.").commit()
            Schedule.record(c,"Background test delivery outside deadline; no Termux command launched."); return
        }
        if (p.getLong("activeUntil",0)>now) {
            p.edit().putString("probeStatus","Alarm delivered; execution busy. Repeat later.").commit()
            Schedule.record(c,"Background test delivered but execution busy; no command launched."); return
        }
        try {
            c.startForegroundService(Intent(c,RunService::class.java).putExtra("scheduled",at).putExtra("mode","test").putExtra("probe",true))
        } catch (error: RuntimeException) {
            p.edit().putString("probeStatus","Alarm delivered; service rejected: ${error.javaClass.simpleName}").commit()
            Schedule.record(c,"Background test service rejected: ${error.javaClass.simpleName}.")
        }
    }
}
