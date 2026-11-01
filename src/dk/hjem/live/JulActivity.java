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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Julekalender: 24 låger med en julefilm bag hver. Lågen kan åbnes fra sin dato; filmen er en overraskelse indtil da. */
public class JulActivity extends Activity {
    private static final int COLS = 8, DW = 190, DH = 236, GAP = 22, GOLD = 0xFFF3C76A;
    private static final int[] PAPER = {0xFFB3262F, 0xFF1F6F4A, 0xFFC8A14A, 0xFF2A4F8F, 0xFF8E1B24, 0xFF3F7D3A, 0xFFA33A6B, 0xFF245E73};
    private static final int[] RIBBON = {0xFFF3C76A, 0xFFF6F1E7, 0xFFB3262F, 0xFFF3C76A, 0xFFF6F1E7, 0xFFF3C76A, 0xFFF6F1E7, 0xFFF3C76A};

    private static int darker(int c) { return 0xFF000000 | ((int) (((c >> 16) & 255) * .78) << 16) | ((int) (((c >> 8) & 255) * .78) << 8) | (int) ((c & 255) * .78); }
    private Px px;
    private FrameLayout root, grid;
    private Ui.TopBar top;
    private TextView sub;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int focusDay;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        px = new Px(this);
        root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF123524, 0xFF0B2418, 0xFF0B0B0D}));
        setContentView(root);
        top = Ui.topBar(this, px, Nav.TABS, Nav.JUL);
        root.addView(top.root, Ui.at(px.p(96), px.p(42), px.p(1728), -2));
        Nav.wire(this, top, Nav.JUL, null);
        TextView t = px.t(54, 700, 0xFFFFFFFF);
        t.setText("❄  Julekalender  ❄");
        root.addView(t, Ui.at(px.p(96), px.p(140), -2, -2));
        sub = px.t(22, 400, Theme.white(0.72f));
        sub.setText("Henter kalenderen …");
        root.addView(sub, Ui.at(px.p(98), px.p(214), px.p(1500), -2));
        grid = new FrameLayout(this);
        grid.setClipChildren(false);
        root.setClipChildren(false);
        root.addView(grid, Ui.at(px.p(96), px.p(286), px.p(1728), px.p(760)));
        root.addView(new Snow(this), new FrameLayout.LayoutParams(-1, -1)); // sne ovenpå det hele (tager ikke fokus)
    }

    /** Sne, der daler stille ned; tegnes kun mens skærmen er synlig. */
    static final class Snow extends View {
        private final float[] x = new float[90], y = new float[90], r = new float[90], sp = new float[90], ph = new float[90];
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final java.util.Random rnd = new java.util.Random();
        private boolean init;
        Snow(android.content.Context c) { super(c); setFocusable(false); paint.setColor(0xFFFFFFFF); }
        @Override protected void onDraw(android.graphics.Canvas c) {
            int w = getWidth(), hgt = getHeight();
            if (w == 0) return;
            float s = w / 1920f;
            if (!init) { for (int i = 0; i < x.length; i++) { x[i] = rnd.nextFloat() * w; y[i] = rnd.nextFloat() * hgt; r[i] = (1.2f + rnd.nextFloat() * 3.4f) * s; sp[i] = (0.6f + rnd.nextFloat() * 1.6f) * s; ph[i] = rnd.nextFloat() * 6.28f; } init = true; }
            for (int i = 0; i < x.length; i++) {
                y[i] += sp[i]; ph[i] += 0.012f; x[i] += (float) Math.sin(ph[i]) * 0.6f * s;
                if (y[i] > hgt + 6) { y[i] = -6; x[i] = rnd.nextFloat() * w; }
                paint.setAlpha((int) (120 + r[i] / s * 30));
                c.drawCircle(x[i], y[i], r[i], paint);
            }
            postInvalidateOnAnimation();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        top.clock.setText(Fmt.clock());
        load();
    }

    private void load() {
        Bg.run("jul", () -> {
            JSONObject d = null;
            String err = null;
            try {
                String ip = Sync.webServer(this);
                if (ip == null) err = "Julekalenderen kræver Vores TV-serveren i huset.";
                else {
                    d = new JSONObject(get("http://" + ip + ":" + Web.PORT + "/api/jul"));
                    if (!Search.acctOk(this, d.optString("acct"))) { d = null; err = "Serveren bruger et andet IPTV-login end dette tv."; }
                }
            } catch (Exception e) { err = "Kunne ikke hente kalenderen."; }
            final JSONObject fd = d; final String fe = err;
            h.post(() -> { if (!isFinishing()) { if (fd != null) draw(fd); else sub.setText(fe); } });
        });
    }

    private void draw(JSONObject d) {
        int open = d.optInt("open"), today = d.optInt("today");
        if (open == 0) {
            java.util.Calendar dec = java.util.Calendar.getInstance();
            dec.set(d.optInt("year"), java.util.Calendar.DECEMBER, 1, 0, 0, 0);
            long days = (long) Math.ceil((dec.getTimeInMillis() - System.currentTimeMillis()) / 86400000.0);
            sub.setText("Gaverne må pakkes ud fra 1. december" + (days > 0 ? " – om " + days + (days == 1 ? " dag" : " dage") : "") + ".  I hver gave ligger en julefilm.");
        } else sub.setText(today > 0 ? today + ". december  ·  vælg dagens gave og tryk OK for at pakke den ud 🎁" : "Alle gaver kan pakkes ud resten af julen");
        grid.removeAllViews();
        JSONArray doors = d.optJSONArray("doors");
        View restore = null, todayV = null, first = null;
        for (int i = 0; doors != null && i < doors.length(); i++) {
            JSONObject x = doors.optJSONObject(i);
            View v = door(x);
            int c = i % COLS, r = i / COLS;
            grid.addView(v, Ui.at(px.p(c * (DW + GAP)), px.p(r * (DH + GAP)), px.p(DW), px.p(DH)));
            int day = x.optInt("day");
            if (day == focusDay) restore = v;
            if (x.optBoolean("today")) todayV = v;
            if (first == null) first = v;
        }
        View f = restore != null ? restore : todayV != null ? todayV : first;
        if (f != null) f.requestFocus();
    }

    private View door(final JSONObject x) {
        final int day = x.optInt("day");
        final boolean opened = x.optBoolean("opened"), can = x.optBoolean("canOpen"), today = x.optBoolean("today");
        final JSONObject film = x.optJSONObject("film");
        final FrameLayout box = new FrameLayout(this);
        box.setFocusable(true);
        box.setClipToOutline(true);
        final int paper = PAPER[day % PAPER.length], ribbon = RIBBON[day % RIBBON.length];
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, opened ? new int[]{0xFF141414, 0xFF141414} : new int[]{paper, darker(paper)});
        bg.setCornerRadius(px.p(12));
        bg.setStroke(px.p(today ? 4 : opened ? 2 : 0), today ? GOLD : 0x59F3C76A);
        box.setBackground(bg);
        if (!opened && !can) box.setAlpha(0.55f);
        TextView num = px.t(opened ? 30 : 58, 800, GOLD);
        num.setText(String.valueOf(day));
        if (opened) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (film != null) Img.load(iv, film.optString("img", null), px.p(DW));
            box.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            View shade = new View(this);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xE6000000, 0x00000000}));
            FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-1, px.p(120), Gravity.BOTTOM);
            box.addView(shade, sl);
            TextView name = px.t(17, 650, 0xFFFFFFFF);
            name.setMaxLines(2);
            name.setText(film == null ? "" : film.optBoolean("missing") ? film.optString("name") + " (findes ikke)" : film.optString("name"));
            FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            nl.leftMargin = nl.rightMargin = px.p(10); nl.bottomMargin = px.p(10);
            box.addView(name, nl);
            FrameLayout.LayoutParams ql = new FrameLayout.LayoutParams(-2, -2);
            ql.leftMargin = px.p(10); ql.topMargin = px.p(4);
            box.addView(num, ql);
        } else { // en indpakket gave: bånd på kryds, sløjfe og et mærkat med datoen
            View rv = new View(this); rv.setBackgroundColor(ribbon);
            box.addView(rv, new FrameLayout.LayoutParams(px.p(26), -1, Gravity.CENTER_HORIZONTAL));
            View rh = new View(this); rh.setBackgroundColor(ribbon);
            FrameLayout.LayoutParams rhl = new FrameLayout.LayoutParams(-1, px.p(20));
            rhl.topMargin = px.p(52);
            box.addView(rh, rhl);
            TextView bow = px.t(46, 400, ribbon);
            bow.setText("🎀");
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL);
            bl.topMargin = px.p(18);
            box.addView(bow, bl);
            num.setTextColor(0xFF8E1B24);
            num.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px.p(40));
            num.setGravity(Gravity.CENTER);
            num.setBackground(Ui.round(0xFFF6F1E7, px.p(8)));
            num.setPadding(px.p(14), px.p(2), px.p(14), px.p(2));
            num.setRotation(-4);
            num.setElevation(px.p(4));
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
            tl.topMargin = px.p(20);
            box.addView(num, tl);
            box.setTag(new View[]{rv, rh, bow, num});
            if (can) {
                TextView hint = px.t(16, 600, Theme.white(0.85f));
                hint.setText(today ? "Pak ud i dag" : "Pak ud");
                hint.setShadowLayer(px.p(3), 0, px.p(1), 0xB3000000);
                FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                hl.bottomMargin = px.p(14);
                box.addView(hint, hl);
            }
        }
        box.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).translationZ(has ? px.p(30) : 0).setDuration(180).start();
            bg.setStroke(px.p(has ? 5 : today ? 4 : 2), has ? 0xFFFFFFFF : today ? GOLD : 0x59F3C76A);
            if (has) focusDay = day;
        });
        box.setOnClickListener(v -> {
            if (opened) {
                if (film == null || film.optBoolean("missing")) return;
                startActivity(new Intent(this, DetailActivity.class).putExtra("kind", film.optString("kind")).putExtra("id", film.optString("id"))
                        .putExtra("name", film.optString("name")).putExtra("poster", film.optString("img", null)).putExtra("ext", film.optString("ext", null)));
                return;
            }
            if (!can) {
                box.animate().translationX(px.p(8)).setDuration(60).withEndAction(() -> box.animate().translationX(-px.p(8)).setDuration(80).withEndAction(() -> box.animate().translationX(0).setDuration(60).start()).start()).start();
                Toast.makeText(this, "Gave " + day + " må først pakkes ud " + day + ". december", Toast.LENGTH_SHORT).show();
                return;
            }
            // pak ud: sløjfen flyver op, båndet glider væk, papiret folder sig sammen
            View[] parts = (View[]) box.getTag();
            if (parts != null) {
                parts[2].animate().translationY(-px.p(140)).rotation(40).alpha(0).setDuration(450).start();
                parts[0].animate().scaleY(0).setDuration(400).start();
                parts[1].animate().scaleX(0).setDuration(400).start();
                parts[3].animate().scaleX(1.3f).scaleY(1.3f).alpha(0).setStartDelay(250).setDuration(350).start();
            }
            box.animate().scaleX(1.18f).scaleY(1.18f).alpha(0f).setStartDelay(420).setDuration(380).start();
            Bg.run("jul", () -> {
                boolean ok;
                try {
                    String ip = Sync.webServer(this);
                    ok = ip != null && post("http://" + ip + ":" + Web.PORT + "/api/jul/open", "{\"day\":" + day + "}");
                } catch (Exception e) { ok = false; }
                final boolean fok = ok;
                h.postDelayed(() -> {
                    if (isFinishing()) return;
                    if (!fok) Toast.makeText(this, "Lågen kunne ikke åbnes lige nu", Toast.LENGTH_SHORT).show();
                    focusDay = day;
                    load();
                }, 600);
            });
        });
        return box;
    }

    private static String get(String u) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        con.setConnectTimeout(2000); con.setReadTimeout(8000);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buf = new char[8192]; int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        } finally { con.disconnect(); }
        return sb.toString();
    }

    private static boolean post(String u, String body) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        con.setConnectTimeout(2000); con.setReadTimeout(8000);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        try (OutputStream o = con.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
        int code = con.getResponseCode();
        con.disconnect();
        return code == 200;
    }
}
