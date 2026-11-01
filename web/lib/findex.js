'use strict';
// Søgeindeks bygget på forhånd (samme princip som MiniSearch): hvert ord peger på de dokumenter, der indeholder det.
// Et opslag finder ord, der starter med søgeordet (binær søgning i den sorterede ordliste), i stedet for at
// gennemgå hundredtusinder af titler. Indekset giver kandidater; den almindelige match-regel afgør til sidst.
const SPLIT = /[^\p{L}\p{N}]+/u;
const tokens = s => String(s || '').split(SPLIT).filter(Boolean);

/** texts: normaliserede tekster; dokument-nr = plads i listen. */
function build(texts) {
  const map = new Map();
  for (let i = 0; i < texts.length; i++) {
    for (const t of new Set(tokens(texts[i]))) {
      let p = map.get(t);
      if (!p) map.set(t, p = []);
      p.push(i);
    }
  }
  const terms = [...map.keys()].sort();
  return { size: texts.length, terms, posts: terms.map(t => Int32Array.from(map.get(t))) };
}

function lower(terms, w) {
  let lo = 0, hi = terms.length;
  while (lo < hi) { const m = (lo + hi) >> 1; if (terms[m] < w) lo = m + 1; else hi = m; }
  return lo;
}

/** Dokumenter med et ord, der starter med w (eller er præcis w). */
function docs(idx, w, exact) {
  const out = new Set();
  for (let i = lower(idx.terms, w); i < idx.terms.length && idx.terms[i].startsWith(w); i++) {
    if (exact && idx.terms[i] !== w) break;
    for (const d of idx.posts[i]) out.add(d);
  }
  return out;
}

/**
 * Kandidater for en forespørgsel fra core.query (alternativer af ord, "=" = helt ord), sorteret i katalogets rækkefølge.
 * null betyder: forespørgslen kan ikke slås op (fx kun tegn) – gennemgå det hele.
 */
function candidates(idx, q) {
  const all = new Set();
  for (const words of q) {
    let cur = null;
    for (const raw of words) {
      const whole = raw.startsWith('=');
      const parts = tokens(whole ? raw.slice(1) : raw);
      if (!parts.length) return null;
      parts.forEach((p, i) => {
        const last = i === parts.length - 1;
        const d = docs(idx, p, !last || whole); // ord inde i en frase skal være hele
        if (cur === null) cur = d;
        else for (const x of cur) if (!d.has(x)) cur.delete(x);
      });
      if (cur && !cur.size) break;
    }
    if (cur) for (const x of cur) all.add(x);
  }
  return Int32Array.from(all).sort();
}

module.exports = { build, candidates, tokens };
