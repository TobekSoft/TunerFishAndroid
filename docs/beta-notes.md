# TunerFish for Android 0.9.11 beta.4

Thanks for trying TunerFish. It's a music player for a **Jellyfin** server, built around albums:
pick an artist, pick an album, and it plays the whole album in order. This is a private beta, so
some things haven't been tested on many phones yet. Please tell me what goes wrong, and what feels
odd.

## New in beta.4

- **App log:** Settings → **App log** keeps what the app did and any errors. When something goes
  wrong, share it with your report (see *Reporting a problem*). If the app closes by itself, it offers
  to share the log next time.

## New in beta.3

- **Search the whole collection:** 🔍 at the top searches artists, album titles and track titles.
- **The mini player** shows only while something is playing or paused; it goes when the queue is
  cleared or has played to the end.
- **Recents** and **Most** are the new, shorter names of the played views.

## New in beta.2

- **Bass and treble:** the tone button on Now Playing.
- **Plays are reported to Jellyfin**, so your server keeps play counts and when you last played
  something. Plays made offline are sent when the server is back.
- **Favourite, Like and Hide** artists and albums (see *Marks* below).
- **Views:** tap the title on the artists screen for Favourites, Liked, Recents, Most
  and Hidden.

## What you need

- An Android phone running **Android 9 or later**.
- A **Jellyfin server** with your music on it, and an account on it. Your music stays on the server;
  TunerFish never changes it.

**Tested on:** a Galaxy S9 (Android 10) and a Galaxy S24 (Android 16). Other phones and Android
versions should work but haven't been tried yet, so please say which phone and Android version
you're on when you report anything.

## Installing

1. Copy `TunerFish-0.9.11-beta.4.apk` to the phone and tap it.
2. Android will say it can't install apps from this source. Tap **Settings**, turn on **Allow from
   this source** for the app you tapped it in (Files, your browser, etc.), then go back and tap
   **Install**.
3. Open **TunerFish**.

**Updates:** install the new APK over the old one. Don't uninstall first: uninstalling deletes your
sign-in, queue and any albums downloaded to app storage.

*Checking the APK is genuine (optional):* its signing certificate's SHA-256 fingerprint is
`6de90ea8303c4c11d240fe96166feddf846d2c44dc2a03b8d521c0abfc68aeec`.

## First run

1. **Sign in:** your server's address (e.g. `http://192.168.1.10:8096`), user name and password.
   The password isn't stored, only the session Jellyfin gives back.
2. **Where should downloads go?** Either **App storage** (private to TunerFish, deleted if you
   uninstall) or a folder you choose, such as **Music** (other apps can see it, and it survives an
   uninstall).
3. On Android 13 and later, allow notifications when asked. That's how download progress and the
   playback controls show.

## Using it

- **Artists → albums → album.** Use the A–Z bar on the right, or search.
- **Search:** tap 🔍 at the top and type (🔍 again closes it). The chips under the box choose what it looks in: **Artists**,
  **Albums**, **Tracks**, any combination. Results are artists: one that matched by name comes with all
  their albums; one that matched by an album or a track comes with just those albums, and says what
  matched under the name ("Breathe (The Dark Side of the Moon)"). Their albums screen has **Show all**
  for the rest. In a view such as Favourites, it searches only that view.
- **Play album** replaces the queue and plays. **Add to queue** adds the album to the end; into an
  empty queue, it opens the queue with the first track ready (paused).
- **Long-press an album cover** for Play / Add to queue without opening it.
- **The queue:** the button at the top right of each browse screen, or Now Playing → queue. Tap a
  track to play it, ✕ removes one track, and a **long-press removes the whole album**.
- **The mini player** at the bottom opens Now Playing. Music keeps playing when you leave the app or
  swipe it away; **⋮ → Exit** stops it and closes the app.
- **No shuffle or repeat, on purpose.** Albums play in order.
- **Bass and treble:** the tone button on Now Playing changes them as you listen. They're
  TunerFish's own, so they sound the same on every phone and don't fight the phone's sound effects.

## Marks and views

