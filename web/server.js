'use strict';
// Vores TV – web. En lille server på hjemmenettet (Mac, Windows eller Linux) med samme funktioner som tv-appen.
// Start: node server.js   (port 8766, samme som tv'ernes telefon-side, så "Del login" fra et tv finder den)
process.env.TZ = process.env.TZ || 'Europe/Copenhagen';

const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const { Readable } = require('stream');
const C = require('./lib/core');
const X = require('./lib/provider');
const SH = require('./lib/sports');
const Users = require('./lib/users');

const PORT = +process.env.PORT || 8766;
const PUB = path.join(__dirname, 'public');
const NAME = process.env.VORESTV_NAME || ('Vores TV web (' + os.hostname().replace(/\.local$/, '') + ')');

// ---- hjælpere ----
const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.ttf': 'font/ttf', '.json': 'application/json', '.webmanifest': 'application/manifest+json' };
function send(res, code, body, type) {
  res.writeHead(code, { 'Content-Type': type || 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(typeof body === 'string' || Buffer.isBuffer(body) ? body : JSON.stringify(body));
}
const privateIp = ip => /^(::ffff:)?(127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|169\.254\.)/.test(ip) || ip === '::1' || /^f[cd]/i.test(ip);
async function body(req) {
  const chunks = [];
  for await (const c of req) { chunks.push(c); if (chunks.length > 200) break; }
  const s = Buffer.concat(chunks).toString('utf8');
  if ((req.headers['content-type'] || '').includes('json')) try { return JSON.parse(s || '{}'); } catch { return {}; }
  return Object.fromEntries(new URLSearchParams(s));
}

/** Alle sider og data for én bruger (eget login, egne favoritter). */
function makeApi(P, S, U) {
// Kanal til browseren: aldrig login eller udbyderens adresser
function chOut(ch, withEpg) {
  const k = P.catalog.cats.get(ch.cat);
  const o = { id: ch.id, num: ch.num, name: ch.name, logo: ch.logo, cat: ch.cat, group: k ? k.name : '', fav: P.state.favs.includes(ch.id) };
  const ev = P.evOf(ch);
  if (ev) { o.event = C.nice(ev.title.replace(/^(betano pokalen|oddset pokalen)\s+/i, '')); o.eventStart = ev.start; }
  if (S.family(ch)) o.angles = true;
  if (withEpg) {
    const now = Date.now(), l = P.epgGet(ch.id).filter(p => p.stop > now);
    o.epg = l.slice(0, withEpg).map(p => ({ title: p.title, desc: p.desc, start: p.start, stop: p.stop }));
    if (!P.epgGet(ch.id).length) P.epgWant(ch.id);
  }
  return o;
}
function gameOut(g, withCount) {
  const o = { key: g.id + '|' + g.league, sport: g.sport, sportName: S.sportName(g.sport), title: g.title || null, sub: g.sub || null, league: g.league, leagueName: g.leagueName, start: g.start, allDay: !!g.allDay, state: g.state, status: S.status(g), home: g.home, away: g.away, mine: S.mine(g) };
  if (withCount) {
    const near = S.live(g) || (g.start - Date.now() < 6 * 3600e3 && !S.done(g));
    o.streams = near ? S.streams(g, false).length : null;
  }
  return o;
}
const streamOut = s => ({ ch: chOut(s.ch), confidence: s.confidence, confidenceText: s.confidence === 3 ? 'Sikker' : s.confidence === 2 ? 'Meget sandsynlig' : 'Sandsynlig', quality: s.quality, country: s.country, why: s.why });

// ---- API ----
const routes = {
  'GET /api/cast': (req, res, q) => {
    const mp = String(q.get('path') || '');
    if (!/^\/play\/(live\/\d+\.(m3u8|ts)|(movie|series)\/[\w-]+\.\w+)$/.test(mp)) return send(res, 400, { error: 'Ugyldig' });
    const proto = U.remote || req.headers['x-forwarded-proto'] === 'https' ? 'https' : 'http';
    return { url: proto + '://' + req.headers.host + mp + '?ct=' + encodeURIComponent(castToken(U.name, mp)) };
  },
  'GET /api/me': () => ({ user: U.name, admin: !!U.admin, remote: !!U.remote, hasPassword: !!U.hasPassword, home: U.name === Users.HOME }),
  'POST /api/me/password': (req, res, q, b) => {
    if (U.hasPassword && !Users.check(U.name, String(b.old || ''))) return send(res, 400, { error: 'Den nuværende kode er forkert.' });
    const e = Users.setPassword(U.name, String(b.password || ''));
    return e ? send(res, 400, { error: e }) : { ok: true };
  },
  'GET /hello': () => ({ app: 'vorestv', name: NAME, in: P.ok(), web: true }),

  // Et tv i huset sender sit login ("Del login" på tv'ets telefon-side). Kun hvis vi ikke allerede er logget ind.
  'POST /receive': async (req, res, q, b) => {
    if (P.ok()) return send(res, 409, 'Allerede logget ind.', 'text/plain; charset=utf-8');
    const c = P.parseCred(b.server, b.user, b.pass);
    c.alts = P.parseAlts(b.alts);
    const err = await P.login(c);
    if (err) return send(res, 502, err, 'text/plain; charset=utf-8');
    return send(res, 200, 'Logget ind med login fra ' + (b.from || 'et andet tv') + '.', 'text/plain; charset=utf-8');
  },

  'GET /api/status': () => ({
    loggedIn: P.ok(), loaded: P.catalog.loaded, loading: P.catalog.loading, error: P.catalog.error, problem: P.health.problem, switched: P.health.switched,
    channels: P.catalog.channels.length, name: NAME, alts: P.ok() ? (P.cred().alts || []).length : 0,
  }),
  'POST /api/login': async (req, res, q, b) => {
    const c = P.parseCred(b.server, b.user, b.pass);
    c.alts = P.parseAlts(b.alts);
    const err = await P.login(c);
    return err ? send(res, 400, { error: err }) : { ok: true };
  },
  'POST /api/logout': () => { P.logout(); return { ok: true }; },
  'POST /api/alts': (req, res, q, b) => { const c = P.cred(); if (c) { c.alts = P.parseAlts(b.alts).filter(a => a !== c.server); P.login(c); } return { ok: true }; },

  // live
  'GET /api/live/groups': () => {
    const shown = new Set(P.shownGroups());
    return {
      prefixes: P.prefixes(), shown: P.shownGroups(),
      groups: [...P.catalog.cats.values()].filter(k => k.count > 0).map(k => ({ id: k.id, name: k.name, prefix: k.prefix || 'Andet', count: k.count, shown: shown.has(k.id) })),
    };
  },
  'POST /api/live/group': (req, res, q, b) => { P.toggleGroup(String(b.id)); return { ok: true }; },
  'GET /api/live/channels': (req, res, q) => {
    const cat = q.get('cat');
    let list = cat === 'fav' ? P.favorites() : cat === 'recent' ? P.state.history.map(id => P.catalog.byId.get(id)).filter(Boolean) : P.inCategory(cat);
    list = list.filter(ch => !P.separator(ch));
    return { channels: list.map(ch => chOut(ch, 3)), pending: P.epgPending() };
  },
  'GET /api/live/channel': (req, res, q) => { const ch = P.catalog.byId.get(+q.get('id')); return ch ? chOut(ch, 8) : send(res, 404, { error: 'Kanalen findes ikke.' }); },
  'POST /api/fav': (req, res, q, b) => { P.toggleFav(+b.id); return { ok: true, favs: P.state.favs }; },
  'POST /api/fav/move': (req, res, q, b) => { P.moveFav(+b.id, +b.delta); return { ok: true, favs: P.state.favs }; },
  'POST /api/seen': (req, res, q, b) => { P.seen(+b.id); return { ok: true }; },
  'GET /api/angles': (req, res, q) => { const fam = S.family(P.catalog.byId.get(+q.get('id'))); return { family: fam, angles: fam ? S.angles(fam).map(a => ({ label: a.label, sub: a.sub, section: a.section, ch: chOut(a.ch) })) : [] }; },

  // film og serier
  'GET /api/vod/groups': (req, res, q) => {
    const kind = q.get('kind'), m = P.vodCategories(kind), shown = P.vodShownGroups(kind);
    return { loading: !m.size, shown, groups: [...m.values()].map(k => ({ id: k.id, name: k.name, prefix: k.prefix, shown: shown.includes(k.id) })) };
  },
  'POST /api/vod/group': (req, res, q, b) => { P.vodToggleGroup(b.kind, String(b.id)); return { ok: true }; },
  'GET /api/vod/items': (req, res, q) => { const l = P.vodInCategory(q.get('kind'), q.get('cat')); return { loading: !l, items: (l || []).slice(0, +q.get('max') || 400) }; },
  'GET /api/vod/info': async (req, res, q) => {
    const kind = q.get('kind'), id = q.get('id');
    try {
      const v = await P.vodInfo(kind, id);
      const resume = {};
      for (const [k, r] of Object.entries(P.state.resume)) if (r.kind === kind && (r.id === id || r.seriesId === id)) resume[k] = r;
      const done = Object.keys(P.state.done).filter(k => k.startsWith(kind === P.MOVIE ? 'm:' : 'e:'));
      return { ...v, resume, done };
    } catch (e) { return send(res, 502, { error: P.friendly(e) }); }
  },
  'POST /api/resume': (req, res, q, b) => { P.saveResume(b); return { ok: true }; },
  'POST /api/sync': (req, res, q, b) => P.sync(b),
  'GET /api/continue': (req, res, q) => P.continueList(q.get('kind')),

  // søg
  'GET /api/search': (req, res, q) => {
    P.buildIndex(); P.warmEpg();
    const qq = C.query(q.get('q') || '');
    if (!qq.length) return { channels: [], programs: [], movies: [], series: [] };
    return {
      channels: P.searchChannels(qq, 40), programs: P.searchPrograms(qq, 40), movies: P.searchVod(P.MOVIE, qq, 60), series: P.searchVod(P.SERIES, qq, 60),
      progress: P.search.progress || (P.epgPending() ? 'Henter programguide …' : null),
    };
  },

  // sport
  'GET /api/sport/games': (req, res, q) => {
    refreshAll(); S.guide();
    const f = q.get('f') || 'mine';
    let l = S.list(f === 'mine');
    if (f === 'live') l = l.filter(S.live);
    else if (f !== 'mine' && f !== 'all') l = l.filter(g => g.sport === f);
    return { games: l.slice(0, 150).map(g => gameOut(g, true)), spoiler: S.prefs().spoiler, sports: S.SPORTS };
  },
  'GET /api/sport/game': (req, res, q) => {
    const g = S.find(q.get('key'));
    if (!g) return send(res, 404, { error: 'Kampen findes ikke længere.' });
    const table = g.sport === 'football' && /^[a-z]+\.\d$/.test(g.league) ? S.table(g.league) : [];
    return { game: gameOut(g), streams: S.streams(g, true).map(streamOut), round: S.round(g).map(x => gameOut(x)), table, pending: P.epgPending(), spoiler: S.prefs().spoiler };
  },
  'GET /api/sport/prefs': () => ({ ...S.prefs(), allLeagues: S.LEAGUES.concat(S.GUIDE_LEAGUES), allSports: S.SPORTS }),
  'POST /api/sport/prefs': (req, res, q, b) => { S.setPrefs(b); return { ok: true }; },

  // hjem
  'GET /api/home': () => {
    refreshAll(); S.guide();
    const mineGames = S.list(true).filter(g => !S.done(g) && g.start < Date.now() + 7 * 86400e3).slice(0, 16);
    let hero = null;
    for (const g of mineGames) {
      if (!S.live(g)) continue;
      const st = S.streams(g, true);
      if (st.length) { hero = { type: 'game', game: gameOut(g), stream: streamOut(st[0]) }; break; }
    }
    const recent = P.state.history.map(id => P.catalog.byId.get(id)).filter(Boolean);
    if (!hero && recent.length) hero = { type: 'channel', ch: chOut(recent[0], 2) };
    const favs = P.favorites();
    return {
      hero, games: mineGames.map(g => gameOut(g, true)),
      channels: (favs.length ? favs : recent).slice(0, 20).map(ch => chOut(ch, 1)), channelsTitle: favs.length ? 'Dine kanaler' : 'Senest sete',
      cont: P.continueList().slice(0, 20), sport: S.sportNow(12),
      problem: P.health.problem, switched: P.health.switched,
    };
  },
};
return routes;
}

// ---- afspilning gennem serveren (login forlader aldrig denne maskine) ----
// ---- signerede afspilningslinks til Chromecast/AirPlay (modtageren har ingen cookie) ----
const SECRET_FILE = path.join(process.env.VORESTV_DATA || path.join(__dirname, 'data'), 'cast.secret');
let SECRET; try { SECRET = fs.readFileSync(SECRET_FILE); } catch { SECRET = crypto.randomBytes(32); fs.mkdirSync(path.dirname(SECRET_FILE), { recursive: true }); fs.writeFileSync(SECRET_FILE, SECRET); }
const hmac = s => crypto.createHmac('sha256', SECRET).update(s).digest('base64url');
function castToken(user, mediaPath, hours = 12) {
  const body = Buffer.from(JSON.stringify({ u: user, p: mediaPath, e: Date.now() + hours * 3600e3 })).toString('base64url');
  return body + '.' + hmac(body);
}
function castCheck(t, p) {
  if (!t || !t.includes('.')) return null;
  const [body, sig] = t.split('.');
  const want = hmac(body);
  if (sig.length !== want.length || !crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(want))) return null;
  try { const o = JSON.parse(Buffer.from(body, 'base64url').toString()); return o.e > Date.now() && (p.startsWith('/seg/') || o.p === p) ? o : null; } catch { return null; }
}

const tokens = new Map(); // token -> {url, at}
function tok(url, owner) { const t = crypto.randomBytes(12).toString('base64url'); tokens.set(t, { url, owner, at: Date.now() }); return t; }
setInterval(() => { const old = Date.now() - 15 * 60e3; for (const [k, v] of tokens) if (v.at < old) tokens.delete(k); }, 60e3).unref();

function rewrite(text, base, owner, suffix = '') {
  const abs = u => '/seg/' + tok(new URL(u, base).href, owner) + suffix;
  return text.split(/\r?\n/).map(l => {
    if (!l) return l;
    if (l.startsWith('#')) return l.replace(/URI="([^"]+)"/g, (m, u) => 'URI="' + abs(u) + '"');
    return abs(l.trim());
  }).join('\n');
}

