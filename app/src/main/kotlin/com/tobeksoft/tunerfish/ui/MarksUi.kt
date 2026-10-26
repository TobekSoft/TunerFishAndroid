package com.tobeksoft.tunerfish.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tobeksoft.tunerfish.core.Marks

// The user's marks on artists and albums: ♡ favourite (perennial), 👍 like (into it now), and hidden (out of the way while browsing). See core's Marks.

@Composable
private fun heart(on: Boolean) = if (on) Icons.Default.Favorite else Icons.Default.FavoriteBorder

private fun thumb(on: Boolean) = if (on) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp
private fun thumbDown(on: Boolean) = if (on) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown

/** The three marks as menu items, for long-press and ⋮ menus. [what] names the thing: "album", "artist". */
@Composable
fun MarkMenuItems(marks: Marks, what: String, onChange: (Marks) -> Unit, close: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Favourite") },
        leadingIcon = { Icon(heart(marks.favourite), null) },
        trailingIcon = { if (marks.favourite) Icon(Icons.Default.Check, "On") },
        onClick = { close(); onChange(marks.withFavourite(!marks.favourite)) },
    )
    DropdownMenuItem(
        text = { Text("Like") },
        leadingIcon = { Icon(thumb(marks.liked), null) },
        trailingIcon = { if (marks.liked) Icon(Icons.Default.Check, "On") },
        onClick = { close(); onChange(marks.withLiked(!marks.liked)) },
    )
    DropdownMenuItem(
        text = { Text(if (marks.hidden) "Unhide $what" else "Hide $what") },
        leadingIcon = { Icon(thumbDown(marks.hidden), null) },
        onClick = { close(); onChange(marks.withHidden(!marks.hidden)) },
    )
}

/** Heart, thumbs up and thumbs down (hide) as toggle buttons (the album screen). */
@Composable
fun MarkButtons(marks: Marks, onChange: (Marks) -> Unit) {
    IconButton(onClick = { onChange(marks.withFavourite(!marks.favourite)) }) {
        Icon(heart(marks.favourite), if (marks.favourite) "Favourite: on" else "Favourite: off")
    }
    IconButton(onClick = { onChange(marks.withLiked(!marks.liked)) }) {
        Icon(thumb(marks.liked), if (marks.liked) "Like: on" else "Like: off")
    }
    IconButton(onClick = { onChange(marks.withHidden(!marks.hidden)) }) {
        Icon(thumbDown(marks.hidden), if (marks.hidden) "Hidden: on" else "Hidden: off")
    }
}

/** Small marks beside a name in a list; nothing when there are none. Hidden shows only while hidden things are shown. */
@Composable
fun MarkBadges(marks: Marks, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    if (marks == Marks.None) return
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (marks.favourite) Icon(Icons.Default.Favorite, "Favourite", Modifier.size(size), tint)
        if (marks.liked) Icon(Icons.Filled.ThumbUp, "Liked", Modifier.size(size), tint)
        if (marks.hidden) Icon(Icons.Filled.ThumbDown, "Hidden", Modifier.size(size), tint)
    }
}
