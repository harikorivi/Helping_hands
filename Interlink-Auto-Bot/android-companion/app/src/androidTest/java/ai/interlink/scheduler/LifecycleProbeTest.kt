package ai.interlink.scheduler

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Opt-in, disposable emulator only: arm, terminate instrumentation, kill background process,
 * then inspect delivery in another instrumentation invocation. Never launches a claim. */
class LifecycleProbeTest {
    @Test fun arm() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("lifecycleProbe") == "true")
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        Schedule.disable(c)
        Schedule.prefs(c).edit().clear().commit()
        BackgroundTest.schedule(c,20000)
    }
    @Test fun verify() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("lifecycleProbe") == "true")
        val c = InstrumentationRegistry.getInstrumentation().targetContext
        val history = Schedule.prefs(c).getString("events","").orEmpty()
        assertTrue(history,history.contains("Background test alarm delivered:"))
        assertTrue(history,history.contains("Run service entered: mode=test"))
        assertFalse(history,history.contains("mode=claim"))
        BackgroundTest.cancel(c)
    }
}
