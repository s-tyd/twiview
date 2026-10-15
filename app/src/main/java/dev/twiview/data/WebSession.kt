package dev.twiview.data

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.twiview.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

private val Context.webStore by preferencesDataStore("web_session")

data class Account(
    val userId: String,
    val login: String,
    val accessToken: String,
    val appToken: String? = null,
)

class WebSession(private val context: Context, private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<Account?>(null)
    val state: StateFlow<Account?> = _state

    private val _clientId = MutableStateFlow(BuildConfig.TWITCH_CLIENT_ID.ifBlank { null })
    val clientId: StateFlow<String?> = _clientId

    private object K {
        val token = stringPreferencesKey("auth_token")
        val login = stringPreferencesKey("login")
        val userId = stringPreferencesKey("user_id")
        val appToken = stringPreferencesKey("app_token")
        val clientId = stringPreferencesKey("client_id")
        val session = listOf(token, login, userId, appToken)
    }

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val p = context.webStore.data.first()
            p[K.clientId]?.let { _clientId.value = it }
            val stored = p[K.token] ?: return@launch
            val t = TokenCipher.decrypt(stored) ?: run {
                context.webStore.edit { e -> K.session.forEach { e.remove(it) } }
                return@launch
            }
            val appToken = p[K.appToken]?.let(TokenCipher::decrypt)
            _state.value = Account(p[K.userId].orEmpty(), p[K.login].orEmpty(), t, appToken)
            if (!TokenCipher.isEncrypted(stored) || p[K.appToken]?.let(TokenCipher::isEncrypted) == false) {
                context.webStore.edit {
                    it[K.token] = TokenCipher.encrypt(t)
                    if (appToken != null) it[K.appToken] = TokenCipher.encrypt(appToken) else it.remove(K.appToken)
                }
            }
            if (p[K.userId].isNullOrEmpty()) {
                runCatching { save(t) }.getOrNull()?.let { fresh -> _state.value = fresh.copy(appToken = appToken) }
            }
        }
    }

    fun token(): String? = _state.value?.accessToken

    suspend fun save(token: String): Account? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://id.twitch.tv/oauth2/validate")
            .header("Authorization", "OAuth $token").build()
        val v = http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@withContext null
            json.parseToJsonElement(res.body!!.string()) as JsonObject
        }
        val acc = Account(v.str("user_id").orEmpty(), v.str("login").orEmpty(), token)
        context.webStore.edit {
            it[K.token] = TokenCipher.encrypt(token)
            it[K.login] = acc.login
            it[K.userId] = acc.userId
        }
        acc.also { _state.value = it }
    }

    suspend fun setAppToken(token: String?) {
        context.webStore.edit { if (token == null) it.remove(K.appToken) else it[K.appToken] = TokenCipher.encrypt(token) }
        _state.value = _state.value?.copy(appToken = token)
    }

    suspend fun setClientId(id: String?) {
        if (id == _clientId.value) return
        context.webStore.edit { if (id == null) it.remove(K.clientId) else it[K.clientId] = id }
        _clientId.value = id
        setAppToken(null)
    }

    suspend fun clear() {
        context.webStore.edit { e -> K.session.forEach { e.remove(it) } }
        _state.value = null
        withContext(Dispatchers.Main) {
            CookieManager.getInstance().removeAllCookies(null)
        }
    }

    companion object {
        const val APP_REDIRECT = "https://localhost"

        fun isAppRedirect(url: String): Boolean =
            url == APP_REDIRECT || listOf("/", "#", "?").any { url.startsWith(APP_REDIRECT + it) }

        private val clientIdRe = Regex("[a-z0-9]{30}")

        fun isValidClientId(id: String) = clientIdRe.matches(id)

        fun appAuthorizeUrl(clientId: String): String =
            "https://id.twitch.tv/oauth2/authorize?client_id=$clientId" +
                "&redirect_uri=${URLEncoder.encode(APP_REDIRECT, "UTF-8")}" +
                "&response_type=token&scope=${URLEncoder.encode("user:read:follows", "UTF-8")}"

        fun appTokenFromRedirect(url: String): String? =
            url.substringAfter('#', "").split('&')
                .firstOrNull { it.startsWith("access_token=") }
                ?.substringAfter('=')
                ?.takeIf { it.isNotBlank() }

        fun tokenFromCookies(cookies: String?): String? =
            cookies?.split(';')?.map { it.trim() }
                ?.firstOrNull { it.startsWith("auth-token=") }
                ?.substringAfter("auth-token=")
                ?.takeIf { it.isNotBlank() }
    }
}
