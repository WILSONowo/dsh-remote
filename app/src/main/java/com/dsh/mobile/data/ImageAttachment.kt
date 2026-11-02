package com.dsh.mobile.data

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
private val IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/webp", "image/gif")

/** Read and encode off the UI thread; consume at most the limit plus one byte. */
suspend fun readImageAttachment(resolver: ContentResolver, uri: Uri): DshConnection.ImagePart? =
    withContext(Dispatchers.IO) {
        val type = resolver.getType(uri) ?: "image/png"
        require(type in IMAGE_TYPES) { "不支持的图片格式: $type" }
        resolver.openInputStream(uri)?.use { encodeImageAttachment(it, type) }
    }

internal fun encodeImageAttachment(input: InputStream, type: String): DshConnection.ImagePart {
    require(type in IMAGE_TYPES) { "不支持的图片格式: $type" }
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer, 0, minOf(buffer.size, MAX_IMAGE_BYTES + 1 - output.size()))
        if (count < 0) break
        require(output.size() + count <= MAX_IMAGE_BYTES) { "图片超过 4MB" }
        output.write(buffer, 0, count)
    }
    return DshConnection.ImagePart(type, Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP))
}