function upstreamFail(res, e, code) {
  const msg = code === 403 || code === 458 || code === 509
    ? 'Udbyderen afviste afspilningen (' + code + '). Det sker typisk, når jeres login allerede bruges på et andet tv. Stop det, og prøv igen.'
    : code === 404 ? 'Udbyderen har ikke kanalen eller filen lige nu (404).' : code ? 'Udbyderen svarede med fejl ' + code + '.' : X.friendly(e);
  if (!res.headersSent) send(res, 502, { error: msg, code: code || 0 });
  else res.destroy();
}

async function proxy(req, res, url, { playlist, type, owner, suffix } = {}) {
  const ac = new AbortController();
  res.on('close', () => ac.abort()); // stop hos udbyderen med det samme (de tæller samtidige forbindelser)
  try {
    const h = { 'User-Agent': X.UA };
    if (req.headers.range) h.Range = req.headers.range;
    const r = await fetch(url, { headers: h, redirect: 'follow', signal: ac.signal });
    if (!r.ok && r.status !== 206) { r.body?.cancel().catch(() => { }); return upstreamFail(res, null, r.status); }
    const ct = r.headers.get('content-type') || '';
    if (playlist || /mpegurl/i.test(ct) || /\.m3u8(\?|$)/.test(r.url)) {
      const t = await r.text();
      if (!t.startsWith('#EXTM3U')) return upstreamFail(res, null, 404);
      return send(res, 200, rewrite(t, r.url, owner, suffix), 'application/vnd.apple.mpegurl');
    }
    const out = { 'Content-Type': type || ct || 'application/octet-stream', 'Cache-Control': 'no-store', 'Accept-Ranges': 'bytes' };
    for (const k of ['content-length', 'content-range']) { const v = r.headers.get(k); if (v) out[k] = v; }
    res.writeHead(r.status, out);
    Readable.fromWeb(r.body).on('error', () => res.destroy()).pipe(res);
  } catch (e) { if (e.name !== 'AbortError') upstreamFail(res, e); }
}

