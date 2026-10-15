package dev.twiview.data

import dev.twiview.jsonNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VodCommentTest {
    private val full = """
        {"id":"c1","contentOffsetSeconds":125,"createdAt":"2026-10-13T00:00:10Z",
         "commenter":{"id":"42","login":"alice","displayName":"アリス"},
         "message":{"userColor":"#FF0000",
           "fragments":[{"text":"見て "},{"text":"Kappa","emote":{"emoteID":"25"}},{"text":" 😀 "},{"text":"PogChamp","emote":{"emoteID":"305954156"}}],
           "userBadges":[{"setID":"subscriber","version":"12"},{"setID":"","version":"1"}]}}
    """

    @Test fun `断片をつなぎ、エモート位置をコードポイント単位で終端込みにする`() {
        val c = parseVodComment(jsonNode(full))!!
        assertEquals(125, c.offsetSec)
        assertEquals("見て Kappa 😀 PogChamp", c.message.text)
        assertEquals(listOf(EmoteRange("25", 3, 7), EmoteRange("305954156", 11, 18)), c.message.emotes)
    }

    @Test fun `送信者・色・バッジ・時刻を読む(空の setID は捨てる)`() {
        val m = parseVodComment(jsonNode(full))!!.message
        assertEquals("c1", m.id)
        assertEquals("42", m.userId)
        assertEquals("alice", m.login)
        assertEquals("アリス", m.displayName)
        assertEquals(0xFFFF0000.toInt(), m.color)
        assertEquals(listOf("subscriber/12"), m.badges)
        assertEquals(1791849610000L, m.timestamp)
    }

    @Test fun `送信者が消えていてもコメントは出す`() {
        val c = parseVodComment(jsonNode("""{"id":"c","contentOffsetSeconds":1,"message":{"fragments":[{"text":"x"}]}}"""))!!
        assertEquals("", c.message.login)
        assertEquals("", c.message.displayName)
        assertEquals("x", c.message.text)
        assertEquals(0L, c.message.timestamp)
    }

    @Test fun `id・位置・本文のどれかが無ければ捨てる`() {
        assertNull(parseVodComment(jsonNode("""{"contentOffsetSeconds":1,"message":{"fragments":[]}}""")))
        assertNull(parseVodComment(jsonNode("""{"id":"c","message":{"fragments":[]}}""")))
        assertNull(parseVodComment(jsonNode("""{"id":"c","contentOffsetSeconds":1}""")))
    }

    @Test fun `本文が空のエモート断片には範囲を付けない`() {
        val c = parseVodComment(jsonNode("""{"id":"c","contentOffsetSeconds":1,"message":{"fragments":[{"text":"","emote":{"emoteID":"1"}},{"text":"a"}]}}"""))!!
        assertEquals("a", c.message.text)
        assertEquals(emptyList<EmoteRange>(), c.message.emotes)
    }
}
