/* den-tv Fan Hub data: fixtures, live scores, anime episodes and weather in one feed.
 *
 * Works in Node 18+ (require('./fanhub.js')) and in a browser (<script src> sets window.denFanHub).
 * No dependencies. Sources:
 *   TheSportsDB   fixtures, results, league table   free test key "123", no sign-up
 *   football-data live football scores              optional free key (football-data.org)
 *   CricketData   live cricket scores               optional free key (cricketdata.org, 100 calls a day)
 *   AniList       anime episode schedule            no key
 *   Open-Meteo    weather for the hike forecast     no key
 * Keys live in the TV's settings (or fanhub/config.json, which git ignores), never in the repo.
 */
(function (root) {
  'use strict';

  const DEFAULTS = {
    tz: 'Asia/Kolkata',
    teams: [
      { name: 'FC Barcelona', sport: 'football', sportsdb: '133739', footballData: 81 },
      { name: 'India', sport: 'cricket', sportsdb: '137143', cricketName: 'India' },
      { name: 'Mumbai Indians', sport: 'cricket', sportsdb: '135795', cricketName: 'Mumbai Indians' },
    ],
    table: { sportsdb: '4335', name: 'La Liga' },
    shows: ['One Piece', 'Jujutsu Kaisen', 'Demon Slayer'],
    place: { name: 'Rajmachi', lat: 18.83, lon: 73.4 },
    keys: { sportsdb: '123', footballData: '', cricketData: '' },
  };

  /* ---------- names ---------- */
  const SHORT = { Barcelona: 'BAR', 'FC Barcelona': 'BAR', 'Barça': 'BAR', 'Real Madrid': 'RMA', 'Atlético Madrid': 'ATM', 'Atletico Madrid': 'ATM', Sevilla: 'SEV', Villarreal: 'VIL', 'Real Sociedad': 'RSO', 'Athletic Bilbao': 'ATH', Valencia: 'VAL', 'Deportivo Alavés': 'ALA',
    India: 'IND', Australia: 'AUS', England: 'ENG', Pakistan: 'PAK', 'South Africa': 'SA', 'New Zealand': 'NZ', 'Sri Lanka': 'SL', 'West Indies': 'WI', Bangladesh: 'BAN', Afghanistan: 'AFG',
    'Mumbai Indians': 'MI', 'Chennai Super Kings': 'CSK', 'Royal Challengers Bengaluru': 'RCB', 'Royal Challengers Bangalore': 'RCB', 'Kolkata Knight Riders': 'KKR', 'Delhi Capitals': 'DC', 'Rajasthan Royals': 'RR', 'Sunrisers Hyderabad': 'SRH', 'Punjab Kings': 'PBKS', 'Gujarat Titans': 'GT', 'Lucknow Super Giants': 'LSG' };
  const DISPLAY = { Barcelona: 'Barça', 'FC Barcelona': 'Barça' };
  const clean = (n) => String(n || '').replace(/ Cricket$/, '').trim();
  const displayName = (n) => DISPLAY[clean(n)] || clean(n);
  function short(n) { const c = clean(n); if (SHORT[c]) return SHORT[c]; const w = c.split(/\s+/).filter(Boolean); return (w.length > 1 ? w.map((x) => x[0]).join('') : c.slice(0, 3)).toUpperCase(); }

  /* ---------- time ---------- */
  function local(iso, tz, opts) { return new Date(iso).toLocaleString('en-GB', Object.assign({ timeZone: tz || DEFAULTS.tz }, opts)); }
  const fmtTime = (iso, tz) => local(iso, tz, { hour: '2-digit', minute: '2-digit', hour12: false });
  const fmtDay = (iso, tz, now) => Math.abs(Date.parse(iso) - (now || Date.now())) > 6 * 864e5 ? local(iso, tz, { weekday: 'short', day: 'numeric', month: 'short' }) : local(iso, tz, { weekday: 'short' });
  function fmtIn(iso, now) { let m = Math.round((Date.parse(iso) - now) / 60000); const neg = m < 0; m = Math.abs(m); const d = Math.floor(m / 1440), h = Math.floor((m % 1440) / 60), mm = m % 60;
    const s = d ? d + 'd ' + h + 'h' : h ? h + 'h ' + mm + 'm' : mm + 'm'; return neg ? s + ' ago' : 'in ' + s; }
  function localParts(now, tz) { const p = Object.fromEntries(new Intl.DateTimeFormat('en-GB', { timeZone: tz || DEFAULTS.tz, weekday: 'short', hour: 'numeric', hour12: false, month: 'numeric' }).formatToParts(new Date(now)).map((x) => [x.type, x.value]));
    return { day: p.weekday, hour: +p.hour % 24, month: +p.month }; }

  /* ---------- fetching ---------- */
  async function getJSON(url, opts) {
    const ctl = typeof AbortController !== 'undefined' ? new AbortController() : null; const t = ctl && setTimeout(() => ctl.abort(), 8000);
    try { const r = await fetch(url, Object.assign({ signal: ctl && ctl.signal }, opts)); if (!r.ok) throw new Error('HTTP ' + r.status); return await r.json(); }
    finally { if (t) clearTimeout(t); }
  }
  const FINISHED = /^(FT|AET|PEN|AP|MATCH FINISHED|FINISHED|ABD|AWD|CANC|PST|POSTP|ABANDONED|CANCELLED|POSTPONED)/;

  function fromSportsDb(e, team, fromLast, now) {
    const ts = e.strTimestamp ? e.strTimestamp.replace(/(\+00:00)?$/, '') : (e.dateEvent || '') + 'T' + (e.strTime || '00:00:00');
    const start = new Date(/Z$/.test(ts) ? ts : ts + 'Z').toISOString(), st = String(e.strStatus || '').toUpperCase();
    const hs = e.intHomeScore, as = e.intAwayScore, has = hs != null && hs !== '';
    const status = fromLast || FINISHED.test(st) ? 'finished' : Date.parse(start) > now ? 'upcoming' : has ? 'live' : 'upcoming';
    const m = { id: 'tsdb-' + e.idEvent, sport: team.sport, team: team.name, league: e.strLeague || '', home: clean(e.strHomeTeam), away: clean(e.strAwayTeam), start, status, score: null, goals: null, minute: null, detail: null, source: 'TheSportsDB' };
    if (/^(ABD|ABANDONED|CANC|CANCELLED|PST|POSTP|POSTPONED)/.test(st)) m.detail = st.startsWith('ABD') || st.startsWith('ABAN') ? 'Abandoned' : st.startsWith('PST') || st.startsWith('POSTP') ? 'Postponed' : 'Cancelled';
    if (has && team.sport === 'football') { m.goals = [+hs, +as]; m.score = hs + '–' + as; }
    if (has && team.sport === 'cricket') m.score = short(m.home) + ' ' + hs + (as != null && as !== '' ? ' · ' + short(m.away) + ' ' + as : '');
    if (e.strResult && !m.detail && team.sport === 'cricket') m.detail = String(e.strResult).split(/\r?\n/)[0].slice(0, 80); // football results hold a match report, not a result line
    if (status === 'live' && e.strProgress) m.minute = e.strProgress + "'";
    return m;
  }
  async function sportsdbTeam(team, key, now) {
    const base = 'https://www.thesportsdb.com/api/v1/json/' + (key || '123') + '/';
    const [n, l] = await Promise.all([getJSON(base + 'eventsnext.php?id=' + team.sportsdb), getJSON(base + 'eventslast.php?id=' + team.sportsdb)]);
    return (n.events || []).map((e) => fromSportsDb(e, team, false, now)).concat((l.results || []).map((e) => fromSportsDb(e, team, true, now)));
  }
  async function sportsdbTable(table, key, now) {
    const d = new Date(now), y = d.getUTCFullYear(), season = d.getUTCMonth() >= 6 ? y + '-' + (y + 1) : (y - 1) + '-' + y;
    const r = await getJSON('https://www.thesportsdb.com/api/v1/json/' + (key || '123') + '/lookuptable.php?l=' + table.sportsdb + '&s=' + season);
    return (r.table || []).map((t) => ({ rank: +t.intRank, team: clean(t.strTeam), played: +t.intPlayed, points: +t.intPoints, form: t.strForm || '' }));
  }
  async function footballDataLive(team, key) {
    const r = await getJSON('https://api.football-data.org/v4/teams/' + team.footballData + '/matches?status=LIVE,IN_PLAY,PAUSED', { headers: { 'X-Auth-Token': key } });
    return (r.matches || []).map((x) => { const h = x.score && x.score.fullTime ? x.score.fullTime.home : null, a = x.score && x.score.fullTime ? x.score.fullTime.away : null;
      return { id: 'fd-' + x.id, sport: 'football', team: team.name, league: x.competition ? x.competition.name : '', home: clean(x.homeTeam.shortName || x.homeTeam.name), away: clean(x.awayTeam.shortName || x.awayTeam.name), start: x.utcDate, status: 'live', goals: h == null ? null : [h, a], score: h == null ? null : h + '–' + a, minute: x.minute ? x.minute + "'" : x.status === 'PAUSED' ? 'HT' : null, detail: null, source: 'football-data.org' }; });
  }
  async function cricketDataLive(teams, key) {
    const r = await getJSON('https://api.cricapi.com/v1/currentMatches?apikey=' + encodeURIComponent(key) + '&offset=0');
    if (r.status && r.status !== 'success') throw new Error(r.reason || 'CricketData refused the request');
    const out = [];
    (r.data || []).forEach((x) => { const team = teams.find((t) => (x.teams || []).some((n) => clean(n) === t.cricketName)); if (!team) return;
      const sc = (x.score || []).map((s) => short(String(s.inning || '').replace(/ Inning.*$/i, '')) + ' ' + s.r + '/' + s.w + ' (' + s.o + ')');
      out.push({ id: 'cd-' + x.id, sport: 'cricket', team: team.name, league: x.matchType ? x.matchType.toUpperCase() : '', home: clean((x.teams || [])[0]), away: clean((x.teams || [])[1]), start: new Date((x.dateTimeGMT || '').replace(/Z?$/, 'Z')).toISOString(),
        status: x.matchEnded ? 'finished' : x.matchStarted ? 'live' : 'upcoming', score: sc.length ? sc[sc.length - 1] : null, goals: null, minute: null, detail: x.status || null, source: 'CricketData.org' }); });
    return out;
  }
  const ANI_SEARCH = 'query($s:String){Page(perPage:8){media(search:$s,type:ANIME,sort:[SEARCH_MATCH]){id status title{english romaji} startDate{year month} nextAiringEpisode{episode airingAt}}}}';
  const ANI_SCHED = 'query($ids:[Int],$a:Int,$b:Int){Page(perPage:50){airingSchedules(mediaId_in:$ids,airingAt_greater:$a,airingAt_lesser:$b,sort:TIME){episode airingAt mediaId}}}';
  const ani = (query, variables) => getJSON('https://graphql.anilist.co', { method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json' }, body: JSON.stringify({ query, variables }) });
  async function anilist(shows, now) {
    const picks = await Promise.all(shows.slice(0, 8).map(async (s) => { // a handful of shows stays well inside AniList's rate limit
      const r = await ani(ANI_SEARCH, { s }); const list = (r.data && r.data.Page.media) || [];
      return { show: s, media: list.find((x) => x.status === 'RELEASING') || list.slice().sort((a, b) => ((b.startDate.year || 0) * 12 + (b.startDate.month || 0)) - ((a.startDate.year || 0) * 12 + (a.startDate.month || 0)))[0] }; }));
    const ids = picks.filter((p) => p.media).map((p) => p.media.id), t = Math.floor(now / 1000);
    const r = ids.length ? await ani(ANI_SCHED, { ids, a: t - 3 * 86400, b: t + 14 * 86400 }) : null; const sched = (r && r.data && r.data.Page.airingSchedules) || [];
    const out = [];
    picks.forEach(({ show, media }) => { if (!media) return; const mine = sched.filter((x) => x.mediaId === media.id);
      const last = mine.filter((x) => x.airingAt * 1000 <= now).pop(), na = media.nextAiringEpisode, next = mine.find((x) => x.airingAt * 1000 > now) || (na && { episode: na.episode, airingAt: na.airingAt }); // a show on a break still has a next date
      if (last) out.push({ show, episode: last.episode, airingAt: new Date(last.airingAt * 1000).toISOString(), status: 'out' });
      if (next) out.push({ show, episode: next.episode, airingAt: new Date(next.airingAt * 1000).toISOString(), status: 'upcoming' });
      if (!last && !next) out.push({ show, episode: null, airingAt: null, status: 'off' }); });
    return out;
  }
  const WMO = (c) => c === 0 ? 'Clear' : c <= 2 ? 'Partly cloudy' : c === 3 ? 'Cloudy' : c <= 48 ? 'Fog' : c <= 57 ? 'Drizzle' : c <= 67 ? 'Rain' : c <= 77 ? 'Snow' : c <= 82 ? 'Showers' : 'Thunderstorms';
  async function openMeteo(place, tz) {
    const r = await getJSON('https://api.open-meteo.com/v1/forecast?latitude=' + place.lat + '&longitude=' + place.lon + '&daily=weathercode,temperature_2m_max,precipitation_probability_max&forecast_days=7&timezone=' + encodeURIComponent(tz));
    const d = r.daily || {}; return { place: place.name, days: (d.time || []).map((date, i) => ({ date, code: d.weathercode[i], text: WMO(d.weathercode[i]), max: Math.round(d.temperature_2m_max[i]), rain: d.precipitation_probability_max[i] })) };
  }

  /* Fetch everything; a source that fails is reported in feed.sources and the rest still arrive.
     Pass the previous feed as `prev` and a failed source keeps its last good data (marked stale). */
  async function fetchFeed(config, now, prev) {
    const c = Object.assign({}, DEFAULTS, config || {}), k = Object.assign({}, DEFAULTS.keys, (config || {}).keys); now = now || Date.now();
    const jobs = [
      ['fixtures', 'Fixtures and results · TheSportsDB', () => Promise.all(c.teams.filter((t) => t.sportsdb).map((t) => sportsdbTeam(t, k.sportsdb, now))).then((a) => a.flat())],
      ['table', c.table.name + ' table · TheSportsDB', () => sportsdbTable(c.table, k.sportsdb, now)],
      ['football', 'Live football · football-data.org', k.footballData ? () => Promise.all(c.teams.filter((t) => t.footballData).map((t) => footballDataLive(t, k.footballData))).then((a) => a.flat()) : null],
      ['cricket', 'Live cricket · CricketData.org', k.cricketData ? () => cricketDataLive(c.teams.filter((t) => t.cricketName), k.cricketData) : null],
      ['anime', 'Anime episodes · AniList', () => anilist(c.shows, now)],
      ['weather', 'Weather · Open-Meteo', () => openMeteo(c.place, c.tz)],
    ];
    const res = await Promise.all(jobs.map(([id, label, run]) => run ? run().then((data) => ({ id, label, ok: true, data }), (e) => ({ id, label, ok: false, error: e.message || String(e) })) : { id, label, ok: false, off: true, error: 'No key yet' }));
    if (prev && !prev.sample) { const keep = { fixtures: () => prev.matches.filter((m) => /^tsdb-/.test(m.id)), table: () => prev.table, anime: () => prev.episodes, weather: () => prev.weather };
      res.forEach((r) => { if (!r.ok && !r.off && keep[r.id]) { r.data = keep[r.id](); r.stale = true; } }); }
    const by = Object.fromEntries(res.map((r) => [r.id, r]));
    let matches = (by.fixtures.data || []).slice();
    [].concat(by.football.data || [], by.cricket.data || []).forEach((lv) => { // a live score replaces the fixture it belongs to
      const i = matches.findIndex((m) => m.sport === lv.sport && Math.abs(Date.parse(m.start) - Date.parse(lv.start)) < 6 * 3600e3 && (short(m.home) === short(lv.home) || short(m.away) === short(lv.away)));
      if (i >= 0) matches[i] = Object.assign({}, matches[i], lv, { id: matches[i].id }); else matches.push(lv); });
    matches.sort((a, b) => Date.parse(a.start) - Date.parse(b.start));
    return { sample: false, updated: new Date(now).toISOString(), tz: c.tz, matches, table: by.table.data || [], episodes: by.anime.data || [], weather: by.weather.data || null,
      sources: res.map(({ id, label, ok, off, stale, error, data }) => ({ id, label, ok, off: !!off, stale: !!stale, error: error || null, count: Array.isArray(data) ? data.length : data ? 1 : 0 })) };
  }

  /* A feed with times relative to now, for previews that can't reach the internet. Clearly marked sample. */
  function sampleFeed(now) {
    now = now || Date.now(); const H = 3600e3, D = 24 * H, iso = (t) => new Date(t).toISOString();
    const weekday = (n) => { const d = new Date(now + n * D); return d.toISOString().slice(0, 10); };
    const at = (days, h, m) => { const d = new Date(now + days * D); d.setUTCHours(h, m, 0, 0); return iso(d); }; // a day ahead at a fixed UTC time, so sample times look real
    return { sample: true, updated: iso(now), tz: DEFAULTS.tz,
      matches: [
        { id: 's1', sport: 'football', team: 'FC Barcelona', league: 'La Liga', home: 'Barcelona', away: 'Real Madrid', start: iso(now - 70 * 60e3), status: 'live', goals: [2, 1], score: '2–1', minute: "67'", detail: null, source: 'sample' },
        { id: 's2', sport: 'football', team: 'FC Barcelona', league: 'La Liga', home: 'Barcelona', away: 'Sevilla', start: at(3, 15, 15), status: 'upcoming', goals: null, score: null, minute: null, detail: null, source: 'sample' },
        { id: 's3', sport: 'cricket', team: 'India', league: 'T20I', home: 'India', away: 'Australia', start: iso(now - 2 * H), status: 'live', goals: null, score: 'IND 187/3 (16.2)', minute: null, detail: 'India need 18 runs in 22 balls', source: 'sample' },
        { id: 's4', sport: 'cricket', team: 'Mumbai Indians', league: 'IPL', home: 'Mumbai Indians', away: 'Chennai Super Kings', start: at(140, 14, 0), status: 'upcoming', goals: null, score: null, minute: null, detail: null, source: 'sample' },
      ],
      table: [{ rank: 1, team: 'Barcelona', played: 10, points: 27, form: 'WWWWD' }, { rank: 2, team: 'Real Madrid', played: 10, points: 24, form: 'WLWWW' }, { rank: 3, team: 'Atlético Madrid', played: 10, points: 21, form: 'DWWLW' }],
      episodes: [{ show: 'Jujutsu Kaisen', episode: 14, airingAt: iso(now - 5 * H), status: 'out' }, { show: 'One Piece', episode: 1150, airingAt: at(4, 15, 0), status: 'upcoming' }, { show: 'Demon Slayer', episode: 9, airingAt: at(2, 16, 30), status: 'upcoming' }],
      weather: { place: 'Rajmachi', days: [0, 1, 2, 3, 4, 5, 6].map((n) => ({ date: weekday(n), code: n % 3 ? 2 : 0, text: n % 3 ? 'Partly cloudy' : 'Clear', max: 27 + (n % 3), rain: 10 + n * 4 })) },
      sources: [] };
  }

  /* ---------- reading the feed ---------- */
  const followed = (feed, team) => feed.matches.filter((m) => m.team === team);
  const liveOf = (feed, team) => followed(feed, team).find((m) => m.status === 'live') || null;
  const nextOf = (feed, team, now) => followed(feed, team).find((m) => m.status === 'upcoming' && Date.parse(m.start) > (now || Date.now()) - 3 * 3600e3) || null;
  const lastOf = (feed, team) => followed(feed, team).filter((m) => m.status === 'finished').pop() || null;
  function newEpisode(feed, now, hours) { const lim = (hours || 48) * 3600e3; return feed.episodes.filter((e) => e.status === 'out' && (now || Date.now()) - Date.parse(e.airingAt) < lim).sort((a, b) => Date.parse(b.airingAt) - Date.parse(a.airingAt))[0] || null; }
  const nextEpisodes = (feed) => feed.episodes.filter((e) => e.status === 'upcoming').sort((a, b) => Date.parse(a.airingAt) - Date.parse(b.airingAt));
  function weekendHike(feed, now, tz) { if (!feed.weather) return null; const days = feed.weather.days.map((d) => Object.assign({ wd: new Date(d.date + 'T12:00:00Z').toLocaleString('en-GB', { weekday: 'long', timeZone: 'UTC' }) }, d)).filter((d) => d.wd === 'Saturday' || d.wd === 'Sunday');
    if (!days.length) return null; const best = days.slice().sort((a, b) => a.rain - b.rain)[0]; return Object.assign({ go: best.rain < 50 && best.code < 61, place: feed.weather.place }, best); }
  const scoreLine = (m) => m.sport === 'football' ? displayName(m.home) + ' ' + (m.score || 'v') + ' ' + displayName(m.away) : (m.score || displayName(m.home) + ' v ' + displayName(m.away));

  /* Which look the TV should switch to right now. First match wins; `on` holds the Auto rule switches. */
  const LOOK_FOR_SHOW = { 'One Piece': 'grand-line', 'Jujutsu Kaisen': 'domain', 'Demon Slayer': 'water-breathing', Naruto: 'nine-tails', 'Dragon Ball': 'super-saiyan', 'Solo Leveling': 'arise' };
  function whatToShow(feed, now, on) {
    now = now || Date.now(); on = Object.assign({ match: true, india: true, ipl: true, episode: true, weekend: true, late: true }, on); const H = 3600e3, near = (m, before, after) => m && (m.status === 'live' || (Date.parse(m.start) - before * H <= now && now <= Date.parse(m.start) + after * H));
    const p = localParts(now, feed.tz);
    const barca = liveOf(feed, 'FC Barcelona') || nextOf(feed, 'FC Barcelona', now);
    if (on.match && near(barca, 2, 2.5)) return { rule: 'match', look: 'camp-nou', why: barca.status === 'live' ? 'Barça are playing: ' + scoreLine(barca) : 'Barça kick off at ' + fmtTime(barca.start, feed.tz) };
    const india = liveOf(feed, 'India') || nextOf(feed, 'India', now);
    if (on.india && near(india, 1, 8)) return { rule: 'india', look: 'blue-billion', why: india.status === 'live' ? 'India are playing: ' + scoreLine(india) : 'India start at ' + fmtTime(india.start, feed.tz) };
    const mi = liveOf(feed, 'Mumbai Indians') || nextOf(feed, 'Mumbai Indians', now);
    if (on.ipl && near(mi, 3, 5)) return { rule: 'ipl', look: 'wankhede', why: 'Mumbai Indians play today' };
    const ep = newEpisode(feed, now, 18);
    if (on.episode && ep && LOOK_FOR_SHOW[ep.show]) return { rule: 'episode', look: LOOK_FOR_SHOW[ep.show], why: ep.show + ' Ep ' + ep.episode + ' is out' };
    const hike = weekendHike(feed, now);
    if (on.weekend && (p.day === 'Sat' || p.day === 'Sun') && p.hour < 10 && hike && hike.go) return { rule: 'weekend', look: 'trail-dawn', why: 'Weekend morning, ' + hike.text.toLowerCase() + ' at ' + hike.place };
    if (on.late && (p.hour >= 23 || p.hour < 5)) return { rule: 'late', look: null, calm: true, why: 'Late night: calm motion, no moments' };
    return null;
  }

  /* Big moments, found by comparing two feeds: a goal for a followed football team, or an episode coming out. */
  function momentsBetween(prev, next) {
    const out = []; if (!prev || !next) return out;
    next.matches.forEach((m) => { if (m.sport !== 'football' || !m.goals) return; const p = prev.matches.find((x) => x.id === m.id); if (!p) return; const pg = p.goals || [0, 0];
      const homeIsUs = short(m.home) === short(m.team); const ours = homeIsUs ? 0 : 1;
      if (m.goals[ours] > pg[ours]) out.push({ moment: 'goal', team: m.team, why: scoreLine(m) + (m.minute ? ' · ' + m.minute : '') }); });
    next.episodes.forEach((e) => { if (e.status !== 'out') return; const p = prev.episodes.find((x) => x.show === e.show && x.episode === e.episode); if (p && p.status === 'upcoming') out.push({ moment: 'drop', show: e.show, why: e.show + ' Ep ' + e.episode + ' is out' }); });
    return out;
  }

  const api = { DEFAULTS, fetchFeed, sampleFeed, whatToShow, momentsBetween, liveOf, nextOf, lastOf, newEpisode, nextEpisodes, weekendHike, scoreLine, displayName, short, fmtTime, fmtDay, fmtIn, LOOK_FOR_SHOW };
  if (typeof module === 'object' && module.exports) module.exports = api; else root.denFanHub = api;
})(typeof self !== 'undefined' ? self : this);
