'use strict';
// Sport: kampprogram (ESPN, TheSportsDB, PPV-kanalnavne), stream-rangering og kameravinkler – som tv-appen.
const C = require('./core');
const X = require('./provider');

const LEAGUES = [
  ['den.1', 'Superligaen'], ['eng.1', 'Premier League'], ['ger.1', 'Bundesliga'], ['esp.1', 'La Liga'], ['eng.2', 'Championship'],
  ['ger.2', '2. Bundesliga'], ['uefa.champions', 'Champions League'], ['uefa.europa', 'Europa League'], ['uefa.europa.conf', 'Conference League'],
  ['ita.1', 'Serie A'], ['fra.1', 'Ligue 1'], ['den.cup', 'Pokalen'], ['eng.fa', 'FA Cup'], ['esp.copa_del_rey', 'Copa del Rey'],
  ['ger.dfb_pokal', 'DFB-Pokal'], ['eng.league_cup', 'Carabao Cup'],
  ['fifa.world', 'VM'], ['uefa.euro', 'EM'], ['fifa.worldq.uefa', 'VM-kvalifikation'], ['uefa.euroq', 'EM-kvalifikation'],
  ['uefa.nations', 'Nations League'], ['fifa.friendly', 'Venskabskampe'],
].map(([code, name]) => ({ code, name }));

const COUNTRY = [
  ['danmark', 'denmark'], ['norge', 'norway'], ['sverige', 'sweden'], ['tyskland', 'germany'], ['england', 'england'], ['frankrig', 'france'],
  ['spanien', 'spain'], ['italien', 'italy'], ['holland', 'netherlands'], ['nederlandene', 'netherlands'], ['belgien', 'belgium'], ['portugal', 'portugal'],
  ['finland', 'finland'], ['island', 'iceland'], ['skotland', 'scotland'], ['wales', 'wales'], ['irland', 'republic of ireland'],
  ['nordirland', 'northern ireland'], ['polen', 'poland'], ['schweiz', 'switzerland'], ['østrig', 'austria'], ['kroatien', 'croatia'],
  ['serbien', 'serbia'], ['tjekkiet', 'czechia'], ['ungarn', 'hungary'], ['tyrkiet', 'turkey'], ['grækenland', 'greece'], ['ukraine', 'ukraine'],
  ['brasilien', 'brazil'], ['argentina', 'argentina'],
];

const FOOTBALL = 'football', F1 = 'f1', DARTS = 'darts', HANDBALL = 'handball', TENNIS = 'tennis', MMA = 'mma', GOLF = 'golf', MOTOR = 'motor';
const SPORTS = [[FOOTBALL, 'Fodbold'], [F1, 'Formel 1'], [MOTOR, 'Motorsport'], [DARTS, 'Dart'], [HANDBALL, 'Håndbold'], [TENNIS, 'Tennis'], [MMA, 'UFC'], [GOLF, 'Golf']];
const sportName = s => (SPORTS.find(x => x[0] === s) || [s, s])[1];

const ESPN_OTHER = [['racing/f1', F1, 'Formel 1'], ['tennis/atp', TENNIS, 'ATP'], ['tennis/wta', TENNIS, 'WTA'], ['mma/ufc', MMA, 'UFC'], ['golf/pga', GOLF, 'PGA Tour'], ['racing/irl', MOTOR, 'IndyCar'], ['racing/nascar-premier', MOTOR, 'NASCAR']];
const TSDB = [['Darts', DARTS], ['Handball', HANDBALL]];
const GUIDE = [
  ['den.pokal', 'Pokalen', /^.*\b(betano ?pokalen|oddset pokalen|sydbank ?pokalen|landspokal\w*|dbu pokal\w*|pokalfodbold|pokalen)\b.*$/i, FOOTBALL, 'pokal'],
  ['motor.ppv', 'Motorsport', /^.*\b(indycar|indy 500|nascar|dtm|wec|le mans|superbike|worldsbk|wsbk|motogp|moto2|moto3|formula [234e]|formel [234e]|porsche carrera|super formula|imsa|supercars|rally|wrc)\b.*$/i, MOTOR, ''],
];
const GUIDE_LEAGUES = [{ code: 'den.pokal', name: 'Pokalen (fra kanalerne)' }];

