package dev.twiview.data

import dev.twiview.MockServerTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class ChatConnectionTest : MockServerTest() {
    private val http = OkHttpClient()

    private class FakeIrc(
        private val onLine: (WebSocket, String) -> Unit = { _, _ -> },
    ) : WebSocketListener() {
        val received = CopyOnWriteArrayList<String>()
        val privmsg = CompletableDeferred<String>()
        val pong = CompletableDeferred<String>()
        override fun onMessage(webSocket: WebSocket, text: String) {
            received += text
            if (text.startsWith("PRIVMSG")) privmsg.complete(text)
            if (text.startsWith("PONG")) pong.complete(text)
            onLine(webSocket, text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }
    }

    private fun serve(irc: FakeIrc) = server.enqueue(MockResponse().withWebSocketUpgrade(irc))

    private fun client() = ChatClient(http, ircUrl = server.url("/").toString())

    private fun <T> collecting(conn: ChatConnection, block: suspend (ReceiveChannel<ChatEvent>) -> T): T = runBlocking {
        val events = Channel<ChatEvent>(Channel.UNLIMITED)
        val job = launch { conn.events().collect { events.send(it) } }
        try {
            withTimeout(10_000) { block(events) }
        } finally {
            job.cancelAndJoin()
        }
    }

    private val joinReply = ":justinfan1!justinfan1@justinfan1.tmi.twitch.tv JOIN #chan\r\n"

    @Test fun `匿名で入室し、届いた発言を流す。匿名は書き込めない`() {
        val irc = FakeIrc { ws, line ->
            if (line.startsWith("JOIN")) ws.send(joinReply + "@id=m1;display-name=A;tmi-sent-ts=5 :a!a@a PRIVMSG #chan :hi\r\n")
        }
        serve(irc)
        val conn = client().connect("Chan", credentials = { null }, onAuthFailed = { error("呼ばれない") })
        collecting(conn) { ev ->
            assertEquals(ChatEvent.Connection(true, loggedIn = false), ev.receive())
            assertFalse(conn.send("x"))
            assertEquals("hi", (ev.receive() as ChatEvent.Message).message.text)
        }
        assertEquals("CAP REQ :twitch.tv/tags twitch.tv/commands", irc.received[0])
        assertEquals("PASS SCHMOOPIIE", irc.received[1])
        assertTrue(irc.received[2], irc.received[2].startsWith("NICK justinfan"))
        assertEquals("JOIN #chan", irc.received[3])
    }

    @Test fun `ログイン中は本人として入室し、書き込める`() {
        val irc = FakeIrc { ws, line -> if (line.startsWith("JOIN")) ws.send(joinReply) }
        serve(irc)
        val conn = client().connect("chan", credentials = { Account("1", "me", "tok") }, onAuthFailed = { error("呼ばれない") })
        collecting(conn) { ev ->
            assertEquals(ChatEvent.Connection(true, loggedIn = true), ev.receive())
            assertTrue(conn.send("hello world"))
            assertEquals("PRIVMSG #chan :hello world", irc.privmsg.await())
        }
        assertEquals("PASS oauth:tok", irc.received[1])
        assertEquals("NICK me", irc.received[2])
    }

    @Test fun `PING には PONG を返す`() {
        val irc = FakeIrc { ws, line -> if (line.startsWith("JOIN")) ws.send("PING :tmi.twitch.tv\r\n") }
        serve(irc)
        val conn = client().connect("chan", credentials = { null }, onAuthFailed = {})
        collecting(conn) { assertEquals("PONG :tmi.twitch.tv", irc.pong.await()) }
    }

    @Test fun `ログイン情報が拒否されたら破棄を知らせ、待たずに匿名で入り直す`() {
        var account: Account? = Account("1", "me", "bad")
        var authFailed = 0
        serve(FakeIrc { ws, line -> if (line.startsWith("NICK")) ws.send(":tmi.twitch.tv NOTICE * :Login authentication failed\r\n") })
        val second = FakeIrc { ws, line -> if (line.startsWith("JOIN")) ws.send(joinReply) }
        serve(second)
        val conn = client().connect("chan", credentials = { account }, onAuthFailed = { authFailed++; account = null })
        collecting(conn) { ev ->
            assertEquals(ChatEvent.Connection(false), ev.receive())
            assertEquals(ChatEvent.Connection(true, loggedIn = false), ev.receive())
        }
        assertEquals(1, authFailed)
        assertEquals("PASS SCHMOOPIIE", second.received[1])
    }

    @Test fun `サーバーに切られたら 2秒後に張り直す`() {
        serve(FakeIrc { ws, line -> if (line.startsWith("JOIN")) { ws.send(joinReply); ws.close(1000, "bye") } })
        serve(FakeIrc { ws, line -> if (line.startsWith("JOIN")) ws.send(joinReply) })
        val conn = client().connect("chan", credentials = { null }, onAuthFailed = {})
        val started = System.currentTimeMillis()
        collecting(conn) { ev ->
            assertEquals(ChatEvent.Connection(true), ev.receive())
            assertEquals(ChatEvent.Connection(false), ev.receive())
            assertEquals(ChatEvent.Connection(true), ev.receive())
        }
        assertTrue(System.currentTimeMillis() - started >= 2_000)
        assertEquals(2, server.requestCount)
    }

    @Test fun `RECONNECT が来たら自分から切って張り直す`() {
        serve(FakeIrc { ws, line -> if (line.startsWith("JOIN")) ws.send(joinReply + ":tmi.twitch.tv RECONNECT\r\n") })
        serve(FakeIrc { ws, line -> if (line.startsWith("JOIN")) ws.send(joinReply) })
        val conn = client().connect("chan", credentials = { null }, onAuthFailed = {})
        collecting(conn) { ev ->
            assertEquals(ChatEvent.Connection(true), ev.receive())
            assertEquals(ChatEvent.Connection(false), ev.receive())
            assertEquals(ChatEvent.Connection(true), ev.receive())
        }
        assertEquals(2, server.requestCount)
    }
}
