package ai.interlink.scheduler

import android.app.*
import android.content.*
import android.os.PowerManager
import android.provider.Settings
import android.view.*
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmPermissionTest {
    @Test fun deniedExactAccessPreventsRegistrationAndUiRequestsCorrectNativePage() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("permissionDenied")=="true")
        val inst=InstrumentationRegistry.getInstrumentation(); val c=inst.targetContext
        assertFalse(Schedule.allowed(c))
        Schedule.prefs(c).edit().clear().commit()
        assertFalse(Schedule.enable(c)); assertFalse(RunService.running)
        assertTrue(ExecutionPolicy.hours.none { Schedule.prefs(c).getBoolean("armed:$it",false) })
        val monitor=inst.addMonitor(IntentFilter(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply { addDataScheme("package") },Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true)
        var activity:Activity?=null
        try {
            activity=inst.startActivitySync(Intent(c,MainActivity::class.java).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun find(v:View):Button? {
                if(v is Button && v.text.toString()=="Alarms & reminders access") return v
                if(v is ViewGroup) for(n in 0 until v.childCount) find(v.getChildAt(n))?.let { return it }
                return null
            }
            inst.runOnMainSync { find(activity!!.window.decorView)!!.performClick() }
            assertEquals(1,monitor.hits); assertFalse(Schedule.allowed(c))
        } finally { inst.removeMonitor(monitor); activity?.let { screen -> inst.runOnMainSync { screen.finish() } }; Schedule.disable(c) }
    }
    @Test fun unavailableOemScreensFallBackToOwnAppInfo() {
        val inst=InstrumentationRegistry.getInstrumentation(); val c=inst.targetContext
        // This AVD has no vivo packages; verify the production fallback without granting special access.
        assumeTrue(!SetupAccess.vivo())
        Schedule.prefs(c).edit().putBoolean("enabled",false).commit()
        val monitor=inst.addMonitor(IntentFilter(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { addDataScheme("package") },Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true)
        var activity:Activity?=null
        try {
            activity=inst.startActivitySync(Intent(c,MainActivity::class.java).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            inst.runOnMainSync { SetupAccess.oem(activity!!,"autostart"); SetupAccess.oem(activity!!,"background_power") }
            assertEquals(2,monitor.hits)
        } finally { inst.removeMonitor(monitor); activity?.let { screen -> inst.runOnMainSync { screen.finish() } } }
    }
}
