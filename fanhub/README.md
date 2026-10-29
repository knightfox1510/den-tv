# Fan Hub data

`fanhub.js` gathers everything the Fan Hub, the live badges and the Auto rules need into one feed:
fixtures, results, live scores, the league table, anime episodes and the weekend weather. It has no
dependencies and runs in Node 18+ and in a browser, so the mockup, the phone page and tests share one
copy. The launcher will port the same logic to Java.

## Try it

```bash
node fanhub/cli.js
```

That prints your teams' next and last matches, the La Liga table, episode dates, the weather at
Rajmachi and what the TV would switch to right now. Other options:

```bash
node fanhub/cli.js --watch 60
```

`--watch 60` checks every 60 seconds and prints a line for each goal or new episode, the same events
that play the big moments on the TV. `--json` prints the raw feed. `--sample` uses the built-in
sample feed, with no internet needed.

## Sources

| What | Source | Key |
|---|---|---|
| Fixtures, results, La Liga table | TheSportsDB | none (public test key `123`) |
| Live football scores during a match | football-data.org | free key |
| Live cricket scores during a match | CricketData.org | free key, 100 calls a day |
| Anime episode dates | AniList | none |
| Weekend weather | Open-Meteo | none |

Without the two optional keys, scores arrive when a match ends. With them, they update every minute
during a match. Sixes and goal scorers need ball-by-ball data from a paid feed, so a goal is found by
comparing scores between two checks, and a six can't be detected yet.

## Your settings and keys

Copy `config.example.json` to `config.json` (git ignores it) to change teams, shows, the hike
location or add keys:

```bash
cp fanhub/config.example.json fanhub/config.json
```

On the TV the same settings live in the phone page (Customise → Live data) and are stored on the TV
only. Keys never go into the repo.

## What the feed drives

- **Chips and badges on Home:** the live score, the next match countdown, "New: <show> Ep n", and the
  weekend hike.
- **Fan Hub cards:** cricket, football with the table, episodes, trails and your next 72 hours.
- **Auto rules** (`whatToShow`): Barça match day → Camp Nou Nights, India playing → Blue Billion,
  Mumbai Indians match → Wankhede Roar, a new episode → its look, a dry weekend morning → Trail Dawn,
  late night → calm motion.
- **Big moments** (`momentsBetween`): a Barça goal plays the goal moment, and a new episode plays the
  new episode moment, whatever look is on.

The TV checks every 15 minutes, and every minute while a followed team is playing.
