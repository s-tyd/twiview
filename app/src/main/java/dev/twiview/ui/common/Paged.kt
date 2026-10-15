package dev.twiview.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.twiview.R
import dev.twiview.Strings
import dev.twiview.data.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class Paged<T>(
    private val scope: CoroutineScope,
    private val key: (T) -> String,
    private val fetch: suspend (cursor: String?) -> Page<T>,
) {
    var items by mutableStateOf<List<T>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var loadedOnce by mutableStateOf(false); private set
    private var cursor: String? = null
    private var end = false
    private var job: Job? = null

    fun refresh(showIndicator: Boolean = true) {
        job?.cancel()
        job = scope.launch {
            refreshing = showIndicator
            loading = true
            try {
                val p = fetch(null)
                items = p.items.distinctBy(key)
                cursor = p.cursor
                end = p.cursor == null
                error = null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.message ?: Strings.get(R.string.error_load_failed)
            } finally {
                loading = false
                refreshing = false
                loadedOnce = true
            }
        }
    }

    fun loadMore() {
        if (loading || end || cursor == null) return
        job = scope.launch {
            loading = true
            try {
                val p = fetch(cursor)
                val seen = items.mapTo(HashSet(), key)
                items = items + p.items.filter { seen.add(key(it)) }
                cursor = p.cursor
                end = p.cursor == null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.message
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun LoadMoreEffect(state: LazyListState, onLoadMore: () -> Unit) {
    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }.distinctUntilChanged().filter { it }.collect { onLoadMore() }
    }
}

@Composable
fun LoadMoreEffect(state: LazyGridState, onLoadMore: () -> Unit) {
    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 9
        }.distinctUntilChanged().filter { it }.collect { onLoadMore() }
    }
}
