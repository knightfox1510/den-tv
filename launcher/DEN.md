# den-tv launcher

The den-tv home screen for Android TV and Google TV: the [cleanyourtv](https://github.com/Gathin23/cleanyourtv)
launcher (forked unchanged in the first commit, MIT) with den-tv's looks on top. It installs as
`tv.den.launcher`, so it sits beside the cleanyourtv launcher or replaces it.

## What it does (v1.0)

- **Looks tile.** A Looks tile in the dock opens every look with its wallpaper. OK puts a look on Home and
  Back keeps what is on. The first tile, "Rotate every look", goes back to a new wallpaper every 5 minutes.
- **The den-mix theme is built in.** On the first start the APK copies its wallpapers and settings to where the
  launcher reads a theme, so there is nothing to push. A theme pushed later replaces it as usual.
- **Watchers.** Every watcher in `looks/README.md` follows the focused tile: the eight eye styles, the straw hat,
  Zoro's swords, the Dragon Balls, crows, butterflies, the walkers (Luffy, Gear 5, Zoro going the wrong way
  first) and the rest. Their pictures are rendered from the mockup (`tools/watchers`).
- **Clock styles.** Each look's clock (`thin`, `brush`, `board`, `flap`, `hud`, `term`, `analog`, `ring`, `neon`,
  `crt`, `roll`, `over`, `words`) replaces Home's clock; the date beside it stays.
- **App icon styles.** Tiles keep each app's own banner, full size, and the look's style is a frame over it:
  Wanted poster border, Dragon Ball stars, manga ink, neon edge, jersey stripes, enamel-pin gold, a scorecard
  name strip, pixel blocks, glass sheen, sticker edge, HUD brackets.
- **Wake animations.** When the TV switches on, the look's wake plays: bloom, a HUD or terminal boot, a CRT
  switch-on, a sword slash, a storm, a Sharingan spin, ink spreading, a tunnel and the rest (15). They are off when Launcher settings has animations off or reduced
  motion on.
- **Icon layouts.** Home's row stays one row (so the D-pad works as always), but each look can shape it:
  `formation` (two lines on a pitch), `field` (round the boundary), `arc` (islands along the Grand Line route),
  `shelf` (manga spines, the picked one slides out) and `carousel` (the picked tile big in the middle).
- **Focus effects and extras.** Moving between apps can throw a spotlight, pass a football, draw a ball-tracking
  arc, send ripples or pulse an aura. Extras drift over the wallpaper: flags, floodlight flashes, fireworks, crows,
  petals, embers, confetti, sparks, gold, silver, leaves, wisteria, rain and snow; each scene has its own.
- **Figures, Hero Wall and HUD.** Looks with a figure show a code-drawn character on the right that leans toward
  the picked app and blinks. Hero Wall moves to the next hero at most every 5 minutes. The Geek look adds
  SYSTEM, NETWORK and FEED panels on the right.
- **Big moments.** With live scores on (football-data.org and CricketData.org keys, entered from the phone's
  TV tab → Live data; they stay on the TV), a goal, or every fifty runs, plays the look's celebration: GOAL! with
  confetti, a six sailing out of the ground, a Kamehameha beam, shadow soldiers rising, a punch or Gear 5. A new
  episode slides its card in. Matches update every 2 minutes while live.
- **Badges, smart order and the Shows folder.** Tiles show a red LIVE pill (with the score) on FanCode, Hotstar or
  JioCinema while your match is on, and an orange EP pill on Crunchyroll for a new episode. The phone can sort
  the row by what you watch (now, or every day), and put apps in a **Shows** folder tile on Home.
- **Fan Hub add-on** (`fanhub/`, its own app, `tv.den.fanhub`). It fetches each team's next match from
  TheSportsDB (with the score while it is live) and each show's next episode from AniList, and Home shows them
  in the panel under the clock, a countdown before and LIVE during. The launcher stays offline; only the add-on
  goes online. Open **Fan Hub** on the TV for its dashboard, in the colours and wallpaper of the look on Home: the
  next match with a countdown (or the live score), your teams' later matches, the anime week, the weekend's trail
  weather, the next 72 hours, and the phone pairing code. OK fetches again; Menu forgets paired phones.
