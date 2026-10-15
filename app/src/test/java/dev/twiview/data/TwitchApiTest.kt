package dev.twiview.data

import dev.twiview.MockServerTest
import dev.twiview.jsonResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class TwitchApiTest : MockServerTest() {
    private var webToken: String? = null
    private var rejected = 0

    private fun api() = twitchApi(webToken = { webToken }, onWebTokenRejected = { rejected++; webToken = null })

    private val streamNode = """
        {"id":"s1","title":"配信中","viewersCount":32781,"createdAt":"2026-10-13T07:13:03Z",
         "previewImageURL":"https://p/1.jpg",
         "broadcaster":{"id":"545","login":"kato","displayName":"加藤","profileImageURL":"https://a/1.png"},
         "game":{"id":"509658","name":"Just Chatting","displayName":"雑談"}}
    """

    @Test fun `人気配信を読み、Client-ID と言語を送る`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"streams":{"edges":[{"cursor":"c1","node":$streamNode}],"pageInfo":{"hasNextPage":true}}}}"""))
        val page = api().topStreams("ja", after = null)
        val s = page.items.single()
        assertEquals("s1", s.id)
        assertEquals(32781, s.viewers)
        assertEquals(Instant.parse("2026-10-13T07:13:03Z"), s.startedAt)
        assertEquals("kato", s.channel.login)
        assertEquals("加藤", s.channel.displayName)
        assertEquals("雑談", s.game?.displayName)
        assertEquals("c1", page.cursor)

        val req = server.takeRequest()
        assertEquals("kimne78kx3ncx6brgo4mv6wki5h1ko", req.getHeader("Client-ID"))
        assertNull(req.getHeader("Authorization"))
        assertTrue(req.body.readUtf8().contains("languages: [JA]"))
    }

    @Test fun `次ページが無ければ cursor は null`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"streams":{"edges":[{"cursor":"c1","node":$streamNode}],"pageInfo":{"hasNextPage":false}}}}"""))
        assertNull(api().topStreams(null, null).cursor)
    }

    @Test fun `配信者情報が欠けたノードは捨てる`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"streams":{"edges":[{"cursor":"c","node":{"id":"x","title":"t"}}],"pageInfo":{"hasNextPage":false}}}}"""))
        assertTrue(api().topStreams(null, null).items.isEmpty())
    }

    @Test fun `カテゴリ別は言語を文字列で送る`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"game":{"streams":{"edges":[],"pageInfo":{"hasNextPage":false}}}}}"""))
        api().gameStreams("Minecraft", "ja", null)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("""languages: [\"ja\"]"""))
        assertTrue(body.contains("""game(name: \"Minecraft\")"""))
    }

    @Test fun `検索語に引用符が入っても GraphQL を壊さない`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"searchFor":{"channels":{"items":[]},"games":{"items":[]}}}}"""))
        api().search("a\"b\\c")
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("""userQuery: \"a\\\"b\\\\c\""""))
    }

    @Test fun `チャンネル状態は 50件ずつ問い合わせ、存在しないユーザーは飛ばす`() = runBlocking {
        val user = """{"id":"1","login":"u","displayName":"U","profileImageURL":null,"stream":null,
            "lastBroadcast":{"startedAt":"2026-10-12T00:00:00.123Z","title":"前回"}}"""
        server.enqueue(jsonResponse("""{"data":{"users":[$user,null]}}"""))
        server.enqueue(jsonResponse("""{"data":{"users":[]}}"""))
        val logins = (1..60).map { "user$it" }
        val r = api().channels(logins + "user1")
        assertEquals(2, server.requestCount)
        val st = r.single()
        assertNull(st.stream)
        assertEquals("前回", st.lastTitle)
        assertEquals(Instant.parse("2026-10-12T00:00:00.123Z"), st.lastBroadcastAt)
    }

    @Test fun `配信中のチャンネルは配信情報にチャンネルを紐づける`() = runBlocking {
        val user = """{"id":"1","login":"u","displayName":"U","stream":{"id":"s","title":"live","viewersCount":5,"createdAt":"2026-10-13T00:00:00Z"}}"""
        server.enqueue(jsonResponse("""{"data":{"users":[$user]}}"""))
        val st = api().channels(listOf("u")).single()
        assertEquals("u", st.stream?.channel?.login)
        assertEquals(5, st.stream?.viewers)
    }

    @Test fun `処理中のアーカイブのサムネはプレースホルダを使わない`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"user":{"videos":{"edges":[
            {"cursor":"a","node":{"id":"1","title":"処理中","lengthSeconds":10,"viewCount":1,"createdAt":"2026-10-13T00:00:00Z","previewThumbnailURL":"https://vod-secure.twitch.tv/_404/404_processing_320x180.png"}},
            {"cursor":"a","node":{"id":"2","title":"完了","lengthSeconds":66101,"viewCount":743468,"createdAt":"2026-10-11T21:55:45Z","previewThumbnailURL":"https://thumb/2.jpg"}}
        ],"pageInfo":{"hasNextPage":false}}}}}"""))
        val v = api().videos("u").items
        assertNull(v[0].thumbnailUrl)
        assertEquals("https://thumb/2.jpg", v[1].thumbnailUrl)
        assertEquals(66101, v[1].lengthSeconds)
    }

    @Test fun `data が無くエラーだけならその文言で失敗する`() = runBlocking {
        server.enqueue(jsonResponse("""{"errors":[{"message":"service error"}]}"""))
        try {
            api().topGames(null); fail()
        } catch (e: TwitchException) {
            assertEquals("service error", e.message)
        }
    }

    @Test fun `HTTP エラーはステータス付きで失敗する`() = runBlocking {
        server.enqueue(jsonResponse("oops", code = 503))
        try {
            api().topGames(null); fail()
        } catch (e: HttpStatusException) {
            assertEquals(503, e.code)
        }
    }

    @Test fun `バッジはチャンネル固有がグローバルを上書きする`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{
            "badges":[{"setID":"subscriber","version":"0","imageURL":"global"},{"setID":"moderator","version":"1","imageURL":"mod"}],
            "user":{"broadcastBadges":[{"setID":"subscriber","version":"0","imageURL":"channel"}]}}}"""))
        val b = api().badges("u")
        assertEquals("channel", b["subscriber/0"])
        assertEquals("mod", b["moderator/1"])
    }

    @Test fun `再生 URL は署名とトークン付きで usher を指す`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"streamPlaybackAccessToken":{"value":"{\"channel\":\"kato\"}","signature":"sig123"}}}"""))
        val url = api().liveUrl("Kato")
        assertTrue(url, url.contains("/usher/api/channel/hls/kato.m3u8"))
        assertTrue(url.contains("sig=sig123"))
        assertTrue(url.contains("token=%7B%22channel%22%3A%22kato%22%7D"))
        assertTrue(url.contains("supported_codecs=avc1"))
    }

    @Test fun `twitch_tv にログイン中は再生トークンをその権限で取る(広告なしのため)`() = runBlocking {
        webToken = "webtok"
        server.enqueue(jsonResponse("""{"data":{"streamPlaybackAccessToken":{"value":"{}","signature":"s"}}}"""))
        api().liveUrl("kato")
        assertEquals("OAuth webtok", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `ログイン情報が拒否されたら破棄して匿名でやり直す`() = runBlocking {
        webToken = "expired"
        server.enqueue(jsonResponse("""{"error":"Unauthorized"}""", code = 401))
        server.enqueue(jsonResponse("""{"data":{"streamPlaybackAccessToken":{"value":"{}","signature":"s"}}}"""))
        val url = api().liveUrl("kato")
        assertTrue(url.contains("sig=s"))
        assertEquals(1, rejected)
        assertEquals("OAuth expired", server.takeRequest().getHeader("Authorization"))
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `401 以外の失敗ではログイン情報を捨てない`() = runBlocking {
        webToken = "ok"
        server.enqueue(jsonResponse("down", code = 500))
        try {
            api().liveUrl("kato"); fail()
        } catch (e: HttpStatusException) {
            assertEquals(500, e.code)
        }
        assertEquals(0, rejected)
    }

    @Test fun `再生トークンが返らなければ分かる文言で失敗する`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"videoPlaybackAccessToken":null}}"""))
        try {
            api().vodUrl("1"); fail()
        } catch (e: TwitchException) {
            assertEquals(dev.twiview.R.string.error_vod_unplayable, e.resId)
        }
    }

    @Test fun `フォロー一覧はページをたどって全部取る`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":[{"broadcaster_login":"a"},{"broadcaster_login":"b"}],"pagination":{"cursor":"next"}}"""))
        server.enqueue(jsonResponse("""{"data":[{"broadcaster_login":"c"}],"pagination":{}}"""))
        val r = api().followedLogins("42", "apptok", "cid")
        assertEquals(listOf("a", "b", "c"), r)
        val first = server.takeRequest()
        assertEquals("Bearer apptok", first.getHeader("Authorization"))
        assertEquals("cid", first.getHeader("Client-Id"))
        assertTrue(first.path!!.contains("user_id=42"))
        assertFalse(first.path!!.contains("after="))
        assertTrue(server.takeRequest().path!!.contains("after=next"))
    }

    @Test fun `フォロー一覧で許可切れは 401 として伝える`() = runBlocking {
        server.enqueue(jsonResponse("""{"status":401}""", code = 401))
        try {
            api().followedLogins("42", "old", "cid"); fail()
        } catch (e: HttpStatusException) {
            assertEquals(401, e.code)
        }
    }
}
