package com.pokerlanka.pokie_talkie

import com.pokerlanka.pokie_talkie.consent.ConsentBus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentBusUnitTest {

    @Test
    fun consentBus_completesPendingRequestWithDecision() = runBlocking {
        val allowDeferred = ConsentBus.request("req_1")
        ConsentBus.respond("req_1", true)
        assertTrue(allowDeferred.await())

        val denyDeferred = ConsentBus.request("req_2")
        ConsentBus.respond("req_2", false)
        assertFalse(denyDeferred.await())
    }
}
