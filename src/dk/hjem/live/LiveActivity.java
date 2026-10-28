package dk.hjem.live;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.ArrayList;
import java.util.List;

/** Live-TV (design 1b): forhåndsvisning, programinfo, grupper og en tidslinje-guide. */
public class LiveActivity extends Activity {
    static final String FAV = "__fav", ALL = "__all";
    static final int ROWS = 5, ROW_H = 78, ROW_GAP = 9, CH_W = 336, TL_X = 456, TL_W = 1350, GRID_Y = 594;
    static final float PX_MIN = 11.25f; // 1350 px = 120 min
    private Px px;
    private FrameLayout root, setup;
    private Ui.TopBar top;
    private FrameLayout preview;
    private TextureView video;
    private ImageView previewLogo;
    private TextView infoCh, infoTitle, infoStart, infoEnd, infoNext, infoDesc;
    private Ui.Bar infoBar;
    private LinearLayout pills;
    private final List<TextView> pillViews = new ArrayList<>();
    private final List<String> pillIds = new ArrayList<>();
    private FrameLayout grid, slotRow;
    private View nowLine;
    private final FrameLayout[] rows = new FrameLayout[ROWS];
    private TextView empty;
    private List<Catalog.Ch> list = new ArrayList<>();
    private String cat = FAV;
    private int sel, topRow, prog; // prog: -1 = det der kører nu
    private long winStart;
    private ExoPlayer player;
    private boolean triedHls;
    private Catalog.Ch previewing;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable startPreview = this::startPreview;
    private final Runnable onData = this::dataChanged;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            top.clock.setText(Fmt.clock());
            bindAll();
            h.postDelayed(this, 30_000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        px = new Px(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        cat = getSharedPreferences("live", MODE_PRIVATE).getString("cat", FAV);
        wantCatName = getIntent().getStringExtra("catName"); // genvej: åbn en bestemt gruppe (navn)
        build();
        Web.start(this);
        Catalog.load(this, false);
        openTab(getIntent());
    }

    /** Genvej udefra (fx fra Hjem): extra "open" = movie | series. */
    @Override protected void onNewIntent(Intent in) { super.onNewIntent(in); openTab(in); }

    private void openTab(Intent in) {
        String k = in != null ? in.getStringExtra("open") : null;
        if (Vod.MOVIE.equals(k) || Vod.SERIES.equals(k)) startActivity(new Intent(this, MediaActivity.class).putExtra("kind", k));
    }

    private void build() {
        top = Ui.topBar(this, px, Nav.TABS, Nav.LIVE);
        root.addView(top.root, Ui.at(px.p(96), px.p(42), px.p(1728), -2));
        Nav.wire(this, top, Nav.LIVE, this::soon);

        preview = new FrameLayout(this);
        preview.setBackground(Ui.round(0xFF1C1C21, px.p(21)));
        preview.setClipToOutline(true);
        previewLogo = new ImageView(this);
        previewLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewLogo.setPadding(px.p(130), px.p(80), px.p(130), px.p(80));
        preview.addView(previewLogo, new FrameLayout.LayoutParams(-1, -1));
        video = new TextureView(this);
        video.setAlpha(0f);
        preview.addView(video, new FrameLayout.LayoutParams(-1, -1));
        TextView live = Ui.badge(this, px, "LIVE", true);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        ll.leftMargin = px.p(21); ll.bottomMargin = px.p(18);
        preview.addView(live, ll);
        root.addView(preview, Ui.at(px.p(96), px.p(144), px.p(528), px.p(297)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        infoCh = px.t(21, 500, Theme.white(0.6f));
        info.addView(infoCh);
        infoTitle = px.t(51, 700, 0xFFFFFFFF);
        infoTitle.setLetterSpacing(-0.025f);
        info.addView(infoTitle, lp(-1, -2, 12));
        LinearLayout time = new LinearLayout(this);
        time.setGravity(Gravity.CENTER_VERTICAL);
        infoStart = px.t(21, 500, Theme.white(0.7f));
        time.addView(infoStart);
        infoBar = new Ui.Bar(this, Theme.white(0.2f), 0xFFFFFFFF);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px.p(300), px.p(6));
        bl.leftMargin = px.p(18); bl.rightMargin = px.p(18);
        time.addView(infoBar, bl);
        infoEnd = px.t(21, 500, Theme.white(0.7f));
        time.addView(infoEnd);
        infoNext = px.t(21, 400, Theme.white(0.5f));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1);
        nl.leftMargin = px.p(18);
        time.addView(infoNext, nl);
        info.addView(time, lp(-1, -2, 16));
        infoDesc = px.t(22, 400, Theme.white(0.62f));
        infoDesc.setSingleLine(false);
        infoDesc.setMaxLines(3);
        infoDesc.setLineSpacing(px.p(6), 1f);
        info.addView(infoDesc, lp(-1, -2, 18));
        root.addView(info, Ui.at(px.p(672), px.p(150), px.p(1152), px.p(300)));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        pills = new LinearLayout(this);
        hs.addView(pills);
        root.addView(hs, Ui.at(px.p(96), px.p(470), px.p(1728), px.p(60)));

        slotRow = new FrameLayout(this);
        root.addView(slotRow, Ui.at(px.p(TL_X), px.p(549), px.p(TL_W), px.p(30)));

        grid = new FrameLayout(this) {
            @Override public boolean dispatchKeyEvent(KeyEvent e) {
                return gridKey(e) || super.dispatchKeyEvent(e);
            }
        };
        grid.setFocusable(true);
        grid.setClipChildren(false);
        grid.setOnFocusChangeListener((v, has) -> bindAll());
        root.addView(grid, Ui.at(0, px.p(GRID_Y), -1, px.p(ROWS * (ROW_H + ROW_GAP))));
        for (int i = 0; i < ROWS; i++) {
            FrameLayout r = new FrameLayout(this);
            r.setClipChildren(false);
            grid.addView(r, Ui.at(px.p(96), px.p(i * (ROW_H + ROW_GAP)), px.p(1824), px.p(ROW_H)));
            rows[i] = r;
        }
        empty = px.t(26, 500, Theme.white(0.6f));
        empty.setSingleLine(false);
        grid.addView(empty, Ui.at(px.p(96), px.p(20), px.p(1300), -2));
        nowLine = new View(this);
        nowLine.setBackgroundColor(Ui.RED);
        root.addView(nowLine, Ui.at(0, px.p(GRID_Y - 12), px.p(3), px.p(ROWS * (ROW_H + ROW_GAP) + 12)));

        setup = new FrameLayout(this);
        setup.setBackgroundColor(Ui.BG);
        setup.setVisibility(View.GONE);
        root.addView(setup, new FrameLayout.LayoutParams(-1, -1));
    }

    private LinearLayout.LayoutParams lp(int w, int hh, float top) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, hh);
        l.topMargin = px.p(top);
        return l;
    }

    private void soon() {
        infoCh.setText("");
        infoTitle.setText("Kommer snart");
        infoDesc.setText("Hjem-skærmen bygges i næste runde. Live-TV, film og serier virker allerede.");
    }

    @Override protected void onResume() {
        super.onResume();
        Catalog.listeners.add(onData);
        player = Play.create(this);
        player.setVideoTextureView(video);
        player.addListener(new Player.Listener() {
            @Override public void onRenderedFirstFrame() { video.animate().alpha(1f).setDuration(250).start(); }
            @Override public void onPlayerError(PlaybackException e) {
                if (!triedHls && previewing != null) { triedHls = true; Play.live(player, Cred.load(LiveActivity.this), previewing, true); }
            }
        });
        dataChanged();
        h.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        Catalog.listeners.remove(onData);
        h.removeCallbacks(tick);
        h.removeCallbacks(startPreview);
        if (player != null) { player.release(); player = null; }
        previewing = null;
        video.setAlpha(0f);
    }

    // ---- data ----
    private String shownSetup = "", wantCatName;

    private void dataChanged() {
        boolean in = Cred.load(this).ok();
        String state = in + "|" + Catalog.loaded + "|" + Catalog.error;
        if (!in || !Catalog.loaded) {
            if (!state.equals(shownSetup)) showSetup(in);
            shownSetup = state;
            return;
        }
        shownSetup = state;
        boolean wasSetup = setup.getVisibility() == View.VISIBLE;
        if (wasSetup) setup.setVisibility(View.GONE);
        if (wantCatName != null) {
            for (Catalog.Cat k : Catalog.cats.values()) if (k.name.equalsIgnoreCase(wantCatName)) { cat = k.id; sel = 0; topRow = 0; break; }
            wantCatName = null;
        }
        buildPills();
        Catalog.Ch keep = cur();
        list = cat.equals(FAV) ? Catalog.favorites(this) : Catalog.inCategory(cat);
        if (keep != null) { int i = indexOf(keep.id); if (i >= 0) sel = i; }
        if (sel >= list.size()) sel = Math.max(0, list.size() - 1);
        if (keep == null) {
            int last = Catalog.lastChannel(this), i = indexOf(last);
            if (i >= 0) sel = i;
        }
        clampTop();
        bindAll();
        if (picker == null && (wasSetup || getCurrentFocus() == null || getCurrentFocus() == setup)) {
            if (list.isEmpty() && pillViews.size() > 1) pillViews.get(1).requestFocus(); else grid.requestFocus();
        }
    }

    private int indexOf(int id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id == id) return i;
        return -1;
    }

    private Catalog.Ch cur() { return sel >= 0 && sel < list.size() ? list.get(sel) : null; }

    private void showSetup(boolean loggedIn) {
        setup.removeAllViews();
        setup.setVisibility(View.VISIBLE);
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        setup.addView(box, Ui.at(px.p(160), 0, px.p(1600), -1));
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        box.addView(txt, new LinearLayout.LayoutParams(0, -2, 1));
        TextView logo = px.t(33, 750, 0xFFFFFFFF);
        logo.setText("vores tv");
        txt.addView(logo);
        TextView t = px.t(64, 700, 0xFFFFFFFF);
        t.setText(loggedIn ? (Catalog.error != null ? "Kunne ikke hente kanaler" : "Henter kanaler …") : "Log ind på telefonen");
        txt.addView(t, lp(-2, -2, 40));
        TextView d = px.t(28, 400, Theme.white(0.7f));
        d.setSingleLine(false);
        d.setLineSpacing(px.p(10), 1f);
        d.setText(loggedIn ? (Catalog.error != null ? Catalog.error + "\nTryk OK for at prøve igen." : "Det tager et øjeblik første gang.")
                : "Scan koden med telefonens kamera og indtast jeres IPTV-login\n– eller indsæt bare jeres M3U-link. Det gemmes kun på tv'et.\n\nEr et andet tv i huset logget ind? Åbn dets telefon-side og tryk \"Del login\".");
        txt.addView(d, lp(-2, -2, 24));
        TextView a = px.t(24, 500, Theme.white(0.45f));
        a.setText(Web.address());
        txt.addView(a, lp(-2, -2, 36));
        if (!loggedIn) {
            ImageView qr = new ImageView(this);
            qr.setImageBitmap(Qr.make(Web.address(), px.p(400)));
            qr.setBackground(Ui.round(0xFFFFFFFF, px.p(24)));
            qr.setPadding(px.p(22), px.p(22), px.p(22), px.p(22));
            box.addView(qr, new LinearLayout.LayoutParams(px.p(444), px.p(444)));
        }
        setup.setFocusable(true);
        setup.setOnClickListener(v -> Catalog.load(this, true));
        setup.requestFocus();
    }

    private void buildPills() {
        List<String> ids = new ArrayList<>();
        ids.add(FAV);
        ids.addAll(Catalog.shownGroups(this));
        if (!cat.equals(FAV) && !ids.contains(cat) && Catalog.cats.containsKey(cat)) ids.add(cat);
        ids.add(ALL);
        if (ids.equals(pillIds)) { stylePills(); return; }
        pillIds.clear(); pillIds.addAll(ids);
        pills.removeAllViews(); pillViews.clear();
        for (int i = 0; i < ids.size(); i++) {
            final String id = ids.get(i);
            final TextView t = px.t(21, 500, 0xFFFFFFFF);
            t.setText(id.equals(FAV) ? "★ Favoritter" : id.equals(ALL) ? "Alle grupper …" : Catalog.cats.get(id).name);
            if (id.equals(ALL)) t.setOnClickListener(v -> openGroups());
            t.setPadding(px.p(22.5f), px.p(10.5f), px.p(22.5f), px.p(10.5f));
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> {
                stylePills();
                if (has && !id.equals(ALL)) { h.removeCallbacks(pickCat); pending = id; h.postDelayed(pickCat, 300); }
            });
            t.setOnKeyListener((v, code, e) -> {
                if (e.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN) { grid.requestFocus(); return true; }
                return false;
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) l.leftMargin = px.p(12);
            pills.addView(t, l);
            pillViews.add(t);
        }
        stylePills();
    }

    private String pending;
    private final Runnable pickCat = () -> {
        if (pending == null || pending.equals(cat)) return;
        cat = pending;
        getSharedPreferences("live", MODE_PRIVATE).edit().putString("cat", cat).apply();
        sel = 0; topRow = 0; prog = -1;
        list = cat.equals(FAV) ? Catalog.favorites(this) : Catalog.inCategory(cat);
        bindAll();
    };

    private GroupPicker picker;

    private void openGroups() {
        picker = new GroupPicker(this, px, GroupPicker.live(this), () -> {
            root.removeView(picker);
            picker = null;
            pillIds.clear();
            buildPills();
            focusPill();
        });
        root.addView(picker, new FrameLayout.LayoutParams(-1, -1));
        picker.requestFocus();
    }

    private void stylePills() {
        for (int i = 0; i < pillViews.size(); i++) {
            TextView t = pillViews.get(i);
            boolean on = pillIds.get(i).equals(cat), f = t.hasFocus();
            t.setBackground(Ui.round(f ? 0xFFFFFFFF : on ? Theme.white(0.28f) : Theme.white(0.1f), 999));
            t.setTextColor(f ? 0xFF000000 : on ? 0xFFFFFFFF : Theme.white(0.8f));
        }
    }

    private void clampTop() {
        if (sel < topRow) topRow = sel;
        if (sel >= topRow + ROWS) topRow = sel - ROWS + 1;
        topRow = Math.max(0, Math.min(topRow, Math.max(0, list.size() - ROWS)));
    }

    // ---- guide ----
    private boolean gridKey(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (sel > 0) { sel--; prog = -1; moved(); }
                else focusPill();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (sel < list.size() - 1) { sel++; prog = -1; moved(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int n = visibleProgs(cur()).size();
                if (prog < n - 1) { prog = prog < 0 ? nowIndex(cur()) + 1 : prog + 1; if (prog >= n) prog = n - 1; bindAll(); }
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (prog >= 0) { prog--; int ni = nowIndex(cur()); if (prog <= ni) prog = -1; bindAll(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() > 0) return true;
                play();
                return true;
            case KeyEvent.KEYCODE_MENU: case KeyEvent.KEYCODE_BOOKMARK:
                toggleFav();
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP: if (sel > 0) { sel--; moved(); } return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN: if (sel < list.size() - 1) { sel++; moved(); } return true;
        }
        return false;
    }

    @Override public boolean onKeyLongPress(int code, KeyEvent e) {
        if ((code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) && grid.hasFocus()) { toggleFav(); return true; }
        return super.onKeyLongPress(code, e);
    }

    private void focusPill() {
        int i = pillIds.indexOf(cat);
        if (i >= 0 && i < pillViews.size()) pillViews.get(i).requestFocus();
    }

    private void toggleFav() {
        Catalog.Ch c = cur();
        if (c == null) return;
        boolean was = Catalog.isFav(this, c.id);
        Catalog.toggleFav(this, c.id);
        infoCh.setText((was ? "Fjernet fra favoritter · " : "★ Tilføjet til favoritter · ") + c.name);
    }

    /** Bladring i guiden skifter ikke kanal – kun infoboksen følger markeringen. Kanalen skifter først ved OK. */
    private void moved() {
        clampTop();
        bindAll();
    }

    private void play() {
        Catalog.Ch c = cur();
        if (c == null) return;
        int[] ids = new int[list.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = list.get(i).id;
        startActivity(new Intent(this, PlayerActivity.class).putExtra("ids", ids).putExtra("i", sel).putExtra("cat", cat));
    }

    /** Forhåndsvisningen viser den kanal, der sidst blev set (eller den første, hvis der ingen er). */
    private void startPreview() {
        Catalog.Ch c = Catalog.byId.get(Catalog.lastChannel(this));
        if (c == null) c = cur();
        if (player == null || c == null || (previewing != null && previewing.id == c.id)) return;
        previewing = c;
        triedHls = false;
        video.setAlpha(0f);
        Play.live(player, Cred.load(this), c, false);
    }

    private List<Xtream.Prog> visibleProgs(Catalog.Ch c) {
        List<Xtream.Prog> out = new ArrayList<>();
        if (c == null) return out;
        long end = winStart + 120 * 60_000L;
        for (Xtream.Prog p : Epg.get(c.id)) if (p.stop > winStart && p.start < end) out.add(p);
        return out;
    }

    private int nowIndex(Catalog.Ch c) {
        long t = System.currentTimeMillis();
        List<Xtream.Prog> l = visibleProgs(c);
        for (int i = 0; i < l.size(); i++) if (l.get(i).now(t)) return i;
        return -1;
    }

    private void bindAll() {
        if (setup.getVisibility() == View.VISIBLE) return;
        long now = System.currentTimeMillis();
        long half = 30 * 60_000L;
        winStart = ((now - 20 * 60_000L) / half) * half;
        slotRow.removeAllViews();
        for (int i = 0; i < 4; i++) {
            TextView s = px.t(19.5f, 500, Theme.white(0.5f));
            s.setText(Fmt.hm(winStart + i * half));
            slotRow.addView(s, Ui.at(px.p(i * 30 * PX_MIN), 0, -2, -2));
        }
        nowLine.setTranslationX(px.p(TL_X + (now - winStart) / 60_000f * PX_MIN));
        nowLine.setVisibility(list.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        slotRow.setVisibility(list.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        boolean gf = grid.hasFocus();
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        if (list.isEmpty()) empty.setText(cat.equals(FAV)
                ? "Ingen favoritter endnu.\nVælg en gruppe ovenfor, og hold OK nede på de kanaler I ser.\nDet kan også gøres på telefonen: " + Web.address().replace("http://", "")
                : "Ingen kanaler i gruppen.");
        for (int i = 0; i < ROWS; i++) {
            int idx = topRow + i;
            if (idx < list.size()) { rows[i].setVisibility(View.VISIBLE); bindRow(rows[i], list.get(idx), idx + 1, idx == sel && gf); }
            else rows[i].setVisibility(View.INVISIBLE);
        }
        bindInfo();
        if (previewing == null) { h.removeCallbacks(startPreview); h.postDelayed(startPreview, 900); }
    }

    private void bindRow(FrameLayout r, Catalog.Ch c, int pos, boolean focused) {
        r.removeAllViews();
        Epg.want(this, c.id);
        LinearLayout cell = new LinearLayout(this);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(px.p(18), 0, px.p(18), 0);
        cell.setBackground(focused ? Ui.round(Theme.white(0.1f), px.p(15)) : null);
        TextView num = px.t(19.5f, 500, Theme.white(0.5f));
        num.setText(String.valueOf(pos));
        cell.addView(num, new LinearLayout.LayoutParams(px.p(56), -2));
        ImageView logo = new ImageView(this);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setBackground(Ui.round(0xFF1F1F24, px.p(9)));
        logo.setPadding(px.p(4), px.p(4), px.p(4), px.p(4));
        Img.load(logo, c.logo, px.p(60));
        cell.addView(logo, new LinearLayout.LayoutParams(px.p(60), px.p(42)));
        TextView name = px.t(21, 500, 0xFFFFFFFF);
        name.setText((Catalog.isFav(this, c.id) && !cat.equals(FAV) ? "★ " : "") + c.name);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1);
        nl.leftMargin = px.p(18);
        cell.addView(name, nl);
        r.addView(cell, new FrameLayout.LayoutParams(px.p(CH_W), -1));

        long t = System.currentTimeMillis(), end = winStart + 120 * 60_000L;
        List<Xtream.Prog> ps = visibleProgs(c);
        int focusIdx = focused ? (prog >= 0 ? prog : nowIndex(c)) : -1;
        if (ps.isEmpty()) {
            TextView none = px.t(19.5f, 400, Theme.white(0.35f));
            none.setText("Ingen programinfo");
            none.setGravity(Gravity.CENTER_VERTICAL);
            none.setPadding(px.p(21), 0, 0, 0);
            none.setBackground(Ui.round(focused ? 0xFFFFFFFF : Theme.white(0.06f), px.p(15)));
            if (focused) none.setTextColor(0xFF000000);
            r.addView(none, Ui.at(px.p(TL_X - 96), 0, px.p(TL_W - 6), -1));
            return;
        }
        for (int i = 0; i < ps.size(); i++) {
            Xtream.Prog p = ps.get(i);
            long a = Math.max(p.start, winStart), b = Math.min(p.stop, end);
            int x = px.p((a - winStart) / 60_000f * PX_MIN), w = px.p((b - a) / 60_000f * PX_MIN) - px.p(6);
            if (w < px.p(8)) continue;
            boolean live = p.now(t), f = i == focusIdx;
            LinearLayout blk = new LinearLayout(this);
            blk.setOrientation(LinearLayout.VERTICAL);
            blk.setGravity(Gravity.CENTER_VERTICAL);
            blk.setPadding(px.p(21), 0, px.p(12), 0);
            blk.setBackground(Ui.round(f ? 0xFFFFFFFF : live ? Theme.white(0.13f) : Theme.white(0.06f), px.p(15)));
            TextView tt = px.t(21, 600, f ? 0xFF000000 : 0xFFFFFFFF);
            tt.setText(p.title);
            blk.addView(tt);
            TextView tm = px.t(18, 400, f ? 0x99000000 : Theme.white(0.6f));
            tm.setText(Fmt.hm(p.start) + " – " + Fmt.hm(p.stop));
            blk.addView(tm, lp(-2, -2, 3));
            if (f) { blk.setElevation(px.p(18)); blk.setScaleX(1.02f); blk.setScaleY(1.04f); }
            r.addView(blk, Ui.at(px.p(TL_X - 96) + x, 0, w, -1));
        }
    }

    private void bindInfo() {
        Catalog.Ch c = cur();
        if (c == null) { infoCh.setText(""); infoTitle.setText(list.isEmpty() ? "" : " "); infoStart.setText(""); infoEnd.setText(""); infoNext.setText(""); infoDesc.setText(""); infoBar.set(0); previewLogo.setImageDrawable(null); return; }
        Catalog.Ch pv = previewing != null ? previewing : Catalog.byId.get(Catalog.lastChannel(this));
        Img.load(previewLogo, (pv != null ? pv : c).logo, px.p(270));
        List<Xtream.Prog> ps = visibleProgs(c);
        int ni = nowIndex(c);
        int fi = prog >= 0 && prog < ps.size() ? prog : ni;
        Xtream.Prog p = fi >= 0 && fi < ps.size() ? ps.get(fi) : null;
        long t = System.currentTimeMillis();
        infoCh.setText((sel + 1) + " · " + c.name);
        infoTitle.setText(p != null ? p.title : c.name);
        infoStart.setText(p != null ? Fmt.hm(p.start) : "");
        infoEnd.setText(p != null ? Fmt.hm(p.stop) : "");
        infoBar.setVisibility(p != null ? View.VISIBLE : View.INVISIBLE);
        infoBar.set(p != null && p.now(t) ? p.progress(t) : 0);
        Xtream.Prog nx = Epg.next(c.id);
        infoNext.setText(p != null && p.now(t) && nx != null ? "· Derefter " + nx.title : p != null && !p.now(t) ? "· Senere i dag" : "");
        infoDesc.setText(p != null && !TextUtils.isEmpty(p.desc) ? p.desc : "");
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK && grid.hasFocus() && prog >= 0) { prog = -1; bindAll(); return true; }
        return super.onKeyDown(code, e);
    }
}
