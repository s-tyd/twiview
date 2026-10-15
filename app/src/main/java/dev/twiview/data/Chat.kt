package dev.twiview.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel as KChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import kotlin.math.min

data class EmoteRange(val id: String, val start: Int, val end: Int)

data class ChatMessage(
    val id: String,
    val userId: String,
    val login: String,
    val displayName: String,
    val color: Int?,
    val text: String,
    val emotes: List<EmoteRange>,
    val badges: List<String>,
    val timestamp: Long,
    val isAction: Boolean = false,
    val systemText: String? = null,
    val deleted: Boolean = false,
    val firstMessage: Boolean = false,
)

sealed interface ChatEvent {
    data class Message(val message: ChatMessage) : ChatEvent
    data class ClearUser(val login: String) : ChatEvent
    data object ClearAll : ChatEvent
    data class Delete(val messageId: String) : ChatEvent
    data class Connection(val connected: Boolean, val loggedIn: Boolean = false) : ChatEvent
    data class UserState(val displayName: String?, val color: Int?, val badges: List<String>) : ChatEvent
    data class Notice(val text: String) : ChatEvent
}

data class IrcMessage(
    val tags: Map<String, String>,
    val prefix: String?,
    val command: String,
    val params: List<String>,
)

fun parseIrc(raw: String): IrcMessage? {
    var rest = raw.trimEnd('\r', '\n')
    if (rest.isEmpty()) return null
    var tags = emptyMap<String, String>()
    if (rest.startsWith('@')) {
        val sp = rest.indexOf(' ')
        if (sp < 0) return null
        tags = parseTags(rest.substring(1, sp))
        rest = rest.substring(sp + 1)
    }
    var prefix: String? = null
    if (rest.startsWith(':')) {
        val sp = rest.indexOf(' ')
        if (sp < 0) return null
        prefix = rest.substring(1, sp)
        rest = rest.substring(sp + 1)
    }
    val trailingAt = rest.indexOf(" :")
    val head = if (trailingAt >= 0) rest.substring(0, trailingAt) else rest
    val parts = head.split(' ').filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val params = parts.drop(1).toMutableList()
    if (trailingAt >= 0) params += rest.substring(trailingAt + 2)
    return IrcMessage(tags, prefix, parts[0], params)
}

private fun parseTags(s: String): Map<String, String> {
    val out = HashMap<String, String>()
    for (pair in s.split(';')) {
        val eq = pair.indexOf('=')
        if (eq < 0) out[pair] = "" else out[pair.substring(0, eq)] = unescapeTag(pair.substring(eq + 1))
    }
    return out
}

