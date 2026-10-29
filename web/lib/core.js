'use strict';
// Fælles tekstlogik, oversat 1:1 fra tv-appen (Names, Search, Vod.cleanTitle, Ev, Fmt).

// ---- Names: "DK| DENMARK ᴴᴰ/ᴿᴬᵂ" → "Denmark HD/RAW" ----
const SUP_FROM = 'ᴬᴮᴰᴱᴳᴴᴵᴶᴷᴸᴹᴺᴼᴾᴿᵀᵁⱽᵂᵃᵇᶜᵈᵉᶠᵍʰⁱᶦʲᵏˡᵐⁿᵒᵖʳˢᵗᵘᵛʷˣʸᶻ⁰¹²³⁴⁵⁶⁷⁸⁹';
const SUP_TO = 'ABDEGHIJKLMNOPRTUVWabcdefghiijklmnoprstuvwxyz0123456789';
const PREFIX = /^\s*([A-Za-z0-9+]{2,6})\s*[|:]\s*/;
const KEEP = new Set(['PPV', 'VIP', 'RAW', 'UHD', 'FHD', 'HD', 'SD', 'HEVC', '4K', '8K', 'TV', 'TV2', 'TV3', 'DR', 'MAX', 'HBO', 'BBC', 'ITV', 'NBC', 'CBS', 'ABC', 'ESPN', 'NFL', 'NBA', 'NHL', 'UFC', 'USA']);

function prefix(s) {
  const m = PREFIX.exec(s || '');
  return m ? m[1].toUpperCase() : '';
}

function sup(s) {
  let b = '', inSup = false;
  for (const c of s) {
    const k = SUP_FROM.indexOf(c);
    if (k >= 0) {
      const last = b[b.length - 1];
      if (!inSup && b.length && last !== ' ' && last !== '/') b += ' ';
      b += SUP_TO[k].toUpperCase();
      inSup = true;
    } else { b += c; inSup = false; }
  }
  return b;
}

function channel(s) {
  if (!s) return '';
  return sup(s.replace(PREFIX, '')).replace(/\s+/g, ' ').trim();
}

function group(s) {
  if (!s) return '';
  const t = sup(s.replace(PREFIX, '')).replace(/\s+/g, ' ').trim();
  return t.split(' ').map(w => {
    const bare = w.replace(/[^\p{L}0-9+]/gu, '');
    const caps = bare.length > 3 && bare === bare.toUpperCase() && !/\d/.test(bare);
    return caps && !KEEP.has(bare) && !w.includes('/') ? w[0] + w.slice(1).toLowerCase() : w;
  }).join(' ');
}

/** "4K-DK - Sommerdahl (2020) (DK)" → {name: "Sommerdahl", year: "2020"} */
function cleanTitle(raw, year) {
  let nm = channel(raw).replace(/^[A-Z0-9+]{1,6}(-[A-Z0-9+]{1,6})?\s+-\s+/, '');
  for (let r = 0; r < 3; r++) nm = nm.replace(/\s*[(\[](DK|SE|NO|FI|EN|ENG|DANSK|NORDIC|MULTI[^)\]]*|SUB[^)\]]*|4K|HD|UHD)[)\]]\s*$/i, '');
  const ym = /\s*[(\[]?((19|20)\d\d)[)\]]?\s*$/.exec(nm);
  if (ym) { if (!year) year = ym[1]; nm = nm.slice(0, ym.index).trim(); }
  return { name: nm.replace(/\s*-\s*$/, ''), year: year || '' };
}

// ---- Search ----
function norm(s) {
  if (!s) return '';
  s = s.toLowerCase().replace(/æ/g, '\u0001').replace(/ø/g, '\u0002').replace(/å/g, '\u0003');
  s = s.normalize('NFD').replace(/\p{M}/gu, '');
  return s.replace(/\u0001/g, 'æ').replace(/\u0002/g, 'ø').replace(/\u0003/g, 'å');
}

