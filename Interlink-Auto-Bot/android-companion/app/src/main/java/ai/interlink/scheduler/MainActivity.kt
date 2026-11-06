package ai.interlink.scheduler

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.View
import android.widget.*
import java.time.*
import java.time.format.DateTimeFormatter

/** Personal scheduler setup: every setting names its target app and reports its state. */
class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private val ink = Color.rgb(20, 42, 54)
    private val teal = Color.rgb(21, 94, 117)
    private val commandPermission = "com.termux.permission.RUN_COMMAND"
    private val handler = Handler(Looper.getMainLooper())
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post { if (!isFinishing) render() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun installed(): Boolean = try { packageManager.getApplicationInfo("com.termux", 0); true } catch (_: PackageManager.NameNotFoundException) { false }
    private fun batteryExempt(pkg: String) = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg)
    private fun box(parent: LinearLayout): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(18).toFloat() }
        }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        return card
    }
    private fun text(parent: LinearLayout, value: String, size: Float = 15f, bold: Boolean = false): TextView {
        val view = TextView(this).apply {
            text = value; textSize = size; setTextColor(ink)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(7))
        }
        parent.addView(view); return view
    }
    private fun button(parent: LinearLayout, label: String, primary: Boolean = false, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label; isAllCaps = false; textSize = 15f
            setTextColor(if (primary) Color.WHITE else teal)
            backgroundTintList = android.content.res.ColorStateList.valueOf(if (primary) teal else Color.rgb(231, 244, 247))
            minHeight = dp(48)
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
    }
    private fun explain(title: String, message: String, action: (() -> Unit)? = null) {
        val dialog = AlertDialog.Builder(this).setTitle(title).setMessage(message)
        if (action == null) dialog.setPositiveButton("Got it", null)
        else dialog.setPositiveButton("Open settings") { _, _ -> action() }.setNegativeButton("Cancel", null)
        dialog.show()
    }
    private fun openSettings(intent: Intent) {
        try { startActivity(intent) }
        catch (_: RuntimeException) {
            explain("Open Settings manually", "This phone could not open that settings page. Open Android Settings → Apps → Interlink Scheduler or Termux, as indicated by the button. Phone manufacturers use different menu names.")
        }
    }
    private fun appInfo(pkg: String) = openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
    private fun batterySettings(pkg: String, name: String) {
        explain("Battery • $name", "On the next screen, select Battery or App battery usage, then Unrestricted / No restrictions / Allow background activity. If your phone uses a separate Battery optimization list, return here and tap that list button. Select $name there and choose Don't optimize. Do not select the official Interlink app.") { appInfo(pkg) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(242, 246, 248)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.navigationBarColor = Color.rgb(242, 246, 248)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(20),dp(16),dp(24)) }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(242,246,248))
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
            addView(content)
        })
    }
    override fun onResume() {
        super.onResume()
        Schedule.checkOnOpen(this)
        Schedule.prefs(this).registerOnSharedPreferenceChangeListener(listener)
        render()
    }
    override fun onPause() {
        Schedule.prefs(this).unregisterOnSharedPreferenceChangeListener(listener)
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        render()
        if (requestCode == 1 && !granted(commandPermission)) {
            explain("Command permission still needed", "Open Interlink Scheduler's App info → Permissions → Additional permissions, and allow Run commands in Termux environment. If that option is missing, verify that a supported Termux app is installed. Android may stop showing a prompt after repeated denials.") { appInfo(packageName) }
        }
    }
    private fun startManual(mode: String) {
        if (!installed() || !granted(commandPermission)) {
            explain("Allow Termux commands first", "Complete step 1 below. Exact-alarm permission is not needed for a button-triggered test."); return
        }
        val p = Schedule.prefs(this)
        val now = System.currentTimeMillis()
        val activeUntil = p.getLong("activeUntil",0)
        if (activeUntil > now) {
            explain("A run is active", "Wait for the current run to finish before starting another action."); return
        }
        if (now - p.getLong("lastButtonRun",0) < 5000) return
        p.edit().putLong("lastButtonRun",now).putString("manualResult","Starting Termux…").commit()
        try {
            startForegroundService(Intent(this,RunService::class.java).putExtra("scheduled",now).putExtra("mode",mode))
        } catch (_: RuntimeException) { p.edit().putString("manualResult","Android blocked the launch. Check app permissions and battery settings.").commit() }
        render()
    }
    private fun render() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply { setImageResource(R.drawable.ic_launcher); contentDescription = "Interlink Scheduler clock icon" }, LinearLayout.LayoutParams(dp(52),dp(52)).apply { rightMargin = dp(12) })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text(titles,"Interlink Scheduler",23f,true)
        text(titles,"Your Termux automation companion • v1.5",13f)
        header.addView(titles, LinearLayout.LayoutParams(0,-2,1f)); content.addView(header)
        text(content,"Tap once to open a setting. Return here after changing it; status updates automatically.",14f)
        val p = Schedule.prefs(this)
        val enabled = p.getBoolean("enabled",false)
        val canRun = installed() && granted(commandPermission) && Schedule.allowed(this) && batteryExempt(packageName) && batteryExempt("com.termux")
        val next = p.getLong("next",0)
        val overview = box(content)
        text(overview,if (!enabled) "Schedule is paused" else if (!canRun) "Schedule needs attention" else "Schedule is enabled",21f,true)
        val time = if (enabled && next > 0) Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, d MMM • h:mm a")) else "Not scheduled"
        text(overview,"Next run: $time",17f,true)
        text(overview,"Phone timezone: ${ZoneId.systemDefault().id}\nSix runs daily: 1:26 AM, 5:26 AM, 9:26 AM, 1:26 PM, 5:26 PM, 9:26 PM. Check results after minute 35.",14f)
        text(overview,"An enabled schedule is not proof of a successful claim. See recent activity below.",13f)
        button(overview,if (enabled) "Repair next scheduled run" else "Enable schedule",true) {
            if (!canRun) explain("Finish setup first", "Install Termux, allow commands (step 1), allow exact alarms (step 2), and exempt BOTH Interlink Scheduler and Termux from battery optimization (step 3). These are required before enabling automatic execution. Manufacturer restrictions may still apply.")
            else {
                p.edit().putBoolean("enabled",true).commit(); Schedule.recover(this)
                render(); Toast.makeText(this,"Next run scheduled",Toast.LENGTH_SHORT).show()
            }
        }
        if (enabled) button(overview,"Pause future runs") {
            AlertDialog.Builder(this).setTitle("Pause the schedule?").setMessage("Future runs will stop. An execution already in progress may finish.")
                .setPositiveButton("Pause") { _, _ ->
                    Schedule.disable(this); render()
                }.setNegativeButton("Keep enabled",null).show()
        }
        val manual = box(content)
        text(manual,"Try it now",18f,true)
        text(manual,"Run immediately, even with the schedule paused. Complete the Termux setup first. Testing now does not prove alarms will run with the screen locked.")
        text(manual,p.getString("manualResult","No on-demand action yet.").orEmpty(),15f,true)
        button(manual,"Test connection now • no claim",true) { startManual("test") }
        button(manual,"Claim once now") {
            AlertDialog.Builder(this).setTitle("Submit one eligible claim?")
                .setMessage("This is a real claim, not a test. It checks server eligibility and submits at most once. It may affect eligibility for the next scheduled window. An uncertain submission will not be retried. Continue?")
                .setPositiveButton("Check and claim once") { _, _ -> startManual("claim") }
                .setNegativeButton("Cancel",null).show()
        }
        text(manual,"Manual actions share the scheduled bot's lock. Near a scheduled run, let it finish first. Details: logs/on-demand-YYYY-MM-DD.log in Termux.",13f)
        val commands = box(content)
        text(commands,"1. Allow Termux commands",18f,true)
        text(commands,if (!installed()) "Termux is not installed or not visible." else if (granted(commandPermission)) "Allowed ✓" else "Permission needed",16f,true)
        text(commands,"This permission belongs to Interlink Scheduler. It lets this companion start your bot in Termux. Your Termux properties file must also contain allow-external-apps=true.")
        button(commands,if (granted(commandPermission)) "View Scheduler permissions" else "Allow Termux commands") {
            if (!installed()) explain("Install Termux first", "Install a supported Termux build and open it once, then return here.")
            else if (granted(commandPermission) || p.getBoolean("askedCommands",false)) {
                explain("Interlink Scheduler permissions", "Select Permissions → Additional permissions → Run commands in Termux environment. If it is already allowed, nothing needs changing.") { appInfo(packageName) }
            } else { p.edit().putBoolean("askedCommands",true).apply(); requestPermissions(arrayOf(commandPermission),1) }
        }
        val alarms = box(content)
        text(alarms,"2. Allow scheduled alarms",18f,true)
        text(alarms,if (Schedule.allowed(this)) "Allowed ✓" else "Permission needed",16f,true)
        text(alarms,"Select Interlink Scheduler on the Alarms & reminders screen. This is not a setting for Termux.")
        button(alarms,"Open Scheduler alarm settings") {
            if (Build.VERSION.SDK_INT >= 31) openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")))
            else explain("No alarm permission needed", "This Android version does not have the Alarms & reminders permission screen. Alarm access is already available.")
        }
        val battery = box(content)
        text(battery,"3. Allow both apps in background",18f,true)
        text(battery,"Choose Interlink Scheduler AND Termux. You do not need to change the official Interlink app's battery setting.")
        text(battery,"Interlink Scheduler: ${if (batteryExempt(packageName)) "battery optimization exemption detected ✓" else "optimization exemption not detected"}",15f,true)
        button(battery,"Battery • Interlink Scheduler") { batterySettings(packageName,"Interlink Scheduler") }
        text(battery,"Termux: ${if (!installed()) "not installed" else if (batteryExempt("com.termux")) "battery optimization exemption detected ✓" else "optimization exemption not detected"}",15f,true)
        button(battery,"Battery • Termux") { batterySettings("com.termux","Termux") }
        button(battery,"Open battery optimization list") {
            explain("Choose both apps", "If shown, select All apps. Find Interlink Scheduler and choose Don't optimize. Repeat for Termux. Names vary by phone.") { openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
        text(battery,"Some phones also require Auto-start or Allow background activity. These manufacturer settings cannot be verified here. Do not force-stop either app.",13f)
        val notifications = box(content)
        text(notifications,"4. Show run notifications",18f,true)
        text(notifications,if (getSystemService(NotificationManager::class.java).areNotificationsEnabled()) "Notifications enabled ✓" else "Notifications disabled",16f,true)
        text(notifications,"Allow notifications for Interlink Scheduler. A notification appears while a scheduled run is active.")
        button(notifications,"Open Scheduler notifications") {
            openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
        }
        val events = box(content)
        text(events,"Recent activity",18f,true)
        text(events,"Today and yesterday • up to 100 saved entries",13f)
        val lines = p.getString("events","").orEmpty().lines().filter { it.isNotBlank() }.takeLast(20).reversed()
        if (lines.isEmpty()) text(events,"No executions recorded yet. Wait for the next run shown above.")
        for (line in lines) {
            val rawTime = line.substringBefore(' ')
            val local = try { Instant.parse(rawTime).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, h:mm:ss a")) } catch (_: Exception) { rawTime }
            val raw = line.substringAfter(' ',"")
            val description = when {
                raw.startsWith("DISABLED") -> "Schedule paused. These older entries do not describe the current status."
                raw.startsWith("TERMUX_DISPATCHED") -> "Command sent to Termux. Claim results are not yet confirmed."
                raw.startsWith("TERMUX_COMPLETED exit=0;") -> "Older command completed successfully; its mode was not recorded. See the corresponding bot log."
                raw.startsWith("TERMUX_COMPLETED") -> "Termux command finished with an issue. Check the bot log."
                raw.startsWith("TERMUX_DISPATCH_FAILED") -> "Could not start Termux. Check command permission and background settings."
                raw.startsWith("EXECUTION_DEADLINE") -> "Execution deadline reached. Service stopped; check the bot log."
                raw.startsWith("MISSED_SCHEDULE") -> "A scheduled run was missed. It will not be replayed."
                raw.startsWith("BACKGROUND_START_REJECTED") -> "Android blocked background execution. Review both apps' battery settings."
                else -> raw
            }
            text(events,local,13f,true); text(events,description,14f)
        }
        button(events,"Refresh status") { Schedule.checkOnOpen(this); render() }
        button(events,"Copy diagnostic history") {
            val details = "Interlink Scheduler 1.5\nEnabled: ${p.getBoolean("enabled",false)}\nExact alarm access: ${Schedule.allowed(this)}\nBattery exemptions: Scheduler=${batteryExempt(packageName)}, Termux=${batteryExempt("com.termux")}\n" +
                ExecutionPolicy.hours.joinToString("\n") { hour -> val at = p.getLong("alarm:$hour",0); "Hour $hour: ${if (at > 0) Schedule.time(at) else "not registered"}" } +
                "\nActivity (UTC timestamps):\n" + p.getString("events","")
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Scheduler diagnostics",details))
            Toast.makeText(this,"Diagnostic history copied; no tokens included",Toast.LENGTH_SHORT).show()
        }
        text(content,"Personal companion, not the official Interlink app. This screen reports Android scheduling and command delivery; claim outcomes are stored in the bot's private logs.",13f)
    }
}
