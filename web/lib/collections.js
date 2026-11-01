'use strict';
// Fanen "Univers": universer (Marvel, Star Wars …) og sæsontemaer i rækkefølge, plus julekalenderne.
// For hver titel vælges den bedste engelske udgave i kataloget: engelsk først, så surround-lyd, så 4K, så højeste bitrate.
// Børnenes julekalender er derimod på dansk (de mindste kan ikke læse undertekster).
const fs = require('fs');
const path = require('path');
const C = require('./core');

const U = require('./universes');
const LISTS = [...U.UNIVERSES, ...U.SEASONS.map(x => ({ ...x, season: true }))];

// Julekalenderen: én film bag hver låge (fra jeres gamle "Jul"-playliste i Plex + tre andre julefilm derfra)
const JUL = [
  ['m', 'The Polar Express', 2004],
  ['m', 'Elf', 2003],
  ['m', 'The Santa Clause', 1994],
  ['m', 'Jingle All the Way', 1996],
  ['s', 'Mr. Bean', 1990, 'Mr Bean', { show: 'Merry Christmas Mr. Bean', note: 'Afsnit 7 i Mr. Bean' }], // tv-specialen findes kun som afsnit
  ['m', 'Home Alone', 1990],
  ['m', 'The Holiday', 2006],
  ['m', 'Gremlins', 1984],
  ['m', 'How the Grinch Stole Christmas', 2000, 'Dr. Seuss\' How the Grinch Stole Christmas'],
  ['m', 'Office Christmas Party', 2016],
  ['m', 'Surviving Christmas', 2004],
  ['m', 'Trading Places', 1983],
  ['m', 'Harry Potter and the Philosopher\'s Stone', 2001, 'Harry Potter and the Sorcerer\'s Stone'],
  ['m', 'The Night Before', 2015],
  ['m', 'Daddy\'s Home 2', 2017],
  ['m', 'Anna and the Apocalypse', 2018],
  ['m', 'Fatman', 2020],
  ['m', 'A Bad Moms Christmas', 2017],
  ['m', 'The Grinch', 2018, 'Dr. Seuss\' The Grinch'],
  ['m', 'Bad Santa', 2003],
  ['m', 'Die Hard', 1988],
  ['m', 'Home Alone 2: Lost in New York', 1992],
  ['m', 'Love Actually', 2003],
  ['m', 'National Lampoon\'s Christmas Vacation', 1989, 'Christmas Vacation'],
];
LISTS.push({ key: 'jul', name: 'Julekalender', hidden: true, items: JUL });
// Børnenes julekalender: dagens afsnit af en dansk børnejulekalender (24 afsnit á ca. 24 min)
const BORN = ['s', 'Ludvig & julemanden', 2011, 'Ludvig og Julemanden', 'Ludvig og julemanden'];
const CALS = {
  film: { name: 'Julekalender', state: 'jul', order: [17, 5, 22, 11, 2, 19, 8, 14, 24, 1, 13, 20, 6, 9, 23, 3, 16, 10, 21, 4, 15, 12, 7, 18] },
  born: { name: 'Børnenes julekalender', state: 'julBorn', order: [3, 12, 7, 20, 1, 16, 9, 24, 14, 5, 18, 10, 22, 2, 13, 8, 19, 4, 23, 11, 6, 17, 21, 15] },
};
const danish = cat => /DANSK|DANSKE|^DK\b/i.test(String(cat || ''));

// titler sammenlignes uden tegnsætning: "Star Wars: Episode IV – A New Hope" = "star wars episode iv a new hope"
const key = s => C.norm(s).replace(/&/g, ' and ').replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
// katalogets navne har ofte "(2024) (US)" bagved eller "4K-ES-HDR - " foran
const keys = s => {
  const base = String(s || '').replace(/\s*\((\d{4}|[A-Z]{2,3})\)/g, '').replace(/^(?:4K\s*)?-?\s*SC\s*-\s*/i, ''); // "4K -SC- Frozen" = skandinavisk udgave
  const out = [key(base)];
  const m = /^[^:]{1,20}?\s+-\s+(.+)$/.exec(base);
  if (m) out.push(key(m[1]));
  return out;
};

