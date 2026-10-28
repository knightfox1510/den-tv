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
2. Read `cleanyourtv/PROMPT.md` and follow it completely: connect, back up, debloat, install the
   cleanyourtv launcher, verify with screenshots and close network debugging at the end. Its safety
   rules apply to everything below. Ask me before anything that depends on my preferences.
3. Before closing network debugging, apply the den-tv wallpapers:
   `TV_HOST=<tv ip> cleanyourtv/tools/theme.sh den-tv/themes/den-mix`.
   First ask me my name for the greeting and which looks I want to keep, then edit
   `den-tv/themes/den-mix/config.json` (`greeting_name`) and remove the wallpapers I don't want,
   with their lines in `captions.txt`. Take a screenshot from the TV and check it.
4. Then finish cleanyourtv's closing steps: switch network debugging off and confirm port 5555
   refuses connections.

---

The watchers, wake animations and big moments need the den-tv launcher, which isn't released yet.
Until then the TV gets every scene as a rotating wallpaper. To see the full experience, open
`mockups/den-looks.html` in a browser.
