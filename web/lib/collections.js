'use strict';
// Samlinger under Film: Marvel og Star Wars i historiens rækkefølge (med tv-serierne på tidslinjen) og Harry Potter.
// For hver titel vælges den bedste engelske udgave i kataloget: engelsk gruppe først, så 4K, så højeste bitrate.
const fs = require('fs');
const path = require('path');
const C = require('./core');

// [type, titel, år, alternative titler …]  –  m = film, s = serie
const LISTS = [
  {
    key: 'marvel', name: 'Marvel – tidslinjen', note: 'Marvel-filmene og -serierne i den rækkefølge, historien foregår.',
    items: [
      ['m', 'Captain America: The First Avenger', 2011],
      ['s', 'Marvel\'s Agent Carter', 2015, 'Agent Carter'],
      ['m', 'Captain Marvel', 2019],
      ['m', 'Iron Man', 2008],
      ['m', 'Iron Man 2', 2010],
      ['m', 'The Incredible Hulk', 2008],
      ['m', 'Thor', 2011],
      ['m', 'The Avengers', 2012, 'Marvel\'s The Avengers', 'Avengers Assemble'],
      ['m', 'Thor: The Dark World', 2013],
      ['m', 'Iron Man 3', 2013],
      ['m', 'Captain America: The Winter Soldier', 2014],
      ['m', 'Guardians of the Galaxy', 2014],
      ['m', 'Guardians of the Galaxy Vol. 2', 2017, 'Guardians of the Galaxy Vol 2', 'Guardians of the Galaxy Volume 2'],
      ['m', 'Avengers: Age of Ultron', 2015],
      ['m', 'Ant-Man', 2015],
      ['m', 'Captain America: Civil War', 2016],
      ['m', 'Black Widow', 2021],
      ['m', 'Black Panther', 2018],
      ['m', 'Spider-Man: Homecoming', 2017],
      ['m', 'Doctor Strange', 2016],
      ['m', 'Thor: Ragnarok', 2017],
      ['m', 'Ant-Man and the Wasp', 2018],
      ['m', 'Avengers: Infinity War', 2018],
      ['m', 'Avengers: Endgame', 2019],
      ['s', 'Loki', 2021],
      ['s', 'What If...?', 2021, 'What If'],
      ['s', 'WandaVision', 2021],
      ['s', 'The Falcon and the Winter Soldier', 2021],
      ['m', 'Shang-Chi and the Legend of the Ten Rings', 2021],
      ['m', 'Eternals', 2021],
      ['m', 'Spider-Man: Far From Home', 2019],
      ['m', 'Spider-Man: No Way Home', 2021],
      ['m', 'Doctor Strange in the Multiverse of Madness', 2022],
      ['s', 'Hawkeye', 2021],
      ['s', 'Moon Knight', 2022],
      ['m', 'Black Panther: Wakanda Forever', 2022],
      ['s', 'Echo', 2024],
      ['s', 'She-Hulk: Attorney at Law', 2022],
      ['s', 'Ms. Marvel', 2022],
      ['m', 'Thor: Love and Thunder', 2022],
      ['m', 'Werewolf by Night', 2022],
      ['m', 'The Guardians of the Galaxy Holiday Special', 2022],
      ['m', 'Ant-Man and the Wasp: Quantumania', 2023],
      ['m', 'Guardians of the Galaxy Vol. 3', 2023, 'Guardians of the Galaxy Vol 3', 'Guardians of the Galaxy Volume 3'],
      ['s', 'Secret Invasion', 2023],
      ['m', 'The Marvels', 2023],
      ['m', 'Deadpool & Wolverine', 2024, 'Deadpool and Wolverine'],
      ['s', 'Agatha All Along', 2024],
      ['m', 'Captain America: Brave New World', 2025],
      ['s', 'Daredevil: Born Again', 2025],
      ['m', 'Thunderbolts*', 2025, 'Thunderbolts', 'The New Avengers'],
      ['s', 'Ironheart', 2025],
      ['m', 'The Fantastic Four: First Steps', 2025, 'Fantastic Four: First Steps', 'The Fantastic 4: First Steps'],
      ['s', 'Wonder Man', 2026],
      ['m', 'Spider-Man: Brand New Day', 2026],
    ],
  },
  {
    key: 'starwars', name: 'Star Wars – tidslinjen', note: 'Film og serier i den rækkefølge, historien foregår.',
    items: [
      ['s', 'Star Wars: The Acolyte', 2024, 'The Acolyte'],
      ['s', 'Star Wars: Tales of the Jedi', 2022, 'Tales of the Jedi'],
      ['m', 'Star Wars: Episode I - The Phantom Menace', 1999, 'Star Wars: The Phantom Menace', 'The Phantom Menace', 'Star Wars Episode I'],
      ['m', 'Star Wars: Episode II - Attack of the Clones', 2002, 'Star Wars: Attack of the Clones', 'Attack of the Clones', 'Star Wars Episode II'],
      ['m', 'Star Wars: The Clone Wars', 2008],
      ['s', 'Star Wars: The Clone Wars', 2008, 'The Clone Wars'],
      ['m', 'Star Wars: Episode III - Revenge of the Sith', 2005, 'Star Wars: Revenge of the Sith', 'Revenge of the Sith', 'Star Wars Episode III'],
      ['s', 'Star Wars: Tales of the Empire', 2024, 'Tales of the Empire'],
      ['s', 'Star Wars: The Bad Batch', 2021, 'The Bad Batch'],
      ['m', 'Solo: A Star Wars Story', 2018, 'Solo'],
      ['s', 'Obi-Wan Kenobi', 2022],
      ['s', 'Andor', 2022, 'Star Wars: Andor'],
      ['s', 'Star Wars Rebels', 2014, 'Star Wars: Rebels'],
      ['m', 'Rogue One: A Star Wars Story', 2016, 'Rogue One'],
      ['m', 'Star Wars: Episode IV - A New Hope', 1977, 'Star Wars', 'Star Wars: A New Hope', 'A New Hope', 'Star Wars Episode IV'],
      ['m', 'Star Wars: Episode V - The Empire Strikes Back', 1980, 'The Empire Strikes Back', 'Star Wars: The Empire Strikes Back', 'Star Wars Episode V'],
      ['m', 'Star Wars: Episode VI - Return of the Jedi', 1983, 'Return of the Jedi', 'Star Wars: Return of the Jedi', 'Star Wars Episode VI'],
      ['s', 'The Mandalorian', 2019, 'Star Wars: The Mandalorian'],
      ['s', 'The Book of Boba Fett', 2021, 'Star Wars: The Book of Boba Fett'],
      ['m', 'The Mandalorian and Grogu', 2026, 'The Mandalorian & Grogu'],
      ['s', 'Star Wars: Skeleton Crew', 2024, 'Skeleton Crew'],
      ['s', 'Ahsoka', 2023, 'Star Wars: Ahsoka'],
      ['s', 'Star Wars Resistance', 2018, 'Star Wars: Resistance'],
      ['m', 'Star Wars: Episode VII - The Force Awakens', 2015, 'Star Wars: The Force Awakens', 'The Force Awakens', 'Star Wars Episode VII'],
      ['m', 'Star Wars: Episode VIII - The Last Jedi', 2017, 'Star Wars: The Last Jedi', 'The Last Jedi', 'Star Wars Episode VIII'],
      ['m', 'Star Wars: Episode IX - The Rise of Skywalker', 2019, 'Star Wars: The Rise of Skywalker', 'The Rise of Skywalker', 'Star Wars Episode IX'],
    ],
  },
  {
    key: 'potter', name: 'Harry Potter', note: 'Alle otte film i rækkefølge.',
    items: [
      ['m', 'Harry Potter and the Philosopher\'s Stone', 2001, 'Harry Potter and the Sorcerer\'s Stone'],
      ['m', 'Harry Potter and the Chamber of Secrets', 2002],
      ['m', 'Harry Potter and the Prisoner of Azkaban', 2004],
      ['m', 'Harry Potter and the Goblet of Fire', 2005],
      ['m', 'Harry Potter and the Order of the Phoenix', 2007],
      ['m', 'Harry Potter and the Half-Blood Prince', 2009],
      ['m', 'Harry Potter and the Deathly Hallows: Part 1', 2010, 'Harry Potter and the Deathly Hallows Part 1'],
      ['m', 'Harry Potter and the Deathly Hallows: Part 2', 2011, 'Harry Potter and the Deathly Hallows Part 2'],
    ],
  },
];

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
// Lågernes placering på kalenderen (fast blanding, så tallene ikke står i rækkefølge)
const DOOR_ORDER = [17, 5, 22, 11, 2, 19, 8, 14, 24, 1, 13, 20, 6, 9, 23, 3, 16, 10, 21, 4, 15, 12, 7, 18];

