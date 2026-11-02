package com.dsh.mobile.data

import org.junit.Assert.*
import org.junit.Test

class UpdateVersionTest {
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