- **First-time setup.** The first time Home opens, the TV walks through welcome, Wi-Fi and pairing your phone
  (QR code and a 6-digit code that changes every 5 minutes); then the phone takes you through your name, teams and shows, what Home shows
  (greeting, match panel, watcher: each can be off) and a look picked from your interests, while the TV follows
  along. Run it again from the **Set up den-tv** tile at the end of the Looks screen. It needs the Fan Hub app.
- **The phone app.** Pair once (scan the code on the Fan Hub or setup screen); after that the page works whenever
  the TV is on, from any phone on the same Wi-Fi, and you can add it to your phone's home screen. Five tabs:
  - **Looks:** every look by category or search, and your own photos as the wallpaper (they stay on the TV and
    never join the rotation).
  - **Tweak:** the look's watcher, clock, app icons and colour; your name, the greeting, the match panel,
    animations, the wake animation, reduced motion and the idle clock; hide, show and rename apps; teams and shows.
  - **Auto:** looks that change by themselves for match day, India and IPL, a new episode or late night, and
    come back to yours after.
  - **Remote:** volume, mute, play/pause, Back, Home, sleep, a sleep timer, typing into a search box, HDMI inputs,
    watch now for a live match or new episode, and every app.
  - **TV:** Wi-Fi strength and a speed test, memory and closing background apps, storage, screen time, the kids
    timer, quiet hours and paired phones.

  - **People:** who's watching. Each person has a name for the greeting, a colour and their own look; a guest
    sees only looks for everyone, no match panel and no name, until the TV sleeps. Switch from the phone or from
    the "Watching" tile on the TV's Looks screen.
  - **Your characters** (Looks tab): a picture of yours as a big figure that leans toward the app you pick, or a
    small walker that walks to it; an eyes-closed picture makes it blink. Kept on the TV only.
  - **Widgets on Home** (Tweak tab): "Did you know?" facts along the bottom, the system line, and the weekend
    hike forecast (Open-Meteo, from Friday noon) in the match panel.
  - **Festivals** (Auto tab): Diwali Night for the five days of Diwali, Neon City on New Year's Eve.
  - **More rules** (Auto tab): IPL season (March to May, Wankhede for Mumbai Indians), weekend mornings (Saturday
    and Sunday, 5 to 10, a dawn trail look) and **Surprise me** (a new random look every morning; off at first).
  - **Make your own look** (Looks tab): pick any look's wallpaper, then your own watcher, clock, icons, layout,
    focus effect, wake and extras, and a colour. Up to 20 of your own looks, in the picker under "Mine".
  - **Private profile** (People tab): a PIN-locked person whose own photos show only while they are watching, and
    who never comes up when switching to the next person. The PIN is stored hashed; 5 wrong tries lock it for
    10 minutes. Leaving it (or the TV sleeping) goes back to the first person.
  - **Screen time per person** (TV tab): today's minutes for each person, next to the minutes for each app.

  Pairing only works while the Fan Hub or setup screen shows the code. Back, Home, sleep, typing, opening apps,
  screen time and the kids timer need the **den-tv helper**: on the TV, Settings → Accessibility → den-tv helper →
  On (or from Bugjaeger: `settings put secure enabled_accessibility_services tv.den.fanhub/tv.den.fanhub.Helper`
  then `settings put secure accessibility_enabled 1`; that replaces any other accessibility service that is on).
  The arrow keys inside other apps and waking the TV aren't possible for an app; the Google TV app does those.
- **Your changes to a look, kept or undone.** Changes made in Tweak → This look are saved with that look; it shows
  "Changed" on the phone and on the TV's Looks screen, and **Restore to default look** (in Tweak, or Restore default
  on the look in the list) puts its own parts back. **Save as my own look** keeps the changed version in My looks
  and restores the original.