const VTYPE = { mp4: 'video/mp4', m4v: 'video/mp4', mov: 'video/mp4', mkv: 'video/webm', webm: 'video/webm', avi: 'video/x-msvideo', ts: 'video/mp2t' };

async function play(req, res, p, P, owner, suffix = '') {
  if (!P.ok()) return send(res, 401, { error: 'Ikke logget ind.' });
  let m;
  if ((m = /^\/play\/live\/(\d+)\.(m3u8|ts)$/.exec(p))) return proxy(req, res, P.liveUrl(m[1], m[2] === 'm3u8'), { playlist: m[2] === 'm3u8', type: m[2] === 'ts' ? 'video/mp2t' : null, owner, suffix });
  if ((m = /^\/play\/(movie|series)\/([\w-]+)\.(\w+)$/.exec(p))) return proxy(req, res, P.vodUrl(m[1], m[2], m[3]), { type: VTYPE[m[3].toLowerCase()], owner });
  if ((m = /^\/seg\/([\w-]+)$/.exec(p))) { const t = tokens.get(m[1]); if (!t || t.owner !== owner) return send(res, 410, { error: 'Udløbet' }); return proxy(req, res, t.url, { owner, suffix }); }
  send(res, 404, { error: 'Findes ikke' });
}

// ---- filer ----
function serveFile(res, p) {
  if (p === '/' || !path.extname(p)) p = '/index.html';
  const f = path.join(PUB, path.normalize(p).replace(/^(\.\.[/\\])+/, ''));
  if (!f.startsWith(PUB)) return send(res, 403, 'Nej');
  fs.readFile(f, (e, b) => {
    if (e) return send(res, 404, 'Ikke fundet', 'text/plain');
    res.writeHead(200, { 'Content-Type': MIME[path.extname(f)] || 'application/octet-stream', 'Cache-Control': p === '/index.html' ? 'no-cache' : 'max-age=300' });
    res.end(b);
  });
}

