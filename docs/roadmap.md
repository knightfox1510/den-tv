# Roadmap

## 1. Wallpapers on the cleanyourtv launcher (done)
`themes/den-mix/` works on any TV that runs the cleanyourtv launcher.

## 2. den-tv launcher
A fork of the cleanyourtv launcher (Java, Android 8+), with its safety and install scripts kept.
- **Look engine:** reads `looks/*.json` and draws the scene, hero, clock, icons and extras.
- **App icons:** each app's real icon and banner come from the installed app (PackageManager), and
  den-tv draws the style around them (13 styles), places them (7 layouts) and adds live badges. Each
  style is drawn once into a bitmap; only the selected icon animates. No brand logos in the repo.
  Smart order, folders, renamed apps, picture icons and per-person hidden apps live on the TV.
- **Remote navigation:** rows, spines and the rail move one step at a time, the carousel wraps
  round, and the formation, cricket field and arc move to the nearest app in the pressed direction.
  Watchers aim at the selected app's real position, whatever the layout.
- **Watchers:** eyes that follow the selected app and blink, Dragon Balls that gather over it, the
  Power Pole that extends to it, Nami's thundercloud, Shinobu's butterflies, Luffy's hat and Zoro's
  swords. Each is a small vector view, with no images or video.
- **Wake animations** when the screen turns on, and **focus effects** when you move between apps.
- **Moments:** goal, six, Kamehameha and new episode, played on demand or set off by Fan Hub.
- **Opening and closing apps:** eight opening animations (icon zoom, portal, warp speed, sword slash,
  manga panels, shatter, ink wipe, camera flash) and five for coming back Home (shrink to icon, iris,
  slide down, shatter out, fade). Each can match the look, be chosen by hand, be randomised or be off.
- **Widgets:** a "Did you know?" facts card or ticker that follows the look's heroes, and a next-match
  countdown chip. More widgets slot in the same way.
- **Motion levels:** Full, Calm and Off, with Calm the default on 1 GB TVs.

## 3. Phone settings page
Served by the TV on the home Wi-Fi, opened by scanning a QR code on the TV, with a pairing code.
- Five tabs, each grouped into sections you open one at a time:
  - **Looks:** gallery, search, favourites, Surprise me, describe a look.
  - **Customise:** Look, Animations and motion, Clock and greeting, App icons, Widgets on Home (Wi-Fi
    strength, system line, HUD panels, scores, episodes, facts, screen time), Fan Hub, Your photos.
  - **Auto:** rules.
  - **People:** renamable profiles, Private.
  - **TV:** Remote, Memory and background apps, Storage, Wi-Fi and network (with a speed test),
    Screen time, Kids timer, Sleep and quiet hours, Screen care.
- The rest, as in the mockup. Every profile has a name you
  can change, and profiles belong to each TV, so a friend's TV has its own people.
- A PIN-locked Private profile for your own pictures. They stay on the TV and phone and are never
  synced to this repo; guest mode and Surprise me skip them.
- Upload your own photos as heroes or wallpapers.
- Share a look with a friend as a file or a QR code.
- No network debugging needed after setup.
- TV health: memory meter, close background apps, keep-running list. See [tv-health.md](tv-health.md).

## 4. Fan Hub add-on
Live scores (Barça, India, Mumbai Indians, La Liga, EPL), the anime airing schedule (AniList),
weekend trail weather (Open-Meteo), and a "next 72 hours" list. It sets off moments and auto rules.
It opens from its dock tile in the current look's colours, and the phone page chooses which cards
show and which teams, leagues and shows they follow.

## 5. Better art
Detailed code-drawn characters now, then Rive characters whose eyes and heads follow the focused app,
and imported wallpapers and posters per TV. See [art.md](art.md).

## 6. Optional AI
Connect a Gemini, Claude or other key for describe-a-look, rules in plain words, match previews,
spoiler-safe recaps and a weekend planner. AI never runs while you move around Home. See
[ai.md](ai.md).

## 7. Extras
Room lights that react to goals, festival looks by date, more scenes, heroes and watchers.
