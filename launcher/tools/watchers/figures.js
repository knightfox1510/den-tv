// Renders the mockup's code-drawn figures (Luffy, Gear 5, Zoro) to PNGs: eyes open and lids shut, for blinking.
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const mock = path.resolve(__dirname, '../../../../mockups/den-looks.html');
const html = fs.readFileSync(fs.existsSync(mock) ? mock : '/home/user/dev-tv/mockups/den-looks.html', 'utf8');
const css = [...html.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)].map((m) => m[1]).join('\n');
const OUT = process.argv[2] || 'figs';   // then convert to WebP in app/src/main/assets/den/figures/ fs.mkdirSync(OUT, { recursive: true });
(async () => {
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  await p.goto('file://' + (fs.existsSync(mock) ? mock : '/home/user/dev-tv/mockups/den-looks.html'));
  for (const f of ['luffy', 'g5', 'zoro']) {
    await p.evaluate((f) => { localStorage.setItem('den2.setup', JSON.stringify({ done: true, phones: [] })); localStorage.setItem('den2.look', '"camp-nou"'); localStorage.setItem('den2.over', JSON.stringify({ figure: f })); }, f);
    await p.reload(); await p.waitForTimeout(400);
    const svg = await p.evaluate(() => document.getElementById('figure').innerHTML);
    for (const [name, lids] of [['', 'scaleY(0)'], ['-closed', 'scaleY(1)']]) {
      const q = await b.newPage({ viewport: { width: 600, height: 900 } });
      await q.setContent('<!doctype html><html><head><style>' + css + '</style><style>html,body{margin:0;background:transparent!important} *{animation:none!important;transition:none!important} .lids{transform:' + lids + '!important} #s{position:relative;width:456px;height:760px;margin:60px 72px}</style></head><body><div id="s"><div class="figure" style="position:static;width:456px;height:760px">' + svg + '</div></div></body></html>');
      await q.waitForTimeout(150);
      await (await q.$('#s')).screenshot({ path: OUT + '/figure-' + f + name + '.png', omitBackground: true });
      await q.close();
    }
  }
  await b.close();
})();
