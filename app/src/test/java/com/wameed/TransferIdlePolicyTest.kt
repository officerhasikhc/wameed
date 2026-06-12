package com.wameed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferIdlePolicyTest {
    @Test
    fun idleTimeoutDoesNotStopServiceWhileTransferIsActive() {
        assertFalse(
            TransferIdlePolicy.shouldStopForIdle(
                idleMs = 20 * 60 * 1000L,
                timeoutMs = 15 * 60 * 1000L,
                activeTransfers = 1
            )
        )
    }

    @Test
    fun idleTimeoutStopsServiceWhenNoTransferIsActive() {
        assertTrue(
            TransferIdlePolicy.shouldStopForIdle(
                idleMs = 20 * 60 * 1000L,
                timeoutMs = 15 * 60 * 1000L,
                activeTransfers = 0
            )
        )
    }
}
