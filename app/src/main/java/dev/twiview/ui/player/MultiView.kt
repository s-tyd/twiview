package dev.twiview.ui.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import dev.twiview.MainActivity
import dev.twiview.R
import dev.twiview.TwiviewApp
import dev.twiview.app
import dev.twiview.data.ChannelStatus
import dev.twiview.data.MultiLayout
import dev.twiview.data.Settings
import dev.twiview.data.Stream
import dev.twiview.ui.common.CenterMessage
import dev.twiview.ui.common.LiveDot
import dev.twiview.ui.common.LoadMoreEffect
import dev.twiview.ui.common.SectionLabel
import dev.twiview.ui.common.SplitTabs
import dev.twiview.ui.common.StreamRow
import dev.twiview.ui.common.currentLocale
import dev.twiview.ui.common.formatCount
import dev.twiview.ui.home.FollowViewModel
import dev.twiview.ui.home.Nav
import dev.twiview.ui.home.PopularViewModel
import dev.twiview.ui.home.SearchViewModel
import dev.twiview.ui.theme.C
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

const val MULTI_MAX = 4

data class Pick(val login: String, val name: String, val preview: String?)

private fun Stream.pick() = Pick(channel.login, channel.displayName, freshPreviewUrl)

private fun ChannelStatus.pick() = Pick(channel.login, channel.displayName, stream?.freshPreviewUrl)

enum class PickSource { Follow, Popular }

class MultiPickViewModel : ViewModel() {
    var picks by mutableStateOf<List<Pick>>(emptyList()); private set
    var source by mutableStateOf(PickSource.Follow); private set

    fun toggle(p: Pick) {
        picks = if (picks.any { it.login == p.login }) picks.filter { it.login != p.login }
        else if (picks.size < MULTI_MAX) picks + p
        else picks
    }

    fun remove(login: String) {
        picks = picks.filter { it.login != login }
    }

    fun choose(s: PickSource) {
        source = s
    }
}

class MultiViewModel(private val app: TwiviewApp, initial: List<String>) : ViewModel() {
    private val stores = LinkedHashMap<String, ViewModelStore>()
    var tiles by mutableStateOf<List<PlayerViewModel>>(emptyList()); private set
    var main by mutableStateOf<PlayerViewModel?>(null); private set
    var chat by mutableStateOf<PlayerViewModel?>(null); private set
    val volumes = mutableStateMapOf<String, Float>()

    init {
        initial.forEach { add(it) }
    }

    fun setVolume(vm: PlayerViewModel, v: Float) {
        vm.player.volume = v
        volumes[vm.login] = v
    }

    fun add(login: String): Boolean {
        val l = login.trim().lowercase()
        if (l.isEmpty() || tiles.size >= MULTI_MAX || stores.containsKey(l)) return false
        val store = ViewModelStore()
        stores[l] = store
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PlayerViewModel(app, l, multi = true) as T
        }
        val vm = ViewModelProvider(store, factory)[PlayerViewModel::class.java]
        setVolume(vm, 1f)
        vm.limitHeight(MAX_HEIGHT)
        tiles = tiles + vm
        if (main == null) main = vm
        if (chat == null) chat = vm
        return true
    }

    fun remove(vm: PlayerViewModel) {
        if (vm !in tiles) return
        tiles = tiles - vm
        volumes.remove(vm.login)
        stores.remove(vm.login)?.clear()
        if (main == vm) main = tiles.firstOrNull()
        if (chat == vm) chat = tiles.firstOrNull()
    }

    fun select(vm: PlayerViewModel) {
        if (vm !in tiles) return
        main = vm
        chat = vm
    }

    fun selectChat(vm: PlayerViewModel) {
        if (vm in tiles) chat = vm
    }

    fun swap(a: PlayerViewModel, b: PlayerViewModel) {
        if (a == b || a !in tiles || b !in tiles) return
        tiles = tiles.map { if (it == a) b else if (it == b) a else it }
        if (main == a) main = b else if (main == b) main = a
    }

    fun cycleChat(delta: Int) {
        if (tiles.isEmpty()) return
        val i = tiles.indexOf(chat)
        chat = tiles[((i + delta) % tiles.size + tiles.size) % tiles.size]
    }

    fun onForeground() = tiles.forEach { it.onForeground() }

    fun onBackground() = tiles.forEach { it.onBackground(dismissedFromPip = true) }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
    }

    companion object {
        private const val MAX_HEIGHT = 720
    }
}

