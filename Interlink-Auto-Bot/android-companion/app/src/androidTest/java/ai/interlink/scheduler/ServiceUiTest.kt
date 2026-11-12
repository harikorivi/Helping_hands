package ai.interlink.scheduler

import android.content.*
import android.app.ActivityManager
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
    @Test fun actualAlarmSurvivesRecentsRemovalStartsBoundedServiceAndReturnsToIdle() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("serviceUi")=="true")
        val started=System.currentTimeMillis(); val inst=InstrumentationRegistry.getInstrumentation(); val c=inst.targetContext; val ui=inst.uiAutomation
        val forced=InstrumentationRegistry.getArguments().getString("forcedIdle")=="true"
        fun shell(command:String)=ui.executeShellCommand(command).use { java.io.FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() } }
        fun await(check:()->Boolean) { val end=SystemClock.elapsedRealtime()+25000; while(!check() && SystemClock.elapsedRealtime()<end) SystemClock.sleep(100); assertTrue("Condition not reached",check()) }
        RunService.dispatchOverride={ throw AssertionError("Probe must not launch Termux") }
        try {
            c.stopService(Intent(c,RunService::class.java)); Schedule.prefs(c).edit().clear().commit()
            shell("appops set ${c.packageName} SCHEDULE_EXACT_ALARM allow")
            val serviceInfo=ui.serviceInfo
            serviceInfo.flags=serviceInfo.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS; ui.serviceInfo=serviceInfo
            shell("input keyevent 224"); shell("wm dismiss-keyguard")
            assertTrue(shell("am start -W -f 0x10008000 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n ai.interlink.scheduler/.MainActivity").contains("Status: ok")); SystemClock.sleep(800)
            await { c.getSystemService(ActivityManager::class.java).appTasks.any { it.taskInfo.topActivity?.className==MainActivity::class.java.name } }
            Schedule.probe(c,15000)
            assertFalse(RunService.running); assertFalse(RunService.wakeHeld)
            shell("input keyevent 3"); SystemClock.sleep(500); shell("input keyevent 187"); SystemClock.sleep(1000)
            fun find(node:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
                if(node==null) return null
                val b=android.graphics.Rect(); node.getBoundsInScreen(b)
                if(node.viewIdResourceName?.endsWith(":id/task")==true && b.width()>c.resources.displayMetrics.widthPixels/2) return node
                for(n in 0 until node.childCount) find(node.getChild(n))?.let { return it }
                return null
            }
            var task:AccessibilityNodeInfo?=null; await { task=find(ui.rootInActiveWindow); task!=null }
            val bounds=android.graphics.Rect(); task!!.getBoundsInScreen(bounds)
            shell("input swipe ${bounds.centerX()} ${bounds.centerY()} ${bounds.centerX()} 0 250")
            await { c.getSystemService(ActivityManager::class.java).appTasks.isEmpty() }
            shell("input keyevent 223")
            if(forced) { shell("dumpsys battery unplug"); assertTrue(shell("dumpsys deviceidle force-idle").contains("idle",true)) }
            await { Schedule.prefs(c).getString("probeStatus","")!!.startsWith("Alarm and foreground service delivered") }
            await { !RunService.running && !RunService.wakeHeld && !Handoff.held() }
            assertFalse(c.getSystemService(PowerManager::class.java).isInteractive)
            assertNull(Schedule.prefs(c).getString("activeId",null))
            val events=Diagnostics.report(c).lineSequence().filter { it.startsWith("{") }.map { org.json.JSONObject(it) }.filter { it.optLong("actualAt")>=started }.toList()
            val received=events.single { it.optString("event")=="ALARM_RECEIVED" }
            val promoted=events.single { it.optString("event")=="FOREGROUND_PROMOTED" }
            assertEquals(received.getString("attemptId"),promoted.getString("attemptId"))
            assertTrue(events.any { it.optString("event")=="EXECUTION_RESOURCES_RELEASED" && it.getString("attemptId")==received.getString("attemptId") })
            assertFalse(events.any { it.optString("event")=="TERMUX_DISPATCHED" })
        } finally {
            Schedule.disable(c); c.stopService(Intent(c,RunService::class.java)); RunService.dispatchOverride=null
            if(forced) { shell("dumpsys deviceidle unforce"); shell("dumpsys battery reset") }
            shell("input keyevent 224")
        }
    }
}
