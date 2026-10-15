package dev.twiview.data

import androidx.annotation.StringRes
import dev.twiview.R
import dev.twiview.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import kotlin.random.Random

class HttpStatusException(val code: Int) : IOException("HTTP $code")
class TwitchException private constructor(raw: String?, @StringRes val resId: Int) : IOException(raw) {
    constructor(message: String) : this(message, 0)
    constructor(@StringRes resId: Int) : this(null, resId)

    override val message: String? get() = super.message ?: Strings.get(resId)
}

class TwitchApi(
    private val http: OkHttpClient,
    private val webToken: () -> String? = { null },
    private val onWebTokenRejected: suspend () -> Unit = {},
    private val gqlUrl: String = "https://gql.twitch.tv/gql",
    private val helixBase: String = "https://api.twitch.tv/helix",
    private val usherBase: String = "https://usher.ttvnw.net",
    private val spadeUrl: String = "https://spade.twitch.tv/track",
) {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun gql(query: String, authToken: String? = null): JsonObject = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("query", query) }.toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(gqlUrl)
            .header("Client-ID", CLIENT_ID)
            .apply { if (authToken != null) header("Authorization", "OAuth $authToken") }
            .post(body)
            .build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw HttpStatusException(res.code)
            val root = json.parseToJsonElement(res.body!!.string()) as JsonObject
            val data = root["data"] as? JsonObject
            if (data == null) {
                val msg = (root["errors"] as? JsonArray)?.firstOrNull()?.obj?.str("message")
                throw msg?.let { TwitchException(it) } ?: TwitchException(R.string.error_empty_response)
            }
            data
        }
    }

    suspend fun topStreams(language: String?, after: String?): Page<Stream> {
        val opts = language?.let { "options: {languages: [${it.uppercase()}]}" }
        val d = gql("query { streams(${args("first: 30", after?.let { "after: ${lit(it)}" }, opts)}) { edges { cursor node { $STREAM } } pageInfo { hasNextPage } } }")
        return page(d.o("streams")) { parseStream(it) }
    }

    suspend fun topGames(after: String?): Page<Game> {
        val d = gql("query { games(${args("first: 45", after?.let { "after: ${lit(it)}" })}) { edges { cursor node { $GAME } } pageInfo { hasNextPage } } }")
        return page(d.o("games")) { parseGame(it) }
    }

    suspend fun gameStreams(name: String, language: String?, after: String?): Page<Stream> {
        val opts = language?.let { "options: {languages: [${lit(it)}]}" }
        val d = gql("query { game(name: ${lit(name)}) { streams(${args("first: 30", after?.let { "after: ${lit(it)}" }, opts)}) { edges { cursor node { $STREAM } } pageInfo { hasNextPage } } } }")
        return page(d.o("game")?.o("streams")) { parseStream(it) }
    }

    suspend fun channels(logins: List<String>): List<ChannelStatus> =
        logins.distinct().chunked(50).flatMap { chunk ->
            val d = gql("query { users(logins: [${chunk.joinToString { lit(it) }}]) { $USER } }")
            d.a("users").orEmpty().mapNotNull { it.obj?.let(::parseStatus) }
        }

    suspend fun channel(login: String): ChannelStatus? {
        val d = gql("query { user(login: ${lit(login)}) { $USER description } }")
        return d.o("user")?.let(::parseStatus)
    }

    suspend fun search(query: String): SearchResult {
        val d = gql("query { searchFor(userQuery: ${lit(query)}, platform: \"web\") { channels { items { $USER } } games { items { $GAME } } } }")
        val s = d.o("searchFor")
        return SearchResult(
            channels = s?.o("channels")?.a("items").orEmpty().mapNotNull { it.obj?.let(::parseStatus) },
            games = s?.o("games")?.a("items").orEmpty().mapNotNull { it.obj?.let(::parseGame) },
        )
    }

    suspend fun videos(login: String): Page<Video> {
        val d = gql("query { user(login: ${lit(login)}) { videos(first: 100, type: ARCHIVE, sort: TIME) { edges { cursor node { $VIDEO } } pageInfo { hasNextPage } } } }")
        return Page(page(d.o("user")?.o("videos")) { parseVideo(it) }.items, cursor = null)
    }

    suspend fun clips(login: String, period: ClipPeriod): Page<Clip> {
        val d = gql("query { user(login: ${lit(login)}) { clips(first: 100, criteria: {period: ${period.gql}, sort: VIEWS_DESC}) { edges { cursor node { $CLIP } } pageInfo { hasNextPage } } } }")
        return Page(page(d.o("user")?.o("clips")) { parseClip(it) }.items, cursor = null)
    }

    suspend fun clip(slug: String): Pair<Clip, String>? {
        val d = gql("query { clip(slug: ${lit(slug)}) { $CLIP videoQualities { sourceURL } playbackAccessToken(params: {platform: \"web\", playerType: \"site\"}) { signature value } } }")
        val o = d.o("clip") ?: return null
        val c = parseClip(o) ?: return null
        val source = o.a("videoQualities")?.firstOrNull()?.obj?.str("sourceURL") ?: return null
        val t = o.o("playbackAccessToken") ?: return null
        return c to clipPlaybackUrl(source, t.str("signature").orEmpty(), t.str("value").orEmpty())
    }

    private fun parseClip(o: JsonObject): Clip? {
        val slug = o.str("slug") ?: return null
        return Clip(
        slug = slug,
        title = o.str("title").orEmpty(),
        views = o.int("viewCount") ?: 0,
        durationSec = o.int("durationSeconds") ?: 0,
        thumbnailUrl = o.str("thumbnailURL"),
        createdAt = o.str("createdAt")?.let(::instant),
        game = o.o("game")?.let(::parseGame),
        curator = o.o("curator")?.let { it.str("displayName") ?: it.str("login") },
        channel = o.o("broadcaster")?.let(::parseChannel),
        videoId = o.o("video")?.str("id"),
        videoOffsetSec = o.int("videoOffsetSeconds") ?: 0,
        )
    }

    suspend fun video(id: String): Pair<Video, Channel?>? {
        val d = gql("query { video(id: ${lit(id)}) { $VIDEO owner { id login displayName profileImageURL(width: 70) } muteInfo { mutedSegmentConnection { nodes { duration offset } } } } }")
        val o = d.o("video") ?: return null
        val v = parseVideo(o) ?: return null
        val muted = o.o("muteInfo")?.o("mutedSegmentConnection")?.a("nodes").orEmpty().mapNotNull { n ->
            val m = n.obj ?: return@mapNotNull null
            MutedSegment(m.int("offset") ?: return@mapNotNull null, m.int("duration") ?: return@mapNotNull null)
        }
        return v.copy(mutedSegments = muted) to o.o("owner")?.let(::parseChannel)
    }

    suspend fun badges(login: String): Map<String, String> {
        val d = gql("query { badges { setID version imageURL(size: DOUBLE) } user(login: ${lit(login)}) { broadcastBadges { setID version imageURL(size: DOUBLE) } } }")
        val out = HashMap<String, String>()
        fun add(arr: JsonArray?) = arr.orEmpty().forEach { e ->
            val o = e.obj ?: return@forEach
            val set = o.str("setID") ?: return@forEach
            val url = o.str("imageURL") ?: return@forEach
            out["$set/${o.str("version")}"] = url
        }
        add(d.a("badges"))
        add(d.o("user")?.a("broadcastBadges"))
        return out
    }

    suspend fun schedule(login: String, now: Instant = Instant.now()): List<ScheduleSegment> {
        val from = now.truncatedTo(ChronoUnit.DAYS)
        val segments = ArrayList<ScheduleSegment>()
        for (day in 0 until SCHEDULE_DAYS step SCHEDULE_STEP_DAYS) {
            val at = from.plus(day.toLong(), ChronoUnit.DAYS)
            val d = gql("query { user(login: ${lit(login)}) { channel { schedule { segments(startingWeekday: \"MONDAY\", relativeDate: ${lit(at.toString())}) { $SEGMENT } } } } }")
            val s = d.o("user")?.o("channel")?.o("schedule") ?: return emptyList()
            s.a("segments").orEmpty().mapNotNullTo(segments) { it.obj?.let(::parseSegment) }
        }
        return segments.distinctBy { it.id }.sortedBy { it.startAt }
    }

    suspend fun followedLogins(userId: String, appToken: String, clientId: String): List<String> = withContext(Dispatchers.IO) {
        val out = ArrayList<String>()
        var cursor: String? = null
        repeat(20) {
            val url = "$helixBase/channels/followed".toHttpUrl().newBuilder()
                .addQueryParameter("user_id", userId)
                .addQueryParameter("first", "100")
                .apply { cursor?.let { addQueryParameter("after", it) } }
                .build()
            val req = Request.Builder().url(url)
                .header("Client-Id", clientId)
                .header("Authorization", "Bearer $appToken")
                .build()
            val o = http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) throw HttpStatusException(res.code)
                json.parseToJsonElement(res.body!!.string()) as JsonObject
            }
            o.a("data").orEmpty().mapNotNullTo(out) { it.obj?.str("broadcaster_login") }
            cursor = o.o("pagination")?.str("cursor") ?: return@withContext out
        }
        out
    }

    private suspend fun playbackGql(query: String): JsonObject {
        val token = webToken() ?: return gql(query)
        return try {
            gql(query, token)
        } catch (e: HttpStatusException) {
            if (e.code != 401) throw e
            onWebTokenRejected()
            gql(query)
        }
    }

    suspend fun liveUrl(login: String): String {
        val d = playbackGql("query { streamPlaybackAccessToken(channelName: ${lit(login)}, params: $TOKEN_PARAMS) { value signature } }")
        val t = d.o("streamPlaybackAccessToken") ?: throw TwitchException(R.string.error_playback_token)
        return usher("api/channel/hls/${login.lowercase()}.m3u8", t)
    }

    suspend fun vodUrl(id: String): String {
        val d = playbackGql("query { videoPlaybackAccessToken(id: ${lit(id)}, params: $TOKEN_PARAMS) { value signature } }")
        val t = d.o("videoPlaybackAccessToken") ?: throw TwitchException(R.string.error_vod_unplayable)
        return usher("vod/$id.m3u8", t)
    }

    suspend fun vodComments(videoId: String, offsetSec: Int): List<VodComment> {
        val d = gql("query { video(id: ${lit(videoId)}) { comments(contentOffsetSeconds: $offsetSec) { edges { node { $COMMENT } } } } }")
        return d.o("video")?.o("comments")?.a("edges").orEmpty().mapNotNull { it.obj?.o("node")?.let(::parseVodComment) }
    }

    suspend fun channelPoints(login: String, token: String): ChannelPoints? {
        val d = gql("query { user(login: ${lit(login)}) { channel { self { communityPoints { balance availableClaim { id } } } communityPointsSettings { isEnabled name image { url2x } } } } }", token)
        val ch = d.o("user")?.o("channel") ?: return null
        val settings = ch.o("communityPointsSettings")
        if (settings?.bool("isEnabled") == false) return null
        val cp = ch.o("self")?.o("communityPoints") ?: return null
        return ChannelPoints(
            balance = (cp["balance"] as? JsonPrimitive)?.longOrNull ?: 0,
            claimId = cp.o("availableClaim")?.str("id"),
            name = settings?.str("name")?.takeIf { it.isNotBlank() },
            iconUrl = settings?.o("image")?.str("url2x"),
        )
    }

    suspend fun sendMinuteWatched(channelId: String, broadcastId: String, login: String, userId: String) = withContext(Dispatchers.IO) {
        val event = buildJsonArray {
            addJsonObject {
                put("event", "minute-watched")
                putJsonObject("properties") {
                    put("channel_id", channelId)
                    put("broadcast_id", broadcastId)
                    put("channel", login)
                    put("user_id", userId)
                    put("player", "site")
                    put("live", true)
                    put("logged_in", true)
                    put("hidden", false)
                    put("muted", false)
                    put("location", "channel")
                }
            }
        }
        val data = Base64.getEncoder().encodeToString(event.toString().toByteArray())
        val req = Request.Builder().url(spadeUrl).post(FormBody.Builder().add("data", data).build()).build()
        http.newCall(req).execute().use { if (!it.isSuccessful) throw HttpStatusException(it.code) }
    }

    suspend fun audioOnlyPlaylist(masterUrl: String): String? = withContext(Dispatchers.IO) {
        val text = http.newCall(Request.Builder().url(masterUrl).build()).execute().use { res ->
            if (!res.isSuccessful) return@withContext null
            res.body!!.string()
        }
        val lines = text.lines()
        val i = lines.indexOfFirst { it.startsWith("#EXT-X-STREAM-INF") && "audio_only" in it }
        if (i < 0) return@withContext null
        lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
    }

    private fun usher(path: String, token: JsonObject): String =
        "$usherBase/$path".toHttpUrl().newBuilder()
            .addQueryParameter("sig", token.str("signature"))
            .addQueryParameter("token", token.str("value"))
            .addQueryParameter("allow_source", "true")
            .addQueryParameter("allow_audio_only", "true")
            .addQueryParameter("fast_bread", "true")
            .addQueryParameter("p", Random.nextInt(1_000_000).toString())
            .addQueryParameter("player_backend", "mediaplayer")
            .addQueryParameter("playlist_include_framerate", "true")
            .addQueryParameter("supported_codecs", "avc1")
            .build().toString()

    private fun parseChannel(o: JsonObject) = Channel(
        id = o.str("id").orEmpty(),
        login = o.str("login").orEmpty(),
        displayName = o.str("displayName") ?: o.str("login").orEmpty(),
        avatarUrl = o.str("profileImageURL"),
    )

    private fun parseGame(o: JsonObject) = Game(
        id = o.str("id").orEmpty(),
        name = o.str("name").orEmpty(),
        displayName = o.str("displayName") ?: o.str("name").orEmpty(),
        viewers = o.int("viewersCount") ?: 0,
        boxArtUrl = o.str("boxArtURL"),
    )

    private fun parseStream(o: JsonObject, channel: Channel? = null): Stream? {
        val ch = channel ?: o.o("broadcaster")?.let(::parseChannel) ?: return null
        return Stream(
            id = o.str("id") ?: return null,
            title = o.str("title").orEmpty(),
            viewers = o.int("viewersCount") ?: 0,
            startedAt = o.str("createdAt")?.let(::instant),
            previewUrl = o.str("previewImageURL"),
            channel = ch,
            game = o.o("game")?.let(::parseGame),
        )
    }

    private fun parseVideo(o: JsonObject): Video? {
        val id = o.str("id") ?: return null
        return Video(
            id = id,
            title = o.str("title").orEmpty(),
            lengthSeconds = o.int("lengthSeconds") ?: 0,
            views = o.int("viewCount") ?: 0,
            createdAt = o.str("createdAt")?.let(::instant),
            thumbnailUrl = o.str("previewThumbnailURL")?.takeUnless { "404_processing" in it },
            game = o.o("game")?.let(::parseGame),
        )
    }

    private fun parseStatus(o: JsonObject): ChannelStatus {
        val ch = parseChannel(o)
        val last = o.o("lastBroadcast")
        return ChannelStatus(
            channel = ch,
            stream = o.o("stream")?.let { parseStream(it, ch) },
            lastBroadcastAt = last?.str("startedAt")?.let(::instant),
            lastTitle = last?.str("title"),
            description = o.str("description"),
        )
    }

    private fun <T> page(conn: JsonObject?, map: (JsonObject) -> T?): Page<T> {
        val edges = conn?.a("edges").orEmpty()
        val items = edges.mapNotNull { e -> e.obj?.o("node")?.let(map) }
        val hasNext = conn?.o("pageInfo")?.bool("hasNextPage") == true
        val cursor = edges.lastOrNull()?.obj?.str("cursor")
        return Page(items, if (hasNext && items.isNotEmpty()) cursor else null)
    }

    companion object {
        const val CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
        private const val TOKEN_PARAMS = "{platform: \"web\", playerBackend: \"mediaplayer\", playerType: \"site\"}"
        private const val GAME_REF = "game { id name displayName }"
        private const val STREAM_CORE = "id title viewersCount createdAt previewImageURL(width: 440, height: 248) $GAME_REF"
        private const val STREAM = "$STREAM_CORE broadcaster { id login displayName profileImageURL(width: 70) }"
        private const val USER = "id login displayName profileImageURL(width: 70) stream { $STREAM_CORE } lastBroadcast { startedAt title }"
        private const val VIDEO = "id title lengthSeconds viewCount createdAt previewThumbnailURL(width: 320, height: 180) $GAME_REF"
        private const val GAME = "id name displayName viewersCount boxArtURL(width: 144, height: 192)"
        private const val SCHEDULE_DAYS = 21
        private const val SCHEDULE_STEP_DAYS = 4
        private const val CLIP = "slug title viewCount durationSeconds thumbnailURL createdAt $GAME_REF curator { login displayName } broadcaster { id login displayName profileImageURL(width: 70) } videoOffsetSeconds video { id }"
        private const val SEGMENT = "id startAt endAt title isCancelled categories { displayName }"
        private const val COMMENT = "id contentOffsetSeconds createdAt commenter { id login displayName } " +
            "message { userColor fragments { text emote { emoteID } } userBadges { setID version } }"
    }
}

