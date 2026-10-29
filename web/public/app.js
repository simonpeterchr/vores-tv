'use strict';
// Vores TV – web. Samme opbygning som tv-appen: Søg · Hjem · Live-TV · Sport · Film · Serier.

const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const view = $('#view');
const pad = n => String(n).padStart(2, '0');
const hm = t => { const d = new Date(t); return pad(d.getHours()) + '.' + pad(d.getMinutes()); };
const prog = (a, b) => Math.max(0, Math.min(1, (Date.now() - a) / Math.max(1, b - a)));
const mins = ms => Math.max(0, Math.round(ms / 60000)) + ' min';
const dur = s => { s = Math.max(0, Math.floor(s)); const h = Math.floor(s / 3600), m = Math.floor(s / 60) % 60; return (h ? h + ':' + pad(m) : m) + ':' + pad(s % 60); };
const sleep = ms => new Promise(r => setTimeout(r, ms));
let gen = 0; // skifter ved hver ny side, så gamle opdateringer stopper

async function api(path, data) {
  const r = await fetch(path, data === undefined ? {} : { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
  const j = await r.json().catch(() => ({}));
  if (r.status === 401 && j.login) { signIn(); throw new Error('Log ind'); }
  if (!r.ok) throw new Error(j.error || 'Fejl ' + r.status);
  return j;
}
function toast(msg, ms = 3000) {
  const t = $('#toast'); t.textContent = msg; t.classList.remove('hidden');
  clearTimeout(toast.t); toast.t = setTimeout(() => t.classList.add('hidden'), ms);
}
const initials = n => esc((n || '?').replace(/[^\p{L}0-9 ]/gu, '').split(' ').filter(Boolean).slice(0, 2).map(w => w[0]).join('').toUpperCase() || '?');
// Mørke logoer (fx sort tekst på gennemsigtig baggrund) får en lys plade, så de kan ses på de mørke kort
const tones = new Map();
window.logoTone = img => {
  const set = d => img.parentNode && img.parentNode.classList.toggle('light', d);
  if (tones.has(img.src)) return set(tones.get(img.src));
  try {
    const c = document.createElement('canvas'); c.width = c.height = 24;
    const x = c.getContext('2d'); x.drawImage(img, 0, 0, 24, 24);
    const p = x.getImageData(0, 0, 24, 24).data;
    let sum = 0, n = 0, clear = 0;
    for (let i = 0; i < p.length; i += 4) { if (p[i + 3] < 128) { clear++; continue; } sum += (0.2126 * p[i] + 0.7152 * p[i + 1] + 0.0722 * p[i + 2]) / 255; n++; }
    const dark = n > 0 && clear > 40 && sum / n < 0.28; // kun gennemsigtige logoer med mørkt indhold
    tones.set(img.src, dark); set(dark);
  } catch { tones.set(img.src, false); }
};
const logo = (src, name, cls = '') => `<div class="logo ${cls} ${tones.get(src) ? 'light' : ''}">${src ? `<img src="${esc(src)}" alt="" loading="lazy"${/^https:\/\/cdn\.jsdelivr\.net\//.test(src) ? ' crossorigin="anonymous" onload="logoTone(this)"' : ''} onerror="this.replaceWith(Object.assign(document.createElement('span'),{className:'ini',textContent:this.dataset.i}))" data-i="${initials(name)}">` : `<span class="ini">${initials(name)}</span>`}</div>`;
const bar = p => `<div class="bar"><i style="width:${Math.round(p * 100)}%"></i></div>`;

// ---- navigation ----
const TABS = [['sog', 'Søg'], ['hjem', 'Hjem'], ['live', 'Live-TV'], ['sport', 'Sport'], ['film', 'Film'], ['serier', 'Serier']];
$('#nav').innerHTML = TABS.map(([k, n]) => `<a href="#/${k}" data-t="${k}">${n}</a>`).join('');
setInterval(() => { $('#clock').textContent = hm(Date.now()); }, 5000);
// lille indikator i toppen: forbindelser i brug hos udbyderen (klik = statussiden)
async function conPill() {
  try {
    const d = await fetch('/api/ops/status').then(r => r.ok ? r.json() : null);
    const el = $('#conpill'); if (!d || !el) return;
    const a = d.account || {};
    el.classList.remove('hidden');
    el.className = 'conpill' + (d.health.problem || a.ok === false ? ' bad' : a.max && a.active >= a.max ? ' full' : '');
    el.textContent = a.ok === false ? '● Udbyder nede' : a.max ? '● ' + a.active + '/' + a.max : '● Status';
  } catch { }
}
setInterval(conPill, 30000); setTimeout(conPill, 1500);
$('#clock').textContent = hm(Date.now());

let status = {}, me = {};
/** Login til selve Vores TV (kræves via den offentlige adresse). */
function signIn() {
  if ($('#signin')) return;
  gen++;
  if (Player.mode) Player.close();
  $('#nav').style.visibility = 'hidden';
  view.innerHTML = `<div class="box" style="max-width:440px;margin:8vh auto"><h1 style="margin-top:0">Log ind</h1><form class="form" id="signin">
    <label>Brugernavn<input name="name" autocomplete="username" autocapitalize="none" required></label>
    <label>Kode<input name="password" type="password" autocomplete="current-password" required></label>
    <div class="err" id="serr"></div><div><button class="btn">Log ind</button></div></form></div>`;
  $('#signin').onsubmit = async e => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.target));
    const r = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(f) });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) { $('#serr').textContent = j.error || 'Kunne ikke logge ind.'; return; }
    $('#nav').style.visibility = '';
    route();
  };
}
async function route() {
  const g = ++gen;
  const [tab, ...rest] = location.hash.replace(/^#\/?/, '').split('/');
  const t = tab || 'hjem';
  $$('#nav a').forEach(a => a.classList.toggle('on', a.dataset.t === t || (t === 'kamp' && a.dataset.t === 'sport') || (t === 'titel' && a.dataset.t === (rest[0] === 'series' ? 'serier' : 'film')) || (t === 'guide' && a.dataset.t === 'live')));
  if (t !== 'live' && Player.mode === 'mini') Player.close();
  try { [status, me] = await Promise.all([api('/api/status'), api('/api/me')]); } catch { return; }
  if (g !== gen) return;
  if (!status.loggedIn && t !== 'indstillinger') return Views.login(g);
  window.scrollTo(0, 0);
  const fn = { sog: Views.search, hjem: Views.home, live: Views.live, sport: Views.sport, kamp: Views.match, film: Views.vod, serier: Views.vod, titel: Views.title, indstillinger: Views.settings, status: Views.status, guide: Views.guide }[t] || Views.home;
  view.innerHTML = '<div class="spinner"></div>';
  try { await fn(g, rest.map(decodeURIComponent), t); }
  catch (e) { if (g === gen) view.innerHTML = `<div class="alert">${esc(e.message)}</div>`; }
}
window.addEventListener('hashchange', route);

// ======================================================================
// Chromecast (Chrome/Edge på https) og AirPlay (Safari)
// ======================================================================
const Cast = {
  ok: false,
  init() {
    const c = cast.framework.CastContext.getInstance();
    c.setOptions({ receiverApplicationId: chrome.cast.media.DEFAULT_MEDIA_RECEIVER_APP_ID, autoJoinPolicy: chrome.cast.AutoJoinPolicy.ORIGIN_SCOPED });
    this.ok = true;
    c.addEventListener(cast.framework.CastContextEventType.SESSION_STATE_CHANGED, e => {
      const S = cast.framework.SessionState;
      if (e.sessionState === S.SESSION_STARTED || e.sessionState === S.SESSION_RESUMED) { if (Player.ctx) Player.load(); }
      else if (e.sessionState === S.SESSION_ENDED) Player.castEnded();
    });
    if (Player.ctx) Player.topUi();
  },
  session() { return this.ok ? cast.framework.CastContext.getInstance().getCurrentSession() : null; },
  stop() { const s = this.session(); if (s) s.endSession(true); },
  async load() {
    const s = this.session(), c = Player.ctx; if (!s || !c) return;
    const { url } = await api('/api/cast?path=' + encodeURIComponent(Player.mediaPath()));
    const vod = c.type === 'vod';
    const mi = new chrome.cast.media.MediaInfo(url, vod ? (/mkv/i.test(c.ext) ? 'video/x-matroska' : 'video/mp4') : 'application/x-mpegurl');
    if (!vod) { mi.streamType = chrome.cast.media.StreamType.LIVE; mi.hlsSegmentFormat = chrome.cast.media.HlsSegmentFormat.TS; mi.hlsVideoSegmentFormat = chrome.cast.media.HlsVideoSegmentFormat.MPEG2_TS; }
    mi.metadata = new chrome.cast.media.GenericMediaMetadata();
    mi.metadata.title = $('#ptitle').textContent; mi.metadata.subtitle = $('#psub').textContent;
    const img = vod ? c.poster : Player.cur().logo;
    if (img) mi.metadata.images = [new chrome.cast.Image(img)];
    const req = new chrome.cast.media.LoadRequest(mi);
    if (vod) req.currentTime = Player.v.currentTime || c.start || 0;
    try { await s.loadMedia(req); }
    catch { Player.msg('Chromecast kunne ikke afspille den her stream.'); return; }
    Player.spin(false);
    Player.msg(`<div class="casting"><div class="ic">📺</div><div class="t">Spiller på ${esc(s.getCastDevice().friendlyName)}</div><button class="btn" onclick="Cast.stop()">Stop casting</button></div>`);
  },
};
window.Cast = Cast;
window.__onGCastApiAvailable = ok => { if (ok && window.cast && cast.framework) Cast.init(); };

// ======================================================================
// Afspiller (én for hele appen: lille i guiden, stor ellers)
// ======================================================================
const Player = {
  el: $('#player'), v: $('#video'), mode: null, ctx: null, hls: null, ts: null, slot: null, tried: null, saveT: 0, infoT: 0,

  /** ctx: {type:'live', ch, list, streams?, idx?, title?} eller {type:'vod', kind, id, ext, title, sub, poster, seriesId, start, next} */
  open(ctx, mode = 'full') {
    this.ctx = ctx;
    this.setMode(mode);
    this.load();
  },

  setMode(mode) {
    this.mode = mode;
    this.el.classList.remove('hidden', 'mini', 'full');
    this.el.classList.add(mode);
    if (mode === 'full') {
      Object.assign(this.el.style, { left: '', top: '', width: '', height: '' });
      document.body.style.overflow = 'hidden';
      this.el.focus();
      this.wake();
    } else { document.body.style.overflow = ''; this.panel(null); this.dockNow(); }
    this.v.controls = mode === 'full' && this.ctx && this.ctx.type === 'vod';
  },

  dock(slot) { this.slot = slot; if (this.mode === 'mini') this.dockNow(); },
  dockNow() {
    if (this.mode !== 'mini' || !this.slot || !this.slot.isConnected) return;
    const r = this.slot.getBoundingClientRect();
    Object.assign(this.el.style, { left: r.left + 'px', top: r.top + 'px', width: r.width + 'px', height: r.height + 'px' });
  },

  stopEngines() {
    if (this.hls) { this.hls.destroy(); this.hls = null; }
    if (this.ts) { try { this.ts.destroy(); } catch { } this.ts = null; }
    this.v.removeAttribute('src'); this.v.load();
  },

  close() {
    this.saveResume();
    if (Cast.session()) Cast.stop();
    this.stopEngines();
    this.ctx = null; this.mode = null;
    this.el.classList.add('hidden');
    document.body.style.overflow = '';
    if (document.fullscreenElement) document.exitFullscreen().catch(() => { });
    clearInterval(this.infoT);
    document.dispatchEvent(new Event('player-closed'));
  },

  msg(html) { const m = $('#pmsg'); m.innerHTML = html ? `<div>${html}</div>` : ''; m.classList.toggle('hidden', !html); },
  spin(on) { $('#pspin').classList.toggle('hidden', !on); },

  cur() { const c = this.ctx; return c.streams ? c.streams[c.idx].ch : c.ch; },

  mediaPath() {
    const c = this.ctx;
    return c.type === 'live' ? '/play/live/' + this.cur().id + '.m3u8' : '/play/' + c.kind + '/' + c.id + '.' + (c.ext || 'mp4');
  },

  castEnded() {
    if (!this.ctx) return;
    toast('Casting stoppet – fortsætter her');
    this.load();
  },

  async load() {
    const c = this.ctx;
    this.stopEngines(); this.msg(null); this.spin(true); this.panel(null);
    clearInterval(this.infoT);
    if (Cast.session()) { // spiller på et tv – ikke her (sparer en forbindelse hos udbyderen)
      if (c.type === 'live') { api('/api/seen', { id: this.cur().id }).catch(() => { }); this.liveInfo(); this.infoT = setInterval(() => this.liveInfo(), 30000); }
      this.topUi();
      return Cast.load();
    }
    if (this.airplay != null || (window.WebKitPlaybackTargetAvailabilityEvent && this.v.canPlayType('application/vnd.apple.mpegurl'))) { // Safari: eget afspilningslink, så AirPlay-modtageren kan hente det selv
      let url = this.mediaPath();
      try { url = (await api('/api/cast?path=' + encodeURIComponent(url))).url; } catch { }
      if (c !== this.ctx) return;
      if (c.type === 'live') { api('/api/seen', { id: this.cur().id }).catch(() => { }); this.liveInfo(); this.infoT = setInterval(() => this.liveInfo(), 30000); }
      else this.v.onloadedmetadata = () => { if (c.start > 0 && c.start < this.v.duration - 30) this.v.currentTime = c.start; };
      this.v.src = url; this.v.play().catch(() => { });
      this.topUi();
      return;
    }
    if (c.type === 'live') {
      const ch = this.cur();
      api('/api/seen', { id: ch.id }).catch(() => { });
      this.tried = 'hls';
      this.attachHls('/play/live/' + ch.id + '.m3u8');
      this.liveInfo(); this.infoT = setInterval(() => this.liveInfo(), 30000);
    } else {
      this.v.src = '/play/' + c.kind + '/' + c.id + '.' + (c.ext || 'mp4');
      this.v.onloadedmetadata = () => { if (c.start > 0 && c.start < this.v.duration - 30) this.v.currentTime = c.start; };
      this.v.play().catch(() => { });
      this.vodUi();
    }
    this.topUi();
  },

  attachHls(url) {
    const v = this.v;
    if (window.Hls && Hls.isSupported()) {
      const h = this.hls = new Hls({ maxBufferLength: 24, liveSyncDurationCount: 3, manifestLoadingMaxRetry: 1, levelLoadingMaxRetry: 3, fragLoadingMaxRetry: 4, enableWorker: true });
      h.on(Hls.Events.ERROR, (e, d) => {
        if (!d.fatal) return;
        if (d.type === Hls.ErrorTypes.MEDIA_ERROR && !h._recovered) { h._recovered = 1; h.recoverMediaError(); return; }
        const txt = d.networkDetails && d.networkDetails.responseText;
        let err = null; try { err = JSON.parse(txt).error; } catch { }
        this.fail(err, d.response && d.response.code);
      });
      h.on(Hls.Events.MANIFEST_PARSED, () => { v.play().catch(() => { v.muted = true; v.play().catch(() => { }); }); this.topUi(); });
      h.on(Hls.Events.AUDIO_TRACKS_UPDATED, () => this.topUi());
      h.on(Hls.Events.SUBTITLE_TRACKS_UPDATED, () => this.topUi());
      h.loadSource(url); h.attachMedia(v);
    } else if (v.canPlayType('application/vnd.apple.mpegurl')) {
      v.src = url; v.play().catch(() => { });
    } else this.fail('Browseren kan ikke afspille live-tv.');
  },

  attachTs(url) {
    if (!(window.mpegts && mpegts.getFeatureList().mseLivePlayback)) return false;
    this.tried = 'ts';
    const p = this.ts = mpegts.createPlayer({ type: 'mpegts', isLive: true, url }, { enableStashBuffer: true, liveBufferLatencyChasing: false });
    p.attachMediaElement(this.v); p.load();
    p.on(mpegts.Events.ERROR, () => this.fail(null));
    this.v.play().catch(() => { });
    return true;
  },

  async fail(err, code) {
    const c = this.ctx;
    if (!c) return;
    if (c.type === 'live' && this.tried === 'hls' && code !== 502 && code !== 403) { // kanalen findes måske kun som .ts
      this.stopEngines();
      if (this.attachTs('/play/live/' + this.cur().id + '.ts')) return;
    }
    if (c.type === 'live' && !err) { try { const r = await fetch('/play/live/' + this.cur().id + '.m3u8'); if (!r.ok) err = (await r.json()).error; } catch { } }
    if (c !== this.ctx) return;
    if (c.streams && c.idx < c.streams.length - 1) { // som på tv'et: prøv automatisk næste stream
      toast('Streamen svarer ikke – prøver den næste');
      c.idx++; this.load(); return;
    }
    this.spin(false);
    this.msg(esc(err || 'Kan ikke afspille lige nu.') + (this.mode === 'full' ? '<br><button class="btn" onclick="Player.load()">Prøv igen</button>' : ''));
  },

  // ---- brugerflade ----
  topUi() {
    const c = this.ctx; if (!c) return;
    const acts = [];
    if (c.type === 'live') {
      const ch = this.cur();
      $('#ptitle').textContent = c.title || ch.event || ch.name;
      $('#psub').textContent = c.title || ch.event ? ch.name : ch.group || '';
      if (c.streams && c.streams.length > 1) acts.push(['streams', 'Streams (' + c.streams.length + ')']);
      if (ch.angles) acts.push(['angles', '◂ Vinkler']);
      if (c.list && c.list.length > 1) acts.push(['chans', 'Kanaler']);
      acts.push(['fav', ch.fav ? '★' : '☆']);
    } else {
      $('#ptitle').textContent = c.title; $('#psub').textContent = c.sub || '';
      if (c.next) acts.push(['next', 'Næste afsnit ›']);
    }
    const h = this.hls;
    if (h && (h.audioTracks.length > 1 || h.subtitleTracks.length)) acts.push(['tracks', 'Lyd og tekst']);
    if (this.airplay) acts.push(['airplay', 'AirPlay']);
    if (me.home && !me.remote) acts.push(['tv', '📺 Spil på tv']);
    if (!Cast.session()) acts.push(['fs', '⛶']);
    $('#pactions').innerHTML = acts.map(([k, n]) => `<button class="pill" data-a="${k}">${esc(n)}</button>`).join('') + (Cast.ok ? '<google-cast-launcher class="pill" title="Chromecast"></google-cast-launcher>' : '');
  },

  async liveInfo() {
    const ch = this.cur(); if (!ch) return;
    let d; try { d = await api('/api/live/channel?id=' + ch.id); } catch { return; }
    if (!this.ctx || this.cur().id !== ch.id) return;
    Object.assign(ch, { fav: d.fav, angles: d.angles });
    const [now, next] = (d.epg || []).filter(p => p.stop > Date.now());
    $('#pbottom').innerHTML = `<div class="p-now">${logo(d.logo, d.name)}<div style="min-width:0;flex:1">
      ${now ? `<div class="t">${esc(now.title)}</div><div class="tm">${hm(now.start)}–${hm(now.stop)} · ${mins(now.stop - Date.now())} tilbage</div>${bar(prog(now.start, now.stop))}` : `<div class="t">${esc(d.event || d.name)}</div><div class="tm">${esc(d.group)}</div>`}
      ${next ? `<div class="nx">Derefter ${hm(next.start)} · ${esc(next.title)}</div>` : ''}</div></div>`;
    this.topUi();
  },

  vodUi() {
    $('#pbottom').innerHTML = '';
  },

  saveResume() {
    const c = this.ctx, v = this.v;
    if (!c || c.type !== 'vod' || !v.duration || !isFinite(v.duration) || v.currentTime < 5) return;
    api('/api/resume', { key: (c.kind === 'movie' ? 'm:' : 'e:') + c.id, kind: c.kind, id: c.id, title: c.title, sub: c.sub || '', poster: c.poster || '', ext: c.ext, seriesId: c.seriesId || '', pos: Math.round(v.currentTime * 1000), dur: Math.round(v.duration * 1000) }).catch(() => { });
  },

  panel(kind) {
    const p = $('#ppanel');
    if (!kind || p.dataset.k === kind) { p.classList.add('hidden'); p.dataset.k = ''; return; }
    p.dataset.k = kind; p.classList.remove('hidden');
    const c = this.ctx, cur = c.type === 'live' ? this.cur() : null;
    const it = (i, ch, name, sub, on) => `<button class="it ${on ? 'on' : ''}" data-i="${i}">${logo(ch.logo, ch.name)}<div style="min-width:0"><div class="n">${esc(name)}</div><div class="s">${esc(sub)}</div></div></button>`;
    if (kind === 'chans') {
      p.innerHTML = '<h3>Kanaler</h3>' + c.list.map((ch, i) => it(i, ch, ch.event || ch.name, ch.epg && ch.epg[0] ? ch.epg[0].title : ch.group, ch.id === cur.id)).join('');
      p.onclick = e => { const b = e.target.closest('[data-i]'); if (!b) return; c.ch = c.list[+b.dataset.i]; delete c.streams; delete c.title; this.load(); };
      const on = $('.on', p); if (on) on.scrollIntoView({ block: 'center' });
    } else if (kind === 'streams') {
      p.innerHTML = '<h3>Streams</h3>' + c.streams.map((s, i) => it(i, s.ch, s.ch.name, s.confidenceText + ' · ' + [s.quality, s.country].filter(Boolean).join(' · '), i === c.idx)).join('');
      p.onclick = e => { const b = e.target.closest('[data-i]'); if (!b) return; c.idx = +b.dataset.i; this.load(); };
    } else if (kind === 'angles') {
      p.innerHTML = '<h3>Kameravinkler</h3><div class="spinner"></div>';
      api('/api/angles?id=' + cur.id).then(d => {
        let sec = '', html = '<h3>Kameravinkler</h3>';
        d.angles.forEach((a, i) => { if (a.section !== sec) { sec = a.section; html += `<h4>${esc(sec)}</h4>`; } html += it(i, a.ch, a.label, a.sub, a.ch.id === cur.id); });
        p.innerHTML = d.angles.length ? html : '<h3>Kameravinkler</h3><p class="muted">Ingen ekstra vinkler fundet lige nu.</p>';
        p.onclick = e => { const b = e.target.closest('[data-i]'); if (!b) return; const a = d.angles[+b.dataset.i]; if (c.streams) c.streams[c.idx] = { ...c.streams[c.idx], ch: a.ch }; else c.ch = a.ch; this.load(); };
      });
    } else if (kind === 'tv') {
      p.innerHTML = '<h3>Spil på tv</h3><div class="spinner"></div>';
      api('/api/tvs').then(d => {
        if (p.dataset.k !== 'tv') return;
        p.innerHTML = '<h3>Spil på tv</h3>' + (d.tvs.length ? d.tvs.map((t, i) => `<button class="it noimg" data-t="${i}"><div class="n">📺 ${esc(t.name)}</div><div class="s">${esc(t.ip)}</div></button>`).join('')
          : '<p class="muted" style="margin:8px">Ingen tv\'er fundet. Vores TV skal være åben på tv\'et.</p>');
        p.onclick = async e => {
          const b = e.target.closest('[data-t]'); if (!b) return;
          const t = d.tvs[+b.dataset.t], c = this.ctx;
          const body = c.type === 'live'
            ? { ip: t.ip, kind: 'live', id: this.cur().id, ids: (c.list || [this.cur()]).slice(0, 60).map(x => x.id).join(','), title: c.title || '' }
            : { ip: t.ip, kind: c.kind, id: c.id, ext: c.ext || 'mp4', title: c.title, sub: c.sub || '', poster: c.poster || '', seriesId: c.seriesId || '', pos: Math.round((this.v.currentTime || c.start || 0) * 1000) };
          b.querySelector('.s').textContent = 'Sender …';
          try { await api('/api/tv/send', body); toast('Spiller nu på ' + t.name); this.close(); }
          catch (x) { b.querySelector('.s').textContent = x.message; }
        };
      }).catch(x => { p.innerHTML = '<h3>Spil på tv</h3><p class="muted" style="margin:8px">' + esc(x.message) + '</p>'; });
    } else if (kind === 'tracks') {
      const h = this.hls;
      let html = '<h3>Lyd</h3>' + h.audioTracks.map((t, i) => `<button class="it noimg ${i === h.audioTrack ? 'on' : ''}" data-a="${i}"><div class="n">${esc(t.name || t.lang || 'Spor ' + (i + 1))}</div></button>`).join('');
      html += '<h3 style="margin-top:20px">Undertekster</h3>' + [`<button class="it noimg ${h.subtitleTrack < 0 ? 'on' : ''}" data-s="-1"><div class="n">Fra</div></button>`].concat(h.subtitleTracks.map((t, i) => `<button class="it noimg ${i === h.subtitleTrack ? 'on' : ''}" data-s="${i}"><div class="n">${esc(t.name || t.lang || 'Spor ' + (i + 1))}</div></button>`)).join('');
      p.innerHTML = html;
      p.onclick = e => { const a = e.target.closest('[data-a]'), s = e.target.closest('[data-s]'); if (a) h.audioTrack = +a.dataset.a; if (s) h.subtitleTrack = +s.dataset.s; this.panel(null); };
    }
  },

  zap(d) {
    const c = this.ctx;
    if (!c || c.type !== 'live' || !c.list || c.list.length < 2) return;
    const i = c.list.findIndex(x => x.id === this.cur().id);
    c.ch = c.list[(i + d + c.list.length) % c.list.length]; delete c.streams; delete c.title;
    this.load(); this.wake();
  },

  wake() {
    this.el.classList.remove('idle');
    clearTimeout(this.idleT);
    this.idleT = setTimeout(() => { if (this.mode === 'full' && $('#ppanel').classList.contains('hidden') && !this.v.paused) this.el.classList.add('idle'); }, 3500);
  },

  back() {
    if (!$('#ppanel').classList.contains('hidden')) return this.panel(null);
    if (this.slot && this.slot.isConnected && this.ctx.type === 'live') return this.setMode('mini');
    this.close();
  },
};
window.Player = Player;

(() => {
  const P = Player, v = P.v;
  if (window.WebKitPlaybackTargetAvailabilityEvent) v.addEventListener('webkitplaybacktargetavailabilitychanged', e => { P.airplay = e.availability === 'available'; if (P.ctx) P.topUi(); });
  v.addEventListener('waiting', () => P.spin(true));
  v.addEventListener('playing', () => { P.spin(false); P.msg(null); });
  v.addEventListener('canplay', () => P.spin(false));
  v.addEventListener('error', async () => {
    const c = P.ctx; if (!c || c.type !== 'vod') return;
    let err = null;
    try { const r = await fetch(v.currentSrc || v.src, { headers: { Range: 'bytes=0-0' } }); if (!r.ok) err = (await r.json()).error; else r.body.cancel(); } catch { }
    P.spin(false);
    P.msg(esc(err || (/mkv|avi/i.test(c.ext) ? 'Filen er i ' + c.ext.toUpperCase() + '-format, som browseren ikke kan afspille. Prøv Chrome eller Edge – eller se den på tv\'et.' : 'Browseren kan ikke afspille denne fil.')));
  });
  v.addEventListener('timeupdate', () => {
    const c = P.ctx; if (!c || c.type !== 'vod') return;
    if (Date.now() - P.saveT > 10000) { P.saveT = Date.now(); P.saveResume(); }
  });
  v.addEventListener('ended', () => { const c = P.ctx; if (c && c.type === 'vod') { P.saveResume(); if (c.next) c.next(); } });
  P.el.addEventListener('mousemove', () => P.wake());
  P.el.addEventListener('click', e => {
    if (P.mode === 'mini') { P.setMode('full'); return; }
    if (e.target === v && P.ctx && P.ctx.type === 'live') { $('#ppanel').classList.contains('hidden') ? P.wake() : P.panel(null); }
  });
  $('#pback').onclick = e => { e.stopPropagation(); P.back(); };
  $('#pactions').onclick = async e => {
    const b = e.target.closest('[data-a]'); if (!b) return;
    e.stopPropagation();
    const a = b.dataset.a;
    if (a === 'fs') return document.fullscreenElement ? document.exitFullscreen() : P.el.requestFullscreen?.().catch(() => { });
    if (a === 'fav') { const ch = P.cur(); await api('/api/fav', { id: ch.id }); ch.fav = !ch.fav; P.topUi(); toast(ch.fav ? 'Tilføjet til favoritter' : 'Fjernet fra favoritter'); return; }
    if (a === 'next') return P.ctx.next();
    if (a === 'airplay') return v.webkitShowPlaybackTargetPicker();
    P.panel(a);
  };
  window.addEventListener('resize', () => P.dockNow());
  window.addEventListener('scroll', () => P.dockNow(), { passive: true });
  window.addEventListener('beforeunload', () => P.saveResume());
  document.addEventListener('keydown', e => {
    if (P.mode !== 'full' || e.target.matches('input,textarea')) return;
    const k = e.key;
    if (k === 'Escape' || k === 'Backspace') { e.preventDefault(); P.back(); }
    else if (k === 'ArrowUp' && P.ctx.type === 'live' && $('#ppanel').classList.contains('hidden')) { e.preventDefault(); P.zap(-1); }
    else if (k === 'ArrowDown' && P.ctx.type === 'live' && $('#ppanel').classList.contains('hidden')) { e.preventDefault(); P.zap(1); }
    else if (k === 'ArrowLeft' && P.ctx.type === 'live' && P.cur().angles) { e.preventDefault(); P.panel('angles'); }
    else if (k === 'ArrowLeft' && P.ctx.type === 'vod') { e.preventDefault(); v.currentTime -= 10; }
    else if (k === 'ArrowRight' && P.ctx.type === 'vod') { e.preventDefault(); v.currentTime += 30; }
    else if (k === ' ') { e.preventDefault(); v.paused ? v.play() : v.pause(); }
    else if (k === 'f') P.el.requestFullscreen?.().catch(() => { });
    else if (k === 'm') v.muted = !v.muted;
    else if (k === 'Enter' && P.ctx.type === 'live') P.panel(P.ctx.streams ? 'streams' : 'chans');
    P.wake();
  });
})();

// ======================================================================
// Kort
// ======================================================================
function gameCard(g, spoiler) {
  const sc = t => !spoiler && g.state !== 'pre' && t.score >= 0 ? `<span class="sc">${t.score}</span>` : '';
  const tm = t => `<div class="tm">${t.logo ? `<img src="${esc(t.logo)}" alt="" loading="lazy">` : '<span class="ph"></span>'}<span>${esc(t.shortName || t.name)}</span>${sc(t)}</div>`;
  const st = g.state === 'in' ? `<span class="live-dot"></span>${esc(spoiler ? 'Live' : g.status)}` : esc(g.status);
  const ft = g.streams == null ? '' : g.streams ? `<div class="ft ok">${g.streams} ${g.streams === 1 ? 'stream' : 'streams'}</div>` : '<div class="ft">Ingen streams endnu</div>';
  const body = g.title ? `<div class="ev">${esc(g.title)}</div><div class="muted">${esc(g.sub || '')}</div>` : tm(g.home) + tm(g.away);
  return `<a class="game ${g.state === 'in' ? 'live' : ''} ${g.mine ? 'mine' : ''}" href="#/kamp/${encodeURIComponent(g.key)}"><div class="hd"><span>${esc(g.leagueName || g.sportName)}</span><span class="st">${st}</span></div>${body}${ft}</a>`;
}
function chCard(ch, i) {
  const now = ch.epg && ch.epg[0];
  return `<button class="ch-card" data-ch="${i}">${logo(ch.logo, ch.name)}<div class="meta"><div class="n">${esc(ch.event || ch.name)}</div><div class="s">${esc(now ? now.title : ch.group)}</div>${now ? bar(prog(now.start, now.stop)) : ''}</div></button>`;
}
function posterCard(it, kind) {
  const p = it.dur ? it.pos / it.dur : 0;
  const href = it.kind === 'series' || kind === 'series' ? '#/titel/series/' + encodeURIComponent(it.seriesId || it.id) : '#/titel/movie/' + encodeURIComponent(it.id);
  return `<a class="poster" href="${href}"><div class="img">${esc(it.name || it.title)}${it.poster || it.img ? `<img src="${esc(it.poster || it.img)}" alt="" loading="lazy" onerror="this.remove()">` : ''}${p ? bar(p) : ''}</div><div class="n">${esc(it.title || it.name)}</div><div class="s">${esc(it.sub || it.year || '')}</div></a>`;
}

// ======================================================================
// Sider
// ======================================================================
const Views = {};

Views.login = async (g) => {
  view.innerHTML = `<h1>Velkommen${me.home ? ' til Vores TV' : ', ' + esc(me.user)}</h1>
  ${me.home && !me.remote ? '' : '<div class="box"><p>Log ind med dit eget IPTV-abonnement. Oplysningerne gemmes kun på serveren og vises aldrig igen.</p></div><!--'}
  <div class="box"><p><b>Nemmest:</b> Åbn telefon-siden på et af tv'erne, der allerede er logget ind, og tryk <b>Del login</b>. Vælg <b>${esc(status.name || 'denne computer')}</b> på listen.</p><p class="muted">Siden opdaterer sig selv, når loginet er modtaget.</p></div>${me.home && !me.remote ? '' : '-->'}
  <div class="box"><p><b>${me.home && !me.remote ? 'Eller log ind her' : 'IPTV-login'}</b> med oplysningerne fra udbyderen:</p>${loginForm()}</div>
  ${me.admin ? '<h2 id="brugere">Brugere</h2><div class="box" id="users"><div class="spinner"></div></div>' : ''}`;
  bindLogin();
  if (me.admin) drawUsers();
  while (g === gen) { await sleep(3000); try { if ((await api('/api/status')).loggedIn) { toast('Logget ind'); location.hash = '#/hjem'; return route(); } } catch { } }
};
function loginForm() {
  return `<form class="form" id="login">
    <label>Server-adresse eller M3U-link<input name="server" autocomplete="off" placeholder="http://…" required></label>
    <label>Brugernavn<input name="user" autocomplete="off"></label>
    <label>Kode<input name="pass" type="password" autocomplete="off"></label>
    <label>Reserve-adresser (valgfrit)<textarea name="alts" rows="2" placeholder="Fra udbyderens besked"></textarea></label>
    <div class="err" id="lerr"></div><div><button class="btn">Log ind</button></div></form>`;
}
function bindLogin() {
  $('#login').onsubmit = async e => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.target));
    $('#lerr').textContent = 'Tjekker …';
    try { await api('/api/login', f); toast('Logget ind – henter kanaler'); location.hash = '#/hjem'; route(); }
    catch (x) { $('#lerr').textContent = x.message; }
  };
}

