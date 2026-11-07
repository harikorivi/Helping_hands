package ai.interlink.scheduler

import android.content.*
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.*
import android.view.accessibility.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceUiTest {
    @Test fun realRecentsSwipeAndScreenOffKeepServiceTimerRunning() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("serviceUi")=="true")
        val started=System.currentTimeMillis()
        val inst=InstrumentationRegistry.getInstrumentation(); val c=inst.targetContext
        val ui=inst.uiAutomation
        fun shell(command:String)=ui.executeShellCommand(command).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() } }
        fun await(check:()->Boolean) { val end=SystemClock.elapsedRealtime()+10000; while(!check() && SystemClock.elapsedRealtime()<end) SystemClock.sleep(100); assertTrue(check()) }
        RunService.dispatchOverride={ throw AssertionError("No Termux dispatch allowed in timer-only test") }
        try {
            c.stopService(Intent(c,RunService::class.java)); await { !RunService.running }
            Schedule.prefs(c).edit().clear().commit()
            val serviceInfo=ui.serviceInfo
            serviceInfo.flags=serviceInfo.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            ui.serviceInfo=serviceInfo
            shell("input keyevent 224"); shell("wm dismiss-keyguard")
            shell("am start -W -n ai.interlink.scheduler/.MainActivity")
            SystemClock.sleep(800)
            Schedule.enable(c); await { RunService.wakeHeld }
            RunService.probe(c,12000); await { Schedule.prefs(c).getLong("serviceProbeAt",0)>0 }
            shell("input keyevent 3"); shell("input keyevent 187"); SystemClock.sleep(1000)
            fun findTask(node:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
                if(node==null) return null
                val bounds=android.graphics.Rect(); node.getBoundsInScreen(bounds)
                if(node.viewIdResourceName?.endsWith(":id/task")==true && bounds.width()>c.resources.displayMetrics.widthPixels/2) return node
                for(index in 0 until node.childCount) findTask(node.getChild(index))?.let { return it }
                return null
            }
            var card:AccessibilityNodeInfo?=null
            await { card=findTask(ui.rootInActiveWindow); card!=null }
            val rect=android.graphics.Rect(); card!!.getBoundsInScreen(rect)
            shell("input swipe ${rect.centerX()} ${rect.centerY()} ${rect.centerX()} 0 250")
            await { Diagnostics.report(c).lineSequence().filter { it.startsWith("{") }.map { org.json.JSONObject(it) }
                .any { it.optString("event")=="SERVICE_TASK_REMOVED" && it.optLong("actualAt")>=started } }
            shell("input keyevent 223")
            val end=SystemClock.elapsedRealtime()+20000
            while(!Schedule.prefs(c).getString("probeStatus","")!!.startsWith("Service timer fired") && SystemClock.elapsedRealtime()<end) SystemClock.sleep(150)
            assertTrue(Schedule.prefs(c).getString("probeStatus","")!!.startsWith("Service timer fired"))
            assertFalse(c.getSystemService(PowerManager::class.java).isInteractive)
            assertTrue(RunService.running); assertTrue(RunService.wakeHeld)
            assertNull(Schedule.prefs(c).getString("activeId",null))
        } finally {
            Schedule.disable(c); c.stopService(Intent(c,RunService::class.java)); shell("input keyevent 224")
            RunService.dispatchOverride=null
        }
    }
}
