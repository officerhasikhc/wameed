package com.wameed

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Modern replacement for LocalBroadcastManager using Kotlin SharedFlow.
 */
object WameedEvents {
    private val _events = MutableSharedFlow<WameedEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    suspend fun emit(event: WameedEvent) {
        _events.emit(event)
    }

    fun tryEmit(event: WameedEvent) {
        _events.tryEmit(event)
    }
}

sealed class WameedEvent {
    data class ReceiveProgress(val percent: Int, val speedMbps: Double) : WameedEvent()
    data class ReceiveError(val error: String) : WameedEvent()
    data class ReceiveComplete(val uri: String? = null) : WameedEvent()
    data class ReceiveMeta(val filename: String, val size: Long) : WameedEvent()
    data class ReceiveText(val text: String, val from: String) : WameedEvent()
    data class ReceiveUrl(val url: String, val from: String) : WameedEvent()
    data class ServiceStatus(val isWsConnected: Boolean, val pcName: String) : WameedEvent()
}
