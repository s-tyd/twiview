package dev.twiview.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.twiview.AppLanguage
import dev.twiview.R
import dev.twiview.app
import dev.twiview.ui.common.currentLocale
import dev.twiview.ui.home.streamFilterLabel
import dev.twiview.ui.home.streamFilterOrder
import dev.twiview.ui.theme.C
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableColumn

@Composable
fun StreamOrderPage(onBack: () -> Unit) {
    val prefs = LocalContext.current.app.prefs
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val saved by remember { prefs.settings.map { it.streamOrder } }.collectAsState(initial = null)
    val mine = AppLanguage.streamDefault(currentLocale())
    var order by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(saved, mine) { saved?.let { order = streamFilterOrder(it, mine) } }

    SettingsPage(stringResource(R.string.stream_order), onBack) {
        ReorderableColumn(
            list = order,
            onSettle = { from, to ->
                order = order.toMutableList().apply { add(to, removeAt(from)) }
                scope.launch { prefs.setStreamOrder(order) }
            },
            onMove = { haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) },
            modifier = Modifier.padding(vertical = 4.dp),
        ) { _, tag, dragging ->
            key(tag) {
                ReorderableItem {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .background(if (dragging) C.Line else C.Bg)
                            .longPressDraggableHandle()
                            .padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(streamFilterLabel(tag)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = null,
                            tint = C.Faint,
                            modifier = Modifier.draggableHandle().padding(16.dp),
                        )
                    }
                }
            }
        }
        if (saved?.isNotEmpty() == true) {
            TextButton(onClick = { scope.launch { prefs.setStreamOrder(emptyList()) } }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.stream_order_reset))
            }
        }
    }
}