- **Photo looks.** Looks → Save a photo as a look: one of your photos as the wallpaper with any look's parts, in My
  photos. It stays on the TV, never joins the rotation, isn't shown to a guest, and private photos can't be used.
- **Describe a look.** Looks → Describe a look: a sentence becomes a look of your own. With a Gemini API key
  (TV → Live data; it stays on the TV and the phone can't read it back) Gemini picks the wallpaper and parts from
  den-tv's own; without one, the words are matched to the looks' names. The description is sent to Google.
- **Now playing.** The Remote tab shows the app on the TV, for how long, who's watching and the volume, with Mute.
- **Form and standings.** The Fan Hub shows your team's last five results under the next match (TheSportsDB), and,
  with a football-data.org key, its place in the league table with the teams either side (every 6 hours).
- **Screensaver.** The look on Home drifting slowly behind its own clock style, the date, what's next from the Fan
  Hub (a countdown, or LIVE) and a "Did you know?" fact; the clock moves every minute so nothing burns in. Home's
  idle clock (Tweak → Idle clock on the phone, 10 minutes in den-mix) shows it while Home is open. To use it as the
  TV's screensaver everywhere, run once from Bugjaeger:
  `settings put secure screensaver_components tv.den.launcher/com.gathin.tvhome.DenDream`
  (Google TV's settings only list its own; choosing Ambient mode there again undoes it).
- **Looks by category.** The TV's Looks screen and the phone list the looks in one order, under Football, Cricket,
  One Piece, Dragon Ball, Naruto, Demon Slayer, Jujutsu Kaisen, Anime, Heroes, Outdoors, Seasonal, Together, After
  dark and Geek & code (`category` and `order` in `looks/*.json`).
- **Sachin and Yamal.** Master Blaster (Sachin at Sharjah, the Desert Storm; his straight drive races to the app and
  the crowd chants), Rocafonda 304 and Wonderkid (Lamine Yamal; a trivela curls in to the app) and Barça Wings
  (Yamal and Raphinha; 11 and 10 play a one-two into it). The bat watcher carries the hero's own number.
- **One long row.** Every app sits in one row that scrolls sideways, as in the mockup. The picked app scrolls to
  the middle, and the tiles are sized so the apps either side peek in at both edges. When there are more apps past
  an edge, that edge fades and a pill says how many ("‹ 4", "12 ›"); a slim track under the row shows where the
  focused app is. The focus glow follows the tile wherever the look's icon layout lifts, drops or scales it. Watchers that follow the focused app stay on screen at either end of the row. Hold OK on a tile and
  choose **Remove from Home** to hide an app; the App library button in the top bar lists every app and puts
  hidden ones back.
- **Opening and closing apps.** Opening an app from Home plays an animation from its tile (icon zoom, portal,
  warp speed, sword slash, manga panels, shatter, ink wipe, camera flash), and coming back Home plays one into the
  tile you left (shrink to icon, iris, slide down, shatter out, fade). Tweak → Animations chooses each: match the
  look, one by hand, random, or off.
- **Motion: Full, Calm or Off.** Calm keeps the watcher, the wake (as a plain bloom) and moments, and drops the
  drifting extras and focus effects. A TV with under 1.3 GB of memory starts on Calm.
- **Moving round the layouts.** The carousel wraps round from the last app to the first, and in the formation Up
  and Down move between the two lines.
- **Favourites and Surprise me.** Star looks on the phone; they open a Favourites row at the top of the TV's Looks
  screen and a Favourites chip on the phone. Surprise me puts on a random look (skipping private photos).
- **Sharing a look.** Share one of your own looks as a code, a QR code or a .json file, and import one from a
  friend. Photo looks share without their photo.
- **Picture icons and hiding apps per person.** Tweak → Apps: your own picture for any app's tile (kept on the TV),
  and "Hide for <name>" to hide an app only while that person is watching.
- **Continue and screen-time badges.** A teal CONTINUE pill on an app you left in the last 3 hours, and (if turned
  on) today's minutes on each tile.
- **Keep running.** TV → Memory: apps ticked here are never closed by "Close background apps".
- **Screen care.** Home's contents shift by a few pixels every 3 minutes so nothing stays still long enough to
  burn in (TV → Screen care).
- **Gemini model.** TV → Live data chooses the Gemini model; the key is checked when saved, and a model your key
  can't use falls back to one it can.
- **Everything else from cleanyourtv:** dock and grid, D-pad navigation, Inputs and Wi-Fi pills, Launcher
  settings, the remote-button helpers and the safety checks in the install script.

## Not possible

- **The rail layout** (a second row of tiles): Home's apps are one sideways row, and a second row would need the
  launcher's grid and D-pad handling rewritten. Looks with `rail` use the plain row.
- **Waking the TV from the phone**, the **arrow keys inside other apps** and **clearing other apps' caches**:
  Android doesn't let an app do these. The Google TV app on your phone does the first two.
- While looks rotate, no watcher shows, because each wallpaper would want a different one.

## Build

```bash
cd launcher && ./gradlew assembleRelease
```

The launcher is `launcher/app/build/outputs/apk/release/app-release.apk` (about 9 MB, most of it wallpapers) and
the Fan Hub is `launcher/fanhub/build/outputs/apk/release/fanhub-release.apk`.
It needs JDK 17 or newer and the Android SDK (platform 34). `build-and-install.sh` (or `build-and-install.ps1` on
Windows) builds both, installs them over ADB and makes den-tv the home screen; they use cleanyourtv's `tools/`, so
clone cleanyourtv next to den-tv first. Every push that changes the launcher, the looks or
the theme also builds it on GitHub: open the repo's **Actions** tab, then the latest **den-tv launcher** run,
and download **den-tv-launcher** and **den-tv-fanhub** at the bottom.

GitHub signs both with den-tv's own key when the repo has the `DEN_KEYSTORE_B64` and `DEN_KEYSTORE_PASSWORD`
secrets (Settings → Secrets and variables → Actions). Then every build installs over the last one, and the
phone page may change the look (the launcher lets only apps with the same key do that). Without the secrets each
build gets a throwaway key and the run shows a warning.

Unit tests: `./gradlew testReleaseUnitTest`. To render den-tv's screens to PNGs without a TV (the eyes, the
picker, Home with a look on), add `-Pden.screens=1 --tests '*DenScreensTest'`; they land in
`app/build/den-screens/`.

## Install on a Vu 4K Google TV

For most people, [INSTALL.md](../INSTALL.md) is the way: one line on a PC or Mac (`install/install.sh` or
`install/install.ps1`), an Android phone, or Claude Code, with the APKs from the repo's Releases. Tagging a commit
`v*` (or running the **den-tv release** workflow) publishes both APKs there, signed with den-tv's key. What follows
is the longer way, building it yourself with cleanyourtv's tools.

cleanyourtv has tested this TV: [`examples/vu-4k-lushan`](https://github.com/Gathin23/cleanyourtv/tree/main/examples/vu-4k-lushan)
(model "VU 4K", Google TV 12, MediaTek, about 1.8 GB RAM). From its notes:

- USB debugging alone opens port 5555; no wireless-debugging pairing is needed.
- APKs install over ADB, and Android 12 is well above the launcher's minimum (Android 8).
- Google's launcher (`com.google.android.apps.tv.launcherx`) is disabled by the install script only after the
  new launcher is running. The setup wizard (`com.google.android.tungsten.setupwraith`) is turned off as well, or
  it takes Home back.
- Never disable `com.vt.tv.smartsense`, `com.vt.tv.smartsense.ui` or `com.vt.tv.aiot`: on the same platform
  that sent the TV into Android Recovery.
- The Live TV app is `com.vt.tv.tvcenter`, and the remote's Input key keeps opening Vu's own input panel.

Steps, from a computer on the same Wi-Fi, with cleanyourtv cloned next to den-tv:

1. On the TV: Settings → System → About → press OK on "Build" 7 times, then Developer options → USB debugging on.
2. Follow cleanyourtv's PROMPT.md to connect, back up and debloat, using `examples/vu-4k-lushan/debloat.txt`.
3. Install den-tv instead of the cleanyourtv launcher:

```bash
TV_HOST=<tv ip> den-tv/launcher/build-and-install.sh
```

   `SKIP_BUILD=1` installs an APK you downloaded from GitHub instead: put it at
   `launcher/app/build/outputs/apk/release/app-release.apk` first.
4. Press Home, open the **Looks** tile and pick a look. Then switch network debugging off, as cleanyourtv's
   prompt says.

### Without a computer: from an Android phone

This is how den-tv first went onto a Vu 4K. You need an Android phone on the same Wi-Fi, with the free app
**Bugjaeger Mobile ADB**.

1. On the TV, turn USB debugging on (step 1 above) and note the TV's IP address.
2. On the phone, download **den-tv-launcher.apk** and **den-tv-fanhub.apk** from the repo's Releases page (or the
   latest **den-tv launcher** run on the Actions tab, and extract each `.apk` from its zip). Don't tap the `.apk` itself: that tries to install it on the phone, and
   Play Protect blocks it because of the remote-button helpers.
3. In Bugjaeger, connect to `<tv ip>:5555`, allow the prompt on the TV, and install the launcher `.apk`, then the
   Fan Hub `.apk`, with Bugjaeger's install button.
4. In Bugjaeger's shell, run these in order. Run the second and third lines only once the first has shown
   den-tv on screen: while Google's launcher is enabled it keeps Home, so `set-home-activity` alone does
   nothing.

```bash
am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n tv.den.launcher/com.gathin.tvhome.MainActivity
pm disable-user --user 0 com.google.android.apps.tv.launcherx; pm disable-user --user 0 com.google.android.tungsten.setupwraith
cmd package set-home-activity --user 0 tv.den.launcher/com.gathin.tvhome.MainActivity
pm grant tv.den.launcher android.permission.ACCESS_FINE_LOCATION
```

   The last line lets Home show the Wi-Fi name. Then turn USB debugging off.

This path skips cleanyourtv's debloat and the remote-button helpers.

### Updating from v0.1 (once)

v0.1 was signed with a throwaway key, so Android refuses v0.2 on top of it. In Bugjaeger's shell, give Home back
to Google's launcher first, so the TV is never left without one, then remove v0.1:

```bash
pm enable com.google.android.apps.tv.launcherx; cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity
pm uninstall tv.den.launcher
```

Then install both APKs and run the four lines above again. From v0.2 on, builds signed with den-tv's key install
straight over the last one, and Home stays den-tv.



## Undo

Put Google's launcher back, the same way cleanyourtv's notes describe:

```bash
TV_HOST=<tv ip> cleanyourtv/tools/tv.py "pm enable com.google.android.apps.tv.launcherx"
TV_HOST=<tv ip> cleanyourtv/tools/tv.py "cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity"
```

Then remove den-tv with `pm uninstall tv.den.launcher`, and re-enable anything the debloat turned off with
cleanyourtv's `tools/restore.sh` and the same list.

## How den-tv changes the fork

den-tv's own code is in `Den*.java`. The rest is cleanyourtv's, with these small hooks:

| File | Change |
|---|---|
| `MainActivity.java` | `DenTheme.ensure` before the config loads; `DenLayer.attach`, `onResume`, `onDestroy` |
| `AppAdapter.java` | lets the Looks screen through as a tile (the launcher's own entries stay out otherwise) |
| `AndroidManifest.xml` | the Looks screen, and the app name "den-tv" |
| `res/values/config.xml` | `tv.den.launcher` (the Looks tile) at the end of `dock_apps` |
| `app/build.gradle` | bundles `looks/` and `themes/den-mix/`, version name, Robolectric for the screen renders |
| `gradle.properties` | `launcherApplicationId=tv.den.launcher` |
| `build-and-install.sh` | cleanyourtv's `tools/` from the clone next door, and den-tv's package and components |

Keeping the hooks this small makes it easy to bring in cleanyourtv's later fixes.