const OTHER = /^(PT|BR|ES|FR|DE|IT|PL|GR|TR|AR|RU|RO|AL|IN|IR|CN|JP|PH)\b|[Ͱ-ϿЀ-ӿ֐-ۿ]|FRANCE|FRENCH|QUÉBEC|GERMAN|DEUTSCH|ITAL|ESPAÑ|SPAIN|LATINO|MEXIC|POLSK|TURK|GREEC|GREEK|INDIA|HINDI|PAKISTAN|SOMALIA|ALBANIA|AFRICA|MALTA|PORTUG|BRASIL|RUSSIA|ROMANIA|BULGAR|BELGIUM|VIDEOLAND|ASIA|CHINA|JAPAN|KOREA|PHILIPP|TAGALOG|BENGALI|OSN|MENA|ARAB|ÍSLAND|SUOMEN|DANSKE|\((FR|NL|TR)\)|DUBB/i;
const BRAND = /NETFLIX|DISNEY\+|APPLE\+|AMAZON|HBO|PARAMOUNT|PEACOCK|SHOWTIME|^SKY\b|UNIVERSAL|DREAMWORKS|TOP MOVIES|TOP KIDS|JAMES BOND|NICKELODEON|DISCOVERY\+|VIAPLAY|MULTI-SUBS/i;
/** Hvor engelsk er en gruppe? 3 = engelsk, 2.75 = engelsk m. nordiske undertekster, 2.5 = streamingtjenestens originaludgave, 2 = flersproget lyd, 1 = originalsprog m. nordiske/hollandske undertekster, 0 = andet sprog. */
function english(cat) {
  const n = String(cat || '').toUpperCase();
  const pre = /^([A-Z]{2})(\/[A-Z]{2})?\s*[-|:]/.exec(n);
  const kids = /KIDS|BARN|BØRN|ANIM/.test(n); // børnegrupper er ofte eftersynkroniserede
  if (pre) return /^(EN|UK|US)$/.test(pre[1]) ? 3 : /^(DK|NO|FI)$/.test(pre[1]) ? (kids ? 1 : 2.75) : /^(NL|EX)$/.test(pre[1]) ? 1 : 0; // NL/EX: originalt lydspor m. undertekster
  if (/^(NORDIC|SCANDINAVIA)/.test(n)) return kids ? 1 : 2.75; // engelsk tale med danske/nordiske undertekster
  if (OTHER.test(n)) return 0;
  if (/ENGLISH/.test(n)) return 3;
  if (BRAND.test(n)) return 2.5;
  if (/\(MULTI\)/.test(n)) return 2;
  return 1; // NORDIC, SVENSKA, NORGE, NETHERLANDS og ukendte
}
const is4k = cat => /4K|⁴ᴷ|3840|2160|UHD/i.test(String(cat || ''));
/** Surround (5.1/Atmos): grupper mærket Dolby Audio – eller streamingtjenesternes 4K-udgaver, der næsten altid har det. */
const surround = cat => /DOLBY AUDIO|ᴰᴼᴸᴮʸ ᴬᵁᴰᴵᴼ|ATMOS/i.test(String(cat || '')) || (is4k(cat) && /NETFLIX|DISNEY\+|APPLE\+|AMAZON|HBO|PARAMOUNT/i.test(String(cat || '')));
const tier = en => en >= 2.5 ? 3 : en >= 2 ? 2 : en >= 1 ? 1 : 0;

