package ai.interlink.scheduler

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ExecutionPolicyTest {
    @Test fun sixIndependentSlotsSurviveAnEarlierMiss() {
        val now = ZonedDateTime.of(2026,11,6,0,0,0,0,ZoneId.of("Asia/Kolkata"))
        val times = ExecutionPolicy.hours.associateWith { ExecutionPolicy.next(it,now) }
        assertEquals(6,times.values.toSet().size)
        val late = now.withHour(4).withMinute(37)
        assertFalse(ExecutionPolicy.inWindow(times.getValue(1),late.toInstant().toEpochMilli()))
        assertTrue(times.getValue(5) > late.toInstant().toEpochMilli())
        assertEquals(times.getValue(5),ExecutionPolicy.next(5,late))
    }
    @Test fun deadlineCountsSuspensionAndDoesNotDependOnHandlerUptime() {
        assertFalse(ExecutionPolicy.expired(100,200,1000,1100))
        assertTrue(ExecutionPolicy.expired(4000000,4000100,1000,1100))
        assertTrue(ExecutionPolicy.expired(0,1200,1000,1100)) // wall clock moved backwards
    }
    @Test fun boundariesAndDuplicatesUseOriginalWindow() {
        assertTrue(ExecutionPolicy.inWindow(1000,1000))
        assertTrue(ExecutionPolicy.inWindow(1000,480999))
        assertFalse(ExecutionPolicy.inWindow(1000,481000))
        assertFalse(ExecutionPolicy.inWindow(1000,999))
    }
    @Test fun nextOccurrenceUsesLocalDateNotFixed24Hours() {
        val zone=ZoneId.of("America/New_York")
        val now=ZonedDateTime.of(2026,3,7,6,0,0,0,zone)
        val next=Instant.ofEpochMilli(ExecutionPolicy.next(5,now)).atZone(zone)
        assertEquals(8,next.dayOfMonth);assertEquals(5,next.hour);assertEquals(26,next.minute)
    }
    @Test fun midnightAndTimezonePlanning() {
        val now=ZonedDateTime.of(2026,11,6,22,0,0,0,ZoneId.of("Asia/Kolkata"))
        for(hour in ExecutionPolicy.hours) {
            val target=Instant.ofEpochMilli(ExecutionPolicy.next(hour,now)).atZone(now.zone)
            assertEquals(7,target.dayOfMonth);assertEquals(hour,target.hour)
        }
    }
    @Test fun testSuccessNeverClaimsBothWindowsSucceeded() {
        assertTrue(ExecutionPolicy.outcome("test",0).contains("No claim submitted"))
        assertFalse(ExecutionPolicy.outcome("test",0).contains("both window"))
        assertTrue(ExecutionPolicy.outcome("claim",0).contains("Manual claim confirmed"))
        assertTrue(ExecutionPolicy.outcome("scheduled",14).contains("another bot execution"))
    }
}
