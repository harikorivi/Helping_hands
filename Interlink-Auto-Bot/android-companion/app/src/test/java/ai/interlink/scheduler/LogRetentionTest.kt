package ai.interlink.scheduler
import org.junit.Test
import org.junit.Assert.*
import java.time.*
class LogRetentionTest {
    @Test fun indiaMidnightRetainsTwoLocalCalendarDates() {
        val history=listOf("2026-11-04T18:29:59Z old","2026-11-04T18:30:00Z yesterday","2026-11-05T18:30:00Z today","2026-11-07T00:00:00Z future").joinToString("\n")
        val kept=LogRetention.retain(history,Instant.parse("2026-11-05T18:31:00Z"),ZoneId.of("Asia/Kolkata"))
        assertEquals(3,kept.size);assertFalse(kept.any {it.endsWith("old")})
    }
    @Test fun dstAndYearBoundariesUseDates() {
        val history="2026-10-31T03:59:59Z old\n2026-10-31T04:00:00Z yesterday"
        assertEquals(1,LogRetention.retain(history,Instant.parse("2026-11-02T04:59:00Z"),ZoneId.of("America/New_York")).size)
        assertEquals(1,LogRetention.retain("2025-12-30T12:00:00Z old\n2025-12-31T12:00:00Z yesterday",Instant.parse("2026-01-01T23:59:00Z"),ZoneOffset.UTC).size)
    }
    @Test fun legacyLinesAreBoundedAndBlankLinesRemoved() {
        assertEquals(listOf("legacy unknown"),LogRetention.retain("\nlegacy unknown\n"))
        assertEquals(100,LogRetention.retain((1..150).joinToString("\n") {"legacy $it"}).size)
        assertTrue(LogRetention.retain("").isEmpty())
    }
}
