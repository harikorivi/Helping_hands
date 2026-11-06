package ai.interlink.scheduler

import java.time.*

object LogRetention {
    fun retain(history: String, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): List<String> {
        val cutoff = now.atZone(zone).toLocalDate().minusDays(1)
        return history.lineSequence().filter { it.isNotBlank() }.filter { line ->
            // Unknown legacy formats stay within the count cap, rather than being guessed old.
            val date = try { Instant.parse(line.substringBefore(' ')).atZone(zone).toLocalDate() }
                       catch (_: Exception) { null }
            date == null || !date.isBefore(cutoff)
        }.toList().takeLast(100)
    }
}
