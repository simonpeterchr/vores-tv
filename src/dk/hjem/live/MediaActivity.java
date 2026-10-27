package dk.hjem.live;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Film eller serier (design 1a-stil): stor titel øverst for det markerede, plakat-rækker pr. gruppe. */
public class MediaActivity extends Activity {
    static final int PW = 200, PH = 300, ROW_MAX = 60;
    private Px px;
    private String kind;
    private FrameLayout root;
    private Ui.TopBar top;
    private ImageView backdrop;
    private TextView kicker, title, meta, hint;
    private LinearLayout rows;
    private ScrollView scroll;
    private GroupPicker picker;
    private String shown = "", focusKey;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable rebuild = this::rebuild;
    private final Runnable onData = () -> { h.removeCallbacks(rebuild); h.postDelayed(rebuild, 150); };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        px = new Px(this);
        kind = getIntent().getStringExtra("kind");
        if (kind == null) kind = Vod.MOVIE;
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);

        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0.55f);
        root.addView(backdrop, Ui.at(px.p(820), 0, px.p(1100), px.p(620)));
        View fadeL = new View(this);
        fadeL.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Ui.BG, 0x00000000}));
        root.addView(fadeL, Ui.at(px.p(820), 0, px.p(600), px.p(620)));
        View fadeB = new View(this);
        fadeB.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{Ui.BG, 0x00000000}));
        root.addView(fadeB, Ui.at(px.p(820), px.p(300), px.p(1100), px.p(320)));

        int tab = Vod.MOVIE.equals(kind) ? Nav.MOVIES : Nav.SERIES;
        top = Ui.topBar(this, px, Nav.TABS, tab);
        root.addView(top.root, Ui.at(px.p(96), px.p(42), px.p(1728), -2));
        Nav.wire(this, top, tab, null);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        kicker = px.t(18, 600, Theme.white(0.65f));
        kicker.setLetterSpacing(0.12f);
        hero.addView(kicker);
        title = px.t(78, 700, 0xFFFFFFFF);
        title.setLetterSpacing(-0.035f);
        hero.addView(title, lp(14));
        meta = px.t(22.5f, 400, Theme.white(0.75f));
        hero.addView(meta, lp(16));
        hint = px.t(20, 400, Theme.white(0.5f));
        hero.addView(hint, lp(14));
        root.addView(hero, Ui.at(px.p(96), px.p(170), px.p(1100), -2));

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, px.p(44), 0, px.p(80));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        scroll.addView(rows);
        root.addView(scroll, Ui.at(0, px.p(456), -1, px.p(624)));
        Vod.loadCategories(this, kind);
    }

    private LinearLayout.LayoutParams lp(float topM) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        l.topMargin = px.p(topM);
        return l;
    }

    @Override protected void onResume() {
        super.onResume();
        Catalog.listeners.add(onData);
        top.clock.setText(Fmt.clock());
        shown = "";
        rebuild();
    }

    @Override protected void onPause() {
        super.onPause();
        Catalog.listeners.remove(onData);
    }

    private void rebuild() {
        if (picker != null) return;
        List<String> groups = Vod.shownGroups(this, kind);
        List<Vod.Resume> cont = Vod.continueList(this, kind);
        StringBuilder st = new StringBuilder(Vod.categories(kind).size() + "|" + cont.size() + "|");
        for (String g : groups) st.append(g).append(':').append(Vod.inCategory(this, kind, g).size()).append(',');
        for (Vod.Resume r : cont) st.append(r.key).append(r.pos / 60000);
        if (st.toString().equals(shown)) return;
        shown = st.toString();
        rows.removeAllViews();
        View restore = null;
        String word = Vod.MOVIE.equals(kind) ? "film" : "serier";
        if (Vod.categories(kind).isEmpty()) {
            setHero("", "Henter " + word + " …", "", "");
            return;
        }
        if (!cont.isEmpty()) {
            LinearLayout r = row("Fortsæt");
            for (Vod.Resume rv : cont) {
                final Vod.Resume x = rv;
                String sub = Vod.SERIES.equals(kind) ? rv.sub : Fmt.mins(rv.dur - rv.pos) + " tilbage";
                LinearLayout c = Poster.card(this, px, px.p(PW), px.p(PH), rv.poster, rv.title, sub, rv.progress(),
                        () -> { focusKey = "r:" + x.key; setHero("FORTSÆT", x.title, sub, "OK fortsætter afspilningen"); });
                Poster.box(c).setOnClickListener(v -> resume(x));
                add(r, c);
                if (("r:" + x.key).equals(focusKey)) restore = Poster.box(c);
            }
        }
        for (String g : groups) {
            Vod.Cat k = Vod.categories(kind).get(g);
            List<Vod.Item> items = Vod.inCategory(this, kind, g);
            if (k == null) continue;
            LinearLayout r = row(k.name);
            if (items.isEmpty()) { TextView t = px.t(20, 400, Theme.white(0.4f)); t.setText("Henter …"); r.addView(t); continue; }
            final String gname = k.name;
            for (int i = 0; i < Math.min(ROW_MAX, items.size()); i++) {
                final Vod.Item it = items.get(i);
                String sub = (it.year != null ? it.year : "") + (it.rating != null && !it.rating.isEmpty() && !it.rating.equals("0") ? "  ★ " + it.rating : "");
                LinearLayout c = Poster.card(this, px, px.p(PW), px.p(PH), it.poster, it.name, sub, 0,
                        () -> { focusKey = it.kind + it.id; setHero(gname.toUpperCase(Fmt.DA), it.name, sub.trim(), "OK for detaljer"); Img.load(backdrop, it.poster, px.p(600)); });
                Poster.box(c).setOnClickListener(v -> open(it));
                add(r, c);
                if ((it.kind + it.id).equals(focusKey)) restore = Poster.box(c);
            }
        }
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(px.p(96), px.p(20), px.p(96), 0);
        TextView all = Ui.pill(this, px, groups.isEmpty() ? "Vælg grupper …" : "Alle grupper …");
        all.setOnClickListener(v -> openGroups());
        bar.addView(all);
        rows.addView(bar);
        if (groups.isEmpty()) setHero("", "Ingen grupper valgt", "Vælg hvilke " + word + "-grupper I vil se.", "");
        View cf = getCurrentFocus();
        boolean inRows = cf != null && cf.isShown() && isInside(cf, rows);
        if (restore != null) restore.requestFocus();
        else if (!inRows) {
            View first = groups.isEmpty() && cont.isEmpty() ? null : firstFocusable(rows);
            (first != null ? first : all).requestFocus();
        }
    }

    private static boolean isInside(View v, View parent) {
        for (android.view.ViewParent p = v.getParent(); p != null; p = p.getParent()) if (p == parent) return true;
        return false;
    }

    private View firstFocusable(View v) {
        if (v.isFocusable() && v.getVisibility() == View.VISIBLE && !(v instanceof HorizontalScrollView) && !(v instanceof ScrollView)) return v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) { View f = firstFocusable(g.getChildAt(i)); if (f != null) return f; }
        }
        return null;
    }

    private LinearLayout row(String name) {
        TextView t = px.t(27, 600, 0xFFFFFFFF);
        t.setText(name);
        t.setPadding(px.p(96), px.p(18), 0, 0);
        rows.addView(t);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setFocusable(false);
        hs.setClipToPadding(false);
        hs.setPadding(px.p(96), px.p(26), px.p(96), px.p(20));
        LinearLayout r = new LinearLayout(this);
        r.setClipChildren(false);
        hs.addView(r);
        rows.addView(hs);
        return r;
    }

    private void add(LinearLayout r, LinearLayout c) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        if (r.getChildCount() > 0) l.leftMargin = px.p(30);
        r.addView(c, l);
    }

    private void setHero(String k, String t, String m, String hh) {
        kicker.setText(k); title.setText(t); meta.setText(m); hint.setText(hh);
    }

    private void open(Vod.Item it) {
        startActivity(new Intent(this, DetailActivity.class).putExtra("kind", it.kind).putExtra("id", it.id)
                .putExtra("name", it.name).putExtra("poster", it.poster).putExtra("ext", it.ext).putExtra("year", it.year));
    }

    private void resume(Vod.Resume r) {
        if (Vod.SERIES.equals(r.kind)) {
            startActivity(new Intent(this, DetailActivity.class).putExtra("kind", Vod.SERIES).putExtra("id", r.seriesId)
                    .putExtra("name", r.title).putExtra("poster", r.poster));
        } else {
            startActivity(new Intent(this, VodPlayerActivity.class).putExtra("kind", Vod.MOVIE).putExtra("ids", new String[]{r.id})
                    .putExtra("titles", new String[]{r.title}).putExtra("subs", new String[]{""}).putExtra("exts", new String[]{r.ext})
                    .putExtra("poster", r.poster).putExtra("resume", true));
        }
    }

    private void openGroups() {
        picker = new GroupPicker(this, px, GroupPicker.vod(this, kind), () -> {
            root.removeView(picker);
            picker = null;
            shown = "";
            rebuild();
        });
        root.addView(picker, new FrameLayout.LayoutParams(-1, -1));
        picker.requestFocus();
    }
}
