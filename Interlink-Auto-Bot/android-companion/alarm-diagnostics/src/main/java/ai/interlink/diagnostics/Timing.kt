package ai.interlink.diagnostics

object Timing {
    fun delay(actual: Long, expected: Long) = actual - expected
    fun status(expected: Long, delivered: Long, now: Long): String = when {
        expected == 0L -> "No test scheduled."
        delivered > 0 -> "Receiver ran. Delivery delay: ${delay(delivered,expected)/1000}s. Compare RECEIVER_DELIVERED with APP_OPENED below."
        now < expected -> "Waiting for the alarm. Opening this app does not trigger or repair it."
        else -> "No receiver delivery recorded yet (${(now-expected)/1000}s overdue). Opening does not replay it."
    }
}
