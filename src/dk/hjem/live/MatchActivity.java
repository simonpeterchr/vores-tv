package dk.hjem.live;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Kampsiden: alle streams der viser kampen (bedst først), stillingen og resten af runden. */
public class MatchActivity extends Activity {
    private Px px;
    private String key;
    private LinearLayout head, streams, side;
    private String shown = "";
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable rebuild = this::rebuild;
    private final Runnable onData = () -> { h.removeCallbacks(rebuild); h.postDelayed(rebuild, 300); };
    private final Runnable tick = new Runnable() { @Override public void run() { Sports.refresh(); h.postDelayed(this, 30_000); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        px = new Px(this);
        key = getIntent().getStringExtra("key");
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        root.addView(head, Ui.at(px.p(96), px.p(70), px.p(1000), -2));
        ScrollView sv = new ScrollView(this);
        sv.setFocusable(false);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, px.p(8), 0, px.p(60));
        streams = new LinearLayout(this);
        streams.setOrientation(LinearLayout.VERTICAL);
        streams.setClipChildren(false);
        sv.addView(streams);
        root.addView(sv, Ui.at(px.p(96), px.p(430), px.p(1000), px.p(650)));
        ScrollView ss = new ScrollView(this);
        ss.setFocusable(false);
        ss.setVerticalScrollBarEnabled(false);
        side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        ss.addView(side);
        root.addView(ss, Ui.at(px.p(1180), px.p(70), px.p(644), px.p(1010)));
    }

    @Override protected void onResume() { super.onResume(); Catalog.listeners.add(onData); shown = ""; rebuild(); h.post(tick); }
    @Override protected void onPause() { super.onPause(); Catalog.listeners.remove(onData); h.removeCallbacks(tick); }

    private void rebuild() {
        final Sports.Game g = Sports.games.get(key);
        if (g == null) return;
        boolean spoil = Sports.spoilerFree(this), score = !spoil && !"pre".equals(g.state);
        final List<Match.Stream> list = found != null && key.equals(foundFor) ? found : new java.util.ArrayList<>();
        findLater(g);
        StringBuilder st = new StringBuilder().append(g.state).append(g.home.score).append(g.away.score).append(g.clock).append(spoil);
        for (Match.Stream s : list) st.append(s.ch.id).append(s.confidence);
        st.append(Sports.FOOTBALL.equals(g.sport) ? Sports.table(g.league).size() : 0);
        if (st.toString().equals(shown)) return;
        shown = st.toString();
        View keep = getCurrentFocus();
        Object keepTag = keep != null ? keep.getTag() : null;

        // hoved: liga · status, to hold med score
        head.removeAllViews();
        LinearLayout k = new LinearLayout(this);
        k.setGravity(Gravity.CENTER_VERTICAL);
        TextView lg = px.t(19, 600, Theme.white(0.6f));
        lg.setLetterSpacing(0.1f);
        lg.setText((Sports.FOOTBALL.equals(g.sport) ? g.leagueName : Sports.sportName(g.sport) + " · " + g.leagueName).toUpperCase(Fmt.DA) + "   ·   " + (g.allDay && !g.live() && !g.done() ? Sports.status(g) + " · hele dagen" : Sports.status(g)));
        k.addView(lg);
        if (g.live()) { LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2); bl.leftMargin = px.p(16); k.addView(Ui.badge(this, px, "LIVE", true), bl); }
        head.addView(k);
        if (g.title != null) {
            TextView tt = px.t(56, 700, 0xFFFFFFFF);
            tt.setSingleLine(false);
            tt.setMaxLines(2);
            tt.setText(g.title);
            head.addView(tt, lp(24));
            TextView sb = px.t(30, 500, Theme.white(0.75f));
            sb.setText(g.sub != null ? g.sub : "");
            head.addView(sb, lp(10));
        } else {
            head.addView(team(g.home, score), lp(28));
            head.addView(team(g.away, score), lp(14));
        }

        // streams
        streams.removeAllViews();
        TextView sh = px.t(27, 600, 0xFFFFFFFF);
        sh.setText(list.isEmpty() ? (foundFor == null || !key.equals(foundFor) ? "Leder efter streams …" : "Ingen streams fundet endnu") : list.size() + (list.size() == 1 ? " stream viser kampen" : " streams viser kampen"));
        streams.addView(sh);
        if (list.isEmpty()) {
            TextView e = px.t(21, 400, Theme.white(0.55f));
            e.setSingleLine(false);
            e.setText("Vores TV kigger i alle jeres kanaler og i sportskanalernes programguide. Kampe der først spilles senere, dukker ofte op i programguiden tættere på kickoff.");
            streams.addView(e, lp(12));
        }
        View first = null, restore = null;
        for (int i = 0; i < list.size(); i++) {
            final int idx = i;
            View r = streamRow(list.get(i), i == 0);
            r.setTag("s" + list.get(i).ch.id);
            r.setOnClickListener(v -> play(list, idx));
            streams.addView(r, lp(i == 0 ? 18 : 10));
            if (first == null) first = r;
            if (r.getTag().equals(keepTag)) restore = r;
        }

