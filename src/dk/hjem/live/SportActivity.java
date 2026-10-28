package dk.hjem.live;

import android.app.Activity;
import android.content.Intent;
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
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sport (inspireret af Couchside): kampene først – live øverst, jeres hold og ligaer først, streams fundet for jer. */
public class SportActivity extends Activity {
    static final String MINE = "mine", LIVE = "live", ALL = "all";
    private Px px;
    private FrameLayout root;
    private Ui.TopBar top;
    private LinearLayout pills, rows;
    private TextView status;
    private String filter = MINE, shown = "", focusKey;
    private boolean firstFocus;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable rebuild = this::rebuild;
    private final Runnable onData = () -> { h.removeCallbacks(rebuild); h.postDelayed(rebuild, 300); };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            top.clock.setText(Fmt.clock());
            Sports.refresh();
            final android.content.Context app = getApplicationContext();
            new Thread(() -> Sports.guide(app), "sport-guide").start();
            h.postDelayed(this, 30_000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        px = new Px(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        top = Ui.topBar(this, px, Nav.TABS, Nav.SPORT);
        root.addView(top.root, Ui.at(px.p(96), px.p(42), px.p(1728), -2));
        Nav.wire(this, top, Nav.SPORT, null);

        HorizontalScrollView ps = new HorizontalScrollView(this);
        ps.setHorizontalScrollBarEnabled(false);
        ps.setFocusable(false);
        pills = new LinearLayout(this);
        ps.addView(pills);
        root.addView(ps, Ui.at(px.p(96), px.p(150), px.p(1728), -2));
        status = px.t(20, 400, Theme.white(0.5f));
        root.addView(status, Ui.at(px.p(96), px.p(228), px.p(1728), -2));

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
        Catalog.load(this, false);
    }

    @Override protected void onResume() {
        super.onResume();
        Catalog.listeners.add(onData);
        shown = "";
        Sports.refresh();
        buildPills();
        rebuild();
        h.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        Catalog.listeners.remove(onData);
        h.removeCallbacks(tick);
    }

    private void buildPills() {
        pills.removeAllViews();
        List<String[]> ps = new ArrayList<>();
        ps.add(new String[]{MINE, "★ Mine kampe"});
        ps.add(new String[]{LIVE, "● Live nu"});
        for (String[] s : Sports.SPORTS) ps.add(new String[]{"sport:" + s[0], s[1]});
        ps.add(new String[]{ALL, "Alt"});
        for (final String[] p : ps) {
            final TextView t = px.t(22.5f, 500, 0xFFFFFFFF);
            t.setText(p[1]);
            t.setPadding(px.p(27), px.p(13), px.p(27), px.p(13));
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> { if (has && !filter.equals(p[0])) { filter = p[0]; shown = ""; rebuild(); } stylePills(); });
            add(pills, t, 12);
        }
        final TextView sp = px.t(22.5f, 500, 0xFFFFFFFF);
        sp.setPadding(px.p(27), px.p(13), px.p(27), px.p(13));
        sp.setFocusable(true);
        sp.setTag("spoiler");
        sp.setOnClickListener(v -> { Sports.setSpoilerFree(this, !Sports.spoilerFree(this)); shown = ""; rebuild(); stylePills(); });
        sp.setOnFocusChangeListener((v, has) -> stylePills());
        add(pills, sp, 36);
        stylePills();
    }

    private void stylePills() {
        for (int i = 0; i < pills.getChildCount(); i++) {
            TextView t = (TextView) pills.getChildAt(i);
            boolean f = t.hasFocus();
            if ("spoiler".equals(t.getTag())) {
                t.setText(Sports.spoilerFree(this) ? "Spoilerfri: til" : "Spoilerfri: fra");
                t.setBackground(Ui.round(f ? 0xFFFFFFFF : Theme.white(0.06f), 999));
                t.setTextColor(f ? 0xFF000000 : Theme.white(0.7f));
                continue;
            }
            boolean on = i == index(filter);
            t.setBackground(Ui.round(f ? 0xFFFFFFFF : on ? Theme.white(0.28f) : Theme.white(0.1f), 999));
            t.setTextColor(f ? 0xFF000000 : on ? 0xFFFFFFFF : Theme.white(0.8f));
        }
    }

