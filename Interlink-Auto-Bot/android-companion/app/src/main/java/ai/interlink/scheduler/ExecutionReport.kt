package ai.interlink.scheduler

import org.json.JSONObject

object ExecutionReport {
    data class Parsed(val summary:String,val successful:Int,val events:List<JSONObject>,val complete:Boolean)
    private val reasons=setOf("accepted_and_last_claim_advanced","server_not_eligible","missed_window","authentication_required",
        "startup_authentication_required","previous_submission_outcome_unknown","submission_outcome_unknown","verification_not_advanced",
        "claim_response_not_accepted","insufficient_time_remaining","insufficient_time_after_preclaim","verification_baseline_missing",
        "rate_limited","authentication_rejected","execution_deadline","clock_or_timezone_changed","interrupted","previous_result",
        "request_failed_eligibility","request_failed_account_baseline","request_failed_pre_claim_sequence","finished","nonzero_exit",
        "tokens_persisted","missing_refresh_token","refresh_rejected_or_invalid_response","local_state_or_runtime_failure")
    fun reason(value:String) = if(value in reasons) value else "unspecified_or_unrecognized_reason"
    fun parse(stdout:String?,executionId:String,attemptId:String,scheduled:Long):Parsed? {
        if(stdout==null || stdout.length>65536) return null
        return try {
            val line=stdout.lineSequence().singleOrNull { it.startsWith("INTERLINK_RESULT ") } ?: return null
            val data=JSONObject(line.removePrefix("INTERLINK_RESULT "))
            if(data.optInt("version")!=1 || data.optString("executionId")!=executionId || data.optString("attemptId")!=attemptId || data.optLong("scheduledAt")!=scheduled) return null
            if(!data.optBoolean("nodeStarted")) return Parsed("Node startup not confirmed. Wrapper exit ${data.optInt("exitCode",-1)}; check installation, lock or timeout.",0,emptyList(),false)
            val windows=data.optJSONObject("windows") ?: JSONObject()
            var successes=0; var complete=true
            val summary=(1..2).joinToString("\n") { n ->
                val w=windows.optJSONObject(n.toString())
                val result=w?.optString("result").orEmpty()
                val valid=result in setOf("WINDOW_${n}_SUCCESS","WINDOW_${n}_NOT_ELIGIBLE","WINDOW_${n}_FAILED")
                if(!valid) { complete=false; "Window $n: UNKNOWN — no completed window report." }
                else {
                    if(result.endsWith("_SUCCESS")) successes++
                    val uncertain=w?.optBoolean("submitted")==true && !result.endsWith("_SUCCESS")
                    "Window $n: ${result.removePrefix("WINDOW_${n}_")} — ${reason(w?.optString("reason").orEmpty())}${if(uncertain) "; submitted, outcome may be uncertain; do not blindly retry" else ""}" +
                        (if(w?.has("httpStatus")==true && !w.isNull("httpStatus")) "; HTTP ${w.optInt("httpStatus")}" else "")
                }
            }
            val events=mutableListOf<JSONObject>(); val list=data.optJSONArray("events")
            val names=setOf("NODE_PROCESS_STARTED","NODE_PROCESS_EXITED","TOKEN_REFRESH_STARTED","TOKEN_REFRESH_COMPLETED","TOKEN_REFRESH_REQUEST_FAILED",
                "CLAIM_WINDOW_1_STARTED","CLAIM_WINDOW_1_COMPLETED","CLAIM_WINDOW_2_STARTED","CLAIM_WINDOW_2_COMPLETED","REQUEST_FAILED","ELIGIBILITY_CHECKED","CLAIM_SUBMISSION_STARTED","CLAIM_RESPONSE_RECEIVED","EXECUTION_FAILED")
            if(list!=null) for(i in 0 until minOf(list.length(),102)) {
                val e=list.optJSONObject(i) ?: continue
                if(e.optString("event") !in names || e.optLong("actualAt")<=0) continue
                val safe=JSONObject().put("event",e.getString("event")).put("actualAt",e.getLong("actualAt")).put("pid",e.optInt("pid"))
                if(e.has("reason")) safe.put("reason",reason(e.optString("reason")))
                for(k in listOf("httpStatus","applicationStatus","attempt","delayMs","exitCode")) if(e.has(k)&& !e.isNull(k)) safe.put(k,e.optLong(k))
                for(k in listOf("eligible","submitted")) if(e.has(k)) safe.put(k,e.optBoolean(k))
                val result=e.optString("result"); if(result.matches(Regex("(SUCCESS|FAILED|WINDOW_[12]_(SUCCESS|NOT_ELIGIBLE|FAILED))"))) safe.put("result",result)
                val stage=e.optString("stage"); if(stage in setOf("eligibility","account_baseline","pre_claim_sequence","claim_submission","claim_verification","token_refresh")) safe.put("stage",stage)
                val code=e.optString("code"); if(code in setOf("ECONNABORTED","ETIMEDOUT","ENOTFOUND","ECONNRESET","ERR_CANCELED","ERR_NETWORK","REQUEST_OR_RESPONSE_FAILED")) safe.put("code",code)
                events.add(safe)
            }
            Parsed("$successes/2 confirmed claims\n$summary",successes,events,complete)
        } catch(_:Exception) { null }
    }
}
