package com.dsh.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Proxy

class OkHttpClientFactoryTest {

    @Test
    fun relayCredentialStaysWithinHostPathAndSurvivesStaleProfile() {
        val profile = HostProfile(id = "token-scope-test", remark = "test", url = "",
            connectionMode = "relay", relayServerUrl = "https://relay.example", relayHostId = "pc-a")
        val client = OkHttpClientFactory.build(profile).first
        OkHttpClientFactory.ChannelTokenRegistry.set(profile.id, "fresh-token")
        OkHttpClientFactory.build(profile) // old Compose snapshot must not erase the new token
        fun tokenAt(url: String): String? {
            var sent: String? = null
            val probe = client.newBuilder().addInterceptor { chain ->
                sent = chain.request().header("Authorization")
                okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200).message("fixture").body(okhttp3.ResponseBody.create(null, "")).build()
            }.build()
            probe.newCall(okhttp3.Request.Builder().url(url).build()).execute().close()
            return sent
        }
        assertEquals("Bearer fresh-token", tokenAt("https://relay.example/h/pc-a/api/session.list"))
        assertNull(tokenAt("https://relay.example/h/pc-b/api/session.list"))
        assertNull(tokenAt("https://another.example/h/pc-a/api/session.list"))
        assertNull(tokenAt("http://relay.example/h/pc-a/api/session.list"))
        assertTrue(!client.followRedirects)
        assertTrue(!client.retryOnConnectionFailure)
        OkHttpClientFactory.build(profile.copy(relayHostId = "pc-b"))
        assertNull(OkHttpClientFactory.ChannelTokenRegistry.get(profile.id))
        OkHttpClientFactory.release(profile.id)
    }

    @Test
    fun proxyMapping() {
        assertNull(buildProxy(null))
        assertNull(buildProxy(ProxyConfig(type = "none")))
        val http = buildProxy(ProxyConfig(type = "http", host = "10.0.0.1", port = 8080))
        assertEquals(Proxy.Type.HTTP, http!!.type())
        val socks = buildProxy(ProxyConfig(type = "socks5", host = "127.0.0.1", port = 1080))
        assertEquals(Proxy.Type.SOCKS, socks!!.type())
    }

    @Test
    fun trustAllSslContextBuilds() {
        val ctx = trustAllSslContext()
        assertNotNull(ctx)
        assertNotNull(ctx.socketFactory)
    }

    @Test
    fun parseCaCertificateHandlesGarbage() {
        assertNull(parseCaCertificate(byteArrayOf()))
        assertNull(parseCaCertificate("not a pem".toByteArray()))
        assertNull(parseCaCertificate(byteArrayOf(0, 1, 2, 3)))
    }

    @Test
    fun mergedCaContextGarbageReturnsNull() {
        assertNull(OkHttpClientFactory.mergedCaContext("not a pem".toByteArray()))
        assertNull(OkHttpClientFactory.mergedCaContext(byteArrayOf()))
    }

    @Test
    fun mergedCaContextValidPemTrustsChain() {
        // 自签名证书（CN=dsh-remote-test.local），系统链不认识 → 由导入链接住，证明 CA 已合入信任链
        val pem = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDPjCCAiagAwIBAgIQPudwMNZu/6dEnTtgc+XR8zANBgkqhkiG9w0BAQsFADAgMR4wHAYDVQQD\n" +
            "DBVkc2gtcmVtb3RlLXRlc3QubG9jYWwwIBcNMjYwODE1MjMxMTQwWhgPMjA5ODEyMzExNjAwMDBa\n" +
            "MCAxHjAcBgNVBAMMFWRzaC1yZW1vdGUtdGVzdC5sb2NhbDCCASIwDQYJKoZIhvcNAQEBBQADggEP\n" +
            "ADCCAQoCggEBAKWbhgL++rZv3PXgYz2rfkhSwYR412dIetsVdyT6ib3xmtC+6Ig9fM9XO0MNJcYo\n" +
            "ZHg1Eb2qjaPFqAjqTDHC65FPICyTqw5A5yw8BW1pESyYQKo9VxuR3hh9iaRNFGSc1h3pLbQSvKrQ\n" +
            "DcQIz3DjRN8eJQn9EorXoHRpmLo76mXF1AoAC9reLspNKbH2sTDXUZ29hOs5pTNmPA7ZGCA0/X0m\n" +
            "9UPx19L5It2U50jwt2h0Qt4pp7y/Se56cHycczgcKJo9MKfalbMAj5rIZCtGMqHBAPGaw8oHSf1f\n" +
            "KbqBwLLuVkUVoUkrS4Gz4PmLN6IVSePRAuNm8m6Ffok5gHXidAkCAwEAAaNyMHAwDgYDVR0PAQH/\n" +
            "BAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMCBggrBgEFBQcDATAgBgNVHREEGTAXghVkc2gtcmVt\n" +
            "b3RlLXRlc3QubG9jYWwwHQYDVR0OBBYEFDHlcoLPyVpiFcVNnz0wwPSsam1gMA0GCSqGSIb3DQEB\n" +
            "CwUAA4IBAQA/h5QaDUq6bCzHD1ESo8QjjEzoRolQ4TfZLSmV7Gfn+7Pu3ll+SWwO2LXl0KnhiGj7\n" +
            "KJznH5dFuhSCG5wKf5OzAFVNjjv8NhK2BIq9nd2RKttN1lckVfR6VC+7FsLDP20x6R+gG5Vi+mBn\n" +
            "Z48lHzUZGxt2W4clm7Bs+YgWcCXo+/eUjjebiWOiwj8xu6vh+6f/YnaXBo5mS1UFDBHtkj2bdFM+\n" +
            "DivAydVIzYX0pgLbeCulEOOYVKf2mzgf3b487LHuXXqm1+omW/YOBhDRjx8+VXCW4xSuquBMGnOy\n" +
            "1lF8iUX5HNWHS80PJEzwGnNiHB500m1HuiojQyJE4RXFUgcP\n" +
            "-----END CERTIFICATE-----\n"
        val bytes = pem.toByteArray()
        val merged = OkHttpClientFactory.mergedCaContext(bytes)
        assertNotNull(merged)
        val leaf = parseCaCertificate(bytes)
        assertNotNull(leaf)
        // 不抛异常即通过：primary（系统链）拒绝后由 extra（导入 CA）接住
        merged!!.second.checkServerTrusted(arrayOf(leaf!!), "RSA")
    }
}
