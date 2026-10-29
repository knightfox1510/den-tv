# Watcher pictures

`app/src/main/assets/den/watchers/` holds the watchers' parts as pictures, rendered from the mockup so the TV
draws exactly what `mockups/den-looks.html` shows. To redo them after changing a watcher in the mockup (Node
and Playwright with Chromium):

```bash
cd launcher/tools/watchers && node dump.js && node render.js
```

Then convert `out/*.png` to WebP in `app/src/main/assets/den/watchers/` and copy the anchors from
`out/sprites.json` (each entry becomes `"name": [ax, ay]`).

The bat is drawn without a number: the launcher prints the look hero's own on it (DenWatcherView.numberFor).

# Wallpapers

`tools/wallpapers/render.js` draws a look's wallpaper (its scene and hero) from the mockup into
`themes/den-mix/wallpapers/`, e.g. `node render.js 81-master-blaster=sharjah:sachin 85-hero-sachin=black:sachin`.
Raise `DenTheme.VERSION` with new wallpapers so TVs set up earlier copy them.