    private int index(String key) {
        List<String> keys = new ArrayList<>();
        keys.add(MINE); keys.add(LIVE);
        for (String[] s : Sports.SPORTS) keys.add("sport:" + s[0]);
        keys.add(ALL);
        return keys.indexOf(key);
    }

    private void rebuild() {
        List<Sports.Game> all = Sports.list(this, false), list = new ArrayList<>();
        for (Sports.Game g : all) {
            if (MINE.equals(filter) && !Sports.mine(this, g)) continue;
            if (LIVE.equals(filter) && !g.live()) continue;
            if (filter.startsWith("sport:") && !filter.substring(6).equals(g.sport)) continue;
            list.add(g);
        }
        StringBuilder st = new StringBuilder(filter).append(Sports.spoilerFree(this)).append(Catalog.loaded);
        for (Sports.Game g : list) st.append(g.id).append(g.state).append(g.home.score).append(g.away.score).append(g.clock).append(counts.get(g.id + g.league));
        if (st.toString().equals(shown)) return;
        shown = st.toString();
        View keep = getCurrentFocus();
        boolean inRows = keep != null && keep.getTag(0x7f0a0003) != null;
        rows.removeAllViews();
        status.setText(Sports.games.isEmpty() ? "Henter kampprogram …" : list.isEmpty() ? "Intet på programmet her de næste dage." : list.size() + (list.size() == 1 ? " begivenhed" : " begivenheder"));
        // grupper: Live nu, I dag, I morgen, ugedage
        Map<String, List<Sports.Game>> secs = new LinkedHashMap<>();
        for (Sports.Game g : list) {
            String k = g.live() ? "Live nu" : g.done() ? "Spillet" : dayLabel(g.start);
            if (!secs.containsKey(k)) secs.put(k, new ArrayList<>());
            secs.get(k).add(g);
        }
        View restore = null;
        for (Map.Entry<String, List<Sports.Game>> e : secs.entrySet()) {
            if (e.getKey().equals("Spillet") && !(ALL.equals(filter) || MINE.equals(filter))) continue;
            LinearLayout r = section(e.getKey(), e.getKey().equals("Live nu"));
            for (Sports.Game g : e.getValue()) {
                View card = card(g);
                add(r, card, 30);
                if ((g.id + g.league).equals(focusKey)) restore = card;
            }
        }
        if (restore != null && inRows) restore.requestFocus();
        else if (!firstFocus && pills.getChildCount() > 0) { pills.getChildAt(Math.max(0, index(filter))).requestFocus(); firstFocus = true; }
    }

