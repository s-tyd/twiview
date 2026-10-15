package dev.twiview.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.graphics.ColorUtils
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import dev.twiview.R
import dev.twiview.data.ChatMessage
import dev.twiview.data.DanmakuSettings
import dev.twiview.data.EmoteMode
import dev.twiview.data.Settings
import dev.twiview.ui.common.SectionLabel
import dev.twiview.ui.settings.SliderRow
import dev.twiview.ui.theme.C
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal interface LaneOccupant {
    val lane: Int
    val startNs: Long
    val width: Float
}

private class Bullet(
    val layout: TextLayoutResult,
    val emotes: List<Pair<Rect, String>>,
    override val lane: Int,
    override val startNs: Long,
) : LaneOccupant {
    override val width: Float get() = layout.size.width.toFloat()
}

@Composable
fun DanmakuOverlay(
    messages: List<ChatMessage>,
    chat: Settings,
    s: DanmakuSettings,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val scale = constraints.maxHeight / 1080f
        DanmakuCanvas(messages, chat, s, s.fontPx * scale, Modifier.fillMaxSize())
    }
}

@Composable
private fun DanmakuCanvas(
    messages: List<ChatMessage>,
    chat: Settings,
    s: DanmakuSettings,
    fontPx: Float,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val bullets = remember { ArrayList<Bullet>() }
    val state = remember { object { var lastId: String? = messages.lastOrNull()?.id; var width = 0f; var height = 0f } }
    var frame by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    val durationNs = (s.durationSec * 1e9).toLong()
    val density = LocalDensity.current
    val textStyle = remember(fontPx, density) {
        TextStyle(
            fontSize = with(density) { fontPx.toSp() },
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textMotion = TextMotion.Animated,
        )
    }
    val lineH = remember(textStyle) { measurer.measure("あAg😀", textStyle).size.height * 1.15f }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val emoteCache = remember { HashMap<String, ImageBitmap>() }
    val emoteLoading = remember { HashSet<String>() }
    val emotePlaceholder = remember(fontPx, density, chat.emoteMode) {
        if (chat.emoteMode == EmoteMode.Image) with(density) { Placeholder((fontPx * 1.3f).toSp(), (fontPx * 1.3f).toSp(), PlaceholderVerticalAlign.TextCenter) } else null
    }

    LaunchedEffect(messages) {
        if (state.width <= 0f) {
            state.lastId = messages.lastOrNull()?.id; return@LaunchedEffect
        }
        val start = messages.indexOfLast { it.id == state.lastId } + 1
        state.lastId = messages.lastOrNull()?.id
        val now = System.nanoTime()
        val lanes = ((state.height * s.area) / lineH).toInt().coerceAtLeast(1)
        for (m in messages.subList(start.coerceAtMost(messages.size), messages.size)) {
            if (m.systemText != null || m.deleted || chat.isNg(m) || m.text.isBlank()) continue
            if (s.hideWithEmoji && containsEmoji(m)) continue
            if (s.maxItems > 0 && bullets.size >= s.maxItems) break
            if (bullets.size >= HARD_CAP) break
            val text = danmakuText(m, s, emotePlaceholder)
            val layout = measurer.measure(text.text, textStyle, maxLines = 1, softWrap = false, placeholders = text.placeholders)
            val lane = freeLane(bullets, lanes, now, durationNs, state.width, layout.size.width.toFloat())
                ?: if (s.maxItems == DanmakuSettings.UNLIMITED) leastBusyLane(bullets, lanes, now, durationNs, state.width) else continue
            val emotes = layout.placeholderRects.mapIndexedNotNull { i, r -> r?.let { it to text.emoteIds[i] } }
            for ((_, id) in emotes) {
                if (id in emoteCache || !emoteLoading.add(id)) continue
                scope.launch { loadEmote(context, id)?.let { emoteCache[id] = it } }
            }
            bullets += Bullet(layout, emotes, lane, now)
        }
        if (bullets.isNotEmpty()) running = true
    }

    LaunchedEffect(running, durationNs) {
        if (!running) return@LaunchedEffect
        while (bullets.isNotEmpty()) {
            withFrameNanos { t ->
                bullets.removeAll { b -> System.nanoTime() - b.startNs > durationNs }
                frame = t
            }
        }
        frame = 0L
        running = false
    }

    Canvas(modifier.fillMaxSize().graphicsLayer { alpha = s.opacity }) {
        state.width = size.width
        state.height = size.height
        @Suppress("UNUSED_EXPRESSION") frame
        val now = System.nanoTime()
        for (b in bullets) {
            val w = b.layout.size.width
            val p = (now - b.startNs).toFloat() / durationNs
            val x = size.width - p * (size.width + w)
            val y = b.lane * lineH + 2f
            drawText(b.layout, color = Color.Black.copy(alpha = 0.85f), topLeft = Offset(x, y), drawStyle = Stroke(width = (fontPx / 11f).coerceIn(3f, 8f)))
            drawText(
                b.layout,
                color = if (s.useNameColor || s.showName) Color.Unspecified else Color.White,
                topLeft = Offset(x, y),
                drawStyle = Fill,
            )
            for ((r, id) in b.emotes) {
                val img = emoteCache[id] ?: continue
                drawImage(
                    img,
                    dstOffset = IntOffset((x + r.left).roundToInt(), (y + r.top).roundToInt()),
                    dstSize = IntSize(r.width.roundToInt(), r.height.roundToInt()),
                )
            }
        }
    }
}

