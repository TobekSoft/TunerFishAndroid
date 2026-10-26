# TunerFish for Android: acceptance tests

Tests that need a person: they play music, switch Wi-Fi or change settings. Everything that can be
checked without the phone is covered by `./gradlew :core:test`.

| | |
|---|---|
| **Build** | 0.9.11 beta.4 (commit ____________) |
| **Phone** | ____________, Android ____ |
| **Tester / date** | ____________ |

**New in beta.2:** tests 15–21 (bass and treble, play reporting, marks, views). **New in beta.3:**
tests 22–23 (search, the mini player). **New in beta.4:** test 24 (the app log). Tests 1–14 are
unchanged from beta 1; rerun any that were never passed.

**Before you start**

- Signed in, with the server reachable on home Wi-Fi.
- Only tests 7 and 8 need a phone with mobile data. A hotspot doesn't count: the app treats it as
  Wi-Fi. Every other test runs on a phone without mobile data: "turn Wi-Fi and mobile data off"
  then just means Wi-Fi off.
- No FLAC needed anywhere: MP3 albums work for every test.
- At least two albums downloaded (✓ on the cover), and some albums not downloaded.
- Downloads screen: menu ⋮ → Downloads. Settings: menu ⋮ → Settings.
- Wi-Fi on and off: the quick settings panel (swipe down from the top).
- Tests 16–20 check things in **Jellyfin's web page** as the same user (a computer's browser is
  easiest). Refresh the page to see changes.

---

## 1. Offline: skip to the next downloaded track

**Setup:** pick album **A**, not downloaded, and album **B**, downloaded.

1. On Wi-Fi, clear the queue, then open **A** and tap **Add to queue**, then **B** → **Add to queue**.

**Expected:** after adding **A**, the queue opens and nothing plays; the mini player shows **A**
track 1, paused. Adding **B** to the now non-empty queue stays on the album screen.
2. Downloads screen → 🗑 on **A** → Remove. **A** stays in the queue but is no longer on the phone.
3. Turn Wi-Fi **and** mobile data off, so the phone has no network.
4. Open the queue (Now Playing → queue) and tap the first track of **A**.

**Expected:** after about 4 seconds, **B** track 1 starts playing. No error is shown and the app
doesn't freeze.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 2. Exit while music is playing

1. Play any album.
2. On the artists screen: menu ⋮ → **Exit**.

**Expected:** the music stops at once. The notification and lock-screen controls disappear.
TunerFish isn't in the open-apps list.

3. Open TunerFish again.

**Expected:** the mini player shows the same track at the same place, paused.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 3. Closing from the open-apps screen keeps playing

1. Play any album.
2. Open the open-apps screen and swipe TunerFish away.

**Expected:** the music keeps playing, and the notification stays.

3. Pause from the notification, then swipe the notification away.

**Expected:** the notification goes and the music stays stopped.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 4. Clear queue on exit

1. Settings → Queue → turn **Clear queue on exit** on.
2. Queue an album, then menu ⋮ → **Exit**.
3. Open TunerFish again.

**Expected:** no mini player; the queue is empty.

4. Turn **Clear queue on exit** off again.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 5. An interrupted download carries on

**Setup:** an album that isn't downloaded, with plenty of tracks (12 or more), so there's time to
turn Wi-Fi off part-way.

1. On Wi-Fi, open the album and tap ⬇ (Download).
2. Once the album screen says "Downloading · 3 of …" or later, turn Wi-Fi **and** mobile data off.
3. Wait about 30 seconds and note the count: ______

**Expected:** the count stops, and the app doesn't crash.

4. Turn Wi-Fi back on.

**Expected:** within a minute or so the count goes up again from where it stopped (not from 0) and
reaches "Downloaded · kept". The album's folder has no files ending in `.part`.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 6. Downloads to the app's own storage

1. Settings → Download folder → **Use app storage**.
2. Download a small album (⬇ on the album screen).

**Expected:** "Downloaded · kept". With a file manager, the files are under
`Android/data/com.tobeksoft.tunerfish/files/Music/<Artist>/<Album>/`.

3. Turn Wi-Fi and mobile data off, then play the album from the phone source (the artists screen switches to it by itself).

**Expected:** it plays.

4. Turn the network back on. Settings → **Choose folder…** → pick `Music` again.
5. Play an album downloaded earlier to `Music` (e.g. Birdboys) offline.

**Expected:** it still plays. Downloads stay where they went.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 7. Wi-Fi only

**Setup:** an album with plenty of tracks that isn't downloaded.

