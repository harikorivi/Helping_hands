package ai.interlink.diagnostics

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.graphics.Color
import android.widget.*
import java.time.*
import java.time.format.DateTimeFormatter

object Probe {
    const val ACTION = "ai.interlink.diagnostics.PROBE"
    fun prefs(c: Context) = c.getSharedPreferences("diagnostics",Context.MODE_PRIVATE)
    fun manager(c: Context) = c.getSystemService(AlarmManager::class.java)
    fun allowed(c: Context) = Build.VERSION.SDK_INT < 31 || manager(c).canScheduleExactAlarms()
    fun format(at: Long) = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss XXX"))
    fun pending(c: Context, id: String) = PendingIntent.getBroadcast(c,1,
        Intent(c,ProbeReceiver::class.java).setAction(ACTION).putExtra("id",id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    @Synchronized fun record(c: Context, event: String) {
        val p = prefs(c)
        val line = "${Instant.now()} elapsedMs=${SystemClock.elapsedRealtime()} $event"
        p.edit().putString("history",(p.getString("history","").orEmpty().lines().filter { it.isNotBlank() } + line).takeLast(150).joinToString("\n")).commit()
    }
    fun power(c: Context): String {
        val pm = c.getSystemService(PowerManager::class.java)
        return "idle=${pm.isDeviceIdleMode} saver=${pm.isPowerSaveMode} interactive=${pm.isInteractive} batteryExempt=${pm.isIgnoringBatteryOptimizations(c.packageName)}"
    }
    fun schedule(c: Context, minutes: Int, scenario: String) {
        val p = prefs(c)
        val at = System.currentTimeMillis()+minutes*60000L
        val elapsed = SystemClock.elapsedRealtime()+minutes*60000L
        val id = java.util.UUID.randomUUID().toString()
        // Replace the prior test explicitly; daily Interlink alarms belong to another package.
        manager(c).cancel(pending(c,"cancel"))
        p.edit().remove("expected").remove("delivered").remove("id").commit()
        p.edit().putString("id",id).putLong("expected",at).putLong("expectedElapsed",elapsed).putString("scenario",scenario).commit()
        try {
            manager(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending(c,id))
            record(c,"REGISTERED id=$id scenario=$scenario expected=${format(at)} expectedEpochMs=$at ${power(c)}")
        } catch (e: RuntimeException) {
            p.edit().remove("expected").remove("id").commit()
            record(c,"REGISTRATION_FAILED type=${e.javaClass.simpleName}")
        }
    }
    fun cancel(c: Context) {
        manager(c).cancel(pending(c,"cancel"))
        prefs(c).edit().remove("expected").remove("id").remove("delivered").commit()
        record(c,"CANCELLED_BY_USER")
    }
    fun report(c: Context): String {
        val p = prefs(c)
        return "Alarm Delivery Check 1.0 (offline; no Termux or claims)\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}; SDK ${Build.VERSION.SDK_INT}\n" +
            "Timezone: ${ZoneId.systemDefault().id}\nExact-alarm capability: ${allowed(c)}\n${power(c)}\n" +
            "Scenario selected: ${p.getString("scenario","none")}\n" +
            Timing.status(p.getLong("expected",0),p.getLong("delivered",0),System.currentTimeMillis()) +
            "\nEvents (UTC + elapsed time since boot; max 150):\n" + p.getString("history","")
    }
}

class ProbeReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val p = Probe.prefs(c)
        val id = i.getStringExtra("id") ?: return
        if (i.action != Probe.ACTION || p.getString("id",null)!=id || p.getLong("delivered",0)>0) return
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        p.edit().putLong("delivered",now).commit()
        Probe.record(c,"RECEIVER_DELIVERED id=$id wallDelayMs=${Timing.delay(now,p.getLong("expected",now))} elapsedDelayMs=${elapsed-p.getLong("expectedElapsed",elapsed)} ${Probe.power(c)}")
    }
}
class ChangesReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Probe.record(c,"SYSTEM_EVENT action=${i.action}; no automatic replay")
        if (i.action == Intent.ACTION_BOOT_COMPLETED) {
            Probe.prefs(c).edit().remove("id").remove("expected").remove("delivered").commit()
            Probe.record(c,"TEST_INVALIDATED_BY_REBOOT; schedule a new test")
        }
    }
}
class MainActivity : Activity() {
    private lateinit var body: LinearLayout
    private var scenario = "Home + screen locked"
    private val handler = Handler(Looper.getMainLooper())
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> handler.post { render() } }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        body = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,28,32,28) }
        setContentView(ScrollView(this).apply {
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom); insets
            }
            addView(body)
        })
        scenario = Probe.prefs(this).getString("selection",scenario).orEmpty()
    }
    override fun onResume() {
        super.onResume()
        Probe.record(this,"APP_OPENED ${Probe.power(this)}")
        Probe.prefs(this).registerOnSharedPreferenceChangeListener(listener)
        render()
    }
    override fun onPause() {
        Probe.prefs(this).unregisterOnSharedPreferenceChangeListener(listener)
        handler.removeCallbacksAndMessages(null)
        Probe.record(this,"APP_LEFT_FOREGROUND (does not distinguish Home from swipe)")
        super.onPause()
    }
    private fun text(value: String, size: Float=16f) { body.addView(TextView(this).apply { text=value; textSize=size; setTextColor(Color.rgb(20,45,55)); setPadding(0,10,0,10) }) }
    private fun button(label: String, action: () -> Unit) { body.addView(Button(this).apply { text=label; isAllCaps=false; setOnClickListener { action() } }) }
    private fun settings(intent: Intent) {
        try { startActivity(intent) } catch (_: RuntimeException) { Toast.makeText(this,"Open this app's settings manually; this screen is unavailable on your phone.",Toast.LENGTH_LONG).show() }
    }
    private fun start(minutes: Int) {
        if (!Probe.allowed(this)) { Toast.makeText(this,"Enable exact-alarm access first.",Toast.LENGTH_LONG).show(); return }
        Probe.schedule(this,minutes,scenario); render()
    }
    private fun render() {
        if (!::body.isInitialized || isFinishing) return
        body.removeAllViews()
        text("Alarm Delivery Check",25f)
        text("Offline diagnostic • version 1.0\nNo network, account, Termux, or reward claims. This app tests its own alarms; it cannot read another app's private logs or privileged system logs.",14f)
        text("Exact-alarm capability: ${Probe.allowed(this)}\n${Probe.power(this)}",14f)
        button("Exact-alarm settings") {
            if (Build.VERSION.SDK_INT >= 31) settings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")))
            else Toast.makeText(this,"No separate permission required on this Android version.",Toast.LENGTH_SHORT).show()
        }
        button("Battery optimization list") { settings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        text("For comparison with Interlink Scheduler, exempt Alarm Delivery Check from battery optimization too. Capability may come from exemption even if the separate alarm toggle is off. Manufacturer Autostart controls are not checked.",14f)
        button("Test condition: $scenario") {
            val choices = arrayOf("Home + screen locked","Swiped from Recents + screen locked","App left open")
            AlertDialog.Builder(this).setTitle("Select what you will do after scheduling").setItems(choices) { _, which ->
                scenario=choices[which]; Probe.prefs(this).edit().putString("selection",scenario).apply(); render()
            }.show()
        }
        button("Schedule test in 2 minutes") { start(2) }
        button("Schedule test in 10 minutes") { start(10) }
        button("Cancel pending test") { Probe.cancel(this); render() }
        val at=Probe.prefs(this).getLong("expected",0)
        if (at>0) text("Expected: ${Probe.format(at)}",18f)
        text(Timing.status(at,Probe.prefs(this).getLong("delivered",0),System.currentTimeMillis()),18f)
        text("After scheduling, perform the selected action. Reopen one minute after the expected time. Compare RECEIVER_DELIVERED with APP_OPENED: delivery near reopening is not proof of unattended success. Opening never repairs or replays a test. Do not force-stop. Wait at least 10 minutes between tests to reduce idle-alarm throttling; a short test is not an overnight guarantee.",14f)
        button("Refresh results (does not trigger alarm)") { render() }
        button("Copy diagnostic report") {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Alarm diagnostic",Probe.report(this)))
            Toast.makeText(this,"Report copied; paste it into this conversation.",Toast.LENGTH_LONG).show()
        }
        text("Recent events",20f)
        text(Probe.prefs(this).getString("history","").orEmpty().lines().takeLast(30).reversed().joinToString("\n\n"),13f)
    }
}
