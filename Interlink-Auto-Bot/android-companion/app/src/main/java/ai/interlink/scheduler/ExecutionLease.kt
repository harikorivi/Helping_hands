package ai.interlink.scheduler

data class ExecutionLease(val id:String,val scheduled:Long,val mode:String,val wallDeadline:Long,val elapsedDeadline:Long) {
    fun remaining(wall:Long,elapsed:Long)=minOf(wallDeadline-wall,if(elapsedDeadline>0) elapsedDeadline-elapsed else Long.MAX_VALUE).coerceAtLeast(0)
    fun expired(wall:Long,elapsed:Long)=remaining(wall,elapsed)<=0
}