        // side: stilling + resten af runden
        side.removeAllViews();
        List<Sports.Row> table = Sports.FOOTBALL.equals(g.sport) ? Sports.table(g.league) : new java.util.ArrayList<>();
        if (!table.isEmpty()) {
            TextView th = px.t(24, 600, 0xFFFFFFFF);
            th.setText("Stillingen");
            side.addView(th);
            int at = 0;
            for (int i = 0; i < table.size(); i++) if (isTeam(table.get(i).team, g)) { at = i; break; }
            int from = Math.max(0, Math.min(at - 4, table.size() - 10));
            for (int i = from; i < Math.min(table.size(), from + 10); i++) side.addView(tableRow(table.get(i), isTeam(table.get(i).team, g), spoil), lp(6));
        }
        List<Sports.Game> round = Sports.round(g);
        if (!round.isEmpty()) {
            TextView rh = px.t(24, 600, 0xFFFFFFFF);
            rh.setText(Sports.F1.equals(g.sport) ? "Resten af weekenden" : "Resten af runden");
            side.addView(rh, lp(30));
            for (final Sports.Game o : round) {
                TextView t = px.t(20, 500, 0xFFFFFFFF);
                boolean sc = !spoil && !"pre".equals(o.state);
                t.setText(o.title != null ? (o.live() ? "● " : "") + o.sub + "     " + Sports.status(o)
                        : (o.live() ? "● " : "") + o.home.shortName + (sc ? "  " + o.home.score + "–" + o.away.score + "  " : "  –  ") + o.away.shortName + "     " + Sports.status(o));
                t.setPadding(px.p(16), px.p(12), px.p(16), px.p(12));
                t.setFocusable(true);
                t.setTag("r" + o.id);
                t.setBackground(Ui.round(Theme.white(0.05f), px.p(14)));
                t.setOnFocusChangeListener((v, has) -> { v.setBackground(Ui.round(has ? 0xFFFFFFFF : Theme.white(0.05f), px.p(14))); ((TextView) v).setTextColor(has ? 0xFF000000 : 0xFFFFFFFF); });
                t.setOnClickListener(v -> { key = o.id + "|" + o.league; shown = ""; rebuild(); });
                side.addView(t, lp(8));
                if (t.getTag().equals(keepTag)) restore = t;
            }
        }
        if (restore != null) restore.requestFocus();
        else if (first != null && (keep == null || !keep.isShown())) first.requestFocus();
    }

    // Streams findes i baggrunden (titusinder af kanaler på et langsomt tv må ikke fryse skærmen)
    private volatile List<Match.Stream> found;
    private volatile String foundFor;
    private volatile boolean finding;
    private long foundAt;
    private static final java.util.concurrent.ExecutorService finder = java.util.concurrent.Executors.newSingleThreadExecutor();

    private void findLater(final Sports.Game g) {
        if (finding || (key.equals(foundFor) && System.currentTimeMillis() - foundAt < 20_000)) return;
        finding = true;
        final String k = key;
        finder.execute(() -> {
            List<Match.Stream> l = Match.streams(getApplicationContext(), g);
            h.post(() -> { finding = false; foundAt = System.currentTimeMillis(); if (k.equals(key)) { found = l; foundFor = k; shown = ""; rebuild(); } });
        });
    }

    private static boolean isTeam(String n, Sports.Game g) {
        return n != null && (n.equals(g.home.shortName) || n.equals(g.away.shortName) || n.equals(g.home.name) || n.equals(g.away.name));
    }

    private LinearLayout team(Sports.Team t, boolean score) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        ImageView lg = new ImageView(this);
        lg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Img.load(lg, t.logo, px.p(96));
        r.addView(lg, new LinearLayout.LayoutParams(px.p(84), px.p(84)));
        TextView n = px.t(50, 700, 0xFFFFFFFF);
        n.setText(t.name);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1);
        nl.leftMargin = px.p(26);
        r.addView(n, nl);
        TextView s = px.t(64, 700, 0xFFFFFFFF);
        s.setText(score && t.score >= 0 ? String.valueOf(t.score) : "");
        r.addView(s);
        return r;
    }

    /** Én stream: kanal, land, hvor sikker vi er, og kvalitet. Den bedste er markeret. */
    private View streamRow(Match.Stream s, boolean best) {
        final LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px.p(20), px.p(16), px.p(24), px.p(16));
        r.setFocusable(true);
        ImageView lg = new ImageView(this);
        lg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        lg.setBackground(Ui.round(0xFF26262B, px.p(10)));
        lg.setPadding(px.p(6), px.p(6), px.p(6), px.p(6));
        Img.load(lg, s.ch.logo, px.p(80));
        r.addView(lg, new LinearLayout.LayoutParams(px.p(80), px.p(54)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        final TextView n = px.t(24, 600, 0xFFFFFFFF);
        n.setText(s.ch.name);
        col.addView(n);
        final TextView w = px.t(18, 400, Theme.white(0.55f));
        w.setText((best ? "Bedste valg · " : "") + s.why);
        col.addView(w, lp(4));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1);
        cl.leftMargin = px.p(20);
        r.addView(col, cl);
        if (!s.country.isEmpty()) r.addView(pill(s.country, Theme.white(0.14f), 0xFFFFFFFF), plp());
        int cc = s.confidence == Match.CERTAIN ? 0xFF1F7A4D : s.confidence == Match.VERY_LIKELY ? 0xFF6B5A1C : 0xFF4A4A52;
        r.addView(pill(s.confidenceText(), cc, 0xFFFFFFFF), plp());
        if (!s.quality.isEmpty()) r.addView(pill(s.quality, 0, 0xFFFFFFFF), plp());
        r.setBackground(Ui.round(best ? 0xFF22222A : 0xFF18181C, px.p(18)));
        r.setOnFocusChangeListener((v, has) -> {
            v.setBackground(Ui.round(has ? 0xFFFFFFFF : best ? 0xFF22222A : 0xFF18181C, px.p(18)));
            n.setTextColor(has ? 0xFF000000 : 0xFFFFFFFF);
            w.setTextColor(has ? 0x99000000 : Theme.white(0.55f));
            v.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f).setDuration(150).start();
        });
        return r;
    }

    private TextView pill(String text, int bg, int fg) {
        TextView t = px.t(16.5f, 650, fg);
        t.setText(text);
        t.setPadding(px.p(12), px.p(6), px.p(12), px.p(6));
        android.graphics.drawable.GradientDrawable d = Ui.round(bg, px.p(8));
        if (bg == 0) d.setStroke(px.p(1.5f), Theme.white(0.4f));
        t.setBackground(d);
        return t;
    }

    private LinearLayout.LayoutParams plp() { LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2); l.leftMargin = px.p(10); return l; }

    private View tableRow(Sports.Row r, boolean hl, boolean spoil) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px.p(14), px.p(8), px.p(14), px.p(8));
        if (hl) row.setBackground(Ui.round(Theme.white(0.1f), px.p(12)));
        TextView rk = px.t(19, 500, Theme.white(0.55f));
        rk.setText(String.valueOf(r.rank));
        row.addView(rk, new LinearLayout.LayoutParams(px.p(40), -2));
        ImageView lg = new ImageView(this);
        lg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Img.load(lg, r.logo, px.p(32));
        row.addView(lg, new LinearLayout.LayoutParams(px.p(30), px.p(30)));
        TextView n = px.t(20, hl ? 700 : 500, 0xFFFFFFFF);
        n.setText(r.team);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1);
        nl.leftMargin = px.p(14);
        row.addView(n, nl);
        TextView p = px.t(19, 500, Theme.white(0.7f));
        p.setText(spoil ? "" : r.played + " k   " + r.diff + "   " + r.points + " p");
        row.addView(p);
        return row;
    }

    /** Afspil fra den valgte stream; resten af listen er reserver, hvis signalet forsvinder. */
    private void play(List<Match.Stream> list, int idx) {
        int[] ids = new int[list.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = list.get(i).ch.id;
        Sports.Game g = Sports.games.get(key);
        startActivity(new Intent(this, PlayerActivity.class).putExtra("ids", ids).putExtra("i", idx)
                .putExtra("failover", true).putExtra("title", g == null ? null : g.title != null ? g.title + (g.sub != null ? " · " + g.sub : "") : g.home.shortName + " – " + g.away.shortName));
    }

    private LinearLayout.LayoutParams lp(float top) { LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2); l.topMargin = px.p(top); return l; }
}
