'use strict';
// Udbyderen (Xtream Codes): login, kanaler, programguide, film/serier og søgning – som tv-appen.
const fs = require('fs');
const path = require('path');
const C = require('./core');
const Logos = require('./logos');


const UA = 'Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36';
const MAX_AGE = 12 * 3600e3;

// ---- små filhjælpere ----
function readJson(file, def) { try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch { return def; } }
function writeJson(file, v) { const tmp = file + '.tmp'; fs.writeFileSync(tmp, JSON.stringify(v)); fs.renameSync(tmp, file); }
const fresh = (file, age) => { try { return Date.now() - fs.statSync(file).mtimeMs < age; } catch { return false; } };
const exists = f => fs.existsSync(f);

function parseCred(server, user, pass) {
  server = (server || '').trim();
  if (server.includes('username=') && server.includes('password=')) {
    const u = new URL(server.startsWith('http') ? server : 'http://' + server);
    return { server: u.protocol + '//' + u.host, user: u.searchParams.get('username') || '', pass: u.searchParams.get('password') || '', alts: [] };
  }
  if (server && !server.startsWith('http')) server = 'http://' + server;
  server = server.replace(/\/+$/, '').replace(/\/(player_api|get)\.php.*$/, '');
  return { server, user: (user || '').trim(), pass: (pass || '').trim(), alts: [] };
}

function parseAlts(text) {
  const out = [];
  for (const m of String(text || '').matchAll(/\bhttps?:\/\/[a-z0-9.-]+(:\d+)?/gi)) { const u = m[0].toLowerCase(); if (!out.includes(u)) out.push(u); }
  return out;
}

/** Reserve-adressen som den er, og med hovedadressens port hvis den mangler. */
function candidates(alt, main) {
  const out = [alt];
  try { const m = new URL(main), a = new URL(alt); if (!a.port && m.port) out.unshift(a.protocol + '//' + a.hostname + ':' + m.port); } catch { }
  return out;
}

// ---- HTTP ----
class HttpError extends Error { constructor(code) { super('HTTP ' + code); this.code = code; } }

async function get(url, timeout = 30000) {
  const r = await fetch(url, { headers: { 'User-Agent': UA, 'Accept-Language': 'da,en;q=0.8' }, redirect: 'follow', signal: AbortSignal.timeout(timeout) });
  if (!r.ok) { r.body?.cancel().catch(() => { }); throw new HttpError(r.status); }
  return r;
}
const text = async (url, t) => (await get(url, t)).text();
const json = async (url, t) => JSON.parse(await text(url, t));

/** Brugervenlig fejltekst uden serveradresser eller IP'er. */
function friendly(e) {
  if (e instanceof HttpError) return 'Udbyderen svarede med fejl ' + e.code + '.';
  const code = e?.cause?.code || e?.code;
  if (e?.name === 'TimeoutError' || code === 'UND_ERR_CONNECT_TIMEOUT' || code === 'ETIMEDOUT') return 'Udbyderen svarer ikke lige nu – prøv igen om lidt.';
  if (code === 'ENOTFOUND' || code === 'EAI_AGAIN') return 'Ingen forbindelse til internettet eller udbyderen.';
  if (code === 'ECONNREFUSED' || code === 'ECONNRESET') return 'Kan ikke få forbindelse til udbyderen lige nu.';
  return 'Noget gik galt med forbindelsen til udbyderen.';
}


