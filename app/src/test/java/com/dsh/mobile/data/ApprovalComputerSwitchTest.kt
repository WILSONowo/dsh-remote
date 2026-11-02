package com.dsh.mobile.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalComputerSwitchTest {
    @Test fun switchingComputersClearsPendingItemsWithTheSameSessionId() = runTest(UnconfinedTestDispatcher()) {
        var target = "a"
        val events = MutableSharedFlow<DshConnection.Event>(extraBufferCapacity = 8)
        val state = MutableStateFlow<DshConnection.State>(DshConnection.State.Disconnected)
        val sent = mutableListOf<String>()
        val center = ApprovalCenter(events, { _, _, _ -> sent += target }, { _, _ -> }, state,
            { emptyList() }, { HistoryValue() }, backgroundScope, { target })
        events.emit(DshConnection.Event.ApprovalRequested("same", "approval", "tool", null, null))
        assertEquals(1, center.items.value.size)
        target = "b"
        state.value = DshConnection.State.Connecting("http://192.0.2.11")
        assertTrue(center.items.value.isEmpty())
        center.allow("same", "approval")
        assertTrue(sent.isEmpty())
    }

    @Test fun delayedFailureFromPreviousComputerCannotRestoreAnOldApproval() = runTest(UnconfinedTestDispatcher()) {
        var target = "a"
        val events = MutableSharedFlow<DshConnection.Event>(extraBufferCapacity = 8)
        val state = MutableStateFlow<DshConnection.State>(DshConnection.State.Disconnected)
        val release = CompletableDeferred<Unit>()
        val center = ApprovalCenter(events, { _, _, _ -> release.await(); error("synthetic failure") }, { _, _ -> },
            state, { emptyList() }, { HistoryValue() }, backgroundScope, { target })
        events.emit(DshConnection.Event.ApprovalRequested("same", "approval", "tool", null, null))
        val answering = async { runCatching { center.allow("same", "approval") } }
        target = "b"
        state.value = DshConnection.State.Connecting("http://192.0.2.11")
        release.complete(Unit)
        assertTrue(answering.await().isFailure)
        assertTrue(center.items.value.isEmpty())
    }
}