1. Settings → Network → turn **Wi-Fi only** on.
2. On Wi-Fi, open the large album and tap ⬇. Once it says "Downloading · 2 of …", go back to the
   artists screen.
3. Turn Wi-Fi off (mobile data stays on).

**Expected:** within a few seconds, the artists screen shows "No Wi-Fi: albums on this phone" and
only downloaded artists. The download stops (Downloads screen: its count doesn't move), even
though mobile data is on.

4. Play a downloaded album.

**Expected:** it plays.

5. In the queue, tap a track of an album that isn't on the phone, if there is one (e.g. left over
   from test 1).

**Expected:** it doesn't stream; playback skips to the next downloaded track, or stops.

6. Turn Wi-Fi on.

**Expected:** the full server library comes back by itself, and the download carries on.

7. Turn **Wi-Fi only** off again.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 8. Asking before a big album on mobile data

**Setup:** Wi-Fi only **off**, Wi-Fi off, mobile data on, and an album over 100 MB that isn't
downloaded (a long MP3 album will do; e.g. one with 18+ tracks). The server must be reachable over mobile data (NetBird); if the artists screen says
"Can't reach the server", this test can't run.

1. Open the album and tap **Play album**.

**Expected:** a "Mobile data" dialog: "<Album> is ___ MB (MP3). Play it on mobile data?"

2. Tap **Cancel**.

**Expected:** nothing plays, and the queue doesn't change.

3. Tap **Play album** again, then **Play**.

**Expected:** it plays and starts downloading.

4. Try a short album (under 100 MB, e.g. 10 tracks) the same way.

**Expected:** no question; it just plays.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 9. Another app's copy: Don't download

**Setup:** with a file manager, make a folder named exactly after an album that isn't downloaded,
`Music/<Album Artist>/<Album>/`, and put any one music file in it. Settings: **Replace other apps'
copies without asking** is off.

1. Open that album and tap ⬇.

**Expected:** the "Already on the phone" dialog names the album and folder.

2. Tap **Don't download**.

**Expected:** the folder and its file are unchanged. The album screen says "Not downloaded: another
app's copy is there". Playing the album streams it, and the question doesn't come back.

3. Changing your mind: tap ⬇ on the album again.

**Expected:** the "Already on the phone" dialog comes back.

4. Tap **Replace**.

**Expected:** the music file is deleted, the album downloads into the same folder, and no
"<Album> (2)" folder appears.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 10. Another app's copy: replace without asking

**Setup:** as in test 9, with another album.

1. Settings → turn **Replace other apps' copies without asking** on.
2. Open the album and tap ⬇.

**Expected:** no dialog. The file is replaced and the album downloads into the existing folder.
Other files in it (e.g. a `.txt`) are still there.

3. Turn the switch off again.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 11. Clean-up when over the limit

Needs more than 1 GB of downloads (the smallest limit): about 10 MP3 albums.

1. Downloads screen: note the total "___ used". Pin (Keep) one album that isn't kept.
2. Settings → **Space for downloads** → **1 GB**.

**Expected:** within seconds, albums that aren't kept disappear from the Downloads screen, the
longest unplayed first, until the total is under the limit. Kept albums and albums in the queue
stay. Their folders are gone from `Music` (and an artist folder too, if it's now empty).

3. Set the limit back to 20 GB.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 12. Notification permission (Android 13 and later)

1. On a fresh install, queue or download an album.

**Expected:** Android asks once whether TunerFish may send notifications. Allow it: a "Downloading
<Album>" notification with a progress bar appears. It isn't asked again on later downloads.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 13. First run: where downloads go

1. On a fresh install, sign in.

**Expected:** "Where should downloads go?" appears, explaining App storage and a folder such as Music.

2. Tap **Choose folder…**, then cancel the folder picker.

**Expected:** the question goes away for now. Close the app (menu ⋮ → Exit) and reopen it: it's
asked again.

3. Tap **Choose folder…** and pick `Music`.

**Expected:** Settings → Download folder shows "Internal storage/Music". Reopening the app doesn't
ask again.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 14. Remove an album from the queue

**Setup:** three albums in the queue, **A**, **B** and **C**, with **A** playing. If you can, make
**B** and **C** two albums with the same title by different artists.

1. In the queue, long-press any track of **B**.

**Expected:** a bordered menu with a bin icon: "Remove album (n tracks)", where n is **B**'s track
count.

2. Tap it.

**Expected:** all of **B**'s tracks go, and only those: **C** is untouched, even with the same
title. **A** keeps playing. Nothing is removed from the Downloads screen.

3. Long-press a track of **A** (playing) → Remove album.

**Expected:** **C** track 1 plays.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 15. Bass and treble

1. Play an album you know well, with headphones if you can. Open Now Playing and tap the tone
   button (sliders icon).

**Expected:** a "Sound" panel with **Bass** and **Treble** sliders and a **Flat** button.

2. Slide **Bass** up to +12, then down to −12. Do the same with **Treble**.

**Expected:** the sound changes as you slide, within a moment, with no clicks, dropouts or
distortion beyond what boosted bass would do. The music doesn't restart or skip.

3. Set Bass +6, Treble −3. Menu ⋮ → **Exit**, open TunerFish again and play.

**Expected:** the panel still shows +6 and −3, and it sounds that way.

4. Tap **Flat**.

**Expected:** both go to 0 and the sound is back to normal.

5. If you have one, try a Bluetooth speaker or car with Bass at +6.

**Expected:** it works the same there, with no extra lag starting or pausing.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 16. Plays are reported to Jellyfin

**Setup:** server reachable. Pick an album **P** you haven't played in Jellyfin before (in the web
page, its tracks have no ✓ played mark).

