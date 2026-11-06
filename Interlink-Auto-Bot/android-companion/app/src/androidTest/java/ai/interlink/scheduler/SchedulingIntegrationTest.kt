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
