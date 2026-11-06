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