function create(P, DATA, opts = {}) {
  const canProbe = opts.canProbe || (() => true); // lydsporene tjekkes kun, når ingen ser noget
  const FILE = path.join(DATA, 'cache', 'collections.json');
  let store = {};
  try { store = JSON.parse(fs.readFileSync(FILE, 'utf8')); } catch { }
  const save = () => { try { fs.mkdirSync(path.dirname(FILE), { recursive: true }); fs.writeFileSync(FILE, JSON.stringify(store)); } catch { } };
  let busy = false;
  const tech = store.tech || (store.tech = {}); // id -> {b: bitrate, at}

  function candidates(src, cats, it, da, dub) {
    const [, title, year, ...rest] = it;
    const names = new Set([title, ...rest.filter(x => typeof x === 'string')].map(key));
    const out = [];
    for (const e of src) {
      if (!keys(e[1]).some(k => names.has(k))) continue;
      const y = +e[4];
      if (y && year && Math.abs(y - year) > 1) continue;
      const cat = cats.get(String(e[3])) || '';
      let en = da ? (danish(cat) ? 3 : 0) : english(cat);
      if (!da && /(^|\s)-?SC-?\s/i.test(e[1])) en = Math.max(en, 2.75); // SC-udgaver: engelsk tale, danske undertekster
      if (!en && !(dub && /NORDIC|BARN|BØRN|KIDS/i.test(cat))) continue;
      out.push({ e, cat, en, t: tier(en), k4: is4k(cat), sur: surround(cat), exact: y === year, dk: danish(cat) });
    }
    // findes udgaver med det rigtige år, droppes dem uden år (fx "The Grinch" kan være både 2000- og 2018-filmen)
    const sure = out.some(c => c.exact) ? out.filter(c => c.exact || +c.e[4]) : out;
    return sure.sort(rank);
  }
  // bedst først: mest engelsk, så surround-lyd, så 4K, så højeste bitrate
  const rank = (a, b) => (b.dan || 0) - (a.dan || 0) || b.t - a.t || b.sur - a.sur || b.k4 - a.k4 || (b.b || 0) - (a.b || 0) || b.en - a.en || b.exact - a.exact;

  /** Hvilke af kandidaterne har dansk tale? Danske grupper antages at have det; nordiske/børne-mkv'er tjekkes i filen (gemmes 90 dage). */
  async function danishAudio(cs) {
    let probes = 0;
    for (const c of cs) {
      if (c.dk) { c.dan = 1; continue; }
      if (!/NORDIC|BARN|BØRN|KIDS|SC/i.test(c.cat + ' ' + c.e[1]) || !/^mkv$/i.test(c.e[5] || '')) continue;
      const t = tech[c.e[0]] || (tech[c.e[0]] = { b: 0, at: 0 });
      if (!t.l || Date.now() - t.la > 90 * 86400e3) {
        if (probes >= 4 || !canProbe()) continue; // højst 4 tjek pr. titel, og aldrig mens nogen ser noget
        probes++;
        const l = await P.audioLangs(c.e[0], c.e[5]);
        await new Promise(r => setTimeout(r, 1500));
        if (!l) continue;
        t.l = l; t.la = Date.now();
      }
      if (t.l.includes('dan')) c.dan = 1;
    }
    cs.sort(rank);
  }

  /** Bygger samlingerne (henter bitrate for de bedste kandidater, langsomt for ikke at belaste udbyderen). */
  async function resolve() {
    if (busy || !P.search.movies || !P.search.series) return;
    busy = true;
    try {
      const cmap = kind => new Map([...P.vodCategories(kind).values()].map(c => [c.id, c.raw || c.name]));
      const mcats = cmap(P.MOVIE), scats = cmap(P.SERIES);
      if (!mcats.size || !scats.size) return; // grupperne hentes stadig – prøv igen ved næste kald
      const out = [];
      for (const L of LISTS) {
        const items = [];
        for (const it of L.items) {
          const movie = it[0] === 'm';
          const dub = L.group === 'Familie & animation'; // børnene: dansk tale først
          const cs = candidates(movie ? P.search.movies : P.search.series, movie ? mcats : scats, it, false, dub);
          if (dub) await danishAudio(cs);
          if (!cs.length) { items.push({ missing: true, name: (it.find(x => x && typeof x === 'object') || {}).show || it[1], year: it[2], kind: movie ? 'movie' : 'series' }); continue; }
          let best = cs[0];
          if (movie) {
            // blandt de bedste udgaver (samme sprog-niveau) hentes bitrate, så den bedste kan vælges
            const top = cs.filter(c => (c.dan || 0) === (best.dan || 0) && c.t === best.t && c.sur === best.sur && c.k4 === best.k4).slice(0, 4);
            for (const c of top.length > 1 || best.k4 ? top : []) {
              const t = tech[c.e[0]];
              if (!t || Date.now() - t.at > 30 * 86400e3) {
                try { tech[c.e[0]] = { b: (await P.vodTech(c.e[0])).bitrate, at: Date.now() }; } catch { tech[c.e[0]] = { b: 0, at: Date.now() - 29 * 86400e3 }; }
                await new Promise(r => setTimeout(r, 1200));
              }
              c.b = tech[c.e[0]].b || 0;
              if (c.b && c.b < 2000) c.k4 = c.sur = false; // "4K" med under 2 Mbit/s er en dårlig kopi
            }
            best = top.sort(rank)[0] || best;
            // en meget lav bitrate (fx 600 kbit/s) er for ringe: så hellere originalsproget med undertekster i langt bedre kvalitet
            if (!best.dan && best.b && best.b < 2000) {
              for (const c of cs.filter(c => c.t === 1).slice(0, 3)) {
                const t = tech[c.e[0]];
                if (!t || Date.now() - t.at > 30 * 86400e3) {
                  try { tech[c.e[0]] = { b: (await P.vodTech(c.e[0])).bitrate, at: Date.now() }; } catch { tech[c.e[0]] = { b: 0, at: Date.now() - 29 * 86400e3 }; }
                  await new Promise(r => setTimeout(r, 1200));
                }
                c.b = tech[c.e[0]].b || 0;
                if (c.b >= 2 * best.b || (!c.b && (c.k4 || c.sur))) { best = c; break; }
              }
            }
          }
          const e = best.e;
          const opt = it.find(x => x && typeof x === 'object') || {};
          items.push({ kind: movie ? 'movie' : 'series', id: e[0], name: opt.show || it[1], note: opt.note, year: it[2], img: e[2], ext: e[5], cat: best.cat, quality: best.k4 ? '4K' : 'HD', surround: best.sur, bitrate: best.b || 0, audio: best.dan ? 'dan' : undefined, lang: best.dan ? 'dansk tale' : best.en === 2.75 ? 'engelsk, danske undertekster' : best.en >= 2.5 ? 'engelsk' : best.en === 2 ? 'flere lydspor' : 'original m. undertekster', versions: cs.length });
        }
        out.push({ key: L.key, name: L.name, group: L.group, order: L.order, note: L.note, season: !!L.season, items, covers: items.filter(x => !x.missing && x.img).slice(0, 4).map(x => x.img) });
        // gem undervejs, så de første universer kan vises, mens resten findes
        store.lists = out.concat((store.lists || []).filter(l => !out.some(o => o.key === l.key) && LISTS.some(x => x.key === l.key)));
        save();
      }
      const bc = candidates(P.search.series, scats, BORN, true);
      store.born = bc.length ? { id: bc[0].e[0], name: 'Ludvig & Julemanden', img: bc[0].e[2], cat: bc[0].cat } : null;
      store.at = Date.now();
      save();
      // billeder og klip til alle universer i baggrunden, så de er klar, når man går ind i dem
      (async () => { for (const L of store.lists) if (!media[L.key] || Date.now() - media[L.key].at > 30 * 86400e3) await buildMedia(L); })();
    } finally { busy = false; }
  }

  // ---- billeder og klip til et univers: baggrundsbilleder og officielle trailere fra filmenes oplysninger ----
  const media = store.media || (store.media = {});
  const mbusy = new Set();
  async function buildMedia(L) {
    if (mbusy.has(L.key)) return;
    mbusy.add(L.key);
    try {
      const movies = L.items.filter(x => !x.missing && x.kind === 'movie');
      const pick = movies.length <= 12 ? movies : Array.from({ length: 12 }, (_, i) => movies[Math.floor(i * movies.length / 12)]); // spredt over hele tidslinjen
      const bd = [], tr = [];
      for (const x of pick) {
        try {
          const v = await P.vodInfo(P.MOVIE, x.id);
          if (v.backdrop && /^https:\/\/image\.tmdb\.org\//.test(v.backdrop)) bd.push(v.backdrop);
          if (v.trailer && /^[\w-]{11}$/.test(v.trailer)) tr.push({ id: v.trailer, name: x.name, year: x.year });
        } catch { }
        await new Promise(r => setTimeout(r, 1200));
      }
      media[L.key] = { at: Date.now(), bd, tr };
      save();
    } finally { mbusy.delete(L.key); }
  }
  /** Ét univers med billeder, klip og temamusik (bygges første gang i baggrunden). */
  function universe(key) {
    const L = lists(true).lists.find(l => l.key === key);
    if (!L) return null;
    const m = media[key];
    if (!m || Date.now() - m.at > 30 * 86400e3) buildMedia(L);
    const def = LISTS.find(l => l.key === key) || {};
    return { ...L, theme: def.theme || null, media: m ? { backdrops: m.bd, trailers: m.tr } : null, mediaBuilding: mbusy.has(key) };
  }

  /** all = også sæsontemaer uden for deres periode (til test). */
  function lists(all) {
    if (!store.lists || Date.now() - store.at > 24 * 3600e3 || (store.lists && store.lists.length < LISTS.length && !busy)) { P.buildIndex(); resolve(); }
    const now = new Date();
    const def = new Map(LISTS.map(l => [l.key, l]));
    const ls = (store.lists || []).filter(l => l.key !== 'jul' && def.has(l.key) && (!l.season || all || def.get(l.key).when(now)))
      .sort((a, b) => LISTS.indexOf(def.get(a.key)) - LISTS.indexOf(def.get(b.key)));
    return { building: busy, groups: U.GROUPS, jul: U.julSeason(now), lists: ls };
  }

  // ---- julekalender: lågen kan åbnes fra sin dato (1.–24. december; resten af julen kan alle åbnes) ----
  function julNow() {
    const d = new Date(), m = d.getMonth(), day = d.getDate();
    const year = m === 0 ? d.getFullYear() - 1 : d.getFullYear(); // i januar hører kalenderen til sidste år
    const open = m === 11 ? Math.min(24, day) : m === 0 && day <= 6 ? 24 : 0;
    return { year, today: m === 11 ? day : 0, open };
  }
  async function calendar(cal, demo) {
    const C2 = CALS[cal] || CALS.film;
    lists(); // sørger for, at filmene er fundet
    const { year, today, open } = julNow();
    const st = P.state[C2.state] && P.state[C2.state].year === year ? P.state[C2.state] : { year, opened: [] };
    let items = null, extra = {};
    if (C2 === CALS.film) {
      const L = (store.lists || []).find(l => l.key === 'jul');
      if (L) items = L.items;
    } else if (store.born) { // dagens afsnit
      extra.series = store.born;
      try {
        const info = await P.vodInfo(P.SERIES, store.born.id);
        const eps = Object.keys(info.seasons || {}).sort((a, b) => a - b).flatMap(k => info.seasons[k]);
        items = eps.slice(0, 24).map((ep, i) => ({ kind: 'episode', seriesId: store.born.id, id: ep.id, ext: ep.ext, name: 'Afsnit ' + (i + 1) + ': ' + ep.title, show: store.born.name, img: ep.img || info.poster || store.born.img, poster: info.poster || store.born.img, durMs: ep.durMs, lang: 'dansk' }));
      } catch { }
    }
    const doors = C2.order.map(day => {
      const opened = st.opened.includes(day) || !!demo;
      const it = items && items[day - 1];
      return { day, opened, canOpen: demo || day <= open, today: day === today, film: opened && it && !it.missing ? it : opened && it ? { missing: true, name: it.name, year: it.year } : null };
    });
    return { cal: cal === 'born' ? 'born' : 'film', name: C2.name, year, today, open, ready: !!items, doors, ...extra };
  }
  async function openDoor(day, cal) {
    const C2 = CALS[cal] || CALS.film;
    const { year, open } = julNow();
    day = +day;
    if (!(day >= 1 && day <= 24)) return { error: 'Den låge findes ikke.' };
    if (day > open) return { error: open ? 'Gave ' + day + ' må først pakkes ud ' + day + '. december.' : 'Gaverne må pakkes ud fra 1. december.' };
    if (!P.state[C2.state] || P.state[C2.state].year !== year) P.state[C2.state] = { year, opened: [] };
    if (!P.state[C2.state].opened.includes(day)) { P.state[C2.state].opened.push(day); P.save(); }
    return (await calendar(cal)).doors.find(d => d.day === day);
  }

  return { lists, universe, resolve, calendar, openDoor };
}

module.exports = { create, LISTS, english, key, keys, surround };
