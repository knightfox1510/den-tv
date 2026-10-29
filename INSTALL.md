# Install den-tv on your TV

About 10 minutes. You need:

- An **Android TV or Google TV** (Sony, TCL, Hisense, Xiaomi, Vu, OnePlus, Philips, Chromecast with Google TV,
  Nvidia Shield and most others). Samsung, LG and Fire TV don't run it.
- **One of these** on the same Wi-Fi as the TV: an Android phone, or a Windows PC or Mac.

You get two apps: **den-tv** (the home screen) and **Fan Hub** (scores, episodes and the phone app). You can take it
all off again at any time ([Undo](#undo)).

## Step 1: get the TV ready (all ways need this)

1. **Turn on Developer options.** On the TV open **Settings → System → About**, scroll to **Android TV OS build**
   (on older TVs: **Settings → Device Preferences → About → Build**) and press OK on it **7 times**, until it says
   you are a developer.
2. **Turn on USB debugging.** Go back to **Settings → System → Developer options** (older TVs: **Device
   Preferences → Developer options**) and turn on **USB debugging**. If you also see **Network debugging** or
   **ADB over network**, turn that on too.
3. **Write down the TV's IP address.** It is under **Settings → Network & Internet →** your Wi-Fi network (or
   **Settings → System → About → Status**). It looks like `192.168.1.50`.

Keep the TV on and on this screen's Wi-Fi. The first time you connect, the TV asks **"Allow USB debugging?"**: tick
**Always allow from this computer** and choose **Allow**.

## Step 2: install. Pick one way

| | What you need | How hard |
|---|---|---|
| [A. Windows PC or Mac](#a-windows-pc-or-mac) | A computer | Easiest: paste one line |
| [B. Android phone](#b-android-phone-no-computer) | An Android phone | Easy: a free app and a few taps |
| [C. Claude Code](#c-claude-code-desktop) | Claude Code desktop on a computer | Easiest if you have it: it does it for you and fixes problems |

### A. Windows PC or Mac

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

### B. Android phone (no computer)

1. On the phone, open this repo's [Releases](https://github.com/knightfox1510/den-tv/releases) page and download
   **den-tv-launcher.apk** and **den-tv-fanhub.apk**. Don't tap them to open: they are for the TV, not the phone.
2. Install **Bugjaeger Mobile ADB** from the Play Store.
3. In Bugjaeger, connect to the TV: enter its IP address with port **5555**, connect, and choose **Allow** on the
   TV.
4. With Bugjaeger's install-APK button, install **den-tv-launcher.apk**, then **den-tv-fanhub.apk**. The launcher
   must go first.
5. Open Bugjaeger's shell (where you type commands), run this line and check that den-tv appears on the TV:

   ```bash
   am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n tv.den.launcher/com.gathin.tvhome.MainActivity
   ```

6. Only if den-tv showed up, run this line. It turns off Google's home screen so the Home button opens den-tv:

   ```bash
   pm disable-user --user 0 com.google.android.apps.tv.launcherx; pm disable-user --user 0 com.google.android.tungsten.setupwraith; cmd package set-home-activity --user 0 tv.den.launcher/com.gathin.tvhome.MainActivity; pm grant tv.den.launcher android.permission.ACCESS_FINE_LOCATION
   ```

   On an older Android TV (not Google TV) the first package is `com.google.android.tvlauncher` instead.
7. Optional: den-tv's screensaver (the look with a clock and what's next) instead of the TV's own:

   ```bash
   settings put secure screensaver_components tv.den.launcher/com.gathin.tvhome.DenDream
   ```

8. Press Home on the remote. Then turn **USB debugging** off on the TV (Developer options).

Turn on the den-tv helper from the TV itself afterwards ([After installing](#after-installing)).

### C. Claude Code desktop

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

## Updating

Run the same installer again (way A or C), or install both new APKs from Releases with Bugjaeger (way B; no need
to repeat steps 5 to 7). Each time, turn USB debugging on first and off after.

## Undo

Turn USB debugging on, then run the installer with undo, using your TV's IP address. It puts the TV's own home
screen back and removes both apps.

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
- **"Signed with a different key".** An older test build of den-tv is on the TV. Run the [Undo](#undo), then
  install again.
- **Home goes back to Google's screen,** or the installer says Home went elsewhere. Some makers lock the home screen.
  The installer puts the TV's own home screen back in that case, so nothing is broken. Open an issue with your TV
  model, or try way C, which can look into it.
- **Don't switch off other system apps** to make room or speed things up unless you know they are safe for your TV:
  on some Vu, TCL and Xiaomi sets that can send the TV into recovery. [cleanyourtv](https://github.com/Gathin23/cleanyourtv)
  does that part safely, with tested lists per TV.

## What it changes on the TV

Two apps are installed (`tv.den.launcher`, `tv.den.fanhub`). Google's home screen app and Google TV's setup wizard
are switched off (not removed; Undo switches them back on). Optionally the den-tv helper (an accessibility service)
and the screensaver are switched on. Nothing else is touched, and nothing is sent anywhere except the score,
episode and weather lookups the Fan Hub makes, and Gemini if you add a key.
