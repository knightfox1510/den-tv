# Watcher pictures

`app/src/main/assets/den/watchers/` holds the watchers' parts as pictures, rendered from the mockup so the TV
draws exactly what `mockups/den-looks.html` shows. To redo them after changing a watcher in the mockup (Node
and Playwright with Chromium):

```bash
cd launcher/tools/watchers && node dump.js && node render.js
```

Then convert `out/*.png` to WebP in `app/src/main/assets/den/watchers/` and copy the anchors from
`out/sprites.json` (each entry becomes `"name": [ax, ay]`).