const SYN = {
  'fodbold': ['fodbold', 'superliga', 'champions league', 'europa league', 'conference league', 'premier league', 'la liga', 'laliga', 'bundesliga', 'serie a', 'ligue 1', 'landskamp', 'landsholdet', 'pokal', 'sydbank pokalen', '1. division', 'soccer', 'football', 'vm-kval', 'em-kval', 'nations league', 'uefa', 'fifa'],
  'håndbold': ['håndbold', 'handball', 'herreligaen', 'kvindeligaen', 'ehf'],
  'tennis': ['tennis', 'atp', 'wta', 'wimbledon', 'roland garros', 'us open', 'australian open'],
  'cykling': ['cykling', 'tour de france', 'giro', 'vuelta', 'cycling'],
  'formel 1': ['formel 1', 'formula 1', 'f1', 'grand prix'],
  'ishockey': ['ishockey', 'nhl', 'metal ligaen', 'hockey'],
  'dart': ['darts', 'pdc', 'premier league darts', 'world matchplay', 'grand slam of darts', 'players championship', 'world grand prix', 'european championship darts', 'vm i dart', 'masters darts', 'littler', 'van gerwen'],
  'golf': ['golf', 'pga', 'dp world tour', 'ryder cup'],
  'nyheder': ['nyheder', 'tv avisen', 'tva', 'news', '21 søndag', 'deadline'],
};

function synonyms(key) { return (SYN[key] || []).map(norm); }

/** En søgning = liste af alternativer; hvert alternativ er ord, der alle skal findes ("=" = helt ord). */
function query(q) {
  q = norm(q).trim();
  if (!q) return [];
  for (const [k, v] of Object.entries(SYN)) {
    const nk = norm(k);
    if (q === nk || q === nk + 'kamp' || q === nk + 'kampe') return v.map(s => ['=' + norm(s)]);
  }
  return [q.split(/\s+/)];
}

const LD = /[\p{L}\p{N}]/u;
function has(t, w) {
  const whole = w.startsWith('=');
  if (whole) w = w.slice(1);
  for (let i = t.indexOf(w); i >= 0; i = t.indexOf(w, i + 1)) {
    const startOk = i === 0 || !LD.test(t[i - 1]);
    const e = i + w.length;
    const endOk = !whole || e >= t.length || !LD.test(t[e]);
    if (startOk && endOk) return true;
  }
  return false;
}

function matchNorm(t, q) {
  if (!t || !q.length) return false;
  return q.some(words => words.every(w => has(t, w)));
}

// ---- tid (dansk) ----
const DA = 'da-DK';
const pad = n => String(n).padStart(2, '0');
function hm(t) { const d = new Date(t); return pad(d.getHours()) + '.' + pad(d.getMinutes()); }
function dayDiff(t) {
  const a = new Date(); a.setHours(0, 0, 0, 0);
  const b = new Date(t); b.setHours(0, 0, 0, 0);
  return Math.round((b - a) / 86400000);
}
function when(t) {
  const d = dayDiff(t);
  const day = d === 0 ? 'I dag' : d === 1 ? 'I morgen' : d === -1 ? 'I går' : dayName(t);
  return day + ' ' + hm(t);
}
function dayName(t) {
  const d = new Date(t);
  return d.toLocaleDateString(DA, { weekday: 'short' }) + ' ' + d.getDate() + '. ' + d.toLocaleDateString(DA, { month: 'short' });
}

/** Tidspunkt i en given tidszone som epoch-ms. */
const fmts = {};
function tzOffset(tz, t) {
  const f = fmts[tz] || (fmts[tz] = new Intl.DateTimeFormat('en-US', { timeZone: tz, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }));
  const p = {};
  for (const x of f.formatToParts(new Date(t))) p[x.type] = x.value;
  return Date.UTC(+p.year, +p.month - 1, +p.day, +p.hour, +p.minute, +p.second) - Math.floor(t / 1000) * 1000;
}
function at(tz, y, mo, d, h, mi) {
  const g = Date.UTC(y, mo, d, h, mi);
  const t = g - tzOffset(tz, g);
  return g - tzOffset(tz, t);
}