// ---- Hjem ----
Views.home = async (g) => {
  let d;
  const draw = () => {
    const alert = d.problem ? `<div class="alert">${esc(d.problem)}</div>` : d.switched ? `<div class="alert info">${esc(d.switched)}</div>` : '';
    let hero = '';
    if (d.hero && d.hero.type === 'game') {
      const x = d.hero.game, s = d.hero.stream;
      hero = `<section class="hero"><div class="z"><div class="k"><span class="live-dot"></span>Live nu · ${esc(x.leagueName || x.sportName)}</div>
        <h1>${esc(x.title || (x.home.shortName + ' – ' + x.away.shortName))}</h1><div class="s">${esc(x.status)} · ${esc(s.ch.name)}</div>
        <div class="acts"><button class="btn" id="hplay">▶ Se kampen</button><a class="btn ghost" href="#/kamp/${encodeURIComponent(x.key)}">Alle streams</a></div></div>
        ${logo(x.home.logo || s.ch.logo, x.home.name || s.ch.name, 'bg')}</section>`;
    } else if (d.hero) {
      const ch = d.hero.ch, now = ch.epg && ch.epg[0];
      hero = `<section class="hero"><div class="z"><div class="k">Sidst set</div><h1>${esc(ch.event || ch.name)}</h1>
        <div class="s">${now ? esc(now.title) + ' · ' + hm(now.start) + '–' + hm(now.stop) : esc(ch.group)}</div>
        <div class="acts"><button class="btn" id="hplay">▶ Se videre</button><a class="btn ghost" href="#/live">Guide</a></div></div>${logo(ch.logo, ch.name, 'bg')}</section>`;
    } else hero = `<section class="hero"><div class="z"><div class="k">Vores TV</div><h1>God fornøjelse</h1><div class="s">Find en kanal i guiden, eller se hvad der er af sport.</div><div class="acts"><a class="btn" href="#/live">Live-TV</a><a class="btn ghost" href="#/sport">Sport</a></div></div></section>`;
    view.innerHTML = alert + hero +
      (d.games.length ? `<h2>Dine kampe</h2><div class="row">${d.games.map(x => gameCard(x, false)).join('')}</div>` : '') +
      (d.channels.length ? `<h2>${esc(d.channelsTitle)}</h2><div class="row" id="hch">${d.channels.map(chCard).join('')}</div>` : '') +
      (d.cont.length ? `<h2>Fortsæt</h2><div class="row">${d.cont.map(r => posterCard(r)).join('')}</div>` : '') +
      (d.sport.length ? `<h2>Sport i dag</h2><div class="row" id="hsp">${d.sport.map((h, i) => `<button class="ch-card" data-i="${i}">${logo(h.img, h.name)}<div class="meta"><div class="n">${esc(h.name)}</div><div class="s">${esc(h.sub)}</div>${h.start <= Date.now() ? bar(prog(h.start, h.stop)) : ''}</div></button>`).join('')}</div>` : '');
    const hp = $('#hplay');
    if (hp) hp.onclick = () => d.hero.type === 'game' ? playGame(d.hero.game.key) : Player.open({ type: 'live', ch: d.hero.ch, list: d.channels });
    const hc = $('#hch'); if (hc) hc.onclick = e => { const b = e.target.closest('[data-ch]'); if (b) Player.open({ type: 'live', ch: d.channels[+b.dataset.ch], list: d.channels }); };
    const hs = $('#hsp'); if (hs) hs.onclick = async e => { const b = e.target.closest('[data-i]'); if (!b) return; const ch = await api('/api/live/channel?id=' + d.sport[+b.dataset.i].chId); Player.open({ type: 'live', ch, list: [ch] }); };
  };
  let sig = '';
  for (let i = 0; g === gen; i++) {
    d = await api('/api/home');
    if (g !== gen) return;
    const s = JSON.stringify([d.hero && (d.hero.game ? d.hero.game.key + d.hero.game.status : d.hero.ch.id), d.games.map(x => x.key + x.status + x.streams), d.channels.map(c => c.id + (c.epg[0] || {}).title), d.cont.length, d.sport.map(h => h.name), d.problem, d.switched]);
    if (s !== sig && !(i > 0 && document.activeElement && view.contains(document.activeElement))) { sig = s; draw(); }
    await sleep(i < 4 ? 4000 : 30000); // først hurtigt, mens data kommer ind
  }
};

