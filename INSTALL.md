# Install den-tv on your TV

About 10 minutes. You need:

- An **Android TV or Google TV** (Sony, TCL, Hisense, Xiaomi, Vu, OnePlus, Philips, Chromecast with Google TV,
  Nvidia Shield and most others). Samsung, LG and Fire TV don't run it.
- Nothing else: the TV can do it all by itself. (A computer or an Android phone on the same Wi-Fi works too.)

You get two apps: **den-tv** (the home screen) and **Fan Hub** (scores, episodes and the phone app). You can take it
all off again at any time ([Undo](#undo)).

## Step 1: get the TV ready (all ways need this)

1. **Turn on Developer options.** On the TV open **Settings → System → About**, scroll to **Android TV OS build**
   (on older TVs: **Settings → Device Preferences → About → Build**) and press OK on it **7 times**, until it says
   you are a developer.
2. **Turn on USB debugging.** Go back to **Settings → System → Developer options** (older TVs: **Device
   Preferences → Developer options**) and turn on **USB debugging**. If you also see **Network debugging** or
   **ADB over network**, turn that on too.
3. **Only for ways B to D: write down the TV's IP address.** It is under **Settings → Network & Internet →** your Wi-Fi network (or
   **Settings → System → About → Status**). It looks like `192.168.1.50`.

The first time something connects, the TV asks **"Allow USB debugging?"**: tick **Always allow from this computer**
and choose **Allow**. USB debugging goes off again at the end.

## Step 2: install. Pick one way

| | What you need | How hard |
|---|---|---|
| [A. Just the TV](#a-just-the-tv) | The remote | Easiest: two downloads on the TV, then press OK a few times |
| [B. Windows PC or Mac](#b-windows-pc-or-mac) | A computer | Easy: paste one line |
| [C. Android phone](#c-android-phone) | An Android phone | A free app and a few copy-pastes |
| [D. Claude Code](#d-claude-code-desktop) | Claude Code desktop on a computer | Easiest if you have it: it does it for you and fixes problems |

### A. Just the TV

1. On the TV, open the **Play Store**, search for **Downloader** (the orange app by AFTVnews) and install it.
2. Open Downloader and allow it to use storage if it asks. In its address box type the code **3476905** and press
   Go. It opens den-tv's releases page. (Without the code: `github.com/knightfox1510/den-tv/releases`, or
   `aftv.news/3476905` in any browser.)
3. Under the newest release, choose **den-tv-launcher.apk**. When it has downloaded, choose **Install**. The first
   time, Android says the TV isn't allowed to install apps from Downloader: choose **Settings**, switch Downloader
   on, press Back, and choose **Install** again. Then choose **Done** (not Open).
4. Back on the releases page, do the same for **den-tv-fanhub.apk**, and this time choose **Open**. (den-tv must be
   installed first.)
5. The Fan Hub offers to **Make den-tv your home screen**. Press OK and answer the questions: the helper, the
   screensaver, and, if [cleanyourtv](https://github.com/Gathin23/cleanyourtv) has a list tested on your TV model,
   whether to [speed the TV up](#speeding-the-tv-up). If USB debugging isn't on yet it shows you how (step 1 above).
6. The TV asks **"Allow USB debugging?"**: tick **Always allow from this computer** and choose **Allow**. It's the
   Fan Hub asking, on the TV itself. It switches Google's home screen off, makes den-tv Home, turns USB debugging
   off again and shows a tick for each step.
7. Choose **Go Home**. In Downloader you can delete the two downloads to save space.

This works on TVs where USB debugging listens on the network (most, including Vu, Sony, TCL, Hisense and Xiaomi). If
the Fan Hub says nothing answers on port 5555, use way B or C instead.

### B. Windows PC or Mac

The installer downloads Android's own `adb` tool from Google and the newest den-tv from this repo's
[Releases](https://github.com/knightfox1510/den-tv/releases), installs both apps, makes den-tv the home screen and
switches USB debugging off at the end. It asks before each choice.

**Windows:** open the Start menu, type **PowerShell**, open it, paste this line and press Enter:

```powershell
iwr https://raw.githubusercontent.com/knightfox1510/den-tv/main/install/install.ps1 -OutFile $env:TEMP\den-tv.ps1; powershell -ExecutionPolicy Bypass -File $env:TEMP\den-tv.ps1
```

**Mac:** open **Terminal** (Spotlight: type Terminal), paste this line and press Enter:

```bash
curl -fsSL https://raw.githubusercontent.com/knightfox1510/den-tv/main/install/install.sh | bash
```

(Linux works the same as Mac.) Type the TV's IP address when it asks, allow the prompt on the TV, and answer the
questions. If Windows asks whether to allow `adb` through the firewall, choose Allow.

Running it again later updates den-tv to the newest release; your looks and settings stay.

### C. Android phone

1. On the phone, open this repo's [Releases](https://github.com/knightfox1510/den-tv/releases) page and download
   **den-tv-launcher.apk** and **den-tv-fanhub.apk**. Don't tap them to open: they are for the TV, not the phone.
2. Install **Bugjaeger Mobile ADB** from the Play Store.
3. In Bugjaeger, connect to the TV: enter its IP address with port **5555**, connect, and choose **Allow** on the
   TV.
4. With Bugjaeger's install-APK button, install **den-tv-launcher.apk**, then **den-tv-fanhub.apk**. The launcher
   must go first.
5. Open Bugjaeger's shell (where you type commands). On a Google TV, first run this line:

   ```bash
   dumpsys wifi | grep -o 'cname=[^ ]*'
   ```

   If any line it prints says `setupwraith`, stop here: your Wi-Fi network was saved by Google's setup, and step 6
   would delete it. On the TV, go to **Settings → Network & Internet**, choose your network, **Forget** it, join it
   again with its password, reconnect Bugjaeger, and carry on.
6. Run this line and check that den-tv appears on the TV:

   ```bash
   am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n tv.den.launcher/com.gathin.tvhome.MainActivity
   ```

7. Only if den-tv showed up, run this line. It turns off Google's home screen so the Home button opens den-tv:

   ```bash
   pm disable-user --user 0 com.google.android.apps.tv.launcherx; pm disable-user --user 0 com.google.android.tungsten.setupwraith; cmd package set-home-activity --user 0 tv.den.launcher/com.gathin.tvhome.MainActivity; pm grant tv.den.launcher android.permission.ACCESS_FINE_LOCATION; appops set tv.den.fanhub REQUEST_INSTALL_PACKAGES allow; pm grant tv.den.fanhub android.permission.WRITE_SECURE_SETTINGS
   ```

   The last two parts let the Fan Hub keep den-tv up to date by itself.

   On an older Android TV (not Google TV) the first package is `com.google.android.tvlauncher` instead.
8. Optional: den-tv's screensaver (the look with a clock and what's next) instead of the TV's own:

   ```bash
   settings put secure screensaver_components tv.den.launcher/com.gathin.tvhome.DenDream
   ```

9. Press Home on the remote. Then turn **USB debugging** off on the TV (Developer options).

Turn on the den-tv helper from the TV itself afterwards ([After installing](#after-installing)).

### D. Claude Code desktop

If you have [Claude Code](https://claude.com/claude-code) on a Windows PC or Mac (a paid Claude plan), it can run
the installer for you and deal with anything unusual about your TV.

1. Do [step 1](#step-1-get-the-tv-ready-all-ways-need-this) on the TV.
2. Open Claude Code, choose any folder, and paste the text in [PROMPT.md](PROMPT.md) with your TV's model and IP
   address filled in.
3. Allow the commands it asks to run, and choose **Allow** on the TV when it asks.

## After installing

1. **Pair your phone.** den-tv opens a welcome screen with a QR code and a 6-digit code. Scan it with your phone's
   camera (same Wi-Fi), type the code, and the phone takes you through your name, teams, shows and first look. Add
   the page to your phone's home screen to keep it. Later, open **Fan Hub** on the TV to pair another phone.
2. **The den-tv helper** (if you didn't say yes in the installer): on the TV, **Settings → Accessibility → den-tv
   helper → On**. It lets the phone press Back and Home, type, open apps and count screen time. Volume and
   play/pause work without it.
3. **Optional: live scores and AI.** Free keys from football-data.org, CricketData.org and Google AI Studio (Gemini)
   go in on the phone, **TV tab → Live data**. They stay on the TV; never post them anywhere.
4. Make sure **USB debugging is off** (Settings → System → Developer options).

## Speeding the TV up

Switching Google's home screen off is the biggest saving on its own: it is one of the heaviest apps on a TV (often
200 MB or more of memory). Beyond that, TVs come with apps running in the background that you never see: ad and
tracking services, shop demo modes, partner auto-installers, makers' preloads.

[cleanyourtv](https://github.com/Gathin23/cleanyourtv) keeps lists of these for over 350 TV models, each one tested
on that model by its owner (a reboot, the HDMI inputs, the remote). The Fan Hub carries those lists. If your TV is on
one, setup shows it (way A, or hold OK on the Fan Hub and choose **Speed up this TV**) and switches off what you tick:

- Background apps start ticked; apps with a tile of their own (that you might watch) start unticked.
- Whatever a list says, it never touches core Android, the software behind the HDMI inputs, the app that opens
  Settings, your keyboard, or a home screen; if Home stops opening den-tv afterwards, everything comes back on.
- Apps are switched off, not removed. **Speed up this TV → Switch the apps den-tv switched off back on** (or Give Home
  back to Google) brings them all back; **Settings → Apps** brings back any one.

No list for your TV? Then nothing is switched off: guessing is how TVs lose their HDMI inputs or Settings. You can
add your TV to cleanyourtv with its own guide (it walks you through testing a list with Claude Code), and den-tv
picks it up in a later release.

## Updating

**It's automatic.** Twice a day the Fan Hub looks for a newer release here. When there is one it downloads it,
checks it's signed with den-tv's own key, and installs it while nobody is watching Home or the Fan Hub (the TV off,
another app on screen, or 3 to 5 in the morning). No USB debugging is needed. A release installs itself 3 days after
it comes out, so a bad one can be pulled before it reaches most TVs; **Check now** on the phone gets it sooner, and
**Get releases first** makes a TV install each one straight away.

- On **Android 12 and later** it just happens. The very first update may ask once: the Fan Hub's footer says
  **Update ready · OK installs it now**; press OK, allow it if Android asks, and after that it's silent.
- On **older TVs** Android asks every time: the Fan Hub's footer says when one is ready, and OK installs it.
- On the phone, **TV tab → Updates** shows the versions, checks now, installs now, or turns automatic updates off.

You can still update by hand: download the newer APKs in Downloader (way A, steps 2 to 4), run the installer again
(way B or D), or install them with Bugjaeger (way C, steps 1 to 4).

## Undo

**From the TV:** open the Fan Hub and hold OK (or, on the phone, **TV tab → Home screen → Open it on the TV**), then
choose **Give Home back to Google**. It needs USB debugging on for a minute, and turns it off again. To remove the
apps too: **Settings → Apps →** den-tv and Fan Hub → Uninstall.

**From a computer:** turn USB debugging on, then run the installer with undo, using your TV's IP address. It puts the
TV's own home screen back and removes both apps.

Windows (PowerShell):

```powershell
iwr https://raw.githubusercontent.com/knightfox1510/den-tv/main/install/install.ps1 -OutFile $env:TEMP\den-tv.ps1; powershell -ExecutionPolicy Bypass -File $env:TEMP\den-tv.ps1 192.168.1.50 -Undo
```

Mac or Linux (Terminal):

```bash
curl -fsSL https://raw.githubusercontent.com/knightfox1510/den-tv/main/install/install.sh | bash -s -- 192.168.1.50 --undo
```

From a phone with Bugjaeger, run this in its shell:

```bash
pm enable com.google.android.apps.tv.launcherx; pm enable com.google.android.tungsten.setupwraith; cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity; pm uninstall tv.den.fanhub; pm uninstall tv.den.launcher
```

## What changes when Google's home screen is off

den-tv replaces Google's home screen, so what lives only there goes with it: the Google TV recommendations and
"Continue watching" rows, the Live tab, Google TV profiles and their kids' settings, Ambient mode (den-tv's
screensaver can replace it), and some Assistant answers that open on the home screen. Your apps, voice search,
HDMI inputs and Settings all work as before.

On some Google TVs (the Chromecast with Google TV among them), switching Google's setup off can stop **casting**
to the TV. den-tv only switches it off where the TV needs that for den-tv to be the home screen. If you rely on
casting, check it after setup; Give Home back to Google brings it back.

## If Home stops working

- **Safe mode.** If den-tv crashes twice within ten minutes, it starts as a plain home screen (wallpapers, your
  apps, no looks or animations) and says so. Picking a look tries again; an update to den-tv ends it too.
- **Get Google's home screen back without a computer.** Open Settings: the remote's settings (gear) button, the
  Assistant ("open settings"), or the gear on den-tv's top bar. Then **Apps → See all apps → Show system apps →
  Google TV Home** (or **Android TV Home**) **→ Enable**, and press Home. Uninstalling den-tv in **Apps** works too.
- **Last resort:** with USB debugging on, the [Undo](#undo) from a computer or phone. A factory reset (**Settings →
  System → Reset**, or the maker's recovery menu) always brings the TV back as it came, without den-tv.

## Troubleshooting

- **"The TV didn't answer."** Check the IP address, that USB debugging is on, that the computer or phone is on the
  same Wi-Fi (not a guest network), and look at the TV for the Allow prompt. Restarting the TV helps if it was
  allowed before but stops answering.
- **The TV only has "Wireless debugging"** (newer TVs). Turn it on and open it: it shows an IP address with a port,
  like `192.168.1.50:41234`. Choose **Pair device with pairing code**, then on the computer run
  `~/.den-tv/platform-tools/adb pair 192.168.1.50:PAIRPORT` (Windows:
  `& $env:USERPROFILE\.den-tv\platform-tools\adb.exe pair 192.168.1.50:PAIRPORT`) with the pairing port and code the
  TV shows. Then run the installer and give it the address with its port, `192.168.1.50:41234`. (The installer
  downloads adb the first time it runs, so run it once first if that folder isn't there.)
- **"The Wi-Fi network this TV is on was saved by Google's setup"**. Android would delete that network if Google's
  setup were switched off, so den-tv stops before changing anything. In **Settings → Network & Internet**, choose
  your network, **Forget** it, join it again with its password, and run the setup again.
- **"Nothing answers on port 5555"** (way A). This TV's USB debugging doesn't take connections from apps on the TV
  itself. Use way B, C or D.
- **"Signed with a different key".** An older test build of den-tv is on the TV. Run the [Undo](#undo), then
  install again.
- **Home goes back to Google's screen,** or the installer says Home went elsewhere. Some makers lock the home screen.
  The installer puts the TV's own home screen back in that case, so nothing is broken. Open an issue with your TV
  model, or try way D, which can look into it.
- **Don't switch off other system apps** to make room or speed things up unless you know they are safe for your TV:
  on some Vu, TCL and Xiaomi sets that can send the TV into recovery. [cleanyourtv](https://github.com/Gathin23/cleanyourtv)
  does that part safely, with tested lists per TV.

## What it changes on the TV

Two apps are installed (`tv.den.launcher`, `tv.den.fanhub`). Google's home screen app and Google TV's setup wizard
are switched off (not removed; Undo switches them back on). Optionally the den-tv helper (an accessibility service)
and the screensaver are switched on. The Fan Hub is allowed to install den-tv's updates, and to switch its own
helper back on after an update (Android switches it off when an app is replaced). Nothing else is touched, and
nothing is sent anywhere except the score, episode and weather lookups the Fan Hub makes, the update checks on
GitHub, and Gemini if you add a key.
