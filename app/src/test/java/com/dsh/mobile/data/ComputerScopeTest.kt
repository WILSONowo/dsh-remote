package com.dsh.mobile.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ComputerScopeTest {
    @get:Rule val folder = TemporaryFolder()
    private val first = HostProfile("computer-a", "Computer A", "http://192.0.2.10:7941")
    private val second = HostProfile("computer-b", "Computer B", "http://192.0.2.11:7941")

    @Test fun renameAndTokenRotationKeepScopeButEndpointChangeDoesNot() {
        assertEquals(first.computerScope(), first.copy(remark = "Renamed", channelToken = "synthetic").computerScope())
        assertNotEquals(first.computerScope(), second.computerScope())
        assertNotEquals(first.computerScope(), first.copy(url = second.url).computerScope())
    }

    @Test fun identicalSessionIdsNeverShareHistoryListsOrPrefetch() {
        val a = HistoryCache(folder.root, first.computerScope())
        val b = HistoryCache(folder.root, second.computerScope())
        a.saveHistory("same", listOf(CachedItem("user", "from-a")))
        a.saveSessionList(listOf(SessionSummary("same", updatedAt = 1L)))
        val prefetched = HistoryValue(events = listOf(HistoryEntry(SessionEventWire("fixture", 1L, 1L))))
        a.putPrefetch("same", prefetched)
        assertNull(b.takePrefetch("same"))
        assertEquals(prefetched, a.takePrefetch("same"))
        assertNull(b.loadHistory("same"))
        assertNull(b.loadSessionList())
        b.saveHistory("same", listOf(CachedItem("user", "from-b")))
        assertEquals("from-a", a.loadHistory("same")!!.single().text)
        assertEquals("from-b", b.loadHistory("same")!!.single().text)
        a.clearHistory("same")
        assertNull(a.loadHistory("same"))
        assertEquals("from-b", b.loadHistory("same")!!.single().text)
    }

    @Test fun completeHistoryWatermarkSurvivesReopeningTheSameComputer() {
        val scope = first.computerScope()
        HistoryCache(folder.root, scope).saveHistory("complete", listOf(CachedItem("user", "complete")), 7L)
        assertEquals(7L, HistoryCache(folder.root, scope).loadHistoryWithMeta("complete")?.completeThroughSeq)
    }

    @Test fun fileKeysCannotCollideOrEscapeCacheDirectory() {
        val a = HistoryCache(folder.root, first.computerScope())
        a.saveHistory("a/b", listOf(CachedItem("user", "slash")))
        a.saveHistory("a_b", listOf(CachedItem("user", "underscore")))
        a.saveHistory("../../escape", listOf(CachedItem("user", "path")))
        assertEquals("slash", a.loadHistory("a/b")!!.single().text)
        assertEquals("underscore", a.loadHistory("a_b")!!.single().text)
        assertTrue(folder.root.walkTopDown().filter { it.isFile }.all { it.canonicalPath.startsWith(folder.root.canonicalPath) })
    }
}
