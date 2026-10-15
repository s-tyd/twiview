package dev.twiview.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSessionTest {
    @Test fun `Cookie 文字列から auth-token を取り出す`() {
        val c = "unique_id=abc; auth-token=tok123; login=me"
        assertEquals("tok123", WebSession.tokenFromCookies(c))
    }

    @Test fun `auth-token が無い・空・Cookie 自体が無いときは null`() {
        assertNull(WebSession.tokenFromCookies("unique_id=abc; login=me"))
        assertNull(WebSession.tokenFromCookies("auth-token="))
        assertNull(WebSession.tokenFromCookies(null))
    }

    @Test fun `似た名前の Cookie と取り違えない`() {
        assertEquals("real", WebSession.tokenFromCookies("x-auth-token=fake; auth-token=real"))
    }

    @Test fun `暗黙フローのリダイレクトからアクセストークンを取り出す`() {
        val url = "https://localhost#access_token=apptok&scope=user%3Aread%3Afollows&token_type=bearer"
        assertEquals("apptok", WebSession.appTokenFromRedirect(url))
    }

    @Test fun `拒否されたリダイレクトや断片なしは null`() {
        assertNull(WebSession.appTokenFromRedirect("https://localhost?error=access_denied&error_description=x"))
        assertNull(WebSession.appTokenFromRedirect("https://localhost"))
    }

    @Test fun `戻り先の判定は localhost 本体だけ通す`() {
        assertTrue(WebSession.isAppRedirect("https://localhost"))
        assertTrue(WebSession.isAppRedirect("https://localhost#access_token=x"))
        assertTrue(WebSession.isAppRedirect("https://localhost/?error=access_denied"))
        assertFalse(WebSession.isAppRedirect("https://localhost.example.com#access_token=x"))
        assertFalse(WebSession.isAppRedirect("https://www.twitch.tv/"))
    }

    @Test fun `許可 URL は登録した Client ID・リダイレクト先・フォロー権限を含む`() {
        val u = WebSession.appAuthorizeUrl("abc123")
        assertTrue(u.startsWith("https://id.twitch.tv/oauth2/authorize?"))
        assertTrue("client_id=abc123" in u)
        assertTrue("redirect_uri=https%3A%2F%2Flocalhost" in u)
        assertTrue("response_type=token" in u)
        assertTrue("scope=user%3Aread%3Afollows" in u)
    }

    @Test fun `Client ID は英小文字と数字の30文字だけ通す`() {
        assertTrue(WebSession.isValidClientId("kimne78kx3ncx6brgo4mv6wki5h1ko"))
        assertFalse(WebSession.isValidClientId("kimne78kx3ncx6brgo4mv6wki5h1k"))
        assertFalse(WebSession.isValidClientId("kimne78kx3ncx6brgo4mv6wki5h1koX"))
        assertFalse(WebSession.isValidClientId("KIMNE78KX3NCX6BRGO4MV6WKI5H1KO"))
        assertFalse(WebSession.isValidClientId(" kimne78kx3ncx6brgo4mv6wki5h1ko"))
        assertFalse(WebSession.isValidClientId(""))
    }
}
