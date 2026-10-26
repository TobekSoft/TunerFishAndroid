package com.tobeksoft.tunerfish.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tobeksoft.tunerfish.TunerFishApp
import com.tobeksoft.tunerfish.core.Tone
import com.tobeksoft.tunerfish.playback.ToneLevels
import kotlin.math.roundToInt

/**
 * The tone button on Now Playing: opens a panel with Bass, Treble and Flat. Changes are heard at
 * once (ToneLevels) and saved for next time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToneButton() {
    val app = LocalContext.current.applicationContext as TunerFishApp
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Default.Tune, "Bass and treble") }
    if (!open) return

    var bass by remember { mutableIntStateOf(app.settings.bass) }
    var treble by remember { mutableIntStateOf(app.settings.treble) }
    fun change(newBass: Int, newTreble: Int) {
        bass = newBass; treble = newTreble
        ToneLevels.bass = newBass; ToneLevels.treble = newTreble
        app.settings.bass = newBass; app.settings.treble = newTreble
    }

    ModalBottomSheet(onDismissRequest = { open = false }) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                "Sound", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ToneSlider("Bass", bass) { change(it, treble) }
            ToneSlider("Treble", treble) { change(bass, it) }
            TextButton(
                onClick = { change(0, 0) },
                enabled = bass != 0 || treble != 0,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) { Text("Flat") }
        }
    }
}

/** One tone control: -12 to +12 dB in whole steps, the level shown on the right. */
@Composable
private fun ToneSlider(label: String, db: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(64.dp))
        Slider(
            value = db.toFloat(),
            onValueChange = { v -> val n = v.roundToInt(); if (n != db) onChange(n) },
            valueRange = -Tone.MAX_DB.toFloat()..Tone.MAX_DB.toFloat(),
            steps = Tone.MAX_DB * 2 - 1,
            modifier = Modifier.weight(1f),
        )
        Text(Tone.label(db), Modifier.width(64.dp), textAlign = TextAlign.End)
    }
}