/** Én udbyder-forbindelse pr. bruger: eget login, egne kanaler, favoritter og cache. */
function create(DATA) {
const CACHE = path.join(DATA, 'cache');
fs.mkdirSync(CACHE, { recursive: true });

// ---- husstandens indstillinger (deles af alle browsere, som på et tv) ----
const STATE_FILE = path.join(DATA, 'state.json');
const state = Object.assign({ favs: [], groups: null, history: [], vodGroups: {}, resume: {}, done: {}, sport: {}, mtime: {} }, readJson(STATE_FILE, {}));
// tidspunkt for seneste ændring pr. felt – bruges når tv'erne synkroniserer (nyeste vinder)
const touch = f => { state.mtime[f] = Date.now(); };
let saveTimer = null;
function save() { clearTimeout(saveTimer); saveTimer = setTimeout(() => writeJson(STATE_FILE, state), 300); }

// ---- login (kun på denne maskine; sendes aldrig til browseren) ----
const CRED_FILE = path.join(DATA, 'cred.json');
let cred = readJson(CRED_FILE, null);
const ok = () => !!(cred && cred.server && cred.user && cred.pass);

// ---- Xtream ----
const enc = encodeURIComponent;
function api(action, extra, c = cred) {
  let u = c.server + '/player_api.php?username=' + enc(c.user) + '&password=' + enc(c.pass);
  if (action) u += '&action=' + action;
  if (extra) u += extra;
  return u;
}

async function check(c) {
  try {
    const o = await json(api(null, null, c), 15000);
    const ui = o && o.user_info;
    if (!ui || +ui.auth !== 1) return 'Udbyderen afviste brugernavn eller kode.';
    if (ui.status && !/^active$/i.test(ui.status)) return 'Abonnementet står som "' + ui.status + '".';
    return null;
  } catch (e) { return friendly(e); }
}

/** Logger ind (fra web-siden eller et tv i huset). Prøver reserve-adresserne, hvis hovedadressen ikke svarer. */
async function login(c) {
  if (!(c.server && c.user && c.pass)) return 'Mangler oplysninger.';
  c.alts = (c.alts || []).filter(a => a !== c.server);
  let err = await check(c);
  if (err) {
    outer: for (const alt of [...c.alts]) for (const cand of candidates(alt, c.server)) {
      if (!(await check({ ...c, server: cand }))) { c.alts = c.alts.filter(a => a !== alt).concat([c.server]); c.server = cand; err = null; break outer; }
    }
  }
  if (err) return err;
  cred = c;
  writeJson(CRED_FILE, cred);
  loadCatalog(true);
  return null;
}

function logout() {
  cred = null;
  try { fs.unlinkSync(CRED_FILE); } catch { }
  for (const f of fs.readdirSync(CACHE)) try { fs.unlinkSync(path.join(CACHE, f)); } catch { }
  Object.assign(catalog, { channels: [], byId: new Map(), cats: new Map(), byCat: new Map(), loaded: false, error: null });
  vod.cats = {}; vod.items.clear(); search.movies = search.series = null; epg.clear();
}

// ---- abonnementet: forbindelser i brug, udløb, svartid (fra udbyderens user_info) ----
let acct = null, acctAt = 0;
async function account(force) {
  if (!ok()) return null;
  if (!force && acct && Date.now() - acctAt < 120e3) return acct;
  const t0 = Date.now();
  try {
    const o = await json(api(null, null), 15000);
    const ui = (o && o.user_info) || {};
    acct = { active: +ui.active_cons || 0, max: +ui.max_connections || 0, exp: (+ui.exp_date || 0) * 1000, status: ui.status || '', ms: Date.now() - t0, ok: true };
  } catch (e) { acct = { ok: false, error: friendly(e), ms: Date.now() - t0 }; }
  acctAt = Date.now();
  return acct;
}

/** Navn på en film/serie ud fra det, der allerede er hentet (til "hvem ser hvad"). */
function vodName(kind, id) {
  const idx = search[kind === MOVIE ? 'movies' : 'series'];
  if (idx) for (const e of idx) if (e[0] === id) return e[1];
  for (const [k, l] of vod.items) if (k.startsWith(kind + '|')) for (const it of l) if (it.id === id) return it.name;
  for (const v of vod.info.values()) if (v.v.kind === kind && v.v.id === id) return v.v.name;
  return kind === MOVIE ? 'En film' : 'Et afsnit';
}

// ---- helbred: skift til en reserve-adresse, hvis hovedadressen holder op med at svare ----
const health = { problem: null, switched: null, at: 0, running: false };
async function healthCheck(force) {
  if (!ok() || health.running || (!force && Date.now() - health.at < 55000)) return;
  health.running = true;
  try {
    let p = await check(cred);
    if (p) {
      outer: for (const alt of [...(cred.alts || [])]) for (const cand of candidates(alt, cred.server)) {
        if (!(await check({ ...cred, server: cand }))) {
          const old = cred.server;
          cred.alts = cred.alts.filter(a => a !== alt);
          if (!cred.alts.includes(old)) cred.alts.push(old);
          cred.server = cand;
          writeJson(CRED_FILE, cred);
          health.switched = 'Skiftede til udbyderens reserve-adresse, fordi hovedadressen ikke svarede.';
          p = null;
          loadCatalog(true);
          break outer;
        }
      }
    }
    const before = health.problem;
    health.problem = p;
    if (before && !p) loadCatalog(true);
  } finally { health.running = false; health.at = Date.now(); }
}

// ---- kanaler ----
const catalog = { channels: [], byId: new Map(), cats: new Map(), byCat: new Map(), loaded: false, error: null, loading: false, version: 0 };

async function download(url, file, timeout = 180000) {
  const t = await text(url, timeout);
  JSON.parse(t); // kun gyldige svar gemmes
  fs.writeFileSync(file + '.tmp', t);
  fs.renameSync(file + '.tmp', file);
}

function parseCatalog(fc, fs_) {
  const cats = new Map();
  for (const o of readJson(fc, []) || []) {
    const rn = String(o.category_name || '');
    cats.set(String(o.category_id), { id: String(o.category_id), name: C.group(rn), prefix: C.prefix(rn), count: 0 });
  }
  const channels = [], byId = new Map(), byCat = new Map();
  let i = 0;
  for (const o of readJson(fs_, []) || []) {
    i++;
    const raw = String(o.name || '').trim();
    const ch = { id: +o.stream_id || 0, num: +o.num || i, raw, name: C.channel(raw), logo: o.stream_icon || '', cat: String(o.category_id ?? ''), epgId: o.epg_channel_id || '', archive: +o.tv_archive || 0 };
    const k0 = cats.get(ch.cat);
    ch.logo = Logos.find(ch.name, [C.prefix(raw), k0 && k0.prefix].filter(Boolean)) || ch.logo; // rigtige logoer i stedet for udbyderens klemte
    channels.push(ch);
    byId.set(ch.id, ch);
    if (!byCat.has(ch.cat)) byCat.set(ch.cat, []);
    byCat.get(ch.cat).push(ch);
    const k = cats.get(ch.cat);
    if (k) k.count++;
  }
  Object.assign(catalog, { channels, byId, cats, byCat, loaded: channels.length > 0, version: catalog.version + 1 });
}

async function loadCatalog(force) {
  if (!ok() || catalog.loading) return;
  catalog.loading = true;
  const fc = path.join(CACHE, 'live_categories.json'), fs_ = path.join(CACHE, 'live_streams.json');
  try {
    const isFresh = !force && fresh(fs_, MAX_AGE);
    if (!catalog.loaded && exists(fs_)) parseCatalog(fc, fs_);
    if (isFresh) return;
    await download(api('get_live_categories'), fc);
    await download(api('get_live_streams'), fs_);
    parseCatalog(fc, fs_);
    catalog.error = null;
  } catch (e) {
    catalog.error = friendly(e);
  } finally { catalog.loading = false; }
}

const inCategory = id => catalog.byCat.get(id) || [];
const separator = ch => /^[#=*~\-_ ]{3,}/.test(ch.name);

function shownGroups() {
  if (!state.groups) return [...catalog.cats.values()].filter(k => k.count > 0 && k.prefix === 'DK').map(k => k.id);
  return state.groups.filter(id => catalog.cats.has(id));
}
function toggleGroup(id) {
  const g = shownGroups();
  state.groups = g.includes(id) ? g.filter(x => x !== id) : g.concat([id]);
  touch('groups'); save();
}
function favorites() { return state.favs.map(id => catalog.byId.get(id)).filter(Boolean); }
function toggleFav(id) { state.favs = state.favs.includes(id) ? state.favs.filter(x => x !== id) : state.favs.concat([id]); touch('favs'); save(); }
function moveFav(id, d) {
  const f = state.favs, i = f.indexOf(id), j = i + d;
  if (i < 0 || j < 0 || j >= f.length) return;
  f.splice(i, 1); f.splice(j, 0, id); touch('favs'); save();
}
function seen(id) { state.history = [id].concat(state.history.filter(x => x !== id)).slice(0, 20); touch('history'); save(); }

/** Landepræfikser, de største først og DK øverst. */
function prefixes() {
  const n = new Map();
  for (const k of catalog.cats.values()) if (k.count > 0) { const p = k.prefix || 'Andet'; n.set(p, (n.get(p) || 0) + 1); }
  return [...n.keys()].sort((a, b) => a === 'DK' ? -1 : b === 'DK' ? 1 : n.get(b) - n.get(a));
}

// ---- programguide (kort EPG pr. kanal, hentes efter behov – skånsomt over for udbyderen) ----
// Gemmes på disken, så en genstart ikke henter alt forfra; højst 2 ad gangen med pause imellem,
// og helt stop i 10 minutter, hvis udbyderen holder op med at svare (ellers kan de blokere jeres adresse).
const EPG_FILE = path.join(CACHE, 'epg.json');
const EPG_AGE = 3 * 3600e3;
const epg = new Map(Object.entries(readJson(EPG_FILE, {})).filter(([, v]) => v && Date.now() - v.at < EPG_AGE).map(([k, v]) => [+k, v]));
setInterval(() => { try { writeJson(EPG_FILE, Object.fromEntries(epg)); } catch { } }, 5 * 60e3).unref();
const epgQueue = [], epgQueued = new Set();
let epgRunning = 0, epgFails = 0, epgPauseUntil = 0, epgTimer = null;

function epgGet(id) { const e = epg.get(id); return e ? e.progs : []; }
function epgWant(id) {
  const e = epg.get(id);
  if ((e && Date.now() - e.at < 45 * 60e3) || epgQueued.has(id) || !ok()) return;
  if (epgQueue.length > 400) return; // aldrig en kæmpe kø
  epgQueued.add(id); epgQueue.push(id); pumpEpg();
}
function pumpEpg() {
  if (epgTimer) return;
  const wait = epgPauseUntil - Date.now();
  if (wait > 0) { epgTimer = setTimeout(() => { epgTimer = null; pumpEpg(); }, wait); return; }
  if (epgRunning >= 2 || !epgQueue.length) return;
  const id = epgQueue.shift();
  epgRunning++;
  shortEpg(id, 24).then(p => { epg.set(id, { at: Date.now(), progs: p }); epgFails = 0; })
    .catch(() => {
      epg.set(id, { at: Date.now() - 30 * 60e3, progs: epgGet(id) }); // prøv igen om et kvarter
      if (++epgFails >= 3) { epgPauseUntil = Date.now() + 10 * 60e3; epgFails = 0; }
    })
    .finally(() => { epgRunning--; epgQueued.delete(id); epgTimer = setTimeout(() => { epgTimer = null; pumpEpg(); }, 350); });
  epgTimer = setTimeout(() => { epgTimer = null; pumpEpg(); }, 350);
}
const epgPending = () => epgQueue.length + epgRunning;

function local(s, fallback) {
  const m = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})/.exec(s || '');
  return m ? new Date(+m[1], m[2] - 1, +m[3], +m[4], +m[5], +m[6]).getTime() : fallback;
}