// ---- hentning ----
const games = new Map();
const fetched = new Map();
const live = g => g.state === 'in', done = g => g.state === 'post';
function day(off, dash) {
  const d = new Date(); d.setDate(d.getDate() + off);
  const s = d.getFullYear() + String(d.getMonth() + 1).padStart(2, '0') + String(d.getDate()).padStart(2, '0');
  return dash ? s.slice(0, 4) + '-' + s.slice(4, 6) + '-' + s.slice(6) : s;
}
const team = () => ({ name: null, shortName: null, abbr: '', logo: '', score: -1 });
const iso = s => { const t = Date.parse(String(s || '').slice(0, 16) + 'Z'); return isNaN(t) ? 0 : t; };

// små køer, så vi ikke hamrer løs på de åbne API'er
function queue(n, gap = 0) {
  const q = []; let running = 0;
  const pump = () => {
    while (running < n && q.length) {
      const job = q.shift(); running++;
      job().catch(() => { }).finally(() => setTimeout(() => { running--; pump(); }, gap));
    }
  };
  return job => { q.push(job); pump(); };
}
const espnQ = queue(4), tsdbQ = queue(1, 2500);

function want(key, maxAge, q, job) {
  const f = fetched.get(key);
  if (f && Date.now() - f < maxAge) return;
  fetched.set(key, Date.now());
  q(() => job().catch(e => { fetched.delete(key); throw e; }));
}

/** Henter kampprogram; turneringer nogen følger (favCodes) hentes 14 dage frem, andre 3. */
function refresh(favCodes = new Set()) {
  for (const o of ESPN_OTHER) for (let i = -1; i <= 6; i++) {
    const d = day(i);
    want(o[0] + '|' + d, i === 0 ? 120e3 : 6 * 3600e3, espnQ, async () => parseOther(o, await X.json('https://site.api.espn.com/apis/site/v2/sports/' + o[0] + '/scoreboard?dates=' + d, 20000)));
  }
  for (const t of TSDB) for (let i = 0; i <= 6; i++) {
    const d = day(i, true);
    want('tsdb|' + t[0] + '|' + d, i === 0 ? 15 * 60e3 : 12 * 3600e3, tsdbQ, async () => parseTsdb(t[1], await X.json('https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d=' + d + '&s=' + t[0], 20000)));
  }
  for (const l of LEAGUES) for (let i = -1; i <= (favCodes.has(l.code) ? 14 : 3); i++) {
    const d = day(i);
    want(l.code + '|' + d, i === 0 ? 60e3 : i < 0 ? 6 * 3600e3 : 3 * 3600e3, espnQ, async () => parse(l, await X.json('https://site.api.espn.com/apis/site/v2/sports/soccer/' + l.code + '/scoreboard?dates=' + d, 20000)));
  }
}

function setStatus(g, st) {
  if (!st) return;
  if (st.type) { g.state = st.type.state || 'pre'; g.detail = st.type.shortDetail || ''; }
  g.clock = st.displayClock || '';
}

function parse(lg, o) {
  for (const e of (o && o.events) || []) {
    const g = { sport: FOOTBALL, id: String(e.id), league: lg.code, leagueName: lg.name, start: iso(e.date), state: 'pre', detail: '', clock: '', home: team(), away: team() };
    const comp = (e.competitions || [])[0];
    setStatus(g, (comp && comp.status) || e.status);
    for (const c of (comp && comp.competitors) || []) {
      const t = c.homeAway === 'home' ? g.home : g.away, tm = c.team;
      if (tm) { t.name = tm.displayName; t.shortName = tm.shortDisplayName || t.name; t.abbr = tm.abbreviation || ''; t.logo = tm.logo || ''; }
      const s = parseInt(c.score, 10); if (!isNaN(s)) t.score = s;
    }
    games.set(g.id + '|' + g.league, g);
  }
}

