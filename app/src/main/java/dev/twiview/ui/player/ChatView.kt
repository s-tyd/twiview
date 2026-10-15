package dev.twiview.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import coil3.compose.AsyncImage
import dev.twiview.R
import dev.twiview.app
import dev.twiview.data.BadgeMode
import dev.twiview.data.ChatMessage
import dev.twiview.data.DeletedMode
import dev.twiview.data.EmoteMode
import dev.twiview.data.NameColorMode
import dev.twiview.data.NameMode
import dev.twiview.data.Separator
import dev.twiview.data.Settings
import dev.twiview.ui.theme.C
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChatPanel(
    vm: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    var selectedUser by remember { mutableStateOf<ChatMessage?>(null) }
    val settings = vm.settings
    val myLogin = LocalContext.current.app.web.state.collectAsState().value?.login
    val focus = LocalFocusManager.current
    val parity = remember { HashMap<String, Boolean>() }
    val counter = remember { intArrayOf(0) }
    val visible = remember(
        vm.messages, settings.ngUsers, settings.ngWords, settings.deletedMode, settings.showSystemNotices,
    ) {
        val list = vm.messages.filterNot {
            settings.isNg(it) ||
                (it.deleted && settings.deletedMode == DeletedMode.Hide) ||
                (it.systemText != null && !settings.showSystemNotices)
        }
        for (m in list) parity.getOrPut(m.id) { (counter[0]++ % 2) == 1 }
        if (parity.size > 2 * PlayerViewModel.MAX_MESSAGES) {
            val keep = list.mapTo(HashSet()) { it.id }
            parity.keys.retainAll(keep)
        }
        list.asReversed()
    }
    Box(modifier) {
        ChatList(visible, settings, vm.badges, myLogin, { parity[it] == true }, onUser = { focus.clearFocus(); selectedUser = it })
        if (!vm.chatConnected && vm.messages.isEmpty()) {
            Text(
                stringResource(R.string.chat_connecting),
                color = C.Faint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
    selectedUser?.let { u ->
        UserSheet(
            user = u,
            messages = vm.messages.filter { it.login == u.login },
            settings = settings,
            badges = vm.badges,
            onDismiss = { selectedUser = null },
            onNg = { vm.addNgUser(u.login); selectedUser = null },
        )
    }
}

@Composable
private fun ChatList(
    messages: List<ChatMessage>,
    settings: Settings,
    badges: Map<String, String>,
    myLogin: String?,
    shade: (String) -> Boolean,
    onUser: (ChatMessage) -> Unit,
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var stick by remember { mutableStateOf(true) }
    val dragged by state.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) {
        if (dragged) stick = false else if (!state.isScrollInProgress) stick = state.firstVisibleItemIndex == 0
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.filter { !it }.collect { stick = state.firstVisibleItemIndex == 0 }
    }
    val newestId = messages.firstOrNull()?.id
    LaunchedEffect(newestId) {
        if (stick && newestId != null) state.scrollToItem(0)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(messages, key = { it.id }) { m ->
                ChatLine(
                    m, settings, badges, onUser, myLogin,
                    shaded = settings.separator == Separator.Zebra && shade(m.id),
                )
            }
        }
        val behind by remember { derivedStateOf { state.firstVisibleItemIndex } }
        if (!stick && behind > 0) {
            Text(
                stringResource(R.string.chat_jump_latest, behind),
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .background(C.Accent, RoundedCornerShape(50))
                    .clickable {
                        stick = true
                        scope.launch { state.scrollToItem(0) }
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun ChatLine(
    m: ChatMessage,
    settings: Settings,
    badges: Map<String, String>,
    onUser: ((ChatMessage) -> Unit)?,
    myLogin: String? = null,
    shaded: Boolean = false,
) {
    val s = settings
    if (m.deleted && s.deletedMode == DeletedMode.Hide) return
    if (m.systemText != null && !s.showSystemNotices) return
    val fs = s.chatFontSize
    val nameColor = remember(m.color, m.login, s.nameColorMode, s.brightenNames) { nameColorFor(m, s) }
    val aligned = s.alignNames && s.nameMode != NameMode.Hidden
    val deletedText = stringResource(R.string.chat_deleted)
    val parts = remember(m, s, badges, nameColor, onUser != null, deletedText) {
        buildParts(m, s, badges, nameColor, if (aligned) null else onUser, deletedText)
    }
    val lineHeight = when (s.lineSpacing) { 0 -> 1.2f; 2 -> 1.7f; else -> 1.45f }
    val vPad = when (s.lineSpacing) { 0 -> 0.dp; 2 -> 4.dp; else -> 2.dp }
    val mention = s.highlightMentions && !myLogin.isNullOrEmpty() && m.login != myLogin &&
        m.text.contains("@$myLogin", ignoreCase = true)
    val bg = when {
        mention -> C.Accent.copy(alpha = 0.16f)
        s.highlightFirstMessage && m.firstMessage -> C.Notice.copy(alpha = 0.09f)
        shaded -> Color.White.copy(alpha = 0.035f)
        else -> Color.Transparent
    }
    val rowMod = Modifier
        .fillMaxWidth()
        .background(bg)
        .then(
            if (s.separator == Separator.Line) Modifier.drawBehind {
                drawLine(C.Line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
            } else Modifier,
        )
        .then(if (onUser != null) Modifier.clickable { onUser(m) } else Modifier)

    val line = @Composable {
        if (aligned) {
            val nameWidth = with(LocalDensity.current) { (fs * s.nameWidth).sp.toDp() }
            Row(Modifier.padding(horizontal = 10.dp, vertical = vPad)) {
                parts.time?.let {
                    Text(it, color = C.Faint, fontSize = (fs - 2).sp, lineHeight = (fs * lineHeight).sp, maxLines = 1)
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    parts.header,
                    inlineContent = parts.inline,
                    fontSize = fs.sp,
                    lineHeight = (fs * lineHeight).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .width(nameWidth)
                        .then(if (onUser != null) Modifier.clickable { onUser(m) } else Modifier),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    parts.body,
                    inlineContent = parts.inline,
                    fontSize = fs.sp,
                    lineHeight = (fs * lineHeight).sp,
                    color = C.Text,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Text(
                parts.full,
                inlineContent = parts.inline,
                fontSize = fs.sp,
                lineHeight = (fs * lineHeight).sp,
                color = C.Text,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = vPad),
            )
        }
    }
    if (m.systemText != null) {
        Row(rowMod.height(IntrinsicSize.Min).padding(vertical = 3.dp).background(C.Notice.copy(alpha = 0.06f))) {
            Box(Modifier.width(3.dp).heightIn(min = 20.dp).fillMaxHeight().background(C.Notice))
            Column(Modifier.padding(vertical = 3.dp)) {
                if (m.systemText.isNotEmpty()) {
                    Text(
                        m.systemText,
                        color = C.Notice,
                        fontSize = (fs - 1).sp,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                }
                if (m.text.isNotEmpty()) line()
            }
        }
    } else {
        Box(rowMod) { line() }
    }
}

private class LineParts(
    val header: AnnotatedString,
    val body: AnnotatedString,
    val full: AnnotatedString,
    val time: String?,
    val inline: Map<String, InlineTextContent>,
)

private val clockMin = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val clockSec = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

private fun displayNameFor(m: ChatMessage, mode: NameMode): String = when (mode) {
    NameMode.DisplayOnly -> m.displayName
    NameMode.LoginOnly -> m.login.ifEmpty { m.displayName }
    NameMode.Hidden -> ""
    NameMode.Both ->
        if (m.displayName.equals(m.login, ignoreCase = true) || m.login.isEmpty()) m.displayName
        else "${m.displayName}(${m.login})"
}

private fun buildParts(
    m: ChatMessage,
    s: Settings,
    badges: Map<String, String>,
    nameColor: Color,
    onUser: ((ChatMessage) -> Unit)?,
    deletedText: String,
): LineParts {
    val fs = s.chatFontSize
    val inline = HashMap<String, InlineTextContent>()
    val time = if (s.showTimestamps) {
        (if (s.timestampSeconds) clockSec else clockMin).format(Instant.ofEpochMilli(m.timestamp))
    } else null

    val header = buildAnnotatedString {
        if (s.badgeMode != BadgeMode.None) {
            val shown = m.badges.filter { badges.containsKey(it) }
                .let { if (s.badgeMode == BadgeMode.First) it.take(1) else it }
            for (b in shown) {
                val key = "b:$b"
                if (key !in inline) {
                    val url = badges.getValue(b)
                    inline[key] = InlineTextContent(
                        Placeholder((fs * 1.15f).sp, (fs * 1.15f).sp, PlaceholderVerticalAlign.TextCenter),
                    ) { AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize()) }
                }
                appendInlineContent(key, " ")
                append(" ")
            }
        }
        val name = displayNameFor(m, s.nameMode)
        if (name.isNotEmpty()) {
            val nameStyle = SpanStyle(color = nameColor, fontWeight = FontWeight.Bold)
            if (onUser != null) {
                withLink(LinkAnnotation.Clickable("user") { onUser(m) }) { withStyle(nameStyle) { append(name) } }
            } else {
                withStyle(nameStyle) { append(name) }
            }
        }
    }

    val body = buildAnnotatedString {
        if (m.deleted && s.deletedMode == DeletedMode.Placeholder) {
            withStyle(SpanStyle(color = C.Faint, fontStyle = FontStyle.Italic)) { append(deletedText) }
            return@buildAnnotatedString
        }
        val bodyStyle = when {
            m.deleted -> SpanStyle(color = C.Faint, textDecoration = TextDecoration.LineThrough)
            m.isAction -> SpanStyle(color = nameColor, fontStyle = FontStyle.Italic)
            else -> SpanStyle()
        }
        val emoteScale = when (s.emoteSize) { 0 -> 1.4f; 2 -> 2.3f; else -> 1.75f }
        withStyle(bodyStyle) {
            val cps = m.text.codePoints().toArray()
            var pos = 0
            for (e in m.emotes) {
                if (e.start < pos || e.end >= cps.size || e.start > e.end) continue
                if (e.start > pos) append(String(cps, pos, e.start - pos))
                val code = String(cps, e.start, e.end - e.start + 1)
                if (s.emoteMode == EmoteMode.Text) {
                    append(code)
                } else {
                    val key = "e:${e.id}"
                    if (key !in inline) {
                        inline[key] = InlineTextContent(
                            Placeholder((fs * emoteScale).sp, (fs * emoteScale).sp, PlaceholderVerticalAlign.TextCenter),
                        ) {
                            AsyncImage(
                                model = "https://static-cdn.jtvnw.net/emoticons/v2/${e.id}/default/dark/2.0",
                                contentDescription = code,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    appendInlineContent(key, code)
                }
                pos = e.end + 1
            }
            if (pos < cps.size) append(String(cps, pos, cps.size - pos))
        }
    }

    val full = buildAnnotatedString {
        time?.let { withStyle(SpanStyle(color = C.Faint, fontSize = (fs - 2).sp)) { append(it); append(" ") } }
        append(header)
        if (s.nameMode != NameMode.Hidden) append(if (m.isAction) " " else ": ")
        append(body)
    }
    return LineParts(header, body, full, time, inline)
}

private val fallbackPalette = listOf(
    0xFFFF7A7A, 0xFF7AB8FF, 0xFF7CE38B, 0xFFFFB86B, 0xFFE38BFF, 0xFF6BE4E0, 0xFFFFE06B, 0xFFB0A8FF,
).map { Color(it) }

private val uniformNameColor = Color(0xFFC4C4D0)

private fun paletteColor(login: String) = fallbackPalette[(login.hashCode() and 0x7fffffff) % fallbackPalette.size]

private fun nameColorFor(m: ChatMessage, s: Settings): Color = when (s.nameColorMode) {
    NameColorMode.Uniform -> uniformNameColor
    NameColorMode.Auto -> paletteColor(m.login)
    NameColorMode.Twitch -> when {
        m.color == null -> paletteColor(m.login)
        s.brightenNames -> readableColor(m.color)
        else -> Color(m.color)
    }
}

private fun readableColor(argb: Int): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    if (hsl[2] < 0.62f) hsl[2] = 0.62f
    return Color(ColorUtils.HSLToColor(hsl))
}

@Composable
private fun UserSheet(
    user: ChatMessage,
    messages: List<ChatMessage>,
    settings: Settings,
    badges: Map<String, String>,
    onDismiss: () -> Unit,
    onNg: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(user.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(user.login, style = MaterialTheme.typography.bodySmall, color = C.Muted)
                }
                OutlinedButton(onClick = onNg) { Text(stringResource(R.string.ng_user_button), color = C.Live) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.user_messages_count, messages.size),
                style = MaterialTheme.typography.labelSmall,
                color = C.Faint,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(messages.asReversed(), key = { it.id }) { m ->
                    ChatLine(m, settings.copy(showTimestamps = true), badges, onUser = null)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
