# Set up den-tv on your TV

Paste the part between the lines into Claude Code (or another coding assistant with shell access)
on a computer on the same Wi-Fi as the TV.

Before you start, on the TV: Settings → Device Preferences → About → press OK on "Build" 7 times,
then go back → Developer options → turn on USB debugging (and Network/ADB debugging if your TV
shows it). Keep the TV on.

---

I want to clean up my Android TV and give it the den-tv look. My TV model is: <MODEL NAME HERE>.

1. Clone both repos side by side, not one inside the other:
   `git clone https://github.com/Gathin23/cleanyourtv` and
   `git clone https://github.com/knightfox1510/den-tv`.
2. Read `cleanyourtv/PROMPT.md` and follow it: connect, back up, debloat, verify with screenshots
   and close network debugging at the end. Its safety rules apply to everything below. If
   `cleanyourtv/examples/` has a folder for my TV, use its notes and debloat list. Ask me before
   anything that depends on my preferences.
3. For the launcher half, install the den-tv launcher instead of cleanyourtv's (read
   `den-tv/launcher/DEN.md` first):
   `TV_HOST=<tv ip> den-tv/launcher/build-and-install.sh`.
   It is cleanyourtv's launcher with den-tv's looks on top, under its own package
   (`tv.den.launcher`), and the den-mix wallpapers are built in, so no theme push is needed.
   Press Home, open the Looks tile, put a look on and take a screenshot from the TV to check it.
4. Then finish cleanyourtv's closing steps: switch network debugging off and confirm port 5555
   refuses connections.

---

The den-tv launcher is at v0.1: looks with their wallpapers, eye watchers that follow the focused
tile, and a wake animation. The other watchers, big moments, icon styles and live data come next;
open `mockups/den-looks.html` in a browser to see where it is going.

If you only want the wallpapers on cleanyourtv's own launcher, use
`TV_HOST=<tv ip> cleanyourtv/tools/theme.sh den-tv/themes/den-mix` instead of step 3.
