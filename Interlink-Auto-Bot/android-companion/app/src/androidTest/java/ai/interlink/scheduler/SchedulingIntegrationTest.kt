package ai.interlink.scheduler

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.*

/** All Termux dispatches are intercepted: no authentication, network calls or real claims. */
@RunWith(AndroidJUnit4::class)
class SchedulingIntegrationTest {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private val c get()=instrument.targetContext
    private val commands=LinkedBlockingQueue<Intent>()
    private fun waitUntil(test:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+12000
        while(!test() && SystemClock.elapsedRealtime()<end) SystemClock.sleep(30)
        assertTrue("Condition not reached",test())
    }
    @Before fun setup() {
        c.stopService(Intent(c,RunService::class.java))
        waitUntil { !RunService.running }
        Schedule.prefs(c).edit().clear().commit()
        RunService.dispatchOverride={ command -> commands.offer(command); ComponentName("com.termux","FakeCommand") }
    }
    @After fun cleanup() {
        Schedule.prefs(c).edit().putBoolean("enabled",false).commit()
        c.stopService(Intent(c,RunService::class.java))
        waitUntil { !RunService.running && !RunService.wakeHeld }
        RunService.dispatchOverride=null
    }
    private fun start() {
        Schedule.enable(c)
        waitUntil { RunService.running && RunService.wakeHeld && Schedule.prefs(c).getLong("next",0)>0 }
    }
    private fun complete(id:String,exit:Int=0) {
        ResultReceiver().onReceive(c,Intent().setData(Uri.parse("interlink-result://run/$id"))
            .putExtra("result",Bundle().apply { putInt("exitCode",exit) }))
    }
    @Test fun singleSpecialUseServiceAndNoExactAlarmPermission() {
        val info=c.packageManager.getPackageInfo(c.packageName,android.content.pm.PackageManager.GET_SERVICES or android.content.pm.PackageManager.GET_PERMISSIONS)
        assertEquals(1,info.services!!.size)
        assertEquals("ai.interlink.scheduler.RunService",info.services!!.single().name)
        if(Build.VERSION.SDK_INT>=34) assertEquals(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,info.services!!.single().foregroundServiceType)
        assertFalse(info.services!!.single().flags and android.content.pm.ServiceInfo.FLAG_STOP_WITH_TASK !=0)
        assertFalse(info.requestedPermissions!!.contains("android.permission.SCHEDULE_EXACT_ALARM"))
        start()
        assertEquals(6,ExecutionPolicy.hours.count { Schedule.prefs(c).getLong("slot:$it",0)>0 })
        assertEquals(0,commands.size)
    }
    @Test fun foregroundPromotionDoesNotWaitForDiagnosticsWorker() {
        val entered=CountDownLatch(1); val release=CountDownLatch(1)
        Diagnostics.work { entered.countDown(); release.await(15,TimeUnit.SECONDS) }
        assertTrue(entered.await(5,TimeUnit.SECONDS))
        try {
            Schedule.enable(c)
            waitUntil { RunService.running && RunService.wakeHeld }
        } finally { release.countDown() }
        assertTrue(Diagnostics.report(c).contains("FOREGROUND_PROMOTED"))
    }
    @Test fun serviceProbeFiresWithoutTermux() {
        start()
        RunService.probe(c,1100)
        waitUntil { Schedule.prefs(c).getLong("serviceProbeAt",0)>0 }
        // A separate UI test validates actual launcher removal.
        c.startForegroundService(Intent(c,RunService::class.java).setAction(RunService.ENSURE))
        waitUntil { Schedule.prefs(c).getString("probeStatus","")!!.startsWith("Service timer fired") }
        assertTrue(RunService.running); assertTrue(RunService.wakeHeld)
        assertEquals(0,commands.size)
    }
    @Test fun manualCallbackAndStaleCallbackDoNotStopContinuousService() {
        start()
        RunService.manual(c,"test")
        assertNotNull(commands.poll(5,TimeUnit.SECONDS))
        val first=Schedule.prefs(c).getString("activeId",null)!!
        complete(first)
        waitUntil { Schedule.prefs(c).getString("activeId",null)==null }
        assertTrue(RunService.running)
        RunService.manual(c,"test")
        assertNotNull(commands.poll(5,TimeUnit.SECONDS))
        val second=Schedule.prefs(c).getString("activeId",null)!!
        complete(first)
        assertEquals(second,Schedule.prefs(c).getString("activeId",null))
        assertTrue(RunService.running)
        complete(second)
    }
    @Test fun duplicateManualDoesNotDispatchAndPauseAllowsActiveRunToFinish() {
        start()
        RunService.manual(c,"test")
        assertNotNull(commands.poll(5,TimeUnit.SECONDS))
        val id=Schedule.prefs(c).getString("activeId",null)!!
        RunService.manual(c,"test")
        waitUntil { Schedule.prefs(c).getString("manualResult","")!!.startsWith("Another execution") }
        assertEquals(0,commands.size)
        Schedule.disable(c)
        assertTrue(RunService.running)
        complete(id)
        waitUntil { !RunService.running && !RunService.wakeHeld }
    }
    @Test fun expiredPersistedRunIsUnknownAndNeverResubmitted() {
        val now=System.currentTimeMillis(); val p=Schedule.prefs(c)
        p.edit().putString("activeId","old").putLong("runTime:old",now-600000)
            .putString("runMode:old","scheduled").putLong("activeUntil",now-1)
            .putLong("activeElapsedUntil",SystemClock.elapsedRealtime()-1).commit()
        start()
        waitUntil { p.getString("activeId",null)==null }
        assertEquals(0,commands.size)
        assertTrue(Diagnostics.report(c).contains("deadline_without_callback"))
    }
    @Test fun dueSlotUsesOriginalTimestampAndCannotDispatchTwice() {
        start()
        val p=Schedule.prefs(c); val at=System.currentTimeMillis()-2000
        p.edit().putLong("slot:1",at).commit()
        RunService.request(c,"test_due_slot")
        val command=commands.poll(5,TimeUnit.SECONDS)!!
        val args=command.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS")!!
        assertTrue(args.contains(at.toString()))
        val id=p.getString("activeId",null)!!
        assertEquals(at,p.getLong("accepted:1",0))
        p.edit().putLong("slot:1",at).commit()
        RunService.request(c,"duplicate_slot")
        complete(id,2)
        waitUntil { p.getLong("slot:1",0)!=at }
        assertEquals(0,commands.size)
        assertTrue(RunService.running)
    }
    @Test fun overdueSlotIsRecordedAndNotReplayed() {
        start()
        val at=System.currentTimeMillis()-600000
        Schedule.prefs(c).edit().putLong("slot:1",at).commit()
        RunService.request(c,"test_overdue")
        waitUntil { Schedule.prefs(c).getLong("slot:1",0)!=at }
        assertEquals(0,commands.size)
        assertTrue(Diagnostics.report(c).contains("original_deadline_passed"))
    }
    @Test fun rebootRecoveryDoesNotResumePriorMonotonicLease() {
        val now=System.currentTimeMillis(); val p=Schedule.prefs(c)
        p.edit().putBoolean("enabled",true).putString("activeId","oldboot")
            .putString("runMode:oldboot","scheduled").putLong("runTime:oldboot",now-10000)
            .putLong("activeUntil",now+470000).putLong("activeElapsedUntil",Long.MAX_VALUE).commit()
        RecoveryReceiver().onReceive(c,Intent(Intent.ACTION_BOOT_COMPLETED))
        waitUntil { RunService.running && p.getString("activeId",null)==null }
        assertEquals(0,commands.size)
        assertTrue(Diagnostics.report(c).contains("reboot_during_prior_execution"))
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
        assertTrue(Diagnostics.report(c).contains("Command exit=0; no valid Node report"))
    }
    @Test fun migrationCancelsOnlyLegacyInterlinkPendingIntents() {
        fun operation(code:Int,receiver:String,action:String,flags:Int)=PendingIntent.getBroadcast(c,code,
            Intent().setClassName(c.packageName,"${c.packageName}.$receiver").setAction(action),flags or PendingIntent.FLAG_IMMUTABLE)
        val legacy=operation(101,"AlarmReceiver","ai.interlink.scheduler.RUN",PendingIntent.FLAG_UPDATE_CURRENT)
        val unrelated=operation(777,"ResultReceiver","unrelated-test",PendingIntent.FLAG_UPDATE_CURRENT)
        val p=Schedule.prefs(c)
        p.edit().putString("credentialSentinel","keep-private-state").putLong("alarm:1",123).commit()
        Schedule.migrate(c)
        assertNull(operation(101,"AlarmReceiver","ai.interlink.scheduler.RUN",PendingIntent.FLAG_NO_CREATE))
        assertNotNull(operation(777,"ResultReceiver","unrelated-test",PendingIntent.FLAG_NO_CREATE))
        assertEquals("keep-private-state",p.getString("credentialSentinel",null))
        assertFalse(p.contains("alarm:1"))
        assertEquals(123L,p.getLong("slot:1",0))
        unrelated.cancel(); legacy.cancel()
    }
    @Test fun timezoneReplanPreservesOriginalActiveExecutionDeadline() {
        start()
        RunService.manual(c,"test"); assertNotNull(commands.poll(5,TimeUnit.SECONDS))
        val p=Schedule.prefs(c); val id=p.getString("activeId",null)!!
        val original=p.getLong("activeUntil",0)
        RecoveryReceiver().onReceive(c,Intent(Intent.ACTION_TIMEZONE_CHANGED))
        waitUntil { Diagnostics.report(c).contains("timezone_change") }
        assertEquals(original,p.getLong("activeUntil",0))
        assertEquals(id,p.getString("activeId",null))
        assertEquals(0,commands.size)
        complete(id)
    }
    @Test fun bootIdentityGuardsLeaseWhenBootBroadcastWasNotObserved() {
        val now=System.currentTimeMillis(); val p=Schedule.prefs(c)
        val current=android.provider.Settings.Global.getInt(c.contentResolver,android.provider.Settings.Global.BOOT_COUNT,0)
        p.edit().putString("activeId","pastboot").putString("runMode:pastboot","scheduled")
            .putLong("runTime:pastboot",now-10000).putLong("activeUntil",now+470000)
            .putLong("activeElapsedUntil",Long.MAX_VALUE).putInt("activeBoot",current+1).commit()
        start()
        waitUntil { p.getString("activeId",null)==null }
        assertEquals(0,commands.size)
        assertTrue(Diagnostics.report(c).contains("boot_identity_changed"))
    }
    @Test fun termuxInternalFailureCannotBecomeSuccessfulManualTest() {
        start()
        RunService.manual(c,"test"); assertNotNull(commands.poll(5,TimeUnit.SECONDS))
        val id=Schedule.prefs(c).getString("activeId",null)!!
        ResultReceiver().onReceive(c,Intent().setData(Uri.parse("interlink-result://run/$id"))
            .putExtra("result",Bundle().apply { putInt("exitCode",0); putInt("err",0) }))
        assertTrue(Schedule.prefs(c).getString("manualResult","")!!.contains("internal error"))
        assertFalse(Schedule.prefs(c).getString("manualResult","")!!.contains("passed"))
        assertTrue(RunService.running)
    }
    @Test fun migratedAlreadyDeliveredSlotCannotDispatchAgain() {
        val p=Schedule.prefs(c); val at=System.currentTimeMillis()-2000
        p.edit().putLong("alarm:1",at).putLong("delivered:1",at).commit()
        start()
        waitUntil { p.getLong("slot:1",0)!=at }
        assertEquals(at,p.getLong("accepted:1",0))
        assertEquals(0,commands.size)
    }
    @Test fun actualPendingIntentCallbackCompletesOnlyItsLeaseAndKeepsService() {
        start()
        RunService.manual(c,"test")
        val command=commands.poll(5,TimeUnit.SECONDS)!!
        @Suppress("DEPRECATION")
        val callback=command.getParcelableExtra<PendingIntent>("com.termux.RUN_COMMAND_PENDING_INTENT")!!
        callback.send(c,0,Intent().putExtra("result",Bundle().apply { putInt("exitCode",0); putInt("err",-1) }))
        waitUntil { Schedule.prefs(c).getString("manualResult","")!!.startsWith("Connection test passed") }
        assertNull(Schedule.prefs(c).getString("activeId",null))
        assertTrue(RunService.running); assertTrue(RunService.wakeHeld)
        try { callback.send(); fail("One-shot callback must be consumed") } catch(_:PendingIntent.CanceledException) {}
    }
}