async function shortEpg(id, limit) {
  const o = await json(api('get_short_epg', '&stream_id=' + id + '&limit=' + limit), 20000);
  const out = [];
  for (const e of (o && o.epg_listings) || []) {
    // Udbyderens tidsstempler ligger en time forkert; tekst-tiderne er dansk tid.
    const p = { title: C.b64(e.title), desc: C.b64(e.description), start: local(e.start, (+e.start_timestamp || 0) * 1000), stop: local(e.end, (+e.stop_timestamp || 0) * 1000) };
    if (p.start > 0 && p.stop > p.start) out.push(p);
  }
  out.sort((a, b) => a.start - b.start);
  const clean = [];
  for (const p of out) {
    const last = clean[clean.length - 1];
    if (last && p.start < last.stop) {
      if (p.title === last.title || p.stop <= last.stop) continue;
      p.start = last.stop;
    }
    if (p.stop > p.start) clean.push(p);
  }
  return clean;
}

// ---- film og serier ----
const MOVIE = 'movie', SERIES = 'series';
const vod = { cats: {}, items: new Map(), loading: new Set(), info: new Map() };

async function cached(file, fetcher) {
  const f = path.join(CACHE, file);
  if (fresh(f, MAX_AGE)) return readJson(f, null);
  try { const v = await fetcher(); writeJson(f, v); return v; }
  catch (e) { if (exists(f)) return readJson(f, null); throw e; }
}

