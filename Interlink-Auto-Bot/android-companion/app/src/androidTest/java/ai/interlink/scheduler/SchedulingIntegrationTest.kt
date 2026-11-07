package ai.interlink.scheduler

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Emulator-only fixtures. No Termux command is dispatched and no API is called. */
@RunWith(AndroidJUnit4::class)
class SchedulingIntegrationTest {
    private val c get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup() {
        c.stopService(Intent(c,RunService::class.java))
        Schedule.disable(c)
        Schedule.prefs(c).edit().clear().putBoolean("enabled",true).commit()
        assertTrue("Grant SCHEDULE_EXACT_ALARM app-op in the emulator first",Schedule.allowed(c))
    }
    @After fun cleanup() { Schedule.disable(c); Schedule.prefs(c).edit().clear().commit() }
    @Test fun wakeLockCycleUsesRealIntervalAndKeepsScreenOffTimerAlive() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("testWakeCycle") == "true")
        val start=System.currentTimeMillis()
        fun observed(name:String)=Diagnostics.report(c).lines().filter { it.startsWith("{") }.map { org.json.JSONObject(it) }
            .filter { it.optString("event")==name && it.optLong("actualAt")>=start }
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command:String)=automation.executeShellCommand(command).use { fd -> java.io.FileInputStream(fd.fileDescriptor).bufferedReader().readText() }
        Schedule.recover(c,"fixture")
        Schedule.prefs(c).edit().putBoolean("continuousMonitor",true).putBoolean("wakeLockCycling",true).commit()
        PersistentMonitorService.start(c,"real_interval_cycle_test")
        try {
            val ready=SystemClock.elapsedRealtime()+5000
            while(observed("MONITOR_TIMER_PLANNED").isEmpty() && SystemClock.elapsedRealtime()<ready) Thread.sleep(50)
            assertTrue(PersistentMonitorService.active)
            PersistentMonitorService.scheduleProbe(c,125000)
            shell("input keyevent 223")
            val end=SystemClock.elapsedRealtime()+140000
            while(observed("MONITOR_TEST_TIMER_FIRED").isEmpty() && SystemClock.elapsedRealtime()<end) Thread.sleep(200)
            val cycle=observed("WAKE_LOCK_CYCLED").lastOrNull()
            assertNotNull("No real 120-second cycle observed",cycle)
            assertTrue(cycle!!.getJSONObject("details").getLong("heldForMs")>=120000)
            val fired=observed("MONITOR_TEST_TIMER_FIRED").lastOrNull()
            assertNotNull("Probe failed after wake lock cycling",fired)
            assertFalse(fired!!.getJSONObject("device").getBoolean("interactive"))
            assertFalse(Schedule.prefs(c).contains("activeId"))
        } finally {
            PersistentMonitorService.stop(c)
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
        }
    }
    @Test fun continuousMonitorSurvivesRecentsSwipeAndScreenOffTimer() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("testRecents") == "true")
        val started=System.currentTimeMillis()
        fun observed(name:String)=Diagnostics.report(c).lines().filter { it.startsWith("{") }.map { org.json.JSONObject(it) }
            .any { it.optString("event")==name && it.optLong("actualAt")>=started }
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo=automation.serviceInfo.apply { flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS }
        fun shell(command:String)=automation.executeShellCommand(command).use { fd -> java.io.FileInputStream(fd.fileDescriptor).bufferedReader().readText() }
        Schedule.recover(c,"fixture")
        shell("input keyevent 224")
        shell("wm dismiss-keyguard")
        shell("am start -W -n ai.interlink.scheduler/.MainActivity")
        Thread.sleep(800)
        Schedule.prefs(c).edit().putBoolean("continuousMonitor",true).commit()
        PersistentMonitorService.start(c,"recents_test")
        try {
            val end=SystemClock.elapsedRealtime()+5000
            while(!observed("MONITOR_TIMER_PLANNED") && SystemClock.elapsedRealtime()<end) Thread.sleep(50)
            assertTrue(PersistentMonitorService.active)
            PersistentMonitorService.scheduleProbe(c,15000)
            shell("input keyevent 3")
            shell("input keyevent 187")
            Thread.sleep(1000)
            fun findCard(node:android.view.accessibility.AccessibilityNodeInfo?):android.graphics.Rect? {
                if(node==null) return null
                val bounds=android.graphics.Rect().also { node.getBoundsInScreen(it) }
                if(node.contentDescription?.toString()=="Interlink Scheduler" ||
                    (node.viewIdResourceName=="com.android.launcher3:id/task" && bounds.width()>c.resources.displayMetrics.widthPixels/2)) return bounds
                for(n in 0 until node.childCount) findCard(node.getChild(n))?.let { return it }
                return null
            }
            var card:android.graphics.Rect?=null
            val cardEnd=SystemClock.elapsedRealtime()+5000
            while(card==null && SystemClock.elapsedRealtime()<cardEnd) {
                card=findCard(automation.rootInActiveWindow)
                if(card==null) Thread.sleep(100)
            }
            if(card==null) {
                automation.takeScreenshot()?.let { bitmap -> java.io.File(c.filesDir,"recents-test.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } }
            }
            assertNotNull("Scheduler card not visible in Recents; root=${automation.rootInActiveWindow?.packageName}",card)
            shell("input swipe ${card!!.centerX()} ${card.centerY()} ${card.centerX()} 0 250")
            val removedEnd=SystemClock.elapsedRealtime()+4000
            while(!observed("MONITOR_TASK_REMOVED") && SystemClock.elapsedRealtime()<removedEnd) Thread.sleep(50)
            assertTrue("Actual Recents swipe did not produce task-removal evidence",observed("MONITOR_TASK_REMOVED"))
            shell("input keyevent 223")
            val fireEnd=SystemClock.elapsedRealtime()+20000
            while(!observed("MONITOR_TEST_TIMER_FIRED") && SystemClock.elapsedRealtime()<fireEnd) Thread.sleep(50)
            assertTrue("Foreground service stopped after removing Recents task",PersistentMonitorService.active)
            assertTrue("Locked timer did not fire after Recents removal",observed("MONITOR_TEST_TIMER_FIRED"))
            val evidence=Diagnostics.report(c).lines().filter { it.startsWith("{") }.map { org.json.JSONObject(it) }
                .last { it.optString("event")=="MONITOR_TEST_TIMER_FIRED" && it.optLong("actualAt")>=started }
            assertFalse("Screen must still be off when the timer fires",evidence.getJSONObject("device").getBoolean("interactive"))
            // This AVD has no device credential; screen-off is asserted, credential lock is physical-device validation.
            println("Screen-off Recents timer evidence: $evidence")
            assertFalse(Schedule.prefs(c).contains("activeId"))
        } finally {
            PersistentMonitorService.stop(c)
            shell("input keyevent 224")
        }
    }
    @Test fun continuousMonitorStartsWithWakeLockAndStopsCleanly() {
        Schedule.recover(c,"fixture")
        val scheduled=ExecutionPolicy.hours.map { Schedule.prefs(c).getLong("alarm:$it",0) }
        Schedule.prefs(c).edit().putBoolean("continuousMonitor",true).commit()
        PersistentMonitorService.start(c,"instrumentation")
        val deadline=SystemClock.elapsedRealtime()+5000
        while(!PersistentMonitorService.active && SystemClock.elapsedRealtime()<deadline) Thread.sleep(50)
        assertTrue("Foreground monitor did not start",PersistentMonitorService.active)
        while(!Diagnostics.report(c).contains("MONITOR_TIMER_PLANNED") && SystemClock.elapsedRealtime()<deadline) Thread.sleep(50)
        val report=Diagnostics.report(c)
        assertTrue(report.contains("MONITOR_WAKE_LOCK_ACQUIRED"))
        assertTrue(report.contains("MONITOR_TIMER_PLANNED"))
        assertEquals(scheduled,ExecutionPolicy.hours.map { Schedule.prefs(c).getLong("alarm:$it",0) })
        PersistentMonitorService.scheduleProbe(c,2000)
        val probeDeadline=SystemClock.elapsedRealtime()+8000
        while(!Diagnostics.report(c).contains("MONITOR_TEST_TIMER_FIRED") && SystemClock.elapsedRealtime()<probeDeadline) Thread.sleep(50)
        assertTrue("Continuous test timer did not fire",Diagnostics.report(c).contains("MONITOR_TEST_TIMER_FIRED"))
        assertFalse("No claim service should start for a diagnostic timer",Schedule.prefs(c).contains("activeId"))
        PersistentMonitorService.stop(c)
        val stopDeadline=SystemClock.elapsedRealtime()+5000
        while(PersistentMonitorService.active && SystemClock.elapsedRealtime()<stopDeadline) Thread.sleep(50)
        assertFalse(PersistentMonitorService.active)
        assertFalse(PersistentMonitorService.desired(c))
    }
    @Test fun alarmClockProbeDuringForcedIdle() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("testDoze") == "true")
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command:String)=automation.executeShellCommand(command).use { fd -> java.io.FileInputStream(fd.fileDescriptor).bufferedReader().readText() }
        try {
            shell("dumpsys battery unplug")
            shell("input keyevent 223")
            val output=shell("dumpsys deviceidle force-idle")
            assertTrue(output,output.contains("idle",ignoreCase=true))
            alarmClockProbeDeliversThroughReceiverWithoutOpeningUi()
        } finally {
            shell("dumpsys deviceidle unforce")
            shell("dumpsys battery reset")
            shell("input keyevent 224")
        }
    }
    @Test fun alarmClockProbeRegistersAndCancelsWithoutChangingDailySlots() {
        Schedule.recover(c,"fixture")
        val before=ExecutionPolicy.hours.map { Schedule.prefs(c).getLong("alarm:$it",0) }
        BackgroundTest.schedule(c,600000,true)
        val at=Schedule.prefs(c).getLong("probeAt",0)
        assertEquals(at,Schedule.manager(c).nextAlarmClock!!.triggerTime)
        assertEquals(before,ExecutionPolicy.hours.map { Schedule.prefs(c).getLong("alarm:$it",0) })
        BackgroundTest.cancel(c)
        assertTrue(Schedule.manager(c).nextAlarmClock?.triggerTime != at)
        assertEquals(0L,Schedule.prefs(c).getLong("probeAt",0))
    }
    @Test fun alarmClockProbeDeliversThroughReceiverWithoutOpeningUi() {
        // Keep execution busy: verify alarm delivery without launching Termux or claims.
        Schedule.prefs(c).edit().putLong("activeUntil",System.currentTimeMillis()+60000).commit()
        BackgroundTest.schedule(c,3000,true)
        val at=Schedule.prefs(c).getLong("probeAt",0)
        val end=SystemClock.elapsedRealtime()+20000
        while(Schedule.prefs(c).getLong("probeAt",0)==at && SystemClock.elapsedRealtime()<end) Thread.sleep(100)
        assertEquals(0L,Schedule.prefs(c).getLong("probeAt",0))
        val lines=Diagnostics.report(c).lines().filter { it.startsWith("{") }.map { org.json.JSONObject(it) }
        assertTrue(lines.any { it.optString("event")=="ALARM_RECEIVED" && it.optLong("scheduledAt")==at && it.optString("reason")=="alarm_clock_probe" })
        assertTrue(Schedule.prefs(c).getString("probeStatus","")!!.contains("busy"))
    }
    @Test fun recoveryArmsAllSixAndDisableClearsThem() {
        Schedule.recover(c,"integration fixture")
        val p=Schedule.prefs(c)
        assertEquals(6,ExecutionPolicy.hours.map { p.getLong("alarm:$it",0) }.filter { it > System.currentTimeMillis() }.toSet().size)
        Schedule.disable(c)
        assertTrue(ExecutionPolicy.hours.all { p.getLong("alarm:$it",0)==0L })
    }
    @Test fun lateAndDuplicateDeliveryPreserveOtherAlarms() {
        Schedule.recover(c,"fixture")
        val p=Schedule.prefs(c)
        val other=p.getLong("alarm:5",0)
        val old=System.currentTimeMillis()-600000
        p.edit().putLong("alarm:1",old).commit()
        val i=Intent(c,AlarmReceiver::class.java).setAction(Schedule.ACTION).putExtra("hour",1).putExtra("scheduled",old)
        AlarmReceiver().onReceive(c,i)
        assertEquals(other,p.getLong("alarm:5",0))
        assertEquals(old,p.getLong("delivered:1",0))
        assertFalse(p.contains("activeId"))
        AlarmReceiver().onReceive(c,i)
        assertTrue(p.getString("events","")!!.contains("Duplicate or replaced alarm"))
    }
    @Test fun oldCallbackDoesNotStopOrOverwriteNewManualAction() {
        val p=Schedule.prefs(c)
        p.edit().putString("activeId","new").putString("lastManualId","new").putString("manualResult","new running")
            .putString("runMode:old","test").putLong("runTime:old",System.currentTimeMillis()-100000).commit()
        val i=Intent(c,ResultReceiver::class.java).setData(Uri.parse("interlink-result://run/old"))
            .putExtra("result",Bundle().apply { putInt("exitCode",0) })
        ResultReceiver().onReceive(c,i)
        assertEquals("new",p.getString("activeId",null))
        assertEquals("new running",p.getString("manualResult",null))
        assertTrue(p.getString("events","")!!.contains("Connection test passed"))
        assertTrue(p.getString("events","")!!.contains("not a new run"))
    }
    @Test fun overdueRecoveryIsDifferentFromAlarmDelivery() {
        val p=Schedule.prefs(c)
        p.edit().putLong("alarm:5",System.currentTimeMillis()-600000).commit()
        Schedule.checkOnOpen(c)
        val events=p.getString("events","")!!
        assertTrue(events.contains("Overdue alarm detected during app opened"))
        assertFalse(events.contains("Alarm delivered:"))
    }
    @Test fun rebootAndTimezoneReceiversRebuildAllSixSlots() {
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIMEZONE_CHANGED)) {
            RecoveryReceiver().onReceive(c,Intent(action))
            assertTrue(ExecutionPolicy.hours.all { Schedule.prefs(c).getLong("alarm:$it",0)>System.currentTimeMillis() })
        }
    }
    @Test fun androidDeliversPendingIntentAndExpiredPayloadNeverLaunchesTermux() {
        Schedule.recover(c,"fixture")
        val p=Schedule.prefs(c)
        val old=System.currentTimeMillis()-600000
        p.edit().putLong("alarm:1",old).commit()
        Schedule.manager(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+3000,Schedule.pending(c,1,old))
        val until=SystemClock.elapsedRealtime()+30000
        // Delivery is persisted before rearming/logging completes; wait for the receiver's final outcome.
        while((p.getLong("delivered:1",0)!=old || !p.getString("events","")!!.contains("Late/early alarm skipped")) && SystemClock.elapsedRealtime()<until) SystemClock.sleep(100)
        assertEquals("OS alarm was not delivered",old,p.getLong("delivered:1",0))
        assertFalse(p.contains("activeId"))
        assertTrue(p.getString("events","")!!.contains("Late/early alarm skipped"))
    }
    @Test fun foregroundRecoveryPreservesOriginalDeadlineAndDoesNotDispatchTwice() {
        val p = Schedule.prefs(c)
        // Model opening 3m35s after the intended start (the reported 13:29:35 case).
        val expected = System.currentTimeMillis()-215000
        p.edit().putLong("alarm:13",expected).commit()
        val launches = mutableListOf<Intent>()
        Schedule.recover(c,"app opened") { launches.add(it) }
        assertEquals(1,launches.size)
        assertEquals(expected,launches.single().getLongExtra("scheduled",0))
        assertEquals("scheduled",launches.single().getStringExtra("mode"))
        Schedule.recover(c,"app opened") { launches.add(it) }
        assertEquals(1,launches.size)
        assertTrue(p.getString("events","")!!.contains("Foreground recovery accepted"))
        assertFalse(p.getString("events","")!!.contains("Alarm delivered:"))
    }
    @Test fun expiredRecoveryNeverLaunchesAndDoesNotBlockOtherSlots() {
        val p = Schedule.prefs(c)
        p.edit().putLong("alarm:13",System.currentTimeMillis()-480001).commit()
        Schedule.recover(c,"app opened") { fail("Expired run must never launch") }
        assertTrue(ExecutionPolicy.hours.all { p.getLong("alarm:$it",0)>System.currentTimeMillis() })
    }
    @Test fun backgroundProbeIsIndependentAndLateDeliveryDoesNotDispatch() {
        Schedule.recover(c,"fixture")
        val before = ExecutionPolicy.hours.map { Schedule.prefs(c).getLong("alarm:$it",0) }
        BackgroundTest.schedule(c)
        assertEquals(before,ExecutionPolicy.hours.map { Schedule.prefs(c).getLong("alarm:$it",0) })
        val old = System.currentTimeMillis()-120001
        Schedule.prefs(c).edit().putLong("probeAt",old).commit()
        BackgroundTestReceiver().onReceive(c,Intent(c,BackgroundTestReceiver::class.java)
            .setAction(BackgroundTest.ACTION).putExtra("scheduled",old))
        assertFalse(Schedule.prefs(c).contains("activeId"))
        assertTrue(Schedule.prefs(c).getString("probeStatus","")!!.contains("outside"))
        BackgroundTest.cancel(c)
        assertEquals(0L,Schedule.prefs(c).getLong("probeAt",0))
    }
    @Test fun receiverEntryIsVisibleEvenWhenScheduleDisabled() {
        Schedule.prefs(c).edit().putBoolean("enabled",false).commit()
        val at=System.currentTimeMillis()
        AlarmReceiver().onReceive(c,Intent(c,AlarmReceiver::class.java).setAction(Schedule.ACTION).putExtra("hour",1).putExtra("scheduled",at))
        val report=Diagnostics.report(c)
        assertTrue(report.contains("RECEIVER_STARTED"))
        assertTrue(report.contains("schedule_disabled"))
    }
    @Test fun ordinaryRecoveryDoesNotReplaceUpcomingIdentities() {
        Schedule.recover(c,"fixture")
        val before=ExecutionPolicy.hours.map { Schedule.prefs(c).getLong("alarm:$it",0) }
        Schedule.recover(c,"app opened")
        assertEquals(before,ExecutionPolicy.hours.map { Schedule.prefs(c).getLong("alarm:$it",0) })
    }
    @Test fun reportContractRejectsUncorrelatedAndPreservesIndependentWindowReasons() {
        val stdout="INTERLINK_RESULT " + org.json.JSONObject().put("version",1).put("executionId","schedule-123").put("attemptId","attempt")
            .put("scheduledAt",123).put("nodeStarted",true).put("events",org.json.JSONArray())
            .put("windows",org.json.JSONObject().put("1",org.json.JSONObject().put("result","WINDOW_1_SUCCESS").put("reason","accepted_and_last_claim_advanced"))
                .put("2",org.json.JSONObject().put("result","WINDOW_2_NOT_ELIGIBLE").put("reason","server_not_eligible"))).toString()
        val parsed=ExecutionReport.parse(stdout,"schedule-123","attempt",123)!!
        assertEquals(1,parsed.successful); assertTrue(parsed.complete)
        assertTrue(parsed.summary.contains("Window 2: NOT_ELIGIBLE"))
        assertNull(ExecutionReport.parse(stdout,"schedule-123","wrong",123))
        assertNull(ExecutionReport.parse("raw sensitive output","schedule-123","attempt",123))
    }
    @Test fun missingNodeReportNeverBecomesTwoSuccessfulClaims() {
        val p=Schedule.prefs(c); val at=System.currentTimeMillis()
        p.edit().putString("runMode:fixture","scheduled").putLong("runTime:fixture",at).commit()
        ResultReceiver().onReceive(c,Intent(c,ResultReceiver::class.java).setData(Uri.parse("interlink-result://run/fixture"))
            .putExtra("result",Bundle().apply { putInt("exitCode",0) }))
        assertTrue(Diagnostics.dashboard(c).contains("window results UNKNOWN"))
    }
    @Test fun idleAlarmDeliveryWhenRequested() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("testDoze") == "true")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) = automation.executeShellCommand(command).use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).bufferedReader().readText()
        }
        try {
            shell("dumpsys battery unplug")
            shell("input keyevent 223")
            val output = shell("dumpsys deviceidle force-idle")
            assertTrue(output,output.contains("idle",ignoreCase=true))
            androidDeliversPendingIntentAndExpiredPayloadNeverLaunchesTermux()
        } finally {
            shell("dumpsys deviceidle unforce")
            shell("dumpsys battery reset")
            shell("input keyevent 224")
        }
    }

}
