package dev.twiview.ui.home

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.twiview.AppLanguage
import dev.twiview.R
import dev.twiview.app
import dev.twiview.data.ChannelStatus
import dev.twiview.data.FollowSort
import dev.twiview.data.Stream
import dev.twiview.ui.common.Avatar
import dev.twiview.ui.common.CenterLoading
import dev.twiview.ui.common.CenterMessage
import dev.twiview.ui.common.ErrorFooter
import dev.twiview.ui.common.FooterLoading
import dev.twiview.ui.common.GameCell
import dev.twiview.ui.common.LiveDot
import dev.twiview.ui.common.LoadMoreEffect
import dev.twiview.ui.common.OfflineRow
import dev.twiview.ui.common.Paged
import dev.twiview.ui.common.SectionLabel
import dev.twiview.ui.common.SplitTabs
import dev.twiview.ui.common.StreamRow
import dev.twiview.ui.common.currentLocale
import dev.twiview.ui.common.formatCount
import dev.twiview.ui.player.MultiPickTab
import dev.twiview.ui.player.MultiPickViewModel
import dev.twiview.ui.settings.WebLoginActivity
import dev.twiview.ui.theme.C
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

class Nav(
    val stream: (login: String) -> Unit,
    val channel: (login: String) -> Unit,
    val category: (name: String) -> Unit,
    val vod: (id: String) -> Unit,
    val clip: (slug: String) -> Unit,
    val multiStart: (logins: List<String>) -> Unit,
    val settings: () -> Unit,
    val account: () -> Unit,
    val back: () -> Unit,
)

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    Follow(R.string.tab_following, Icons.Filled.Bookmark),
    Popular(R.string.tab_popular, Icons.Filled.Whatshot),
    Multi(R.string.multiview, Icons.Filled.ViewAgenda),
    Search(R.string.tab_search, Icons.Filled.Search),
}

@Composable
fun HomeScreen(nav: Nav) {
    val app = LocalContext.current.app
    var tab by rememberSaveable { mutableStateOf(Tab.Follow) }
    val holder = rememberSaveableStateHolder()
    val follow = viewModel { FollowViewModel(app) }
    val popular = viewModel { PopularViewModel(app) }
    val games = viewModel { GamesViewModel(app) }
    val search = viewModel { SearchViewModel(app) }
    val pick = viewModel { MultiPickViewModel() }

    Scaffold(
        containerColor = C.Bg,
        bottomBar = {
            NavigationBar(containerColor = C.Surface, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            val liveCount = if (t == Tab.Follow) follow.liveCount else 0
                            BadgedBox(badge = { if (liveCount > 0) Badge(containerColor = C.Live) { Text("$liveCount") } }) {
                                Icon(t.icon, contentDescription = stringResource(t.label))
                            }
                        },
                        label = { Text(stringResource(t.label), style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = C.Surface2,
                            selectedIconColor = C.Accent,
                            selectedTextColor = C.Text,
                            unselectedIconColor = C.Faint,
                            unselectedTextColor = C.Faint,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .padding(bottom = pad.calculateBottomPadding())
                .statusBarsPadding(),
        ) {
            if (tab != Tab.Search) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(tab.label), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (tab == Tab.Follow) FollowSortButton(follow)
                    IconButton(onClick = nav.settings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings), tint = C.Muted) }
                }
            }
            holder.SaveableStateProvider(tab.name) {
                when (tab) {
                    Tab.Follow -> FollowTab(follow, nav)
                    Tab.Popular -> PopularTab(popular, nav)
                    Tab.Multi -> MultiPickTab(pick, follow, popular, nav)
                    Tab.Search -> SearchTab(search, games, nav)
                }
            }
        }
    }
}

@Composable
private fun HintBanner(text: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(C.Surface2, RoundedCornerShape(8.dp))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = C.Muted, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun FollowLinkBanner() {
    val context = LocalContext.current
    HintBanner(stringResource(R.string.follow_link_banner), stringResource(R.string.follow_link)) {
        context.startActivity(Intent(context, WebLoginActivity::class.java))
    }
}

@Composable
private fun FollowSortButton(vm: FollowViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, null, tint = C.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(vm.sort.label), color = C.Muted, style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = C.Surface2) {
            FollowSort.entries.forEach { o ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(o.label),
                            color = if (o == vm.sort) C.Accent else C.Text,
                            fontWeight = if (o == vm.sort) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = { vm.chooseSort(o); open = false },
                )
            }
        }
    }
}