private suspend fun loadEmote(context: android.content.Context, id: String): ImageBitmap? {
    val req = ImageRequest.Builder(context).data("https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/2.0").build()
    return (context.imageLoader.execute(req) as? SuccessResult)?.image?.toBitmap()?.asImageBitmap()
}

internal fun containsEmoji(m: ChatMessage): Boolean =
    m.emotes.isNotEmpty() || m.text.codePoints().anyMatch { isEmojiCodePoint(it) }

private fun isEmojiCodePoint(c: Int): Boolean =
    c in 0x1F000..0x1FAFF ||
        c in 0x2600..0x27BF ||
        c in 0x2300..0x23FF || c in 0x2B00..0x2BFF ||
        c == 0x200D || c == 0xFE0F || c == 0x20E3 ||
        c in 0xE0020..0xE007F

private const val HARD_CAP = 400

internal fun leastBusyLane(bullets: List<LaneOccupant>, lanes: Int, now: Long, durationNs: Long, width: Float): Int {
    val tailX = FloatArray(lanes) { Float.NEGATIVE_INFINITY }
    for (b in bullets) {
        if (b.lane >= lanes) continue
        val w = b.width
        val p = (now - b.startNs).toFloat() / durationNs
        val tail = width - p * (width + w) + w
        if (tail > tailX[b.lane]) tailX[b.lane] = tail
    }
    return tailX.indices.minBy { tailX[it] }
}

internal fun freeLane(bullets: List<LaneOccupant>, lanes: Int, now: Long, durationNs: Long, width: Float, newW: Float): Int? {
    val gap = 24f
    val last = arrayOfNulls<LaneOccupant>(lanes)
    for (b in bullets) {
        if (b.lane >= lanes) continue
        val cur = last[b.lane]
        if (cur == null || b.startNs > cur.startNs) last[b.lane] = b
    }
    val d = durationNs.toFloat()
    for (i in 0 until lanes) {
        val prev = last[i] ?: return i
        val pw = prev.width
        val vPrev = (width + pw) / d
        val vNew = (width + newW) / d
        val tailNow = width - (now - prev.startNs) * vPrev + pw
        if (tailNow > width - gap) continue
        if (vNew > vPrev && tailNow > 0f) {
            val tExit = tailNow / vPrev
            if (width - vNew * tExit < gap) continue
        }
        return i
    }
    return null
}

private class DanmakuText(val text: AnnotatedString, val placeholders: List<AnnotatedString.Range<Placeholder>>, val emoteIds: List<String>)

private fun danmakuText(m: ChatMessage, s: DanmakuSettings, emote: Placeholder?): DanmakuText {
    val ranges = ArrayList<AnnotatedString.Range<Placeholder>>()
    val ids = ArrayList<String>()
    val text = buildAnnotatedString {
        if (s.showName) {
            val c = m.color
            val nameColor = if (s.useNameColor && c != null) {
                val hsl = FloatArray(3); ColorUtils.colorToHSL(c, hsl)
                if (hsl[2] < 0.6f) hsl[2] = 0.6f
                Color(ColorUtils.HSLToColor(hsl))
            } else Color.White.copy(alpha = 0.7f)
            withStyle(SpanStyle(color = nameColor, fontSize = 0.75.em)) { append(m.displayName); append("  ") }
        }
        val body: AnnotatedString.Builder.() -> Unit = {
            if (emote == null || m.emotes.isEmpty()) {
                append(m.text)
            } else {
                val cps = m.text.codePoints().toArray()
                var pos = 0
                for (e in m.emotes) {
                    if (e.start < pos || e.end >= cps.size || e.start > e.end) continue
                    if (e.start > pos) append(String(cps, pos, e.start - pos))
                    val from = length
                    append(String(cps, e.start, e.end - e.start + 1))
                    ranges += AnnotatedString.Range(emote, from, length)
                    ids += e.id
                    pos = e.end + 1
                }
                if (pos < cps.size) append(String(cps, pos, cps.size - pos))
            }
        }
        if (s.useNameColor && !s.showName && m.color != null) {
            val hsl = FloatArray(3); ColorUtils.colorToHSL(m.color, hsl)
            if (hsl[2] < 0.7f) hsl[2] = 0.7f
            withStyle(SpanStyle(color = Color(ColorUtils.HSLToColor(hsl)))) { body() }
        } else {
            body()
        }
    }
    return DanmakuText(text, ranges, ids)
}

