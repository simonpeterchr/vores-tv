'use strict';
// Drift: hvem ser hvad (forbindelser), status/helbred, beskeder til telefonen (ntfy) og natlig backup af indstillinger.
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFile } = require('child_process');

const DATA = process.env.VORESTV_DATA || path.join(__dirname, '..', 'data');
const CFG_FILE = path.join(DATA, 'ops.json');
let cfg = {};
try { cfg = JSON.parse(fs.readFileSync(CFG_FILE, 'utf8')); } catch { }
const saveCfg = () => fs.writeFileSync(CFG_FILE, JSON.stringify(cfg, null, 1));

// ---- hvem ser hvad ----
const viewers = new Map(); // nøgle -> {user, device, title, kind, via, at}
function device(ua = '') {
  if (/CrKey/i.test(ua)) return 'Chromecast';
  if (/AppleTV|AirPlay|AppleCoreMedia/i.test(ua)) return 'Apple TV / AirPlay';
  if (/iPhone/i.test(ua)) return 'iPhone';
  if (/iPad/i.test(ua)) return 'iPad';
  if (/Android/i.test(ua)) return 'Android';
  if (/Macintosh/i.test(ua)) return 'Mac';
  if (/Windows/i.test(ua)) return 'Windows';
  return 'Browser';
}
/** Kaldes ved hver afspilnings-forespørgsel (HLS henter playlisten hvert par sekunder). */
const keyOf = (user, ip, ua) => user + '|' + ip + '|' + device(ua);
function seenStream(user, ip, ua, title, kind, remote) {
  const key = keyOf(user, ip, ua);
  if (kicked.has(key)) return;
  viewers.set(key, { key, user, device: device(ua), title, kind, via: remote ? 'udefra' : 'hjemme', at: Date.now() });
}

// ---- kun én stream ad gangen: den der starter sidst, overtager ----
const kicked = new Map(); // nøgle -> {by, at}  (browsere der skal stoppe)
function kickedBy(user, ip, ua) {
  const k = kicked.get(keyOf(user, ip, ua));
  if (!k) return null;
  if (Date.now() - k.at > 60e3) { kicked.delete(keyOf(user, ip, ua)); return null; }
  return k.by;
}
function clearKick(user, ip, ua) { kicked.delete(keyOf(user, ip, ua)); }
/** Andre, der ser noget på samme login (undtagen den der spørger). */
function others(user, ip, ua) {
  const me = keyOf(user, ip, ua);
  return activeViewers().filter(v => v.user === user && v.key !== me);
}
/** Stop alle andre på samme login. Browsere stoppes her; tv'er returneres, så de kan bedes om at stoppe. */
function takeover(user, byDevice, exceptKey) {
  const tvs = [];
  for (const [k, v] of viewers) {
    if (v.user !== user || k === exceptKey) continue;
    if (v.tv) tvs.push(v); else kicked.set(k, { by: byDevice, at: Date.now() });
    viewers.delete(k);
  }
  return tvs;
}
/** Tv'erne melder selv, hvad de viser (hvert minut, og "stop" når de holder op). */
function tvPlaying(user, name, title, kind, ip) {
  const key = user + '|tv|' + name;
  if (!title) { viewers.delete(key); return false; }
  const isNew = !viewers.has(key) || viewers.get(key).title !== title;
  viewers.set(key, { key, user, device: name, title, kind, via: 'tv', at: Date.now(), tv: true, ip });
  rememberTv(ip, name);
  if (isNew) takeover(user, name, key); // tv'et er gået i gang: browserne på samme login stopper
  return isNew;
}
function activeViewers() {
  const now = Date.now(), out = [];
  for (const [k, v] of viewers) {
    if (now - v.at > (v.tv ? 150e3 : 30e3)) { viewers.delete(k); continue; }
    out.push(v);
  }
  return out.sort((a, b) => a.user.localeCompare(b.user));
}

