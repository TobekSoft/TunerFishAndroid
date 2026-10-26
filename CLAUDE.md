# TunerFish for Android

Album-first music player for a Jellyfin server, for phones. Browse album artists (A–Z) → albums
(by title or year) → an album's tracks, and play. Jellyfin owns the library (scanning, tags, artwork,
streaming); this app never reads or writes the music files (see the read-only rule below).

The phone companion to the TunerFish desktop app (Avalonia, `../TunerFish`). The two are separate
codebases on purpose: the desktop has a bigger feature set and a different shape. Shared rules (how
artists and albums are filed) are kept identical by porting the tests, not by sharing code.

Developed on Linux Mint with Android Studio; tested on a Samsung Galaxy S9 (Android 10, API 29).

## Commands

```bash
./gradlew :core:test                 # core unit tests (plain JVM, no phone needed)
./gradlew :app:assembleDebug         # debug APK
./gradlew :app:installDebug          # install on the connected phone (as "TunerFish Debug")
adb shell am start -n com.tobeksoft.tunerfish.debug/com.tobeksoft.tunerfish.MainActivity
./gradlew :app:assembleRelease       # signed beta APK, if the key is set up (see Rules)
```

## Layout

```
core/    Plain Kotlin/JVM. All behaviour that can be tested without a phone. NO Android, ever.
  SortOrder.kt      Port of the desktop SortOrder: sort key = Jellyfin's SortName, plain-letter comparer, A–Z groups
  Library.kt        Artist/Album/Track, artist filing + filter, album sorting, AlbumSortMode, durations
  JellyfinClient.kt OkHttp + kotlinx.serialization; login, artists, albums, tracks, stream/image URLs
  Models.kt         Jellyfin API response shapes
  Preferences.kt    ThemeMode, readable names for picked folders
  Downloads.kt      DownloadState, DownloadNames (file/folder names), DataUse, DownloadLimit, Cleanup
  Tone.kt           Bass and treble: shelving filters (RBJ cookbook) on float samples, ±12 dB
  Plays.kt          PlayRule: when a play made offline counts (heard to half-way or 4 minutes)
  Marks.kt          Marks: the user's favourite / liked / hidden on an artist or album
  Views.kt          LibraryView (Library, Favourites, Liked, Recently/Most, Hidden), PlayHistory ranking
  Search.kt         The filter box: SearchScope (artists/albums/tracks), hits, narrowing the artist list
  AppLog.kt         The app log: LogArea, keeping secrets out (redact), the shared text
app/     Android only: Compose UI, Media3 playback, settings
  TunerFishApp.kt   Application: Settings + the one JellyfinClient (shared with the playback service)
  Settings.kt       SharedPreferences; the desktop's key names
  AppDatabase.kt    SQLite (tunerfish.db, plain SQLiteOpenHelper): play queue, downloads; one instance (TunerFishApp.db)
  downloads/        Downloads (requests + progress), DownloadWorker (WorkManager, one album at a time)
  LibraryViewModel  Sign-in and the drill-down (artists → albums → tracks), one per activity
  playback/         PlaybackService (MediaSessionService + ExoPlayer), PlayerViewModel (MediaController),
                    ToneProcessor (bass/treble in ExoPlayer's audio sink), PlayReporter (play history)
  ui/               Screens: SignIn, Artists (+ A–Z bar), Albums, Album, NowPlaying, Queue, MiniPlayer, Downloads, Settings, Log
```

## Rules

- **The library is read-only; your own user data isn't.** TunerFish never changes the music files,
  their tags (ID3 etc.), artwork or any library metadata, and keeps sort data out of it too. It may
  write the signed-in user's own Jellyfin user data: favourites, played/unplayed, play counts and
  playback reports. That data is per user and kept apart from the library. Agreed 2026-10-24.
- **Run `./gradlew :core:test` after any change to core.** Everything must pass. New behaviour that can
  be tested without a phone goes in core, with a test in the test class for the thing it exercises.
- **UI and playback can't be unit tested.** Build, install on the phone, and describe what you checked.
- **Filing follows Jellyfin's SortName and must match the desktop exactly.** `SortOrder.kt` is a port of
  `../TunerFish/src/TunerFish.Core/Services/SortOrder.cs`; `SortOrderTest` and `LibraryTest` mirror the
  desktop's filing tests case for case. Change both apps or neither. Display names are never altered.
- **Plays are reported to Jellyfin** (`PlayReporter`: /Sessions/Playing, Progress every 10 s and on
  pause/seek, Stopped), so the server keeps the user's play counts and last played; it decides what
  counts as played. When it can't be told, a play that counts by core's `PlayRule` goes in
  `PendingPlays` (schema 3) and is sent as `/UserPlayedItems/{id}?datePlayed=` before the next start
  report, oldest first. Reports go one at a time, in order, on a queue that outlives the service.