const SESSION = { FP1: '1. træning', FP2: '2. træning', FP3: '3. træning', Qual: 'Kvalifikation', Race: 'Løb', Sprint: 'Sprint', SS: 'Sprint-kvalifikation', SQ: 'Sprint-kvalifikation' };
function parseOther(src, o) {
  for (const e of (o && o.events) || []) {
    const name = e.name || '';
    if (src[1] === F1 && e.competitions) {
      e.competitions.forEach((c, k) => {
        const ab = c.type && c.type.abbreviation;
        const g = other(src, e.id + '-' + k, name.replace(/^(MSC Cruises|Heineken|Qatar Airways|Pirelli|Aramco|Lenovo|Crypto\.com|AWS|Louis Vuitton|Gulf Air|STC|Etihad Airways|Rolex|Formula 1)\s+/, ''), SESSION[ab] || ab || '', c.date, c.status);
        if (g) games.set(g.id + '|' + g.league, g);
      });
      continue;
    }
    const g = other(src, String(e.id), name, src[2], e.date, e.status);
    if (g) { if (src[1] !== MMA) g.allDay = true; games.set(g.id + '|' + g.league, g); }
  }
}
function other(src, id, title, sub, date, st) {
  const g = { sport: src[1], id: String(id), league: src[0], leagueName: src[2], title, sub, start: iso(date), state: 'pre', detail: '', clock: '', home: team(), away: team() };
  if (!g.start) return null;
  if (st && st.type) { g.state = st.type.state || 'pre'; g.detail = st.type.shortDetail || ''; }
  return g;
}

function parseTsdb(sport, o) {
  const strip = s => s.replace(' Håndbold', '').replace(' Handball', '').replace('Handball Sport Verein ', 'HSV ');
  for (const e of (o && o.events) || []) {
    const g = { sport, id: 't' + e.idEvent, league: 'tsdb.' + e.idLeague, state: 'pre', detail: '', clock: '', home: team(), away: team() };
    g.leagueName = String(e.strLeague || '').replace('German Handball-Bundesliga', 'Handball-Bundesliga').replace('Danish Mens Handball League', 'Herreligaen').replace('Danish Womens Handball League', 'Kvindeligaen').replace('PDC Darts', 'PDC');
    let time = e.strTime || '00:00:00';
    if (!time || time === 'null') time = '00:00:00';
    g.allDay = time.startsWith('00:00');
    g.start = Date.parse(e.dateEvent + 'T' + time.slice(0, 8) + 'Z');
    if (isNaN(g.start)) continue;
    const h = e.strHomeTeam || '', a = e.strAwayTeam || '';
    if (h && h !== 'null' && a && a !== 'null') {
      g.home.name = h; g.home.shortName = strip(h); g.away.name = a; g.away.shortName = strip(a);
      g.home.logo = e.strHomeTeamBadge || ''; g.away.logo = e.strAwayTeamBadge || '';
      const hs = parseInt(e.intHomeScore, 10), as = parseInt(e.intAwayScore, 10);
      if (!isNaN(hs) && !isNaN(as)) { g.home.score = hs; g.away.score = as; }
    } else { g.title = e.strEvent; g.sub = g.leagueName; }
    const status = e.strStatus || '';
    g.state = /^(FT|AOT|AET|Match Finished|Finished)$/i.test(status) ? 'post' : /^(1H|2H|HT|Live|In Progress)$/i.test(status) ? 'in' : 'pre';
    if (g.state === 'pre' && !g.allDay && Date.now() > g.start && Date.now() < g.start + 2 * 3600e3) g.state = 'in';
    games.set(g.id + '|' + g.league, g);
  }
}

const tables = new Map(), tableAt = new Map();
function table(code) {
  const t = tableAt.get(code);
  if (!t || Date.now() - t > 10 * 60e3) {
    tableAt.set(code, Date.now());
    X.json('https://site.api.espn.com/apis/v2/sports/soccer/' + code + '/standings', 20000).then(o => {
      const en = o && o.children && o.children[0] && o.children[0].standings && o.children[0].standings.entries;
      if (!en) return;
      tables.set(code, en.map(e => {
        const r = { team: e.team.shortDisplayName || e.team.displayName, logo: (e.team.logos && e.team.logos[0] && e.team.logos[0].href) || '', rank: 0, played: 0, points: 0, diff: '' };
        for (const s of e.stats || []) {
          if (s.name === 'rank') r.rank = s.value; else if (s.name === 'gamesPlayed') r.played = s.value;
          else if (s.name === 'points') r.points = s.value; else if (s.name === 'pointDifferential') r.diff = s.displayValue;
        }
        return r;
      }).sort((a, b) => a.rank - b.rank));
    }).catch(() => tableAt.delete(code));
  }
  return tables.get(code) || [];
}

function status(g) {
  if (live(g)) return g.detail ? g.detail.replace('Halftime', 'Pause').replace('HT', 'Pause') : g.clock || 'Live';
  if (done(g)) return 'Slut';
  if (g.allDay) return C.when(g.start).replace(/ \d{2}\.\d{2}$/, '');
  return C.when(g.start).replace('I dag ', '');
}


