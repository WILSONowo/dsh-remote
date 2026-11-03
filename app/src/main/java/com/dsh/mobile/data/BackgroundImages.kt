package com.dsh.mobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

internal object BackgroundImages {
    // Legacy galleries can grant access only until the activity finishes.
    fun retain(context: Context, uri: Uri): Uri {
        require(uri.scheme == "content")
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return uri
        } catch (_: SecurityException) {
            val dir = File(context.noBackupFilesDir, "backgrounds")
            dir.mkdirs()
            val file = File.createTempFile("image-", ".img", dir)
            try {
                requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                require(file.length() > 0)
                return Uri.fromFile(file)
            } catch (e: Exception) {
                file.delete()
                throw e
            }
        }
    }

    fun release(context: Context, uri: Uri) {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
        } else if (uri.scheme == "file") {
            val file = File(uri.path ?: return)
            if (file.parentFile == File(context.noBackupFilesDir, "backgrounds") && file.name.startsWith("image-")) file.delete()
        }
    }
}
