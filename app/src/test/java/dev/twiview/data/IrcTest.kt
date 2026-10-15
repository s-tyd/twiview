package dev.twiview.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrcTest {
    private fun event(line: String) = parseIrc(line)!!.toChatEvent()
    private fun message(line: String) = (event(line) as ChatEvent.Message).message

    @Test fun `タグ・プレフィックス・コマンド・末尾引数を分解する`() {
        val m = parseIrc("@badge-info=;color=#FF0000 :alice!alice@alice.tmi.twitch.tv PRIVMSG #chan :hello world")!!
        assertEquals("#FF0000", m.tags["color"])
        assertEquals("", m.tags["badge-info"])
        assertEquals("alice!alice@alice.tmi.twitch.tv", m.prefix)
        assertEquals("PRIVMSG", m.command)
        assertEquals(listOf("#chan", "hello world"), m.params)
    }

    @Test fun `本文に コロン付きの語が混ざっても末尾引数は最初の空白コロンで切る`() {
        val m = parseIrc(":a!a@a PRIVMSG #c :見て :) すごい")!!
        assertEquals("見て :) すごい", m.params[1])
    }

    @Test fun `タグがない行や PING も読める`() {
        val ping = parseIrc("PING :tmi.twitch.tv")!!
        assertEquals("PING", ping.command)
        assertEquals(listOf("tmi.twitch.tv"), ping.params)
        assertNull(parseIrc(""))
        assertNull(parseIrc("\r\n"))
    }

    @Test fun `タグ値のエスケープを戻す`() {
        val m = parseIrc("@system-msg=5\\sヶ月\\:\\\\ok\\nend :tmi.twitch.tv USERNOTICE #c")!!
        assertEquals("5 ヶ月;\\ok\nend", m.tags["system-msg"])
    }

    @Test fun `PRIVMSG を表示名・色・バッジ・送信時刻つきのメッセージにする`() {
        val m = message("@id=abc;user-id=42;display-name=アリス;color=#1E90FF;badges=moderator/1,subscriber/12;tmi-sent-ts=1700000000000;first-msg=1 :alice!alice@alice.tmi.twitch.tv PRIVMSG #chan :こんにちは")
        assertEquals("abc", m.id)
        assertEquals("42", m.userId)
        assertEquals("alice", m.login)
        assertEquals("アリス", m.displayName)
        assertEquals(0xFF1E90FF.toInt(), m.color)
        assertEquals(listOf("moderator/1", "subscriber/12"), m.badges)
        assertEquals(1700000000000L, m.timestamp)
        assertEquals("こんにちは", m.text)
        assertTrue(m.firstMessage)
        assertFalse(m.isAction)
    }

    @Test fun `表示名が空ならログイン名を使う`() {
        val m = message("@id=1;display-name= :bob!bob@bob PRIVMSG #c :hi")
        assertEquals("bob", m.displayName)
    }

    @Test fun `ACTION(me) は本文から制御文字を外して印を付ける`() {
        val m = message("@id=1 :bob!bob@bob PRIVMSG #c :\u0001ACTION 踊る\u0001")
        assertTrue(m.isAction)
        assertEquals("踊る", m.text)
    }

    @Test fun `エモート位置を開始順に並べて読む`() {
        val m = message("@id=1;emotes=25:6-10/1902:0-4,12-16 :a!a@a PRIVMSG #c :Keepo Kappa Keepo")
        assertEquals(
            listOf(EmoteRange("1902", 0, 4), EmoteRange("25", 6, 10), EmoteRange("1902", 12, 16)),
            m.emotes,
        )
    }

    @Test fun `壊れたエモートタグは無視する`() {
        val m = message("@id=1;emotes=25:x-y/26 :a!a@a PRIVMSG #c :hi")
        assertTrue(m.emotes.isEmpty())
    }

    @Test fun `色タグは 7文字の16進以外は無色`() {
        assertEquals(0xFF00FF00.toInt(), parseColor("#00FF00"))
        assertNull(parseColor(""))
        assertNull(parseColor("00FF00"))
        assertNull(parseColor("#GGGGGG"))
        assertNull(parseColor(null))
    }

    @Test fun `USERNOTICE はシステム文言と本人の一言を持つ`() {
        val withText = message("@id=2;login=carol;display-name=Carol;system-msg=Carol\\ssubscribed :tmi.twitch.tv USERNOTICE #c :ありがとう")
        assertEquals("Carol subscribed", withText.systemText)
        assertEquals("ありがとう", withText.text)
        assertEquals("carol", withText.login)

        val noText = message("@id=3;login=dave;system-msg=raid :tmi.twitch.tv USERNOTICE #c")
        assertEquals("raid", noText.systemText)
        assertEquals("", noText.text)
    }

    @Test fun `CLEARCHAT は対象ユーザーありなら個別、なしなら全消去`() {
        assertEquals(ChatEvent.ClearUser("troll"), event("@target-user-id=9 :tmi.twitch.tv CLEARCHAT #c :Troll"))
        assertEquals(ChatEvent.ClearAll, event(":tmi.twitch.tv CLEARCHAT #c"))
    }

    @Test fun `CLEARMSG は対象メッセージの削除`() {
        assertEquals(ChatEvent.Delete("m-1"), event("@target-msg-id=m-1 :tmi.twitch.tv CLEARMSG #c :bad"))
    }

    @Test fun `関係ないコマンドはイベントにしない`() {
        assertNull(event(":tmi.twitch.tv ROOMSTATE #c"))
        assertNull(event(":tmi.twitch.tv 001 justinfan1 :Welcome"))
    }
}
