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

import java.util.ArrayList;
import java.util.List;

/** Fanen "Univers": sæsontemaer og julekalendere øverst, derefter universerne gruppe for gruppe. Med "key" vises ét univers i rækkefølge. */
public class UniversActivity extends Activity {
    private static final int CW = 380, CH = 214, PW = 180, PH = 270, PER_ROW = 8;
    private Px px;
    private Ui.TopBar top;
    private TextView title, sub;
    private LinearLayout rows;
    private String key, shown = "", focusKey;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable rebuild = this::rebuild;
    private final Runnable onData = () -> { h.removeCallbacks(rebuild); h.postDelayed(rebuild, 150); };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        px = new Px(this);
        key = getIntent().getStringExtra("key");
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        top = Ui.topBar(this, px, Nav.TABS, Nav.UNIVERS);
        root.addView(top.root, Ui.at(px.p(96), px.p(42), px.p(1728), -2));
        Nav.wire(this, top, Nav.UNIVERS, null);
        title = px.t(54, 700, 0xFFFFFFFF);
        root.addView(title, Ui.at(px.p(96), px.p(140), px.p(1600), -2));
        sub = px.t(21, 400, Theme.white(0.65f));
        root.addView(sub, Ui.at(px.p(98), px.p(214), px.p(1600), -2));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFocusable(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, px.p(10), 0, px.p(80));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        sv.addView(rows);
        root.addView(sv, Ui.at(0, px.p(262), -1, px.p(818)));
    }

    @Override protected void onResume() {
        super.onResume();
        Catalog.listeners.add(onData);
        top.clock.setText(Fmt.clock());
        shown = "";
        rebuild();
    }

    @Override protected void onPause() { super.onPause(); Catalog.listeners.remove(onData); }

    private void rebuild() {
        Coll.load(this);
        List<Coll.List1> ls = Coll.lists;
        String st = ls.size() + "|" + Coll.jul + "|" + Coll.building + "|" + key;
        if (st.equals(shown)) return;
        shown = st;
        rows.removeAllViews();
        View restore = null;
        if (key != null) { // ét univers
            Coll.List1 L = Coll.find(key);
            if (L == null) { title.setText("Univers"); sub.setText(Coll.building ? "Finder titlerne …" : "Ikke fundet."); return; }
            title.setText(L.name);
            String ord = "historien".equals(L.order) ? "I den rækkefølge, historien foregår." : "udgivelserne".equals(L.order) ? "I den rækkefølge, de kom ud." : "nyeste først".equals(L.order) ? "Nyeste først." : "";
            sub.setText((ord + " " + L.note).trim() + (L.missing > 0 ? "  ·  " + L.missing + " findes ikke hos udbyderen" : ""));
            LinearLayout r = null;
            for (int i = 0; i < L.items.size(); i++) {
                if (i % PER_ROW == 0) { r = new LinearLayout(this); r.setClipChildren(false); r.setPadding(px.p(96), px.p(20), px.p(96), px.p(16)); rows.addView(r); }
                final Coll.Item x = L.items.get(i);
                LinearLayout c = Poster.card(this, px, px.p(PW), px.p(PH), x.img, x.n + ". " + x.name, x.sub, 0, () -> focusKey = x.id);
                Poster.box(c).setOnClickListener(v -> startActivity(new Intent(this, DetailActivity.class).putExtra("kind", x.kind).putExtra("id", x.id)
                        .putExtra("name", x.name).putExtra("poster", x.img).putExtra("ext", x.ext)));
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
                if (r.getChildCount() > 0) l.leftMargin = px.p(26);
                r.addView(c, l);
                if (x.id.equals(focusKey)) restore = Poster.box(c);
            }
        } else { // oversigt
            title.setText("Univers");
            sub.setText(Coll.lists.isEmpty() && !Coll.building ? "Universerne kræver Vores TV-serveren i huset." : "Film og serier i den rigtige rækkefølge – i den bedste udgave." + (Coll.building ? "  ·  finder flere …" : ""));
            List<View> now = new ArrayList<>();
            if (Coll.jul) {
                now.add(card("jul:film", "Julekalender", "24 julefilm pakket ind – én om dagen", null, new int[]{0xFFB3262F, 0xFF6E141B}, "🎁",
                        () -> startActivity(new Intent(this, JulActivity.class).putExtra("cal", "film"))));
                now.add(card("jul:born", "Børnenes julekalender", "Ludvig & Julemanden – dagens afsnit på dansk", null, new int[]{0xFF1F6F4A, 0xFF0B2418}, "🧸",
                        () -> startActivity(new Intent(this, JulActivity.class).putExtra("cal", "born"))));
            }
            for (Coll.List1 L : Coll.lists) if (L.season) now.add(uniCard(L));
            if (!now.isEmpty()) restore = section("Lige nu", now, restore);
            for (String g : Coll.groups) {
                List<View> cs = new ArrayList<>();
                for (Coll.List1 L : Coll.lists) if (!L.season && g.equals(L.group)) cs.add(uniCard(L));
                if (!cs.isEmpty()) restore = section(g, cs, restore);
            }
        }
        if (restore != null) restore.requestFocus();
        else if (getCurrentFocus() == null || !getCurrentFocus().isShown()) { View f = first(rows); if (f != null) f.requestFocus(); }
    }

    private View section(String name, List<View> cards, View restore) {
        TextView t = px.t(27, 600, 0xFFFFFFFF);
        t.setText(name);
        t.setPadding(px.p(96), px.p(18), 0, 0);
        rows.addView(t);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setFocusable(false);
        hs.setClipToPadding(false);
        hs.setPadding(px.p(96), px.p(22), px.p(96), px.p(18));
        LinearLayout r = new LinearLayout(this);
        r.setClipChildren(false);
        for (View c : cards) {
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(px.p(CW), px.p(CH));
            if (r.getChildCount() > 0) l.leftMargin = px.p(28);
            r.addView(c, l);
            if (c.getTag() != null && c.getTag().equals(focusKey)) restore = c;
        }
        hs.addView(r);
        rows.addView(hs);
        return restore;
    }

    private View uniCard(final Coll.List1 L) {
        String s = L.items.size() + " titler" + ("historien".equals(L.order) ? "  ·  historiens rækkefølge" : "");
        return card("u:" + L.key, L.name, s, L.covers, null, null, () -> startActivity(new Intent(this, UniversActivity.class).putExtra("key", L.key)));
    }

    /** Stort kort: plakat-collage (eller farve + symbol) med navn nederst. */
    private View card(String tag, String name, String sub, List<String> covers, int[] colors, String icon, Runnable open) {
        FrameLayout box = new FrameLayout(this);
        box.setTag(tag);
        box.setFocusable(true);
        box.setClipToOutline(true);
        GradientDrawable bg = colors != null ? new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors) : new GradientDrawable();
        if (colors == null) bg.setColor(0xFF1F1F24);
        bg.setCornerRadius(px.p(18));
        box.setBackground(bg);
        if (covers != null && !covers.isEmpty()) {
            LinearLayout strip = new LinearLayout(this);
            for (int i = 0; i < 4; i++) {
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if (i < covers.size()) Img.load(iv, covers.get(i), px.p(CW / 4f));
                strip.addView(iv, new LinearLayout.LayoutParams(0, -1, 1));
            }
            box.addView(strip, new FrameLayout.LayoutParams(-1, -1));
        }
        if (icon != null) {
            TextView ic = px.t(64, 400, 0xFFFFFFFF);
            ic.setText(icon);
            FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            il.topMargin = px.p(14); il.rightMargin = px.p(20);
            box.addView(ic, il);
        }
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xF0000000, 0x00000000}));
        box.addView(shade, new FrameLayout.LayoutParams(-1, px.p(130), Gravity.BOTTOM));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        TextView n = px.t(26, 700, 0xFFFFFFFF);
        n.setText(name);
        tx.addView(n);
        TextView s = px.t(16, 400, Theme.white(0.75f));
        s.setText(sub);
        tx.addView(s);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        tl.leftMargin = tl.rightMargin = px.p(18); tl.bottomMargin = px.p(14);
        box.addView(tx, tl);
        box.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).translationZ(has ? px.p(30) : 0).setDuration(180).start();
            bg.setStroke(has ? px.p(4) : 0, 0xFFFFFFFF);
            if (has) focusKey = tag;
        });
        box.setOnClickListener(v -> open.run());
        return box;
    }

    private View first(View v) {
        if (v.isFocusable() && !(v instanceof HorizontalScrollView) && !(v instanceof ScrollView)) return v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) { View f = first(g.getChildAt(i)); if (f != null) return f; }
        }
        return null;
    }
}
