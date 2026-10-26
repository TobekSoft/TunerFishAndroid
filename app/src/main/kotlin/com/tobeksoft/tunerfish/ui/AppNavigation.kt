package com.tobeksoft.tunerfish.ui

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.Manifest
import androidx.compose.runtime.mutableIntStateOf
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tobeksoft.tunerfish.LibraryViewModel
import com.tobeksoft.tunerfish.TunerFishApp
import com.tobeksoft.tunerfish.R
import com.tobeksoft.tunerfish.core.DownloadNames
import com.tobeksoft.tunerfish.core.DownloadState
import com.tobeksoft.tunerfish.playback.PlayerViewModel

object Routes {
    const val SIGN_IN = "signin"
    const val ARTISTS = "artists"
    const val ALBUMS = "albums"
    const val ALBUM = "album"
    const val NOW_PLAYING = "nowplaying"
    const val QUEUE = "queue"
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"
    const val LOG = "log"
}

/** The whole app: screens stacked artists → albums → album, with a mini player under them. */
@Composable
fun TunerFishApp() {
    val library: LibraryViewModel = viewModel()
    val player: PlayerViewModel = viewModel()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route

    // Signing out (or a token the server rejects) always lands on the sign-in screen.
    LaunchedEffect(library.isSignedIn) {
        if (!library.isSignedIn && route != null && route != Routes.SIGN_IN) {
            player.clear()
            nav.navigate(Routes.SIGN_IN) { popUpTo(0) }
        }
    }
    // Adding an album to an empty queue opens the queue (the first track waits there, paused).
    LaunchedEffect(player.showQueue) {
        if (player.showQueue) {
            player.queueShown()
            nav.navigate(Routes.QUEUE)
        }
    }
    val error = library.error
    LaunchedEffect(error, route) {
        if (error != null && route != Routes.SIGN_IN) {
            snackbar.showSnackbar(error)
            library.clearError()
        }
    }

    ReplaceQuestion()
    OfferCrashLog()
    AskForNotifications()
    if (library.isSignedIn) AskForDownloadFolder()

    player.dataQuestion?.let { q ->
        AlertDialog(
            onDismissRequest = { player.answerDataQuestion(false) },
            title = { Text("Mobile data") },
            text = { Text(q.text) },
            confirmButton = { TextButton(onClick = { player.answerDataQuestion(true) }) { Text(q.confirm) } },
            dismissButton = { TextButton(onClick = { player.answerDataQuestion(false) }) { Text("Cancel") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // Only while something is playing or paused: not with the queue empty or played to its end.
            if (player.hasMedia && !player.finished && route != Routes.NOW_PLAYING && route != Routes.SIGN_IN) {
                MiniPlayer(player, onOpen = { nav.navigate(Routes.NOW_PLAYING) })
            }
        },
    ) { padding ->
        // The bottom space (mini player + navigation bar) is taken here, so the screens' own
        // Scaffolds don't leave room for the navigation bar a second time.
        val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
        Column(Modifier.fillMaxSize().padding(bottom).consumeWindowInsets(bottom)) {
            AppNavHost(nav, library, player)
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, library: LibraryViewModel, player: PlayerViewModel) {
    val activity = LocalActivity.current
    NavHost(nav, startDestination = if (library.isSignedIn) Routes.ARTISTS else Routes.SIGN_IN) {
        composable(Routes.SIGN_IN) {
            SignInScreen(library, onSignedIn = { nav.navigate(Routes.ARTISTS) { popUpTo(0) } })
        }
        composable(Routes.ARTISTS) {
            ArtistsScreen(
                library, player,
                onOpenArtist = { library.openArtist(it); nav.navigate(Routes.ALBUMS) },
                onOpenAlbum = { library.openViewAlbum(it); nav.navigate(Routes.ALBUM) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenDownloads = { nav.navigate(Routes.DOWNLOADS) },
                onOpenQueue = { nav.navigate(Routes.QUEUE) },
                onExit = { player.exit(); activity?.finishAndRemoveTask() },
            )
        }
        composable(Routes.ALBUMS) {
            AlbumsScreen(
                library, player,
                onBack = { nav.popBackStack() },
                onOpenAlbum = { library.openAlbum(it); nav.navigate(Routes.ALBUM) },
                onOpenQueue = { nav.navigate(Routes.QUEUE) },
            )
        }
        composable(Routes.ALBUM) {
            AlbumScreen(library, player, onBack = { nav.popBackStack() }, onOpenQueue = { nav.navigate(Routes.QUEUE) })
        }
        composable(Routes.NOW_PLAYING) {
            NowPlayingScreen(player, onBack = { nav.popBackStack() }, onOpenQueue = { nav.navigate(Routes.QUEUE) })
        }
        composable(Routes.QUEUE) {
            QueueScreen(player, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(library, onBack = { nav.popBackStack() }, onOpenLog = { nav.navigate(Routes.LOG) })
        }
        composable(Routes.LOG) {
            LogScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(onBack = { nav.popBackStack() })
        }
    }
}

/**
 * Asks about the first album whose folder holds another app's copy (DownloadState.Conflict).
 * Dismissing it leaves the question on the album and Downloads screens; it isn't asked again this run
 * unless Download is tapped on the album.
 */
@Composable
private fun ReplaceQuestion() {
    val app = LocalContext.current.applicationContext as TunerFishApp
    val progress by app.downloads.progress.collectAsState()
    var dismissals by remember { mutableIntStateOf(0) } // redraws after a dismissal, to close or move on
    val id = progress.entries.firstOrNull {
        dismissals >= 0 && it.value.state == DownloadState.Conflict && app.downloads.shouldAsk(it.key)
    }?.key ?: return
    val a = remember(id) { app.db.downloadedAlbum(id) } ?: return
    val path = DownloadNames.safe(a.album.artist.ifBlank { a.artist.name }) + "/" + DownloadNames.safe(a.album.title)
    AlertDialog(
        onDismissRequest = { app.downloads.dismiss(id); dismissals++ },
        title = { Text("Already on the phone") },
        text = {
            Text(
                "Another app has put ${a.album.title} in the download folder ($path). Replace its music files " +
                    "and pictures with TunerFish's download? They're deleted; the server still has the album.",
            )
        },
        confirmButton = { TextButton(onClick = { app.downloads.replace(id) }) { Text("Replace") } },
        dismissButton = { TextButton(onClick = { app.downloads.skip(id) }) { Text("Don't download") } },
    )
}

/**
 * Android 13+ shows notifications only with permission. Media playback controls are exempt; the
 * download progress notification isn't. Asked once, the first time a download is waiting or running.
 */
@Composable
private fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val app = LocalContext.current.applicationContext as TunerFishApp
    if (app.settings.notificationsAsked) return
    val progress by app.downloads.progress.collectAsState()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val downloading = progress.values.any { it.state == DownloadState.Queued || it.state == DownloadState.Downloading }
    LaunchedEffect(downloading) {
        if (downloading && !app.settings.notificationsAsked) {
            app.settings.notificationsAsked = true
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * After the first sign-in: where should downloads go? Asked until answered (cancelling the folder
 * picker asks again next time); Settings can change it later.
 */
@Composable
private fun AskForDownloadFolder() {
    val context = LocalContext.current
    val app = context.applicationContext as TunerFishApp
    var chosen by remember { mutableStateOf(app.settings.downloadFolderChosen) }
    var later by remember { mutableStateOf(false) }
    val pick = rememberFolderPicker { chosen = true }
    if (chosen || later) return
    AlertDialog(
        onDismissRequest = { later = true },
        title = { Text("Where should downloads go?") },
        text = {
            Text(
                "Albums you play are downloaded, so they play offline too.\n\n" +
                    "App storage: only TunerFish can see them, and Android deletes them if the app is uninstalled.\n\n" +
                    "A folder such as Music: other music apps can play them too, and they stay if the app is " +
                    "uninstalled.\n\nYou can change this in Settings.",
            )
        },
        confirmButton = { TextButton(onClick = { later = true; pick.launch(null) }) { Text("Choose folder…") } },
        dismissButton = { TextButton(onClick = { useAppStorage(context); chosen = true }) { Text("App storage") } },
    )
}

/** After a crash, once: offer to share the log, which has what happened. Either answer stops it asking. */
@Composable
private fun OfferCrashLog() {
    val context = LocalContext.current
    val app = context.applicationContext as TunerFishApp
    var show by remember { mutableStateOf(app.settings.crashToShare) }
    if (!show) return
    fun done() { app.settings.crashToShare = false; show = false }
    AlertDialog(
        onDismissRequest = ::done,
        title = { Text("${context.getString(R.string.app_name)} closed unexpectedly") },
        text = { Text("The app log has what happened. Share it, so it can be fixed? It's also in Settings → App log.") },
        confirmButton = { TextButton(onClick = { done(); shareLog(context) }) { Text("Share log") } },
        dismissButton = { TextButton(onClick = ::done) { Text("Not now") } },
    )
}