function vodCategories(kind) {
  if (vod.cats[kind]) return vod.cats[kind];
  const key = 'cats|' + kind;
  if (ok() && !vod.loading.has(key)) {
    vod.loading.add(key);
    cached(kind + '_categories.json', () => json(api(kind === MOVIE ? 'get_vod_categories' : 'get_series_categories'), 60000))
      .then(a => {
        const m = new Map();
        for (const o of a || []) {
          const rn = String(o.category_name || '');
          let pre = C.prefix(rn);
          const name = C.group(rn);
          if (!pre) { const w = name.split(' ')[0].replace(/[^\p{L}0-9+]/gu, ''); pre = w ? w[0].toUpperCase() + w.slice(1).toLowerCase() : 'Andet'; }
          m.set(String(o.category_id), { id: String(o.category_id), name, prefix: pre, raw: rn });
        }
        vod.cats[kind] = m;
      }).catch(() => { }).finally(() => vod.loading.delete(key));
  }
  return new Map();
}

function toItem(kind, o, cat) {
  const rel = String(o.releaseDate || o.release_date || o.year || '');
  const t = C.cleanTitle(String(o.name || ''), rel.length >= 4 ? rel.slice(0, 4) : '');
  return { kind, id: String(o.stream_id ?? o.series_id), name: t.name, year: t.year, poster: o.stream_icon || o.cover || '', cat: String(cat ?? o.category_id ?? ''), ext: o.container_extension || '', rating: String(o.rating ?? ''), added: +(o.added || o.last_modified) || 0 };
}

