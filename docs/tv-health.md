# TV health

## Closing background apps

What an ordinary launcher may do on Android TV, and what it may not:

| Action | Allowed? | How |
|---|---|---|
| Close apps waiting in the background (not the one on screen) | Yes | `ActivityManager.killBackgroundProcesses`, with the normal `KILL_BACKGROUND_PROCESSES` permission |
| Show memory used and free | Yes | `ActivityManager.getMemoryInfo` |
| Force-stop an app, or stop its background services for good | No | Needs a system permission. Disabling the app during setup (cleanyourtv's debloat) is the lasting fix |
| Clear other apps' cache | No | The page offers a shortcut to the app's info screen in Settings instead |
| Restart the TV on a schedule | No | Needs root or network debugging, which stays off after setup |

The phone page has a **Close background apps** button, a switch to do it each time you come back
Home, and an "always keep running" list (music, Bluetooth audio, the keyboard, Chromecast, Fan Hub,
the sleep timer).

Closing everything all the time isn't free: the next time you open a closed app it starts cold, which
is slower than resuming it. On a 1 GB TV the gain is real but small, so the automatic switch is off by
default.

## Other things in the same spirit

- **Memory and storage meter** on the phone page and, optionally, a line under the clock
  (cleanyourtv's `show_stats`).
- **Storage check:** which apps use the most space, each with a button to its Settings page.
- **Screen time** per app and per profile. It needs usage access, granted once during setup.
- **Kids timer:** an app closes itself after a set time, with a PIN to extend.
- **Sleep timer, quiet hours and bedtime mode**, from cleanyourtv's add-ons.
- **Burn-in care:** the idle clock screensaver moves a little every minute.
- **Network check:** a speed test tile and the Wi-Fi signal on the phone page.
