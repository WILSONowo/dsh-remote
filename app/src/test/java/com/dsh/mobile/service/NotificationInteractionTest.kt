package com.dsh.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import com.dsh.mobile.DshApplication
import com.dsh.mobile.data.HostProfile
import com.dsh.mobile.data.SettingsStore
import com.dsh.mobile.ui.NavigationFixtureApplication
import java.net.ServerSocket
import okio.source
import okio.buffer
import java.io.OutputStream
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = NavigationFixtureApplication::class)
class NotificationInteractionTest {
    @Test fun notificationActionsAnswerOnlyTheirOriginalLiveRequest() {
        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val workers = Executors.newCachedThreadPool()
        val closeStreams = CountDownLatch(1)
        val mux = AtomicReference<OutputStream>()
        val paths = CopyOnWriteArrayList<String>()
        val replies = CopyOnWriteArrayList<JSONObject>()
        workers.execute {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                workers.execute {
                    socket.use {
                        val reader = it.getInputStream().source().buffer()
                        val path = reader.readUtf8Line()?.split(' ')?.getOrNull(1) ?: return@use
                        paths += path.substringBefore('?')
                        val headers = generateSequence { reader.readUtf8Line()?.takeIf { h -> h.isNotEmpty() } }.toList()
                        val output = it.getOutputStream()
                        when {
                            headers.any { h -> h.startsWith("Upgrade:", true) } ->
                                output.write("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n".toByteArray())
                            path.startsWith("/api/events.") -> {
                                output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n: ready\n\n".toByteArray())
                                output.flush()
                                if (path.endsWith(".mux")) mux.set(output)
                                closeStreams.await()
                            }
                            else -> {
                                if (path == "/api/respond") {
                                    val length = headers.first { h -> h.startsWith("Content-Length:", true) }.substringAfter(':').trim().toLong()
                                    replies += JSONObject(reader.readUtf8(length))
                                }
                                val payload = when {
                                    path.substringBefore('?').endsWith("/pair/check") -> """{"ok":true,"paired":true}"""
                                    path == "/api/respond" -> """{"ok":true}"""
                                    else -> """{"type":"server-response","rpcId":"fixture","result":{"ok":true,"value":{"version":"0.2.1-rc.5"}}}"""
                                }.toByteArray()
                                val status = if (path == "/api/respond" && replies.last().getString("rpcId") == "expired") "409 Conflict" else "200 OK"
                                output.write("HTTP/1.1 $status\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                output.write(payload)
                            }
                        }
                        output.flush()
                    }
                }
            }
        }
        val controller = Robolectric.buildService(DshConnectionService::class.java)
        val service = controller.get()
        val manager = service.getSystemService(NotificationManager::class.java)
        for (id in listOf(DshApplication.CHANNEL_ID, DshApplication.CHANNEL_APPROVALS))
            manager.createNotificationChannel(NotificationChannel(id, id, NotificationManager.IMPORTANCE_HIGH))
        val settings = SettingsStore(service)
        val profile = HostProfile("notify-a", "Computer A", "http://127.0.0.1:${server.localPort}", paired = true)
        fun waitFor(condition: () -> Boolean) {
            val deadline = System.nanoTime() + 5_000_000_000
            do {
                shadowOf(Looper.getMainLooper()).idle()
                if (condition()) return
                Thread.sleep(10)
            } while (System.nanoTime() < deadline)
            fail("Timed out: fixture paths=$paths, notifications=" + manager.activeNotifications.map { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT) })
        }
        fun frame(rpc: String, content: String) {
            val data = """{"type":"server-request","rpcId":"$rpc","method":"events.mux","payload":{"sessionId":"session","$content}}"""
            mux.get().apply { write("data: $data\n\n".toByteArray()); flush() }
        }
        fun alerts() = manager.activeNotifications.filter { it.tag?.startsWith("dsh-interaction/") == true }
        fun alertWith(text: String): Notification {
            fun find() = alerts().firstOrNull { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains(text) }
            waitFor { find() != null }
            return find()!!.notification
        }
        fun click(action: Notification.Action, answer: String? = null) {
            val intent = Intent(shadowOf(action.actionIntent).savedIntent)
            assertEquals(DshConnectionService::class.java.name, intent.component!!.className)
            if (answer != null) RemoteInput.addResultsToIntent(action.remoteInputs, intent, Bundle().apply { putCharSequence(REPLY_TEXT, answer) })
            service.onStartCommand(intent, 0, 2)
        }
        fun approval(rpc: String, tool: String) =
            frame(rpc, """type":"approval/requested","approvalId":"$rpc","toolName":"$tool" """)
        fun question(rpc: String, prompt: String) =
            frame(rpc, """type":"question/requested","questions":[{"id":"q","question":"$prompt"}]""")
        try {
            runBlocking {
                settings.setBackgroundNotify(true); settings.setNotifyApprovals(true); settings.setBiometricLock(false)
                settings.upsertProfile(profile); settings.setActiveProfile(profile.id)
            }
            controller.create()
            service.onStartCommand(null, 0, 1)
            waitFor { mux.get() != null }
            approval("first", "read")
            val allow = alertWith("read").actions.single { it.title == "允许一次" }
            click(allow); click(allow)
            waitFor { replies.size == 1 }
            assertEquals("first", replies.single().getString("rpcId"))
            assertEquals("allowed-once", replies.single().getJSONObject("result").getJSONObject("value").getString("outcome"))
            approval("danger", "bash")
            val risky = alertWith("bash")
            assertEquals(listOf("拒绝"), risky.actions.map { it.title })
            click(risky.actions.single())
            waitFor { replies.size == 2 }
            assertEquals("rejected", replies.last().getJSONObject("result").getJSONObject("value").getString("outcome"))
            question("old", "first question")
            val old = alertWith("first question").actions.single()
            question("new", "next question")
            val reply = alertWith("next question").actions.single()
            click(old, "stale"); click(reply, "hello"); click(reply, "duplicate")
            waitFor { replies.size == 3 }
            val body = replies.last()
            assertEquals("new", body.getString("rpcId"))
            val value = body.getJSONObject("result").getJSONObject("value")
            assertEquals("session", value.getString("sessionId"))
            val answer = value.getJSONObject("answer").getJSONArray("answers").getJSONObject(0)
            assertEquals("q", answer.getString("id"))
            assertEquals("hello", answer.getString("custom"))
            assertEquals(0, answer.getJSONArray("selected").length())
            frame("batch", """type":"question/requested","questions":[{"id":"one","question":"pick","multiSelect":true,"options":[{"label":"red"},{"label":"blue"}]},{"id":"two","question":"comment"}]""")
            val pick = alertWith("pick").actions.single()
            click(pick, "red")
            val selected = alertWith("red")
            click(selected.actions.first(), "blue")
            click(alertWith("blue").actions.single { it.title == "提交选择" })
            assertEquals(3, replies.size)
            click(alertWith("comment").actions.single(), "done")
            waitFor { replies.size == 4 }
            val batch = replies.last().getJSONObject("result").getJSONObject("value").getJSONObject("answer").getJSONArray("answers")
            assertEquals("""["red","blue"]""", batch.getJSONObject(0).getJSONArray("selected").toString())
            assertEquals("done", batch.getJSONObject(1).getString("custom"))
            approval("expired", "lookup")
            val expired = alertWith("lookup").actions.first()
            click(expired)
            waitFor { org.robolectric.shadows.ShadowToast.getTextOfLatestToast() == "无法确认处理结果，请查看待办" }
            click(expired)
            approval("resolved", "inspect")
            val stale = alertWith("inspect").actions.first()
            frame("resolved", """type":"approval/resolved","approvalId":"resolved" """)
            waitFor { alerts().isEmpty() }
            click(stale)
            approval("switch", "view")
            val switched = alertWith("view").actions.first()
            runBlocking { settings.setActiveProfile("notify-b") }
            waitFor { alerts().isEmpty() }
            click(switched)
            val previousMux = mux.get()
            runBlocking { settings.setActiveProfile(profile.id) }
            waitFor { mux.get() !== previousMux }
            approval("disconnect", "fetch")
            val disconnected = alertWith("fetch").actions.first()
            mux.get().close()
            waitFor { alerts().isEmpty() }
            click(disconnected)
            controller.destroy()
            click(switched)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf("first", "danger", "new", "batch", "expired"), replies.map { it.getString("rpcId") })
        } finally {
            closeStreams.countDown()
            server.close()
            workers.shutdownNow()
        }
    }
}