// ---- Ev: kamp og tidspunkt i PPV-kanalnavne ----
const MON = '(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*';
const EA = new RegExp('\\|\\s*(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)[a-z]*\\s+(\\d{1,2})\\s+' + MON + '\\s+(\\d{1,2}):(\\d{2})\\s*(CET|CEST|GMT|BST|UTC|EDT|EST|ET)?', 'i');
const EB = new RegExp('@\\s*' + MON + '\\s+(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})\\s*(AM|PM)?', 'i');
const EC = /\|\s*(\d{4})-(\d{2})-(\d{2})\s*\|\s*(\d{1,2}):(\d{2})\s*\((GMT|UTC|CET|ET)\)/i;
const EC2 = /\|\s*(\d{2})-(\d{2})-(\d{4})\s*\|\s*(\d{1,2}):(\d{2})\s*\((GMT|UTC|CET|ET)\)/i;
const ED = /[@|]\s*(\d{4})-(\d{2})-(\d{2})[ T]+(\d{1,2}):(\d{2})/;
const MONTHS = 'janfebmaraprmayjunjulaugsepoctnovdec';
const month = m => MONTHS.indexOf(m.slice(0, 3).toLowerCase()) / 3;

function zone(tz, pre) {
  if (tz) {
    switch (tz.toUpperCase()) {
      case 'GMT': case 'UTC': return 'UTC';
      case 'BST': return 'Europe/London';
      case 'EDT': case 'EST': case 'ET': return 'America/New_York';
      default: return 'Europe/Copenhagen';
    }
  }
  if (pre === 'US' || pre === 'CA') return 'America/New_York';
  if (pre === 'UK') return 'Europe/London';
  return 'Europe/Copenhagen';
}
function yearFor(mo) {
  const now = new Date(), y = now.getFullYear(), m = now.getMonth();
  if (mo - m > 6) return y - 1;
  if (m - mo > 6) return y + 1;
  return y;
}
const evClean = t => t.replace(/^\s*\d{1,3}\s*-\s+/, '').replace(/\s*:\s*$/, '').trim();

function parseEv(name, pre) {
  if (!name) return null;
  let m;
  if ((m = EA.exec(name))) {
    const d = +m[1], mo = month(m[2]);
    return { title: evClean(name.slice(0, m.index)), extra: null, start: at(zone(m[5], pre), yearFor(mo), mo, d, +m[3], +m[4]) };
  }
  if ((m = EC.exec(name))) {
    const parts = name.slice(0, m.index).split('|');
    return { title: evClean(parts[0]), extra: parts.length > 1 ? parts[1].trim() : null, start: at(zone(m[6], pre), +m[1], +m[2] - 1, +m[3], +m[4], +m[5]) };
  }
  if ((m = EC2.exec(name))) {
    const parts = name.slice(0, m.index).split('|');
    return { title: evClean(parts[0]), extra: parts.length > 1 ? parts[1].trim() : null, start: at(zone(m[6], pre), +m[3], +m[2] - 1, +m[1], +m[4], +m[5]) };
  }
  if ((m = ED.exec(name))) {
    return { title: evClean(name.slice(0, m.index)), extra: null, start: at(zone(null, pre), +m[1], +m[2] - 1, +m[3], +m[4], +m[5]) };
  }
  if ((m = EB.exec(name))) {
    const mo = month(m[1]), d = +m[2];
    let h = +m[3] % 12;
    if (m[5] && m[5].toUpperCase() === 'PM') h += 12;
    if (!m[5]) h = +m[3];
    return { title: evClean(name.slice(0, m.index)), extra: null, start: at(zone(null, pre), yearFor(mo), mo, d, h, +m[4]) };
  }
  return null;
}

/** "FC ROSKILDE" → "FC Roskilde" */
function nice(s) {
  if (!s || s !== s.toUpperCase()) return s;
  return s.toLocaleLowerCase('da').split(' ').map(w => {
    if (w.length <= 3 && /^[a-zæøå]+$/.test(w) && !/^(i|og|af|de|la|el|di)$/.test(w)) return w.toUpperCase();
    return w ? w[0].toUpperCase() + w.slice(1) : w;
  }).join(' ');
}

function b64(s) {
  if (!s) return '';
  try { return Buffer.from(s, 'base64').toString('utf8').trim(); } catch { return s; }
}

module.exports = { prefix, channel, group, cleanTitle, norm, synonyms, query, has, matchNorm, hm, when, dayName, dayDiff, at, parseEv, nice, b64 };
