package dk.hjem.live;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Søg på tværs af live-TV (kanaler og programmer), film og serier – eller kun én medietype. */
public class SearchActivity extends Activity {
    static final String[] FILTERS = {"Alt", "Live-TV", "Film", "Serier"};
    private Px px;
    private FrameLayout root;
    private Ui.TopBar top;
    private EditText field;
    private LinearLayout filters, results;
    private TextView status;
    private int filter;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable run = this::run;
    private final Runnable onData = () -> { h.removeCallbacks(run); h.postDelayed(run, 600); };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        px = new Px(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        top = Ui.topBar(this, px, Nav.TABS, Nav.SEARCH);
        root.addView(top.root, Ui.at(px.p(96), px.p(42), px.p(1728), -2));
        Nav.wire(this, top, Nav.SEARCH, null);
        top.clock.setText(Fmt.clock());

        field = new EditText(this);
        field.setSingleLine(true);
        field.setHint("Søg efter kanaler, programmer, film eller serier");
        field.setHintTextColor(Theme.white(0.4f));
        field.setTextColor(0xFFFFFFFF);
        field.setTypeface(Theme.font(this, 500));
        field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px.p(33));
        field.setPadding(px.p(36), px.p(24), px.p(36), px.p(24));
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setBackground(Ui.round(Theme.white(0.1f), 999));
        field.setOnFocusChangeListener((v, has) -> field.setBackground(Ui.round(has ? Theme.white(0.2f) : Theme.white(0.1f), 999)));
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            public void afterTextChanged(Editable s) { h.removeCallbacks(run); h.postDelayed(run, 350); }
        });
        field.setOnEditorActionListener((v, id, e) -> {
            hideKeyboard();
            View first = firstResult();
            if (first != null) first.requestFocus();
            return true;
        });
        root.addView(field, Ui.at(px.p(96), px.p(150), px.p(1100), -2));

        filters = new LinearLayout(this);
        for (int i = 0; i < FILTERS.length; i++) {
            final int fi = i;
            final TextView t = px.t(22.5f, 500, 0xFFFFFFFF);
            t.setText(FILTERS[i]);
            t.setPadding(px.p(27), px.p(13), px.p(27), px.p(13));
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> { if (has && filter != fi) { filter = fi; run(); } styleFilters(); });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) l.leftMargin = px.p(12);
            filters.addView(t, l);
        }
        styleFilters();
        root.addView(filters, Ui.at(px.p(1230), px.p(160), px.p(700), -2));

        status = px.t(21, 400, Theme.white(0.5f));
        root.addView(status, Ui.at(px.p(96), px.p(262), px.p(1700), -2));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFocusable(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, px.p(10), 0, px.p(80));
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setClipChildren(false);
        sv.addView(results);
        root.addView(sv, Ui.at(0, px.p(300), -1, px.p(780)));

        Search.index(this);
        Search.warmEpg(this);
        field.requestFocus();
        String q = getIntent().getStringExtra("q");
        if (q != null) field.setText(q);
    }

    @Override protected void onResume() { super.onResume(); Catalog.listeners.add(onData); }
    @Override protected void onPause() { super.onPause(); Catalog.listeners.remove(onData); }

    private void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(field.getWindowToken(), 0);
    }

    private void styleFilters() {
        for (int i = 0; i < filters.getChildCount(); i++) {
            TextView t = (TextView) filters.getChildAt(i);
            boolean on = i == filter, f = t.hasFocus();
            t.setBackground(Ui.round(f ? 0xFFFFFFFF : on ? Theme.white(0.28f) : Theme.white(0.1f), 999));
            t.setTextColor(f ? 0xFF000000 : on ? 0xFFFFFFFF : Theme.white(0.8f));
        }
    }

    private View firstResult() {
        for (int i = 0; i < results.getChildCount(); i++) {
            View v = results.getChildAt(i);
            if (v instanceof HorizontalScrollView) {
                LinearLayout r = (LinearLayout) ((HorizontalScrollView) v).getChildAt(0);
                if (r.getChildCount() > 0) return Poster.box((LinearLayout) r.getChildAt(0));
            }
        }
        return null;
    }

    private void run() {
        String q = field.getText().toString();
        List<String[]> qq = Search.query(q);
        View keep = getCurrentFocus();
        boolean inResults = keep != null && keep.getTag(0x7f0a0002) != null;
        String keepTag = inResults ? (String) keep.getTag(0x7f0a0002) : null;
        results.removeAllViews();
        if (qq.isEmpty()) { status.setText(Search.indexing() ? "Indlæser film og serier til søgning …" : ""); return; }
        int n = 0;
        View restore = null;
        boolean live = filter == 0 || filter == 1, mov = filter == 0 || filter == 2, ser = filter == 0 || filter == 3;
        int max = filter == 0 ? 24 : 120;
        if (live) {
            List<Search.Hit> progs = Search.programs(this, qq, max);
            List<Search.Hit> nowP = new java.util.ArrayList<>(), later = new java.util.ArrayList<>();
            long t = System.currentTimeMillis();
            for (Search.Hit x : progs) (x.start <= t ? nowP : later).add(x);
            n += nowP.size() + later.size();
            restore = pick(restore, section("Kører nu", nowP, true, keepTag), keepTag);
            restore = pick(restore, section("Senere i dag", later, true, keepTag), keepTag);
            List<Search.Hit> ch = Search.channels(this, qq, max);
            n += ch.size();
            restore = pick(restore, section("Kanaler", ch, true, keepTag), keepTag);
        }
        if (mov) { List<Search.Hit> m = Search.vod(this, Vod.MOVIE, qq, max); n += m.size(); restore = pick(restore, section("Film", m, false, keepTag), keepTag); }
        if (ser) { List<Search.Hit> s = Search.vod(this, Vod.SERIES, qq, max); n += s.size(); restore = pick(restore, section("Serier", s, false, keepTag), keepTag); }
        String extra = Search.indexing() && (mov || ser) ? "  ·  indlæser stadig film og serier …" : Search.epgPending.get() > 0 && live ? "  ·  henter programguide …" : "";
        status.setText((n == 0 ? "Ingen resultater" : n + " resultater") + extra);
        if (restore != null) restore.requestFocus();
    }

    private View pick(View cur, View found, String tag) { return cur != null ? cur : found; }

    /** Én række resultater; returnerer det kort der havde fokus før (så fokus ikke hopper ved opdatering). */
    private View section(String name, List<Search.Hit> hits, boolean wide, String keepTag) {
        if (hits.isEmpty()) return null;
        TextView t = px.t(27, 600, 0xFFFFFFFF);
        t.setText(name + "  ");
        t.setPadding(px.p(96), px.p(16), 0, 0);
        results.addView(t);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setFocusable(false);
        hs.setClipToPadding(false);
        hs.setPadding(px.p(96), px.p(24), px.p(96), px.p(18));
        LinearLayout r = new LinearLayout(this);
        r.setClipChildren(false);
        hs.addView(r);
        results.addView(hs);
        View restore = null;
        long now = System.currentTimeMillis();
        for (final Search.Hit x : hits) {
            LinearLayout c;
            String tag = x.kind + ":" + (x.id != null ? x.id : x.chId + "@" + x.start);
            if ("program".equals(x.kind)) {
                String sub = x.sub + "  ·  " + Fmt.hm(x.start) + "–" + Fmt.hm(x.stop);
                float p = x.start <= now ? (now - x.start) / (float) Math.max(1, x.stop - x.start) : 0;
                c = Poster.logoCard(this, px, px.p(390), px.p(219), x.img, x.name, sub, p, null);
            } else if ("channel".equals(x.kind)) {
                Xtream.Prog np = Epg.now(x.chId);
                c = Poster.logoCard(this, px, px.p(390), px.p(219), x.img, x.name, np != null ? np.title : x.sub, np != null ? np.progress(now) : 0, null);
                Epg.want(this, x.chId);
            } else {
                c = Poster.card(this, px, px.p(200), px.p(300), x.img, x.name, x.sub, 0, null);
            }
            View box = Poster.box(c);
            box.setTag(0x7f0a0002, tag);
            box.setOnClickListener(v -> open(x));
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
            if (r.getChildCount() > 0) l.leftMargin = px.p(30);
            r.addView(c, l);
            if (tag.equals(keepTag)) restore = box;
        }
        return restore;
    }

    private void open(Search.Hit x) {
        if ("program".equals(x.kind) || "channel".equals(x.kind)) {
            Catalog.Ch ch = Catalog.byId.get(x.chId);
            if (ch == null) return;
            List<Catalog.Ch> l = Catalog.inCategory(ch.cat);
            int[] ids = new int[l.size()];
            int at = 0;
            for (int i = 0; i < ids.length; i++) { ids[i] = l.get(i).id; if (ids[i] == ch.id) at = i; }
            startActivity(new Intent(this, PlayerActivity.class).putExtra("ids", ids).putExtra("i", at).putExtra("cat", ch.cat));
        } else {
            startActivity(new Intent(this, DetailActivity.class).putExtra("kind", x.kind).putExtra("id", x.id)
                    .putExtra("name", x.name).putExtra("poster", x.img).putExtra("ext", x.ext).putExtra("year", x.sub));
        }
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_SEARCH) { field.requestFocus(); return true; }
        return super.onKeyDown(code, e);
    }
}