/** Titler i en kategori; null mens de hentes. */
function vodInCategory(kind, cat) {
  const key = kind + '|' + cat;
  if (vod.items.has(key)) return vod.items.get(key);
  if (ok() && !vod.loading.has(key)) {
    vod.loading.add(key);
    cached(kind + '_' + cat.replace(/[^0-9A-Za-z]/g, '_') + '.json', () => json(api(kind === MOVIE ? 'get_vod_streams' : 'get_series', '&category_id=' + enc(cat)), 120000))
      .then(a => vod.items.set(key, (a || []).filter(o => (o.stream_id ?? o.series_id) != null).map(o => toItem(kind, o, cat)).sort((x, y) => y.added - x.added)))
      .catch(() => { }).finally(() => vod.loading.delete(key));
  }
  return null;
}

function rank(k) {
  const r = (k.raw || k.name).toUpperCase();
  return r.includes('DANSK') || r.includes('DANISH') || k.prefix === 'DK' ? 0 : r.includes('NORDIC') || r.includes('NORDISK') || r.includes('SCANDI') ? 1 : 2;
}
function vodShownGroups(kind) {
  const m = vodCategories(kind);
  let out;
  if (!state.vodGroups[kind]) {
    out = [];
    for (const k of m.values()) {
      const r = (k.raw || k.name).toUpperCase();
      const dk = k.prefix === 'DK' || (/\b(DANSK|DANSKE|DANISH|NORDIC|SCANDINAVIAN|SCANDI|NORDISK)\b/.test(r) && !/\b(MUSIC|SPORT|BECK)\b/.test(r));
      const top = /^(NETFLIX (MOVIES|SERIES)|TOP (MOVIES|SERIES) BLURAY.*)$/.test(r);
      if (dk || top) out.push(k.id);
    }
  } else out = state.vodGroups[kind].filter(id => m.has(id));
  return out.sort((a, b) => rank(m.get(a)) - rank(m.get(b)));
}
function vodToggleGroup(kind, id) {
  const g = vodShownGroups(kind);
  state.vodGroups[kind] = g.includes(id) ? g.filter(x => x !== id) : g.concat([id]);
  touch('vodGroups'); save();
}