    /** "I dag", "I morgen", "Fredag 30. oktober". */
    private static String dayLabel(long t) {
        String w = Search.when(t);
        if (w.startsWith("I dag") || w.startsWith("I morgen") || w.startsWith("I går")) return w.substring(0, w.lastIndexOf(' '));
        String s = new java.text.SimpleDateFormat("EEEE d. MMMM", Fmt.DA).format(new java.util.Date(t));
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private LinearLayout section(String name, boolean live) {
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(px.p(96), px.p(16), 0, 0);
        TextView t = px.t(30, 600, 0xFFFFFFFF);
        t.setText(name);
        head.addView(t);
        if (live) { View d = new View(this); d.setBackground(Ui.round(Ui.RED, px.p(6))); LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(px.p(12), px.p(12)); l.leftMargin = px.p(14); head.addView(d, l); }
        rows.addView(head);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setFocusable(false);
        hs.setClipToPadding(false);
        hs.setPadding(px.p(96), px.p(24), px.p(96), px.p(18));
        LinearLayout r = new LinearLayout(this);
        r.setClipChildren(false);
        hs.addView(r);
        rows.addView(hs);
        return r;
    }

    /** Kampkort: liga og status, to hold med logo og score, og hvor mange streams der er fundet. */
    private View card(final Sports.Game g) {
        boolean spoil = Sports.spoilerFree(this);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(px.p(26), px.p(22), px.p(26), px.p(22));
        c.setFocusable(true);
        c.setTag(0x7f0a0003, "g");
        boolean fav = Sports.isFavTeam(this, g.home) || Sports.isFavTeam(this, g.away) || (g.title != null && Sports.favSports(this).contains(g.sport));
        c.setBackground(Ui.round(fav ? 0xFF24242B : 0xFF1C1C21, px.p(20)));
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        TextView lg = px.t(17, 600, Theme.white(0.55f));
        lg.setLetterSpacing(0.08f);
        lg.setText((Sports.FOOTBALL.equals(g.sport) || g.leagueName.equals(Sports.sportName(g.sport)) ? g.leagueName : Sports.sportName(g.sport) + " · " + g.leagueName).toUpperCase(Fmt.DA));
        hd.addView(lg, new LinearLayout.LayoutParams(0, -2, 1));
        if (g.live()) hd.addView(Ui.badge(this, px, "LIVE", true));
        TextView stt = px.t(19, 600, g.live() ? 0xFFFF6B6B : Theme.white(0.8f));
        stt.setText(g.live() || g.done() ? Sports.status(g) : g.allDay ? "Hele dagen" : Fmt.hm(g.start));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.leftMargin = px.p(12);
        hd.addView(stt, sl);
        c.addView(hd);
        boolean showScore = !spoil && !"pre".equals(g.state);
        if (g.title != null) {
            TextView tt = px.t(26, 700, 0xFFFFFFFF);
            tt.setSingleLine(false);
            tt.setMaxLines(2);
            tt.setText(g.title);
            c.addView(tt, lp(18));
            TextView sb = px.t(21, 500, Theme.white(0.7f));
            sb.setText(g.sub != null ? g.sub : "");
            c.addView(sb, lp(8));
        } else {
            c.addView(teamRow(g.home, showScore), lp(18));
            c.addView(teamRow(g.away, showScore), lp(10));
        }
        Integer cached = counts.get(g.id + g.league);
        int n = cached == null ? 0 : cached;
        if (cached == null) countLater(g);
        TextView ft = px.t(18, 500, n > 0 ? 0xFF7EE2A8 : Theme.white(0.4f));
        boolean soon = g.start - System.currentTimeMillis() < 24 * 3600_000L;
        ft.setText(n > 0 ? n + (n == 1 ? " stream" : " streams") + " fundet" : g.done() ? " " : soon ? "Leder efter streams …" : "Streams findes tættere på kickoff");
        c.addView(ft, lp(16));
        c.setOnFocusChangeListener((v, has) -> {
            v.setBackground(Ui.round(has ? 0xFF3A3A42 : fav ? 0xFF24242B : 0xFF1C1C21, px.p(20)));
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).translationZ(has ? px.p(24) : 0).setDuration(160).start();
            if (has) focusKey = g.id + g.league;
        });
        c.setOnClickListener(v -> startActivity(new Intent(this, MatchActivity.class).putExtra("key", g.id + "|" + g.league)));
        c.setLayoutParams(new LinearLayout.LayoutParams(px.p(500), -2));
        return c;
    }

    // Antal streams regnes ud i baggrunden (kan være titusinder af kanaler), så fjernbetjeningen ikke hakker
    private static final java.util.Map<String, Integer> counts = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ExecutorService counter = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.Set<String> pending = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private void countLater(final Sports.Game g) {
        final String k = g.id + g.league;
        if (!pending.add(k)) return;
        counter.execute(() -> {
            int n = Match.streams(getApplicationContext(), g).size();
            pending.remove(k);
            Integer old = counts.put(k, n);
            if (old == null || old != n) h.post(onData);
        });
    }

    private LinearLayout teamRow(Sports.Team t, boolean score) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        ImageView lg = new ImageView(this);
        lg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Img.load(lg, t.logo, px.p(44));
        r.addView(lg, new LinearLayout.LayoutParams(px.p(44), px.p(44)));
        TextView n = px.t(26, Sports.isFavTeam(this, t) ? 700 : 500, 0xFFFFFFFF);
        n.setText(t.shortName != null ? t.shortName : t.name != null ? t.name : "");
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1);
        nl.leftMargin = px.p(16);
        r.addView(n, nl);
        TextView s = px.t(30, 700, 0xFFFFFFFF);
        s.setText(score && t.score >= 0 ? String.valueOf(t.score) : "");
        r.addView(s);
        return r;
    }

    private LinearLayout.LayoutParams lp(float top) { LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2); l.topMargin = px.p(top); return l; }

    private void add(LinearLayout row, View v, float gap) {
        LinearLayout.LayoutParams l = v.getLayoutParams() instanceof LinearLayout.LayoutParams ? (LinearLayout.LayoutParams) v.getLayoutParams() : new LinearLayout.LayoutParams(-2, -2);
        if (row.getChildCount() > 0) l.leftMargin = px.p(gap);
        row.addView(v, l);
    }
}