@Composable
private fun FollowTab(vm: FollowViewModel, nav: Nav) {
    LifecycleResumeEffect(vm) {
        vm.refreshIfStale()
        onPauseOrDispose { }
    }
    LaunchedEffect(vm) {
        while (true) {
            delay(90_000); vm.refresh(indicator = false)
        }
    }
    var removing by remember { mutableStateOf<String?>(null) }
    val favorites = vm.favorites
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val twitchList: @Composable () -> Unit = {
        when {
            favorites == null -> CenterLoading()
            vm.statuses.isEmpty() && vm.error != null -> CenterMessage(vm.error!!, stringResource(R.string.retry)) { vm.refresh() }
            vm.statuses.isEmpty() && !vm.loadedOnce -> CenterLoading()
            vm.statuses.isEmpty() -> CenterMessage(stringResource(R.string.follow_empty))
            else -> ChannelList(vm.live, vm.offline, nav, onLongPress = nav.channel)
        }
    }
    val favoriteList: @Composable () -> Unit = {
        when {
            favorites == null -> CenterLoading()
            favorites.isEmpty() -> CenterMessage(stringResource(R.string.favorites_empty))
            vm.favoriteStatuses.isEmpty() && vm.error != null -> CenterMessage(vm.error!!, stringResource(R.string.retry)) { vm.refresh() }
            vm.favoriteStatuses.isEmpty() -> CenterLoading()
            else -> ChannelList(vm.favoriteLive, vm.favoriteOffline, nav, onLongPress = { removing = it })
        }
    }

    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            when (vm.sync) {
                null -> Unit
                FollowSync.Synced -> SourceSwitch(pager.currentPage == 0) { twitch -> scope.launch { pager.animateScrollToPage(if (twitch) 0 else 1) } }
                FollowSync.NotLoggedIn -> HintBanner(stringResource(R.string.follow_sync_hint_login), stringResource(R.string.login_with_twitch), nav.settings)
                FollowSync.NeedsClientId -> HintBanner(stringResource(R.string.follow_sync_hint_client_id), stringResource(R.string.add_client_id), nav.account)
                FollowSync.NeedsLink -> FollowLinkBanner()
            }
            if (vm.sync == FollowSync.Synced) {
                HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                    Box(Modifier.fillMaxSize()) { if (page == 0) twitchList() else favoriteList() }
                }
            } else {
                favoriteList()
            }
        }
    }
    removing?.let { login ->
        AlertDialog(
            onDismissRequest = { removing = null },
            text = { Text(stringResource(R.string.favorite_remove_confirm, login)) },
            confirmButton = { TextButton(onClick = { vm.removeFavorite(login); removing = null }) { Text(stringResource(R.string.remove)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
            containerColor = C.Surface2,
        )
    }
}

@Composable
private fun SourceSwitch(twitch: Boolean, onChange: (Boolean) -> Unit) {
    SplitTabs(
        listOf(stringResource(R.string.twitch_follows), stringResource(R.string.favorites)),
        if (twitch) 0 else 1,
    ) { onChange(it == 0) }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = C.Accent.copy(alpha = 0.18f),
            selectedLabelColor = C.Accent,
            labelColor = C.Muted,
        ),
        border = null,
        modifier = Modifier.height(30.dp),
    )
}

@Composable
private fun ChannelList(live: List<Stream>, offline: List<ChannelStatus>, nav: Nav, onLongPress: (login: String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (live.isNotEmpty()) {
            item(key = "h-live") { SectionLabel(stringResource(R.string.section_live_count, live.size)) }
            items(live, key = { "l-" + it.channel.login }) { s ->
                StreamRow(s, onClick = { nav.stream(s.channel.login) }, onLongClick = { onLongPress(s.channel.login) })
            }
        }
        if (offline.isNotEmpty()) {
            item(key = "h-off") { SectionLabel(stringResource(R.string.section_offline_count, offline.size)) }
            items(offline, key = { "o-" + it.channel.login }) { s ->
                OfflineRow(s, onClick = { nav.channel(s.channel.login) }, onLongClick = { onLongPress(s.channel.login) })
            }
        }
    }
}

@Composable
private fun rememberStreamFilterOrder(): List<String>? {
    val prefs = LocalContext.current.app.prefs
    val saved by remember { prefs.settings.map { it.streamOrder } }.collectAsState(initial = null)
    val mine = AppLanguage.streamDefault(currentLocale())
    return saved?.let { streamFilterOrder(it, mine) }
}

@Composable
fun rememberStreamFilterCodes(): List<String?>? =
    rememberStreamFilterOrder()?.map { it.takeIf { t -> t != ALL_STREAMS } }

@Composable
fun LanguageChips(selected: String?, onSelect: (String?) -> Unit) {
    val order = rememberStreamFilterOrder() ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        order.forEach { tag ->
            val code = tag.takeIf { it != ALL_STREAMS }
            SelectChip(stringResource(streamFilterLabel(tag)), selected == code) { onSelect(code) }
        }
    }
}

@Composable
fun StreamList(paged: Paged<Stream>, nav: Nav, showGame: Boolean = true, header: @Composable () -> Unit = {}) {
    val state = rememberLazyListState()
    LoadMoreEffect(state) { paged.loadMore() }
    PullToRefreshBox(isRefreshing = paged.refreshing, onRefresh = { paged.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state) {
            item(key = "header") { header() }
            items(paged.items, key = { it.id }) { s ->
                StreamRow(s, onClick = { nav.stream(s.channel.login) }, onLongClick = { nav.channel(s.channel.login) }, showGame = showGame)
            }
            when {
                paged.error != null -> item(key = "err") { ErrorFooter(paged.error!!) { if (paged.items.isEmpty()) paged.refresh() else paged.loadMore() } }
                paged.loading && !paged.refreshing -> item(key = "loading") { FooterLoading() }
                paged.loadedOnce && paged.items.isEmpty() -> item(key = "empty") { CenterMessage(stringResource(R.string.streams_empty)) }
            }
        }
    }
}

