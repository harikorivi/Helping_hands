package ai.interlink.scheduler

/** Experimental timing policy; never extends either original execution deadline. */
object WakeLockCyclePolicy {
    const val INTERVAL_MS=120000L
    fun due(nowElapsed:Long,lastElapsed:Long)=nowElapsed-lastElapsed>=INTERVAL_MS
    fun remaining(wallDeadline:Long,elapsedDeadline:Long,nowWall:Long,nowElapsed:Long)=
        minOf(wallDeadline-nowWall,elapsedDeadline-nowElapsed).coerceAtLeast(0L)
}
