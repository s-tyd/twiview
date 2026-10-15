package dev.twiview.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.twiview.R
import dev.twiview.TwiviewApp
import dev.twiview.app
import dev.twiview.data.ChannelStatus
import dev.twiview.data.Clip
import dev.twiview.data.ClipPeriod
import dev.twiview.data.ScheduleSegment
import dev.twiview.data.Video
import dev.twiview.data.WatchPosition
import dev.twiview.ui.common.Avatar
import dev.twiview.ui.common.CenterMessage
import dev.twiview.ui.common.ClipRow
import dev.twiview.ui.common.ErrorFooter
import dev.twiview.ui.common.FavoriteButton
import dev.twiview.ui.common.FooterLoading
import dev.twiview.ui.common.LoadMoreEffect
import dev.twiview.ui.common.Paged
import dev.twiview.ui.common.SectionLabel
import dev.twiview.ui.common.SplitTabs
import dev.twiview.ui.common.StreamRow
import dev.twiview.ui.common.VideoRow
import dev.twiview.ui.common.currentLocale
import dev.twiview.ui.common.formatAgo
import dev.twiview.ui.home.Nav
import dev.twiview.ui.settings.ChipRow
import dev.twiview.ui.theme.C
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

class ChannelViewModel(private val app: TwiviewApp, val login: String) : ViewModel() {
    var status by mutableStateOf<ChannelStatus?>(null); private set
    var notFound by mutableStateOf(false); private set
    val videos = Paged(viewModelScope, Video::id) { app.api.videos(login) }
    var clipPeriod by mutableStateOf(ClipPeriod.Week); private set
    val clips = Paged(viewModelScope, Clip::slug) { app.api.clips(login, clipPeriod) }
    private var favorites by mutableStateOf<List<String>>(emptyList())
    var positions by mutableStateOf<Map<String, WatchPosition>>(emptyMap()); private set
    val isFavorite: Boolean get() = login.lowercase() in favorites
    var weekStart by mutableStateOf(weekStartOf(LocalDate.now())); private set
    var segments by mutableStateOf<List<ScheduleSegment>>(emptyList()); private set
    private var scheduleJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { app.api.channel(login) }
                .onSuccess { status = it; notFound = it == null }
        }
        viewModelScope.launch { app.prefs.settings.collect { favorites = it.favorites } }
        viewModelScope.launch { app.history.positions.collect { positions = it } }
        videos.refresh(showIndicator = false)
    }

    fun toggleFavorite() {
        viewModelScope.launch { app.prefs.toggleFavorite(login) }
    }

    fun showWeek(start: LocalDate) {
        weekStart = start
    }

    fun loadClips() {
        if (!clips.loadedOnce && !clips.loading) clips.refresh(showIndicator = false)
    }

    fun chooseClipPeriod(period: ClipPeriod) {
        clipPeriod = period
        clips.refresh()
    }

    fun loadSchedule() {
        if (scheduleJob != null) return
        scheduleJob = viewModelScope.launch {
            segments = runCatching { app.api.schedule(login) }.getOrDefault(emptyList())
        }
    }
}