@Composable
fun DanmakuSettingsContent(s: DanmakuSettings, onChange: (DanmakuSettings) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.danmaku), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(checked = s.enabled, onCheckedChange = { onChange(s.copy(enabled = it)) })
        }
        SectionLabel(stringResource(R.string.danmaku_speed))
        Chips(listOf(8f to stringResource(R.string.speed_slow), 5f to stringResource(R.string.speed_normal), 3.5f to stringResource(R.string.speed_fast)), s.durationSec) { onChange(s.copy(durationSec = it)) }
        SectionLabel(stringResource(R.string.text_size))
        Chips(listOf(
                32f to stringResource(R.string.size_small), 48f to stringResource(R.string.size_medium),
                64f to stringResource(R.string.size_large), 84f to stringResource(R.string.size_xlarge),
            ), s.fontPx) { onChange(s.copy(fontPx = it)) }
        SliderRow(
            label = stringResource(R.string.pixels),
            value = "%.1f%%".format(s.fontPx / 1080f * 100f),
            current = s.fontPx,
            range = 16f..120f,
            steps = 0,
            onChange = { onChange(s.copy(fontPx = it.roundToInt().toFloat())) },
        )
        SectionLabel(stringResource(R.string.opacity))
        Chips(listOf(1f to "100%", 0.9f to "90%", 0.7f to "70%", 0.5f to "50%", 0.3f to "30%"), s.opacity) { onChange(s.copy(opacity = it)) }
        SliderRow(
            label = stringResource(R.string.opacity),
            value = "${(s.opacity * 100).roundToInt()}%",
            current = s.opacity,
            range = 0.1f..1f,
            steps = 0,
            onChange = { onChange(s.copy(opacity = (it * 100).roundToInt() / 100f)) },
        )
        SectionLabel(stringResource(R.string.danmaku_area))
        Chips(listOf(1f to stringResource(R.string.area_full), 0.5f to stringResource(R.string.area_top_half), 0.33f to stringResource(R.string.area_top_third)), s.area) { onChange(s.copy(area = it)) }
        SectionLabel(stringResource(R.string.danmaku_max))
        val idx = maxItemSteps.indexOf(s.maxItems).let { if (it < 0) maxItemSteps.indexOf(DanmakuSettings.FIT) else it }
        SliderRow(
            label = stringResource(R.string.danmaku_limit),
            value = maxItemLabel(s.maxItems),
            current = idx.toFloat(),
            range = 0f..(maxItemSteps.size - 1).toFloat(),
            steps = maxItemSteps.size - 2,
            onChange = { onChange(s.copy(maxItems = maxItemSteps[it.roundToInt()])) },
        )
        Spacer(Modifier.height(8.dp))
        SheetToggle(stringResource(R.string.danmaku_show_name), s.showName) { onChange(s.copy(showName = it)) }
        SheetToggle(stringResource(R.string.danmaku_name_color), s.useNameColor) { onChange(s.copy(useNameColor = it)) }
        SheetToggle(stringResource(R.string.danmaku_hide_emoji), s.hideWithEmoji) { onChange(s.copy(hideWithEmoji = it)) }
    }
}

private val maxItemSteps = listOf(5, 10, 20, 30, 40, 60, 80, 100, 150, 200, 300, DanmakuSettings.FIT, DanmakuSettings.UNLIMITED)

@Composable
private fun maxItemLabel(v: Int) = when (v) {
    DanmakuSettings.FIT -> stringResource(R.string.danmaku_fit)
    DanmakuSettings.UNLIMITED -> stringResource(R.string.danmaku_unlimited)
    else -> "$v"
}

@Composable
private fun SheetToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (v, label) ->
            FilterChip(
                selected = selected == v,
                onClick = { onSelect(v) },
                label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = C.Accent.copy(alpha = 0.18f),
                    selectedLabelColor = C.Accent,
                    labelColor = C.Muted,
                ),
                border = null,
            )
        }
    }
}
