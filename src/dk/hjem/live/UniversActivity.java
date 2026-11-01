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
    private FrameLayout root;
    private ImageView bgA, bgB;
    private View heroShade;
    private LinearLayout acts;
    private android.webkit.WebView theme;
    private org.json.JSONObject uni; // billeder, klip og temamusik for ét univers
    private final List<String> bds = new ArrayList<>();
    private int bdAt;
    private final Runnable rebuild = this::rebuild;
    private final Runnable onData = () -> { h.removeCallbacks(rebuild); h.postDelayed(rebuild, 150); };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        px = new Px(this);
        key = getIntent().getStringExtra("key");
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        if (key != null) { // baggrundsbilleder fra universets film, der glider over i hinanden
            bgA = new ImageView(this); bgB = new ImageView(this);
            for (ImageView iv : new ImageView[]{bgA, bgB}) { iv.setScaleType(ImageView.ScaleType.CENTER_CROP); iv.setAlpha(0f); root.addView(iv, Ui.at(0, 0, -1, px.p(700))); }
            heroShade = new View(this);
            heroShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{Ui.BG, 0x990B0B0D, 0x400B0B0D}));
            root.addView(heroShade, Ui.at(0, 0, -1, px.p(702)));
            View left = new View(this);
            left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xD90B0B0D, 0x000B0B0D}));
            root.addView(left, Ui.at(0, 0, px.p(1100), px.p(700)));
        }
        top = Ui.topBar(this, px, Nav.TABS, Nav.UNIVERS);
        root.addView(top.root, Ui.at(px.p(96), px.p(42), px.p(1728), -2));
        Nav.wire(this, top, Nav.UNIVERS, null);
        title = px.t(54, 700, 0xFFFFFFFF);
        boolean one = key != null;
        if (one) title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px.p(78));
        root.addView(title, Ui.at(px.p(96), px.p(one ? 200 : 140), px.p(1600), -2));
        sub = px.t(21, 400, Theme.white(one ? 0.8f : 0.65f));
        root.addView(sub, Ui.at(px.p(98), px.p(one ? 306 : 214), px.p(1300), -2));
        if (one) {
            acts = new LinearLayout(this);
            root.addView(acts, Ui.at(px.p(96), px.p(360), -2, -2));
        }
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFocusable(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, px.p(10), 0, px.p(80));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        sv.addView(rows);
        root.addView(sv, Ui.at(0, px.p(one ? 450 : 262), -1, px.p(one ? 630 : 818)));
        if (one) loadUniverse();
    }

    // ---- ét univers: billeder, klip og temamusik fra serveren ----
    private void loadUniverse() {
        Bg.run("univers", () -> {
            try {
                String ip = Sync.webServer(this);
                if (ip == null) return;
                java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL("http://" + ip + ":" + Web.PORT + "/api/vod/universe?key=" + java.net.URLEncoder.encode(key, "UTF-8")).openConnection();
                con.setConnectTimeout(2000); con.setReadTimeout(8000);
                StringBuilder sb = new StringBuilder();
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(con.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    char[] buf = new char[8192]; int n;
                    while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                } finally { con.disconnect(); }
                final org.json.JSONObject o = new org.json.JSONObject(sb.toString());
                h.post(() -> { if (!isFinishing()) { uni = o; showMedia(); shown = ""; rebuild(); } });
                if (o.optBoolean("mediaBuilding")) h.postDelayed(this::loadUniverse, 10_000); // billederne hentes første gang
            } catch (Exception ignored) { }
        });
    }

    private void showMedia() {
        org.json.JSONObject m = uni.optJSONObject("media");
        org.json.JSONArray a = m != null ? m.optJSONArray("backdrops") : null;
        boolean first = bds.isEmpty();
        bds.clear();
        for (int i = 0; a != null && i < a.length(); i++) bds.add(a.optString(i));
        if (bds.isEmpty()) { org.json.JSONArray c = uni.optJSONArray("covers"); for (int i = 0; c != null && i < c.length(); i++) bds.add(c.optString(i)); }
        if (first && !bds.isEmpty()) { bdAt = -1; h.removeCallbacks(nextBd); nextBd.run(); }
        // knapper: start fra nr. 1 + temamusik til/fra
        acts.removeAllViews();
        Coll.List1 L = Coll.find(key);
        if (L != null && !L.items.isEmpty()) {
            final Coll.Item x = L.items.get(0);
            TextView go = Ui.pill(this, px, "▶  Start med nr. 1");
            go.setOnClickListener(v -> startActivity(new Intent(this, DetailActivity.class).putExtra("kind", x.kind).putExtra("id", x.id).putExtra("name", x.name).putExtra("poster", x.img).putExtra("ext", x.ext)));
            acts.addView(go);
        }
        final String tid = uni.optString("theme", "");
        if (!tid.isEmpty()) {
            final TextView mu = Ui.pill(this, px, musicOn() ? "🔊  Tema" : "🔇  Tema");
            mu.setOnClickListener(v -> {
                boolean on = !musicOn();
                getSharedPreferences("univers", MODE_PRIVATE).edit().putBoolean("music", on).apply();
                mu.setText(on ? "🔊  Tema" : "🔇  Tema");
                if (on) playTheme(tid); else stopTheme();
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
            l.leftMargin = px.p(18);
            acts.addView(mu, l);
            if (musicOn() && theme == null) playTheme(tid);
        }
    }

    private final Runnable nextBd = new Runnable() {
        @Override public void run() {
            if (bds.isEmpty() || isFinishing()) return;
            bdAt = (bdAt + 1) % bds.size();
            final ImageView in = bdAt % 2 == 0 ? bgA : bgB, out = in == bgA ? bgB : bgA;
            Img.load(in, bds.get(bdAt), px.p(1920));
            in.setScaleX(1.1f); in.setScaleY(1.1f);
            in.animate().alpha(1f).setDuration(1600).start();
            in.animate().scaleX(1f).scaleY(1f).setDuration(12000).start(); // langsom "Ken Burns"-zoom
            out.animate().alpha(0f).setDuration(1600).start();
            if (bds.size() > 1) h.postDelayed(this, 7000);
        }
    };

    private boolean musicOn() { return getSharedPreferences("univers", MODE_PRIVATE).getBoolean("music", true); }

    /** Temamusikken: officiel koncertoptagelse på YouTube, afspillet i en usynlig WebView. */
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private void playTheme(String id) {
        stopTheme();
        theme = new android.webkit.WebView(this);
        theme.getSettings().setJavaScriptEnabled(true);
        theme.getSettings().setMediaPlaybackRequiresUserGesture(false);
        theme.setFocusable(false);
        String html = "<!doctype html><html><body style='margin:0;background:#000'><iframe width='320' height='180' frameborder='0' allow='autoplay; encrypted-media' referrerpolicy='strict-origin-when-cross-origin' "
                + "src='https://www.youtube-nocookie.com/embed/" + id + "?autoplay=1&loop=1&playlist=" + id + "&controls=0&playsinline=1'></iframe></body></html>";
        theme.loadDataWithBaseURL("https://tv.simonp.dk/", html, "text/html", "UTF-8", null);
        root.addView(theme, 0, new FrameLayout.LayoutParams(px.p(4), px.p(4))); // bagerst og bitte lille: kun lyden bruges
    }

    private void stopTheme() {
        if (theme == null) return;
        root.removeView(theme);
        theme.loadUrl("about:blank");
        theme.destroy();
        theme = null;
    }

    @Override protected void onStop() {
        super.onStop();
        stopTheme(); // musikken stopper, når man går videre til en film
        h.removeCallbacks(nextBd);
    }

    @Override protected void onRestart() {
        super.onRestart();
        if (uni != null) {
            String tid = uni.optString("theme", "");
            if (!tid.isEmpty() && musicOn()) playTheme(tid);
            if (!bds.isEmpty()) { h.removeCallbacks(nextBd); nextBd.run(); }
        }
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
        String st = ls.size() + "|" + Coll.jul + "|" + Coll.building + "|" + key + "|" + (uni != null ? uni.optBoolean("mediaBuilding") + "" + uni.optJSONObject("media") : "");
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
            org.json.JSONObject m = uni != null ? uni.optJSONObject("media") : null;
            org.json.JSONArray tr = m != null ? m.optJSONArray("trailers") : null;
            if (tr != null && tr.length() > 0) { // klip: officielle trailere (åbnes i YouTube)
                List<View> clips = new ArrayList<>();
                for (int i = 0; i < tr.length(); i++) {
                    final org.json.JSONObject t = tr.optJSONObject(i);
                    final String yt = t.optString("id");
                    List<String> img = new ArrayList<>();
                    img.add("https://i.ytimg.com/vi/" + yt + "/hqdefault.jpg");
                    View cv = card("t:" + yt, "▶  " + t.optString("name"), "Trailer  ·  " + t.optInt("year"), img, null, null, () -> {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=" + yt))); }
                        catch (Exception e) { android.widget.Toast.makeText(this, "YouTube-appen mangler på dette tv", android.widget.Toast.LENGTH_SHORT).show(); }
                    });
                    clips.add(cv);
                }
                restore = section("Klip", clips, restore);
            }
            TextView all = px.t(27, 600, 0xFFFFFFFF);
            all.setText("Alle titler");
            all.setPadding(px.p(96), px.p(18), 0, 0);
            rows.addView(all);
            LinearLayout r = null;
            for (int i = 0; i < L.items.size(); i++) {
                if (i % PER_ROW == 0) { r = new LinearLayout(this); r.setClipChildren(false); r.setPadding(px.p(96), px.p(20), px.p(96), px.p(16)); rows.addView(r); }
                final Coll.Item x = L.items.get(i);
                LinearLayout c = Poster.card(this, px, px.p(PW), px.p(PH), x.img, x.n + ". " + x.name, x.sub, 0, () -> focusKey = x.id);
                Poster.box(c).setOnClickListener(v -> { Coll.rememberAudio(this, x); startActivity(new Intent(this, DetailActivity.class).putExtra("kind", x.kind).putExtra("id", x.id)
                        .putExtra("name", x.name).putExtra("poster", x.img).putExtra("ext", x.ext)); });
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
            int k = covers.size() == 1 ? 1 : 4; // ét billede (klip) fylder hele kortet
            for (int i = 0; i < k; i++) {
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if (i < covers.size()) Img.load(iv, covers.get(i), px.p(CW / (float) k));
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
