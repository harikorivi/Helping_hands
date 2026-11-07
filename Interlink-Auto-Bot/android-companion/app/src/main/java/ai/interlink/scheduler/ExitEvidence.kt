package ai.interlink.scheduler

/** System descriptions are optional, unstable text. Keep useful cause labels without credentials. */
object ExitEvidence {
    fun name(reason:Int):String = when(reason) {
        0 -> "UNKNOWN"; 1 -> "EXIT_SELF"; 2 -> "SIGNALED"; 3 -> "LOW_MEMORY"
        4 -> "CRASH"; 5 -> "CRASH_NATIVE"; 6 -> "ANR"; 7 -> "INITIALIZATION_FAILURE"
        8 -> "PERMISSION_CHANGE"; 9 -> "EXCESSIVE_RESOURCE_USAGE"; 10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"; 12 -> "DEPENDENCY_DIED"; 13 -> "OTHER"; 14 -> "FREEZER"
        15 -> "PACKAGE_STATE_CHANGE"; 16 -> "PACKAGE_UPDATED"; else -> "UNRECOGNIZED"
    }
    fun description(raw:String?):String? = raw?.take(4096)
        ?.replace(Regex("(?i)Bearer\\s+[^\\s,;]+"),"Bearer [redacted]")
        ?.replace(Regex("[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"),"[redacted-token]")
        ?.replace(Regex("(?i)(token|passcode|password|otp|authorization)\\s*[:=]\\s*[^\\s,;]+"),"$1=[redacted]")
        ?.replace(Regex("(?i)https?://[^\\s]+"),"[redacted-url]")
        ?.replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),"[redacted-email]")
        ?.replace(Regex("[^\\s]{81,}"),"[redacted-long-value]")
        ?.replace(Regex("[\\p{Cntrl}]")," ")?.take(512)
}