async function playGame(key) {
  const d = await api('/api/sport/game?key=' + encodeURIComponent(key));
  if (!d.streams.length) return toast('Ingen streams fundet til kampen endnu');
  const x = d.game;
  Player.open({ type: 'live', streams: d.streams, idx: 0, title: x.title || x.home.shortName + ' – ' + x.away.shortName, list: d.streams.map(s => s.ch) });
}

// ---- Live-TV ----
Views.live = async (g, [cat]) => {
  const gr = await api('/api/live/groups');
  if (g !== gen) return;
  const groups = [{ id: 'fav', name: 'Favoritter' }, { id: 'recent', name: 'Senest sete' }].concat(gr.groups.filter(k => k.shown));
  if (!gr.groups.length) { view.innerHTML = `<div class="empty">${status.loading || !status.loaded ? 'Henter kanaler fra udbyderen …' : esc(status.error || 'Ingen kanaler.')}</div>`; await sleep(3000); if (g === gen) route(); return; }
  cat = cat || localStorage.getItem('vt.cat') || (gr.shown[0] || 'fav');
  try { localStorage.setItem('vt.cat', cat); } catch { }
  view.innerHTML = `<div class="livetv">
    <div class="groups">${groups.map(k => `<button data-g="${esc(k.id)}" class="${k.id === cat ? 'on' : ''}">${esc(k.name)}${k.count ? `<small>${k.count}</small>` : ''}</button>`).join('')}<a class="pill" style="margin-top:12px" href="#/guide/${encodeURIComponent(cat)}">▦ Guide-gitter</a><a class="pill" style="margin-top:8px" href="#/indstillinger/grupper">Vælg grupper</a></div>
    <div class="groups-m"><a class="pill" href="#/guide/${encodeURIComponent(cat)}">▦ Gitter</a>${groups.map(k => `<button class="pill ${k.id === cat ? 'on' : ''}" data-g="${esc(k.id)}">${esc(k.name)}</button>`).join('')}</div>
    <div class="chlist" id="chl"><div class="spinner"></div></div>
    <div class="side"><div class="slot" id="slot">Vælg en kanal</div><div class="info" id="info"></div><div class="hint">↑↓ vælger uden at skifte · Enter/klik viser i den lille skærm · igen for fuld skærm</div></div></div>`;
  $$('[data-g]').forEach(b => b.onclick = () => { location.hash = '#/live/' + encodeURIComponent(b.dataset.g); });
  Player.dock($('#slot'));
  let chs = [], sel = 0;
  const playingId = () => Player.ctx && Player.ctx.type === 'live' && Player.mode ? Player.cur().id : null;
  const row = (ch, i) => {
    const [now, next] = ch.epg || [];
    return `<button class="chrow ${i === sel ? 'sel' : ''} ${ch.id === playingId() ? 'playing' : ''}" data-i="${i}"><div class="num">${ch.num}</div>${logo(ch.logo, ch.name)}
      <div style="min-width:0"><div class="n">${esc(ch.event || ch.name)}${ch.fav ? '<span class="star">★</span>' : ''}</div>
      ${now ? `<div class="p"><b>${hm(now.start)}</b>${esc(now.title)}</div>${bar(prog(now.start, now.stop))}${next ? `<div class="nx">${hm(next.start)} ${esc(next.title)}</div>` : ''}` : `<div class="p">${esc(ch.event ? ch.name : (ch.eventStart ? '' : ch.group))}</div>`}</div></button>`;
  };
  const drawList = () => {
    const l = $('#chl'); if (!l) return;
    l.innerHTML = chs.length ? chs.map(row).join('') : `<div class="empty">${cat === 'fav' ? 'Ingen favoritter endnu. Åbn en kanal og tryk ☆.' : 'Ingen kanaler.'}</div>`;
  };
  const drawInfo = () => {
    const ch = chs[sel], el = $('#info'); if (!ch || !el) return;
    const [now, ...rest] = ch.epg || [];
    el.innerHTML = `<div class="t">${esc(now ? now.title : ch.event || ch.name)}</div><div class="tm">${esc(ch.name)}${now ? ' · ' + hm(now.start) + '–' + hm(now.stop) : ''}</div>
      ${now && now.desc ? `<div class="d">${esc(now.desc)}</div>` : ''}
      <div class="acts"><button class="btn" id="iplay">▶ Se</button><button class="btn ghost" id="ifull">Fuld skærm</button><button class="btn ghost" id="ifav">${ch.fav ? '★ Favorit' : '☆ Favorit'}</button></div>
      ${rest.length ? `<div class="upnext">${rest.map(p => `<div><b>${hm(p.start)}</b>${esc(p.title)}</div>`).join('')}</div>` : ''}`;
    $('#iplay').onclick = () => choose(sel);
    $('#ifull').onclick = () => { choose(sel); Player.setMode('full'); };
    $('#ifav').onclick = async () => { await api('/api/fav', { id: ch.id }); ch.fav = !ch.fav; drawList(); drawInfo(); };
  };
  const select = (i, scroll) => {
    sel = Math.max(0, Math.min(chs.length - 1, i));
    $$('.chrow').forEach(b => b.classList.toggle('sel', +b.dataset.i === sel));
    if (scroll) { const b = $(`.chrow[data-i="${sel}"]`); if (b) b.scrollIntoView({ block: 'nearest' }); }
    drawInfo();
  };
  const choose = i => {
    const ch = chs[i];
    if (Player.ctx && Player.mode && playingId() === ch.id) { Player.setMode('full'); return; } // anden gang: fuld skærm
    select(i);
    Player.open({ type: 'live', ch, list: chs }, 'mini');
    Player.dock($('#slot'));
    $$('.chrow').forEach(b => b.classList.toggle('playing', +b.dataset.i === i));
  };
  $('#chl').onclick = e => { const b = e.target.closest('[data-i]'); if (b) choose(+b.dataset.i); };
  $('#chl').ondblclick = e => { const b = e.target.closest('[data-i]'); if (b) { choose(+b.dataset.i); Player.setMode('full'); } };
  const onKey = e => {
    if (g !== gen) return document.removeEventListener('keydown', onKey);
    if (Player.mode === 'full' || e.target.matches('input,textarea')) return;
    if (e.key === 'ArrowDown') { e.preventDefault(); select(sel + 1, true); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); select(sel - 1, true); }
    else if (e.key === 'Enter') { e.preventDefault(); choose(sel); }
  };
  document.addEventListener('keydown', onKey);
  // Hold den viste kanal markeret, når man zapper i fuld skærm og vender tilbage
  const onClose = () => { if (g === gen) drawList(); };
  document.addEventListener('player-closed', onClose, { once: true });
  for (let i = 0; g === gen; i++) {
    const d = await api('/api/live/channels?cat=' + encodeURIComponent(cat));
    if (g !== gen) return;
    const keep = chs[sel] && chs[sel].id;
    chs = d.channels;
    if (keep) sel = Math.max(0, chs.findIndex(c => c.id === keep));
    drawList(); if (!$('#info').innerHTML || i === 0 || keep) drawInfo();
    if (i === 0 && chs.length) { const pid = playingId(); if (pid) { const j = chs.findIndex(c => c.id === pid); if (j >= 0) select(j, true); } }
    await sleep(d.pending ? 3000 : 60000);
  }
};

