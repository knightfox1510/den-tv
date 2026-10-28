# Roadmap

## 1. Wallpapers on the cleanyourtv launcher (done)
`themes/den-mix/` works on any TV that runs the cleanyourtv launcher.

## 2. den-tv launcher
A fork of the cleanyourtv launcher (Java, Android 8+), with its safety and install scripts kept.
- **Look engine:** reads `looks/*.json` and draws the scene, hero, clock, icons and extras.
- **Watchers:** eyes that follow the selected app and blink, Dragon Balls that gather over it, the
  Power Pole that extends to it, Nami's thundercloud, Shinobu's butterflies, Luffy's hat and Zoro's
  swords. Each is a small vector view, with no images or video.
- **Wake animations** when the screen turns on, and **focus effects** when you move between apps.
- **Moments:** goal, six, Kamehameha and new episode, played on demand or set off by Fan Hub.
- **Motion levels:** Full, Calm and Off, with Calm the default on 1 GB TVs.

## 3. Phone settings page
Served by the TV on the home Wi-Fi, opened by scanning a QR code on the TV, with a pairing code.
- Gallery, Customise, Auto rules, People (owner, partner, guest) and Remote, as in the mockup.
- A PIN-locked Private profile for your own pictures. They stay on the TV and phone and are never
  synced to this repo; guest mode and Surprise me skip them.
- Upload your own photos as heroes or wallpapers.
- Share a look with a friend as a file or a QR code.
- No network debugging needed after setup.

## 4. Fan Hub add-on
Live scores (Barça, India, Mumbai Indians, La Liga, EPL), the anime airing schedule (AniList),
weekend trail weather (Open-Meteo), and a "next 72 hours" list. It sets off moments and auto rules.

## 5. Extras
Room lights that react to goals, festival looks by date, more scenes, heroes and watchers.
