package com.wameed

object TransferIdlePolicy {
    fun shouldStopForIdle(
        idleMs: Long,
        timeoutMs: Long,
        activeTransfers: Int
    ): Boolean {
        return activeTransfers <= 0 && idleMs >= timeoutMs
    }
}
