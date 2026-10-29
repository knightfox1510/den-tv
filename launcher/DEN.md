# den-tv launcher

The den-tv home screen for Android TV and Google TV: the [cleanyourtv](https://github.com/Gathin23/cleanyourtv)
launcher (forked unchanged in the first commit, MIT) with den-tv's looks on top. It installs as
`tv.den.launcher`, so it sits beside the cleanyourtv launcher or replaces it.

## What v0.1 does

- **Looks tile.** A Looks tile in the dock opens every look with its wallpaper. OK puts a look on Home and
  Back keeps what is on. The first tile, "Rotate every look", goes back to a new wallpaper every 5 minutes.
- **The den-mix theme is built in.** On the first start the APK copies its wallpapers and settings to where the
  launcher reads a theme, so there is nothing to push. A theme pushed later replaces it as usual.
- **Eyes that watch.** Looks with an eye watcher (Sharingan, Mangekyō, Six Eyes, Rinnegan, Super Saiyan, Ultra
  Instinct, Nine-Tails, Sage Mode) get a pair of eyes at the top of Home. They turn toward the focused tile and blink.
- **Wake animation.** When the TV switches on, Home fades up from a soft flash and settles from a slight zoom.
  It is off when Launcher settings has animations off or reduced motion on.
- **Everything else from cleanyourtv:** dock and grid, D-pad navigation, Inputs and Wi-Fi pills, the clock,
  Launcher settings, the remote-button helpers and the safety checks in the install script.

The launcher stays offline, as upstream intends. Live scores and episodes (see [`fanhub/`](../fanhub/README.md))
will come as a separate add-on that hands Home its data, the way cleanyourtv's Game Day add-on does.

## Not in v0.1 yet

The other watchers (Dragon Balls, swords, walkers and the rest), big moments, the clock styles, the app icon
styles and layouts, the phone settings page, and Hero Wall rotating its heroes (it shows one hero poster for
now). While looks rotate, no watcher shows, because each wallpaper would want a different one. The mockup
(`mockups/den-looks.html`) shows where each of these is going.

## Build

```bash
cd launcher && ./gradlew assembleRelease
```

The APK is `launcher/app/build/outputs/apk/release/app-release.apk` (about 8 MB, most of it wallpapers).
It needs JDK 17 or newer and the Android SDK (platform 34). Every push that changes the launcher, the looks or
the theme also builds it on GitHub: open the repo's **Actions** tab, then the latest **den-tv launcher** run,
and download **den-tv-launcher** at the bottom.

Unit tests: `./gradlew testReleaseUnitTest`. To render den-tv's screens to PNGs without a TV (the eyes, the
picker, Home with a look on), add `-Pden.screens=1 --tests '*DenScreensTest'`; they land in
`app/build/den-screens/`.

## Install on a Vu 4K Google TV

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
2. On the phone, download **den-tv-launcher** from the latest **den-tv launcher** run on the Actions tab, and
   extract the `.apk` from the zip. Don't tap the `.apk` itself: that tries to install it on the phone, and
   Play Protect blocks it because of the remote-button helpers.
3. In Bugjaeger, connect to `<tv ip>:5555`, allow the prompt on the TV, and install the `.apk` with
   Bugjaeger's install button.
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

`build-and-install.ps1` (Windows) is still cleanyourtv's and installs cleanyourtv's package name: use the
`.sh` script from WSL or Git Bash for now.

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