@Composable
private fun PopularTab(vm: PopularViewModel, nav: Nav) {
    LaunchedEffect(Unit) { vm.streams.refresh(showIndicator = false) }
    val codes = rememberStreamFilterCodes()
    var direction by remember { mutableIntStateOf(1) }
    val snapshots = remember { HashMap<String?, List<Stream>>() }
    val lang = vm.language
    snapshots[lang] = vm.streams.items
    fun go(target: String?) {
        val from = codes?.indexOf(lang) ?: -1
        val to = codes?.indexOf(target) ?: -1
        direction = if (to >= from) 1 else -1
        vm.chooseLanguage(target)
    }
    Column(
        Modifier.fillMaxSize().pointerInput(codes, lang) {
            val list = codes ?: return@pointerInput
            var dx = 0f
            detectHorizontalDragGestures(
                onDragStart = { dx = 0f },
                onDragEnd = {
                    if (abs(dx) < 48.dp.toPx()) return@detectHorizontalDragGestures
                    val i = list.indexOf(lang)
                    val next = if (dx < 0) i + 1 else i - 1
                    if (i >= 0 && next in list.indices) go(list[next])
                },
            ) { change, drag -> dx += drag; change.consume() }
        },
    ) {
        LanguageChips(lang, ::go)
        AnimatedContent(
            targetState = lang,
            transitionSpec = {
                (slideInHorizontally { it * direction } + fadeIn()) togetherWith (slideOutHorizontally { -it * direction } + fadeOut())
            },
            label = "popular",
        ) { shown ->
            if (shown == lang) StreamList(vm.streams, nav) else StaticStreamList(snapshots[shown].orEmpty(), nav)
        }
    }
}

@Composable
private fun StaticStreamList(items: List<Stream>, nav: Nav) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { s -> StreamRow(s, onClick = { nav.stream(s.channel.login) }, showGame = true) }
    }
}

@Composable
private fun GamesGrid(vm: GamesViewModel, nav: Nav) {
    val state = rememberLazyGridState()
    val paged = vm.games
    LoadMoreEffect(state) { paged.loadMore() }
    PullToRefreshBox(isRefreshing = paged.refreshing, onRefresh = { paged.refresh() }, modifier = Modifier.fillMaxSize()) {
        if (!paged.loadedOnce) {
            CenterLoading()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                state = state,
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(paged.items, key = { it.id }) { g -> GameCell(g) { nav.category(g.name) } }
            }
        }
    }
}

@Composable
private fun SearchTab(vm: SearchViewModel, games: GamesViewModel, nav: Nav) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = vm.query,
            onValueChange = vm::onQuery,
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = C.Muted) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Filled.Close, stringResource(R.string.clear), tint = C.Muted) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = C.Surface2,
                unfocusedContainerColor = C.Surface2,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        )
        if (vm.query.isBlank()) {
            GamesGrid(games, nav)
            return@Column
        }
        val r = vm.result
        LazyColumn(Modifier.fillMaxSize()) {
            if (r != null && r.games.isNotEmpty()) {
                item(key = "gh") { SectionLabel(stringResource(R.string.tab_categories)) }
                item(key = "games") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                        items(r.games, key = { it.id }) { g ->
                            Box(Modifier.width(84.dp)) { GameCell(g) { nav.category(g.name) } }
                        }
                    }
                }
            }
            if (r != null && r.channels.isNotEmpty()) {
                item(key = "ch") { SectionLabel(stringResource(R.string.section_channels)) }
                val sorted = r.channels.sortedByDescending { it.stream != null }
                items(sorted, key = { "c-" + it.channel.login }) { s ->
                    SearchChannelRow(s) { if (s.stream != null) nav.stream(s.channel.login) else nav.channel(s.channel.login) }
                }
            }
            if (r != null && r.games.isEmpty() && r.channels.isEmpty() && !vm.searching) {
                item(key = "none") { CenterMessage(stringResource(R.string.search_no_results)) }
            }
        }
    }
}

@Composable
private fun SearchChannelRow(s: ChannelStatus, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(s.channel.avatarUrl, 36.dp)
        Spacer(Modifier.width(10.dp))
        val st = s.stream
        Column(Modifier.weight(1f)) {
            Text(s.channel.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (st != null) {
                Text(st.title, style = MaterialTheme.typography.bodySmall, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (st != null) {
            LiveDot()
            Spacer(Modifier.width(4.dp))
            Text(formatCount(st.viewers, currentLocale()), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun CategoryScreen(name: String, nav: Nav) {
    val app = LocalContext.current.app
    val vm = viewModel { CategoryViewModel(app, name) }
    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StreamList(vm.streams, nav, showGame = false) {
            LanguageChips(vm.language, vm::chooseLanguage)
        }
    }
}
