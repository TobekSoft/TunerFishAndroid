# TunerFish for Android

An album-first music player for Jellyfin, for phones. Browse the way you'd flip through a well-sorted
record collection: **album artist (A–Z) → albums (by title or year) → play the album.**

Jellyfin does the scanning, tag reading and storage. TunerFish never touches your files and never
scans folders. Albums you queue are downloaded to the phone, so they keep playing without a network.

The phone companion to the [TunerFish desktop app](https://github.com/TobekSoft/TunerFish). The two are
separate codebases on purpose, but they file artists and albums identically and share their settings'
names and your Jellyfin marks.

> **Status:** beta. Needs **Android 9 or later**. Tested on a Galaxy S9 (Android 10) and a Galaxy S24
> (Android 16). There's no Play Store release; install the APK by hand (see [Installing](#installing)).

## What it does

- Album artists A–Z, with an A–Z bar down the side
- **Search** artist names, album titles and track titles (🔍 at the top; the Artists · Albums · Tracks
  chips, any combination). The results are artists: one found by an album or track comes with just
  those albums and says what matched; its albums screen has **Show all**. In a view it searches that view.
- Artists and albums are filed by their **sort name in Jellyfin**, so "The Beatles" files under B.
  To file one differently, change its Sort name in Jellyfin's metadata editor. Display names never
  change. Accents are ignored for sorting (Björk files with Bjork), and names starting with a symbol
  or number come first, under `#`. Exactly the same order as the desktop app.
- Albums as covers, sorted by title or year
- **Play album** replaces the queue; **Add to queue** adds the album to the end. Long-press a cover for both.
- **Play queue:** tap a track to play it, ✕ removes one track, long-press removes the whole album
- **No shuffle or repeat, on purpose.** Albums play in order.
- **Downloads and offline:** queued albums download automatically; ⬇ downloads one without queueing.
  Choose app storage or a folder such as Music, set a space limit, 📌 keep albums you don't want removed.
  When the server can't be reached it switches to the albums on the phone, and back again.
- **Mobile data:** asks before a big album over mobile data; **Wi-Fi only** stops it using data at all
- **Your own marks** on artists and albums, kept in Jellyfin as your user data: ♡ favourite, 👍 like,
  hide (out of the way, never deleted). Long-press an artist or album, or use the album screen's buttons.
  Made offline, they're sent when the server is back.
- **Views** (tap the title on the artists screen): Favourites, Liked, Recents, Most (albums played
  through most often) and Hidden
- Plays are reported to Jellyfin, so your server keeps play counts; plays made offline are sent later
- **Bass and treble** on Now Playing: ±12 dB, TunerFish's own, so they sound the same on every phone
- **Several server addresses** (e.g. home network, then a VPN such as NetBird or Tailscale): it uses the
  first that answers, and switches when you move, even mid-album
- Playback carries on in the background, with the usual notification and lock-screen controls
- An **app log** for bug reports (Settings → App log, with Share): connections, errors and crashes,
  never your token or password
- Remembers the queue, paused where you left off, and your sort, theme, bass and treble

Compilations tagged with the album name as Album Artist show up as their own "artist",
filed alphabetically under their own name.

## Installing

1. Copy the APK to the phone and tap it.
2. Android will say it can't install apps from this source. Tap **Settings**, turn on **Allow from
   this source** for the app you tapped it in (Files, your browser, etc.), then go back and tap **Install**.
3. Open **TunerFish**. It asks for your Jellyfin server address (e.g. `192.168.1.20:8096`), user name
   and password. Only the session token is stored, never the password. Then it asks where downloads go.

**Updates:** install the new APK over the old one. Don't uninstall first: that deletes your sign-in,
queue and any albums downloaded to app storage.

`docs/beta-notes.md` is the longer guide given to beta testers, and `docs/acceptance-tests.md` the
checks run on a phone before each beta.

## Building

Requirements: Android Studio (its bundled JDK is enough) or the Android SDK command-line tools.
Gradle fetches everything else.

```bash
git clone https://github.com/TobekSoft/TunerFishAndroid.git
cd TunerFishAndroid
./gradlew :app:assembleDebug        # debug APK in app/build/outputs/apk/debug/
./gradlew :app:installDebug         # install on the connected phone
```

Or open the folder in Android Studio and run the **app** configuration.

Debug builds are `com.tobeksoft.tunerfish.debug`, named "TunerFish Debug", so they install beside a
release build without replacing it.

## Optional: release signing

Not needed for debug builds. A release build is signed only if a keystore is set up; without one it
comes out unsigned and won't install. The keystore and its password stay outside the repo, in
`~/.gradle/gradle.properties`:

```properties
TUNERFISH_KEYSTORE=/path/to/your.keystore
TUNERFISH_KEYSTORE_PASSWORD=...
TUNERFISH_KEY_ALIAS=...
```

```bash
./gradlew :app:assembleRelease
```

Keep the same key for the app's whole life: an update signed with a different key won't install over
the old one. Never commit it.

## Project layout

| Module | What's in it |
|---|---|
| `core/` | All behaviour that can be tested without a phone: Jellyfin client, sort order, search, views, marks, downloads rules, bass and treble filters, app log. Plain Kotlin/JVM, no Android. |
| `app/` | Android only: the Compose screens, Media3 playback service, downloads (WorkManager), settings and the SQLite database. |
| `docs/` | Beta notes for testers and the on-phone acceptance tests. |

Library versions live only in `gradle/libs.versions.toml`.

## Tests

```bash
./gradlew :core:test
```

Plain JVM tests, no phone or emulator needed. They also check core against Android 9's API (Animal
Sniffer), so a call too new for older phones fails the build rather than crashing on the phone.
The UI and playback are checked by hand on a phone (`docs/acceptance-tests.md`).

## Where things live on the phone

| What | Where |
|---|---|
| Settings | The app's private storage (SharedPreferences) |
| Queue, downloads list, app log | `tunerfish.db` in the app's private storage |
| Downloaded albums | App storage (deleted on uninstall) or the folder you chose (kept) |

Settings are stored as words, not numbers, under the same names as the desktop app's, and a value the
app doesn't recognise resets **only that one setting**.

## Working on it with Claude Code

`CLAUDE.md` describes the project, commands and house rules. Start Claude Code in the project folder.

## License

MIT, © 2026 Tobeksoft. See [LICENSE](LICENSE). The app icon and logo come from the desktop app's
artwork, which was made with AI image tools, and may be used on the same terms.

The Jellyfin icon (`app/src/main/res/drawable/ic_jellyfin.xml`) is from
[jellyfin-ux](https://github.com/jellyfin/jellyfin-ux), © Jellyfin contributors, CC BY-SA 4.0. It stands
only for the Jellyfin server, never as this app's logo. Libraries built into the APK (AndroidX, Media3,
OkHttp, Coil, kotlinx) have their own licences, mostly Apache 2.0.
