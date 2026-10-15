package dev.twiview.data

import dev.twiview.MockServerTest
import dev.twiview.jsonResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.URLDecoder
import java.util.Base64

class TwitchApiExtraTest : MockServerTest() {
    @Test fun `過去配信のコメントは再生位置を指定して取り、送信者と本文を読む`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"video":{"comments":{"edges":[
            {"node":{"id":"c1","contentOffsetSeconds":120,"createdAt":"2026-10-13T00:00:00Z","commenter":{"id":"1","login":"a","displayName":"A"},"message":{"userColor":null,"fragments":[{"text":"hi"}],"userBadges":[]}}},
            {"node":{"id":"bad"}}
        ]}}}}"""))
        val r = twitchApi().vodComments("2898313736", 120)
        assertEquals(1, r.size)
        assertEquals(120, r[0].offsetSec)
        assertEquals("hi", r[0].message.text)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("comments(contentOffsetSeconds: 120)"))
        assertTrue(body, body.contains("""video(id: \"2898313736\")"""))
    }

    @Test fun `クリップ一覧は期間を付けて 100 件を一度に取る`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"user":{"clips":{"edges":[{"cursor":"MQ==","node":{"slug":"s1","title":"t","viewCount":5,"durationSeconds":30,"createdAt":"2026-10-13T00:00:00Z",
            "curator":{"login":"c","displayName":"C"},"broadcaster":{"id":"1","login":"b","displayName":"B"},"videoOffsetSeconds":7885,"video":{"id":"v9"}}}],"pageInfo":{"hasNextPage":true}}}}}"""))
        val page = twitchApi().clips("b", ClipPeriod.Week)
        assertEquals(listOf("s1"), page.items.map { it.slug })
        assertEquals("C", page.items[0].curator)
        assertEquals("b", page.items[0].channel?.login)
        assertEquals("v9", page.items[0].videoId)
        assertEquals(7885, page.items[0].videoOffsetSec)
        assertNull(page.cursor)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("period: LAST_WEEK, sort: VIEWS_DESC"))
        assertTrue(body, body.contains("first: 100"))
    }

    @Test fun `クリップの再生 URL は最初の画質に署名とトークンを付ける`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"clip":{"slug":"s1","title":"t","viewCount":1,"durationSeconds":10,
            "videoQualities":[{"sourceURL":"https://cdn.example/a.mp4"},{"sourceURL":"https://cdn.example/b.mp4"}],
            "playbackAccessToken":{"signature":"sig","value":"{\"a\":1}"}}}}"""))
        val (clip, url) = twitchApi().clip("s1")!!
        assertEquals("s1", clip.slug)
        assertEquals("https://cdn.example/a.mp4?sig=sig&token=%7B%22a%22%3A1%7D", url)
    }

    @Test fun `動画の詳細はミュート区間を読む(無ければ空)`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"video":{"id":"v1","title":"t","lengthSeconds":100,"viewCount":1,"createdAt":"2026-10-13T00:00:00Z",
            "owner":{"id":"1","login":"a","displayName":"A"},
            "muteInfo":{"mutedSegmentConnection":{"nodes":[{"duration":180,"offset":0},{"duration":60,"offset":3000}]}}}}}"""))
        val (v, _) = twitchApi().video("v1")!!
        assertEquals(listOf(MutedSegment(0, 180), MutedSegment(3000, 60)), v.mutedSegments)
        assertEquals(true, 100L in v.mutedSegments[0])
        assertEquals(false, 180L in v.mutedSegments[0])
        server.enqueue(jsonResponse("""{"data":{"video":{"id":"v2","title":"t","lengthSeconds":100,"viewCount":1,"muteInfo":{"mutedSegmentConnection":null}}}}"""))
        assertEquals(emptyList<MutedSegment>(), twitchApi().video("v2")!!.first.mutedSegments)
    }

    @Test fun `チャンネルポイントは残高・受け取れるボーナス・独自の名前とアイコンを読む`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"user":{"channel":{
            "self":{"communityPoints":{"balance":1920000,"availableClaim":{"id":"claim-1"}}},
            "communityPointsSettings":{"isEnabled":true,"name":"ナオコイン","image":{"url2x":"https://i/2x.png"}}}}}}"""))
        val p = twitchApi().channelPoints("kato", "tok")!!
        assertEquals(1_920_000L, p.balance)
        assertEquals("claim-1", p.claimId)
        assertEquals("ナオコイン", p.name)
        assertEquals("https://i/2x.png", p.iconUrl)
        assertEquals("OAuth tok", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `ポイント名が空なら既定の名前を使えるよう null にする`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"user":{"channel":{
            "self":{"communityPoints":{"balance":5,"availableClaim":null}},
            "communityPointsSettings":{"isEnabled":true,"name":"","image":null}}}}}"""))
        val p = twitchApi().channelPoints("kato", "tok")!!
        assertNull(p.claimId)
        assertNull(p.name)
        assertNull(p.iconUrl)
    }

    @Test fun `ポイントが無効なチャンネルや未ログインでは null`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"user":{"channel":{"self":{"communityPoints":{"balance":1}},"communityPointsSettings":{"isEnabled":false}}}}}"""))
        assertNull(twitchApi().channelPoints("kato", "tok"))
        server.enqueue(jsonResponse("""{"data":{"user":{"channel":{"self":null,"communityPointsSettings":{"isEnabled":true}}}}}"""))
        assertNull(twitchApi().channelPoints("kato", "tok"))
        server.enqueue(jsonResponse("""{"data":{"user":null}}"""))
        assertNull(twitchApi().channelPoints("nobody", "tok"))
    }

    @Test fun `視聴報告は公式プレイヤーと同じ形(base64 の JSON)で送る`() = runBlocking {
        server.enqueue(jsonResponse(""))
        twitchApi().sendMinuteWatched("545", "316", "kato", "74")
        val req = server.takeRequest()
        assertEquals("/spade", req.path)
        val form = req.body.readUtf8()
        val data = URLDecoder.decode(form.substringAfter("data="), "UTF-8")
        val events = Json.parseToJsonElement(String(Base64.getDecoder().decode(data))) as JsonArray
        val e = events.single() as JsonObject
        assertEquals("minute-watched", (e["event"] as JsonPrimitive).content)
        val p = e["properties"] as JsonObject
        assertEquals("545", (p["channel_id"] as JsonPrimitive).content)
        assertEquals("316", (p["broadcast_id"] as JsonPrimitive).content)
        assertEquals("kato", (p["channel"] as JsonPrimitive).content)
        assertEquals("74", (p["user_id"] as JsonPrimitive).content)
        assertEquals("true", (p["live"] as JsonPrimitive).content)
    }

    @Test fun `視聴報告が拒否されたらステータス付きで失敗する`() = runBlocking {
        server.enqueue(jsonResponse("", code = 403))
        try {
            twitchApi().sendMinuteWatched("1", "2", "a", "3"); fail()
        } catch (e: HttpStatusException) {
            assertEquals(403, e.code)
        }
    }

    @Test fun `過去配信1本の情報と配信者を読む`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"video":{"id":"1","title":"t","lengthSeconds":10,"viewCount":5,"createdAt":"2026-10-13T00:00:00Z","previewThumbnailURL":"https://t/1.jpg","game":{"id":"g","name":"G","displayName":"ゲーム"},
            "owner":{"id":"9","login":"kato","displayName":"加藤","profileImageURL":"https://a.png"}}}}"""))
        val (v, owner) = twitchApi().video("1")!!
        assertEquals("t", v.title)
        assertEquals("ゲーム", v.game?.displayName)
        assertEquals("kato", owner?.login)
        assertEquals("加藤", owner?.displayName)
    }

    @Test fun `存在しない過去配信は null`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"video":null}}"""))
        assertNull(twitchApi().video("0"))
    }

    @Test fun `チャンネル1件は自己紹介も読み、存在しなければ null`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"user":{"id":"1","login":"u","displayName":"U","description":"感謝します。","stream":null,"lastBroadcast":null}}}"""))
        val st = twitchApi().channel("u")!!
        assertEquals("感謝します。", st.description)
        assertNull(st.stream)
        assertNull(st.lastBroadcastAt)
        server.enqueue(jsonResponse("""{"data":{"user":null}}"""))
        assertNull(twitchApi().channel("nobody"))
    }

    @Test fun `検索はチャンネルとカテゴリを分けて返す`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"searchFor":{
            "channels":{"items":[{"id":"1","login":"a","displayName":"A","stream":{"id":"s","title":"live","viewersCount":7,"createdAt":"2026-10-13T00:00:00Z"}},{"id":"2","login":"b","displayName":"B","stream":null}]},
            "games":{"items":[{"id":"g","name":"Minecraft","displayName":"マイクラ","viewersCount":100,"boxArtURL":"https://b.jpg"}]}}}}"""))
        val r = twitchApi().search("a")
        assertEquals(listOf("a", "b"), r.channels.map { it.channel.login })
        assertEquals(7, r.channels[0].stream?.viewers)
        assertNull(r.channels[1].stream)
        assertEquals("マイクラ", r.games.single().displayName)
        assertEquals(100, r.games.single().viewers)
    }

    @Test fun `人気カテゴリを読む`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"games":{"edges":[{"cursor":"c","node":{"id":"509658","name":"Just Chatting","displayName":"雑談","viewersCount":206658,"boxArtURL":"https://x.jpg"}}],"pageInfo":{"hasNextPage":false}}}}"""))
        val p = twitchApi().topGames(null)
        val g = p.items.single()
        assertEquals("Just Chatting", g.name)
        assertEquals("雑談", g.displayName)
        assertEquals(206658, g.viewers)
        assertEquals("https://x.jpg", g.boxArtUrl)
        assertNull(p.cursor)
    }

    @Test fun `アーカイブ一覧は1回で100件取り、続きのカーソルは返さない`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"user":{"videos":{"edges":[{"cursor":"k1","node":{"id":"1","title":"a"}}],"pageInfo":{"hasNextPage":true}}}}}"""))
        val p = twitchApi().videos("u")
        assertEquals(listOf("1"), p.items.map { it.id })
        assertNull(p.cursor)
        val q = server.takeRequest().body.readUtf8()
        assertTrue(q, q.contains("first: 100"))
        assertTrue(q, q.contains("type: ARCHIVE"))
    }
}