- **Marks, on artists and albums only, never tracks** (core `Marks`, `ui/MarksUi.kt`): ♡ Favourite =
  Jellyfin's IsFavorite (a perennial favourite); 👍 Like = Likes true (into it now);
  Hide = Likes false (junk out of the way, never a delete). Likes is one field, so liked and hidden
  exclude each other. Long-press an artist or album for the menu; the album screen has heart, thumbs-up
  and thumbs-down (hide) buttons. Every artist and album seen on the server has its marks cached in
  `UserMarks` (schema 4), so the phone source can show and filter them offline. A change is saved there
  as `Pending` and sent when the server answers (before each artist load); the pending change wins
  over the server, since Jellyfin keeps no time on marks. Hidden things are left out unless
  "Show hidden" (`ShowHidden`, ⋮ on the artists screen) is on.
- **Views** (core `LibraryView`, chosen from the artists screen's title; Back returns to Library; not
  saved, the app always opens on Library). Favourites, Liked and Hidden are filters on the artist list
  (`LibraryView.filter`): a marked artist is listed with all its albums (a favourite artist = all their
  albums), an artist with marked albums with only those, and a hidden artist hides all its albums. Its
  albums screen says "Favourites: 2 of 14 albums" with **Show all**. The filter box narrows a view too.
  Jellyfin's `filters=IsFavorite` and `Likes` work, but `Dislikes` matches everything not liked, so
  Hidden reads every album and picks the hidden ones out. Recents and Most list albums only (top 100),
  from the user's played tracks (`filters=IsPlayed`, up to 2000): an album's latest play, or its plays through as a whole
  (all its tracks' plays over its track count, `PlayHistory.most`). On the phone source the views use
  the downloaded albums; Recents goes by `LastPlayed`, and Most needs the server.
- **Search** (core `Search`; the box on the artists screen is hidden until 🔍 in the top bar is
  tapped, and stays while it has text; 🔍 again, ✕ or Back clears and closes it; from Recents or Most it goes to the
  Library): scope chips Artists · Albums ·
  Tracks, any combination (`FilterScope`, words, default `Artists Albums`). Album-first: results are the
  artist list, shortened the same way as the views. A name match keeps all the artist's albums; an
  album or track match keeps only those albums, with a line saying what matched, and "Matching: 1 of
  6 albums · Show all" on the albums screen. Artist names filter as you type (as `Library.filterArtists`);
  titles from 2 letters, 300 ms after typing stops: Jellyfin's `searchTerm` (one request per type,
  `limit=300`), or the phone's downloads (accents and case ignored). Inside a view it searches only
  that view; hidden albums stay out unless Show hidden.
- **Bass and treble** (`Bass`, `Treble`: whole dB, ±12, 0 = flat) run in the app's own player
  (`ToneProcessor` in the audio sink, filters in core `Tone.kt`), never Android's system equaliser,
  which differs per phone and fights Samsung's own effects. The tone button on Now Playing changes them live.
- **Album-first: no shuffle and no repeat**, anywhere (player, queue, notification). Albums play in order.
- **The play queue is the player's playlist.** The UI changes it only through `PlayerViewModel`
  (a MediaController); there is no second copy to keep in step. Items carry the Jellyfin item id as
  `mediaId` and a `tunerfish://audio/{id}` URI; `PlaybackService`'s ResolvingDataSource turns that into
  the stream URL when the track opens, from the address in use at that moment.
- **The queue survives restarts.** `PlaybackService` saves it to `QueueItems` whenever it changes, and the
  place in it (`QueueIndex`, `QueuePositionMs`) on track change, pause, seek and every 10 s while playing.
  The mini player shows while something is playing or paused, except on Now Playing (which is the
  player). Nothing playing or paused (queue empty, or played to its end) hides it; `QueueFinished`
  keeps a finished queue without it after a restart.
  It comes back paused at the next start, unless `ClearQueueOnExit` is on. Covers are saved as album id +
  image tag (MediaMetadata extras), never as URLs, which hold one server address.
- **Database schema = `PRAGMA user_version`** (SQLiteOpenHelper's version): bump it and add an `onUpgrade`
  step per change. Never rename or reuse a table or column.
- **One server, several addresses** (`ServerUrls`, most preferred first, e.g. home LAN then NetBird).
  Same token and user on all of them. `JellyfinClient.connect()` probes `/System/Info/Public` on every
  address at once and uses the first in order that answers with the saved `ServerId`; the token is never
  sent to an address that answers with a different id. A request that can't reach the server reconnects
  and retries once; playback does the same on a network error and resumes in place.
- **Settings are stored as words, never numbers** (choices; quantities like `QueueIndex` or `Bass` are plain numbers), under the desktop's key names (`AlbumSort`,
  `LastArtistId`, `Theme`, ...). `ServerUrls` is one address per line; the old single `ServerUrl` key is
  only read as a fallback (retired, never reuse). An unknown value resets only that setting. Never rename or reuse a key.
  The password is never stored, only the session token.
- **Download folder** (`DownloadFolder`): null = the app's own external storage (deleted on uninstall);
  otherwise a folder tree URI from Android's folder picker, with a persisted read/write grant. Release
  the old grant when it changes, unless downloads still live there (each album records its `Root`).
  `DownloadFolderChosen` records that the user answered "Where should downloads go?" (asked once after
  the first sign-in), since a null folder alone can't tell app storage from never asked.
