package dev.twiview.data

import dev.twiview.msg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatBufferTest {
    private fun ev(vararg m: ChatMessage) = m.map { ChatEvent.Message(it) }

    @Test fun `届いた順に積む`() {
        val r = ChatBuffer(10).apply(emptyList(), ev(msg("1"), msg("2")))
        assertEquals(listOf("1", "2"), r.messages.map { it.id })
        assertTrue(r.changed)
    }

    @Test fun `同じ id は二重に積まない(履歴とライブの重複)`() {
        val b = ChatBuffer(10)
        val first = b.apply(emptyList(), ev(msg("1"), msg("2"))).messages
        val r = b.apply(first, ev(msg("2"), msg("3")))
        assertEquals(listOf("1", "2", "3"), r.messages.map { it.id })
    }

    @Test fun `重複だけなら変化なし`() {
        val b = ChatBuffer(10)
        val first = b.apply(emptyList(), ev(msg("1"))).messages
        assertFalse(b.apply(first, ev(msg("1"))).changed)
    }

    @Test fun `上限を超えたら古い順に捨てる`() {
        val r = ChatBuffer(3).apply(emptyList(), ev(msg("1"), msg("2"), msg("3"), msg("4"), msg("5")))
        assertEquals(listOf("3", "4", "5"), r.messages.map { it.id })
    }

    @Test fun `捨てた id は重複判定からも外れる`() {
        val b = ChatBuffer(2)
        val kept = b.apply(emptyList(), ev(msg("1"), msg("2"), msg("3"))).messages
        assertEquals(listOf("2", "3"), kept.map { it.id })
        val r = b.apply(kept, ev(msg("1")))
        assertEquals(listOf("3", "1"), r.messages.map { it.id })
    }

    @Test fun `メッセージ削除は消さずに印を付ける`() {
        val b = ChatBuffer(10)
        val list = b.apply(emptyList(), ev(msg("1"), msg("2"))).messages
        val r = b.apply(list, listOf(ChatEvent.Delete("1")))
        assertEquals(listOf(true, false), r.messages.map { it.deleted })
        assertTrue(r.changed)
    }

    @Test fun `存在しない id や削除済みの削除は変化なし`() {
        val b = ChatBuffer(10)
        val list = b.apply(emptyList(), ev(msg("1", deleted = true))).messages
        assertFalse(b.apply(list, listOf(ChatEvent.Delete("nope"))).changed)
        assertFalse(b.apply(list, listOf(ChatEvent.Delete("1"))).changed)
    }

    @Test fun `タイムアウトはそのユーザーの発言だけに印を付ける`() {
        val b = ChatBuffer(10)
        val list = b.apply(emptyList(), ev(msg("1", login = "troll"), msg("2", login = "nice"), msg("3", login = "troll"))).messages
        val r = b.apply(list, listOf(ChatEvent.ClearUser("troll")))
        assertEquals(listOf(true, false, true), r.messages.map { it.deleted })
    }

    @Test fun `全消去は反映しない`() {
        val b = ChatBuffer(10)
        val list = b.apply(emptyList(), ev(msg("1"))).messages
        val r = b.apply(list, listOf(ChatEvent.ClearAll))
        assertFalse(r.changed)
        assertEquals(1, r.messages.size)
    }

    @Test fun `お知らせはシステム行として積む`() {
        val r = ChatBuffer(10).apply(emptyList(), listOf(ChatEvent.Notice("スローモード中です")), now = { 123L })
        val m = r.messages.single()
        assertEquals("スローモード中です", m.systemText)
        assertEquals(123L, m.timestamp)
    }

    @Test fun `接続状態と自分の情報は最後に届いたものを返す`() {
        val r = ChatBuffer(10).apply(
            emptyList(),
            listOf(
                ChatEvent.Connection(true, loggedIn = false),
                ChatEvent.UserState("Me", null, emptyList()),
                ChatEvent.Connection(true, loggedIn = true),
            ),
        )
        assertEquals(ChatEvent.Connection(true, loggedIn = true), r.connection)
        assertEquals("Me", r.userState?.displayName)
        assertFalse(r.changed)
    }

    @Test fun `接続イベントが無ければ null`() {
        assertNull(ChatBuffer(10).apply(emptyList(), ev(msg("1"))).connection)
    }
}
