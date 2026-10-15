package dev.twiview

import dev.twiview.data.Channel
import dev.twiview.data.ChannelStatus
import dev.twiview.data.ChatMessage
import dev.twiview.data.EmoteRange
import dev.twiview.data.Stream
import dev.twiview.data.TwitchApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import java.time.Instant

fun msg(
    id: String,
    login: String = "user",
    text: String = "hello",
    emotes: List<EmoteRange> = emptyList(),
    deleted: Boolean = false,
) = ChatMessage(
    id = id, userId = "u-$login", login = login, displayName = login, color = null,
    text = text, emotes = emotes, badges = emptyList(), timestamp = 0L, deleted = deleted,
)

fun channel(login: String, display: String = login) = Channel(id = "id-$login", login = login, displayName = display, avatarUrl = null)

fun stream(login: String, viewers: Int = 0, startedAt: Instant? = null, display: String = login) = Stream(
    id = "s-$login", title = "t", viewers = viewers, startedAt = startedAt, previewUrl = null,
    channel = channel(login, display), game = null,
)

fun offline(login: String, last: Instant? = null, display: String = login) =
    ChannelStatus(channel(login, display), stream = null, lastBroadcastAt = last, lastTitle = null)

fun jsonResponse(body: String, code: Int = 200): MockResponse = MockResponse().setResponseCode(code).setBody(body)

fun jsonNode(json: String) = Json.parseToJsonElement(json) as JsonObject

abstract class MockServerTest {
    protected lateinit var server: MockWebServer

    @Before fun startServer() { server = MockWebServer().apply { start() } }
    @After fun stopServer() { server.shutdown() }

    protected fun twitchApi(webToken: () -> String? = { null }, onWebTokenRejected: suspend () -> Unit = {}) = TwitchApi(
        OkHttpClient(),
        webToken = webToken,
        onWebTokenRejected = onWebTokenRejected,
        gqlUrl = server.url("/gql").toString(),
        helixBase = server.url("/helix").toString().trimEnd('/'),
        usherBase = server.url("/usher").toString().trimEnd('/'),
        spadeUrl = server.url("/spade").toString(),
    )
}
