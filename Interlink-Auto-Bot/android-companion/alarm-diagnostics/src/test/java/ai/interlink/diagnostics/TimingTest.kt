package ai.interlink.diagnostics
import org.junit.Test
import org.junit.Assert.*
class TimingTest {
    @Test fun pendingAndOverdueAreNotReportedAsDelivered() {
        assertTrue(Timing.status(2000,0,1000).contains("Waiting"))
        assertTrue(Timing.status(2000,0,9000).contains("No receiver delivery"))
    }
    @Test fun deliveryReportsDelayAndRequiresCorrelationWithOpening() {
        assertEquals(60000L,Timing.delay(62000,2000))
        assertTrue(Timing.status(2000,62000,63000).contains("60s"))
        assertTrue(Timing.status(2000,62000,63000).contains("APP_OPENED"))
    }
    @Test fun cancellationAndEarlyDeliveryAreExplicit() {
        assertEquals("No test scheduled.",Timing.status(0,0,9000))
        assertEquals(-1000L,Timing.delay(1000,2000))
    }
}
