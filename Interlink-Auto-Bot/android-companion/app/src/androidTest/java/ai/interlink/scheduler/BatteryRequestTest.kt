package ai.interlink.scheduler

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatteryRequestTest {
    @Test fun buttonRequestsNativeDialogWithoutPretendingPermissionWasGranted() {
        val inst=InstrumentationRegistry.getInstrumentation(); val c=inst.targetContext
        val power=c.getSystemService(PowerManager::class.java)
        assumeFalse(power.isIgnoringBatteryOptimizations(c.packageName))
        Schedule.prefs(c).edit().putBoolean("enabled",false).commit()
        c.stopService(Intent(c,RunService::class.java))
        val monitor=inst.addMonitor(IntentFilter(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { addDataScheme("package") },Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true)
        var activity:Activity?=null
        try {
            activity=inst.startActivitySync(Intent(c,MainActivity::class.java).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun find(view:View):Button? {
                if(view is Button && view.text.toString()=="Request Scheduler battery exemption") return view
                if(view is ViewGroup) for(n in 0 until view.childCount) find(view.getChildAt(n))?.let { return it }
                return null
            }
            inst.runOnMainSync { assertNotNull(find(activity!!.window.decorView)); find(activity!!.window.decorView)!!.performClick() }
            inst.waitForIdleSync()
            assertEquals(1,monitor.hits)
            assertFalse("A request alone must not be reported as a granted exemption",power.isIgnoringBatteryOptimizations(c.packageName))
        } finally {
            inst.removeMonitor(monitor)
            activity?.let { screen -> inst.runOnMainSync { screen.finish() } }
        }
    }
}
