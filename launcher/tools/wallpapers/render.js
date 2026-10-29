// Draws looks' wallpapers from the mockup into themes/den-mix/wallpapers, as the theme's 1920x1080 JPEGs.
// Usage: node render.js NAME=scene:hero ...   e.g. node render.js 81-master-blaster=sharjah:sachin 85-hero-sachin=black:sachin
// (Node and Playwright with Chromium; the mockup loads its fonts from Google Fonts, so it needs the network.)
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const mock = path.resolve(__dirname, '../../../mockups/den-looks.html');
const out = path.resolve(__dirname, '../../../themes/den-mix/wallpapers');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1280, height: 800 } });
  const errors = [];
  p.on('pageerror', (e) => errors.push(e.message));
  await p.addInitScript(() => { try { localStorage.setItem('den2.setup', JSON.stringify({ done: true, phones: [] })); } catch (e) {} });
  await p.goto('file://' + mock);
  await p.waitForFunction(() => window.denPaint);
  await p.evaluate(async () => { await document.fonts.load('900 100px Outfit'); await document.fonts.load('800 96px Outfit'); await document.fonts.load('500 26px Outfit'); await document.fonts.ready; });
  for (const arg of process.argv.slice(2)) {
    const [name, spec] = arg.split('=');
    const [scene, hero] = spec.split(':');
    const url = await p.evaluate(([s, h]) => window.denPaint(s, h), [scene, hero]);
    const file = path.join(out, name + '.jpg');
    fs.writeFileSync(file, Buffer.from(url.split(',')[1], 'base64'));
    console.log(file, fs.statSync(file).size);
  }
  if (errors.length) console.log('page errors:', errors);
  await b.close();
})();
