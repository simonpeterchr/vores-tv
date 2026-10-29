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
function seenStream(user, ip, ua, title, kind, remote) {
  const key = user + '|' + ip + '|' + device(ua);
  viewers.set(key, { user, device: device(ua), title, kind, via: remote ? 'udefra' : 'hjemme', at: Date.now() });
}
/** Tv'erne melder selv, hvad de viser (hvert minut, og "stop" når de holder op). */
function tvPlaying(user, name, title, kind) {
  const key = user + '|tv|' + name;
  if (!title) viewers.delete(key);
  else viewers.set(key, { user, device: name, title, kind, via: 'tv', at: Date.now(), tv: true });
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

function server() {
  const m = process.memoryUsage();
  return { uptime: Math.round(process.uptime()), rss: m.rss, heap: m.heapUsed, freeMem: os.freemem(), totalMem: os.totalmem(), node: process.version, platform: process.platform, host: os.hostname() };
}

function setCfg(p) {
  if (p.ntfy != null) cfg.ntfy = String(p.ntfy).trim().replace(/[^\w-]/g, '') || undefined;
  if (p.backupDir != null) cfg.backupDir = String(p.backupDir).trim() || undefined;
  saveCfg();
}

module.exports = { seenStream, tvPlaying, activeViewers, sawRemote, tunnel, notify, backup, backupInfo, server, setCfg, cfg: () => cfg };