// ---- tunnelen (Windows-tjenesten "cloudflared") ----
let lastRemote = 0;
const sawRemote = () => { lastRemote = Date.now(); };
function tunnel() {
  return new Promise(res => {
    if (process.platform !== 'win32') return res({ service: null, lastRemote });
    execFile('sc', ['query', 'cloudflared'], { timeout: 4000, windowsHide: true }, (e, out) => {
      const m = /STATE\s*:\s*\d+\s+(\w+)/.exec(out || '');
      res({ service: m ? m[1] : (e ? 'MANGLER' : '?'), lastRemote });
    });
  });
}

// ---- beskeder til telefonen via ntfy.sh (kun hvis et emne er sat) ----
async function notify(title, msg, prio = 'default') {
  if (!cfg.ntfy) return false;
  try {
    await fetch('https://ntfy.sh/' + encodeURIComponent(cfg.ntfy), { method: 'POST', body: msg, headers: { Title: title, Priority: prio, Tags: 'tv' }, signal: AbortSignal.timeout(10000) });
    return true;
  } catch { return false; }
}

// ---- backup af indstillinger (uden login og cache) ----
function defaultBackupDir() {
  if (process.platform === 'win32') {
    try {
      for (const u of fs.readdirSync('C:\\Users')) {
        const od = path.join('C:\\Users', u, 'OneDrive');
        if (fs.existsSync(od)) return path.join(od, 'VoresTV-backup');
      }
    } catch { }
  }
  return path.join(DATA, 'backups');
}
const backupDir = () => cfg.backupDir || defaultBackupDir();
const SKIP = /(^|[\\/])(cache|backups)([\\/]|$)|cred\.json$|cast\.secret$|\.tmp$/;

function backup() {
  const dir = backupDir();
  const d = new Date(), stamp = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  const to = path.join(dir, stamp);
  fs.mkdirSync(to, { recursive: true });
  let files = 0;
  const walk = (from, rel) => {
    for (const e of fs.readdirSync(from, { withFileTypes: true })) {
      const r = path.join(rel, e.name);
      if (SKIP.test(r)) continue;
      if (e.isDirectory()) walk(path.join(from, e.name), r);
      else { fs.mkdirSync(path.join(to, rel), { recursive: true }); fs.copyFileSync(path.join(from, e.name), path.join(to, r)); files++; }
    }
  };
  walk(DATA, '');
  // behold de 14 nyeste
  const all = fs.readdirSync(dir).filter(n => /^\d{4}-\d{2}-\d{2}$/.test(n)).sort();
  for (const old of all.slice(0, Math.max(0, all.length - 14))) fs.rmSync(path.join(dir, old), { recursive: true, force: true });
  cfg.lastBackup = { at: Date.now(), files, dir: to };
  saveCfg();
  return cfg.lastBackup;
}
function backupInfo() {
  let count = 0;
  try { count = fs.readdirSync(backupDir()).filter(n => /^\d{4}-\d{2}-\d{2}$/.test(n)).length; } catch { }
  return { dir: backupDir(), last: cfg.lastBackup || null, count, custom: !!cfg.backupDir };
}

// natlig backup kl. ca. 03.30
setInterval(() => {
  const d = new Date();
  const today = d.toDateString();
  if (d.getHours() === 3 && d.getMinutes() >= 30 && cfg.backupDay !== today) {
    cfg.backupDay = today;
    try { backup(); } catch (e) { notify('Vores TV: backup fejlede', String(e.message || e), 'high'); }
  }
}, 60e3).unref();