// ---- brugere: husstanden på hjemmenettet + egne brugere (fx Thorbjørn) via den offentlige adresse ----
const ctxs = new Map(); // brugernavn -> {P, S, routes}
function ctxFor(u) {
  let c = ctxs.get(u.name);
  if (!c) {
    const P = X.create(Users.dir(u.name));
    const S = Object.assign({}, SH, SH.forUser(P));
    c = { P, S };
    ctxs.set(u.name, c);
    P.loadCatalog(false);
  }
  c.U = { name: u.name, admin: !!u.admin, remote: u.remote, hasPassword: !!u.hash };
  c.routes = makeApi(c.P, c.S, c.U);
  return c;
}
function refreshAll() {
  const codes = new Set();
  for (const c of ctxs.values()) for (const l of c.S.prefs().leagues) codes.add(l);
  SH.refresh(codes);
}

// Kommer forespørgslen udefra (via Cloudflare-tunnelen)? Så kræves login – altid.
const isRemote = req => !!(req.headers['cf-connecting-ip'] || req.headers['x-forwarded-for'] || req.headers['cf-ray']);
const clientIp = req => String(req.headers['cf-connecting-ip'] || req.socket.remoteAddress || '');
const PUBLIC_FILES = /^\/(index\.html|app\.css|app\.js|Onest\.ttf|icon\.svg|manifest\.webmanifest)?$/;

