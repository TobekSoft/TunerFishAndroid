package com.tobeksoft.tunerfish.ui

import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.core.net.toUri
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tobeksoft.tunerfish.LibraryViewModel
import com.tobeksoft.tunerfish.TunerFishApp
import com.tobeksoft.tunerfish.core.DownloadLimit
import com.tobeksoft.tunerfish.core.StorageNames
import com.tobeksoft.tunerfish.core.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(library: LibraryViewModel, onBack: () -> Unit, onOpenLog: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TunerFishApp
    var folder by remember { mutableStateOf(app.settings.downloadFolder) }
    var clearQueue by remember { mutableStateOf(app.settings.clearQueueOnExit) }
    var wifiOnly by remember { mutableStateOf(app.settings.wifiOnly) }
    var limit by remember { mutableStateOf(app.settings.downloadLimit) }
    var replaceSilently by remember { mutableStateOf(app.settings.replaceOtherCopies) }
    var limitOpen by remember { mutableStateOf(false) }

    val pickFolder = rememberFolderPicker { folder = it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Section("Theme")
            for (mode in ThemeMode.entries) {
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = app.theme == mode, role = Role.RadioButton) { app.changeTheme(mode) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = app.theme == mode, onClick = null)
                    Text(mode.name, Modifier.padding(start = 16.dp))
                }
            }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("Queue")
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(clearQueue, role = Role.Switch) { clearQueue = it; app.settings.clearQueueOnExit = it }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Clear queue on exit")
                    Text(
                        if (clearQueue) "The app starts with an empty queue."
                        else "The queue and the place in the track are kept for next time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = clearQueue, onCheckedChange = null)
            }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("Network")
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(wifiOnly, role = Role.Switch) {
                        wifiOnly = it
                        app.settings.wifiOnly = it
                        app.downloads.start(restart = true)
                        library.checkNetwork()
                    }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Wi-Fi only")
                    Text(
                        if (wifiOnly) "Without Wi-Fi, only the albums on this phone. Nothing streams or downloads."
                        else "Streams and downloads on any network. Big albums ask first on mobile data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = wifiOnly, onCheckedChange = null)
            }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section("Download folder")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val name = folder?.let { describeFolder(context, it) }
                Text(name ?: "App storage")
                Text(
                    if (folder == null) "Inside the app's own storage. Android deletes it if the app is uninstalled."
                    else "Downloaded albums are kept here, and stay if the app is uninstalled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickFolder.launch(folder?.let(Uri::parse)) }) { Text("Choose folder…") }
                    if (folder != null) {
                        TextButton(onClick = {
                            useAppStorage(context)
                            folder = null
                        }) { Text("Use app storage") }
                    }
                }
                // Past the limit, albums that aren't kept go, the longest unplayed first.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Space for downloads", Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { limitOpen = true }) { Text(limit.label) }
                        DropdownMenu(limitOpen, onDismissRequest = { limitOpen = false }) {
                            for (l in DownloadLimit.entries) {
                                DropdownMenuItem(text = { Text(l.label) }, onClick = {
                                    limitOpen = false
                                    limit = l
                                    app.settings.downloadLimit = l
                                    app.downloads.cleanUpSoon()
                                })
                            }
                        }
                    }
                }
                Text(
                    "When downloads pass this, albums you haven't kept are removed, the longest unplayed first. " +
                        "Albums in the queue stay.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Advanced: by default the app asks before replacing another app's copy of an album.
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(replaceSilently, role = Role.Switch) { replaceSilently = it; app.settings.replaceOtherCopies = it }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Replace other apps' copies without asking")
                    Text(
                        "If another app has already put an album in the download folder, its music files and pictures " +
                            "are deleted and replaced with TunerFish's download. Off: you're asked each time.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = replaceSilently, onCheckedChange = null)
            }

            HorizontalDivider(Modifier.padding(top = 16.dp))
            Section("Account")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(app.settings.userName ?: "")
                Text(
                    "Server addresses, tried in this order. Add one for each way you reach the same server, " +
                        "e.g. home network then NetBird. The first that answers is used.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ServerAddresses(library)
            OutlinedButton(onClick = library::signOut, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("Sign out")
            }

            HorizontalDivider(Modifier.padding(top = 16.dp))
            Section("Problems")
            Text(
                "The app log has what happened (connections, errors, downloads), to share when reporting a problem.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            OutlinedButton(onClick = onOpenLog, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("App log")
            }
        }
    }
}

/** The server's addresses with a reachable mark each, plus a field to add another. */
@Composable
private fun ServerAddresses(library: LibraryViewModel) {
    var newAddress by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { library.checkAddresses() }

    for ((i, address) in library.addresses.withIndex()) {
        val status = library.addressStatus[address]
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(address, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        status == null -> "Checking…"
                        !status -> "Not reachable"
                        address == library.currentAddress -> "Reachable · in use"
                        else -> "Reachable"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (i > 0) IconButton(onClick = { library.moveAddressUp(address) }) { Icon(Icons.Default.ArrowUpward, "Try this one earlier") }
            if (library.addresses.size > 1) IconButton(onClick = { library.removeAddress(address) }) { Icon(Icons.Default.Close, "Remove") }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            newAddress, { newAddress = it },
            placeholder = { Text("e.g. 100.64.0.5:8096") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { library.addAddress(newAddress); newAddress = "" }),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { library.addAddress(newAddress); newAddress = "" }, enabled = newAddress.isNotBlank()) {
            Text("Add")
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

/**
 * Android's folder picker for the download folder. The app keeps read/write access to the chosen
 * folder across restarts (and gives up the old one). Launch it with the current folder, or null.
 */
@Composable
fun rememberFolderPicker(onPicked: (String) -> Unit): ManagedActivityResultLauncher<Uri?, Uri?> {
    val context = LocalContext.current
    val app = context.applicationContext as TunerFishApp
    return rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        releaseFolder(context, app.settings.downloadFolder, keep = uri)
        app.settings.downloadFolder = uri.toString()
        app.settings.downloadFolderChosen = true
        onPicked(uri.toString())
    }
}

/** Downloads go to the app's own storage from now on. */
fun useAppStorage(context: android.content.Context) {
    val app = context.applicationContext as TunerFishApp
    releaseFolder(context, app.settings.downloadFolder, keep = null)
    app.settings.downloadFolder = null
    app.settings.downloadFolderChosen = true
}

/** "Internal storage/Music/TunerFish", or a warning if the app has lost access to the folder. */
private fun describeFolder(context: android.content.Context, uriText: String): String {
    val uri = uriText.toUri()
    val stillAllowed = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    val name = runCatching { StorageNames.describe(DocumentsContract.getTreeDocumentId(uri)) }.getOrDefault(uriText)
    return if (stillAllowed) name else "$name (no longer available — choose it again)"
}

/**
 * Gives back access to the previous folder, so the app doesn't pile up folder permissions.
 * Not while downloads still live there: they keep playing from the old folder.
 */
private fun releaseFolder(context: android.content.Context, uriText: String?, keep: Uri?) {
    val uri = uriText?.let(Uri::parse) ?: return
    if (uri == keep) return
    if ((context.applicationContext as TunerFishApp).db.hasDownloadsIn(uriText)) return
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
}
