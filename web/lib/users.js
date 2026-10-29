'use strict';
// Brugere: husstanden ("hjem", altid admin) og egne brugere med eget IPTV-login. Koder gemmes som scrypt-hash.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const DATA = process.env.VORESTV_DATA || path.join(__dirname, '..', 'data');
const FILE = path.join(DATA, 'users.json');
const HOME = 'hjem';
const SESSION_AGE = 60 * 86400e3;
fs.mkdirSync(DATA, { recursive: true });

let db = { users: {}, sessions: {} };
try { db = Object.assign(db, JSON.parse(fs.readFileSync(FILE, 'utf8'))); } catch { }
if (!db.users[HOME]) db.users[HOME] = { admin: true, created: Date.now() };
function save() { const t = FILE + '.tmp'; fs.writeFileSync(t, JSON.stringify(db, null, 1)); fs.renameSync(t, FILE); }

const hashPw = (pw, salt) => crypto.scryptSync(pw, salt, 64, { N: 16384 }).toString('hex');
const sha = s => crypto.createHash('sha256').update(s).digest('hex');
const okName = n => /^[a-z0-9æøå._-]{2,24}$/.test(n);
const okPw = pw => pw.length >= 8 ? null : 'Koden skal være mindst 8 tegn.';

function dir(name) { return name === HOME ? DATA : path.join(DATA, 'users', name); }
function get(name) { const u = db.users[name]; return u ? { name, admin: !!u.admin, hash: u.hash } : null; }
function names() { return Object.keys(db.users).filter(n => n !== HOME); }
function list() {
  return Object.entries(db.users).map(([name, u]) => ({ name, admin: !!u.admin, hasPassword: !!u.hash, created: u.created, lastSeen: u.lastSeen || 0 }));
}

function check(name, pw) {
  name = name.trim().toLowerCase();
  const u = db.users[name];
  if (!u || !u.hash) { hashPw(pw, 'x'.repeat(16)); return null; } // samme tidsforbrug uanset om brugeren findes
  const a = Buffer.from(hashPw(pw, u.salt), 'hex'), b = Buffer.from(u.hash, 'hex');
  return a.length === b.length && crypto.timingSafeEqual(a, b) ? get(name) : null;
}

function setPassword(name, pw) {
  const u = db.users[name];
  if (!u) return 'Brugeren findes ikke.';
  const e = okPw(pw); if (e) return e;
  u.salt = crypto.randomBytes(16).toString('hex');
  u.hash = hashPw(pw, u.salt);
  for (const [t, s] of Object.entries(db.sessions)) if (s.name === name) delete db.sessions[t]; // log ud alle steder
  save();
  return null;
}

function add(name, pw) {
  name = name.trim().toLowerCase();
  if (!okName(name)) return 'Brugernavnet må kun have små bogstaver, tal, punktum og bindestreg (2–24 tegn).';
  if (db.users[name]) return 'Brugernavnet findes allerede.';
  const e = okPw(pw); if (e) return e;
  db.users[name] = { admin: false, created: Date.now() };
  return setPassword(name, pw);
}

function remove(name) {
  if (name === HOME || !db.users[name]) return;
  delete db.users[name];
  for (const [t, s] of Object.entries(db.sessions)) if (s.name === name) delete db.sessions[t];
  save();
  try { fs.rmSync(dir(name), { recursive: true, force: true }); } catch { }
}

// ---- sessioner (kun hash af nøglen gemmes) ----
function newSession(name) {
  const t = crypto.randomBytes(32).toString('base64url');
  db.sessions[sha(t)] = { name, at: Date.now() };
  save();
  return t;
}
function session(t) {
  if (!t) return null;
  const s = db.sessions[sha(t)];
  if (!s || Date.now() - s.at > SESSION_AGE || !db.users[s.name]) return null;
  const u = db.users[s.name];
  if (!u.lastSeen || Date.now() - u.lastSeen > 3600e3) { u.lastSeen = Date.now(); save(); }
  return get(s.name);
}
function endSession(t) { if (t && db.sessions[sha(t)]) { delete db.sessions[sha(t)]; save(); } }

// ---- spærring efter for mange forkerte forsøg ----
const attempts = new Map();
function allowAttempt(ip) {
  const now = Date.now(), a = (attempts.get(ip) || []).filter(t => now - t < 15 * 60e3);
  a.push(now); attempts.set(ip, a);
  return a.length <= 8;
}
function resetAttempts(ip) { attempts.delete(ip); }

module.exports = { HOME, dir, get, names, list, check, add, setPassword, remove, newSession, session, endSession, allowAttempt, resetAttempts };
