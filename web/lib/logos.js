'use strict';
// Rigtige kanallogoer fra github.com/tv-logo/tv-logos (udbyderens er klemt ned i 96×96).
// Matcher på kanalnavn, i landets mappe først: "DK| TV 2 SPORT X ᴴᴰ" → countries/nordic/denmark/tv2-sport-x-dk.png
const C = require('./core');
const PATHS = require('./logos.json');
const CDN = 'https://cdn.jsdelivr.net/gh/tv-logo/tv-logos@main/';

const FOLDER = { DK: 'denmark', SE: 'sweden', NO: 'norway', FI: 'finland', UK: 'united-kingdom', GB: 'united-kingdom', US: 'united-states', DE: 'germany', ES: 'spain', IT: 'italy', FR: 'france', NL: 'netherlands' };
const maps = {}; // mappe -> Map(navn -> sti)
for (const p of PATHS) {
  const parts = p.split('/');
  const folder = parts[1] === 'nordic' && parts.length > 3 ? parts[2] : parts[1];
  let stem = parts[parts.length - 1].replace(/\.png$/, '').replace(/-[a-z]{2,3}$/, '');
  const hz = /-hz$/.test(stem);
  if (hz) stem = stem.slice(0, -3);
  const m = maps[folder] || (maps[folder] = new Map());
  if (!m.has(stem) || !hz) m.set(stem, p); // hellere det almindelige logo end den vandrette variant
}

const DROP = /\b(hd|fhd|uhd|4k|8k|raw|sd|hevc|h265|vip|ultra|syn|multi|multi-sub|multi-audio|overlay-sub|sub|50fps|60fps|backup|alt|dk|se|no|uk|danmark|europe|ukraine|tv guide)\b/g;
const ALIAS = [
  [/^viaplay-(?=(tv|v|dk|see|ramsang|film))/, ''], // "VIAPLAY TV2 HD" → tv2
  [/^v-(explore|history|nature)/, 'viasat-$1'], [/^v-sport-ultra.*/, 'v-sport-ultrahd'], [/^viaplay-film-/, 'v-film-'],
  [/premier(e)?$|premiume$/, 'premiere'], [/^nat-geo/, 'national-geographic'], [/^id-investigation/, 'investigation'],
  [/^discovery-channels$/, 'discovery-channel'], [/ramsang/, 'dr-ramasjang'], [/^dr-ramasjang/, 'dr-ramasjang'],
  [/^disney-junoir/, 'disney-jr'], [/^disney-junior/, 'disney-jr'], [/^sports-news/, 'sport-news'], [/^viaplay-sports-news/, 'viaplay-sport-news'],
  [/^tv2-oj$/, 'tv2-ostjylland'], [/ostjyl(and)?$/, 'ostjylland'], [/^tv2-midt-vest$|^tv-2-midt-vest$/, 'tv-midtvest'], [/^tv2-midtvest$/, 'tv-midtvest'], [/^tv2-syd$/, 'tv-syd'],
  [/^dk1$/, 'dr1'], [/^dk2$/, 'dr2'], [/^film-/, 'v-film-'], [/^v-film-series$/, 'v-series'], [/^nicktoons$/, 'nick-toons'],
  [/^skyshowtime-?(\d)$/, 'sky-showtime-$1'], [/^folketingskanalen$/, 'folketinget'], [/^dk-4$|^4$/, 'dk4'],
];
function keys(name) {
  let s = C.norm(name).replace(/æ/g, 'ae').replace(/ø/g, 'o').replace(/å/g, 'a').replace(/[’'`*]/g, '').replace(/[()\[\]]/g, ' ').replace(/&/g, ' and ').replace(/\+/g, ' plus ');
  s = s.replace(DROP, ' ').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
  if (!s) return [];
  const out = new Set();
  if (/\bv-sport-ultra|\bv sport ultra/i.test(C.norm(name).replace(/\s+/g, ' '))) out.add('v-sport-ultrahd');
  const add = k => { if (!k) return; out.add(k); out.add(k.replace(/\b([a-z]{1,3})-(\d)/g, '$1$2')); }; // "tv-2-sport" → "tv2-sport"
  add(s);
  let a = s.replace(/\b([a-z]{1,3})-(\d)/g, '$1$2');
  for (const [re, to] of ALIAS) a = a.replace(re, to);
  add(a);
  out.add(s.replace(/-/g, ''));
  return [...out];
}

const cache = new Map();
/** Bedste logo-URL for et kanalnavn, eller null. countries: landekoder at prøve først (fx ["DK"]). */
function find(name, countries) {
  const ck = countries.join(',') + '|' + name;
  if (cache.has(ck)) return cache.get(ck);
  const order = [...new Set(countries.map(c => FOLDER[c]).filter(Boolean).concat(['denmark', 'nordic', 'sweden', 'norway', 'international', 'united-kingdom']))];
  const ks = keys(name);
  let hit = null;
  outer: for (const f of order) {
    const m = maps[f]; if (!m) continue;
    for (const k of ks) if (m.has(k)) { hit = CDN + m.get(k); break outer; }
  }
  cache.set(ck, hit);
  return hit;
}

module.exports = { find };
