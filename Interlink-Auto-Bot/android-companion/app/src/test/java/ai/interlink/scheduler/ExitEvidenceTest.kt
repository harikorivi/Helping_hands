package ai.interlink.scheduler

import org.junit.Assert.*
import org.junit.Test

class ExitEvidenceTest {
    @Test fun systemCauseRemainsReadableWhileCredentialsAreRemoved() {
        val safe=ExitEvidence.description("remove task by com.vivo.abe; token=secret-value password=123456 https://private/path user@example.com Bearer abc.def.ghi")!!
        assertTrue(safe.contains("remove task by com.vivo.abe"))
        for(secret in listOf("secret-value","123456","https://private","user@example.com","abc.def.ghi")) assertFalse(safe.contains(secret))
    }
    @Test fun unavailableAndUnknownMetadataStayExplicit() {
        assertNull(ExitEvidence.description(null))
        assertEquals("USER_REQUESTED",ExitEvidence.name(10))
        assertEquals("UNRECOGNIZED",ExitEvidence.name(999))
        assertTrue(ExitEvidence.description("cause "+"a".repeat(10000))!!.length<=512)
    }
}