1. Play **P** and let track 1 play to the end, into track 2.
2. In Jellyfin's web page, open **P**.

**Expected:** track 1 is marked played. While track 2 plays, the web page's dashboard (Dashboard →
Activity or the active devices) shows TunerFish playing it.

3. In TunerFish, skip to track 3 after only 10 seconds of track 2.

**Expected:** track 2 is **not** marked played.

4. Back on the artists screen, tap the title → **Recents**.

**Expected:** **P** is first.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 17. Plays made offline are sent later

**Setup:** a downloaded album **Q** whose tracks aren't marked played in the web page.

1. Turn Wi-Fi and mobile data off. Note the time: ______
2. Play **Q** from the phone source and let track 1 play to the end.
3. Exit TunerFish (menu ⋮ → Exit), turn Wi-Fi back on and open TunerFish. Play anything for a few
   seconds.

**Expected:** in the web page, **Q** track 1 is marked played, with the time from step 1 (not the
time you reconnected), if the page shows it. **Q** shows in TunerFish's **Recents**.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 18. Marks on an artist

**Setup:** an artist **R** with no marks.

1. On the artists screen, long-press **R**.

**Expected:** a menu with **Favourite**, **Like** and **Hide artist**.

2. Choose **Favourite**, then long-press again and choose **Like**.

**Expected:** **R**'s row shows ♡ and 👍, and the menu shows ✓ by both. In the web page, **R** is a
favourite.

3. Long-press → **Hide artist**.

**Expected:** **R** disappears from the list. Like goes off (Like and Hide exclude each other);
the favourite stays.

4. Artists screen ⋮ → **Show hidden**.

**Expected:** **R** is back, marked hidden (👎) and ♡.

5. Long-press **R** → **Unhide artist**, then turn **Show hidden** off, and take the favourite off.

**Expected:** **R** stays in the list with no marks. In the web page it's no longer a favourite.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 19. Marks on an album

**Setup:** an album **S** with no marks.

1. Open **S**.

**Expected:** heart, thumbs-up and thumbs-down buttons, all off.

2. Tap the heart, then thumbs-up.

**Expected:** both fill in. Going back to the artist's albums, **S**'s cover shows ♡ and 👍. In the
web page, **S** is a favourite.

3. Open **S** again and tap thumbs-down.

**Expected:** thumbs-down fills and thumbs-up empties. Back on the artist's albums, **S** is gone;
the sort menu's **Show hidden** brings it back.

4. Long-press **S**'s cover.

**Expected:** the menu has Play / Add to queue and the marks, with **Unhide album**. Unhide it and
take the heart off.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 20. Marks changed offline

**Setup:** a downloaded album **T** with no marks.

1. Turn Wi-Fi and mobile data off. On the phone source, open **T** and tap the heart.

**Expected:** it fills in at once, with no error.

2. Exit TunerFish, turn Wi-Fi on and open it again.

**Expected:** within a few seconds of the server list loading, **T** is a favourite in the web
page, and still ♡ in TunerFish.

3. Take the heart off **T** in TunerFish.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 21. Views

**Setup:** at least one favourite, one liked and one hidden artist or album (tests 18–19 can make
them; put them back afterwards), and some plays (tests 16–17).

1. On the artists screen, tap the title.

**Expected:** a list: Library, Favourites, Liked, Recents, Most, Hidden.

2. Choose each in turn.

