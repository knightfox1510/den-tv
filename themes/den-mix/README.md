# den-mix

Every den-tv scene as a still wallpaper for the [cleanyourtv](https://github.com/Gathin23/cleanyourtv)
launcher: Camp Nou, Wankhede, Blue Billion, Crimson Moon, Super Saiyan, Nine-Tails, Three-Sword Style
and the rest, plus a Hero Wall poster for each hero. A new one every 5 minutes, in random order, with
its name under the top-right buttons.

This is the part of den-tv that works on a TV today. The moving parts (watchers that follow the
selected app, wake animations, goals and sixes) need the den-tv launcher, which is still being built.

## Use it

With the cleanyourtv launcher installed and set as Home:

```bash
git clone https://github.com/Gathin23/cleanyourtv
TV_HOST=<tv ip> cleanyourtv/tools/theme.sh den-tv/themes/den-mix
```

`theme.sh` refuses a theme folder inside the cleanyourtv checkout, so keep den-tv next to it rather
than inside it.

## Make it yours

- Add your name to the greeting: `"greeting_name": "Gaurav"` in `config.json`.
- Keep only the pictures you like: delete the others from `wallpapers/` and their lines from
  `captions.txt`.
- Change how often it rotates with `wallpaper_rotation_minutes` (0 keeps one picture).
- On a TV with 2 GB of RAM or more, `"wallpaper_true_color": true` removes banding in dark skies.

The pictures are 1920×1080 JPEGs drawn in code by `mockups/den-looks.html`, so they can be shared
freely. Player photos or official art you add yourself stay on your own TV; don't commit them here.
