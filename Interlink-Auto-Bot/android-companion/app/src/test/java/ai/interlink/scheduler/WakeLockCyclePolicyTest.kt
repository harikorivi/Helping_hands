package ai.interlink.scheduler

import org.junit.Assert.*
import org.junit.Test

class WakeLockCyclePolicyTest {
    @Test fun cyclesOnlyAfterTheRealElapsedInterval() {
        assertFalse(WakeLockCyclePolicy.due(120999,1000))
        assertTrue(WakeLockCyclePolicy.due(121000,1000))
    }
    @Test fun reacquisitionCannotExtendOriginalDeadlinesAfterClockChangesOrSuspension() {
        assertEquals(1000L,WakeLockCyclePolicy.remaining(5000,6000,4000,5000))
        assertEquals(1000L,WakeLockCyclePolicy.remaining(5000,6000,1000,5000))
        assertEquals(0L,WakeLockCyclePolicy.remaining(5000,6000,6000,5000))
        assertEquals(0L,WakeLockCyclePolicy.remaining(5000,6000,1000,7000))
    }
}