// ---- tv'ernes lager: tøm apps' midlertidige filer (cache) via adb – login og data røres ikke ----
// Serveren kører som SYSTEM: adb og dens nøgle (som tv'erne har godkendt) ligger i en almindelig brugers profil.
function adbHome() {
  if (process.platform !== 'win32') return null;
  const homes = [process.env.LOCALAPPDATA && path.dirname(path.dirname(process.env.LOCALAPPDATA))];
  try { for (const u of fs.readdirSync('C:\\Users')) homes.push(path.join('C:\\Users', u)); } catch { }
  for (const h of homes.filter(Boolean)) {
    const pk = path.join(h, 'AppData', 'Local', 'Microsoft', 'WinGet', 'Packages');
    try {
      for (const d of fs.readdirSync(pk)) if (/^Google\.PlatformTools/i.test(d)) {
        const p = path.join(pk, d, 'platform-tools', 'adb.exe');
        if (fs.existsSync(p)) return { exe: p, keys: path.join(h, '.android', 'adbkey') };
      }
    } catch { }
  }
  return null;
}
function adb(args, ms = 20000) {
  const h = adbHome();
  const env = Object.assign({}, process.env);
  if (h && fs.existsSync(h.keys)) env.ADB_VENDOR_KEYS = h.keys;
  return new Promise(res => execFile(process.env.VORESTV_ADB || (h ? h.exe : 'adb'), args, { timeout: ms, windowsHide: true, env }, (e, out, err) => res({ e, out: String(out || '') + String(err || '') })));
}
const freeKb = out => { const l = out.trim().split('\n').pop().trim().split(/\s+/); return +l[3] || 0; };
/** Tv'er, vi har set på hjemmenettet (gemmes, så natten også finder dem, når appen er lukket). */
function rememberTv(ip, name) {
  ip = String(ip || '').replace(/^::ffff:/, '');
  if (!/^(192\.168\.|10\.|172\.(1[6-9]|2\d|3[01])\.)/.test(ip)) return;
  cfg.tvs = cfg.tvs || {};
  const nm = name || cfg.tvs[ip] || ip;
  if (cfg.tvs[ip] === nm) return;
  cfg.tvs[ip] = nm; saveCfg();
}
async function cleanTvs(found) {
  for (const t of found) rememberTv(t.ip, t.name);
  const tvs = Object.entries(cfg.tvs || {}).map(([ip, name]) => ({ ip, name }));
  const res = [];
  for (const tv of tvs) {
    const t = tv.ip + ':5555';
    const c = await adb(['connect', t], 10000);
    if (c.e && !/connected|authenticate/.test(c.out)) { res.push({ name: tv.name, error: 'adb mangler eller svarer ikke' }); continue; }
    const before = await adb(['-s', t, 'shell', 'df', '/data']);
    if (/unauthorized|authenticate/.test(c.out + before.out)) { res.push({ name: tv.name, error: 'tv\'et har ikke godkendt serveren (adb)' }); continue; }
    if (before.e) { res.push({ name: tv.name, error: (before.out.trim().split('\n').pop() || 'fejl').slice(0, 120) }); continue; }
    await adb(['-s', t, 'shell', 'pm', 'trim-caches', '64G'], 120000);
    const after = await adb(['-s', t, 'shell', 'df', '/data']);
    const b = freeKb(before.out), a = freeKb(after.out);
    res.push({ name: tv.name, freedMB: Math.max(0, Math.round((a - b) / 1024)), freeMB: Math.round(a / 1024) });
  }
  cfg.lastClean = { at: Date.now(), tvs: res };
  saveCfg();
  const low = res.filter(r => r.freeMB != null && r.freeMB < 600);
  if (low.length) notify('Vores TV: tv-lager næsten fyldt', low.map(r => r.name + ': ' + r.freeMB + ' MB fri efter oprydning').join('\n'), 'high');
  return cfg.lastClean;
}

function server() {
  const m = process.memoryUsage();
  return { uptime: Math.round(process.uptime()), rss: m.rss, heap: m.heapUsed, freeMem: os.freemem(), totalMem: os.totalmem(), node: process.version, platform: process.platform, host: os.hostname() };
}

function setCfg(p) {
  if (p.ntfy != null) cfg.ntfy = String(p.ntfy).trim().replace(/[^\w-]/g, '') || undefined;
  if (p.backupDir != null) cfg.backupDir = String(p.backupDir).trim() || undefined;
  saveCfg();
}

module.exports = { seenStream, tvPlaying, others, takeover, kickedBy, clearKick, keyOf, device, activeViewers, sawRemote, tunnel, notify, backup, backupInfo, cleanTvs, rememberTv, server, setCfg, cfg: () => cfg };
