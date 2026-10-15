package dev.twiview.data

import dev.twiview.MockServerTest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRecentTest : MockServerTest() {
    private fun client() = ChatClient(OkHttpClient(), recentBase = server.url("/recent").toString().trimEnd('/'))

    @Test fun `直近ログのうちメッセージだけを古い順に読む`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"messages":[
            "@id=1;display-name=A;tmi-sent-ts=1 :a!a@a PRIVMSG #c :一つ目",
            "@target-msg-id=1 :tmi.twitch.tv CLEARMSG #c :一つ目",
            "@id=2;login=b;system-msg=sub :tmi.twitch.tv USERNOTICE #c",
            "@id=3 :c!c@c PRIVMSG #c :三つ目"
        ]}"""))
        val r = client().recent("Chan", limit = 10)
        assertEquals(listOf("1", "2", "3"), r.map { it.id })
        assertTrue(server.takeRequest().path!!.endsWith("/recent/chan?limit=10"))
    }

    @Test fun `取得に失敗しても空で返し、チャット自体は始められる`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(client().recent("c").isEmpty())
        server.enqueue(MockResponse().setBody("not json"))
        assertTrue(client().recent("c").isEmpty())
    }
}
