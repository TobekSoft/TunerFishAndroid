package com.tobeksoft.tunerfish.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tobeksoft.tunerfish.R
import com.tobeksoft.tunerfish.TunerFishApp
import com.tobeksoft.tunerfish.core.AppLog
import java.time.ZoneId

/** Settings → App log: what the app noted, newest first. Share sends it as text, by whatever app the user picks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TunerFishApp
    var entries by remember { mutableStateOf(app.db.log().reversed()) }
    val zone = remember { ZoneId.systemDefault() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App log") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { shareLog(context) }) { Icon(Icons.Default.Share, "Share the log") }
                    TextButton(onClick = { app.db.clearLog(); entries = emptyList() }, enabled = entries.isNotEmpty()) { Text("Clear") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "What the app noted: connections, errors, downloads. No passwords or sign-in tokens. " +
                    "Share it when reporting a problem.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries) { e ->
                    Text(
                        AppLog.line(e, zone), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** Opens Android's share sheet with the log as text; the user picks where it goes, or doesn't. */
fun shareLog(context: Context) {
    val app = context.applicationContext as TunerFishApp
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${context.getString(R.string.app_name)} log")
        putExtra(Intent.EXTRA_TEXT, app.logText())
    }
    context.startActivity(Intent.createChooser(send, "Share the log"))
}
