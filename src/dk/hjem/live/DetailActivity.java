package dk.hjem.live;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Detaljeside for film (1c) og serier (1d). */
public class DetailActivity extends Activity {
    private Px px;
    private String kind, id, name, poster, ext;
    private FrameLayout root;
    private ImageView backdrop;
    private TextView title, meta, plot, extra;
    private LinearLayout buttons, seasonRow, epRow;
    private HorizontalScrollView epScroll;
    private final Handler h = new Handler(Looper.getMainLooper());
    // serier
    static final class Ep { String id, ext, title, plot, img; int season, num; long durMs; }
    private final List<Integer> seasons = new ArrayList<>();
    private final java.util.Map<Integer, List<Ep>> eps = new java.util.TreeMap<>();
    private int season = -1;
    private long movieDur;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        px = new Px(this);
        Intent in = getIntent();
        kind = in.getStringExtra("kind"); id = in.getStringExtra("id"); name = in.getStringExtra("name");
        poster = in.getStringExtra("poster"); ext = in.getStringExtra("ext");
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0.6f);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        Img.load(backdrop, poster, px.p(900));
        View l = new View(this);
        l.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xF20B0B0D, 0x990B0B0D, 0x000B0B0D}));
        root.addView(l, new FrameLayout.LayoutParams(px.p(1400), -1));
        View bt = new View(this);
        bt.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xFF0B0B0D, 0x000B0B0D}));
        root.addView(bt, new FrameLayout.LayoutParams(-1, px.p(620), Gravity.BOTTOM));

        boolean series = Vod.SERIES.equals(kind);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        root.addView(col, Ui.at(px.p(96), px.p(series ? 120 : 260), px.p(1728), -2));
        title = px.t(84, 700, 0xFFFFFFFF);
        title.setLetterSpacing(-0.035f);
        title.setText(name);
        col.addView(title);
        meta = px.t(22.5f, 400, Theme.white(0.75f));
        col.addView(meta, lp(-2, 18));
        plot = px.t(24, 400, Theme.white(0.82f));
        plot.setSingleLine(false);
        plot.setMaxLines(series ? 3 : 4);
        plot.setLineSpacing(px.p(8), 1f);
        col.addView(plot, lpw(px.p(1050), 22));
        buttons = new LinearLayout(this);
        buttons.setClipChildren(false);
        col.addView(buttons, lp(-2, 36));
        extra = px.t(21, 400, Theme.white(0.55f));
        extra.setSingleLine(false);
        extra.setMaxLines(2);
        col.addView(extra, lpw(px.p(1300), 34));
        if (series) {
            HorizontalScrollView ss = new HorizontalScrollView(this);
            ss.setHorizontalScrollBarEnabled(false);
            ss.setClipToPadding(false);
            seasonRow = new LinearLayout(this);
            ss.addView(seasonRow);
            col.addView(ss, lp(-1, 20));
            epScroll = new HorizontalScrollView(this);
            epScroll.setHorizontalScrollBarEnabled(false);
            epScroll.setClipToPadding(false);
            epScroll.setPadding(0, px.p(30), px.p(96), px.p(20));
            epRow = new LinearLayout(this);
            epRow.setClipChildren(false);
            epScroll.addView(epRow);
            col.addView(epScroll, lp(-1, 4));
        }
        meta.setText(in.getStringExtra("year") != null ? in.getStringExtra("year") : "");
        load();
    }

    private LinearLayout.LayoutParams lp(int w, float top) { LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, -2); l.topMargin = px.p(top); return l; }
    private LinearLayout.LayoutParams lpw(int w, float top) { return lp(w, top); }

    @Override protected void onResume() {
        super.onResume();
        if (!eps.isEmpty() || movieDur >= 0) renderButtons();
    }

    private void load() {
        final Cred cr = Cred.load(this);
        final boolean series = Vod.SERIES.equals(kind);
        new Thread(() -> {
            try {
                String js = new Xtream(cr).get(series ? "get_series_info" : "get_vod_info", series ? "&series_id=" + id : "&vod_id=" + id);
                final JSONObject o = new JSONObject(js);
                h.post(() -> bind(o));
            } catch (Exception e) {
                h.post(() -> { plot.setText(Http.friendly(e)); renderButtons(); });
            }
        }, "detail").start();
    }

    private void bind(JSONObject o) {
        JSONObject info = o.optJSONObject("info");
        if (info == null) info = new JSONObject();
        JSONArray bd = info.optJSONArray("backdrop_path");
        if (bd != null && bd.length() > 0 && !bd.optString(0).isEmpty()) Img.load(backdrop, bd.optString(0), px.p(1920));
        String nm = info.optString("name", "");
        if (!nm.isEmpty() && (name == null || name.isEmpty())) title.setText(Names.channel(nm));
        List<String> m = new ArrayList<>();
        String year = first(info, "releasedate", "releaseDate", "release_date", "year");
        if (year.length() >= 4) m.add(year.substring(0, 4));
        String genre = info.optString("genre", "");
        if (!genre.isEmpty()) m.add(genre.split(",")[0].trim());
        if (Vod.MOVIE.equals(kind)) {
            long secs = parseDur(info.optString("duration", ""));
            if (secs <= 0) secs = info.optLong("duration_secs", 0);
            if (secs > 0 && secs < 400) secs *= 60; // nogle sender minutter i sekund-feltet
            movieDur = secs * 1000;
            if (secs > 0) m.add((secs / 3600 > 0 ? secs / 3600 + " t " : "") + (secs % 3600) / 60 + " min");
            JSONObject md = o.optJSONObject("movie_data");
            if (md != null && !md.optString("container_extension").isEmpty()) ext = md.optString("container_extension");
        }
        String rating = info.optString("rating", "");
        if (!rating.isEmpty() && !rating.equals("0")) m.add("★ " + rating);
        if (Vod.SERIES.equals(kind)) parseEpisodes(o);
        if (Vod.SERIES.equals(kind) && !seasons.isEmpty()) m.add(0, seasons.size() + (seasons.size() == 1 ? " sæson" : " sæsoner"));
        meta.setText(TextUtils.join("  ·  ", m));
        plot.setText(first(info, "plot", "description"));
        String cast = first(info, "cast", "actors"), dir = first(info, "director");
        extra.setText((dir.isEmpty() ? "" : "Instruktør: " + dir + (cast.isEmpty() ? "" : "\n")) + (cast.isEmpty() ? "" : "Medvirkende: " + cast));
        renderButtons();
    }

    private static String first(JSONObject o, String... keys) {
        for (String k : keys) { String v = o.optString(k, ""); if (!v.isEmpty() && !v.equals("null")) return v.trim(); }
        return "";
    }

    private static long parseDur(String s) {
        try { String[] p = s.split(":"); if (p.length == 3) return Integer.parseInt(p[0]) * 3600L + Integer.parseInt(p[1]) * 60L + Integer.parseInt(p[2]); }
        catch (Exception ignored) { }
        return 0;
    }

    private void parseEpisodes(JSONObject o) {
        JSONObject e = o.optJSONObject("episodes");
        eps.clear(); seasons.clear();
        if (e == null) {
            JSONArray arr = o.optJSONArray("episodes"); // nogle udbydere sender en liste af lister
            if (arr != null) for (int i = 0; i < arr.length(); i++) addEps(arr.optJSONArray(i), i + 1);
        } else {
            for (Iterator<String> it = e.keys(); it.hasNext(); ) {
                String k = it.next();
                int s; try { s = Integer.parseInt(k); } catch (Exception x) { s = eps.size() + 1; }
                addEps(e.optJSONArray(k), s);
            }
        }
        seasons.addAll(eps.keySet());
        String name0 = title.getText().toString().toLowerCase(Fmt.DA);
        for (List<Ep> l : eps.values()) for (Ep p : l) p.title = cleanEp(p.title, name0, p);
    }

    private void addEps(JSONArray a, int s) {
        if (a == null) return;
        List<Ep> l = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject j = a.optJSONObject(i);
            if (j == null) continue;
            Ep p = new Ep();
            p.id = j.optString("id");
            p.ext = j.optString("container_extension");
            p.season = j.optInt("season", s);
            p.num = j.optInt("episode_num", i + 1);
            p.title = j.optString("title");
            JSONObject inf = j.optJSONObject("info");
            if (inf != null) {
                p.plot = inf.optString("plot", "");
                p.img = inf.optString("movie_image", "");
                long secs = parseDur(inf.optString("duration", ""));
                if (secs <= 0) secs = inf.optLong("duration_secs", 0);
                if (secs > 0 && secs < 400) secs *= 60;
                p.durMs = secs * 1000;
            }
            l.add(p);
        }
        java.util.Collections.sort(l, (x, y) -> x.num - y.num);
        if (!l.isEmpty()) eps.put(s, l);
    }

    /** "Northbound - S02E05 - Whiteout" → "Whiteout". */
    private static String cleanEp(String t, String series, Ep p) {
        String s = Names.channel(t);
        s = s.replaceAll("(?i)^.*?[Ss]\\d{1,2}\\s*[Ee]\\d{1,3}\\s*[-–:]?\\s*", "");
        if (s.toLowerCase(Fmt.DA).startsWith(series) && s.length() > series.length() + 2) s = s.substring(series.length()).replaceAll("^\\s*[-–:]\\s*", "");
        return s.isEmpty() ? "Afsnit " + p.num : s;
    }

    // ---- knapper og afsnit ----
    private void renderButtons() {
        buttons.removeAllViews();
        if (Vod.MOVIE.equals(kind)) {
            final Vod.Resume r = Vod.resume(this, "m:" + id);
            TextView play = Ui.pill(this, px, r != null && r.pos > 60_000 ? "▶  Fortsæt · " + Fmt.mins(r.dur - r.pos) + " tilbage" : "▶  Afspil");
            play.setOnClickListener(v -> playMovie(r != null));
            buttons.addView(play);
            if (r != null && r.pos > 60_000) {
                TextView again = Ui.pill(this, px, "Fra start");
                again.setOnClickListener(v -> playMovie(false));
                add(buttons, again);
            }
            play.requestFocus();
            return;
        }
        if (eps.isEmpty()) return;
        Ep next = nextUp();
        final Ep go = next;
        Vod.Resume r = Vod.resume(this, "e:" + go.id);
        String lbl = (r != null && r.pos > 60_000 ? "▶  Fortsæt " : "▶  Afspil ") + "S" + go.season + " · A" + go.num;
        TextView play = Ui.pill(this, px, lbl);
        play.setOnClickListener(v -> playEp(go, true));
        buttons.addView(play);
        if (season < 0) season = go.season;
        renderSeasons();
        play.requestFocus();
    }

    /** Næste afsnit: det seneste påbegyndte, ellers første usete. */
    private Ep nextUp() {
        Vod.Resume best = null;
        for (Vod.Resume r : Vod.continueList(this, Vod.SERIES)) if (id.equals(r.seriesId)) { best = r; break; }
        Ep firstUnseen = null, after = null;
        boolean passed = false;
        for (List<Ep> l : eps.values()) for (Ep p : l) {
            if (best != null && p.id.equals(best.id)) return p;
            if (firstUnseen == null && !Vod.watched(this, "e:" + p.id)) firstUnseen = p;
        }
        if (firstUnseen != null) return firstUnseen;
        return eps.values().iterator().next().get(0);
    }

    private void renderSeasons() {
        seasonRow.removeAllViews();
        for (final int s : seasons) {
            final TextView t = px.t(21, 500, 0xFFFFFFFF);
            t.setText("Sæson " + s);
            t.setPadding(px.p(24), px.p(12), px.p(24), px.p(12));
            t.setFocusable(true);
            style(t, s == season, false);
            t.setOnFocusChangeListener((v, has) -> {
                if (has && season != s) { season = s; renderEpisodes(); for (int i = 0; i < seasonRow.getChildCount(); i++) style((TextView) seasonRow.getChildAt(i), seasons.get(i) == season, seasonRow.getChildAt(i).hasFocus()); }
                style(t, s == season, has);
            });
            add(seasonRow, t);
        }
        renderEpisodes();
    }

    private void style(TextView t, boolean on, boolean f) {
        t.setBackground(Ui.round(f ? 0xFFFFFFFF : on ? Theme.white(0.28f) : Theme.white(0.1f), 999));
        t.setTextColor(f ? 0xFF000000 : on ? 0xFFFFFFFF : Theme.white(0.8f));
    }

    private void renderEpisodes() {
        epRow.removeAllViews();
        List<Ep> l = eps.get(season);
        if (l == null) return;
        int w = px.p(420), hh = px.p(236);
        for (final Ep p : l) {
            Vod.Resume r = Vod.resume(this, "e:" + p.id);
            boolean seen = Vod.watched(this, "e:" + p.id);
            String badge = seen ? "SET" : r != null && r.pos > 60_000 ? Fmt.mins(r.dur - r.pos).toUpperCase(Fmt.DA) + " TILBAGE" : "";
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setClipChildren(false);
            FrameLayout box = new FrameLayout(this);
            box.setBackground(Ui.round(0xFF1F1F24, px.p(15)));
            box.setClipToOutline(true);
            box.setFocusable(true);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Img.load(iv, p.img != null && !p.img.isEmpty() ? p.img : poster, w);
            box.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            if (!badge.isEmpty()) {
                TextView bd = Ui.badge(this, px, badge, false);
                bd.setBackground(Ui.round(0xB3000000, px.p(7.5f)));
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
                bl.leftMargin = px.p(15); bl.topMargin = px.p(15);
                box.addView(bd, bl);
            }
            if (r != null && r.pos > 60_000) {
                Ui.Bar bar = new Ui.Bar(this, Theme.white(0.3f), 0xFFFFFFFF);
                bar.set(r.progress());
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, px.p(6), Gravity.BOTTOM);
                bl.leftMargin = px.p(18); bl.rightMargin = px.p(18); bl.bottomMargin = px.p(16);
                box.addView(bar, bl);
            }
            card.addView(box, new LinearLayout.LayoutParams(w, hh));
            TextView k = px.t(16.5f, 600, Theme.white(0.55f));
            k.setLetterSpacing(0.08f);
            k.setText("AFSNIT " + p.num + (p.durMs > 0 ? " · " + Fmt.mins(p.durMs) : ""));
            card.addView(k, lp(w, 16));
            TextView t = px.t(22.5f, 600, 0xFFFFFFFF);
            t.setText(p.title);
            card.addView(t, lp(w, 6));
            TextView x = px.t(18, 400, Theme.white(0.55f));
            x.setSingleLine(false);
            x.setMaxLines(2);
            x.setText(p.plot == null ? "" : p.plot);
            card.addView(x, lp(w, 6));
            box.setOnFocusChangeListener((v, has) -> {
                v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).translationZ(has ? px.p(30) : 0).setDuration(180).start();
                for (int i = 1; i < card.getChildCount(); i++) card.getChildAt(i).animate().translationY(has ? px.p(10) : 0).setDuration(180).start();
            });
            box.setOnClickListener(v -> playEp(p, true));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            if (epRow.getChildCount() > 0) cl.leftMargin = px.p(36);
            epRow.addView(card, cl);
        }
    }

    private void add(LinearLayout row, View v) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        if (row.getChildCount() > 0) l.leftMargin = px.p(18);
        row.addView(v, l);
    }

    // ---- afspil ----
    private void playMovie(boolean resume) {
        startActivity(new Intent(this, VodPlayerActivity.class).putExtra("kind", Vod.MOVIE)
                .putExtra("ids", new String[]{id}).putExtra("titles", new String[]{title.getText().toString()})
                .putExtra("subs", new String[]{meta.getText().toString()}).putExtra("exts", new String[]{ext})
                .putExtra("poster", poster).putExtra("resume", resume));
    }

    private void playEp(Ep start, boolean resume) {
        List<Ep> all = new ArrayList<>();
        for (List<Ep> l : eps.values()) all.addAll(l);
        int n = all.size(), at = all.indexOf(start);
        String[] ids = new String[n], titles = new String[n], subs = new String[n], exts = new String[n];
        for (int i = 0; i < n; i++) {
            Ep p = all.get(i);
            ids[i] = p.id; exts[i] = p.ext;
            titles[i] = title.getText().toString();
            subs[i] = "S" + p.season + " · A" + p.num + "  " + p.title;
        }
        startActivity(new Intent(this, VodPlayerActivity.class).putExtra("kind", Vod.SERIES).putExtra("seriesId", id)
                .putExtra("ids", ids).putExtra("titles", titles).putExtra("subs", subs).putExtra("exts", exts)
                .putExtra("i", at).putExtra("poster", poster).putExtra("resume", resume));
    }
}
