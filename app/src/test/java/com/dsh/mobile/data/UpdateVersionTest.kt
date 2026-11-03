package com.dsh.mobile.data

import org.junit.Assert.*
import org.junit.Test

class UpdateVersionTest {
    @Test fun savedMirrorOnlyReordersApkFallbacks() {
        val primary = "https://hub.neko-arc.top/"
        for (saved in listOf(null, "", primary, "https://ghproxy.net/", "https://unused.example/")) {
            val sources = UpdateMirrors.apkDownloadMirrors(saved)
            assertEquals(primary, sources.first())
            assertEquals("", sources.last())
            assertEquals((UpdateMirrors.DOWNLOAD_MIRRORS + primary).toSet(), sources.toSet())
            assertEquals(sources.distinct(), sources)
            if (saved == "https://ghproxy.net/") assertEquals(saved, sources[1])
        }
    }

    @Test fun oversizedVersionComponentsAreRejectedWithoutCrashing() {
        for (version in listOf("999999999999.1.0", "1.999999999999.0", "1.0.999999999999")) {
            assertNull(UpdateChecker.parseVersion(version))
            assertFalse(UpdateChecker.isNewer(version, "3.1.0"))
        }
    }

    @Test fun assetNamesAndNormalVersionComparisonStillWork() {
        assertEquals(Triple(3, 1, 2), UpdateChecker.parseVersion("DSH-Remote-v3.1.2.apk"))
        assertTrue(UpdateChecker.isNewer("v3.1.2", "3.1.0"))
        assertFalse(UpdateChecker.isNewer("v3.1.0", "3.1.2"))
    }
}