@Composable
fun MultiPickTab(pick: MultiPickViewModel, follows: FollowViewModel, popular: PopularViewModel, nav: Nav) {
    val app = LocalContext.current.app
    val search = viewModel(key = "multi-search") { SearchViewModel(app) }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val settings by app.prefs.settings.collectAsState(initial = Settings())
    var notice by remember { mutableStateOf(false) }
    var hideNotice by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        follows.refresh(indicator = false)
        popular.streams.refresh(showIndicator = false)
    }
    val picks = pick.picks
    val selected = picks.map { it.login }.toSet()
    val searching = search.query.isNotBlank()
    val listState = rememberLazyListState()
    LoadMoreEffect(listState) { if (!searching && pick.source == PickSource.Popular) popular.streams.loadMore() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until MULTI_MAX) {
                    val p = picks.getOrNull(i)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(C.Surface)
                            .border(1.dp, if (p != null) C.Accent else C.Surface2, RoundedCornerShape(6.dp)),
                    ) {
                        if (p == null) {
                            Text("${i + 1}", color = C.Faint, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center))
                        } else {
                            AsyncImage(model = p.preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            if (i == 0) {
                                Text(stringResource(R.string.multiview_main), color = C.Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                            }
                            Text(
                                p.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.BottomStart).padding(3.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                            IconButton(onClick = { pick.remove(p.login) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                                Icon(Icons.Filled.Close, stringResource(R.string.remove), tint = Color.White, modifier = Modifier.size(14.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape))
                            }
                        }
                    }
                }
            }
            TextField(
                value = search.query,
                onValueChange = search::onQuery,
                placeholder = { Text(stringResource(R.string.multiview_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = C.Muted) },
                trailingIcon = {
                    if (search.query.isNotEmpty()) IconButton(onClick = { search.onQuery("") }) { Icon(Icons.Filled.Close, stringResource(R.string.clear), tint = C.Muted) }
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (!searching) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceChip(stringResource(R.string.tab_following), pick.source == PickSource.Follow) { pick.choose(PickSource.Follow) }
                    SourceChip(stringResource(R.string.tab_popular), pick.source == PickSource.Popular) { pick.choose(PickSource.Popular) }
                }
            }
            PullToRefreshBox(
                isRefreshing = follows.refreshing || popular.streams.refreshing,
                onRefresh = { follows.refresh(); popular.streams.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 76.dp)) {
                when {
                    searching -> {
                        val live = search.result?.channels.orEmpty().filter { it.stream != null }
                        items(live, key = { "s-" + it.channel.login }) { s ->
                            PickRow(s.stream!!, selected.indexOf(s.channel.login)) { pick.toggle(s.pick()) }
                        }
                        if (search.result != null && live.isEmpty() && !search.searching) {
                            item(key = "none") { CenterMessage(stringResource(R.string.search_no_results)) }
                        }
                    }
                    pick.source == PickSource.Follow -> {
                        val favorites = follows.favoriteLive
                        val live = follows.live.filter { s -> favorites.none { it.channel.login == s.channel.login } }
                        if (favorites.isNotEmpty()) {
                            item(key = "fh") { SectionLabel(stringResource(R.string.multiview_live_favorites)) }
                            items(favorites, key = { "b-" + it.id }) { s -> PickRow(s, selected.indexOf(s.channel.login)) { pick.toggle(s.pick()) } }
                        }
                        if (live.isNotEmpty()) {
                            item(key = "lh") { SectionLabel(stringResource(R.string.multiview_live_follows)) }
                            items(live, key = { "f-" + it.id }) { s -> PickRow(s, selected.indexOf(s.channel.login)) { pick.toggle(s.pick()) } }
                        }
                        if (favorites.isEmpty() && live.isEmpty() && follows.loadedOnce) {
                            item(key = "empty") { CenterMessage(stringResource(R.string.multiview_none_live)) }
                        }
                    }
                    else -> {
                        items(popular.streams.items, key = { "p-" + it.id }) { s -> PickRow(s, selected.indexOf(s.channel.login)) { pick.toggle(s.pick()) } }
                    }
                }
            }
            }
        }
        Button(
            onClick = { if (settings.multiNoticeDone) nav.multiStart(picks.map { it.login }) else notice = true },
            enabled = picks.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = C.Accent, contentColor = Color.Black, disabledContainerColor = C.Surface2, disabledContentColor = C.Faint),
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().height(44.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(4.dp))
            Text(
                if (picks.isEmpty()) stringResource(R.string.multiview_start) else stringResource(R.string.multiview_start_n, picks.size),
                fontWeight = FontWeight.Bold,
            )
        }
    }
    if (notice) {
        AlertDialog(
            onDismissRequest = { notice = false },
            title = { Text(stringResource(R.string.multiview_notice_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.multiview_notice))
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.clickable { hideNotice = !hideNotice }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hideNotice, onCheckedChange = { hideNotice = it })
                        Text(stringResource(R.string.dont_show_again), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    notice = false
                    if (hideNotice) scope.launch { app.prefs.setMultiNoticeDone(true) }
                    nav.multiStart(picks.map { it.login })
                }) { Text(stringResource(R.string.multiview_start)) }
            },
            dismissButton = { TextButton(onClick = { notice = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = C.Surface,
        )
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) C.Accent else C.Muted,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) C.Accent.copy(alpha = 0.15f) else C.Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun PickRow(s: Stream, index: Int, onClick: () -> Unit) {
    val picked = index >= 0
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (picked) C.Accent.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsyncImage(
            model = s.freshPreviewUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp)).background(C.Surface2),
        )
        Column(Modifier.weight(1f)) {
            Text(s.channel.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.title, style = MaterialTheme.typography.bodySmall, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot()
                Spacer(Modifier.width(4.dp))
                Text(formatCount(s.viewers, currentLocale()), style = MaterialTheme.typography.bodySmall, color = C.Text)
                s.game?.let { Text("  ${it.displayName}", style = MaterialTheme.typography.bodySmall, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (picked) C.Accent else Color.Transparent)
                .border(1.5.dp, if (picked) C.Accent else C.Faint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (picked) Text("${index + 1}", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MultiScreen(logins: String, nav: Nav) {
    val app = LocalContext.current.app
    val activity = LocalActivity.current as MainActivity
    val mv = viewModel { MultiViewModel(app, logins.split(',')) }
    DisposableEffect(mv) {
        mv.onForeground()
        onDispose { mv.onBackground() }
    }
    DisposableEffect(Unit) {
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    BackgroundPauseEffect(onForeground = mv::onForeground, onBackground = mv::onBackground)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    FullscreenEffect(landscape)
    var picker by remember { mutableStateOf(false) }
    var chatVisible by rememberSaveable { mutableStateOf(true) }
    var landscapeMode by rememberSaveable { mutableIntStateOf(0) }
    var menuFor by remember { mutableStateOf<PlayerViewModel?>(null) }
    var swapFrom by remember { mutableStateOf<PlayerViewModel?>(null) }
    var controls by remember { mutableStateOf(false) }
    var poke by remember { mutableIntStateOf(0) }
    var settingsSheet by remember { mutableStateOf(false) }
    LaunchedEffect(controls, poke) {
        if (controls) { delay(3_500); controls = false }
    }
    val tiles = mv.tiles
    val mainVm = mv.main
    if (mainVm == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val toggleFullscreen = {
        when {
            !landscape -> {
                landscapeMode = 0
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            landscapeMode == 0 -> {
                landscapeMode = 1
                chatVisible = true
            }
            else -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    val fullscreenIcon = when {
        !landscape -> Icons.Filled.Fullscreen
        landscapeMode == 0 -> Icons.Filled.AspectRatio
        else -> Icons.Filled.FullscreenExit
    }
    val chatVm = mv.chat ?: mainVm
    val layout = mainVm.settings.multiLayout
    val onSelect: (PlayerViewModel) -> Unit = { controls = !controls }
    val chatColumn: @Composable (Modifier) -> Unit = { m ->
        Column(
            m.pointerInput(mv) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (abs(dx) >= 48.dp.toPx()) mv.cycleChat(if (dx < 0) 1 else -1) },
                ) { change, drag -> dx += drag; change.consume() }
            },
        ) {
            ChatPanel(chatVm, modifier = Modifier.weight(1f).fillMaxWidth())
            if (!chatVm.settings.hideChatInput) ChatInput(chatVm)
        }
    }
    val overlay: @Composable BoxScope.() -> Unit = {
        MultiOverlay(
            mv = mv, main = mainVm, visible = controls, landscape = landscape, chatVisible = chatVisible, fullscreenIcon = fullscreenIcon,
            onBack = nav.back, onSettings = { settingsSheet = true; poke++ }, onAdd = { picker = true; poke++ },
            onToggleChat = { chatVisible = !chatVisible; poke++ }, onToggleFullscreen = toggleFullscreen, onPoke = { poke++ },
        )
    }
    if (landscape) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val aspect = gridAspect(layout, tiles.size)
            val fitWidth = (maxHeight * aspect).coerceAtMost(maxWidth - 160.dp)
            val useFit = landscapeMode == 1 && chatVisible
            Row(Modifier.fillMaxSize()) {
                Box(
                    (if (useFit) Modifier.width(fitWidth) else Modifier.weight(1f))
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls = !controls },
                ) {
                    LandGrid(mv, layout, Modifier.fillMaxSize(), onSelect = onSelect, onMenu = { menuFor = it })
                    overlay()
                }
                if (chatVisible) {
                    chatColumn(
                        (if (landscapeMode == 1) Modifier.weight(1f) else Modifier.width(320.dp))
                            .background(C.Bg)
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                            .fillMaxHeight()
                            .imePadding(),
                    )
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding()) {
            Box(Modifier.fillMaxWidth()) {
                TileGrid(mv, layout, Modifier.fillMaxWidth(), onSelect = onSelect, onMenu = { menuFor = it })
                overlay()
            }
            if (tiles.size > 1) {
                SplitTabs(tiles.map { it.status?.channel?.displayName ?: it.login }, tiles.indexOf(chatVm).coerceAtLeast(0)) { mv.selectChat(tiles[it]) }
            }
            chatColumn(Modifier.weight(1f).fillMaxWidth().imePadding())
        }
    }
    if (settingsSheet) {
        PlayerSettingsSheet(mainVm, onDismiss = { settingsSheet = false })
    }
    var tutorial by remember { mutableStateOf(true) }
    var hideTutorial by remember { mutableStateOf(false) }
    val stored by app.prefs.settings.collectAsState(initial = null)
    if (tutorial && stored?.multiTutorialDone == false) {
        val scope = rememberCoroutineScope()
        val done = {
            tutorial = false
            if (hideTutorial) scope.launch { app.prefs.setMultiTutorialDone(true) }
            Unit
        }
        AlertDialog(
            onDismissRequest = done,
            title = { Text(stringResource(R.string.multiview_tutorial_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TutorialRow(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.multiview_tutorial_volume))
                    TutorialRow(Icons.Filled.TouchApp, stringResource(R.string.multiview_tutorial_tap))
                    TutorialRow(Icons.Filled.OpenInFull, stringResource(R.string.multiview_tutorial_hold))
                    TutorialRow(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.multiview_tutorial_chat))
                    Row(Modifier.clickable { hideTutorial = !hideTutorial }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hideTutorial, onCheckedChange = { hideTutorial = it })
                        Text(stringResource(R.string.dont_show_again), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = done) { Text(stringResource(R.string.ok)) } },
            containerColor = C.Surface,
        )
    }
    if (picker) {
        MultiPickerSheet(exclude = tiles.map { it.login }, onPick = { mv.add(it); picker = false }, onDismiss = { picker = false })
    }
    menuFor?.let { vm ->
        ModalBottomSheet(onDismissRequest = { menuFor = null }, containerColor = C.Surface) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Text(vm.status?.channel?.displayName ?: vm.login, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                if (vm != mainVm) SheetAction(Icons.Filled.OpenInFull, stringResource(R.string.multiview_make_main)) { mv.select(vm); menuFor = null }
                if (tiles.size > 1) SheetAction(Icons.Filled.SwapHoriz, stringResource(R.string.multiview_swap)) { swapFrom = vm; menuFor = null }
                if (vm != chatVm) SheetAction(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.multiview_show_chat)) { mv.selectChat(vm); menuFor = null }
                val muted = (mv.volumes[vm.login] ?: 0f) <= 0f
                SheetAction(if (muted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, stringResource(if (muted) R.string.unmute else R.string.mute)) { mv.setVolume(vm, if (muted) 1f else 0f); menuFor = null }
                SheetAction(Icons.Filled.Close, stringResource(R.string.remove)) { mv.remove(vm); menuFor = null }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    swapFrom?.let { from ->
        ModalBottomSheet(onDismissRequest = { swapFrom = null }, containerColor = C.Surface) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Text(stringResource(R.string.multiview_swap_target), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                tiles.filter { it != from }.forEach { to ->
                    SheetAction(if (to == mainVm) Icons.Filled.OpenInFull else Icons.Filled.SwapHoriz, to.status?.channel?.displayName ?: to.login) { mv.swap(from, to); swapFrom = null }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun MultiLayoutPicker(selected: MultiLayout, onSelect: (MultiLayout) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MultiLayout.entries.forEach { l ->
            val on = l == selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) C.Accent.copy(alpha = 0.15f) else C.Surface2)
                    .border(1.dp, if (on) C.Accent else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(l) }
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LayoutDiagram(l, if (on) C.Accent else C.Muted, Modifier.size(width = 64.dp, height = 36.dp))
                Spacer(Modifier.height(6.dp))
                Text(stringResource(l.label), style = MaterialTheme.typography.labelMedium, color = if (on) C.Accent else C.Muted)
            }
        }
    }
}

@Composable
private fun LayoutDiagram(layout: MultiLayout, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val g = 2.dp.toPx()
        val w = size.width
        val h = size.height
        fun box(x: Float, y: Float, bw: Float, bh: Float) = drawRect(color, Offset(x, y), Size(bw, bh))
        when (layout) {
            MultiLayout.Focus -> {
                val mw = (w - g) * 2 / 3
                box(0f, 0f, mw, h)
                box(mw + g, 0f, w - mw - g, (h - g) / 2)
                box(mw + g, (h + g) / 2, w - mw - g, (h - g) / 2)
            }
            MultiLayout.Grid -> {
                val cw = (w - g) / 2
                val ch = (h - g) / 2
                box(0f, 0f, cw, ch); box(cw + g, 0f, cw, ch); box(0f, ch + g, cw, ch); box(cw + g, ch + g, cw, ch)
            }
            MultiLayout.Line -> {
                val cw = (w - 2 * g) / 3
                val ch = cw * 9 / 16
                val y = (h - ch) / 2
                box(0f, y, cw, ch); box(cw + g, y, cw, ch); box(2 * (cw + g), y, cw, ch)
            }
        }
    }
}

@Composable
private fun TutorialRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = C.Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = C.Muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BoxScope.MultiOverlay(
    mv: MultiViewModel,
    main: PlayerViewModel,
    visible: Boolean,
    landscape: Boolean,
    chatVisible: Boolean,
    fullscreenIcon: ImageVector,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    onToggleChat: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onPoke: () -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(Modifier.fillMaxSize().then(if (landscape) Modifier.windowInsetsPadding(WindowInsets.displayCutout) else Modifier)) {
            Box(Modifier.fillMaxWidth().height(64.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))))
            Box(Modifier.fillMaxWidth().height(64.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                OverlayButton(Icons.AutoMirrored.Filled.ArrowBack, R.string.back, onClick = onBack)
                if (landscape) {
                    Column(Modifier.weight(1f)) {
                        Text(main.status?.channel?.displayName ?: main.login, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        main.status?.stream?.title?.let {
                            Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                OverlayButton(Icons.Filled.Settings, R.string.settings, onClick = onSettings)
            }
            OverlayButton(Icons.Filled.Refresh, R.string.reload, Modifier.align(Alignment.BottomStart).padding(4.dp)) { mv.tiles.forEach { it.load() }; onPoke() }
            Row(Modifier.align(Alignment.BottomEnd).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                OverlayButton(
                    if (main.danmaku.enabled) Icons.Filled.Subtitles else Icons.Filled.SubtitlesOff,
                    R.string.danmaku,
                    tint = if (main.danmaku.enabled) C.Accent else Color.White,
                ) { main.updateDanmaku(main.danmaku.copy(enabled = !main.danmaku.enabled)); onPoke() }
                if (mv.tiles.size < MULTI_MAX) OverlayButton(Icons.Filled.Add, R.string.multiview_pick, onClick = onAdd)
                if (landscape && main.hasChat) {
                    OverlayButton(Icons.AutoMirrored.Filled.Chat, R.string.chat, tint = if (chatVisible) C.Accent else Color.White, onClick = onToggleChat)
                }
                OverlayButton(fullscreenIcon, R.string.toggle_fullscreen, onClick = onToggleFullscreen)
            }
        }
    }
}

@Composable
private fun Tiles(mv: MultiViewModel, onSelect: (PlayerViewModel) -> Unit, onMenu: (PlayerViewModel) -> Unit) {
    mv.tiles.forEach { vm ->
        key(vm.login) { Tile(vm, mv.volumes[vm.login] ?: 0f, Modifier, { onSelect(vm) }, { onMenu(vm) }, { mv.setVolume(vm, it) }) }
    }
}

private fun gridAspect(layout: MultiLayout, n: Int): Float = when {
    n <= 1 -> 16f / 9f
    layout == MultiLayout.Line -> n * 16f / 9f
    layout == MultiLayout.Grid || n == 2 -> 32f / (9f * ((n + 1) / 2))
    else -> n * 16f / (9f * (n - 1))
}

@Composable
private fun TileGrid(mv: MultiViewModel, layout: MultiLayout, modifier: Modifier, onSelect: (PlayerViewModel) -> Unit, onMenu: (PlayerViewModel) -> Unit) {
    val gap = 2.dp
    val tiles = mv.tiles
    val main = mv.main
    Layout(
        content = { Tiles(mv, onSelect, onMenu) },
        modifier = modifier,
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val g = gap.roundToPx()
        val n = measurables.size
        val placed: List<Pair<Placeable, Pair<Int, Int>>>
        val total: Int
        when {
            n <= 1 || layout == MultiLayout.Line -> {
                val h = w * 9 / 16
                placed = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(w, h)) to (0 to i * (h + g)) }
                total = n * h + (n - 1) * g
            }
            layout == MultiLayout.Grid -> {
                val cw = (w - g) / 2
                val ch = cw * 9 / 16
                val rows = (n + 1) / 2
                placed = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(cw, ch)) to ((i % 2) * (cw + g) to (i / 2) * (ch + g)) }
                total = rows * ch + (rows - 1) * g
            }
            else -> {
                val mainH = w * 9 / 16
                val subs = n - 1
                val subW = (w - g * (subs - 1)) / subs
                val subH = subW * 9 / 16
                val mainIndex = tiles.indexOf(main)
                var col = 0
                placed = measurables.mapIndexed { i, m ->
                    if (i == mainIndex) m.measure(Constraints.fixed(w, mainH)) to (0 to 0)
                    else m.measure(Constraints.fixed(subW, subH)) to ((col++) * (subW + g) to mainH + g)
                }
                total = mainH + g + subH
            }
        }
        layout(w, total) { placed.forEach { (p, pos) -> p.place(pos.first, pos.second) } }
    }
}

@Composable
private fun LandGrid(mv: MultiViewModel, layout: MultiLayout, modifier: Modifier, onSelect: (PlayerViewModel) -> Unit, onMenu: (PlayerViewModel) -> Unit) {
    val gap = 2.dp
    val tiles = mv.tiles
    val main = mv.main
    Layout(
        content = { Tiles(mv, onSelect, onMenu) },
        modifier = modifier,
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val g = gap.roundToPx()
        val n = measurables.size
        val placed: List<Pair<Placeable, Pair<Int, Int>>>
        when {
            n <= 2 || layout == MultiLayout.Line -> {
                val s = minOf((w - g * (n - 1)) / n, h * 16 / 9)
                val tileH = s * 9 / 16
                val x0 = (w - (s * n + g * (n - 1))) / 2
                val y0 = (h - tileH) / 2
                placed = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(s, tileH)) to (x0 + i * (s + g) to y0) }
            }
            layout == MultiLayout.Grid -> {
                val rows = (n + 1) / 2
                val cw = minOf((w - g) / 2, (h - (rows - 1) * g) / rows * 16 / 9)
                val ch = cw * 9 / 16
                val x0 = (w - (2 * cw + g)) / 2
                val y0 = (h - (rows * ch + (rows - 1) * g)) / 2
                placed = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(cw, ch)) to (x0 + (i % 2) * (cw + g) to y0 + (i / 2) * (ch + g)) }
            }
            else -> {
                val k = n - 1
                val s = minOf((w - g - (k - 1) * g * 16 / 9) / (k + 1), (h - (k - 1) * g) * 16 / (9 * k))
                val subH = s * 9 / 16
                val mainH = k * subH + (k - 1) * g
                val mainW = mainH * 16 / 9
                val x0 = (w - (mainW + g + s)) / 2
                val y0 = (h - mainH) / 2
                val mainIndex = tiles.indexOf(main)
                var row = 0
                placed = measurables.mapIndexed { i, m ->
                    if (i == mainIndex) m.measure(Constraints.fixed(mainW, mainH)) to (x0 to y0)
                    else m.measure(Constraints.fixed(s, subH)) to (x0 + mainW + g to y0 + (row++) * (subH + g))
                }
            }
        }
        layout(w, h) { placed.forEach { (p, pos) -> p.place(pos.first, pos.second) } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(vm: PlayerViewModel, volume: Float, modifier: Modifier, onSelect: () -> Unit, onLongPress: () -> Unit, onVolume: (Float) -> Unit) {
    var hint by remember { mutableStateOf<Int?>(null) }
    val current by rememberUpdatedState(volume)
    LaunchedEffect(hint) {
        if (hint != null) { delay(700); hint = null }
    }
    Box(
        modifier
            .background(Color.Black)
            .combinedClickable(onClick = onSelect, onLongClick = onLongPress)
            .pointerInput(vm) {
                var start = 0f
                var acc = 0f
                detectVerticalDragGestures(
                    onDragStart = { start = current; acc = 0f },
                ) { change, dy ->
                    acc += dy
                    val v = (start - acc / size.height).coerceIn(0f, 1f)
                    onVolume(v)
                    hint = (v * 100).roundToInt()
                    change.consume()
                }
            },
    ) {
        VideoSurface(vm.player)
        if (vm.danmaku.enabled) DanmakuOverlay(vm.messages, vm.settings, vm.danmaku)
        when (val s = vm.state) {
            PlayState.Offline -> Text(stringResource(R.string.offline), color = Color.White, modifier = Modifier.align(Alignment.Center))
            is PlayState.Error -> Text(s.message, color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.Center).padding(8.dp))
            else -> if (vm.buffering) CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.Center), color = Color.White, strokeWidth = 2.dp)
        }
        hint?.let { Pill(Icons.AutoMirrored.Filled.VolumeUp, "$it%", Modifier.align(Alignment.Center)) }
        if (volume <= 0f && hint == null) {
            Icon(Icons.AutoMirrored.Filled.VolumeOff, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(14.dp))
        }
    }
}

@Composable
fun MultiPickerSheet(exclude: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val app = LocalContext.current.app
    val follows = viewModel(key = "multi-picker") { FollowViewModel(app) }
    LaunchedEffect(Unit) { follows.refreshIfStale() }
    val favorites = follows.favoriteLive.filter { it.channel.login !in exclude }
    val live = follows.live.filter { it.channel.login !in exclude && it.channel.login !in favorites.map { s -> s.channel.login } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(stringResource(R.string.multiview_pick), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            if (favorites.isEmpty() && live.isEmpty()) {
                Text(stringResource(R.string.multiview_none_live), color = C.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                if (favorites.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.multiview_live_favorites)) }
                    items(favorites, key = { "b-" + it.id }) { s -> StreamRow(s, onClick = { onPick(s.channel.login) }) }
                }
                if (live.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.multiview_live_follows)) }
                    items(live, key = { "f-" + it.id }) { s -> StreamRow(s, onClick = { onPick(s.channel.login) }) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
