package dev.twiview.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.twiview.R
import dev.twiview.data.ChannelPoints
import dev.twiview.ui.common.currentLocale
import dev.twiview.ui.common.formatCount
import dev.twiview.ui.theme.C
import java.text.NumberFormat

@Composable
fun ChatInput(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    if (vm.loggedInAs == null || vm.isVod) return
    var text by rememberSaveable { mutableStateOf("") }
    val send = { if (vm.sendChat(text)) text = "" }
    val canSend = vm.canSend && text.isNotBlank()
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
        val narrow = maxWidth < 280.dp
        Column {
            if (narrow) vm.points?.let { PointsBalance(it, vm.bonusGained, vm.earnedHere, vm.bonusCount) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!narrow) vm.points?.let { PointsBalance(it, vm.bonusGained, vm.earnedHere, vm.bonusCount) }
                TextField(
                    value = text,
                    onValueChange = { text = it.take(500) },
                    enabled = vm.canSend,
                    placeholder = {
                        Text(
                            stringResource(if (vm.canSend) R.string.chat_send_placeholder else R.string.connecting),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    textStyle = TextStyle(fontSize = 14.sp, color = C.Text),
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = C.Surface2,
                        unfocusedContainerColor = C.Surface2,
                        disabledContainerColor = C.Surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                )
                IconButton(onClick = send, enabled = canSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send), tint = if (canSend) C.Accent else C.Faint)
                }
            }
        }
    }
}

@Composable
private fun PointsBalance(points: ChannelPoints, gained: Long?, earnedHere: Long, bonusCount: Int) {
    var open by remember { mutableStateOf(false) }
    val locale = currentLocale()
    val numbers = NumberFormat.getIntegerInstance(locale)
    val tint = if (gained != null) C.Notice else C.Muted
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PointsIcon(points.iconUrl, tint, 16.dp)
            Spacer(Modifier.width(3.dp))
            Text(
                if (gained != null) "+$gained" else formatCount(points.balance.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), locale),
                fontSize = 12.sp,
                color = tint,
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = C.Surface2,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PointsIcon(points.iconUrl, C.Accent, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(points.name ?: stringResource(R.string.channel_points), fontSize = 13.sp, color = C.Muted)
                }
                Text(
                    numbers.format(points.balance),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = C.Text,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (earnedHere > 0 || bonusCount > 0) {
                    val earned = stringResource(R.string.points_earned_here, numbers.format(earnedHere))
                    val bonus = if (bonusCount > 0) pluralStringResource(R.plurals.points_bonus_count, bonusCount, bonusCount) else ""
                    Text(
                        earned + bonus,
                        fontSize = 12.sp,
                        color = C.Notice,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PointsIcon(url: String?, tint: Color, size: Dp) {
    if (url != null) {
        AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Icon(Icons.Outlined.Toll, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}
