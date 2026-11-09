package com.dsh.mobile.ui.scanner

import org.junit.Assert.*
import org.junit.Test

class ScanPayloadTest {
    @Test fun selectsConnectionWithoutAcceptingCredentialsOrOtherBarcodeTypes() {
        assertEquals("http://192.0.2.10:3000", ScanPayload.choose(listOf("http://192.0.2.10:3000")))
        assertEquals("dshremote://pair#synthetic", ScanPayload.choose(listOf(" dshremote://pair#synthetic ")))
        assertEquals("dshremote://direct#1.synthetic", ScanPayload.choose(listOf("dshremote://direct#1.synthetic")))
        for (raw in listOf("123456", "WIFI:S:sample;;", "https://user:secret@example.test", "https://example.test/?token=secret", "dshremote://wrong#test", "dshremote://pair", "x".repeat(4097))) {
            assertNull(raw, ScanPayload.choose(listOf(raw)))
        }
    }
    @Test fun ambiguousTargetsDoNotAutoConnectAndDuplicateDetectionsAreCollapsed() {
        assertNull(ScanPayload.choose(listOf("https://a.example.test", "https://b.example.test")))
        assertEquals("https://a.example.test", ScanPayload.choose(listOf("https://a.example.test", "https://a.example.test")))
        assertEquals("https://a.example.test", ScanPayload.choose(listOf("WIFI:S:test;;", "https://a.example.test")))
    }
}
