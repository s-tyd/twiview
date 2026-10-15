package dev.twiview.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.twiview.R
import dev.twiview.app
import dev.twiview.data.WebSession
import dev.twiview.ui.theme.C
import kotlinx.coroutines.launch

@Composable
fun AccountSection() {
    val context = LocalContext.current
    val web = context.app.web
    val acc by web.state.collectAsState()
    val clientId by web.clientId.collectAsState()
    val scope = rememberCoroutineScope()

    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val a = acc
            if (a == null) {
                Text(stringResource(R.string.not_logged_in), style = MaterialTheme.typography.bodyMedium, color = C.Muted)
            } else {
                Text(a.login, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(if (a.appToken != null) R.string.account_scope_full else R.string.account_scope_no_follows),
                    style = MaterialTheme.typography.bodySmall,
                    color = C.Muted,
                )
            }
        }
        if (acc == null) {
            Button(onClick = { context.startActivity(Intent(context, WebLoginActivity::class.java)) }) {
                Text(stringResource(R.string.login_with_twitch))
            }
        } else {
            if (acc?.appToken == null && clientId != null) {
                Button(onClick = { context.startActivity(Intent(context, WebLoginActivity::class.java)) }) {
                    Text(stringResource(R.string.connect_follows))
                }
                Spacer(Modifier.padding(4.dp))
            }
            OutlinedButton(onClick = { scope.launch { web.clear() } }) { Text(stringResource(R.string.log_out)) }
        }
    }
    ClientIdRow(clientId) { scope.launch { web.setClientId(it) } }
}

@Composable
private fun ClientIdRow(clientId: String?, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.client_id_title), style = MaterialTheme.typography.bodyMedium)
            Text(
                clientId?.let { it.take(6) + "…" + it.takeLast(4) } ?: stringResource(R.string.client_id_missing),
                style = MaterialTheme.typography.bodySmall,
                color = if (clientId == null) C.Notice else C.Muted,
            )
        }
        OutlinedButton(onClick = { editing = true }) { Text(stringResource(if (clientId == null) R.string.client_id_add else R.string.client_id_change)) }
    }
    if (editing) ClientIdDialog(clientId.orEmpty(), onDismiss = { editing = false }) { onSave(it); editing = false }
}

@Composable
private fun ClientIdDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initial) }
    val id = text.trim()
    val valid = WebSession.isValidClientId(id)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Surface2,
        title = { Text(stringResource(R.string.client_id_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.client_id_help, WebSession.APP_REDIRECT),
                    style = MaterialTheme.typography.bodySmall,
                    color = C.Muted,
                )
                TextButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://dev.twitch.tv/console/apps/create".toUri())) },
                    contentPadding = PaddingValues(0.dp),
                ) { Text(stringResource(R.string.open_dev_console)) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = id.isNotEmpty() && !valid,
                    supportingText = { if (id.isNotEmpty() && !valid) Text(stringResource(R.string.client_id_invalid)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(id) }, enabled = valid) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