- **Long-press an artist or album** for ♡ **Favourite**, 👍 **Like** and **Hide**. The album screen
  has the same three as buttons.
  - ♡ Favourite: the music you'll always love.
  - 👍 Like: what you're into right now. Like and Hide exclude each other.
  - Hide: gets junk (free or bundled albums, say) out of the way. Nothing is deleted, and you can
    unhide it.
- These are saved on your Jellyfin account, so other Jellyfin apps see your favourites too. Marks
  changed offline are sent when the server is back.
- Hidden things are left out of the lists. **⋮ → Show hidden** on the artists screen brings them back.
- **Views:** tap the title on the artists screen to choose one. Back returns to the whole library.
  - **Favourites**, **Liked** and **Hidden** shorten the artist list. A marked artist comes with all
    their albums (favouriting an artist = favouriting everything of theirs); an artist with only some
    albums marked shows just those, with **Show all** for the rest. The filter box works here too.
  - **Recents** and **Most** list albums, from what you've played in TunerFish or any
    other Jellyfin app.
  - Offline, the views use the albums on the phone; Most needs the server.

## Downloads and offline

- **Queued albums download to the phone** automatically, so they play without a network. **⬇** on
  an album downloads it without queueing, and keeps it.
- **⋮ → Downloads** lists everything on the phone: 📌 **Keep** stops it being removed to save
  space, 🗑 removes it now.
- **Settings → Space for downloads** sets a limit. Over it, the albums played longest ago (not kept,
  not queued) are removed.
- **Offline:** when the server can't be reached, the artists screen switches to the albums on the
  phone, and back again when the server returns. The button at the top shows which you're on (a
  phone: the server's library; the Jellyfin logo: the phone's albums, tap to go back) and switches
  by hand.
- **Mobile data:** before playing or queueing an album over 100 MB on mobile data, TunerFish asks first.
  **Settings → Wi-Fi only** stops it using mobile data at all (a phone hotspot counts as Wi-Fi).
- If the folder you picked already has the album from **another app**, TunerFish asks before
  replacing it (only music files and images are deleted; anything else in the folder stays).

## Several server addresses

If you reach your server one way at home and another way when out (e.g. a VPN such as NetBird or
Tailscale), add each address in **Settings → Account**, most preferred first. TunerFish uses the first
that answers, and switches when you move, even mid-album.

## Please try especially

These haven't been tested properly yet:

- **No network at all**, with a mix of downloaded and not-downloaded albums queued: it should skip to
  the next downloaded track.
- **Downloads interrupted** by losing Wi-Fi part-way: they should carry on from where they stopped.
- **Wi-Fi only** turned on, then leaving Wi-Fi with mobile data on.
- **The mobile data question** for a big album (needs the server reachable over mobile data).
- **The space limit** with more than 1 GB downloaded.
- **Another app's copy** of an album in your download folder (Replace / Don't download).
- **Removing an album from the queue** (long-press), including the one that's playing.
- **Plays while offline:** play an album with no network, then reconnect. It should show up in
  Recents (and in Jellyfin's own play counts).
- **Marks while offline:** favourite or hide something on the phone source, reconnect, and check it
  stuck (in Jellyfin's web page too, for favourites).
- **The views** with a big library: Hidden and Most read a lot, so say if they're slow.
- **Bass and treble** with headphones, a car or Bluetooth speaker: any clicks, distortion or lag?

## Known gaps

- No graphic equaliser, only bass and treble.
- The queue can't be reordered (by design: albums play as albums).
- Albums already in your Music folder from other apps aren't picked up; TunerFish only plays what it
  downloaded itself, or streams.

## Reporting a problem

Tell me: your phone and Android version, what you did, what you expected, and what happened. A
screenshot helps.

**Send the app log too:** Settings → **App log** → share (the arrow icon), by email or any messaging
app. It has what the app did and any errors, with times, so I can see what went wrong without your
phone. It never holds your password or sign-in token; it does hold your server's address. Read it
first if you like. If the app closed by itself, it offers to share the log the next time it starts.
