package ai.interlink.scheduler

import org.junit.Assert.*
import org.junit.Test

class ExecutionLeaseTest {
    private val lease=ExecutionLease("id",1000,"scheduled",481000,500000)
    @Test fun remainingUsesEarlierDeadline() {
        assertEquals(1000L,lease.remaining(480000,499000))
        assertEquals(1L,lease.remaining(1000,499999))
    }
    @Test fun changingWallClockBackCannotExtendMonotonicDeadline() {
        assertTrue(lease.expired(-1000000,500000))
        assertTrue(lease.expired(481000,0))
    }
    @Test fun legacyLeaseWithoutElapsedTimeKeepsOriginalWallDeadline() {
        val legacy=lease.copy(elapsedDeadline=0)
        assertEquals(480000L,legacy.remaining(1000,600000))
        assertTrue(legacy.expired(481000,600000))
    }
}
