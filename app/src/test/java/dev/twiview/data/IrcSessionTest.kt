package dev.twiview.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrcSessionTest {
    @Test fun `PING には PONG で応える`() {
        assertEquals(IrcAction.Pong, ircAction("PING :tmi.twitch.tv"))
    }

    @Test fun `JOIN で入室完了、RECONNECT で張り直し`() {
        assertEquals(IrcAction.Joined, ircAction(":justinfan1!justinfan1@justinfan1.tmi.twitch.tv JOIN #chan"))
        assertEquals(IrcAction.Reconnect, ircAction(":tmi.twitch.tv RECONNECT"))
    }

    @Test fun `USERSTATE から自分の表示名・色・バッジを取る`() {
        val a = ircAction("@badges=moderator/1,subscriber/3;color=#1E90FF;display-name=Me :tmi.twitch.tv USERSTATE #chan")
        assertEquals(
            IrcAction.Emit(ChatEvent.UserState("Me", 0xFF1E90FF.toInt(), listOf("moderator/1", "subscriber/3"))),
            a,
        )
    }

    @Test fun `GLOBALUSERSTATE も同じ扱い、バッジ無しは空`() {
        val a = ircAction("@color=;display-name=Me;badges= :tmi.twitch.tv GLOBALUSERSTATE")
        assertEquals(IrcAction.Emit(ChatEvent.UserState("Me", null, emptyList())), a)
    }

    @Test fun `ログイン失敗の NOTICE は認証失敗として扱う`() {
        assertEquals(IrcAction.AuthFailed, ircAction(":tmi.twitch.tv NOTICE * :Login authentication failed"))
        assertEquals(IrcAction.AuthFailed, ircAction(":tmi.twitch.tv NOTICE * :Improperly formatted auth"))
    }

    @Test fun `それ以外の NOTICE はお知らせとして流す`() {
        assertEquals(
            IrcAction.Emit(ChatEvent.Notice("This room is now in slow mode.")),
            ircAction("@msg-id=slow_on :tmi.twitch.tv NOTICE #chan :This room is now in slow mode."),
        )
    }

    @Test fun `発言・削除はチャットイベントになる`() {
        val m = ircAction("@id=1 :a!a@a PRIVMSG #chan :hi") as IrcAction.Emit
        assertEquals("hi", (m.event as ChatEvent.Message).message.text)
        assertEquals(IrcAction.Emit(ChatEvent.Delete("1")), ircAction("@target-msg-id=1 :tmi.twitch.tv CLEARMSG #chan :hi"))
    }

    @Test fun `関係ない行と空行は何もしない`() {
        assertNull(ircAction(":tmi.twitch.tv ROOMSTATE #chan"))
        assertNull(ircAction(":tmi.twitch.tv 001 me :Welcome"))
        assertNull(ircAction(""))
    }

    @Test fun `1フレームに複数行あれば届いた順に、末尾の改行は無視`() {
        val frame = ":x!x@x JOIN #chan\r\nPING :tmi.twitch.tv\r\n@id=1 :a!a@a PRIVMSG #chan :hi\r\n"
        val actions = ircActions(frame)
        assertEquals(3, actions.size)
        assertEquals(IrcAction.Joined, actions[0])
        assertEquals(IrcAction.Pong, actions[1])
        assertTrue(actions[2] is IrcAction.Emit)
    }

    @Test fun `未ログインは匿名で入室する`() {
        assertEquals(
            listOf("CAP REQ :twitch.tv/tags twitch.tv/commands", "PASS SCHMOOPIIE", "NICK justinfan12345", "JOIN #chan"),
            ircLoginLines(null, "chan", 12345),
        )
    }

    @Test fun `ログイン中は本人のトークンで入室する`() {
        assertEquals(
            listOf("CAP REQ :twitch.tv/tags twitch.tv/commands", "PASS oauth:tok", "NICK me", "JOIN #chan"),
            ircLoginLines(Account("1", "me", "tok"), "chan", 12345),
        )
    }

    @Test fun `再接続の待ちは倍々で 30秒まで`() {
        assertEquals(2_000L, reconnectDelayMs(1))
        assertEquals(4_000L, reconnectDelayMs(2))
        assertEquals(16_000L, reconnectDelayMs(4))
        assertEquals(30_000L, reconnectDelayMs(5))
        assertEquals(30_000L, reconnectDelayMs(100))
    }

    @Test fun `送信文の改行は空白にし、空なら送らない`() {
        assertEquals("PRIVMSG #chan :a  b c", outgoingLine("chan", "a\r\nb\nc"))
        assertEquals("PRIVMSG #chan :hi", outgoingLine("chan", "  hi  "))
        assertNull(outgoingLine("chan", "  \n "))
    }

    @Test fun `バッジのタグを分解する`() {
        assertEquals(listOf("a/1", "b/2"), parseBadges("a/1,b/2"))
        assertEquals(emptyList<String>(), parseBadges(""))
        assertEquals(emptyList<String>(), parseBadges(null))
    }
}
