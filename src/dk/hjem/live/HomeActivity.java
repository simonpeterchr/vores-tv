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
    private ImageView backdrop;
    private TextView kicker, title, meta, plot;
    private LinearLayout page, buttons;
    private Vod.Item hero;
    private boolean firstFocusDone;
    private String heroBackdropFor, shown = "";
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable rebuild = this::rebuild;
    private final Runnable onData = () -> { h.removeCallbacks(rebuild); h.postDelayed(rebuild, 200); };
    private final Runnable tick = new Runnable() { @Override public void run() { top.clock.setText(Fmt.clock()); h.postDelayed(this, 30_000); } };

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

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFocusable(false);
        sv.setClipToPadding(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        sv.addView(page);

        FrameLayout heroBox = new FrameLayout(this);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setCropToPadding(true);
        heroBox.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View g1 = new View(this);
        g1.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xD90B0B0D, 0x000B0B0D}));
        heroBox.addView(g1, new FrameLayout.LayoutParams(px.p(1150), -1));
        View g2 = new View(this);
        g2.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xFF0B0B0D, 0x000B0B0D}));
        heroBox.addView(g2, new FrameLayout.LayoutParams(-1, px.p(420), android.view.Gravity.BOTTOM));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.setClipChildren(false);
        kicker = px.t(18, 600, Theme.white(0.65f));
        kicker.setLetterSpacing(0.12f);
        ht.addView(kicker);
        title = px.t(87, 700, 0xFFFFFFFF);
        title.setLetterSpacing(-0.035f);
        title.setSingleLine(false);
        title.setMaxLines(2);
        title.setLineSpacing(0, 0.95f);
        ht.addView(title, lp(-2, 18));
        meta = px.t(22.5f, 400, Theme.white(0.75f));
        ht.addView(meta, lp(-2, 16));
        plot = px.t(24, 400, Theme.white(0.8f));
        plot.setSingleLine(false);
        plot.setMaxLines(2);
        plot.setLineSpacing(px.p(8), 1f);
        ht.addView(plot, lp(px.p(800), 18));
        buttons = new LinearLayout(this);
        buttons.setClipChildren(false);
        ht.addView(buttons, lp(-2, 30));
        heroBox.addView(ht, Ui.at(px.p(96), px.p(190), px.p(1150), -2));
        heroBox.setClipChildren(false);
        page.addView(heroBox, new LinearLayout.LayoutParams(-1, px.p(760)));
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        View topShade = new View(this);
        topShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF20B0B0D, 0x000B0B0D}));
        root.addView(topShade, new FrameLayout.LayoutParams(-1, px.p(190)));
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
        shown = "";
        rebuild();
        h.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        Catalog.listeners.remove(onData);
        h.removeCallbacks(tick);
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
        if (st.toString().equals(shown)) return;
        updatePill();
        shown = st.toString();
        View keep = getCurrentFocus();
        String keepTag = keep != null && keep.getTag(0x7f0a0001) != null ? (String) keep.getTag(0x7f0a0001) : null;

        // forside: nyeste film med plakat, ellers første
        Vod.Item pick = Feed.hero(this);
        if (pick != null && (hero == null || !hero.id.equals(pick.id))) setHero(pick);

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

    private void setHero(final Vod.Item it) {
        hero = it;
        Vod.Cat k = Vod.categories(Vod.MOVIE).get(it.cat);
        kicker.setText("NY FILM");
        title.setText(it.name);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px.p(it.name.length() > 22 ? 66 : 87));
        meta.setText((it.year != null ? it.year : "") + (it.rating != null && !it.rating.isEmpty() && !it.rating.equals("0") ? "   ★ " + it.rating : ""));
        plot.setText("");
        Img.load(backdrop, it.poster, px.p(1000));
        buttons.removeAllViews();
        TextView play = Ui.pill(this, px, "▶  Afspil");
        play.setOnClickListener(v -> startActivity(new Intent(this, VodPlayerActivity.class).putExtra("kind", Vod.MOVIE)
                .putExtra("ids", new String[]{it.id}).putExtra("titles", new String[]{it.name}).putExtra("subs", new String[]{meta.getText().toString()})
                .putExtra("exts", new String[]{it.ext}).putExtra("poster", it.poster).putExtra("resume", true)));
        buttons.addView(play);
        TextView more = Ui.pill(this, px, "Detaljer");
        more.setOnClickListener(v -> open(it));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
        ml.leftMargin = px.p(18);
        buttons.addView(more, ml);
        upd = null;
        updatePill();
        if (it.id.equals(heroBackdropFor)) return;
        heroBackdropFor = it.id;
        final Cred cr = Cred.load(this);
        new Thread(() -> {
            try {
                JSONObject info = new JSONObject(new Xtream(cr).get("get_vod_info", "&vod_id=" + it.id)).optJSONObject("info");
                if (info == null) return;
                JSONArray bd = info.optJSONArray("backdrop_path");
                final String back = bd != null && bd.length() > 0 ? bd.optString(0) : "";
                final String pl = info.optString("plot", "");
                final String genre = info.optString("genre", "").split(",")[0].trim();
                h.post(() -> {
                    if (hero != it) return;
                    if (!back.isEmpty()) Img.load(backdrop, back, px.p(1920));
                    plot.setText(pl);
                    if (!genre.isEmpty()) meta.setText(meta.getText() + "   ·   " + genre);
                });
            } catch (Exception ignored) { }
        }, "hero").start();
    }

    private TextView upd;

    /** "Opdatér til 0.21" ved siden af Afspil/Detaljer, når GitHub har en nyere version. */
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
