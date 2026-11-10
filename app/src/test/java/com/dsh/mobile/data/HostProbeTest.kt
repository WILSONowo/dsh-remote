package com.dsh.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HostProbeTest {

    @Test
    fun relayPresenceResponses() {
        assertEquals(Presence.ONLINE, HostProbe.parsePresence(200, """{"online":true}"""))
        assertEquals(Presence.OFFLINE, HostProbe.parsePresence(200, """{"online":false}"""))
        // 旧中转没有这个接口
        assertEquals(Presence.UNKNOWN, HostProbe.parsePresence(404, """{"error":"not_found"}"""))
        assertEquals(Presence.UNKNOWN, HostProbe.parsePresence(429, null))
        assertEquals(Presence.UNKNOWN, HostProbe.parsePresence(200, "garbage"))
    }

    @Test
    fun labels() {
        assertEquals("检测中…", HostProbe.label(null))
        assertEquals("在线 · 35 ms", HostProbe.label(ProbeResult(Presence.ONLINE, 35)))
        assertEquals("离线", HostProbe.label(ProbeResult(Presence.OFFLINE)))
        assertEquals("状态未知", HostProbe.label(ProbeResult(Presence.UNKNOWN)))
    }
}