// titler sammenlignes uden tegnsætning: "Star Wars: Episode IV – A New Hope" = "star wars episode iv a new hope"
const key = s => C.norm(s).replace(/&/g, ' and ').replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
// katalogets navne har ofte "(2024) (US)" bagved eller "4K-ES-HDR - " foran
const keys = s => {
  const base = String(s || '').replace(/\s*\((\d{4}|[A-Z]{2,3})\)/g, '');
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

function create(P, DATA) {
  const FILE = path.join(DATA, 'cache', 'collections.json');
  let store = {};
  try { store = JSON.parse(fs.readFileSync(FILE, 'utf8')); } catch { }
  const save = () => { try { fs.mkdirSync(path.dirname(FILE), { recursive: true }); fs.writeFileSync(FILE, JSON.stringify(store)); } catch { } };
  let busy = false;
  const tech = store.tech || (store.tech = {}); // id -> {b: bitrate, at}

  function candidates(src, cats, it) {
    const [, title, year, ...rest] = it;
    const names = new Set([title, ...rest.filter(x => typeof x === 'string')].map(key));
    const out = [];
    for (const e of src) {
      if (!keys(e[1]).some(k => names.has(k))) continue;
      const y = +e[4];
      if (y && year && Math.abs(y - year) > 1) continue;
      const cat = cats.get(String(e[3])) || '';
      const en = english(cat);
      if (!en) continue;
      out.push({ e, cat, en, t: tier(en), k4: is4k(cat), sur: surround(cat), exact: y === year });
    }
    // findes udgaver med det rigtige år, droppes dem uden år (fx "The Grinch" kan være både 2000- og 2018-filmen)
    const sure = out.some(c => c.exact) ? out.filter(c => c.exact || +c.e[4]) : out;
    return sure.sort(rank);
  }
  // bedst først: mest engelsk, så surround-lyd, så 4K, så højeste bitrate
  const rank = (a, b) => b.t - a.t || b.sur - a.sur || b.k4 - a.k4 || (b.b || 0) - (a.b || 0) || b.en - a.en || b.exact - a.exact;

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
          const cs = candidates(movie ? P.search.movies : P.search.series, movie ? mcats : scats, it);
          if (!cs.length) { items.push({ missing: true, name: (it.find(x => x && typeof x === 'object') || {}).show || it[1], year: it[2], kind: movie ? 'movie' : 'series' }); continue; }
          let best = cs[0];
          if (movie) {
            // blandt de bedste udgaver (samme sprog-niveau) hentes bitrate, så den bedste kan vælges
            const top = cs.filter(c => c.t === best.t).slice(0, 5);
            for (const c of top) {
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
            if (best.b && best.b < 2000) {
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
          items.push({ kind: movie ? 'movie' : 'series', id: e[0], name: opt.show || it[1], note: opt.note, year: it[2], img: e[2], ext: e[5], cat: best.cat, quality: best.k4 ? '4K' : 'HD', surround: best.sur, bitrate: best.b || 0, lang: best.en === 2.75 ? 'engelsk, danske undertekster' : best.en >= 2.5 ? 'engelsk' : best.en === 2 ? 'flere lydspor' : 'original m. undertekster', versions: cs.length });
        }
        out.push({ key: L.key, name: L.name, note: L.note, items });
      }
      store.lists = out; store.at = Date.now();
      save();
    } finally { busy = false; }
  }

  function lists() {
    if (!store.lists || Date.now() - store.at > 24 * 3600e3) { P.buildIndex(); resolve(); }
    return { building: busy && !store.lists, lists: (store.lists || []).filter(l => l.key !== 'jul') };
  }

  // ---- julekalender: lågen kan åbnes fra sin dato (1.–24. december; resten af julen kan alle åbnes) ----
  function julNow() {
    const d = new Date(), m = d.getMonth(), day = d.getDate();
    const year = m === 0 ? d.getFullYear() - 1 : d.getFullYear(); // i januar hører kalenderen til sidste år
    const open = m === 11 ? Math.min(24, day) : m === 0 && day <= 6 ? 24 : 0;
    return { year, today: m === 11 ? day : 0, open };
  }
  function calendar(demo) {
    lists(); // sørger for, at filmene er fundet
    const { year, today, open } = julNow();
    const st = P.state.jul && P.state.jul.year === year ? P.state.jul : { year, opened: [] };
    const L = (store.lists || []).find(l => l.key === 'jul');
    const doors = DOOR_ORDER.map(day => {
      const opened = st.opened.includes(day) || !!demo;
      const it = L && L.items[day - 1];
      return { day, opened, canOpen: demo || day <= open, today: day === today, film: opened && it && !it.missing ? it : opened && it ? { missing: true, name: it.name, year: it.year } : null };
    });
    return { year, today, open, ready: !!L, doors };
  }
  function openDoor(day) {
    const { year, open } = julNow();
    day = +day;
    if (!(day >= 1 && day <= 24)) return { error: 'Den låge findes ikke.' };
    if (day > open) return { error: open ? 'Lågen må først åbnes ' + day + '. december.' : 'Kalenderen åbner 1. december.' };
    if (!P.state.jul || P.state.jul.year !== year) P.state.jul = { year, opened: [] };
    if (!P.state.jul.opened.includes(day)) { P.state.jul.opened.push(day); P.save(); }
    return calendar().doors.find(d => d.day === day);
  }

  return { lists, resolve, calendar, openDoor };
}

module.exports = { create, LISTS, english, key, keys, surround };
