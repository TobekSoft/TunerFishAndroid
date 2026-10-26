package com.tobeksoft.tunerfish.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tobeksoft.tunerfish.R
import com.tobeksoft.tunerfish.TunerFishApp

/** Opened from the fish or menu ⋮ → About on the artists screen; laid out like the desktop's About window. */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as TunerFishApp
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // The desktop's About logo (fish + music bubbles), from its branding folder.
                Image(painterResource(R.drawable.tunerfish_logo), null, Modifier.size(176.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Version ${app.version}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Album-first music player for Jellyfin.", textAlign = TextAlign.Center)
                Text("Design by Tobeksoft. Code by Claude (Anthropic).", textAlign = TextAlign.Center)
                Text("© 2026 Tobeksoft. MIT License.", textAlign = TextAlign.Center)
                Text(
                    "Playback by Media3 ExoPlayer. Jellyfin icon © Jellyfin contributors, CC BY-SA 4.0.", textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
