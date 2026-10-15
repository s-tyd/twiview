package dev.twiview.data

import dev.twiview.msg
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNgTest {
    @Test fun `NG ユーザーの発言を弾く`() {
        val s = Settings(ngUsers = setOf("troll"))
        assertTrue(s.isNg(msg("1", login = "troll")))
        assertFalse(s.isNg(msg("2", login = "nice")))
    }

    @Test fun `NG ワードを含む発言を大文字小文字を問わず弾く`() {
        val s = Settings(ngWords = listOf("spoiler"))
        assertTrue(s.isNg(msg("1", text = "これは SPOILER です")))
        assertFalse(s.isNg(msg("2", text = "セーフ")))
    }

    @Test fun `NG が空なら何も弾かない`() {
        assertFalse(Settings().isNg(msg("1", text = "なんでも")))
    }
}