function cookie(req, name) {
  const m = new RegExp('(?:^|;\\s*)' + name + '=([^;]+)').exec(req.headers.cookie || '');
  return m ? decodeURIComponent(m[1]) : null;
}

const authRoutes = {
  'POST /api/auth/login': async (req, res, b) => {
    const ip = clientIp(req);
    if (!Users.allowAttempt(ip)) return send(res, 429, { error: 'For mange forsøg. Vent et kvarter, og prøv igen.' });
    const u = Users.check(String(b.name || ''), String(b.password || ''));
    if (!u) { await new Promise(r => setTimeout(r, 600)); return send(res, 401, { error: 'Forkert brugernavn eller kode.' }); }
    Users.resetAttempts(ip);
    const t = Users.newSession(u.name);
    res.writeHead(200, { 'Content-Type': 'application/json', 'Set-Cookie': 'vt=' + t + '; Path=/; HttpOnly; SameSite=Lax; Max-Age=' + 60 * 86400 + (isRemote(req) ? '; Secure' : ''), 'Cache-Control': 'no-store' });
    res.end('{"ok":true}');
  },
  'POST /api/auth/logout': async (req, res) => {
    Users.endSession(cookie(req, 'vt'));
    res.writeHead(200, { 'Content-Type': 'application/json', 'Set-Cookie': 'vt=; Path=/; Max-Age=0' });
    res.end('{"ok":true}');
  },
};

// Administration (kun husstandens admin)
function adminRoutes(U) {
  return {
    'GET /api/users': () => Users.list(),
    'POST /api/users': (req, res, q, b) => { const e = Users.add(String(b.name || ''), String(b.password || '')); return e ? send(res, 400, { error: e }) : { ok: true }; },
    'POST /api/users/password': (req, res, q, b) => { const e = Users.setPassword(String(b.name || ''), String(b.password || '')); return e ? send(res, 400, { error: e }) : { ok: true }; },
    'POST /api/users/delete': (req, res, q, b) => {
      const n = String(b.name || '');
      if (n === Users.HOME) return send(res, 400, { error: 'Husstandens bruger kan ikke slettes.' });
      const c = ctxs.get(n); if (c) { c.P.logout(); ctxs.delete(n); }
      Users.remove(n); return { ok: true };
    },
  };
}

