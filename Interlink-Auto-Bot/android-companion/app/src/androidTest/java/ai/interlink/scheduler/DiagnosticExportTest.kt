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
}
