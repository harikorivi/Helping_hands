package ai.interlink.scheduler

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.*
import android.database.sqlite.*
import android.net.*
import android.os.*
import org.json.JSONObject
import java.time.*

/** Private durable evidence. Failure to save is surfaced, never silently treated as successful evidence. */
object Diagnostics {
    private var helper: SQLiteOpenHelper? = null
    @Synchronized private fun db(c: Context): SQLiteDatabase {
        if (helper == null) helper = object : SQLiteOpenHelper(c.applicationContext,"execution-diagnostics.db",null,1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY, at INTEGER NOT NULL, payload TEXT NOT NULL)")
                db.execSQL("CREATE TABLE runs (executionId TEXT PRIMARY KEY, scheduledAt INTEGER, deliveredAt INTEGER, status TEXT, reason TEXT, result TEXT)")
            }
            override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}
        }
        return helper!!.writableDatabase
    }
    fun liveDebug(c: Context) = Schedule.prefs(c).getBoolean("liveDebug",false)
    fun debug(c: Context, event: String, reason: String = "") {
        if (liveDebug(c)) emit(c,event,reason=reason)
    }
    fun id(at: Long) = "schedule-$at"
    fun device(c: Context): JSONObject {
        val pm=c.getSystemService(PowerManager::class.java)
        val battery=c.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val cm=c.getSystemService(ConnectivityManager::class.java)
        val caps=cm.getNetworkCapabilities(cm.activeNetwork)
        return JSONObject().put("androidVersion",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT)
            .put("batterySaver",pm.isPowerSaveMode).put("charging",battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)?.let { it != 0 } ?: JSONObject.NULL)
            .put("idle",pm.isDeviceIdleMode).put("interactive",pm.isInteractive)
            .put("keyguardLocked",c.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
            .put("deviceLocked",c.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked)
            .put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL)
            .put("liveDebugEnabled",liveDebug(c))
            .put("standbyBucket",if(Build.VERSION.SDK_INT>=28) c.getSystemService(UsageStatsManager::class.java).appStandbyBucket else -1)
            .put("exactAlarmCapability",Schedule.allowed(c)).put("batteryExempt",pm.isIgnoringBatteryOptimizations(c.packageName))
            .put("networkAvailable",caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true)
            .put("networkValidated",caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true)
            .put("pid",Process.myPid()).put("elapsedMs",SystemClock.elapsedRealtime())
    }
    @Synchronized fun emit(c: Context,event: String,at: Long=0,executionId: String=if(at>0) id(at) else "system",
                          status: String="observed",reason: String="",details: JSONObject=JSONObject(),actual: Long=System.currentTimeMillis(),source: String="android") {
        try {
            val row=JSONObject().put("event",event).put("executionId",executionId).put("scheduledAt",if(at>0) at else JSONObject.NULL)
                .put("actualAt",actual).put("recordedAt",System.currentTimeMillis()).put("status",status).put("reason",reason).put("source",source).put("details",details)
            // Node cannot observe Android power state: callback-time state is explicitly separate.
            if(source=="android") row.put("device",device(c)) else row.put("device",JSONObject.NULL).put("deviceAtImport",device(c))
            db(c).insertOrThrow("events",null,ContentValues().apply { put("at",System.currentTimeMillis()); put("payload",row.toString()) })
        } catch (e: RuntimeException) {
            Schedule.prefs(c).edit().putString("diagnosticError","Diagnostic persistence failed: ${e.javaClass.simpleName}").commit()
            android.util.Log.e("InterlinkDiagnostics","Diagnostic persistence failed: ${e.javaClass.simpleName}")
        }
    }
    @Synchronized fun registered(c: Context, at: Long) {
        try { db(c).execSQL("INSERT OR IGNORE INTO runs(executionId,scheduledAt,status) VALUES(?,?,?)",arrayOf(id(at),at,"registered")) }
        catch(e: RuntimeException) { emit(c,"DIAGNOSTIC_STORAGE_FAILED",at,reason=e.javaClass.simpleName) }
    }
    @Synchronized fun outcome(c: Context, at: Long, status: String, reason: String, result: String="", delivered: Long?=null) {
        if(at<=0) return
        registered(c,at)
        try {
            val values=ContentValues().apply { put("status",status); put("reason",reason); if(result.isNotEmpty()) put("result",result); if(delivered!=null) put("deliveredAt",delivered) }
            db(c).update("runs",values,"executionId=?",arrayOf(id(at)))
        } catch(e: RuntimeException) { emit(c,"DIAGNOSTIC_STORAGE_FAILED",at,reason=e.javaClass.simpleName) }
    }
    @Synchronized fun prune(c: Context) {
        try {
            val cutoff=LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            db(c).delete("events","at < ?",arrayOf(cutoff.toString()))
            db(c).execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT 4000)")
            db(c).delete("runs","scheduledAt < ?",arrayOf((System.currentTimeMillis()-30L*86400000).toString()))
        } catch(e: RuntimeException) { emit(c,"DIAGNOSTIC_STORAGE_FAILED",reason=e.javaClass.simpleName) }
    }
    @Synchronized fun report(c: Context): String = try {
        val lines=mutableListOf<String>()
        db(c).rawQuery("SELECT payload FROM events ORDER BY id",null).use { while(it.moveToNext()) lines.add(it.getString(0)) }
        dashboard(c)+"\nEvents JSONL (today/yesterday, max 4000; deviceAtImport is NOT historical Node power state):\n"+lines.joinToString("\n")
    } catch(e:RuntimeException) { "Diagnostic read failed: ${e.javaClass.simpleName}" }
    @Synchronized fun dashboard(c: Context): String = try {
        val rows=mutableListOf<String>()
        db(c).rawQuery("SELECT scheduledAt,status,reason,result FROM runs ORDER BY scheduledAt DESC LIMIT 12",null).use {
            while(it.moveToNext()) rows.add("${Schedule.time(it.getLong(0))}: ${it.getString(1)} ${it.getString(2) ?: ""}\n${it.getString(3) ?: "No Node window report yet."}")
        }
        var stats=""
        db(c).rawQuery("SELECT COUNT(*),COUNT(deliveredAt),AVG(deliveredAt-scheduledAt),MAX(deliveredAt-scheduledAt) FROM runs WHERE scheduledAt<=? AND status!='cancelled'",arrayOf(System.currentTimeMillis().toString())).use {
            it.moveToFirst(); stats="Due registered slots: ${it.getInt(0)}; receiver deliveries: ${it.getInt(1)}; mean latency: ${if(it.isNull(2)) "n/a" else "${it.getLong(2)}ms"}; max: ${if(it.isNull(3)) "n/a" else "${it.getLong(3)}ms"}."
        }
        val last=mutableListOf<String>()
        for((title,phase) in listOf("Last successful run" to "success","Last failed/incomplete run" to "incomplete","Last missed run" to "missed","Last unknown run" to "unknown","Last dispatch failure" to "failed")) {
            db(c).rawQuery("SELECT scheduledAt,reason FROM runs WHERE status=? ORDER BY scheduledAt DESC LIMIT 1",arrayOf(phase)).use {
                last.add("$title: "+if(it.moveToFirst()) "${Schedule.time(it.getLong(0))} ${it.getString(1)}" else "No evidence yet")
            }
        }
        for(name in listOf("ALARM_SCHEDULED","ALARM_RECEIVED")) {
            db(c).rawQuery("SELECT payload FROM events WHERE payload LIKE ? ORDER BY id DESC LIMIT 1",arrayOf("%\"event\":\"$name\"%")).use {
                if(it.moveToFirst()) { val row=JSONObject(it.getString(0)); last.add("Last $name: ${Schedule.time(row.getLong("actualAt"))} (${row.getString("executionId")})") }
            }
        }
        "Evidence since this diagnostic version (run summaries retained 30 days):\n$stats\n"+last.joinToString("\n")+"\n\n"+rows.joinToString("\n\n")
    } catch(e: RuntimeException) { "Diagnostic read failed: ${e.javaClass.simpleName}" }
    fun reconcileActive(c: Context) {
        val p=Schedule.prefs(c); val active=p.getString("activeId",null) ?: return
        if(p.getLong("activeUntil",0)<System.currentTimeMillis()) {
            val at=p.getLong("runTime:$active",0)
            emit(c,"EXECUTION_FAILED",at,status="unknown",reason="deadline_passed_without_callback",details=JSONObject().put("attemptId",active))
            if(p.getString("activeMode","")=="scheduled") outcome(c,at,"unknown","deadline_passed_without_callback; claim outcome unknown")
            p.edit().remove("activeId").remove("activeUntil").remove("active").remove("activeMode").commit()
        }
    }
    fun processHistory(c: Context) {
        if(Build.VERSION.SDK_INT>=30) {
            try {
                val p=Schedule.prefs(c); val since=p.getLong("exitHistoryAt",0)
                val exits=c.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(c.packageName,0,8)
                for(e in exits.filter { it.timestamp>since }) emit(c,"PROCESS_EXIT_OBSERVED",reason="reason_code_${e.reason}",details=JSONObject().put("exitAt",e.timestamp).put("pid",e.pid).put("status",e.status))
                p.edit().putLong("exitHistoryAt",System.currentTimeMillis()).apply()
            } catch(_:RuntimeException) { emit(c,"PROCESS_EXIT_HISTORY_UNAVAILABLE") }
        }
    }
}
class DiagnosticApplication : android.app.Application() {
    private val receiver=object:BroadcastReceiver() {
        override fun onReceive(c:Context,i:Intent) {
            val event = when(i.action) {
                Intent.ACTION_SCREEN_ON -> "SCREEN_ON_OBSERVED"
                Intent.ACTION_SCREEN_OFF -> "SCREEN_OFF_OBSERVED"
                Intent.ACTION_USER_PRESENT -> "USER_UNLOCK_OBSERVED"
                else -> "POWER_STATE_CHANGED"
            }
            Diagnostics.debug(c,event,reason=i.action?.substringAfterLast('.') ?: "unknown")
        }
    }
    override fun onCreate() {
        super.onCreate()
        val filter=IntentFilter().apply { addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED); addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED); addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver,filter)
        // Dynamic receiver exists only while this process lives; no resident monitoring service.
        Diagnostics.emit(this,"PROCESS_STARTED")
    }
}
