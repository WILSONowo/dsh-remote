package com.dsh.mobile.data

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class UpdateIntegrityTest {
    private val hash = "a".repeat(64)

    @Test fun digestFieldMustBeSha256() {
        assertEquals(hash, UpdateIntegrity.fromDigest("sha256:$hash"))
        assertEquals(hash, UpdateIntegrity.fromDigest(" SHA256:${hash.uppercase()} "))
        assertNull(UpdateIntegrity.fromDigest(hash))
        assertNull(UpdateIntegrity.fromDigest("sha512:$hash"))
        assertNull(UpdateIntegrity.fromDigest("sha256:${hash.dropLast(1)}"))
        assertNull(UpdateIntegrity.fromDigest(null))
    }

    @Test fun sumsFileMatchesOnlyTheExactAssetName() {
        val sums = "$hash  DSH-Remote-v4.0.0.apk\n${"b".repeat(64)} *DSH-Remote-v4.0.0-min.apk\n"
        assertEquals(hash, UpdateIntegrity.fromSums(sums, "DSH-Remote-v4.0.0.apk"))
        assertEquals("b".repeat(64), UpdateIntegrity.fromSums(sums, "DSH-Remote-v4.0.0-min.apk"))
        assertNull(UpdateIntegrity.fromSums(sums, "DSH-Remote-v4.0.0"))
        assertNull(UpdateIntegrity.fromSums(sums, "other.apk"))
        // Conflicting entries for one name are refused instead of picking one.
        assertNull(UpdateIntegrity.fromSums(sums + "${"c".repeat(64)}  DSH-Remote-v4.0.0.apk", "DSH-Remote-v4.0.0.apk"))
    }

    @Test fun verifyFailsClosed() {
        val file = File.createTempFile("dsh-update", ".apk").apply { writeText("synthetic apk"); deleteOnExit() }
        val actual = UpdateIntegrity.sha256(file)
        assertEquals(UpdateIntegrity.sha256Bytes("synthetic apk".toByteArray()), actual)
        UpdateIntegrity.verify(file, actual)
        assertThrows(IOException::class.java) { UpdateIntegrity.verify(file, hash) }
        assertThrows(IOException::class.java) { UpdateIntegrity.verify(file, null) }
        assertThrows(IOException::class.java) { UpdateIntegrity.verify(file, "not-a-hash") }
    }
}
