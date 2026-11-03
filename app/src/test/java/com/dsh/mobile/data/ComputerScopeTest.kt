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

    @Test fun diskBudgetEvictsOldestHistoryAcrossComputersAndLeavesOtherFilesAlone() {
        val old = java.io.File(folder.root, "computers/a/history/old.json.gz")
        val recent = java.io.File(folder.root, "computers/b/history/new.json.gz")
        for (file in listOf(old, recent)) {
            file.parentFile!!.mkdirs()
            file.writeBytes(ByteArray(80))
        }
        old.setLastModified(recent.lastModified() - 10_000)
        val other = folder.newFile("unrelated")
        HistoryCache(folder.root, "legacy").trimDiskCache(100)
        assertFalse(old.exists())
        assertTrue(recent.exists())
        assertTrue(other.exists())
    }

    @Test fun updateCleanupPreservesNewerInstallerAndActivePartialDownload() {
        val now = System.currentTimeMillis()
        fun file(name: String, timestamp: Long) = folder.newFile(name).apply { setLastModified(timestamp) }
        val installed = file("previous.apk", now - 3_000)
        val pending = file("pending.apk", now)
        val partial = file("pending.apk.part", now)
        val abandoned = file("old.apk.part", now - 2 * 86_400_000L)
        UpdateChecker.pruneDownloads(folder.root, now - 2_000, now)
        assertFalse(installed.exists())
        assertFalse(abandoned.exists())
        assertTrue(pending.exists())
        assertTrue(partial.exists())
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
