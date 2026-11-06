package ai.interlink.scheduler

import java.time.*

object ExecutionPolicy {
    val hours = listOf(1,5,9,13,17,21)
    fun next(hour: Int, now: ZonedDateTime): Long {
        var target = now.toLocalDate().atTime(hour,26).atZone(now.zone)
        if (!target.isAfter(now)) target = now.toLocalDate().plusDays(1).atTime(hour,26).atZone(now.zone)
        return target.toInstant().toEpochMilli()
    }
    fun inWindow(expected: Long, actual: Long) = expected > 0 && actual >= expected && actual < expected + 480_000
    fun expired(wall: Long, elapsed: Long, wallDeadline: Long, elapsedDeadline: Long) =
        wall >= wallDeadline || elapsed >= elapsedDeadline
    fun label(mode: String) = when(mode) { "test" -> "Connection test"; "claim" -> "Manual claim"; else -> "Scheduled run" }
    fun outcome(mode: String, exit: Int): String = when(exit) {
        0 -> when(mode) {
            "test" -> "Connection test passed. Authentication and eligibility reads worked. No claim submitted."
            "claim" -> "Manual claim confirmed by API acceptance and an advancing claim timestamp."
            else -> "Scheduled run completed successfully. Check the claim log for both window results."
        }
        2 -> "${label(mode)} ended without two confirmed claims. Check the claim log for eligibility or failure reasons."
        10 -> "Server reports not eligible. No manual claim submitted."
        11 -> "Authentication failed or credentials are missing. Check the private log."
        12 -> "${label(mode)} request failed. Check the log for stage and HTTP status."
        13 -> "API response format was unexpected. Check the safe field-type diagnostics."
        14 -> "${label(mode)} skipped: another bot execution holds the lock."
        20 -> "${label(mode)} deferred: minutes 24–34 of scheduled hours are reserved for automatic runs. Try after minute 34."
        21 -> "Manual claim outcome is uncertain. Do not retry; review the log and server outcome."
        22 -> "Wait at least one minute between manual claim attempts."
        23 -> "This window was already attempted; no duplicate submission."
        124,137 -> "${label(mode)} timed out. A submitted claim may have an unknown outcome."
        126,127 -> "Bot command or files missing. Run the repository setup in Termux."
        else -> "${label(mode)} returned exit $exit. Inspect its private log."
    }
}