function parseDur(s) { const p = String(s || '').split(':'); return p.length === 3 ? (+p[0]) * 3600 + (+p[1]) * 60 + (+p[2]) || 0 : 0; }
const first = (o, ...keys) => { for (const k of keys) { const v = o && o[k]; if (v != null && v !== '' && v !== 'null') return String(v).trim(); } return ''; };

async function vodInfo(kind, id) {
  const key = kind + '|' + id;
  const hit = vod.info.get(key);
  if (hit && Date.now() - hit.at < 3600e3) return hit.v;
  const o = await json(api(kind === MOVIE ? 'get_vod_info' : 'get_series_info', (kind === MOVIE ? '&vod_id=' : '&series_id=') + enc(id)), 30000);
  const info = (o && o.info) || {};
  const bd = Array.isArray(info.backdrop_path) ? info.backdrop_path.find(Boolean) : info.backdrop_path;
  const t = C.cleanTitle(first(info, 'name', 'title') || first(o.movie_data, 'name'), String(first(info, 'releasedate', 'releaseDate', 'release_date', 'year')).slice(0, 4));
  let secs = parseDur(info.duration) || +info.duration_secs || 0;
  if (secs > 0 && secs < 400) secs *= 60;
  const v = {
    kind, id, name: t.name, year: t.year, plot: first(info, 'plot', 'description'), genre: first(info, 'genre'), rating: first(info, 'rating'),
    cast: first(info, 'cast', 'actors'), director: first(info, 'director'), poster: first(info, 'cover_big', 'movie_image', 'cover'), backdrop: bd || '',
    durMs: secs * 1000, ext: (o.movie_data && o.movie_data.container_extension) || '', trailer: first(info, 'youtube_trailer'),
  };
  if (kind === SERIES) {
    const seasons = {};
    const add = (arr, s) => {
      const l = (arr || []).filter(Boolean).map((j, i) => {
        const inf = j.info || {};
        let d = parseDur(inf.duration) || +inf.duration_secs || 0;
        if (d > 0 && d < 400) d *= 60;
        return { id: String(j.id), ext: j.container_extension || '', season: +j.season || s, num: +j.episode_num || i + 1, title: cleanEp(String(j.title || ''), t.name, +j.season || s, +j.episode_num || i + 1), plot: inf.plot || '', img: inf.movie_image || '', durMs: d * 1000 };
      }).sort((a, b) => a.num - b.num);
      if (l.length) seasons[s] = l;
    };
    if (Array.isArray(o.episodes)) o.episodes.forEach((a, i) => add(a, i + 1));
    else for (const [k, a] of Object.entries(o.episodes || {})) add(a, +k || Object.keys(seasons).length + 1);
    v.seasons = seasons;
  }
  vod.info.set(key, { at: Date.now(), v });
  return v;
}

/** "DK - Matador - S01E02 - Fødselsdagen" → "Fødselsdagen" (eller "Afsnit 2"). */
function cleanEp(title, series, s, n) {
  let t = C.channel(title);
  const sx = /S\d{1,2}\s*E\d{1,3}/i.exec(t);
  if (sx) t = t.slice(sx.index + sx[0].length);
  else if (series) { const i = C.norm(t).indexOf(C.norm(series)); if (i >= 0) t = t.slice(i + series.length); }
  t = t.replace(/^[\s\-–:|.]+/, '').trim();
  return t || 'Afsnit ' + n;
}

// ---- fortsæt hvor I slap ----
function saveResume(r) {
  const done = r.dur > 0 && r.pos > r.dur - Math.min(r.dur * 0.07, 8 * 60e3);
  if (done) { delete state.resume[r.key]; state.done[r.key] = 1; (state.doneAt || (state.doneAt = {}))[r.key] = Date.now(); }
  else state.resume[r.key] = { ...r, when: Date.now() };
  save();
}
function continueList(kind) {
  const all = Object.values(state.resume).filter(r => r.pos > 60e3).sort((a, b) => b.when - a.when);
  const seenSeries = new Set(), out = [];
  for (const r of all) {
    if (kind && r.kind !== kind) continue;
    if (r.kind === SERIES && seenSeries.has(r.seriesId)) continue;
    if (r.kind === SERIES) seenSeries.add(r.seriesId);
    out.push(r);
  }
  return out;
}

