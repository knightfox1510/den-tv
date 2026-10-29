const { chromium } = require('playwright');
const fs = require('fs');
const W = ['luffy','zoro','dragonballs','pole','cloud','butterflies','chidori','crows','room','hearts','hands','chopperhat','bat','fedora','dice','ballondor','dribble','crown','reticle','caret','walkluffy','walkg5','walkzoro','compass','scouter','odm','getsuga','shadows','fist','nichirin','cleave'];
(async () => {
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  const url = 'file:///home/user/dev-tv/mockups/den-looks.html';
  await p.goto(url); const out = {};
  for (const m of W) {
    await p.evaluate((m) => { localStorage.setItem('den2.setup', JSON.stringify({ done: true, phones: [] })); localStorage.setItem('den2.look', '"camp-nou"'); localStorage.setItem('den2.over', JSON.stringify({ mascot: m })); }, m);
    await p.reload(); await p.waitForTimeout(300);
    out[m] = await p.evaluate(() => document.getElementById('tracker').innerHTML);
  }
  fs.writeFileSync('tracker.json', JSON.stringify(out, null, 1));
  await b.close();
})();
