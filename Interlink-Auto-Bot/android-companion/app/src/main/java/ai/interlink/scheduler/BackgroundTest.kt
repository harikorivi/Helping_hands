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
    fun schedule(c: Context, delayMillis: Long = 120000) {
        val p = Schedule.prefs(c)
        require(delayMillis > 0)
        val at = System.currentTimeMillis()+delayMillis
        try {
            Schedule.manager(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending(c,at))
            p.edit().putLong("probeAt",at).putLong("probeLatest",at).putString("probeStatus","Waiting for ${Schedule.time(at)}").commit()
            Diagnostics.emit(c,"ALARM_SCHEDULED",at,"test-$at",status="registered",reason="connection_probe")
            Schedule.record(c,"Background test alarm registered: intended ${Schedule.time(at)}. No claim; registration is not delivery.")
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
        Diagnostics.emit(c,"RECEIVER_STARTED",i.getLongExtra("scheduled",0),"test-${i.getLongExtra("scheduled",0)}",reason="connection_probe")
        if (i.action != BackgroundTest.ACTION) return
        val p = Schedule.prefs(c)
        val at = i.getLongExtra("scheduled",0)
        if (at <= 0 || p.getLong("probeAt",0)!=at) return
        val now = System.currentTimeMillis()
        Diagnostics.emit(c,"ALARM_RECEIVED",at,"test-$at",status="delivered",reason="connection_probe",details=org.json.JSONObject().put("delayMs",now-at))
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