/** Alt der afhænger af brugerens kanaler og favoritter. */
function forUser(P) {
// ---- favoritter ----
const DEF = { teams: ['Sønderjyske', 'Barcelona', 'Wrexham', 'Hamburg'], leagues: ['den.1', 'den.pokal', 'eng.1', 'ger.1'], sports: ['f1', 'motor', 'darts', 'handball'], spoiler: false };
const prefs = () => Object.assign({}, DEF, P.state.sport);
function setPrefs(p) {
  const s = P.state.sport;
  if (p.teams != null) s.teams = (Array.isArray(p.teams) ? p.teams : String(p.teams).split(/[\n,]/)).map(x => x.trim()).filter(Boolean);
  if (p.leagues != null) s.leagues = p.leagues;
  if (p.sports != null) s.sports = p.sports;
  if (p.spoiler != null) s.spoiler = !!p.spoiler;
  P.state.mtime.sport = Date.now();
  P.save();
}

function isFavTeam(t) {
  if (!t.name) return false;
  const n = ' ' + C.norm(t.name + ' ' + (t.shortName || '')) + ' ';
  for (const f of prefs().teams) {
    const nf = C.norm(f);
    if (n.includes(nf)) return true;
    for (const k of COUNTRY) if (k[0] === nf && (n.includes(' ' + k[1] + ' ') || n.trim() === k[1])) return true;
  }
  return false;
}
function mine(g) {
  const p = prefs();
  if (g.sport !== FOOTBALL) return p.sports.includes(g.sport) || (!!g.home.name && (isFavTeam(g.home) || isFavTeam(g.away)));
  return isFavTeam(g.home) || isFavTeam(g.away) || p.leagues.includes(g.league);
}

// ---- turneringer ingen datakilde har: find kampene i jeres egne kanaler ----
let guideAt = 0;
const ugames = new Map(); // kampe fundet i denne brugers egne kanaler
const VS = /^(.+?)\s+(?:-|–|—|vs\.?|v)\s+(.+)$/;
function guide() {
  const now = Date.now();
  if (!P.catalog.loaded || now - guideAt < 5 * 60e3) return;
  guideAt = now;
  for (const gd of GUIDE) {
    for (const ch of P.catalog.channels) {
      const raw = ch.raw, cat = P.catalog.cats.get(ch.cat), low = raw.toLowerCase();
      if (gd[4] && !low.includes(gd[4]) && !(cat && cat.name.toLowerCase().includes(gd[4]))) continue;
      if (gd[3] === MOTOR && !low.includes('|') && !low.includes('@')) continue;
      if (!(gd[2].test(raw) || (cat && gd[2].test(cat.name)))) continue;
      const ev = P.evOf(ch);
      if (!ev || ev.start < now - 3 * 3600e3) continue;
      addGuideGame(gd, ev.title, ev.extra, ev.start, ev.start + 2 * 3600e3);
    }
    let wanted = 0;
    for (const ch of sportsPool()) {
      if (wanted++ < 60) P.epgWant(ch.id);
      for (const p of P.epgGet(ch.id)) {
        if (p.stop < now - 3600e3) continue;
        const lt = p.title.toLowerCase(), ld = (p.desc || '').toLowerCase();
        if (!gd[4] ? gd[3] === MOTOR : (!lt.includes(gd[4]) && !ld.includes(gd[4]))) continue;
        const inTitle = gd[2].test(p.title), inDesc = !!p.desc && gd[2].test(p.desc.replace(/\n/g, ' '));
        if (inTitle || inDesc) addGuideGame(gd, inTitle ? p.title : p.title.replace(/^(fodbold|live|direkte)\s*[:\-–]\s*/i, ''), null, p.start, p.stop);
      }
    }
  }
}
const hash = s => { let h = 0; for (const c of s) h = (h * 31 + c.codePointAt(0)) | 0; return (h >>> 0).toString(16); };
function addGuideGame(gd, title, extra, start, stop) {
  const t = title.replace(/^.*?(betano ?pokalen|oddset pokalen|sydbank ?pokalen|landspokal\w*|dbu pokal\w*|pokalfodbold|pokalen)\s*[:\-–|]*\s*/i, '')
    .replace(/^(live|direkte)\s*[:\-–]\s*/i, '').replace(/\s*[(\[].*$/, '').trim();
  const now = Date.now();
  const g = { sport: gd[3], league: gd[0], leagueName: gd[1], start, state: now >= start && now < stop ? 'in' : now >= stop ? 'post' : 'pre', detail: '', clock: '', home: team(), away: team() };
  if (gd[3] === MOTOR) {
    const full = C.nice(title.replace(/^\(?[a-z]{2,4}\s*\d*\)?\s*\|\s*/i, '').replace(/^(flo|stan|au)\s*\(?\w*\s*\d*\)?\s*[:|]?\s*/i, ''));
    g.title = full.slice(0, 60);
    g.sub = extra ? C.nice(extra).replace(/\s*20\d\d$/, '') : gd[1];
    g.id = 'm' + hash(C.norm(g.title + g.sub) + Math.floor(start / 600e3));
  } else {
    const m = VS.exec(t);
    if (!m) return; // kun rigtige kampe – ikke studie og optakter
    g.home.name = g.home.shortName = C.nice(m[1].trim());
    g.away.name = g.away.shortName = C.nice(m[2].trim());
    g.id = 'g' + hash(C.norm(t).replace(/\s*(vs\.?|v|–|-)\s*/g, '-').replace(/[^\p{L}0-9-]/gu, '') + Math.floor(start / 600e3));
  }
  if (!ugames.has(g.id + '|' + g.league)) ugames.set(g.id + '|' + g.league, g);
}

const all = () => [...games.values(), ...ugames.values()];
const find = key => games.get(key) || ugames.get(key);
function list(onlyMine) {
  const out = [], from = Date.now() - 24 * 3600e3, seen = new Set();
  for (const g of all()) {
    if (g.start < from || (onlyMine && !mine(g))) continue;
    const dk = g.title ? g.sport + g.title + g.sub + g.start : g.home.name + '-' + g.away.name + '@' + g.start;
    if (seen.has(dk)) continue;
    seen.add(dk);
    out.push(g);
  }
  const r = g => live(g) ? 0 : done(g) ? 2 : 1;
  return out.sort((a, b) => r(a) !== r(b) ? r(a) - r(b) : r(a) === 2 ? b.start - a.start : a.start - b.start);
}

function round(g) {
  if (g.title && g.sport !== F1) return [];
  return [...all()].filter(x => x.league === g.league && x.id !== g.id && Math.abs(x.start - g.start) < 3 * 86400e3).sort((a, b) => a.start - b.start);
}

// ---- Match: find og rangér de kanaler, der viser en kamp ----
const CERTAIN = 3, VERY_LIKELY = 2, LIKELY = 1;
const DROP = new Set(['fc', 'fc', 'afc', 'cf', 'sv', 'fodbold', 'football', 'club', 'ac', 'ssc', 'as', 'bk', 'if', 'fk', 'sc', 'vfb', 'vfl', 'tsg', 'rcd', 'cd', 'ud', 'real', 'united', 'city', 'håndbold', 'haandbold', 'handball', 'hb', 'hk', 'kif', 'tsv', 'hsg', 'sg', 'thw']);

function aliases(t) {
  const a = new Set();
  for (const n of [t.name, t.shortName]) {
    if (!n) continue;
    const x = C.norm(n).replace(/\./g, ' ').replace(/\s+/g, ' ').trim();
    a.add(x);
    const core = x.split(' ').filter(w => !DROP.has(w)).join(' ');
    if (core.length >= 4) a.add(core);
  }
  const n = C.norm(t.name);
  if (n.includes('københavn') || n.includes('kobenhavn')) { a.add('fck'); a.add('københavn'); }
  for (const k of COUNTRY) if (n === k[1] || n === k[1] + ' national team') a.add(k[0]);
  if (n.includes('barcelona')) { a.add('barca'); a.add('barça'); }
  if (n.includes('hamburg')) { a.add('hsv'); a.add('hamburger'); }
  if (n.includes('manchester united')) a.add('man utd');
  if (n.includes('manchester city')) a.add('man city');
  if (n.includes('tottenham')) a.add('spurs');
  if (n.includes('midtjylland')) a.add('fcm');
  if (n.includes('brøndby') || n.includes('brondby')) a.add('brøndby');
  return [...a].filter(s => s.length >= 3);
}
const clean = text => ' ' + C.norm(text).replace(/\./g, ' ').replace(/[^\p{L}0-9]+/gu, ' ') + ' ';
const hasAny = (cleaned, al) => al.some(a => cleaned.includes(' ' + a + ' '));

let normFor = null, normNames = [];
function names() {
  if (P.catalog.channels !== normFor) { normFor = P.catalog.channels; normNames = normFor.map(ch => clean(ch.raw || ch.name)); }
  return normNames;
}

function quality(raw) {
  const r = raw.toUpperCase();
  if (/(4K|UHD|3840|2160)/.test(r)) return '4K';
  if (/(FHD|1080)/.test(r)) return 'FHD';
  if (r.includes('RAW')) return 'RAW';
  if (/\bHD\b/.test(r) || raw.includes('ᴴᴰ')) return 'HD';
  if (/\bSD\b/.test(r)) return 'SD';
  return '';
}
const SPORTY = /\b(sport|sports|soccer|football|fodbold|eurosport|ppv|espn|dazn|premier|liga|champions|bundesliga|laliga|serie a|ligue|viaplay|tv3\+|tv 2 sport|canal\+|sky|bein|arena|f1|darts|tennis|golf|ufc|handball|håndbold|motorsport)\b/i;
const GENERIC = new Set(['grand', 'prix', 'open', 'championship', 'championships', 'masters', 'cup', 'tour', 'night', 'fight', 'event', 'round', 'series', 'the', 'and', 'world']);

function sportWords(s) {
  switch (s) {
    case F1: return C.synonyms('formel 1').concat(['formula one']);
    case DARTS: return C.synonyms('dart');
    case TENNIS: return C.synonyms('tennis');
    case GOLF: return C.synonyms('golf');
    case MMA: return ['ufc', 'mma'];
    case HANDBALL: return C.synonyms('håndbold');
    case MOTOR: return ['indycar', 'nascar', 'dtm', 'wec', 'superbike', 'motogp', 'motorsport', 'porsche', 'le mans', 'rally'];
    default: return [];
  }
}

function sportsPool() {
  const pool = P.favorites();
  for (const grp of P.shownGroups()) {
    const cat = P.catalog.cats.get(grp);
    for (const ch of P.inCategory(grp)) if ((cat && SPORTY.test(cat.name)) || SPORTY.test(ch.name)) pool.push(ch);
  }
  return pool;
}

function add(out, seen, ch, conf, why) {
  if (seen.has(ch.id)) return;
  seen.add(ch.id);
  const raw = ch.raw || ch.name;
  let p = '';
  const m = /\b([A-Z]{2}):\s*\S[^|]*$/.exec(raw);
  if (m) p = m[1];
  const k = P.catalog.cats.get(ch.cat);
  if (!p && k && k.prefix.length === 2) p = k.prefix;
  if (!p) { const q = C.prefix(raw); if (q.length === 2) p = q; }
  const s = { ch, confidence: conf, why, quality: quality(raw), country: p };
  s.score = conf * 1000 + (p === 'DK' ? 3 : p === 'UK' || p === 'EN' ? 2 : 1) * 100 + ({ '4K': 40, FHD: 30, RAW: 28, HD: 20, SD: 5 }[s.quality] || 10);
  out.push(s);
}

function eventStreams(g, fetchEpg) {
  const sw = sportWords(g.sport), tw = clean(g.title).trim().split(' ').filter(w => w.length >= 4 && !GENERIC.has(w) && !/^\d+$/.test(w));
  const out = [], seen = new Set(), nn = names(), chs = normFor;
  for (let i = 0; i < nn.length; i++) {
    let hits = 0;
    for (const w of tw) if (nn[i].includes(' ' + w + ' ')) hits++;
    if (hits >= 2 || (hits >= 1 && hasAny(nn[i], sw))) add(out, seen, chs[i], CERTAIN, 'Begivenheden i kanalnavnet');
  }
  const k0 = g.allDay ? g.start : g.start - 20 * 60e3, k1 = g.allDay ? g.start + 86400e3 : g.start + 30 * 60e3;
  let wanted = 0;
  for (const ch of sportsPool()) {
    if (seen.has(ch.id)) continue;
    if (fetchEpg && wanted++ < 60) P.epgWant(ch.id);
    for (const p of P.epgGet(ch.id)) {
      if (p.stop < k0 || p.start > k1) continue;
      if (p.clean == null) p.clean = clean(p.title + ' ' + (p.desc || ''));
      const sport = hasAny(p.clean, sw);
      const hits = tw.filter(w => p.clean.includes(' ' + w + ' ')).length;
      if (sport && hits > 0) { add(out, seen, ch, VERY_LIKELY, 'Står i programguiden'); break; }
      if (sport) { add(out, seen, ch, LIKELY, sportName(g.sport) + ' i programguiden'); break; }
    }
  }
  return out.sort((x, y) => y.score - x.score);
}

function teamStreams(g, fetchEpg) {
  const home = aliases(g.home), away = aliases(g.away), league = g.leagueName ? C.norm(g.leagueName) : '';
  const out = [], seen = new Set(), nn = names(), chs = normFor;
  for (let i = 0; i < nn.length; i++) if (hasAny(nn[i], home) && hasAny(nn[i], away)) add(out, seen, chs[i], CERTAIN, 'Begge hold i kanalnavnet');
  const k0 = g.start - 20 * 60e3, k1 = g.start + 30 * 60e3;
  let wanted = 0;
  for (const ch of sportsPool()) {
    if (seen.has(ch.id)) continue;
    if (fetchEpg && wanted++ < 60) P.epgWant(ch.id);
    for (const p of P.epgGet(ch.id)) {
      if (p.stop < k0 || p.start > k1) continue;
      if (p.clean == null) p.clean = clean(p.title + ' ' + (p.desc || ''));
      const h = hasAny(p.clean, home), a = hasAny(p.clean, away);
      if (h && a) { add(out, seen, ch, VERY_LIKELY, 'Kampen står i programguiden'); break; }
      if ((h || a) && league && p.clean.includes(' ' + league.split(' ')[0] + ' ')) { add(out, seen, ch, LIKELY, 'Holdet og ligaen i programguiden'); break; }
    }
  }
  return out.sort((x, y) => y.score - x.score);
}

const streamCache = new Map();
function streams(g, full) {
  const key = g.id + '|' + g.league + '|' + full + '|' + P.catalog.version;
  const hit = streamCache.get(key);
  if (hit && Date.now() - hit.at < 90e3) return hit.l;
  const near = live(g) || g.start - Date.now() < 3 * 3600e3;
  const l = g.title ? eventStreams(g, full && near) : teamStreams(g, full && near);
  streamCache.set(key, { at: Date.now(), l });
  return l;
}

// ---- kameravinkler (F1 og MotoGP) ----
const catName = ch => { const k = P.catalog.cats.get(ch.cat); return k ? k.name : ''; };
function family(ch) {
  if (!ch) return null;
  const r = ch.raw.toLowerCase(), k = catName(ch).toLowerCase();
  if (r.includes('motogp')) return 'motogp';
  if (/\b(f1|formula ?1|formel 1|formula one)\b/.test(r) || /\bformula 1\b/.test(k)) return 'f1';
  return null;
}
function q(raw) { return { FHD: 5, HD: 4, RAW: 3, '4K': 2, SD: 1 }[quality(raw)] || 3; }
function angles(fam) {
  const main = [], extra = [], onboard = [], bestMain = new Map(), drivers = new Map();
  const a = (label, sub, section, ch) => ({ label, sub, section, ch });
  for (const c of P.catalog.channels) {
    const r = c.raw, u = r.toUpperCase(), k = catName(c).toUpperCase();
    if (u.startsWith('#') || u.includes('NO EVENT')) continue;
    if (fam === 'f1') {
      let key = null;
      if (u.startsWith('SKY SPORTS F1')) key = 'Sky Sports F1';
      else if (u.startsWith('F1 TV MAIN ENGLISH')) key = 'F1 TV · engelsk';
      else if (u.startsWith('F1 TV MAIN FRANCE')) key = 'F1 TV · fransk';
      else if (u.startsWith('F1 TV GERMANY')) key = 'F1 TV · tysk';
      else if (u.startsWith('F1 TV SPANISH')) key = 'F1 TV · spansk';
      else if (/^CANAL\+ FORMULA ?1/.test(u)) key = 'Canal+ F1';
      if (key) { const b = bestMain.get(key); if (!b || q(r) > q(b.raw)) bestMain.set(key, c); continue; }
      if (u.startsWith('F1 TV PIT LANE')) { extra.push(a('Pitlane', 'F1 TV', 'Ekstra', c)); continue; }
      if (u.startsWith('F1 TV DRIVER TRACKER')) { extra.push(a('Driver tracker', 'Kort over banen', 'Ekstra', c)); continue; }
      if (u.startsWith('ONBOARD |') && u.includes('FORMEL 1')) { extra.push(a('Onboard (Viaplay)', C.nice((r.split('|')[1] || '').trim()), 'Ekstra', c)); continue; }
      if (k.includes('FORMULA 1 PPV') && u.split('|').length >= 3) {
        const p = r.split('|'), name = C.nice(p[0].trim()), surname = name.slice(name.lastIndexOf(' ') + 1).toUpperCase();
        drivers.set(surname, a(name, C.nice(p[1].trim()) + ' · ' + p[2].trim(), 'Onboard', c));
        continue;
      }
      if (u.startsWith('F1 CAR ')) { const s = u.slice(7).trim(); if (!drivers.has(s)) drivers.set(s, a(C.nice(s), 'Onboard', 'Onboard', c)); }
    } else if (fam === 'motogp' && k.includes('FORMULA 1 / MOTOGP') && u.startsWith('MOTOGP')) {
      const rest = C.nice(u.slice(6).trim().replace('BBOARD', 'BOARD')), ob = rest.toLowerCase().startsWith('on board');
      (ob ? onboard : extra).push(a(rest, 'MotoGP', ob ? 'Onboard' : 'Ekstra', c));
    } else if (fam === 'motogp' && /^(SKY SPORT MOTOGP|DAZN MOTOGP)/.test(u)) {
      const key = u.startsWith('SKY') ? 'Sky Sport MotoGP' : 'DAZN MotoGP', b = bestMain.get(key);
      if (!b || q(r) > q(b.raw)) bestMain.set(key, c);
    }
  }
  for (const [k, c] of bestMain) main.push(a(k, 'Hovedudsendelse', 'Hovedudsendelse', c));
  onboard.push(...[...drivers.values()].sort((x, y) => x.label.localeCompare(y.label)));
  return main.concat(extra, onboard);
}

// ---- Feed: sport der kører nu eller senere i dag ----
const SPORT_RE = /\b(sport|sports|soccer|football|fodbold|darts|eurosport|ppv|espn|dazn|premier|liga|champions|golf|tennis|handball|håndbold|f1|nfl|nhl|nba)\b/i;
function sportNow(max) {
  const now = Date.now(), until = new Date().setHours(23, 59, 0, 0), out = [], seen = new Set();
  let wanted = 0;
  for (const g of P.shownGroups()) {
    const k = P.catalog.cats.get(g), sportGroup = k && SPORT_RE.test(k.name);
    for (const ch of P.inCategory(g)) {
      if (P.separator(ch)) continue;
      const ev = P.evOf(ch);
      if (ev) {
        if (ev.start + 2 * 3600e3 < now || ev.start > until) continue;
        const name = C.nice(ev.title.replace(/^(betano pokalen|oddset pokalen)\s+/i, ''));
        if (seen.has(name.toLowerCase())) continue;
        seen.add(name.toLowerCase());
        out.push({ kind: 'channel', chId: ch.id, name, img: ch.logo, start: ev.start, stop: ev.start + 2 * 3600e3, sub: C.when(ev.start) + '  ·  ' + (k ? k.name : '') });
        continue;
      }
      if (!(sportGroup || SPORT_RE.test(ch.name))) continue;
      if (wanted++ < 40) P.epgWant(ch.id);
      for (const p of P.epgGet(ch.id)) {
        if (p.stop < now || p.start > until) continue;
        const key = (p.title + '@' + p.start).toLowerCase();
        if (seen.has(key)) continue;
        seen.add(key);
        out.push({ kind: 'program', chId: ch.id, name: p.title, img: ch.logo, start: p.start, stop: p.stop, sub: ch.name + '  ·  ' + C.hm(p.start) + '–' + C.hm(p.stop) });
        break;
      }
    }
  }
  return out.sort((a, b) => { const an = a.start <= now, bn = b.start <= now; return an !== bn ? (an ? -1 : 1) : a.start - b.start; }).slice(0, max);
}

return { prefs, setPrefs, mine, guide, list, round, find, streams, angles, family, sportNow, isFavTeam };
}

module.exports = { LEAGUES, GUIDE_LEAGUES, SPORTS, sportName, games, refresh, table, status, live, done, forUser };
