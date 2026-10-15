package dev.twiview.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.twiview.AppLanguage
import dev.twiview.R
import dev.twiview.Strings
import dev.twiview.TwiviewApp
import dev.twiview.data.ChannelStatus
import dev.twiview.data.FollowSort
import dev.twiview.data.Game
import dev.twiview.data.HttpStatusException
import dev.twiview.data.SearchResult
import dev.twiview.data.Stream
import dev.twiview.data.silentAppAuthorize
import dev.twiview.ui.common.Paged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class FollowViewModel(private val app: TwiviewApp) : ViewModel() {
    var follows by mutableStateOf<List<String>>(emptyList()); private set
    var favorites by mutableStateOf<List<String>?>(null); private set
    var sync by mutableStateOf<FollowSync?>(null); private set
    var statuses by mutableStateOf<List<ChannelStatus>>(emptyList()); private set
    var favoriteStatuses by mutableStateOf<List<ChannelStatus>>(emptyList()); private set
    var refreshing by mutableStateOf(false); private set
    var loadedOnce by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var sort by mutableStateOf(FollowSort.Viewers); private set
    private var job: Job? = null
    private var lastLoaded = 0L
    private var triedSilentFor: String? = null

    val live: List<Stream>
        get() = sortLive(statuses.mapNotNull { it.stream }, sort, follows)
    val offline: List<ChannelStatus>
        get() = sortOffline(statuses.filter { it.stream == null }, sort, follows)
    val favoriteLive: List<Stream>
        get() = sortLive(favoriteStatuses.mapNotNull { it.stream }, sort, favorites.orEmpty())
    val favoriteOffline: List<ChannelStatus>
        get() = sortOffline(favoriteStatuses.filter { it.stream == null }, sort, favorites.orEmpty())
    val liveCount: Int
        get() = (live + favoriteLive).distinctBy { it.channel.login }.size

    init {
        viewModelScope.launch {
            app.prefs.settings.map { it.followSort }.distinctUntilChanged().collect { sort = it }
        }
        viewModelScope.launch {
            app.prefs.settings.map { it.favorites }.distinctUntilChanged().collect { f ->
                val before = favorites
                favorites = f
                if (before != null && before.containsAll(f)) {
                    favoriteStatuses = favoriteStatuses.filter { it.channel.login in f }
                } else {
                    refresh(indicator = false)
                }
            }
        }
        viewModelScope.launch {
            combine(app.web.state, app.web.clientId) { acc, cid -> Triple(acc?.login, acc?.appToken, cid) }
                .distinctUntilChanged().collect { (login, appToken, clientId) ->
                var s = followSyncOf(login != null, clientId, appToken)
                if (s == FollowSync.NeedsLink && triedSilentFor != clientId) {
                    triedSilentFor = clientId
                    val t = runCatching { silentAppAuthorize(app, clientId!!) }.getOrNull()
                    if (t != null) {
                        app.web.setAppToken(t); return@collect
                    }
                }
                sync = s
                if (s != FollowSync.Synced) {
                    follows = emptyList(); statuses = emptyList()
                }
                refresh(indicator = false)
            }
        }
    }

    fun chooseSort(v: FollowSort) {
        sort = v
        viewModelScope.launch { app.prefs.setFollowSort(v) }
    }

    fun removeFavorite(login: String) {
        viewModelScope.launch { app.prefs.toggleFavorite(login) }
    }

    fun refreshIfStale() {
        if (System.currentTimeMillis() - lastLoaded > 30_000) refresh(indicator = false)
    }

    fun refresh(indicator: Boolean = true) {
        val favs = favorites ?: return
        val acc = app.web.state.value
        val appToken = acc?.appToken
        val clientId = app.web.clientId.value
        val synced = sync == FollowSync.Synced && acc != null && appToken != null && clientId != null
        job?.cancel()
        job = viewModelScope.launch {
            refreshing = indicator
            try {
                if (synced) follows = app.api.followedLogins(acc.userId, appToken, clientId)
                val all = (follows + favs).distinct()
                val st = if (all.isEmpty()) emptyList() else app.api.channels(all)
                val (f, v) = splitStatuses(st, follows, favs)
                statuses = f
                favoriteStatuses = v
                error = null
                loadedOnce = true
                lastLoaded = System.currentTimeMillis()
            } catch (e: HttpStatusException) {
                if (e.code == 401) {
                    triedSilentFor = null
                    app.web.setAppToken(null)
                } else {
                    error = e.message
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.message ?: Strings.get(R.string.error_load_failed)
            } finally {
                refreshing = false
            }
        }
    }
}

class PopularViewModel(private val app: TwiviewApp) : ViewModel() {
    var language by mutableStateOf(AppLanguage.streamDefault()); private set
    val streams = Paged(viewModelScope, Stream::id) { cursor -> app.api.topStreams(language, cursor) }

    init {
        viewModelScope.launch {
            app.prefs.settings.map { it.language }.distinctUntilChanged().collect {
                language = it
                streams.refresh(showIndicator = false)
            }
        }
    }

    fun chooseLanguage(l: String?) {
        viewModelScope.launch { app.prefs.setLanguage(l) }
    }
}

class GamesViewModel(private val app: TwiviewApp) : ViewModel() {
    val games = Paged(viewModelScope, Game::id) { cursor -> app.api.topGames(cursor) }

    init {
        games.refresh(showIndicator = false)
    }
}

@OptIn(FlowPreview::class)
class SearchViewModel(private val app: TwiviewApp) : ViewModel() {
    private val queryFlow = MutableStateFlow("")
    var query by mutableStateOf(""); private set
    var result by mutableStateOf<SearchResult?>(null); private set
    var searching by mutableStateOf(false); private set

    init {
        viewModelScope.launch {
            queryFlow.drop(1).debounce(300).distinctUntilChanged().collectLatest { q ->
                if (q.isBlank()) {
                    result = null; return@collectLatest
                }
                searching = true
                try {
                    result = app.api.search(q.trim())
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                } finally {
                    searching = false
                }
            }
        }
    }

    fun onQuery(q: String) {
        query = q
        queryFlow.value = q
    }
}

class CategoryViewModel(private val app: TwiviewApp, val name: String) : ViewModel() {
    var language by mutableStateOf(AppLanguage.streamDefault()); private set
    val streams = Paged(viewModelScope, Stream::id) { cursor -> app.api.gameStreams(name, language, cursor) }

    init {
        viewModelScope.launch {
            app.prefs.settings.map { it.language }.distinctUntilChanged().collect {
                language = it
                streams.refresh(showIndicator = false)
            }
        }
    }

    fun chooseLanguage(l: String?) {
        viewModelScope.launch { app.prefs.setLanguage(l) }
    }
}
