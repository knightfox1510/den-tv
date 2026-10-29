// Rasterises the mockup's watcher parts to PNGs for the launcher. Each sprite's anchor is its centre unless a pivot is given.
const { chromium } = require('playwright');
const fs = require('fs');
const T = require('./tracker.json');
const html = fs.readFileSync(require('path').resolve(__dirname, '../../../mockups/den-looks.html'), 'utf8');
const css = [...html.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)].map((m) => m[1]).join('\n');
const OUT = 'out'; fs.mkdirSync(OUT, { recursive: true });
const manifest = {};
(async () => {
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  const base = '<!doctype html><html><head><meta charset="utf-8"><style>' + css + '</style><style>html,body{margin:0;background:transparent!important;overflow:hidden} *{animation:none!important;transition:none!important} :root{--acc:#ffffff}</style><style id="extra"></style></head><body><div id="stage" style="position:relative"></div></body></html>';
  async function shot(name, w, h, build, arg, extraCss, anchor) {
    await p.setContent(base); await p.evaluate(({ w, h }) => { const s = document.getElementById('stage'); s.style.width = w + 'px'; s.style.height = h + 'px'; }, { w, h });
    if (extraCss) await p.evaluate((c) => { document.getElementById('extra').textContent = c; }, extraCss);
    await p.evaluate(build, arg);
    await p.waitForTimeout(150);
    await (await p.$('#stage')).screenshot({ path: OUT + '/' + name + '.png', omitBackground: true });
    manifest[name] = { w, h, ax: anchor ? anchor[0] : w / 2, ay: anchor ? anchor[1] : h / 2 };
  }
  // A positioned element from the tracker (by id or the first match), centred on the stage.
  const centre = ({ src, sel, w, h, strip }) => { const t = document.createElement('div'); t.innerHTML = src; const e = t.querySelector(sel); e.removeAttribute('id');
    e.style.left = w / 2 + 'px'; e.style.top = h / 2 + 'px'; e.style.transform = ''; e.style.position = 'absolute'; if (strip) e.style.filter = e.style.filter; document.getElementById('stage').appendChild(e); };
  // An SVG <g> from a full-screen svg, redrawn untransformed with its own viewBox; the anchor is the g's origin.
  const group = ({ src, sel, vb, w, h }) => { const t = document.createElement('div'); t.innerHTML = src; const svg = t.querySelector('svg'); const g = t.querySelector(sel); g.style.transform = '';
    const defs = svg.querySelector('defs'); document.getElementById('stage').innerHTML = '<svg width="' + w + '" height="' + h + '" viewBox="' + vb + '" style="overflow:visible">' + (defs ? defs.outerHTML : '') + g.outerHTML + '</svg>'; };
  const pieces = ({ src, keep, vb, w, h }) => { const t = document.createElement('div'); t.innerHTML = src; const svg = t.querySelector('svg'); const g = svg.querySelector(keep); g.style.transform = '';
    const defs = svg.querySelector('defs'); document.getElementById('stage').innerHTML = '<svg width="' + w + '" height="' + h + '" viewBox="' + vb + '" style="overflow:visible">' + (defs ? defs.outerHTML : '') + g.outerHTML + '</svg>'; };

  // Hats: brim/crown and the eyes under it, separately (they move by different amounts). Origin at the svg's 0,0.
  for (const [m, n] of [['luffy', 'strawhat'], ['chopperhat', 'chopperhat'], ['fedora', 'fedora']]) {
    await shot(n, 600, 480, pieces, { src: T[m], keep: '#hatG', vb: '-300 -220 600 480', w: 600, h: 480 }, '', [300, 220]);
    await shot(n + '-eyes', 600, 480, pieces, { src: T[m], keep: '#hatEyes', vb: '-300 -220 600 480', w: 600, h: 480 }, '', [300, 220]);
  }
  // Pointers drawn along +x from their pivot.
  for (const [m, id, n] of [['zoro', '#sw0', 'sword0'], ['zoro', '#sw1', 'sword1'], ['zoro', '#sw2', 'sword2'], ['bat', '#sw1', 'bat'], ['nichirin', '#sw1', 'nichirin'], ['hands', '#sw0', 'arm']])
    await shot(n, 600, 160, group, { src: T[m], sel: id, vb: '-40 -80 600 160', w: 600, h: 160 }, '', [40, 80]);
  for (let k = 1; k <= 7; k++) await shot('dball' + k, k === 4 ? 200 : 130, k === 4 ? 200 : 130, centre, { src: T.dragonballs, sel: '#db' + k, w: k === 4 ? 200 : 130, h: k === 4 ? 200 : 130 });
  await shot('cloud', 360, 280, centre, { src: T.cloud, sel: '#cloudw', w: 360, h: 280 }, '.cloudw{margin-left:-150px;margin-top:-115px}');
  await shot('butterfly', 110, 96, centre, { src: T.butterflies, sel: '#bf0', w: 110, h: 96 });
  await shot('chidori', 280, 280, centre, { src: T.chidori, sel: '#chid', w: 280, h: 280 });
  await shot('crow', 130, 100, centre, { src: T.crows, sel: '#cr0', w: 130, h: 100 });
  await shot('room', 480, 480, centre, { src: T.room, sel: '#roomd', w: 480, h: 480 });
  await shot('heart', 90, 90, centre, { src: T.hearts, sel: '#ht0', w: 90, h: 90 }, '.heart{font-size:54px!important;transform:translate(-50%,-50%)!important}');
  await shot('die3', 120, 120, centre, { src: T.dice, sel: '#dc0', w: 120, h: 120 });
  await shot('die5', 120, 120, centre, { src: T.dice, sel: '#dc1', w: 120, h: 120 });
  await shot('ballondor', 300, 300, centre, { src: T.ballondor, sel: '#gold', w: 300, h: 300 });
  await shot('ball', 110, 110, centre, { src: T.dribble, sel: '#drib', w: 110, h: 110 }, '.ball{margin:-23px 0 0 -23px}');
  await shot('crown', 300, 260, centre, { src: T.crown, sel: '#crown', w: 300, h: 260 });
  await shot('reticle', 250, 250, centre, { src: T.reticle, sel: '#ret', w: 250, h: 250 }, '.reticle .lab{display:none}');
  await shot('scouter', 250, 250, centre, { src: T.scouter, sel: '#ret', w: 250, h: 250 }, '.reticle .lab{display:none}');
  await shot('getsuga', 320, 230, centre, { src: T.getsuga, sel: '#gts', w: 320, h: 230 });
  await shot('shadow', 130, 210, centre, { src: T.shadows, sel: '#sh0', w: 130, h: 210 }, '.shadowm{margin-top:-85px}');
  await shot('fist', 200, 180, centre, { src: T.fist, sel: '#fist', w: 200, h: 180 });
  await shot('cut', 320, 60, centre, { src: T.cleave, sel: '.cut', w: 320, h: 60 }, '.cut{margin-top:-3px}');
  // Compass: the dial without its needle, and the needle alone.
  await shot('compass', 260, 260, centre, { src: T.compass.replace(/<g class="nd"[\s\S]*?<\/g>/, ''), sel: '#cmp', w: 260, h: 260 });
  await shot('compass-needle', 260, 260, centre, { src: T.compass, sel: '#cmp', w: 260, h: 260 }, '.compass circle, .compass text{display:none}');
  // Walkers: standing, and two walking frames with the legs swung either way. Anchor at the feet.
  for (const m of ['walkluffy', 'walkg5', 'walkzoro']) {
    const n = m.slice(4);
    const walkCss = (a) => '.walker{margin-left:-75px} .lgL,.lgR{transform-box:fill-box;transform-origin:50% 0} .lgL{transform:rotate(' + a + 'deg)!important} .lgR{transform:rotate(' + (-a) + 'deg)!important}';
    await shot('walk-' + n, 200, 300, ({ src }) => { const t = document.createElement('div'); t.innerHTML = src; const e = t.querySelector('#walker'); e.style.left = '100px'; e.style.top = '30px'; e.style.position = 'absolute'; document.getElementById('stage').appendChild(e); }, { src: T[m] }, walkCss(0), [100, 280]);
    for (const [f, a] of [['a', 18], ['b', -18]])
      await shot('walk-' + n + '-' + f, 200, 300, ({ src }) => { const t = document.createElement('div'); t.innerHTML = src; const e = t.querySelector('#walker'); e.style.left = '100px'; e.style.top = '30px'; e.style.position = 'absolute'; document.getElementById('stage').appendChild(e); }, { src: T[m] }, walkCss(a), [100, 280]);
  }
  fs.writeFileSync(OUT + '/sprites.json', JSON.stringify(manifest, null, 1));
  await b.close();
})();
