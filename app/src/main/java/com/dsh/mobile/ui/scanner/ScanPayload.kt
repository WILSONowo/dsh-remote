package com.dsh.mobile.ui.scanner

import java.net.URI

/** Ignore unrelated barcodes without closing the camera. Detailed pairing validation stays at the connection boundary. */
object ScanPayload {
    fun choose(values: List<String>): String? {
        val candidates = values.map(String::trim).distinct().filter { raw ->
            if (raw.length !in 1..4096) return@filter false
            runCatching {
                val uri = URI(raw)
                when (uri.scheme) {
                    "dshremote" -> uri.host in setOf("pair", "direct") && uri.userInfo == null && uri.port == -1 && uri.path.isNullOrEmpty() && uri.query == null && !uri.rawFragment.isNullOrEmpty()
                    "http", "https" -> !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null
                    else -> false
                }
            }.getOrDefault(false)
        }
        return candidates.singleOrNull()
    }
}
