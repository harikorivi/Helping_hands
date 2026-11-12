package ai.interlink.scheduler

import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticExportTest {
    @Test fun startupReportExplainsExitTwoAndRejectsUntrustedReason() {
        val data=org.json.JSONObject().put("version",1).put("executionId","schedule-1").put("attemptId","a1").put("scheduledAt",1)
            .put("nodeStarted",false).put("exitCode",2).put("startupReason","wrapper_exit_without_report")
        val parsed=ExecutionReport.parse("INTERLINK_RESULT $data","schedule-1","a1",1)!!
        assertEquals("wrapper_exit_without_report",parsed.startupReason)
        assertTrue(parsed.summary.contains("exit 2"))
        data.put("startupReason","secret-token")
        assertFalse(ExecutionReport.parse("INTERLINK_RESULT $data","schedule-1","a1",1)!!.summary.contains("secret-token"))
    }

    @Test fun liveDebugToggleGatesExtraEventsButKeepsCoreEvidence() {
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = Schedule.prefs(c)
        val before = Diagnostics.liveDebug(c)
        val marker = java.util.UUID.randomUUID().toString()
        try {
            prefs.edit().putBoolean("liveDebug",false).commit()
            Diagnostics.debug(c,"APP_BACKGROUND",marker + "-disabled")
            Diagnostics.emit(c,"MANUAL_STATE_SNAPSHOT",reason=marker + "-core")
            assertFalse(Diagnostics.report(c).contains(marker + "-disabled"))
            assertTrue(Diagnostics.report(c).contains(marker + "-core"))
            prefs.edit().putBoolean("liveDebug",true).commit()
            Diagnostics.debug(c,"APP_FOREGROUND",marker + "-enabled")
            assertTrue(Diagnostics.report(c).contains(marker + "-enabled"))
            val state = Diagnostics.device(c)
            assertTrue(state.getBoolean("liveDebugEnabled"))
            assertTrue(state.has("deviceLocked"))
            assertTrue(state.has("interactive"))
            assertTrue(DiagnosticExport.snapshot(c).contains(marker + "-enabled"))
        } finally { prefs.edit().putBoolean("liveDebug",before).commit() }
    }

    @Test fun downloadsExportIsPublishedAndReadableWithoutStoragePermission() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val name = DiagnosticExport.fileName()
        val report = "Diagnostic export fixture\nUTF-8: → ✓\n"
        val uri = DiagnosticExport.saveToDownloads(c, name, report)
        try {
            assertEquals(report, c.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            c.contentResolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.RELATIVE_PATH, MediaStore.Downloads.IS_PENDING), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals(name, it.getString(0))
                assertEquals("Download/Interlink Scheduler/", it.getString(1))
                assertEquals(0, it.getInt(2))
            }
        } finally { c.contentResolver.delete(uri, null, null) }
    }
    @Test fun trialRetentionPreservesThirdCalendarDateAndOrdinaryModePrunesIt() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val p=Schedule.prefs(c); val previous=Diagnostics.trialEvidence(c)
        Diagnostics.report(c) // Flush writers and initialize the schema before inserting a dated fixture.
        val db=c.openOrCreateDatabase("execution-diagnostics.db",0,null)
        val marker="trial-retention-"+java.util.UUID.randomUUID()
        val at=java.time.LocalDate.now().minusDays(2).atTime(12,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val payload=org.json.JSONObject().put("event",marker).put("actualAt",at).toString()
        try {
            db.insertOrThrow("events",null,android.content.ContentValues().apply { put("at",at); put("payload",payload) })
            p.edit().putBoolean("trialEvidence",true).commit(); Diagnostics.prune(c)
            assertTrue(Diagnostics.report(c).contains(marker))
            p.edit().putBoolean("trialEvidence",false).commit(); Diagnostics.prune(c)
            assertFalse(Diagnostics.report(c).contains(marker))
        } finally { db.delete("events","payload=?",arrayOf(payload)); db.close(); p.edit().putBoolean("trialEvidence",previous).commit() }
    }
}