**Expected:**
- **Favourites**, **Liked**, **Hidden**: the artist list (with the A–Z bar), shortened to artists
  that are marked or have a marked album. An empty view says how to mark something.
- **Recents**: albums only, the latest played first.
- **Most**: albums only, the ones played through most often first.
- None take more than a few seconds. Note any that are slow: ______

3. In **Favourites**, open an artist that's a favourite itself.

**Expected:** all its albums, with no "Show all" line.

4. Open an artist that's listed only for a favourite album (not a favourite itself).

**Expected:** only the favourite albums, under a line "Favourites: 1 of 9 albums" (or similar).
**Show all** shows the rest ("All 9 albums"); **Favourites only** goes back.

5. Type part of an artist's name in the filter box.

**Expected:** the view's artists narrow as in the library. Clear it.

6. Open an album, then press Back three times.

**Expected:** back to the artist's albums, then to the view, then to **Library**, at the place in
the list you left it.

7. Turn Wi-Fi and mobile data off and choose **Recents**, then **Most**.

**Expected:** Recents lists downloaded albums by when they were last played. Most
says "Most needs the server."

8. Turn the network back on. Choose Favourites, then Exit and reopen the app.

**Expected:** it opens on Library.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 22. Search

1. On the artists screen (Library), check there's no search box, then tap 🔍 in the top bar.

**Expected:** the search box opens with the keyboard up, and chips **Artists**, **Albums**, **Tracks**
under it; Artists and Albums are on. Press Back twice without typing: the keyboard goes, then the box.
Tap 🔍 again.

2. Type `dark side`.

**Expected:** within a second, artists with an album titled that way, e.g. Pink Floyd, with "The Dark
Side of the Moon" under the name.

3. Open that artist.

**Expected:** only the matching albums, under "Matching: 1 of n albums". **Show all** shows the rest.
Go back.

4. Turn **Tracks** on and search for a song title you know, e.g. `breathe`.

**Expected:** every artist with such a track, each showing "Track (Album)", with "+2" and so on when
there's more than one. The A–Z bar works on the results.

5. Turn **Artists** and **Albums** off, leaving Tracks, then try to turn Tracks off too.

**Expected:** Tracks stays on (one must be). Type an artist's name that isn't a song title: no
matches.

6. Turn Artists and Albums back on, Tracks off. Exit the app and reopen it, then tap the search box.

**Expected:** Artists and Albums are on, Tracks off (the choice is remembered).

Type something, open an artist from the results and come back: the search is still there. Press
Back (twice, if the keyboard is up): the search clears and the box closes. Open it again, type, and
tap 🔍: the same.

7. Choose **Favourites** and search for something in one favourite album and in a non-favourite one.

**Expected:** only the favourite artist or album is found.

8. Switch to the phone source (phone button) and search for an album that's downloaded, then one
   that isn't.

**Expected:** the downloaded one is found; the other isn't. Switch back.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 23. The mini player goes when the queue finishes

1. Clear the queue (Queue → Clear).

**Expected:** the mini player goes.

2. Queue a short album, open the queue and tap its last track. On Now Playing, drag the progress bar
   to a few seconds before the end, then go back to the artists screen.

**Expected:** when the track ends, the mini player goes. The queue button still opens the queue,
with the album in it.

3. Exit (menu ⋮ → Exit) and open TunerFish again.

**Expected:** still no mini player.

4. In the queue, tap any track.

**Expected:** it plays and the mini player is back.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## 24. The app log

1. Settings → **App log**.

**Expected:** entries newest first, starting with "Started 0.9.11 on …" for this start. No password,
and no `api_key=` value (it shows `api_key=…`).

2. Turn Wi-Fi and mobile data off, wait for "Can't reach the server", turn them back on, then open
   the log again.

**Expected:** "Can't reach the server: albums on this phone", then "Server answers again: back to its
library" (and "Using …" if it changed address).

3. Tap share (the arrow icon) and send it to yourself, e.g. by email.

**Expected:** a text with the app version, phone and Android version at the top, then the entries
oldest first.

4. **Clear**.

**Expected:** the log is empty.

Pass ☐   Fail ☐   Notes: ______________________________________________

---

## Afterwards

- Settings back to normal: Wi-Fi only off, Clear queue on exit off, Replace without asking off,
  Space for downloads 20 GB, Download folder `Music`, Show hidden off, bass and treble Flat (or
  how you like them).
- Take off any test marks (favourite, like, hide) you don't want to keep. Search chips back to
  Artists + Albums.
- Remove test downloads you don't want: Downloads screen → 🗑.
- Delete any folders made by hand for tests 9–10.