const server = http.createServer(async (req, res) => {
  const remote = isRemote(req);
  if (!remote && !privateIp(req.socket.remoteAddress || '')) return send(res, 403, 'Kun hjemmenettet', 'text/plain');
  const u = new URL(req.url, 'http://x');
  const p = u.pathname;
  const key = req.method + ' ' + p;
  if (remote) res.setHeader('Strict-Transport-Security', 'max-age=31536000');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'same-origin');
  try {
    if (authRoutes[key]) {
      if (!(req.headers['content-type'] || '').includes('application/json')) return send(res, 415, { error: 'Forkert format' });
      return await authRoutes[key](req, res, await body(req));
    }
    // Chromecast/AirPlay: signeret link i stedet for cookie (kun afspilning)
    const ct = u.searchParams.get('ct');
    if (ct && (p.startsWith('/play/') || p.startsWith('/seg/'))) {
      res.setHeader('Access-Control-Allow-Origin', '*');
      res.setHeader('Access-Control-Allow-Headers', 'Range, Content-Type');
      res.setHeader('Access-Control-Expose-Headers', 'Content-Length, Content-Range');
      if (req.method === 'OPTIONS') { res.writeHead(204); return res.end(); }
      const o = castCheck(ct, p), cu = o && Users.get(o.u);
      if (!cu) return send(res, 403, { error: 'Linket er udløbet.' });
      return await play(req, res, p, ctxFor({ ...cu, remote }).P, cu.name, '?ct=' + encodeURIComponent(ct));
    }
    // hvem er det? En gyldig session vinder; ellers er man husstanden, hvis man er på hjemmenettet.
    let user = Users.session(cookie(req, 'vt'));
    if (!user && !remote) user = Users.get(Users.HOME);
    if (!user) {
      if (PUBLIC_FILES.test(p) && req.method === 'GET') return serveFile(res, p);
      return send(res, 401, { error: 'Log ind', login: true });
    }
    user = { ...user, remote };
    // tv'ernes "Del login" taler kun med husstanden på hjemmenettet
    if ((p === '/hello' || p === '/receive') && (remote || user.name !== Users.HOME)) return send(res, 404, 'Ikke fundet', 'text/plain');
    const c = ctxFor(user);
    if (p.startsWith('/play/') || p.startsWith('/seg/')) return await play(req, res, p, c.P, user.name);
    const r = (user.admin && adminRoutes(user)[key]) || c.routes[key];
    if (r) {
      // beskyt mod forfalskede formularer fra andre sider: API-POST skal være JSON (undtagen tv'ernes /receive)
      if (req.method === 'POST' && p !== '/receive' && !(req.headers['content-type'] || '').includes('application/json')) return send(res, 415, { error: 'Forkert format' });
      const b = req.method === 'POST' ? await body(req) : {};
      const out = await r(req, res, u.searchParams, b);
      if (out !== undefined && !res.headersSent) send(res, 200, out);
      return;
    }
    if (p.startsWith('/api/')) return send(res, 404, { error: 'Ukendt' });
    serveFile(res, p);
  } catch (e) {
    console.error(e);
    if (!res.headersSent) send(res, 500, { error: 'Noget gik galt.' });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  const ips = Object.values(os.networkInterfaces()).flat().filter(a => a && a.family === 'IPv4' && !a.internal).map(a => a.address);
  console.log('Vores TV web kører:');
  for (const ip of ips) console.log('  http://' + ip + ':' + PORT);
  ctxFor(Users.get(Users.HOME));
  for (const n of Users.names()) ctxFor(Users.get(n));
  refreshAll();
});

// baggrund: kanalliste, helbred og kampe jævnligt for alle brugere
setInterval(() => { for (const c of ctxs.values()) { c.P.loadCatalog(false); c.P.healthCheck(false); } }, 10 * 60e3).unref();
setInterval(() => { refreshAll(); for (const c of ctxs.values()) c.S.guide(); }, 5 * 60e3).unref();
setTimeout(() => { for (const c of ctxs.values()) c.P.healthCheck(true); }, 5000).unref();