// ---- synkronisering med tv'erne i huset (felt for felt: nyeste ændring vinder) ----
const SYNC_FIELDS = ['favs', 'groups', 'history', 'vodGroups', 'sport'];
function sync(inc) {
  const im = (inc && inc.mtime) || {}, is = (inc && inc.state) || {};
  for (const f of SYNC_FIELDS) {
    if (!(f in is) || !((im[f] || 0) > (state.mtime[f] || 0))) continue;
    state[f] = is[f];
    state.mtime[f] = im[f];
  }
  state.doneAt = state.doneAt || {};
  for (const k of Object.keys(is.done || {})) if (!state.done[k]) { state.done[k] = 1; state.doneAt[k] = Date.now(); delete state.resume[k]; }
  for (const [k, r] of Object.entries(is.resume || {})) {
    if (state.done[k] && !((r.when || 0) > (state.doneAt[k] || 0))) continue; // set færdig – medmindre den er startet forfra siden
    if (!state.resume[k] || (r.when || 0) > (state.resume[k].when || 0)) state.resume[k] = r;
  }
  save();
  const out = {};
  for (const f of SYNC_FIELDS) out[f] = state[f];
  return { state: { ...out, resume: state.resume, done: state.done }, mtime: state.mtime, now: Date.now() };
}

// ---- søgning på tværs ----
const search = { movies: null, series: null, indexing: false, progress: null };

async function fetchIndex(url, timeout) {
  return ((await json(url, timeout)) || []).filter(o => (o.stream_id ?? o.series_id) != null && o.name)
    .map(o => { const it = toItem('', o); return [it.id, it.name, it.poster, it.cat, it.year, it.ext, C.norm(it.name)]; });
}

async function buildIndex() {
  if (search.indexing || (search.movies && search.series) || !ok()) return;
  search.indexing = true;
  try {
    for (const kind of [SERIES, MOVIE]) {
      if (search[kind === MOVIE ? 'movies' : 'series']) continue;
      const word = kind === MOVIE ? 'film' : 'serier';
      const f = path.join(CACHE, 'index_' + kind + '.json');
      let l = fresh(f, 24 * 3600e3) ? readJson(f, null) : null;
      if (!l) {
        search.progress = 'Indlæser ' + word + ' til søgning …';
        try { l = await fetchIndex(api(kind === MOVIE ? 'get_vod_streams' : 'get_series'), 240000); if (!l.length) throw new Error('tomt'); }
        catch { // plan B: gruppe for gruppe
          l = [];
          const cats = await json(api(kind === MOVIE ? 'get_vod_categories' : 'get_series_categories'), 60000);
          let done = 0;
          const work = [...cats];
          await Promise.all([0, 1, 2].map(async () => {
            while (work.length) {
              const k = work.shift();
              try { l.push(...await fetchIndex(api(kind === MOVIE ? 'get_vod_streams' : 'get_series', '&category_id=' + enc(k.category_id)), 60000)); } catch { }
              search.progress = 'Indlæser ' + word + ' til søgning … ' + Math.round(100 * ++done / cats.length) + ' %';
              if (done % 10 === 0) search[kind === MOVIE ? 'movies' : 'series'] = l.slice();
            }
          }));
        }
        if (l.length) writeJson(f, l);
      }
      search[kind === MOVIE ? 'movies' : 'series'] = l;
    }
  } catch { } finally { search.indexing = false; search.progress = null; }
}

const evOf = ch => {
  let p = C.prefix(ch.raw);
  if (!p) { const m = /\b([A-Z]{2}):\s*\S/.exec(ch.raw); if (m) p = m[1]; }
  if (!p) { const k = catalog.cats.get(ch.cat); if (k) p = k.prefix; }
  return C.parseEv(ch.raw, p);
};
const chNorm = new WeakMap();
const normName = ch => { let n = chNorm.get(ch); if (n == null) chNorm.set(ch, n = C.norm(ch.name)); return n; };

