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

/** No credentials or live Termux calls: only the Android boundary is real. */
@RunWith(AndroidJUnit4::class)
class SchedulingIntegrationTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val c get()=inst.targetContext
    private val commands=LinkedBlockingQueue<Intent>()
    private fun waitFor(check:()->Boolean) { val end=SystemClock.elapsedRealtime()+10000; while(!check() && SystemClock.elapsedRealtime()<end) SystemClock.sleep(30); assertTrue("Condition not reached",check()) }
    @Before fun setup() {
        c.stopService(Intent(c,RunService::class.java)); waitFor { !RunService.running && !RunService.wakeHeld }
        Schedule.prefs(c).edit().clear().commit()
        inst.uiAutomation.executeShellCommand("appops set ${c.packageName} SCHEDULE_EXACT_ALARM allow").use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        assertTrue(Schedule.allowed(c))
        RunService.dispatchOverride={ commands.offer(it); ComponentName("com.termux","Fixture") }
    }
    @After fun cleanup() { Schedule.disable(c); c.stopService(Intent(c,RunService::class.java)); waitFor { !RunService.running && !RunService.wakeHeld }; RunService.dispatchOverride=null }
    private fun fire(at:Long,h:Int=1):Intent {
        val p=Schedule.prefs(c); val gen=java.util.UUID.randomUUID().toString()
        p.edit().putBoolean("enabled",true).putInt("alarmPlanVersion",3).putLong("slot:$h",at).putString("generation:$h",gen).commit()
        return Intent(c,SlotAlarmReceiver::class.java).setAction(Schedule.ACTION).putExtra("hour",h).putExtra("scheduled",at).putExtra("generation",gen).also { SlotAlarmReceiver().onReceive(c,it) }
    }
    private fun complete(id:String,exit:Int=0,stdout:String?=null) {
        ResultReceiver().onReceive(c,Intent().setData(Uri.parse("interlink-result://run/$id")).putExtra("result",Bundle().apply { putInt("exitCode",exit); putInt("err",-1); if(stdout!=null) putString("stdout",stdout) }))
    }
    @Test fun sixAlarmRegistrationsRequireNoResidentServiceOrLock() {
        assertTrue(Schedule.enable(c))
        val p=Schedule.prefs(c)
        assertEquals(6,ExecutionPolicy.hours.count { p.getBoolean("armed:$it",false) })
        assertEquals(6,ExecutionPolicy.hours.map { p.getLong("slot:$it",0) }.toSet().size)
        assertNotNull(c.getSystemService(AlarmManager::class.java).nextAlarmClock)
        assertFalse(RunService.running); assertFalse(RunService.wakeHeld); assertFalse(Handoff.held())
        val info=c.packageManager.getPackageInfo(c.packageName,android.content.pm.PackageManager.GET_SERVICES)
        assertEquals(1,info.services!!.size)
        if(Build.VERSION.SDK_INT>=29) assertEquals(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,info.services!!.single().foregroundServiceType)
        assertEquals(0,commands.size)
    }
    @Test fun lateWithinWindowKeepsOriginalTimestampAndCompletionReleasesResources() {
        val at=System.currentTimeMillis()-65000
        fire(at)
        val command=commands.poll(5,TimeUnit.SECONDS)!!
        assertTrue(command.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS")!!.contains(at.toString()))
        waitFor { RunService.running && RunService.wakeHeld && !Handoff.held() }
        val id=Schedule.prefs(c).getString("activeId",null)!!
        complete(id,2)
        waitFor { !RunService.running && !RunService.wakeHeld }
        assertTrue(Schedule.prefs(c).getBoolean("armed:1",false))
        assertTrue(Diagnostics.report(c).contains("EXECUTION_RESOURCES_RELEASED"))
    }
    @Test fun duplicateAndStaleAlarmNeverDispatchAgain() {
        val original=fire(System.currentTimeMillis()-100)
        assertNotNull(commands.poll(5,TimeUnit.SECONDS))
        val p=Schedule.prefs(c); val id=p.getString("activeId",null)!!
        SlotAlarmReceiver().onReceive(c,original)
        assertEquals(0,commands.size); assertEquals(id,p.getString("activeId",null))
        complete(id,2)
        waitFor { !RunService.running }
        SlotAlarmReceiver().onReceive(c,original)
        assertEquals(0,commands.size)
    }
    @Test fun expiredSlotRearmsFutureWithoutLaunchingService() {
        val at=System.currentTimeMillis()-480001
        fire(at)
        assertFalse(RunService.running); assertFalse(Handoff.held()); assertEquals(0,commands.size)
        assertTrue(Schedule.prefs(c).getLong("slot:1",0)>System.currentTimeMillis())
        assertTrue(Diagnostics.report(c).contains("outside_original_deadline"))
    }
    @Test fun pauseCancelsAlarmsButActiveExecutionFinishesWithinOwnDeadline() {
        fire(System.currentTimeMillis()-100); assertNotNull(commands.poll(5,TimeUnit.SECONDS))
        val id=Schedule.prefs(c).getString("activeId",null)!!
        Schedule.disable(c)
        assertTrue(RunService.running)
        assertTrue(ExecutionPolicy.hours.none { Schedule.prefs(c).getBoolean("armed:$it",false) })
        complete(id,2); waitFor { !RunService.running && !RunService.wakeHeld }
    }
    @Test fun manualOneShotCallbackStopsBoundedServiceAndStaleCallbackCannotStopNewRun() {
        RunService.manual(c,"test"); val command=commands.poll(5,TimeUnit.SECONDS)!!
        val first=Schedule.prefs(c).getString("activeId",null)!!
        @Suppress("DEPRECATION") val callback=command.getParcelableExtra<PendingIntent>("com.termux.RUN_COMMAND_PENDING_INTENT")!!
        callback.send(c,0,Intent().putExtra("result",Bundle().apply { putInt("exitCode",0); putInt("err",-1) }))
        waitFor { !RunService.running && !RunService.wakeHeld }
        RunService.manual(c,"test"); assertNotNull(commands.poll(5,TimeUnit.SECONDS))
        val second=Schedule.prefs(c).getString("activeId",null)!!
        complete(first)
        assertEquals(second,Schedule.prefs(c).getString("activeId",null)); assertTrue(RunService.running)
        complete(second); waitFor { !RunService.running }
    }
    @Test fun frameworkAlarmDeliversProbeAndStopsWithoutTermux() {
        val started=System.currentTimeMillis()
        Schedule.probe(c,1300)
        waitFor { Schedule.prefs(c).getString("probeStatus","")!!.startsWith("Alarm and foreground service delivered") }
        waitFor { !RunService.running && !RunService.wakeHeld && !Handoff.held() }
        assertEquals(0,commands.size)
        val events=Diagnostics.report(c).lineSequence().filter { it.startsWith("{") }.map { org.json.JSONObject(it) }.filter { it.optLong("actualAt")>=started }.toList()
        assertTrue(events.any { it.optString("event")=="ALARM_RECEIVED" })
        assertTrue(events.any { it.optString("event")=="FOREGROUND_PROMOTED" && !it.isNull("attemptId") && !it.isNull("serviceId") })
    }
    @Test fun originalElapsedDeadlineStopsExecutionAndReleasesLock() {
        val at=System.currentTimeMillis()-479000
        fire(at); commands.poll(2,TimeUnit.SECONDS)
        waitFor { !RunService.running && !RunService.wakeHeld && Schedule.prefs(c).getString("activeId",null)==null }
        assertTrue(Diagnostics.report(c).contains("original_deadline_without_callback"))
    }
    @Test fun cancelledSummaryBecomesRegisteredAgainWithoutErasingConfirmedResults() {
        val at=System.currentTimeMillis()+600000
        Diagnostics.registered(c,at); Diagnostics.outcome(c,at,"cancelled","user_paused"); Diagnostics.registered(c,at)
        assertFalse(Diagnostics.report(c).contains("${Schedule.time(at)}: cancelled user_paused"))
        Diagnostics.outcome(c,at,"success","fixture_confirmed"); Diagnostics.registered(c,at)
        assertTrue(Diagnostics.report(c).contains("${Schedule.time(at)}: success fixture_confirmed"))
    }
    @Test fun restoringOverduePlanRecordsMissedWithoutAppOpenClaimReplay() {
        Schedule.enable(c)
        val at=System.currentTimeMillis()-600000
        Schedule.prefs(c).edit().putLong("slot:1",at).putBoolean("armed:1",true).commit()
        assertTrue(Schedule.restore(c,"app_opened"))
        assertEquals(0,commands.size); assertFalse(RunService.running)
        assertTrue(Diagnostics.report(c).lineSequence().filter { it.startsWith("{") }.map { org.json.JSONObject(it) }.any { it.optString("event")=="ALARM_MISSED" && it.optString("executionId")==Diagnostics.id(at) })
    }
    @Test fun bootUpdateAndClockRecoveryOnlyRestoreAlarms() {
        Schedule.enable(c)
        for(action in listOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED)) {
            RecoveryReceiver().onReceive(c,Intent(action))
        }
        waitFor { ExecutionPolicy.hours.all { Schedule.prefs(c).getBoolean("armed:$it",false) } }
        assertFalse(RunService.running); assertEquals(0,commands.size)
        Schedule.disable(c)
        assertTrue(ExecutionPolicy.hours.none { Schedule.operation(c,it,flags=PendingIntent.FLAG_NO_CREATE)!=null })
    }
    @Test fun tokenReportsRemainCorrelatedAndWindowsRemainIndependent() {
        val at=System.currentTimeMillis(); val id="fixture"; val execution=Diagnostics.id(at)
        val p=Schedule.prefs(c)
        p.edit().putString("runMode:$id","scheduled").putLong("runTime:$id",at).putString("runExecution:$id",execution).putString("runService:$id","fixture-service").commit()
        val data=org.json.JSONObject().put("version",1).put("executionId",execution).put("attemptId",id).put("scheduledAt",at).put("nodeStarted",true)
            .put("events",org.json.JSONArray().put(org.json.JSONObject().put("event","NODE_PROCESS_STARTED").put("actualAt",at).put("pid",12345)))
            .put("windows",org.json.JSONObject().put("1",org.json.JSONObject().put("result","WINDOW_1_SUCCESS").put("reason","accepted_and_last_claim_advanced"))
                .put("2",org.json.JSONObject().put("result","WINDOW_2_NOT_ELIGIBLE").put("reason","server_not_eligible")))
        assertNull(ExecutionReport.parse("INTERLINK_RESULT $data",execution,"wrong",at))
        complete(id,2,"INTERLINK_RESULT $data")
        val report=Diagnostics.report(c)
        assertTrue(report.contains("Window 2: NOT_ELIGIBLE")); assertTrue(report.contains("fixture-service"))
        val node=report.lineSequence().filter { it.startsWith("{") }.map { org.json.JSONObject(it) }.last { it.optString("event")=="NODE_PROCESS_STARTED" }
        assertEquals(id,node.getString("attemptId")); assertEquals(12345,node.getInt("pid"))
    }
    @Test fun missingReportNeverBecomesTwoSuccessfulClaims() {
        val at=System.currentTimeMillis(); val p=Schedule.prefs(c)
        p.edit().putString("runMode:missing","scheduled").putLong("runTime:missing",at).commit()
        complete("missing",0)
        assertTrue(Diagnostics.report(c).contains("no valid Node report"))
    }
    @Test fun bootIdentityInvalidatesOldElapsedReservationWithoutReplay() {
        val p=Schedule.prefs(c); val now=System.currentTimeMillis()
        val boot=android.provider.Settings.Global.getInt(c.contentResolver,android.provider.Settings.Global.BOOT_COUNT,0)
        p.edit().putString("activeId","old").putString("runMode:old","scheduled").putLong("runTime:old",now-10000)
            .putLong("activeUntil",now+470000).putLong("activeElapsedUntil",Long.MAX_VALUE).putInt("activeBoot",boot+1).commit()
        SessionStore.reconcile(c)
        assertNull(p.getString("activeId",null)); assertEquals(0,commands.size)
        assertTrue(Diagnostics.report(c).contains("boot_identity_changed"))
    }
    @Test fun foregroundPromotionDoesNotWaitForDiagnosticSampling() {
        val entered=CountDownLatch(1); val release=CountDownLatch(1)
        Diagnostics.work { entered.countDown(); release.await(15,TimeUnit.SECONDS) }
        assertTrue(entered.await(5,TimeUnit.SECONDS))
        try { RunService.manual(c,"test"); waitFor { RunService.running && RunService.wakeHeld }; assertNotNull(commands.poll(5,TimeUnit.SECONDS)) }
        finally { release.countDown() }
        complete(Schedule.prefs(c).getString("activeId",null)!!); waitFor { !RunService.running && !RunService.wakeHeld }
    }
    @Test fun pausedProbeOverdueIsReportedWithoutReplayingService() {
        val p=Schedule.prefs(c); val at=System.currentTimeMillis()-130000
        p.edit().putLong("probe",at).putString("generation:0","old").commit()
        Schedule.restore(c,"app_opened")
        assertFalse(p.contains("probe")); assertFalse(RunService.running); assertEquals(0,commands.size)
        assertTrue(p.getString("probeStatus","")!!.contains("not replayed"))
    }
    @Test fun appOpenRearmsFutureSlotsEvenIfStoredRegistrationFlagsSurvivedOsCancellation() {
        Schedule.enable(c)
        val p=Schedule.prefs(c); val at=p.getLong("slot:1",0); val previous=p.getString("generation:1",null)
        c.getSystemService(AlarmManager::class.java).cancel(Schedule.operation(c,1)!!)
        assertTrue(p.getBoolean("armed:1",false))
        assertTrue(Schedule.restore(c,"app_opened"))
        assertNotEquals(previous,p.getString("generation:1",null)); assertEquals(at,p.getLong("slot:1",0))
        assertFalse(RunService.running); assertEquals(0,commands.size)
    }
    @Test fun regrantClearsPermissionWarningWithoutResurrectingPausedSchedule() {
        val p=Schedule.prefs(c)
        p.edit().putBoolean("enabled",false).putString("alarmError","Exact-alarm capability missing. Open alarm access and repair.").commit()
        assertTrue(Schedule.allowed(c)); assertFalse(Schedule.restore(c,"permission_granted"))
        assertEquals("",p.getString("alarmError",null)); assertFalse(Schedule.enabled(c)); assertFalse(RunService.running)
        assertTrue(ExecutionPolicy.hours.none { p.getBoolean("armed:$it",false) })
    }
    @Test fun appOpenAndRepairTwoSecondsAfterDuePreservePendingGenerationUntilRealReceiver() {
        Schedule.enable(c)
        val p=Schedule.prefs(c); val at=System.currentTimeMillis()-2000; val gen="in-flight-original"
        p.edit().putLong("slot:1",at).putString("generation:1",gen).putBoolean("armed:1",true).commit()
        assertTrue(Schedule.restore(c,"app_opened")); assertTrue(Schedule.restore(c,"manual_repair"))
        assertEquals(at,p.getLong("slot:1",0)); assertEquals(gen,p.getString("generation:1",null))
        assertEquals(0,commands.size); assertFalse(RunService.running)
        SlotAlarmReceiver().onReceive(c,Intent(c,SlotAlarmReceiver::class.java).setAction(Schedule.ACTION)
            .putExtra("hour",1).putExtra("scheduled",at).putExtra("generation",gen))
        val command=commands.poll(5,TimeUnit.SECONDS)!!
        assertTrue(command.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS")!!.contains(at.toString()))
        complete(p.getString("activeId",null)!!,2); waitFor { !RunService.running && !RunService.wakeHeld }
    }
    @Test fun unarmedOverdueSlotIsNotReplayedOrPreservedAsAnInFlightAlarm() {
        Schedule.enable(c)
        val p=Schedule.prefs(c); val at=System.currentTimeMillis()-2000
        p.edit().putLong("slot:1",at).putString("generation:1","not-armed").putBoolean("armed:1",false).commit()
        assertTrue(Schedule.restore(c,"app_opened"))
        assertTrue(p.getLong("slot:1",0)>System.currentTimeMillis()); assertFalse(RunService.running); assertEquals(0,commands.size)
    }
    @Test fun nextRealAlarmRepairsIndependentlyExpiredSlotWithoutReplayingIt() {
        Schedule.enable(c)
        val p=Schedule.prefs(c); val old=System.currentTimeMillis()-600000
        p.edit().putLong("slot:5",old).putBoolean("armed:5",false).commit()
        fire(System.currentTimeMillis()-100,1); assertNotNull(commands.poll(5,TimeUnit.SECONDS))
        waitFor { p.getLong("slot:5",0)>System.currentTimeMillis() && p.getBoolean("armed:5",false) }
        assertEquals(0,commands.size)
        complete(p.getString("activeId",null)!!,2); waitFor { !RunService.running }
    }
}