// ---- Sport ----
Views.sport = async (g, [f]) => {
  f = f || localStorage.getItem('vt.sport') || 'mine';
  try { localStorage.setItem('vt.sport', f); } catch { }
  let sig = '';
  for (let i = 0; g === gen; i++) {
    const d = await api('/api/sport/games?f=' + f);
    if (g !== gen) return;
    const pills = [['mine', 'Mine'], ['live', 'Live nu']].concat(d.sports).concat([['all', 'Alt']]);
    const s = JSON.stringify(d.games.map(x => x.key + x.status + x.streams + (x.home.score)));
    if (s !== sig) {
      sig = s;
      const byDay = new Map();
      for (const x of d.games) { const k = x.state === 'in' ? 'Live nu' : x.state === 'post' ? 'Spillet' : dayLabel(x.start); if (!byDay.has(k)) byDay.set(k, []); byDay.get(k).push(x); }
      view.innerHTML = `<div class="pills">${pills.map(([k, n]) => `<a class="pill ${k === f ? 'on' : ''}" href="#/sport/${k}">${esc(n)}</a>`).join('')}<button class="pill ${d.spoiler ? 'on' : ''}" id="spoil">Spoilerfri</button><a class="pill" href="#/indstillinger/sport">Favoritter</a></div>` +
        (d.games.length ? [...byDay].map(([k, l]) => `<h2>${esc(k)}</h2><div class="games">${l.map(x => gameCard(x, d.spoiler)).join('')}</div>`).join('') : `<div class="empty">${i < 2 ? 'Henter kampprogram …' : 'Ingen kampe her de næste dage.'}</div>`);
      $('#spoil').onclick = async () => { await api('/api/sport/prefs', { spoiler: !d.spoiler }); sig = ''; route(); };
    }
    await sleep(i < 5 ? 3000 : 30000);
  }
};
function dayLabel(t) {
  const a = new Date(); a.setHours(0, 0, 0, 0);
  const b = new Date(t); b.setHours(0, 0, 0, 0);
  const n = Math.round((b - a) / 864e5);
  if (n === 0) return 'I dag'; if (n === 1) return 'I morgen';
  const s = new Date(t).toLocaleDateString('da-DK', { weekday: 'long', day: 'numeric', month: 'long' });
  return s[0].toUpperCase() + s.slice(1);
}