function searchChannels(q, max) {
  const out = [], later = [], shown = new Set(shownGroups()), fav = new Set(state.favs);
  for (const ch of catalog.channels) {
    if (/^[#=*~\-_ ]{3,}/.test(ch.name)) continue;
    const k = catalog.cats.get(ch.cat);
    if (!(C.matchNorm(normName(ch), q) || (k && q.length > 1 && C.matchNorm(C.norm(k.name), q)))) continue;
    const h = { kind: 'channel', chId: ch.id, name: ch.name, img: ch.logo, sub: k ? k.name : '' };
    const ev = evOf(ch);
    if (ev) { h.name = C.nice(ev.title); h.start = ev.start; h.sub = C.when(ev.start) + '  ·  ' + h.sub; }
    (shown.has(ch.cat) || fav.has(ch.id) ? out : later).push(h);
    if (out.length >= max) break;
  }
  for (const h of later) { if (out.length >= max) break; out.push(h); }
  return out;
}

function epgChannels() {
  const l = favorites(), ids = new Set(l.map(c => c.id));
  for (const g of shownGroups()) for (const ch of inCategory(g)) if (!ids.has(ch.id)) { ids.add(ch.id); l.push(ch); }
  return l;
}

function searchPrograms(q, max) {
  const out = [], now = Date.now();
  for (const ch of epgChannels()) for (const p of epgGet(ch.id)) {
    if (p.stop < now || !C.matchNorm(C.norm(p.title), q)) continue;
    out.push({ kind: 'program', chId: ch.id, name: p.title, img: ch.logo, start: p.start, stop: p.stop, sub: ch.name });
  }
  return out.sort((a, b) => a.start - b.start).slice(0, max);
}

function warmEpg() { for (const ch of epgChannels().slice(0, 250)) if (!epg.has(ch.id)) epgWant(ch.id); }

function searchVod(kind, q, max) {
  const src = [...(search[kind === MOVIE ? 'movies' : 'series'] || [])];
  const ids = new Set(src.map(e => e[0]));
  for (const [key, list] of vod.items) {
    if (!key.startsWith(kind + '|')) continue;
    for (const it of list) if (!ids.has(it.id)) { ids.add(it.id); src.push([it.id, it.name, it.poster, it.cat, it.year, it.ext, C.norm(it.name)]); }
  }
  const firstL = [], rest = [], shown = new Set(vodShownGroups(kind));
  for (const e of src) {
    if (!C.matchNorm(e[6], q)) continue;
    const h = { kind, id: e[0], name: e[1], img: e[2], cat: e[3], sub: e[4], ext: e[5] };
    (shown.has(e[3]) ? firstL : rest).push(h);
    if (firstL.length >= max) break;
  }
  for (const h of rest) { if (firstL.length >= max) break; firstL.push(h); }
  return firstL;
}

// ---- afspilnings-adresser (kun på serveren) ----
const liveUrl = (id, hls) => cred.server + '/live/' + cred.user + '/' + cred.pass + '/' + id + (hls ? '.m3u8' : '.ts');
const vodUrl = (kind, id, ext) => cred.server + '/' + (kind === MOVIE ? 'movie' : 'series') + '/' + cred.user + '/' + cred.pass + '/' + id + '.' + (ext || 'mp4');

return {
  DATA, state, save, ok, login, logout, health, healthCheck, account, vodName, 
  catalog, loadCatalog, inCategory, separator, shownGroups, toggleGroup, favorites, toggleFav, moveFav, seen, prefixes,
  epgGet, epgWant, epgPending, MOVIE, SERIES, vod, vodCategories, vodInCategory, vodShownGroups, vodToggleGroup, vodInfo,
  saveResume, continueList, sync, search, buildIndex, evOf, searchChannels, searchPrograms, searchVod, warmEpg, epgChannels,
  liveUrl, vodUrl, json, text, cred: () => cred, UA, friendly, parseCred, parseAlts,
};
}

module.exports = { create, UA, friendly, HttpError, get, json, text, parseCred, parseAlts };
