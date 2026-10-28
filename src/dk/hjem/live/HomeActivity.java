package dk.hjem.live;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Hjem (design 1a): ny dansk film som forside, Fortsæt, Live nu, nyt i film og serier. */
public class HomeActivity extends Activity {
    private Px px;
    private FrameLayout root;
    private Ui.TopBar top;
    private FrameLayout previewBox;
    private ImageView previewLogo;
    private android.view.TextureView video;
    private Ui.Bar heroBar;
    private TextView heroStart, heroEnd;
    private androidx.media3.exoplayer.ExoPlayer preview;
    private Catalog.Ch heroCh, previewing;
    private final Runnable startPreview = this::startPreview;
    private TextView kicker, title, meta, plot;
    private LinearLayout page, buttons;
    private boolean heroWasGame;
    private LinearLayout alert;
    private TextView alertTitle, alertText;

    private void bindAlert() {
        if (Health.switched != null && Health.problem == null) {
            alert.setVisibility(View.VISIBLE);
            alertTitle.setText("Skiftede til reserve-adresse");
            alertText.setText("Hovedadressen svarede ikke, så Vores TV bruger nu udbyderens reserve-adresse. Alt virker som før.");
            final String shownMsg = Health.switched;
            h.postDelayed(() -> { if (shownMsg.equals(Health.switched)) { Health.switched = null; bindAlert(); } }, 20_000);
            return;
        }
        String p = Health.problem;
        alert.setVisibility(p == null ? View.GONE : View.VISIBLE);
        if (p == null) return;
        alertTitle.setText(p);
        alertText.setText("Kanaler, film og serier kan ikke afspilles lige nu. Har udbyderen givet dig en ny adresse, så log ind igen på telefonen: "
                + Web.address().replace("http://", "") + "  (Log ud af udbyderen → log ind med den nye server).");
    }
    private boolean firstFocusDone;
    private String shown = "";
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable rebuild = () -> { rebuildQueued = false; rebuild(); };
    private boolean rebuildQueued;
    private final Runnable onData = () -> { if (!rebuildQueued) { rebuildQueued = true; h.postDelayed(rebuild, 500); } }; // samlet, højst ca. 2 gange i sekundet
    private final Runnable tick = new Runnable() { @Override public void run() {
        top.clock.setText(Fmt.clock());
        if (heroCh != null) setLive(heroCh);
        Health.check(HomeActivity.this, false);
        bindAlert();
        h.postDelayed(this, 30_000);
    } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        px = new Px(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        Web.start(this);
        if (!Cred.load(this).ok()) { startActivity(new Intent(this, LiveActivity.class)); }
        Catalog.load(this, false);
        Vod.loadCategories(this, Vod.MOVIE);
        Vod.loadCategories(this, Vod.SERIES);
        Updater.check(this);
        Health.check(this, true);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFocusable(false);
        sv.setClipToPadding(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        sv.addView(page);

        // Live-forside: lille lydløs live-video + det der kører, "Se nu" og "Guide"
        FrameLayout heroBox = new FrameLayout(this);
        heroBox.setClipChildren(false);
        heroBox.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF17171C, 0xFF0B0B0D}));
        previewBox = new FrameLayout(this);
        previewBox.setBackground(Ui.round(0xFF1C1C21, px.p(24)));
        previewBox.setClipToOutline(true);
        previewLogo = new ImageView(this);
        previewLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewLogo.setPadding(px.p(220), px.p(120), px.p(220), px.p(120));
        previewBox.addView(previewLogo, new FrameLayout.LayoutParams(-1, -1));
        video = new android.view.TextureView(this);
        video.setAlpha(0f);
        previewBox.addView(video, new FrameLayout.LayoutParams(-1, -1));
        TextView liveBadge = Ui.badge(this, px, "LIVE", true);
        FrameLayout.LayoutParams lb = new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.BOTTOM | android.view.Gravity.START);
        lb.leftMargin = px.p(24); lb.bottomMargin = px.p(20);
        previewBox.addView(liveBadge, lb);
        heroBox.addView(previewBox, Ui.at(px.p(96), px.p(150), px.p(704), px.p(396)));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.setClipChildren(false);
        kicker = px.t(18, 600, Theme.white(0.65f));
        kicker.setLetterSpacing(0.12f);
        ht.addView(kicker);
        title = px.t(60, 700, 0xFFFFFFFF);
        title.setLetterSpacing(-0.03f);
        title.setSingleLine(false);
        title.setMaxLines(2);
        title.setLineSpacing(0, 0.95f);
        ht.addView(title, lp(-1, 16));
        LinearLayout tr = new LinearLayout(this);
        tr.setGravity(android.view.Gravity.CENTER_VERTICAL);
        heroStart = px.t(21, 500, Theme.white(0.75f));
        tr.addView(heroStart);
        heroBar = new Ui.Bar(this, Theme.white(0.2f), 0xFFFFFFFF);
        LinearLayout.LayoutParams hb = new LinearLayout.LayoutParams(px.p(320), px.p(6));
        hb.leftMargin = px.p(18); hb.rightMargin = px.p(18);
        tr.addView(heroBar, hb);
        heroEnd = px.t(21, 500, Theme.white(0.75f));
        tr.addView(heroEnd);
        ht.addView(tr, lp(-2, 20));
        meta = px.t(21, 400, Theme.white(0.55f));
        ht.addView(meta, lp(-1, 14));
        plot = px.t(22, 400, Theme.white(0.7f));
        plot.setSingleLine(false);
        plot.setMaxLines(2);
        plot.setLineSpacing(px.p(6), 1f);
        ht.addView(plot, lp(-1, 14));
        buttons = new LinearLayout(this);
        buttons.setClipChildren(false);
        ht.addView(buttons, lp(-2, 26));
        heroBox.addView(ht, Ui.at(px.p(848), px.p(158), px.p(976), -2));
        page.addView(heroBox, new LinearLayout.LayoutParams(-1, px.p(600)));
        for (int i = 0; i < 4; i++) { secs[i] = new LinearLayout(this); secs[i].setOrientation(LinearLayout.VERTICAL); secs[i].setClipChildren(false); page.addView(secs[i]); }
        page.addView(new View(this), new LinearLayout.LayoutParams(1, px.p(60)));
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        View topShade = new View(this);
        topShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF20B0B0D, 0x000B0B0D}));
        root.addView(topShade, new FrameLayout.LayoutParams(-1, px.p(190)));
        // Besked nederst, når udbyderen ikke svarer
        alert = new LinearLayout(this);
        alert.setGravity(android.view.Gravity.CENTER_VERTICAL);
        alert.setBackground(Ui.round(0xF2241416, px.p(24)));
        alert.setPadding(px.p(30), px.p(22), px.p(36), px.p(22));
        View dot = new View(this);
        dot.setBackground(Ui.round(Ui.RED, px.p(8)));
        alert.addView(dot, new LinearLayout.LayoutParams(px.p(16), px.p(16)));
        LinearLayout at = new LinearLayout(this);
        at.setOrientation(LinearLayout.VERTICAL);
        alertTitle = px.t(24, 650, 0xFFFFFFFF);
        alertTitle.setSingleLine(false);
        at.addView(alertTitle);
        alertText = px.t(20, 400, Theme.white(0.7f));
        alertText.setSingleLine(false);
        at.addView(alertText, lp(-1, 6));
        LinearLayout.LayoutParams atl = new LinearLayout.LayoutParams(0, -2, 1);
        atl.leftMargin = px.p(22);
        alert.addView(at, atl);
        alert.setVisibility(View.GONE);
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM);
        al.leftMargin = px.p(96); al.rightMargin = px.p(96); al.bottomMargin = px.p(48);
        root.addView(alert, al);
        top = Ui.topBar(this, px, Nav.TABS, Nav.HOME);
        root.addView(top.root, Ui.at(px.p(96), px.p(42), px.p(1728), -2));
        Nav.wire(this, top, Nav.HOME, null);
        openTab(getIntent());
    }

    /** Genvej udefra (fx fra Hjem-launcheren): extra "open" = live | movie | series | search (+ "q"). */
    @Override protected void onNewIntent(Intent in) { super.onNewIntent(in); openTab(in); }

    private void openTab(Intent in) {
        String k = in != null ? in.getStringExtra("open") : null;
        if ("live".equals(k)) startActivity(new Intent(this, LiveActivity.class).putExtra("catName", in.getStringExtra("cat")));
        else if ("sport".equals(k)) startActivity(new Intent(this, SportActivity.class));
        else if ("search".equals(k)) startActivity(new Intent(this, SearchActivity.class).putExtra("q", in.getStringExtra("q")));
        else if (Vod.MOVIE.equals(k) || Vod.SERIES.equals(k)) startActivity(new Intent(this, MediaActivity.class).putExtra("kind", k));
    }

    private LinearLayout.LayoutParams lp(int w, float t) { LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, -2); l.topMargin = px.p(t); return l; }

    @Override protected void onResume() {
        super.onResume();
        Catalog.listeners.add(onData);
        Sports.refresh(this);
        final android.content.Context app = getApplicationContext();
        Bg.run("home-guide", () -> Sports.guide(app));
        preview = Play.create(this, false);
        preview.setVolume(0f);
        preview.setVideoTextureView(video);
        preview.addListener(new androidx.media3.common.Player.Listener() {
            @Override public void onRenderedFirstFrame() { video.animate().alpha(1f).setDuration(300).start(); }
        });
        previewing = null;
        shown = "";
        rebuild();
        h.postDelayed(startPreview, 800);
        h.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        Catalog.listeners.remove(onData);
        h.removeCallbacks(tick);
        h.removeCallbacks(startPreview);
        if (preview != null) { preview.release(); preview = null; }
        previewing = null;
        video.setAlpha(0f);
    }

    // Live-kampe (fra Sport → Mine kampe) med streams, regnet ud i baggrunden
    private static final java.util.Map<String, List<Match.Stream>> liveStreams = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ExecutorService heroPool = Bg.pool(1, "home-streams");
    private final java.util.Set<String> pendingStreams = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private Sports.Game heroGame;

    private void streamsLater(final Sports.Game g) {
        final String k = g.id + "|" + g.league;
        if (liveStreams.containsKey(k) || !pendingStreams.add(k)) return;
        heroPool.execute(() -> {
            liveStreams.put(k, Match.streamsLight(getApplicationContext(), g));
            pendingStreams.remove(k);
            h.post(onData);
        });
    }

    /** Dine kampe: live og i dag; er der ingen i dag, de næste par dage. */
    private List<Sports.Game> myGames() {
        List<Sports.Game> all = Sports.list(this, true), out = new ArrayList<>();
        java.util.Calendar end = java.util.Calendar.getInstance();
        end.set(java.util.Calendar.HOUR_OF_DAY, 23); end.set(java.util.Calendar.MINUTE, 59);
        for (Sports.Game g : all) if (!g.done() && (g.live() || g.start <= end.getTimeInMillis())) out.add(g);
        if (out.isEmpty()) for (Sports.Game g : all) if (!g.done() && g.start - System.currentTimeMillis() < 3 * 86_400_000L) out.add(g);
        return out.size() > 16 ? out.subList(0, 16) : out;
    }

    // Rækkerne bygges hver for sig og kun når deres indhold har ændret sig (ellers hakker siden, mens data kommer ind)
    private final LinearLayout[] secs = new LinearLayout[4]; // kampe, kanaler, fortsæt, nyt
    private final String[] sigs = {"", "", "", ""};
    private List<Vod.Item> newest = new ArrayList<>();
    private String newestSig = "";

    private List<Vod.Item> newest() {
        StringBuilder k = new StringBuilder();
        for (String kind : new String[]{Vod.MOVIE, Vod.SERIES}) for (String g : Vod.shownGroups(this, kind)) k.append(Vod.inCategory(this, kind, g).size()).append(',');
        if (k.toString().equals(newestSig)) return newest;
        newestSig = k.toString();
        List<Vod.Item> out = new ArrayList<>(), nm = Feed.newest(this, Vod.MOVIE, 12), ns = Feed.newest(this, Vod.SERIES, 12);
        for (int i = 0; i < Math.max(nm.size(), ns.size()); i++) { if (i < nm.size()) out.add(nm.get(i)); if (i < ns.size()) out.add(ns.get(i)); }
        return newest = out;
    }

    // Data samles i baggrunden; skærmen bygger kun rækkerne, når data er klar (ingen tunge sorteringer på skærmens tråd)
    private static final java.util.concurrent.ExecutorService gather = Bg.pool(1, "home-data");
    private volatile boolean gathering, gatherAgain;

    private void rebuild() {
        if (gathering) { gatherAgain = true; return; }
        gathering = true;
        final android.content.Context app = this;
        gather.execute(() -> {
            final List<Vod.Resume> cont = Vod.continueList(app, null);
            final List<Catalog.Ch> chans = Feed.liveNow(app, 16);
            final List<Sports.Game> games = myGames();
            final List<Vod.Item> nw = newest();
            h.post(() -> {
                gathering = false;
                if (!isFinishing()) apply(cont, chans, games, nw);
                if (gatherAgain) { gatherAgain = false; onData.run(); }
            });
        });
    }

    private void apply(List<Vod.Resume> cont, List<Catalog.Ch> chans, List<Sports.Game> games, List<Vod.Item> nw) {

        // forsiden: en af dine kampe live (med streams), ellers den kanal du sidst så
        Sports.Game hgNew = null;
        for (Sports.Game g : games) {
            if (!g.live()) continue;
            streamsLater(g);
            List<Match.Stream> l = liveStreams.get(g.id + "|" + g.league);
            if (l != null && !l.isEmpty()) { hgNew = g; break; }
        }
        heroGame = hgNew;
        Catalog.Ch pick = null;
        if (heroGame != null) pick = liveStreams.get(heroGame.id + "|" + heroGame.league).get(0).ch;
        if (pick == null) for (Integer id : Catalog.history(this)) { pick = Catalog.byId.get(id); if (pick != null) break; }
        if (pick == null && !chans.isEmpty()) pick = chans.get(0);
        if (pick != null) setLive(pick);
        updatePill();
        bindAlert();

        View keep = getCurrentFocus();
        String keepTag = keep != null && keep.getTag(0x7f0a0001) != null ? (String) keep.getTag(0x7f0a0001) : null;
        View restore = null;
        boolean spoil = Sports.spoilerFree(this);

        // 1) Dine kampe
        StringBuilder sg = new StringBuilder().append(spoil);
        for (Sports.Game g : games) sg.append(g.id).append(g.state).append(g.home.score).append(g.away.score).append(g.clock);
        if (!sg.toString().equals(sigs[0])) {
            sigs[0] = sg.toString();
            secs[0].removeAllViews();
            if (!games.isEmpty()) {
                LinearLayout r = row(secs[0], "Dine kampe", games.get(0).live());
                for (final Sports.Game g : games) {
                    String title = g.title != null ? g.title : g.home.shortName + "  –  " + g.away.shortName;
                    if (!spoil && g.live() && g.title == null && g.home.score >= 0) title = g.home.shortName + "  " + g.home.score + " – " + g.away.score + "  " + g.away.shortName;
                    String sub = (g.live() ? "● LIVE " + Sports.status(g) : g.allDay ? "Hele dagen" : Sports.status(g)) + "  ·  " + (g.title != null && g.sub != null ? g.sub : g.leagueName);
                    String logo = g.home.logo != null && !g.home.logo.isEmpty() ? g.home.logo : null;
                    LinearLayout c = logo != null ? Poster.logoCard(this, px, px.p(390), px.p(219), logo, title, sub, 0, null)
                            : Poster.card(this, px, px.p(390), px.p(219), null, title, sub, 0, null);
                    View box = Poster.box(c);
                    box.setTag(0x7f0a0001, "g" + g.id);
                    box.setOnClickListener(v -> startActivity(new Intent(this, MatchActivity.class).putExtra("key", g.id + "|" + g.league)));
                    add(r, c);
                    if (("g" + g.id).equals(keepTag)) restore = box;
                }
            }
        }
        // 2) Kanaler
        StringBuilder sc = new StringBuilder().append(Catalog.favorites(this).isEmpty());
        for (Catalog.Ch c : chans) sc.append(c.id).append(',');
        sc.append(System.currentTimeMillis() / 30_000L); // programtitler opdateres hvert 30. sek., ikke for hvert program der kommer ind
        if (!sc.toString().equals(sigs[1])) {
            sigs[1] = sc.toString();
            secs[1].removeAllViews();
            if (!chans.isEmpty()) {
                LinearLayout r = row(secs[1], Catalog.favorites(this).isEmpty() ? "Senest sete kanaler" : "Dine kanaler", false);
                final int[] ids = new int[chans.size()];
                for (int i = 0; i < ids.length; i++) ids[i] = chans.get(i).id;
                long t = System.currentTimeMillis();
                for (int i = 0; i < chans.size(); i++) {
                    final int idx = i;
                    Catalog.Ch ch = chans.get(i);
                    Epg.want(this, ch.id);
                    Xtream.Prog n = Epg.now(ch.id);
                    String et = Ev.eventTitle(ch);
                    LinearLayout c = Poster.logoCard(this, px, px.p(390), px.p(219), ch.logo, et != null ? et : ch.name, et != null ? Ev.channelLabel(ch) : n != null ? n.title : " ", n != null ? n.progress(t) : 0, null);
                    View box = Poster.box(c);
                    box.setTag(0x7f0a0001, "l" + ch.id);
                    box.setOnClickListener(v -> startActivity(new Intent(this, PlayerActivity.class).putExtra("ids", ids).putExtra("i", idx).putExtra("cat", LiveActivity.FAV)));
                    add(r, c);
                    if (("l" + ch.id).equals(keepTag)) restore = box;
                }
            }
        }
        // 3) Fortsæt
        StringBuilder so = new StringBuilder();
        for (Vod.Resume x : cont) so.append(x.key).append(x.pos / 60000);
        if (!so.toString().equals(sigs[2])) {
            sigs[2] = so.toString();
            secs[2].removeAllViews();
            if (!cont.isEmpty()) {
                LinearLayout r = row(secs[2], "Fortsæt", false);
                for (final Vod.Resume x : cont) {
                    String sub = Vod.SERIES.equals(x.kind) ? x.sub : Fmt.mins(x.dur - x.pos) + " tilbage";
                    LinearLayout c = Poster.card(this, px, px.p(390), px.p(219), x.poster, x.title, sub, x.progress(), null);
                    View box = Poster.box(c);
                    box.setTag(0x7f0a0001, "r" + x.key);
                    box.setOnClickListener(v -> resume(x));
                    add(r, c);
                    if (("r" + x.key).equals(keepTag)) restore = box;
                }
            }
        }
        // 4) Nyt
        StringBuilder sn = new StringBuilder();
        for (Vod.Item it : nw) sn.append(it.kind).append(it.id).append(',');
        if (!sn.toString().equals(sigs[3])) {
            sigs[3] = sn.toString();
            secs[3].removeAllViews();
            restore = posterRow(secs[3], "Nyt i film og serier", nw, keepTag, restore);
        }
        if (restore != null && (keep == null || !keep.isShown())) restore.requestFocus();
        else if (keep == null || !keep.isShown() || !firstFocusDone) {
            if (buttons.getChildCount() > 0) { buttons.getChildAt(0).requestFocus(); firstFocusDone = true; }
        }
    }

    private View posterRow(LinearLayout sec, String name, List<Vod.Item> items, String keepTag, View restore) {
        if (items.isEmpty()) return restore;
        LinearLayout r = row(sec, name, false);
        for (int i = 0; i < Math.min(24, items.size()); i++) {
            final Vod.Item it = items.get(i);
            LinearLayout c = Poster.card(this, px, px.p(200), px.p(300), it.poster, it.name, it.year, 0, null);
            View box = Poster.box(c);
            box.setTag(0x7f0a0001, "p" + it.kind + it.id);
            box.setOnClickListener(v -> open(it));
            add(r, c);
            if (("p" + it.kind + it.id).equals(keepTag)) restore = box;
        }
        return restore;
    }

    private LinearLayout row(LinearLayout sec, String name, boolean live) {
        LinearLayout head = new LinearLayout(this);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setPadding(px.p(96), px.p(10), 0, 0);
        TextView t = px.t(30, 600, 0xFFFFFFFF);
        t.setText(name);
        head.addView(t);
        if (live) {
            View dot = new View(this);
            dot.setBackground(Ui.round(Ui.RED, px.p(6)));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px.p(12), px.p(12));
            dl.leftMargin = px.p(15);
            head.addView(dot, dl);
        }
        sec.addView(head);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setFocusable(false);
        hs.setClipToPadding(false);
        hs.setPadding(px.p(96), px.p(28), px.p(96), px.p(26));
        LinearLayout r = new LinearLayout(this);
        r.setClipChildren(false);
        hs.addView(r);
        sec.addView(hs);
        return r;
    }

    private void add(LinearLayout r, LinearLayout c) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        if (r.getChildCount() > 0) l.leftMargin = px.p(30);
        r.addView(c, l);
    }

    /** Live-forsiden: kanal, hvad der kører (eller kampen), fremdrift og knapper. */
    private void setLive(final Catalog.Ch ch) {
        boolean changed = heroCh == null || heroCh.id != ch.id;
        heroCh = ch;
        Epg.want(this, ch.id);
        Img.load(previewLogo, ch.logo, px.p(270));
        Catalog.Cat k = Catalog.cats.get(ch.cat);
        Xtream.Prog n = Epg.now(ch.id), x = Epg.next(ch.id);
        long t = System.currentTimeMillis();
        final Sports.Game hg = heroGame;
        String evTitle = Ev.eventTitle(ch), label = Ev.channelLabel(ch);
        String ttl = hg != null ? (hg.title != null ? hg.title : hg.home.shortName + " – " + hg.away.shortName) : evTitle != null ? evTitle : n != null ? n.title : ch.name;
        long st = hg != null ? hg.start : n != null ? n.start : 0, en = hg != null ? hg.start + 2 * 3600_000L : n != null ? n.stop : 0;
        kicker.setText((hg != null ? "LIVE · " + hg.leagueName + " · " + label : "SIDST SET · " + label).toUpperCase(Fmt.DA));
        title.setText(ttl);
        heroStart.setText(st > 0 ? Fmt.hm(st) : "");
        heroEnd.setText(en > 0 ? Fmt.hm(en) : "");
        heroBar.setVisibility(st > 0 ? View.VISIBLE : View.INVISIBLE);
        heroBar.set(st > 0 ? (t - st) / (float) Math.max(1, en - st) : 0);
        meta.setText(hg != null ? (hg.live() ? Sports.status(hg) : "") : x != null ? "Derefter " + Fmt.hm(x.start) + "  " + x.title : k != null ? k.name : "");
        plot.setText(hg == null && n != null && n.desc != null ? n.desc : "");
        if (changed || buttons.getChildCount() == 0 || (hg != null) != heroWasGame) {
            heroWasGame = hg != null;
            buttons.removeAllViews();
            TextView watch = Ui.pill(this, px, hg != null ? "▶  Se kampen" : "▶  Se videre");
            watch.setOnClickListener(v -> { if (hg != null) playGame(hg); else playChannel(ch.id); });
            buttons.addView(watch);
            TextView guide = Ui.pill(this, px, hg != null ? "Kampside" : "Guide");
            guide.setOnClickListener(v -> {
                if (hg != null) startActivity(new Intent(this, MatchActivity.class).putExtra("key", hg.id + "|" + hg.league));
                else startActivity(new Intent(this, LiveActivity.class).putExtra("catName", k != null ? k.name : null));
            });
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2);
            gl.leftMargin = px.p(18);
            buttons.addView(guide, gl);
            upd = null;
            updatePill();
            View f = getCurrentFocus();
            if (f == null || !f.isShown()) buttons.getChildAt(0).requestFocus();
        }
        if (changed) { previewing = null; video.setAlpha(0f); h.removeCallbacks(startPreview); h.postDelayed(startPreview, 800); }
    }

    private void startPreview() {
        if (preview == null || heroCh == null || (previewing != null && previewing.id == heroCh.id)) return;
        previewing = heroCh;
        Play.live(preview, Cred.load(this), heroCh, false);
    }

    private TextView upd;

    /** "Opdatér til x.y" ved siden af knapperne, når GitHub har en nyere version. */
    private void updatePill() {
        if (upd != null) { buttons.removeView(upd); upd = null; }
        if (!Updater.available(this) && Updater.state == null) return;
        String s = Updater.state;
        String label = "henter".equals(s) ? "Henter opdatering …" : "installerer".equals(s) ? "Installerer …"
                : s != null && !Updater.available(this) ? s : "Opdatér til " + Updater.latestVersion;
        upd = Ui.pill(this, px, label);
        upd.setOnClickListener(v -> Updater.install(this));
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        l.leftMargin = px.p(18);
        buttons.addView(upd, l);
    }

    /** Kampen på bedste stream; de andre er reserver, hvis signalet forsvinder. */
    private void playGame(Sports.Game g) {
        List<Match.Stream> l = liveStreams.get(g.id + "|" + g.league);
        if (l == null || l.isEmpty()) { startActivity(new Intent(this, MatchActivity.class).putExtra("key", g.id + "|" + g.league)); return; }
        int[] ids = new int[l.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = l.get(i).ch.id;
        startActivity(new Intent(this, PlayerActivity.class).putExtra("ids", ids).putExtra("i", 0).putExtra("failover", true)
                .putExtra("title", g.title != null ? g.title : g.home.shortName + " – " + g.away.shortName));
    }

    /** Afspil en kanal sammen med resten af dens gruppe, så ▴▾ zapper videre. */
    private void playChannel(int chId) {
        Catalog.Ch ch = Catalog.byId.get(chId);
        if (ch == null) return;
        List<Catalog.Ch> l = Catalog.inCategory(ch.cat);
        int[] ids = new int[l.size()];
        int at = 0;
        for (int i = 0; i < ids.length; i++) { ids[i] = l.get(i).id; if (ids[i] == chId) at = i; }
        startActivity(new Intent(this, PlayerActivity.class).putExtra("ids", ids).putExtra("i", at).putExtra("cat", ch.cat));
    }

    private void open(Vod.Item it) {
        startActivity(new Intent(this, DetailActivity.class).putExtra("kind", it.kind).putExtra("id", it.id)
                .putExtra("name", it.name).putExtra("poster", it.poster).putExtra("ext", it.ext).putExtra("year", it.year));
    }

    private void resume(Vod.Resume r) {
        if (Vod.SERIES.equals(r.kind)) {
            startActivity(new Intent(this, DetailActivity.class).putExtra("kind", Vod.SERIES).putExtra("id", r.seriesId).putExtra("name", r.title).putExtra("poster", r.poster));
        } else {
            startActivity(new Intent(this, VodPlayerActivity.class).putExtra("kind", Vod.MOVIE).putExtra("ids", new String[]{r.id})
                    .putExtra("titles", new String[]{r.title}).putExtra("subs", new String[]{""}).putExtra("exts", new String[]{r.ext})
                    .putExtra("poster", r.poster).putExtra("resume", true));
        }
    }
}
