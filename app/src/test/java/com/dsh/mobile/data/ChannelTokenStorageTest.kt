package com.dsh.mobile.data

import org.junit.Assert.*
import org.junit.Test

class ChannelTokenStorageTest {
    @Test fun hexadecimalTokenIsEncryptedEvenThoughItLooksLikeBase64() {
        val token = "a".repeat(64)
        var encrypted: String? = null
        val box = object : SecretBox {
            override fun encrypt(plain: String): String { encrypted = plain; return "synthetic-ciphertext" }
            override fun decrypt(enc: String): String? = if (enc == "synthetic-ciphertext") token else null
        }
        val profile = HostProfile("fixture", "Fixture", "http://192.0.2.10", channelToken = token)
        val saved = encryptProfileForStorage(profile, box)
        assertEquals(token, encrypted)
        assertNotEquals(token, saved.channelToken)
        assertEquals(token, decryptProfileFromStorage(saved, box).channelToken)
        assertEquals(token, decryptProfileFromStorage(profile, box).channelToken)
    }
}
