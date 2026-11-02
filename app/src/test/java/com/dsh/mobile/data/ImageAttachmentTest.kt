package com.dsh.mobile.data

import android.app.Application
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ImageAttachmentTest {
    @Test fun supportedImagePreservesBytesAndType() {
        val bytes = ByteArray(1024) { it.toByte() }
        for (type in listOf("image/png", "image/jpeg", "image/webp", "image/gif")) {
            val image = encodeImageAttachment(ByteArrayInputStream(bytes), type)
            assertEquals(type, image.mediaType)
            assertArrayEquals(bytes, Base64.decode(image.base64Data, Base64.NO_WRAP))
        }
    }

    @Test fun exactLimitIsAccepted() {
        val bytes = ByteArray(MAX_IMAGE_BYTES)
        val image = encodeImageAttachment(ByteArrayInputStream(bytes), "image/png")
        assertEquals(MAX_IMAGE_BYTES, Base64.decode(image.base64Data, Base64.NO_WRAP).size)
    }

    @Test fun oversizedStreamStopsAtLimitPlusOneInsteadOfReadingWholeFile() {
        var consumed = 0
        val input = object : InputStream() {
            override fun read(): Int { consumed++; return 0 }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                consumed += length
                return length
            }
        }
        val error = assertThrows(IllegalArgumentException::class.java) {
            encodeImageAttachment(input, "image/png")
        }
        assertEquals("图片超过 4MB", error.message)
        assertEquals(MAX_IMAGE_BYTES + 1, consumed)
    }

    @Test fun unsupportedTypeIsRejectedBeforeReading() {
        val input = object : InputStream() {
            override fun read(): Int = error("Must not read unsupported content")
        }
        assertThrows(IllegalArgumentException::class.java) { encodeImageAttachment(input, "application/pdf") }
    }
}
