// Rasterises the mockup's canvas-drawn watcher sprites (window.denSprite: the Wizarding World owl, phoenix and
// Golden Snitch) to PNGs in out/, adding their anchors to out/sprites.json. Run after dump.js and render.js.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const OUT = 'out'; fs.mkdirSync(OUT, { recursive: true });
const manifestFile = path.join(OUT, 'sprites.json');
const manifest = fs.existsSync(manifestFile) ? JSON.parse(fs.readFileSync(manifestFile)) : {};
(async () => {
  const b = await chromium.launch(); const p = await b.newPage();
  await p.addInitScript(() => { try { localStorage.setItem('den2.setup', JSON.stringify({ done: true, phones: [] })); } catch (e) {} });
  await p.goto('file://' + path.resolve(__dirname, '../../../mockups/den-looks.html'));
  await p.waitForFunction(() => window.denSprite);
  for (const name of await p.evaluate(() => window.denSpriteNames)) {
    const s = await p.evaluate((n) => window.denSprite(n), name);
    fs.writeFileSync(path.join(OUT, name + '.png'), Buffer.from(s.url.split(',')[1], 'base64'));
    manifest[name] = { w: s.w, h: s.h, ax: s.ax, ay: s.ay };
  }
  fs.writeFileSync(manifestFile, JSON.stringify(manifest, null, 1));
  await b.close();
})();
