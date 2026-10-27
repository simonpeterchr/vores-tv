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
    private Search.Hit heroEvent;
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
    private final Runnable rebuild = this::rebuild;
    private final Runnable onData = () -> { h.removeCallbacks(rebuild); h.postDelayed(rebuild, 200); };
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
        else if ("search".equals(k)) startActivity(new Intent(this, SearchActivity.class).putExtra("q", in.getStringExtra("q")));
        else if (Vod.MOVIE.equals(k) || Vod.SERIES.equals(k)) startActivity(new Intent(this, MediaActivity.class).putExtra("kind", k));
    }

    private LinearLayout.LayoutParams lp(int w, float t) { LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, -2); l.topMargin = px.p(t); return l; }

    @Override protected void onResume() {
        super.onResume();
        Catalog.listeners.add(onData);
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

    private void rebuild() {
        List<Vod.Resume> cont = Vod.continueList(this, null);
        List<Catalog.Ch> favs = Feed.liveNow(this, 16);
        List<Search.Hit> sport = Feed.sportNow(this, 20);
        List<Vod.Item> movies = Feed.newest(this, Vod.MOVIE, 24), series = Feed.newest(this, Vod.SERIES, 24);
        StringBuilder st = new StringBuilder();
        st.append(cont.size()).append('|').append(favs.size()).append('|').append(movies.size()).append('|').append(series.size()).append('|').append(sport.size());
        for (Search.Hit x : sport) st.append(x.name);
        for (Vod.Resume r : cont) st.append(r.key).append(r.pos / 60000);
        for (Catalog.Ch c : favs) { Xtream.Prog n = Epg.now(c.id); st.append(n != null ? n.title : "-"); }
        st.append('|').append(Updater.available(this)).append(Updater.state);
        bindAlert();
        if (st.toString().equals(shown)) return;
        updatePill();
        shown = st.toString();
        View keep = getCurrentFocus();
        String keepTag = keep != null && keep.getTag(0x7f0a0001) != null ? (String) keep.getTag(0x7f0a0001) : null;

        // forside: nyeste film med plakat, ellers første
        Catalog.Ch pick = null;
        long tNow = System.currentTimeMillis();
        for (Search.Hit x : sport) if (x.start <= tNow && x.stop > tNow) { pick = Catalog.byId.get(x.chId); if (pick != null) { heroEvent = x; break; } }
        if (pick == null) { heroEvent = null; for (Integer id : Catalog.history(this)) { pick = Catalog.byId.get(id); if (pick != null) break; } }
        if (pick == null && !favs.isEmpty()) pick = favs.get(0);
        if (pick != null) setLive(pick);

        while (page.getChildCount() > 1) page.removeViewAt(1);
        View restore = null;
        if (!cont.isEmpty()) {
            LinearLayout r = row("Fortsæt", false);
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
        if (!sport.isEmpty()) {
            LinearLayout r = row("Sport i dag", true);
            long t0 = System.currentTimeMillis();
            for (final Search.Hit x : sport) {
                float p = x.start <= t0 ? Math.min(1f, (t0 - x.start) / (float) Math.max(1, x.stop - x.start)) : 0;
                LinearLayout c = Poster.logoCard(this, px, px.p(390), px.p(219), x.img, x.name, x.sub, p, null);
                View box = Poster.box(c);
                box.setTag(0x7f0a0001, "s" + x.chId + x.name);
                box.setOnClickListener(v -> playChannel(x.chId));
                add(r, c);
                if (("s" + x.chId + x.name).equals(keepTag)) restore = box;
            }
        }
        if (!favs.isEmpty()) {
            LinearLayout r = row(Catalog.favorites(this).isEmpty() ? "Live nu" : "Live nu · favoritter", true);
            final int[] ids = new int[favs.size()];
            for (int i = 0; i < ids.length; i++) ids[i] = favs.get(i).id;
            long t = System.currentTimeMillis();
            for (int i = 0; i < Math.min(16, favs.size()); i++) {
                final int idx = i;
                Catalog.Ch ch = favs.get(i);
                Epg.want(this, ch.id);
                Xtream.Prog n = Epg.now(ch.id);
                LinearLayout c = Poster.logoCard(this, px, px.p(390), px.p(219), ch.logo, ch.name, n != null ? n.title : " ", n != null ? n.progress(t) : 0, null);
                View box = Poster.box(c);
                box.setTag(0x7f0a0001, "l" + ch.id);
                box.setOnClickListener(v -> startActivity(new Intent(this, PlayerActivity.class).putExtra("ids", ids).putExtra("i", idx).putExtra("cat", LiveActivity.FAV)));
                add(r, c);
                if (("l" + ch.id).equals(keepTag)) restore = box;
            }
        }
        restore = posterRow("Nyt i film", movies, keepTag, restore);
        restore = posterRow("Nyt i serier", series, keepTag, restore);
        View spacer = new View(this);
        page.addView(spacer, new LinearLayout.LayoutParams(1, px.p(60)));
        if (restore != null) restore.requestFocus();
        else if (keep == null || !keep.isShown() || !firstFocusDone) {
            if (buttons.getChildCount() > 0) { buttons.getChildAt(0).requestFocus(); firstFocusDone = true; }
        }
    }

    private View posterRow(String name, List<Vod.Item> items, String keepTag, View restore) {
        if (items.isEmpty()) return restore;
        LinearLayout r = row(name, false);
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

    private LinearLayout row(String name, boolean live) {
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
        page.addView(head);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setFocusable(false);
        hs.setClipToPadding(false);
        hs.setPadding(px.p(96), px.p(28), px.p(96), px.p(26));
        LinearLayout r = new LinearLayout(this);
        r.setClipChildren(false);
        hs.addView(r);
        page.addView(hs);
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
        String ttl = heroEvent != null ? heroEvent.name : n != null ? n.title : ch.name;
        long st = heroEvent != null ? heroEvent.start : n != null ? n.start : 0, en = heroEvent != null ? heroEvent.stop : n != null ? n.stop : 0;
        kicker.setText(("LIVE · " + (heroEvent != null && k != null ? k.name : ch.name)).toUpperCase(Fmt.DA));
        title.setText(ttl);
        heroStart.setText(st > 0 ? Fmt.hm(st) : "");
        heroEnd.setText(en > 0 ? Fmt.hm(en) : "");
        heroBar.setVisibility(st > 0 ? View.VISIBLE : View.INVISIBLE);
        heroBar.set(st > 0 ? (t - st) / (float) Math.max(1, en - st) : 0);
        meta.setText(heroEvent != null ? "Kamp i dag" : x != null ? "Derefter " + Fmt.hm(x.start) + "  " + x.title : k != null ? k.name : "");
        plot.setText(heroEvent == null && n != null && n.desc != null ? n.desc : "");
        if (changed || buttons.getChildCount() == 0) {
            buttons.removeAllViews();
            TextView watch = Ui.pill(this, px, "▶  Se nu");
            watch.setOnClickListener(v -> playChannel(ch.id));
            buttons.addView(watch);
            TextView guide = Ui.pill(this, px, "Guide");
            guide.setOnClickListener(v -> startActivity(new Intent(this, LiveActivity.class).putExtra("catName", k != null ? k.name : null)));
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2);
            gl.leftMargin = px.p(18);
            buttons.addView(guide, gl);
            upd = null;
            updatePill();
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
