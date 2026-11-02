package com.dsh.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** Check only the selected computer's candidates. No code, bearer token, or redirects. */
suspend fun probeLanPairingTarget(target: LanPairingTarget): String? = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
        .connectTimeout(1200, TimeUnit.MILLISECONDS).readTimeout(1200, TimeUnit.MILLISECONDS)
        .callTimeout(1800, TimeUnit.MILLISECONDS).build()
    try {
        target.urls.take(8).firstOrNull { url ->
            ensureActive()
            runCatching {
                client.newCall(Request.Builder().url("$url/api/remote-access/discovery/info?session=${target.session}").build())
                    .execute().use { response ->
                        if (!response.isSuccessful || (response.body?.contentLength() ?: 0) > 4096) false
                        else {
                            val source = response.body?.source() ?: return@use false
                            val buffer = okio.Buffer()
                            while (buffer.size <= 4096 && source.read(buffer, minOf(1024L, 4097 - buffer.size)) != -1L) { }
                            if (buffer.size > 4096) false else Json.parseToJsonElement(buffer.readUtf8()).jsonObject.let {
                                it["ok"]?.jsonPrimitive?.booleanOrNull == true && it["v"]?.jsonPrimitive?.intOrNull == 1 &&
                                    it["session"]?.jsonPrimitive?.content == target.session && it["name"]?.jsonPrimitive?.content == target.name
                            }
                        }
                    }
            }.getOrDefault(false)
        }
    } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
}