@Composable
fun ChannelScreen(login: String, nav: Nav) {
    val app = LocalContext.current.app
    val vm = viewModel { ChannelViewModel(app, login) }
    val st = vm.status
    var tab by rememberSaveable { mutableIntStateOf(1) }
    val listState = rememberLazyListState()
    LoadMoreEffect(listState) { if (tab == 1) vm.videos.loadMore() }
    LaunchedEffect(tab) {
        if (tab == 2) vm.loadClips()
        if (tab != 0) return@LaunchedEffect
        vm.loadSchedule()
        val index = snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "week" }?.index }.filterNotNull().first()
        listState.animateScrollToItem(index)
    }
    val locale = currentLocale()
    val zone = remember { ZoneId.systemDefault() }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(
                st?.channel?.displayName ?: login,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FavoriteButton(vm.isFavorite, vm::toggleFavorite)
        }
        if (vm.notFound) {
            CenterMessage(stringResource(R.string.channel_not_found))
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize().pointerInput(Unit) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = {
                        if (abs(dx) < 48.dp.toPx()) return@detectHorizontalDragGestures
                        val forward = dx < 0
                        val tabsVisible = listState.layoutInfo.visibleItemsInfo.any { it.key == "tabs" }
                        if (tabsVisible) tab = (if (forward) tab + 1 else tab - 1).coerceIn(0, 2)
                        else if (tab == 0) vm.showWeek(if (forward) vm.weekStart.plusWeeks(1) else vm.weekStart.minusWeeks(1))
                    },
                ) { change, drag -> dx += drag; change.consume() }
            },
            state = listState,
        ) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(st?.channel?.avatarUrl, 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(login, style = MaterialTheme.typography.bodySmall, color = C.Muted)
                        st?.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = C.Text.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        if (st != null && st.stream == null && st.lastBroadcastAt != null) {
                            Text(stringResource(R.string.last_live, formatAgo(st.lastBroadcastAt, locale)), style = MaterialTheme.typography.labelSmall, color = C.Faint)
                        }
                    }
                }
            }
            st?.stream?.let { s ->
                item(key = "live-h") { SectionLabel(stringResource(R.string.section_live)) }
                item(key = "live") { StreamRow(s, onClick = { nav.stream(login) }, showGame = true) }
            }
            item(key = "tabs") {
                Spacer(Modifier.height(8.dp))
                SplitTabs(listOf(stringResource(R.string.schedule), stringResource(R.string.section_videos), stringResource(R.string.clips)), tab) { tab = it }
            }
            when (tab) {
                0 -> weekItems(vm, nav, locale, zone)
                1 -> videoItems(vm, nav)
                else -> clipItems(vm, nav)
            }
        }
    }
}

private fun LazyListScope.videoItems(vm: ChannelViewModel, nav: Nav) {
    val p = vm.videos
    items(p.items, key = { "v-" + it.id }) { v ->
        val watched = vm.positions[v.id]?.takeIf { v.lengthSeconds > 0 }?.let { it.positionMs / (v.lengthSeconds * 1000f) }
        VideoRow(v, watched) { nav.vod(v.id) }
    }
    when {
        p.error != null -> item(key = "v-err") { ErrorFooter(p.error!!) { if (p.items.isEmpty()) p.refresh() else p.loadMore() } }
        p.loading -> item(key = "v-loading") { FooterLoading() }
        p.loadedOnce && p.items.isEmpty() -> item(key = "v-empty") {
            Text(stringResource(R.string.videos_empty), color = C.Faint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
        }
    }
}

private fun LazyListScope.clipItems(vm: ChannelViewModel, nav: Nav) {
    val p = vm.clips
    item(key = "c-filter") {
        Column(Modifier.padding(vertical = 4.dp)) {
            ChipRow(ClipPeriod.entries.map { it to stringResource(it.label) }, vm.clipPeriod, vm::chooseClipPeriod)
        }
    }
    items(p.items, key = { "c-" + it.slug }) { c -> ClipRow(c) { nav.clip(c.slug) } }
    when {
        p.error != null -> item(key = "c-err") { ErrorFooter(p.error!!) { p.refresh() } }
        p.loading -> item(key = "c-loading") { FooterLoading() }
        p.loadedOnce && p.items.isEmpty() -> item(key = "c-empty") {
            Text(stringResource(R.string.clips_empty), color = C.Faint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
        }
    }
}

private fun LazyListScope.weekItems(vm: ChannelViewModel, nav: Nav, locale: Locale, zone: ZoneId) {
    val p = vm.videos
    if (p.items.isEmpty() && !p.loadedOnce) {
        item(key = "w-loading") { FooterLoading() }
        return
    }
    item(key = "week") {
        val table = buildWeek(p.items, vm.segments, vm.weekStart, zone)
        WeekScheduleView(
            modifier = Modifier.fillParentMaxHeight(),
            table = table,
            today = LocalDate.now(zone),
            zone = zone,
            locale = locale,
            onPrev = { vm.showWeek(vm.weekStart.minusWeeks(1)) },
            onNext = { vm.showWeek(vm.weekStart.plusWeeks(1)) },
            onToday = { vm.showWeek(weekStartOf(LocalDate.now(zone))) },
            onOpenVod = nav.vod,
        )
    }
    p.error?.let { e -> item(key = "w-err") { ErrorFooter(e) { p.refresh() } } }
}