- **Downloads are whole albums.** Queueing an album (Play / Add to queue) downloads it; "Download" does
  so without queueing and sets `Kept`. Files: `<root>/<Album Artist>/<Album>/01 Title.ext` + `cover.jpg`,
  named by `DownloadNames` (core, tested), written as `.part` then renamed. An existing `<Album>`
  folder from another app is `Conflict`: the app asks Replace (delete its music files and images, keep
  anything else, download into it) or Don't download (`Skipped`, streams); `ReplaceOtherCopies` replaces
  without asking. Another TunerFish album's folder of the same name means `<Album> (2)`. `DownloadedAlbums`/`DownloadedTracks`
  hold everything offline browsing needs; a track's `File` is null until it has landed. Work order: albums
  in the play queue first (queue order), then the rest by `Added`. Network errors retry later (WorkManager);
  an HTTP error or a storage error marks the album `Failed`.
- **Playing:** a track with a downloaded file plays from it (`Downloads.localFile`, which also notices a
  deleted file and downloads it again); otherwise it streams through `TunerFishApp.streamSource`, a
  small Media3 cache keyed by Jellyfin id that the download worker reads through too.
- **Two sources, same screens** (`LibrarySource`): Server, or Phone (only albums with a track on the
  phone, from the database). The toggle chooses by hand; the app switches to Phone itself when the
  server doesn't answer on a network change, or "Wi-Fi only" (`WifiOnly`) is on without Wi-Fi, and
  back when it can (an automatic switch only; `offlineReason` says which). Only real network changes
  (connected / lost) probe the server, never signal-strength updates. Wi-Fi = Wi-Fi or Ethernet
  transport; a hotspot counts. With Wi-Fi only and no Wi-Fi, nothing streams and downloads wait.
- **Mobile data:** queueing an album off Wi-Fi asks first if what isn't downloaded yet is over
  `DataUse.ASK_ABOVE_BYTES` (core, tested). Track sizes come from `fields=MediaSources`.
- **Clean-up:** past `DownloadLimit` (`DownloadLimit` setting, a word), finished albums that aren't
  `Kept` and have no track in the queue are removed, oldest `LastPlayed` first (`Cleanup.choose`,
  core, tested). `LastPlayed` is set when queued and when a track starts. Removing deletes the app's
  own files, cover and `.part` files, then the album and artist folders only if empty.
- **Versions live only in `gradle/libs.versions.toml`.** Modules reference libraries without versions.
- **The display name lives only in `res/values/strings.xml` (`app_name`)**: launcher, sign-in screen and
  Jellyfin's Client= all read it. Exceptions on purpose: package names and the applicationId.
- **compileSdk is 37 because current Compose requires it; targetSdk stays 36, minSdk 28 (Android 9).**
- **Android 9/10 matter**: the test phone is on Android 10, so no dynamic colour there (Theme.kt falls
  back to the default scheme) and no API-31+ calls without a version check. Core is plain JVM, so the
  compiler doesn't know the phone's older Java library: Animal Sniffer (`:core:animalsnifferMain`,
  Android 9's API signature) fails `:core:test` and every app build on a call minSdk doesn't have.
  Java additions since Java 8 are the usual culprits (`URLEncoder.encode(String, Charset)` is API 33).
- **App log** (core `AppLog`, `AppLog` table, schema 5, newest 1000 entries): `TunerFishApp.log(area, message)`
  for what helps with a bug report: connects and address changes, server/phone switches, errors,
  playback failures, downloads, plays and marks the server didn't take, and crashes (with the stack
  trace; the next start offers to share). Every message goes through `AppLog.clean`, which strips the
  token (`api_key=`, `Token=`) and passwords. Settings → App log shows it; Share hands the text to
  Android's share sheet. The app never sends it anywhere itself.
- **Never commit signing keys or tokens.**
- **Release signing:** one key for the app's whole life; an update signed with another key won't install
  over the old one. The keystore and its password live outside the repo; `app/build.gradle.kts` reads
  `TUNERFISH_KEYSTORE`, `TUNERFISH_KEYSTORE_PASSWORD` and `TUNERFISH_KEY_ALIAS` from
  `~/.gradle/gradle.properties`. Debug builds are `com.tobeksoft.tunerfish.debug`, named "TunerFish Debug"
  (`src/debug/res`), so they install beside the beta. No R8 shrinking yet.

## Jellyfin API notes

Same as the desktop (see its CLAUDE.md): `MediaBrowser` Authorization header with Token after login,
`/Artists/AlbumArtists`, `/Items?userId=...&includeItemTypes=MusicAlbum&albumArtistIds=...`, streams via
`/Audio/{id}/stream?static=true&api_key=...` (ExoPlayer decodes the original file), covers via
`/Items/{id}/Images/Primary?maxHeight=...&tag=...`. Compilations are tagged with the album name as
Album Artist, so they appear as their own "artist"; don't add special "Various Artists" handling.
