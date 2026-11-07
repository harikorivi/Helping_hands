package ai.interlink.scheduler

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.View
import android.widget.*
import java.util.concurrent.Executors

/** The service is the scheduler; closing this activity does not pause it. */
class MainActivity:Activity() {
    private lateinit var body:LinearLayout
    private lateinit var status:TextView
    private lateinit var evidence:TextView
    private val exports=Executors.newSingleThreadExecutor()
    private var pendingExport:String?=null
    private var generation=0
    private val handler=Handler(Looper.getMainLooper())
    private val refresh=object:Runnable { override fun run() { refreshStatus(); handler.postDelayed(this,5000) } }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun text(value:String,size:Float=16f):TextView = TextView(this).apply {
        text=value; textSize=size; setTextColor(Color.rgb(28,49,57)); setPadding(0,dp(8),0,dp(8)); body.addView(this)
    }
    private fun button(label:String,action:()->Unit) {
        body.addView(Button(this).apply { text=label; isAllCaps=false; setOnClickListener { action() } },LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
    }
    private fun notice(message:String)=AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK",null).show()
    private fun launch(intent:Intent) { try { startActivity(intent) } catch(_:RuntimeException) { notice("This phone does not expose that settings page. Open Settings → Apps and select the named app.") } }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        @Suppress("DEPRECATION")
        val lightBars=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.systemUiVisibility=lightBars
        body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(20),dp(20),dp(30)) }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(241,247,249)); addView(body)
            setOnApplyWindowInsetsListener { view,insets ->
                @Suppress("DEPRECATION")
                view.setPadding(0,insets.systemWindowInsetTop,0,insets.systemWindowInsetBottom)
                insets
            }
        })
        text("Interlink Scheduler 2.0-rc1",25f)
        text("Continuous background service",20f)
        text("Runs daily at 1:26, 5:26, 9:26 AM and PM in this phone’s timezone. Each run checks two separate claim windows. Keep the service notification visible; Node runs only during an execution.")
        status=text("Loading service status…")
        button("Start / repair 24-hour scheduling") {
            if(ready()) { Schedule.enable(this); refreshStatus() }
        }
        button("Pause scheduled runs") {
            AlertDialog.Builder(this).setMessage("Pause future runs? An active Termux execution may finish within its original deadline.")
                .setPositiveButton("Pause") { _,_ -> Schedule.disable(this); refreshStatus() }.setNegativeButton("Cancel",null).show()
        }
        text("Run now",20f)
        button("Test connection — no claim") { manual("test") }
        button("Submit one eligible claim now") {
            AlertDialog.Builder(this).setMessage("Submit one real claim only if the server reports eligibility? This does not replace either scheduled claim window.")
                .setPositiveButton("Submit eligible claim") { _,_ -> manual("claim") }.setNegativeButton("Cancel",null).show()
        }
        text("Test service survival",20f)
        text("Start scheduling first. Choose a test, swipe this app from Recents, and lock the screen. The service should record its timer event without reopening the app. These timers make no API requests or claims; normal scheduled runs remain enabled.")
        for(minutes in listOf(2,10)) button("$minutes-minute service timer test — no claim") {
            if(!Schedule.enabled(this)) notice("Start 24-hour scheduling first.")
            else safely { RunService.probe(this,minutes*60000L); Toast.makeText(this,"Timer requested. You can close this screen and lock the phone.",Toast.LENGTH_LONG).show() }
        }
        button("Send this screen to background") { moveTaskToBack(true) }
        text("Setup",20f)
        button("Grant Termux command permission") { commandPermission() }
        button("Allow scheduler notifications") {
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),102)
            else launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
        }
        button("Scheduler app / battery settings") { appSettings(packageName) }
        button("Termux app / battery settings") { appSettings("com.termux") }
        button("Open battery optimization list") { launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        text("Allow background power use for both Interlink Scheduler and Termux. Exact-alarm access is not used in this version. A continuous CPU wake lock increases battery use. Android/OEM force-stop can still terminate the service; no app can promise recovery from that state.")
        text("Diagnostics",20f)
        body.addView(Switch(this).apply {
            text="Live screen, unlock and power-state logging"
            isChecked=Diagnostics.liveDebug(this@MainActivity)
            setOnCheckedChangeListener { _,on -> Schedule.prefs(this@MainActivity).edit().putBoolean("liveDebug",on).apply(); Diagnostics.emit(this@MainActivity,"LIVE_DEBUG_CHANGED",reason=on.toString()) }
        })
        button("Record current system state") { Diagnostics.emit(this,"MANUAL_STATE_SNAPSHOT"); refreshStatus() }
        button("Save diagnostic log to Downloads") { export() }
        button("Refresh status") { refreshStatus() }
        evidence=text("Loading diagnostic evidence…")
        text("Personal companion, not the official Interlink app. Termux files and tokens stay private. A command dispatch is not proof of a claim. Each claim result must be confirmed by the Node report.",14f)
    }
    private fun appSettings(name:String)=launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$name")))
    private fun commandPermission() {
        if(checkSelfPermission("com.termux.permission.RUN_COMMAND")==PackageManager.PERMISSION_GRANTED) notice("Termux command permission is already granted.")
        else requestPermissions(arrayOf("com.termux.permission.RUN_COMMAND"),101)
    }
    private fun ready():Boolean {
        try { packageManager.getPackageInfo("com.termux",0) }
        catch(_:PackageManager.NameNotFoundException) { notice("Install Termux first and complete the README-mobile.md setup."); return false }
        if(checkSelfPermission("com.termux.permission.RUN_COMMAND")!=PackageManager.PERMISSION_GRANTED) { commandPermission(); return false }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),102); return false
        }
        return true
    }
    private fun safely(action:()->Unit) { try { action() } catch(e:RuntimeException) { Diagnostics.emit(this,"USER_ACTION_FAILED",reason=e.javaClass.simpleName); notice("Could not start: ${e.javaClass.simpleName}. Export diagnostics for details.") } }
    private fun manual(mode:String) { if(ready()) safely { RunService.manual(this,mode); refreshStatus() } }
    private fun refreshStatus() {
        val current=++generation
        Diagnostics.work {
            val p=Schedule.prefs(this)
            val enabled=Schedule.enabled(this)
            val next=p.getLong("next",0); val heartbeat=p.getLong("serviceHeartbeat",0)
            val power=getSystemService(PowerManager::class.java)
            val state="Schedule: ${if(enabled) "enabled" else "paused"}\nService: ${if(RunService.running) "running" else "not running"}; CPU wake lock: ${RunService.wakeHeld}\n"+
                "Last heartbeat: ${if(heartbeat>0) Schedule.time(heartbeat) else "none"}\nNext: ${if(next>0) Schedule.time(next) else "none"}\n"+
                "Android battery exemptions: Scheduler=${power.isIgnoringBatteryOptimizations(packageName)}, Termux=${power.isIgnoringBatteryOptimizations("com.termux")}\nBattery saver: ${power.isPowerSaveMode}\n"+
                p.getString("serviceError","").orEmpty()+"\n"+p.getString("manualResult","").orEmpty()+"\n"+p.getString("probeStatus","").orEmpty()
            val recent=LogRetention.retain(p.getString("events","").orEmpty()).reversed().joinToString("\n\n")
            val details=ExecutionPolicy.hours.joinToString("\n") { h -> val at=p.getLong("slot:$h",0); "Hour $h: ${if(at>0) Schedule.time(at) else "not planned"}" }+
                "\n\n"+Diagnostics.dashboard(this)+"\n\nRecent activity — today and yesterday:\n"+recent
            runOnUiThread { if(!isDestroyed && current==generation) { status.text=state; evidence.text=details } }
        }
    }
    private fun export() {
        Toast.makeText(this,"Preparing private diagnostic export…",Toast.LENGTH_SHORT).show()
        exports.execute {
            try {
                val report=DiagnosticExport.snapshot(this)
                if(Build.VERSION.SDK_INT>=29) {
                    val name=DiagnosticExport.fileName()
                    DiagnosticExport.saveToDownloads(this,name,report)
                    runOnUiThread { if(!isDestroyed) notice("Saved Downloads/Interlink Scheduler/$name") }
                } else runOnUiThread {
                    pendingExport=report
                    launchExportPicker()
                }
            } catch(e:Exception) { runOnUiThread { if(!isDestroyed) notice("Export failed: ${e.javaClass.simpleName}") } }
        }
    }
    private fun launchExportPicker() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,DiagnosticExport.fileName()),103)
    }
    @Deprecated("Platform activity callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==103) {
            val report=pendingExport; pendingExport=null
            val uri=data?.data
            if(resultCode==RESULT_OK && report!=null && uri!=null) exports.execute {
                try { DiagnosticExport.write(this,uri,report); runOnUiThread { if(!isDestroyed) notice("Diagnostic log saved.") } }
                catch(e:Exception) { runOnUiThread { if(!isDestroyed) notice("Export failed: ${e.javaClass.simpleName}") } }
            }
        }
    }
    override fun onResume() { super.onResume(); Schedule.checkOnOpen(this); Diagnostics.debug(this,"APP_FOREGROUND"); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); Diagnostics.debug(this,"APP_BACKGROUND"); super.onPause() }
    override fun onDestroy() { handler.removeCallbacks(refresh); exports.shutdown(); super.onDestroy() }
}
