package com.dsh.mobile.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDirectoryTest {
    @Test fun invalidationsDuringReadCoalesceAndNeverPublishStaleArchiveState() = runTest {
        val stale = CompletableDeferred<List<String>>()
        val latest = CompletableDeferred<List<String>>()
        var reads = 0
        val entry = DirectoryEntry(backgroundScope) {
            when (++reads) {
                1 -> emptyList()
                2 -> stale.await()
                else -> latest.await()
            }
        }
        entry.load()
        runCurrent()
        repeat(10) { entry.load() }
        assertEquals(1, reads)
        entry.invalidate()
        runCurrent()
        entry.edit { it + "archived" }
        repeat(10) { entry.invalidate() }
        stale.complete(emptyList())
        runCurrent()
        assertEquals(listOf("archived"), entry.state.value.value)
        assertEquals(3, reads)
        latest.complete(listOf("archived", "another"))
        runCurrent()
        assertEquals(listOf("archived", "another"), entry.state.value.value)
        assertFalse(entry.state.value.loading)
    }

    @Test fun failedFirstReadIsDifferentFromAnEmptyComputerAndCanBeRetried() = runTest {
        var available = false
        val entry = DirectoryEntry(backgroundScope) {
            check(available)
            emptyList<String>()
        }
        entry.load()
        runCurrent()
        assertNull(entry.state.value.value)
        assertNotNull(entry.state.value.error)
        available = true
        entry.load(force = true)
        runCurrent()
        assertEquals(emptyList<String>(), entry.state.value.value)
        assertNull(entry.state.value.error)
    }
}
