# den-tv

A home screen for Android TV that looks like *your* stuff: Camp Nou on match night, Super Saiyan
eyes that follow whatever app you select, Zoro's swords pointing the way, a Kamehameha when you need
one. Pick a look from your phone, mix your own, and let the TV switch looks by itself on match days
and new-episode nights.

![Camp Nou Nights](docs/camp-nou.jpg)

| ![Super Saiyan](docs/super-saiyan.jpg) | ![Three-Sword Style](docs/three-sword.jpg) |
|---|---|
| Super Saiyan: the Dragon Balls gather over the selected app | Three-Sword Style: the swords point at it |

Built on [cleanyourtv](https://github.com/Gathin23/cleanyourtv), which cleans an Android TV up safely
and replaces its ad-filled home screen. den-tv adds the looks on top.

## What works today

| Part | Status |
|---|---|
| [`themes/den-mix/`](themes/den-mix/) | **Works now.** Every scene as a wallpaper for the cleanyourtv launcher, a new one every 5 minutes |
| [`looks/`](looks/) | **Done.** 28 looks as small JSON files, the format the launcher will read |
| [`mockups/den-looks.html`](mockups/den-looks.html) | **Works now.** Open it in a browser: the full experience, with the phone settings page driving a TV preview |
| den-tv launcher (look engine, watchers, wake animations, moments) | Planned, see [docs/roadmap.md](docs/roadmap.md) |
| Phone settings page served by the TV | Planned |
| Fan Hub add-on (live scores, anime schedule, trail weather) | Planned |

## Set up a TV

Follow [PROMPT.md](PROMPT.md). It works on Android TV and Google TV sets (Xiaomi, TCL, Sony, Hisense,
OnePlus, Nvidia Shield and most others), not on Samsung, LG or Fire TV.

## The looks

| Category | Looks |
|---|---|
| Football | Camp Nou Nights, El Clásico Eve, Floodlights |
| Cricket | Wankhede Roar, Blue Billion |
| Heroes | Hero Wall, Super Saiyan, Ultra Instinct, Nine-Tails, Sage Mode, Straw Hat, Three-Sword Style, Navigator, Butterfly Mansion |
| Anime | Crimson Moon, Domain Expansion, Grand Line, Water Breathing, Manga Panel, Anime Dusk |
| Outdoors | Trail Dawn, Sahyadri Monsoon |
| Night & neon | Neon Tokyo |
| Seasonal | Diwali Night |
| Together | Our Wall, Seoul Nights, Bollywood Marquee |
| After dark | Midnight Lounge |

Each look picks a scene, a hero, a watcher, a focus effect, a wake animation, a clock, extras and a
big moment from one shared library. [looks/README.md](looks/README.md) lists every option.

## Your pictures

Everything in this repo is drawn in code, so it can be shared. Player photos and official artwork
belong to their photographers and studios: put them on your own TV, but don't commit them here.

## Licence

MIT. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for what comes from cleanyourtv.