private fun lit(s: String) = JsonPrimitive(s).toString()

internal fun clipPlaybackUrl(source: String, signature: String, token: String): String =
    source.toHttpUrl().newBuilder().addQueryParameter("sig", signature).addQueryParameter("token", token).build().toString()
private fun args(vararg parts: String?) = parts.filterNotNull().joinToString(", ")
private fun instant(s: String): Instant? = runCatching { Instant.parse(s) }.getOrNull()

internal val JsonElement?.obj: JsonObject? get() = this as? JsonObject
internal fun JsonObject.o(k: String) = this[k] as? JsonObject
internal fun JsonObject.a(k: String) = this[k] as? JsonArray
internal fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull

internal fun parseSegment(o: JsonObject): ScheduleSegment? {
    val id = o.str("id") ?: return null
    val start = o.str("startAt")?.let(::instant) ?: return null
    return ScheduleSegment(
        id = id,
        startAt = start,
        endAt = o.str("endAt")?.let(::instant),
        title = o.str("title").orEmpty(),
        categories = o.a("categories").orEmpty().mapNotNull { it.obj?.str("displayName") },
        cancelled = o.bool("isCancelled") == true,
    )
}

internal fun parseVodComment(o: JsonObject): VodComment? {
    val id = o.str("id") ?: return null
    val offset = o.int("contentOffsetSeconds") ?: return null
    val user = o.o("commenter")
    val msg = o.o("message") ?: return null
    val text = StringBuilder()
    val emotes = ArrayList<EmoteRange>()
    var cp = 0
    msg.a("fragments").orEmpty().forEach { f ->
        val frag = f.obj ?: return@forEach
        val t = frag.str("text").orEmpty()
        val len = t.codePointCount(0, t.length)
        frag.o("emote")?.str("emoteID")?.takeIf { len > 0 }?.let { emotes += EmoteRange(it, cp, cp + len - 1) }
        text.append(t)
        cp += len
    }
    return VodComment(
        offsetSec = offset,
        message = ChatMessage(
            id = id,
            userId = user?.str("id").orEmpty(),
            login = user?.str("login").orEmpty(),
            displayName = user?.str("displayName") ?: user?.str("login").orEmpty(),
            color = parseColor(msg.str("userColor")),
            text = text.toString(),
            emotes = emotes,
            badges = msg.a("userBadges").orEmpty().mapNotNull { b ->
                val bo = b.obj ?: return@mapNotNull null
                val set = bo.str("setID")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                "$set/${bo.str("version").orEmpty()}"
            },
            timestamp = o.str("createdAt")?.let(::instant)?.toEpochMilli() ?: 0,
        ),
    )
}