Views.match = async (g, [key]) => {
  let sig = '';
  for (let i = 0; g === gen; i++) {
    const d = await api('/api/sport/game?key=' + encodeURIComponent(key));
    if (g !== gen) return;
    const x = d.game, sp = d.spoiler && x.state !== 'pre';
    const s = JSON.stringify([x.status, x.home.score, x.away.score, d.streams.map(s => s.ch.id), d.table.length, d.round.length]);
    if (s !== sig) {
      sig = s;
      const score = x.state !== 'pre' && x.home.score >= 0 && !sp ? `<div class="score">${x.home.score} – ${x.away.score}</div>` : `<div class="score muted">–</div>`;
      const hd = x.title ? `<div class="ev">${esc(x.title)}</div><div class="muted">${esc(x.sub || '')}</div>`
        : `<div class="vs"><div class="t">${x.home.logo ? `<img src="${esc(x.home.logo)}" alt="">` : ''}${esc(x.home.name)}</div>${score}<div class="t">${x.away.logo ? `<img src="${esc(x.away.logo)}" alt="">` : ''}${esc(x.away.name)}</div></div>`;
      const teams = [x.home.name, x.away.name].filter(Boolean).map(n => n.toLowerCase());
      view.innerHTML = `<div class="match-hd"><div class="lg">${esc(x.leagueName || x.sportName)}</div>${hd}<div class="st">${x.state === 'in' ? '<span class="live-dot"></span>' : ''}${esc(sp && x.state === 'in' ? 'Live' : x.status)}${x.state === 'pre' ? ' · ' + esc(dayLabel(x.start)) : ''}</div></div>
        <div class="two"><div><h2>Streams <small>${d.streams.length ? 'bedste først · dansk foretrækkes' : ''}</small></h2>
        ${d.streams.length ? `<div class="streams">${d.streams.map((s, j) => `<button class="stream" data-i="${j}">${logo(s.ch.logo, s.ch.name)}<div style="min-width:0"><div class="n">${esc(s.ch.event || s.ch.name)}</div><div class="w">${esc(s.ch.event ? s.ch.name + ' · ' : '')}${esc(s.why)}</div></div><div class="chips"><span class="chip c${s.confidence}">${esc(s.confidenceText)}</span>${s.quality ? `<span class="chip">${esc(s.quality)}</span>` : ''}${s.country ? `<span class="chip">${esc(s.country)}</span>` : ''}</div></button>`).join('')}</div>`
          : `<div class="empty">${d.pending ? 'Leder i programguiden …' : 'Ingen streams fundet endnu. De dukker typisk op tættere på kampstart.'}</div>`}</div>
        <div>${d.table.length ? `<h2>Tabel</h2><table class="tbl"><tr><th>#</th><th>Hold</th><th>K</th><th>+/-</th><th>P</th></tr>${d.table.map(r => `<tr class="${teams.some(t => t.includes(r.team.toLowerCase()) || r.team.toLowerCase().includes(t)) ? 'me' : ''}"><td>${r.rank}</td><td>${r.logo ? `<img src="${esc(r.logo)}" alt="" loading="lazy">` : ''}${esc(r.team)}</td><td>${r.played}</td><td>${esc(r.diff)}</td><td>${r.points}</td></tr>`).join('')}</table>` : ''}
        ${d.round.length ? `<h2>${x.title ? 'Weekendens program' : 'Resten af runden'}</h2><div class="games">${d.round.map(r => gameCard(r, d.spoiler)).join('')}</div>` : ''}</div></div>`;
      $$('.stream').forEach(b => b.onclick = () => Player.open({ type: 'live', streams: d.streams, idx: +b.dataset.i, title: x.title || x.home.shortName + ' – ' + x.away.shortName, list: d.streams.map(s => s.ch) }));
    }
    await sleep(d.pending || i < 3 ? 3000 : 30000);
  }
};

// ---- Film og serier ----
Views.vod = async (g, args, tab) => {
  const kind = tab === 'film' ? 'movie' : 'series';
  let gr;
  for (let i = 0; i < 40; i++) { gr = await api('/api/vod/groups?kind=' + kind); if (g !== gen) return; if (!gr.loading) break; await sleep(1000); }
  const shown = gr.groups.filter(k => k.shown).sort((a, b) => gr.shown.indexOf(a.id) - gr.shown.indexOf(b.id));
  const cont = await api('/api/continue?kind=' + kind);
  if (g !== gen) return;
  view.innerHTML = `<div class="pills"><a class="pill" href="#/indstillinger/${kind === 'movie' ? 'film' : 'serier'}">Vælg grupper</a><a class="pill" href="#/sog">Søg i alle ${kind === 'movie' ? 'film' : 'serier'}</a></div>` +
    (cont.length ? `<h2>Fortsæt</h2><div class="row">${cont.map(r => posterCard(r, kind)).join('')}</div>` : '') +
    shown.map(k => `<h2>${esc(k.name)}</h2><div class="row" data-cat="${esc(k.id)}"><div class="spinner" style="margin:40px"></div></div>`).join('') +
    (shown.length ? '' : '<div class="empty">Ingen grupper valgt.</div>');
  // hent rækkerne efterhånden (de øverste først)
  const rows = $$('[data-cat]');
  await Promise.all([0, 1, 2].map(async w => {
    for (let r = w; r < rows.length; r += 3) {
      const el = rows[r];
      for (let t = 0; t < 60 && g === gen; t++) {
        const d = await api('/api/vod/items?kind=' + kind + '&cat=' + encodeURIComponent(el.dataset.cat) + '&max=60');
        if (!d.loading) { el.innerHTML = d.items.length ? d.items.map(it => posterCard(it, kind)).join('') : '<div class="empty">Tom gruppe</div>'; break; }
        await sleep(1000);
      }
    }
  }));
};

Views.title = async (g, [kind, id]) => {
  const d = await api('/api/vod/info?kind=' + kind + '&id=' + encodeURIComponent(id));
  if (g !== gen) return;
  const meta = [d.year, d.genre, d.durMs ? Math.round(d.durMs / 60000) + ' min' : '', d.rating && d.rating !== '0' ? '★ ' + (+d.rating).toFixed(1).replace('.', ',') : ''].filter(Boolean);
  const credits = (d.director ? 'Instruktør: ' + d.director : '') + (d.director && d.cast ? '<br>' : '') + (d.cast ? 'Medvirkende: ' + esc(d.cast) : '');
  if (kind === 'movie') {
    const r = d.resume['m:' + id];
    const play = start => Player.open({ type: 'vod', kind, id, ext: d.ext, title: d.name, sub: d.year, poster: d.poster, start });
    view.innerHTML = head(d, meta, `${r ? `<button class="btn" id="tres">▶ Fortsæt (${mins(r.dur - r.pos)} tilbage)</button><button class="btn ghost" id="tplay">Forfra</button>` : '<button class="btn" id="tplay">▶ Afspil</button>'}`) + (credits ? `<div class="credits">${credits}</div>` : '');
    $('#tplay').onclick = () => play(0);
    if (r) $('#tres').onclick = () => play(r.pos / 1000);
    return;
  }
  const seasons = Object.keys(d.seasons || {}).map(Number).sort((a, b) => a - b);
  const done = new Set(d.done);
  const all = seasons.flatMap(s => d.seasons[s]);
  const last = Object.values(d.resume).sort((a, b) => b.when - a.when)[0];
  let season = last ? (all.find(e => e.id === last.id) || {}).season || seasons[0] : seasons[0];
  const playEp = (ep, start) => {
    const i = all.indexOf(ep), nx = all[i + 1];
    Player.open({ type: 'vod', kind: 'series', id: ep.id, ext: ep.ext, title: d.name, sub: 'S' + ep.season + ' · A' + ep.num + ' · ' + ep.title, poster: d.poster, seriesId: id, start, next: nx ? () => { Player.saveResume(); playEp(nx, 0); } : null });
  };
  const firstUnseen = all.find(e => !done.has('e:' + e.id)) || all[0];
  const res = last && all.find(e => e.id === last.id);
  view.innerHTML = head(d, meta, `${res ? `<button class="btn" id="tres">▶ Fortsæt S${res.season} A${res.num}</button>` : firstUnseen ? `<button class="btn" id="tfirst">▶ Afspil S${firstUnseen.season} A${firstUnseen.num}</button>` : ''}`) +
    (credits ? `<div class="credits">${credits}</div>` : '') +
    `<div class="tabs" style="margin-top:26px">${seasons.map(s => `<button class="pill" data-s="${s}">Sæson ${s}</button>`).join('')}</div><div class="eps" id="eps"></div>`;
  const drawEps = () => {
    $$('[data-s]').forEach(b => b.classList.toggle('on', +b.dataset.s === season));
    const eps = d.seasons[season] || [];
    $('#eps').innerHTML = eps.map((e, i) => { const r = d.resume['e:' + e.id]; return `<button class="ep" data-e="${i}"><div class="th" style="background-image:url('${esc(e.img || d.backdrop || d.poster)}')">${r ? bar(r.pos / r.dur) : ''}</div><div style="min-width:0"><div class="n">${e.num}. ${esc(e.title)}${done.has('e:' + e.id) ? '<span class="w">✓ Set</span>' : ''}</div><div class="muted">${e.durMs ? Math.round(e.durMs / 60000) + ' min' : ''}</div><div class="d">${esc(e.plot)}</div></div></button>`; }).join('');
    $$('.ep').forEach(b => b.onclick = () => { const e = eps[+b.dataset.e], r = d.resume['e:' + e.id]; playEp(e, r ? r.pos / 1000 : 0); });
  };
  $$('[data-s]').forEach(b => b.onclick = () => { season = +b.dataset.s; drawEps(); });
  if ($('#tres')) $('#tres').onclick = () => playEp(res, last.pos / 1000);
  if ($('#tfirst')) $('#tfirst').onclick = () => playEp(firstUnseen, 0);
  drawEps();
};
function head(d, meta, acts) {
  return `<section class="title-hd" style="background-image:url('${esc(d.backdrop || d.poster)}')"><div class="z">${d.poster ? `<img class="pst" src="${esc(d.poster)}" alt="" onerror="this.remove()">` : ''}<div style="min-width:0">
    <h1>${esc(d.name)}</h1><div class="meta">${meta.map(m => `<span>${esc(m)}</span>`).join('')}</div>${d.plot ? `<p class="plot">${esc(d.plot)}</p>` : ''}<div class="acts">${acts}</div></div></div></section>`;
}

// ---- Søg ----
Views.search = async (g) => {
  const q0 = sessionStorage.getItem('vt.q') || '';
  view.innerHTML = `<div class="search"><input id="q" placeholder="Søg i kanaler, programmer, film og serier" value="${esc(q0)}" autocomplete="off"></div><div class="muted" id="sprog"></div><div id="res"></div>`;
  const inp = $('#q');
  inp.focus();
  let t = 0, last = '';
  const run = async (again) => {
    const q = inp.value.trim();
    try { sessionStorage.setItem('vt.q', q); } catch { }
    if (!q) { $('#res').innerHTML = '<div class="empty">Prøv fx "dart", "fodbold", en kanal eller en film.</div>'; $('#sprog').textContent = ''; return; }
    const d = await api('/api/search?q=' + encodeURIComponent(q));
    if (g !== gen || inp.value.trim() !== q) return;
    const s = JSON.stringify(d);
    if (s !== last) {
      last = s;
      const hits = d.channels.concat(d.programs);
      $('#res').innerHTML =
        (hits.length ? `<h2>Live-TV</h2><div class="row" id="rch">${hits.map((h, i) => `<button class="ch-card" data-i="${i}">${logo(h.img, h.name)}<div class="meta"><div class="n">${esc(h.name)}</div><div class="s">${esc(h.kind === 'program' ? (h.start <= Date.now() ? 'Nu · ' : hm(h.start) + ' · ') + h.sub : h.sub)}</div>${h.stop && h.start <= Date.now() ? bar(prog(h.start, h.stop)) : ''}</div></button>`).join('')}</div>` : '') +
        (d.movies.length ? `<h2>Film</h2><div class="grid">${d.movies.map(it => posterCard(it, 'movie')).join('')}</div>` : '') +
        (d.series.length ? `<h2>Serier</h2><div class="grid">${d.series.map(it => posterCard(it, 'series')).join('')}</div>` : '') +
        (!hits.length && !d.movies.length && !d.series.length ? `<div class="empty">${d.progress ? 'Leder …' : 'Intet fundet.'}</div>` : '');
      const r = $('#rch');
      if (r) r.onclick = async e => { const b = e.target.closest('[data-i]'); if (!b) return; const ch = await api('/api/live/channel?id=' + hits[+b.dataset.i].chId); Player.open({ type: 'live', ch, list: [ch] }); };
    }
    $('#sprog').textContent = d.progress || '';
    if (d.progress && again !== false) { clearTimeout(t); t = setTimeout(run, 3000); }
  };
  inp.oninput = () => { clearTimeout(t); t = setTimeout(run, 300); };
  run();
};

// ---- Indstillinger ----
Views.settings = async (g, [sec]) => {
  if (!status.loggedIn) return Views.login(g);
  const [lg, sp, mg, sg] = await Promise.all([api('/api/live/groups'), api('/api/sport/prefs'), api('/api/vod/groups?kind=movie'), api('/api/vod/groups?kind=series')]);
  if (g !== gen) return;
  const groupPicker = (id, groups, prefixes, kind) => {
    const pre = prefixes || [...new Set(groups.map(k => k.prefix))];
    return `<div class="tabs" id="${id}-t">${pre.slice(0, 40).map((p, i) => `<button class="pill ${i === 0 ? 'on' : ''}" data-p="${esc(p)}">${esc(p)}</button>`).join('')}</div><div class="checks" id="${id}"></div>`;
  };
  view.innerHTML = `<h1>Indstillinger</h1>
    <h2 id="grupper">Live-TV: viste grupper <small>${lg.shown.length} valgt</small></h2><div class="box">${groupPicker('lgp', lg.groups, lg.prefixes)}</div>
    <h2 id="sport">Sport: favoritter</h2><div class="box"><form class="form" id="spf" style="max-width:none">
      <label>Hold (ét pr. linje – også landshold, fx Danmark)<textarea name="teams" rows="5">${esc(sp.teams.join('\n'))}</textarea></label>
      <div><b>Turneringer</b><div class="checks">${sp.allLeagues.map(l => `<label><input type="checkbox" name="lg" value="${esc(l.code)}" ${sp.leagues.includes(l.code) ? 'checked' : ''}>${esc(l.name)}</label>`).join('')}</div></div>
      <div><b>Sportsgrene</b><div class="checks">${sp.allSports.filter(s => s[0] !== 'football').map(s => `<label><input type="checkbox" name="sp" value="${s[0]}" ${sp.sports.includes(s[0]) ? 'checked' : ''}>${esc(s[1])}</label>`).join('')}</div></div>
      <label style="flex-direction:row;align-items:center;gap:10px"><input type="checkbox" name="spoiler" ${sp.spoiler ? 'checked' : ''} style="width:18px;height:18px">Spoilerfri (skjul resultater)</label>
      <div><button class="btn">Gem</button></div></form></div>
    <h2 id="film">Film: viste grupper <small>${mg.shown.length} valgt</small></h2><div class="box">${groupPicker('mgp', mg.groups)}</div>
    <h2 id="serier">Serier: viste grupper <small>${sg.shown.length} valgt</small></h2><div class="box">${groupPicker('sgp', sg.groups)}</div>
    <h2>Login</h2><div class="box"><p>Logget ind. ${status.alts ? status.alts + ' reserve-adresse' + (status.alts === 1 ? '' : 'r') + ' gemt.' : 'Ingen reserve-adresser.'}</p>
      <form class="form" id="altf"><label>Reserve-adresser fra udbyderen<textarea name="alts" rows="2" placeholder="Indsæt udbyderens besked"></textarea></label><div><button class="btn ghost">Gem reserve-adresser</button></div></form>
      <p style="margin-top:18px"><button class="btn ghost" id="logout">Log IPTV-abonnementet ud</button></p></div>
    <h2 id="kode">Din bruger <small>${esc(me.user)}</small></h2><div class="box">
      <p>${me.home ? 'Husstandens bruger. Hjemme på jeres net kræves ingen kode; koden bruges kun til den offentlige adresse.' : 'Din egen bruger med eget login og egne favoritter.'}</p>
      <form class="form" id="pwf">${me.hasPassword ? '<label>Nuværende kode<input name="old" type="password" autocomplete="current-password"></label>' : ''}
        <label>${me.hasPassword ? 'Ny kode' : 'Vælg en kode'} (mindst 8 tegn)<input name="password" type="password" autocomplete="new-password" minlength="8" required></label>
        <div class="err" id="pwerr"></div><div><button class="btn ghost">Gem kode</button>${me.remote ? ' <button type="button" class="btn ghost" id="signout">Log ud af Vores TV</button>' : ''}</div></form></div>
    ${me.admin ? '<h2 id="brugere">Brugere</h2><div class="box" id="users"><div class="spinner"></div></div>' : ''}`;
  const pick = (id, groups, toggle) => {
    const draw = p => { $('#' + id).innerHTML = groups.filter(k => k.prefix === p).map(k => `<label><input type="checkbox" data-id="${esc(k.id)}" ${k.shown ? 'checked' : ''}>${esc(k.name)}${k.count ? ` <span class="muted">${k.count}</span>` : ''}</label>`).join(''); };
    $('#' + id + '-t').onclick = e => { const b = e.target.closest('[data-p]'); if (!b) return; $$('#' + id + '-t .pill').forEach(x => x.classList.toggle('on', x === b)); draw(b.dataset.p); };
    $('#' + id).onchange = async e => { const k = groups.find(x => x.id === e.target.dataset.id); k.shown = e.target.checked; await toggle(k.id); toast(k.shown ? k.name + ' vises nu' : k.name + ' skjult'); };
    const first = $('#' + id + '-t .pill'); if (first) draw(first.dataset.p);
  };
  pick('lgp', lg.groups, id => api('/api/live/group', { id }));
  pick('mgp', mg.groups, id => api('/api/vod/group', { kind: 'movie', id }));
  pick('sgp', sg.groups, id => api('/api/vod/group', { kind: 'series', id }));
  $('#spf').onsubmit = async e => {
    e.preventDefault();
    const f = e.target;
    await api('/api/sport/prefs', { teams: f.teams.value, leagues: $$('[name=lg]:checked', f).map(x => x.value), sports: $$('[name=sp]:checked', f).map(x => x.value), spoiler: f.spoiler.checked });
    toast('Sport-favoritter gemt');
  };
  $('#altf').onsubmit = async e => { e.preventDefault(); await api('/api/alts', { alts: e.target.alts.value }); toast('Reserve-adresser gemt'); route(); };
  $('#logout').onclick = async () => { if (!confirm('Log ud af udbyderen på denne server?')) return; await api('/api/logout', {}); route(); };
  $('#pwf').onsubmit = async e => {
    e.preventDefault();
    try { await api('/api/me/password', Object.fromEntries(new FormData(e.target))); toast('Koden er gemt'); e.target.reset(); $('#pwerr').textContent = ''; }
    catch (x) { $('#pwerr').textContent = x.message; }
  };
  if ($('#signout')) $('#signout').onclick = async () => { await api('/api/auth/logout', {}); signIn(); };
  if (me.admin) drawUsers();
  if (sec) { const el = document.getElementById(sec); if (el) el.scrollIntoView(); }
};
async function drawUsers() {
  const l = await api('/api/users'), el = $('#users'); if (!el) return;
  const when = t => t ? new Date(t).toLocaleDateString('da-DK', { day: 'numeric', month: 'short' }) : 'aldrig';
  el.innerHTML = `<p>Hver bruger har sit eget IPTV-login, egne favoritter og eget "fortsæt". De logger ind med brugernavn og kode via den offentlige adresse.</p>
    <div class="hits" style="margin:12px 0">${l.filter(u => u.name !== 'hjem').map(u => `<div class="stream" style="grid-template-columns:minmax(0,1fr) auto"><div><div class="n">${esc(u.name)}</div><div class="w">Sidst aktiv: ${when(u.lastSeen)}</div></div><div class="chips"><button class="pill" data-pw="${esc(u.name)}">Ny kode</button><button class="pill" data-del="${esc(u.name)}">Slet</button></div></div>`).join('') || '<div class="muted">Ingen andre brugere endnu.</div>'}</div>
    <form class="form" id="uadd"><b>Ny bruger</b><label>Brugernavn<input name="name" autocapitalize="none" placeholder="fx thorbjoern" required></label><label>Kode (mindst 8 tegn – giv den til personen)<input name="password" minlength="8" required></label><div class="err" id="uerr"></div><div><button class="btn ghost">Opret bruger</button></div></form>`;
  $('#uadd').onsubmit = async e => {
    e.preventDefault();
    try { await api('/api/users', Object.fromEntries(new FormData(e.target))); toast('Bruger oprettet'); drawUsers(); } catch (x) { $('#uerr').textContent = x.message; }
  };
  el.onclick = async e => {
    const pw = e.target.closest('[data-pw]'), del = e.target.closest('[data-del]');
    if (pw) { const p = prompt('Ny kode til ' + pw.dataset.pw + ' (mindst 8 tegn):'); if (p) try { await api('/api/users/password', { name: pw.dataset.pw, password: p }); toast('Koden er skiftet'); } catch (x) { toast(x.message); } }
    if (del && confirm('Slet ' + del.dataset.del + '? Brugerens login, favoritter og "fortsæt" slettes.')) { await api('/api/users/delete', { name: del.dataset.del }); drawUsers(); }
  };
}

// ---- Status: forbindelser, udbyder, server, tunnel, backup og beskeder ----
Views.status = async (g) => {
  const fmtB = n => (n / 1e9 >= 1 ? (n / 1e9).toFixed(1).replace('.', ',') + ' GB' : Math.round(n / 1e6) + ' MB');
  const ago = t => { if (!t) return 'aldrig'; const s = (Date.now() - t) / 1000; return s < 90 ? 'lige nu' : s < 3600 ? Math.round(s / 60) + ' min siden' : s < 86400 ? Math.round(s / 3600) + ' t siden' : new Date(t).toLocaleDateString('da-DK', { day: 'numeric', month: 'short' }) + ' ' + hm(t); };
  const up = s => s > 86400 ? Math.floor(s / 86400) + ' d ' + Math.floor(s % 86400 / 3600) + ' t' : Math.floor(s / 3600) + ' t ' + Math.floor(s % 3600 / 60) + ' min';
  const row = (k, v, cls = '') => `<div class="kv ${cls}"><span>${esc(k)}</span><b>${v}</b></div>`;
  for (let i = 0; g === gen; i++) {
    const d = await api('/api/ops/status');
    if (g !== gen) return;
    const a = d.account || {};
    const focus = document.activeElement && view.contains(document.activeElement) && document.activeElement.matches('input');
    if (!focus) view.innerHTML = `<h1>Status</h1>
      <h2>Hvem ser hvad <small>${a.max ? a.active + ' af ' + a.max + ' forbindelser i brug hos udbyderen' : ''}</small></h2>
      <div class="box">${d.viewers.length ? d.viewers.map(v => row((v.tv ? '📺 ' : v.via === 'udefra' ? '🌍 ' : '💻 ') + v.device + (me.admin && v.user !== me.user ? ' · ' + v.user : ''), esc(v.title))).join('') : '<p class="muted">Ingen ser noget gennem Vores TV lige nu.</p>'}
        <p class="muted" style="margin-top:10px">Udbyderen tæller også apps uden for Vores TV (fx TiviMate). Tv'er med Vores TV 0.43 eller nyere vises her.</p></div>
      <h2>Udbyder</h2><div class="box">
        ${row('Status', a.ok === false ? '<span class="bad">' + esc(a.error) + '</span>' : d.health.problem ? '<span class="bad">' + esc(d.health.problem) + '</span>' : '<span class="good">Svarer</span>')}
        ${a.ok ? row('Svartid', a.ms + ' ms') : ''}${a.exp ? row('Abonnement udløber', new Date(a.exp).toLocaleDateString('da-DK', { day: 'numeric', month: 'long', year: 'numeric' })) : ''}
        ${row('Reserve-adresser', d.health.alts ? d.health.alts + ' gemt' : 'ingen')}${d.health.switched ? row('Seneste skift', esc(d.health.switched)) : ''}
        ${row('Kanaler', d.channels.toLocaleString('da-DK'))}</div>
      ${d.server ? `<h2>Server</h2><div class="box">
        ${row('Maskine', esc(d.server.host) + ' · Node ' + esc(d.server.node))}${row('Kørt i', up(d.server.uptime))}
        ${row('Hukommelse', fmtB(d.server.rss) + ' (fri på maskinen: ' + fmtB(d.server.freeMem) + ' af ' + fmtB(d.server.totalMem) + ')')}${row('Brugere', d.users)}</div>
      <h2>Tunnel <small>tv.simonp.dk</small></h2><div class="box">
        ${row('Tjeneste', d.tunnel.service == null ? 'ukendt' : d.tunnel.service === 'RUNNING' ? '<span class="good">Kører</span>' : '<span class="bad">' + esc(d.tunnel.service) + '</span>')}${row('Seneste besøg udefra', ago(d.tunnel.lastRemote))}</div>
      <h2>Backup</h2><div class="box">
        ${row('Seneste', d.backup.last ? ago(d.backup.last.at) + ' · ' + d.backup.last.files + ' filer' : 'ingen endnu')}${row('Gemte kopier', d.backup.count + ' (de 14 nyeste beholdes)')}${row('Placering', esc(d.backup.dir))}
        <p class="muted">Tages hver nat kl. 03.30: brugere, favoritter, indstillinger og "fortsæt". IPTV-login og cache kommer ikke med.</p>
        <form class="form" id="bkf" style="margin-top:10px"><label>Anden placering (valgfrit)<input name="backupDir" placeholder="${esc(d.backup.dir)}"></label><div><button class="btn ghost">Gem placering</button> <button type="button" class="btn ghost" id="bknow">Tag backup nu</button></div></form></div>
      <h2>Beskeder til telefonen</h2><div class="box">
        <p>${d.ntfy ? '<span class="good">Slået til.</span> Du får en besked, når udbyderen eller tunnelen går ned – og når de er oppe igen.' : 'Få en besked på telefonen, når udbyderen eller tunnelen går ned. Installér appen <b>ntfy</b> (gratis), abonnér på et emne med et langt, hemmeligt navn, og skriv samme navn her.'}</p>
        <form class="form" id="ntf" style="margin-top:10px"><label>ntfy-emne<input name="ntfy" placeholder="${d.ntfy ? 'skjult – skriv nyt for at skifte' : 'fx vorestv-' + Math.random().toString(36).slice(2, 10)}"></label><div><button class="btn ghost">Gem</button>${d.ntfy ? ' <button type="button" class="btn ghost" id="ntest">Send test</button> <button type="button" class="btn ghost" id="noff">Slå fra</button>' : ''}</div></form></div>` : ''}`;
    if (!focus && d.server) {
      $('#bkf').onsubmit = async e => { e.preventDefault(); await api('/api/ops/cfg', { backupDir: e.target.backupDir.value }); toast('Placering gemt'); i = -1; };
      $('#bknow').onclick = async () => { const r = await api('/api/ops/backup', {}); toast(r.error ? 'Fejl: ' + r.error : 'Backup taget: ' + r.files + ' filer'); };
      $('#ntf').onsubmit = async e => { e.preventDefault(); if (!e.target.ntfy.value.trim()) return; await api('/api/ops/cfg', { ntfy: e.target.ntfy.value }); toast('Beskeder slået til'); };
      if ($('#ntest')) $('#ntest').onclick = async () => toast((await api('/api/ops/test', {})).ok ? 'Testbesked sendt' : 'Kunne ikke sende');
      if ($('#noff')) $('#noff').onclick = async () => { await api('/api/ops/cfg', { ntfy: '' }); toast('Beskeder slået fra'); };
    }
    await sleep(10000);
  }
};

// ---- Guide-gitter: kanaler × tid ----
const REM_KEY = 'vt.reminders';
const reminders = () => { try { return JSON.parse(localStorage.getItem(REM_KEY) || '{}'); } catch { return {}; } };
const setReminders = r => { try { localStorage.setItem(REM_KEY, JSON.stringify(r)); } catch { } };
setInterval(() => { // påmindelser: når programmet starter (mens siden er åben)
  const r = reminders(), now = Date.now();
  let changed = false;
  for (const [k, x] of Object.entries(r)) {
    if (x.start - 60e3 > now) continue;
    if (x.start + 30 * 60e3 > now) {
      toast('▶ ' + x.title + ' starter nu på ' + x.ch, 8000);
      if (window.Notification && Notification.permission === 'granted') { const n = new Notification(x.title, { body: 'Starter nu på ' + x.ch }); n.onclick = () => { window.focus(); location.hash = '#/live/' + encodeURIComponent(x.cat); }; }
    }
    delete r[k]; changed = true;
  }
  if (changed) setReminders(r);
}, 20000);

Views.guide = async (g, [cat]) => {
  const gr = await api('/api/live/groups');
  if (g !== gen) return;
  const groups = [{ id: 'fav', name: 'Favoritter' }].concat(gr.groups.filter(k => k.shown));
  cat = cat || localStorage.getItem('vt.cat') || (gr.shown[0] || 'fav');
  const PX = 4; // pixels pr. minut
  const t0 = Math.floor((Date.now() - 30 * 60e3) / 1800e3) * 1800e3, hours = 12, t1 = t0 + hours * 3600e3;
  const x = t => Math.round((Math.max(t0, Math.min(t1, t)) - t0) / 60e3 * PX);
  view.innerHTML = `<div class="pills"><a class="pill" href="#/live/${encodeURIComponent(cat)}">☰ Liste</a>${groups.map(k => `<a class="pill ${k.id === cat ? 'on' : ''}" href="#/guide/${encodeURIComponent(k.id)}">${esc(k.name)}</a>`).join('')}</div>
    <div class="grid-wrap" id="gw"><div class="spinner"></div></div><div class="hint">Klik på det, der kører nu, for at se det · klik på et senere program for en påmindelse</div>`;
  let chs = [];
  const draw = () => {
    const rem = reminders(), now = Date.now();
    let head = '';
    for (let t = t0; t < t1; t += 1800e3) head += `<div class="gt" style="left:${x(t)}px">${hm(t)}</div>`;
    const rows = chs.map((ch, i) => {
      const progs = (ch.epg || []).filter(p => p.stop > t0 && p.start < t1).map(p => {
        const live = p.start <= now && p.stop > now, k = ch.id + '@' + p.start, r = !!rem[k];
        return `<button class="gp ${live ? 'now' : ''} ${p.stop <= now ? 'past' : ''} ${r ? 'rem' : ''}" style="left:${x(p.start)}px;width:${Math.max(2, x(p.stop) - x(p.start) - 3)}px" data-c="${i}" data-s="${p.start}" title="${esc(hm(p.start) + '–' + hm(p.stop) + ' ' + p.title)}"><b>${r ? '🔔 ' : ''}${esc(p.title)}</b><span>${hm(p.start)}–${hm(p.stop)}</span></button>`;
      }).join('');
      return `<div class="grow"><button class="gch" data-ch="${i}">${logo(ch.logo, ch.name)}<span>${esc(ch.name)}</span></button><div class="gline" style="width:${x(t1)}px">${progs || '<div class="gnone">Ingen programguide</div>'}</div></div>`;
    }).join('');
    const gw = $('#gw'); if (!gw) return;
    const sl = gw.scrollLeft, st = gw.scrollTop;
    gw.innerHTML = `<div class="ghead"><div class="gcorner"></div><div class="gline" style="width:${x(t1)}px">${head}<div class="gnow" style="left:${x(now)}px"></div></div></div>${rows}<div class="gnowline" style="left:${x(now) + 210}px"></div>`;
    gw.scrollLeft = sl || Math.max(0, x(now) - 120); gw.scrollTop = st;
  };
  $('#gw').onclick = e => {
    const c = e.target.closest('[data-ch]'), p = e.target.closest('.gp');
    if (c) return Player.open({ type: 'live', ch: chs[+c.dataset.ch], list: chs });
    if (!p) return;
    const ch = chs[+p.dataset.c], start = +p.dataset.s, prog = ch.epg.find(q => q.start === start);
    if (prog.start <= Date.now() && prog.stop > Date.now()) return Player.open({ type: 'live', ch, list: chs });
    if (prog.stop <= Date.now()) return toast('Programmet er slut');
    const r = reminders(), k = ch.id + '@' + start;
    if (r[k]) { delete r[k]; toast('Påmindelse fjernet'); }
    else { r[k] = { title: prog.title, ch: ch.name, start, cat }; toast('🔔 Påmindelse kl. ' + hm(start) + ' – hold siden åben'); if (window.Notification && Notification.permission === 'default') Notification.requestPermission(); }
    setReminders(r); draw();
  };
  for (let i = 0; g === gen; i++) {
    const d = await api('/api/live/grid?cat=' + encodeURIComponent(cat));
    if (g !== gen) return;
    chs = d.channels; draw();
    await sleep(d.pending ? 4000 : 60000);
  }
};

route();
