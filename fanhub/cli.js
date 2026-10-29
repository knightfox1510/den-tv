#!/usr/bin/env node
/* Print the Fan Hub feed and what the TV would do with it.
 *   node fanhub/cli.js                 today's feed, readable
 *   node fanhub/cli.js --json          the raw feed
 *   node fanhub/cli.js --watch 60      check every 60 s and print goals and new episodes as they happen
 *   node fanhub/cli.js --sample        the built-in sample feed, no internet needed
 * Settings and keys come from fanhub/config.json if it exists (copy config.example.json).
 */
'use strict';
const fs = require('fs'), path = require('path');
const hub = require('./fanhub.js');

const args = process.argv.slice(2), flag = (f) => args.includes(f);
const cfgPath = path.join(__dirname, 'config.json');
let config = {};
if (fs.existsSync(cfgPath)) { try { config = JSON.parse(fs.readFileSync(cfgPath, 'utf8')); } catch (e) { console.error('fanhub/config.json is not valid JSON: ' + e.message); process.exit(1); } }

function print(feed, now) {
  const tz = feed.tz, when = (iso) => hub.fmtDay(iso, tz, now) + ' ' + hub.fmtTime(iso, tz), out = [];
  out.push('den-tv Fan Hub · ' + when(feed.updated) + (feed.sample ? ' · SAMPLE DATA' : ''));
  (config.teams || hub.DEFAULTS.teams).forEach((t) => {
    const live = hub.liveOf(feed, t.name), next = hub.nextOf(feed, t.name, now), last = hub.lastOf(feed, t.name);
    out.push('', hub.displayName(t.name) + ' (' + t.sport + ')');
    if (live) out.push('  LIVE  ' + hub.scoreLine(live) + (live.minute ? ' · ' + live.minute : '') + (live.detail ? ' · ' + live.detail : ''));
    if (next) out.push('  Next  ' + hub.displayName(next.home) + ' v ' + hub.displayName(next.away) + ' · ' + next.league + ' · ' + when(next.start) + ' · ' + hub.fmtIn(next.start, now));
    if (last) out.push('  Last  ' + hub.scoreLine(last) + (last.detail ? ' · ' + last.detail : ''));
    if (!live && !next && !last) out.push('  Nothing found');
  });
  if (feed.table.length) out.push('', (config.table || hub.DEFAULTS.table).name + ' table', ...feed.table.slice(0, 5).map((r) => '  ' + String(r.rank).padStart(2) + '  ' + hub.displayName(r.team).padEnd(18) + String(r.points).padStart(3) + ' pts  ' + r.played + ' played  ' + r.form));
  if (feed.episodes.length) out.push('', 'Anime', ...feed.episodes.map((e) => '  ' + e.show.padEnd(16) + (e.status === 'off' ? 'not airing right now' : (e.status === 'out' ? 'Ep ' + e.episode + ' out ' + hub.fmtIn(e.airingAt, now) : 'Ep ' + e.episode + ' ' + when(e.airingAt) + ' · ' + hub.fmtIn(e.airingAt, now)))));
  if (feed.weather) { const h = hub.weekendHike(feed, now); out.push('', 'Weather at ' + feed.weather.place, ...feed.weather.days.map((d) => '  ' + d.date + '  ' + d.text.padEnd(14) + String(d.max).padStart(3) + '°  rain ' + d.rain + '%'));
    if (h) out.push('  Weekend hike: ' + (h.go ? 'go ' + h.wd + ', ' + h.text.toLowerCase() + ', ' + h.max + '°' : 'wet both days, maybe skip')); }
  const rule = hub.whatToShow(feed, now);
  out.push('', 'The TV right now: ' + (rule ? (rule.look ? 'switch to "' + rule.look + '" · ' : '') + rule.why : 'no rule applies, your own look stays on'));
  if (feed.sources.length) out.push('', 'Sources', ...feed.sources.map((s) => '  ' + (s.ok ? 'ok  ' : s.off ? 'off ' : 'FAIL') + '  ' + s.label + (s.ok ? ' (' + s.count + ')' : ' · ' + s.error)));
  console.log(out.join('\n'));
}

(async () => {
  const now = Date.now();
  const load = (prev) => (flag('--sample') ? Promise.resolve(hub.sampleFeed(Date.now())) : hub.fetchFeed(config, Date.now(), prev));
  let feed = await load();
  if (flag('--json')) { console.log(JSON.stringify(feed, null, 2)); return; }
  print(feed, now);
  const i = args.indexOf('--watch');
  if (i >= 0) { const every = Math.max(30, +args[i + 1] || 60) * 1000; console.log('\nWatching every ' + every / 1000 + ' s. Ctrl+C to stop.');
    setInterval(async () => { try { const next = await load(feed); hub.momentsBetween(feed, next).forEach((m) => console.log(new Date().toLocaleTimeString('en-GB') + '  MOMENT ' + m.moment.toUpperCase() + ' · ' + m.why)); feed = next; } catch (e) { console.log('check failed: ' + e.message); } }, every); }
})().catch((e) => { console.error(e.message || e); process.exit(1); });