private fun unescapeTag(v: String): String {
    if ('\\' !in v) return v
    val sb = StringBuilder(v.length)
    var i = 0
    while (i < v.length) {
        val c = v[i]
        if (c == '\\' && i + 1 < v.length) {
            when (v[i + 1]) {
                's' -> sb.append(' ')
                ':' -> sb.append(';')
                '\\' -> sb.append('\\')
                'r' -> sb.append('\r')
                'n' -> sb.append('\n')
                else -> sb.append(v[i + 1])
            }
            i += 2
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

private fun parseEmotes(tag: String?): List<EmoteRange> {
    if (tag.isNullOrEmpty()) return emptyList()
    val out = ArrayList<EmoteRange>()
    for (group in tag.split('/')) {
        val colon = group.indexOf(':')
        if (colon < 0) continue
        val id = group.substring(0, colon)
        for (range in group.substring(colon + 1).split(',')) {
            val dash = range.indexOf('-')
            if (dash < 0) continue
            val start = range.substring(0, dash).toIntOrNull() ?: continue
            val end = range.substring(dash + 1).toIntOrNull() ?: continue
            out += EmoteRange(id, start, end)
        }
    }
    out.sortBy { it.start }
    return out
}

internal fun parseBadges(tag: String?): List<String> =
    tag?.split(',')?.filter { it.isNotEmpty() }.orEmpty()

internal fun parseColor(hex: String?): Int? {
    if (hex == null || hex.length != 7 || hex[0] != '#') return null
    return hex.substring(1).toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
}

fun IrcMessage.toChatEvent(): ChatEvent? = when (command) {
    "PRIVMSG" -> toChatMessage()?.let { ChatEvent.Message(it) }
    "USERNOTICE" -> toChatMessage(system = tags["system-msg"] ?: "")?.let { ChatEvent.Message(it) }
    "CLEARCHAT" -> params.getOrNull(1)?.let { ChatEvent.ClearUser(it.lowercase()) } ?: ChatEvent.ClearAll
    "CLEARMSG" -> tags["target-msg-id"]?.let { ChatEvent.Delete(it) }
    else -> null
}

private fun IrcMessage.toChatMessage(system: String? = null): ChatMessage? {
    var text = params.getOrNull(1) ?: if (system != null) "" else return null
    var action = false
    if (text.startsWith("\u0001ACTION ") && text.endsWith("\u0001")) {
        text = text.substring(8, text.length - 1)
        action = true
    }
    val login = prefix?.substringBefore('!')?.takeIf { it.isNotEmpty() && '.' !in it }
        ?: tags["login"].orEmpty()
    return ChatMessage(
        id = tags["id"] ?: UUID.randomUUID().toString(),
        userId = tags["user-id"].orEmpty(),
        login = login,
        displayName = tags["display-name"]?.takeIf { it.isNotBlank() } ?: login,
        color = parseColor(tags["color"]),
        text = text,
        emotes = parseEmotes(tags["emotes"]),
        badges = parseBadges(tags["badges"]),
        timestamp = tags["tmi-sent-ts"]?.toLongOrNull() ?: System.currentTimeMillis(),
        isAction = action,
        systemText = system,
        firstMessage = tags["first-msg"] == "1",
    )
}

internal sealed interface IrcAction {
    data object Pong : IrcAction
    data object Joined : IrcAction
    data object Reconnect : IrcAction
    data object AuthFailed : IrcAction
    data class Emit(val event: ChatEvent) : IrcAction
}

internal fun ircActions(frame: String): List<IrcAction> =
    frame.split("\r\n").mapNotNull(::ircAction)

internal fun ircAction(line: String): IrcAction? {
    if (line.isEmpty()) return null
    if (line.startsWith("PING")) return IrcAction.Pong
    val msg = parseIrc(line) ?: return null
    return when (msg.command) {
        "JOIN" -> IrcAction.Joined
        "RECONNECT" -> IrcAction.Reconnect
        "GLOBALUSERSTATE", "USERSTATE" -> IrcAction.Emit(
            ChatEvent.UserState(
                displayName = msg.tags["display-name"],
                color = parseColor(msg.tags["color"]),
                badges = parseBadges(msg.tags["badges"]),
            ),
        )
        "NOTICE" -> {
            val text = msg.params.lastOrNull().orEmpty()
            if (isAuthFailure(text)) IrcAction.AuthFailed else IrcAction.Emit(ChatEvent.Notice(text))
        }
        else -> msg.toChatEvent()?.let(IrcAction::Emit)
    }
}

private fun isAuthFailure(notice: String) =
    notice.contains("Login authentication failed") || notice.contains("Improperly formatted auth")

internal fun ircLoginLines(account: Account?, room: String, anonymousNick: Int): List<String> = buildList {
    add("CAP REQ :twitch.tv/tags twitch.tv/commands")
    if (account != null) {
        add("PASS oauth:${account.accessToken}")
        add("NICK ${account.login}")
    } else {
        add("PASS SCHMOOPIIE")
        add("NICK justinfan$anonymousNick")
    }
    add("JOIN #$room")
}

internal fun reconnectDelayMs(attempt: Int): Long = min(30_000L, 1_000L shl min(attempt, 5))

internal fun outgoingLine(room: String, text: String): String? {
    val line = text.replace('\r', ' ').replace('\n', ' ').trim()
    if (line.isEmpty()) return null
    return "PRIVMSG #$room :$line"
}

class ChatConnection(
    private val http: OkHttpClient,
    channel: String,
    private val credentials: suspend () -> Account?,
    private val onAuthFailed: suspend () -> Unit,
    private val url: String = DEFAULT_IRC_URL,
) {
    private val room = channel.lowercase()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var authFailed = false
    @Volatile var loggedIn: Boolean = false; private set

    fun send(text: String): Boolean {
        val ws = socket ?: return false
        if (!loggedIn) return false
        val line = outgoingLine(room, text) ?: return false
        return ws.send(line)
    }

    fun events(): Flow<ChatEvent> = channelFlow {
        var attempt = 0
        while (isActive) {
            val acc = try {
                credentials()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val closed = CompletableDeferred<Unit>()
            val ws = http.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        ircLoginLines(acc, room, (10000..99999).random()).forEach { webSocket.send(it) }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        for (action in ircActions(text)) {
                            when (action) {
                                IrcAction.Pong -> webSocket.send("PONG :tmi.twitch.tv")
                                IrcAction.Joined -> {
                                    attempt = 0
                                    loggedIn = acc != null
                                    trySend(ChatEvent.Connection(true, acc != null))
                                }
                                IrcAction.Reconnect -> webSocket.close(1000, null)
                                IrcAction.AuthFailed -> {
                                    authFailed = true
                                    webSocket.close(1000, null)
                                }
                                is IrcAction.Emit -> trySend(action.event)
                            }
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(1000, null)
                        closed.complete(Unit)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        closed.complete(Unit)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        closed.complete(Unit)
                    }
                },
            )
            socket = ws
            try {
                closed.await()
            } finally {
                socket = null
                loggedIn = false
                ws.cancel()
            }
            send(ChatEvent.Connection(false))
            if (authFailed) {
                authFailed = false
                onAuthFailed()
                continue
            }
            attempt++
            delay(reconnectDelayMs(attempt))
        }
    }.buffer(KChannel.UNLIMITED)

    companion object {
        const val DEFAULT_IRC_URL = "wss://irc-ws.chat.twitch.tv:443"
    }
}

class ChatClient(
    private val http: OkHttpClient,
    private val recentBase: String = "https://recent-messages.robotty.de/api/v2/recent-messages",
    private val ircUrl: String = ChatConnection.DEFAULT_IRC_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun connect(channel: String, credentials: suspend () -> Account?, onAuthFailed: suspend () -> Unit) =
        ChatConnection(http, channel, credentials, onAuthFailed, ircUrl)

    suspend fun recent(channel: String, limit: Int = 80): List<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$recentBase/${channel.lowercase()}?limit=$limit")
                .build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use emptyList()
                parseRecentMessages(res.body!!.string())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    internal fun parseRecentMessages(body: String): List<ChatMessage> {
        val root = json.parseToJsonElement(body) as JsonObject
        return root.a("messages").orEmpty().mapNotNull { el ->
            val line = (el as? JsonPrimitive)?.content ?: return@mapNotNull null
            (parseIrc(line)?.toChatEvent() as? ChatEvent.Message)?.message
        }
    }
}
