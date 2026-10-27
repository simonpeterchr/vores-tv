package dk.hjem.live;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.ArrayList;
import java.util.List;

/** Live-afspiller (design 1e): ▴▾ skifter kanal, OK åbner kanal-panelet, ◂▸ skifter gruppe i panelet. */
public class PlayerActivity extends Activity {
    static final int PANEL_ROWS = 8;
    private Px px;
    private FrameLayout root, videoBox;
    private SurfaceView surface;
    private View shadeLeft, shadeBottom;
    private LinearLayout panel, panelRows, infoBar;
    private TextView panelTitle, panelGroup, clock, numEntry;
    private ImageView infoLogo;
    private TextView infoCh, infoTitle, infoStart, infoEnd, infoNext, infoHint, infoRes, infoMsg;
    private Ui.Bar infoProg;
    private ExoPlayer player;
    private final Handler h = new Handler(Looper.getMainLooper());
    private List<Catalog.Ch> list = new ArrayList<>();
    private String cat;
    private int idx;          // kanal der spiller
    private int panelSel;     // markering i panelet
    private List<Catalog.Ch> panelList = new ArrayList<>();
    private String panelCat;
    private boolean triedHls;
    private Catalog.Ch playing;
    private String digits = "";
    private final Runnable hideInfo = () -> { if (!panelOpen()) fade(infoBar, false); fade(shadeBottom, panelOpen()); };
    private final Runnable doZap = () -> playIndex(idx);
    private final Runnable doNumber = this::jumpNumber;
    private final Runnable onData = this::refresh;
    private final Runnable tick = new Runnable() {
        @Override public void run() { clock.setText(Fmt.clock()); refresh(); h.postDelayed(this, 20_000); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        px = new Px(this);
        cat = getIntent().getStringExtra("cat");
        int[] ids = getIntent().getIntArrayExtra("ids");
        if (ids != null) for (int id : ids) { Catalog.Ch c = Catalog.byId.get(id); if (c != null) list.add(c); }
        idx = Math.max(0, Math.min(getIntent().getIntExtra("i", 0), list.size() - 1));
        build();
    }

    private void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        setContentView(root);
        videoBox = new FrameLayout(this);
        surface = new SurfaceView(this);
        videoBox.addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        root.addView(videoBox, new FrameLayout.LayoutParams(-1, -1));

        shadeLeft = new View(this);
        shadeLeft.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x8C000000, 0x00000000}));
        root.addView(shadeLeft, new FrameLayout.LayoutParams(px.p(860), -1));
        shadeBottom = new View(this);
        shadeBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0x99000000, 0x00000000}));
        root.addView(shadeBottom, new FrameLayout.LayoutParams(-1, px.p(380), Gravity.BOTTOM));

        clock = px.t(27, 500, Theme.white(0.9f));
        clock.setShadowLayer(px.p(12), 0, 0, 0x80000000);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        cl.topMargin = px.p(54); cl.rightMargin = px.p(72);
        root.addView(clock, cl);

        numEntry = px.t(96, 700, 0xFFFFFFFF);
        numEntry.setShadowLayer(px.p(20), 0, 0, 0xA0000000);
        numEntry.setVisibility(View.GONE);
        FrameLayout.LayoutParams nel = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        nel.topMargin = px.p(110); nel.rightMargin = px.p(72);
        root.addView(numEntry, nel);

        // Kanal-panel
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.round(Ui.GLASS, px.p(33)));
        panel.setPadding(px.p(21), px.p(30), px.p(21), px.p(30));
        LinearLayout ph = new LinearLayout(this);
        ph.setGravity(Gravity.CENTER_VERTICAL);
        ph.setPadding(px.p(15), 0, px.p(15), px.p(18));
        panelTitle = px.t(27, 600, 0xFFFFFFFF);
        ph.addView(panelTitle, new LinearLayout.LayoutParams(0, -2, 1));
        panelGroup = px.t(19.5f, 400, Theme.white(0.5f));
        ph.addView(panelGroup);
        panel.addView(ph);
        panelRows = new LinearLayout(this);
        panelRows.setOrientation(LinearLayout.VERTICAL);
        panelRows.setClipChildren(false);
        panel.addView(panelRows, new LinearLayout.LayoutParams(-1, 0, 1));
        panel.setClipChildren(false);
        panel.setVisibility(View.GONE);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(px.p(558), -1);
        pl.leftMargin = px.p(48); pl.topMargin = px.p(48); pl.bottomMargin = px.p(48);
        root.addView(panel, pl);

        // Info-bjælke
        infoBar = new LinearLayout(this);
        infoBar.setOrientation(LinearLayout.VERTICAL);
        infoBar.setBackground(Ui.round(Ui.GLASS, px.p(33)));
        infoBar.setPadding(px.p(39), px.p(33), px.p(39), px.p(30));
        LinearLayout r1 = new LinearLayout(this);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        infoLogo = new ImageView(this);
        infoLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        infoLogo.setBackground(Ui.round(0xFF26262B, px.p(12)));
        infoLogo.setPadding(px.p(6), px.p(6), px.p(6), px.p(6));
        r1.addView(infoLogo, new LinearLayout.LayoutParams(px.p(96), px.p(63)));
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        infoCh = px.t(19.5f, 400, Theme.white(0.6f));
        tcol.addView(infoCh);
        infoTitle = px.t(36, 700, 0xFFFFFFFF);
        infoTitle.setLetterSpacing(-0.02f);
        LinearLayout.LayoutParams ttl = new LinearLayout.LayoutParams(-1, -2);
        ttl.topMargin = px.p(6);
        tcol.addView(infoTitle, ttl);
        LinearLayout.LayoutParams tcl = new LinearLayout.LayoutParams(0, -2, 1);
        tcl.leftMargin = px.p(24); tcl.rightMargin = px.p(18);
        r1.addView(tcol, tcl);
        r1.addView(Ui.badge(this, px, "LIVE", true));
        infoRes = Ui.badge(this, px, "", false);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
        rl.leftMargin = px.p(12);
        r1.addView(infoRes, rl);
        infoBar.addView(r1);
        LinearLayout r2 = new LinearLayout(this);
        r2.setGravity(Gravity.CENTER_VERTICAL);
        infoStart = px.t(19.5f, 500, Theme.white(0.7f));
        r2.addView(infoStart);
        infoProg = new Ui.Bar(this, Theme.white(0.2f), 0xFFFFFFFF);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, px.p(7.5f), 1);
        bl.leftMargin = px.p(21); bl.rightMargin = px.p(21);
        r2.addView(infoProg, bl);
        infoEnd = px.t(19.5f, 500, Theme.white(0.7f));
        r2.addView(infoEnd);
        LinearLayout.LayoutParams r2l = new LinearLayout.LayoutParams(-1, -2);
        r2l.topMargin = px.p(21);
        infoBar.addView(r2, r2l);
        LinearLayout r3 = new LinearLayout(this);
        infoNext = px.t(19.5f, 400, Theme.white(0.6f));
        r3.addView(infoNext, new LinearLayout.LayoutParams(0, -2, 1));
        infoHint = px.t(19.5f, 400, Theme.white(0.6f));
        infoHint.setText("▴▾ Kanaler    OK Oversigt    ★ Menu-knap: favorit");
        r3.addView(infoHint);
        LinearLayout.LayoutParams r3l = new LinearLayout.LayoutParams(-1, -2);
        r3l.topMargin = px.p(18);
        infoBar.addView(r3, r3l);
        infoMsg = px.t(21, 500, 0xFFFFB4B4);
        infoMsg.setVisibility(View.GONE);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, -2);
        ml.topMargin = px.p(12);
        infoBar.addView(infoMsg, ml);
        root.addView(infoBar, infoParams());
    }

    private FrameLayout.LayoutParams infoParams() {
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        l.leftMargin = px.p(panelOpen() ? 654 : 60); l.rightMargin = px.p(60); l.bottomMargin = px.p(54);
        return l;
    }

    private boolean panelOpen() { return panel.getVisibility() == View.VISIBLE; }

    @Override protected void onResume() {
        super.onResume();
        Catalog.listeners.add(onData);
        player = Play.create(this);
        player.setVideoSurfaceView(surface);
        player.addListener(new Player.Listener() {
            @Override public void onVideoSizeChanged(VideoSize v) { fit(v); }
            @Override public void onPlayerError(PlaybackException e) {
                if (!triedHls && playing != null) { triedHls = true; Play.live(player, Cred.load(PlayerActivity.this), playing, true); return; }
                infoMsg.setText("Kanalen svarer ikke lige nu. Prøv en anden, eller vent lidt og tryk OK.");
                infoMsg.setVisibility(View.VISIBLE);
                showInfo(15_000);
            }
            @Override public void onPlaybackStateChanged(int s) {
                if (s == Player.STATE_READY) { infoMsg.setVisibility(View.GONE); res(); }
            }
        });
        if (!list.isEmpty()) playIndex(idx);
        h.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        Catalog.listeners.remove(onData);
        h.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
        playing = null;
    }

    private void fit(VideoSize v) {
        if (v.width == 0 || v.height == 0) return;
        float ar = v.width * v.pixelWidthHeightRatio / v.height;
        int W = root.getWidth(), H = root.getHeight();
        if (W == 0 || H == 0) return;
        int w = W, hh = Math.round(W / ar);
        if (hh > H) { hh = H; w = Math.round(H * ar); }
        surface.setLayoutParams(new FrameLayout.LayoutParams(w, hh, Gravity.CENTER));
    }

    private void res() {
        Format f = player != null ? player.getVideoFormat() : null;
        if (f == null || f.height <= 0) { infoRes.setVisibility(View.GONE); return; }
        String s = f.height + "p" + (f.frameRate > 0 ? " · " + Math.round(f.frameRate) + "fps" : "");
        infoRes.setText(s);
        infoRes.setVisibility(View.VISIBLE);
    }

    private void playIndex(int i) {
        if (list.isEmpty() || player == null) return;
        idx = (i % list.size() + list.size()) % list.size();
        Catalog.Ch c = list.get(idx);
        if (playing != null && playing.id == c.id) return;
        playing = c;
        triedHls = false;
        infoMsg.setVisibility(View.GONE);
        infoRes.setVisibility(View.GONE);
        Play.live(player, Cred.load(this), c, false);
        Catalog.setLastChannel(this, c.id);
        Epg.want(this, c.id);
        bindInfo(c);
        showInfo(5_000);
    }

    /** Opdaterer info-bjælken med det samme ved zapping; selve skiftet venter et øjeblik, så man kan bladre hurtigt. */
    private void zap(int delta) {
        if (list.isEmpty()) return;
        idx = (idx + delta + list.size()) % list.size();
        Catalog.Ch c = list.get(idx);
        Epg.want(this, c.id);
        bindInfo(c);
        showInfo(5_000);
        h.removeCallbacks(doZap);
        h.postDelayed(doZap, 350);
    }

    private void showInfo(long ms) {
        fade(infoBar, true);
        fade(shadeBottom, true);
        h.removeCallbacks(hideInfo);
        h.postDelayed(hideInfo, ms);
    }

    private void fade(View v, boolean show) {
        if (show) { if (v.getVisibility() != View.VISIBLE) { v.setAlpha(0f); v.setVisibility(View.VISIBLE); } v.animate().alpha(1f).setDuration(180).start(); }
        else v.animate().alpha(0f).setDuration(250).withEndAction(() -> v.setVisibility(View.GONE)).start();
    }

    private void bindInfo(Catalog.Ch c) {
        Img.load(infoLogo, c.logo, px.p(96));
        int pos = list.indexOf(c) + 1;
        infoCh.setText((pos > 0 ? pos + " · " : "") + c.name + (Catalog.isFav(this, c.id) ? "  ★" : ""));
        Xtream.Prog n = Epg.now(c.id), x = Epg.next(c.id);
        long t = System.currentTimeMillis();
        infoTitle.setText(n != null ? n.title : c.name);
        infoStart.setText(n != null ? Fmt.hm(n.start) : "");
        infoEnd.setText(n != null ? Fmt.hm(n.stop) : "");
        infoProg.setVisibility(n != null ? View.VISIBLE : View.INVISIBLE);
        infoProg.set(n != null ? n.progress(t) : 0);
        infoNext.setText(x != null ? "Derefter · " + Fmt.hm(x.start) + "  " + x.title : "");
    }

    private void refresh() {
        if (!list.isEmpty()) bindInfo(list.get(idx));
        if (panelOpen()) bindPanel();
    }

    // ---- panel ----
    private void openPanel() {
        panelCat = cat != null ? cat : LiveActivity.FAV;
        panelList = new ArrayList<>(list);
        panelSel = idx;
        panel.setVisibility(View.VISIBLE);
        panel.setAlpha(0f); panel.setTranslationX(-px.p(40));
        panel.animate().alpha(1f).translationX(0).setDuration(200).start();
        fade(shadeLeft, true);
        infoBar.setLayoutParams(infoParams());
        h.removeCallbacks(hideInfo);
        fade(infoBar, true); fade(shadeBottom, true);
        bindPanel();
    }

    private void closePanel() {
        panel.animate().alpha(0f).translationX(-px.p(40)).setDuration(180).withEndAction(() -> {
            panel.setVisibility(View.GONE);
            infoBar.setLayoutParams(infoParams());
        }).start();
        showInfo(4_000);
    }

    private List<String> groups() {
        List<String> g = new ArrayList<>();
        g.add(LiveActivity.FAV);
        g.addAll(Catalog.shownGroups(this));
        if (panelCat != null && !g.contains(panelCat) && Catalog.cats.containsKey(panelCat)) g.add(panelCat);
        return g;
    }

    private String groupName(String id) {
        if (LiveActivity.FAV.equals(id)) return "Favoritter";
        Catalog.Cat k = Catalog.cats.get(id);
        return k != null ? k.name : "";
    }

    private void switchGroup(int d) {
        List<String> g = groups();
        int i = g.indexOf(panelCat);
        panelCat = g.get(((i < 0 ? 0 : i) + d + g.size()) % g.size());
        panelList = LiveActivity.FAV.equals(panelCat) ? Catalog.favorites(this) : Catalog.inCategory(panelCat);
        panelSel = 0;
        if (playing != null) for (int k = 0; k < panelList.size(); k++) if (panelList.get(k).id == playing.id) panelSel = k;
        bindPanel();
    }

    private void bindPanel() {
        panelTitle.setText(groupName(panelCat));
        List<String> g = groups();
        panelGroup.setText(g.size() > 1 ? "◂ ▸  " + (g.indexOf(panelCat) + 1) + "/" + g.size() : "");
        panelRows.removeAllViews();
        if (panelList.isEmpty()) {
            TextView e = px.t(22, 400, Theme.white(0.6f));
            e.setSingleLine(false);
            e.setText("Ingen kanaler her endnu.");
            e.setPadding(px.p(15), px.p(10), px.p(15), 0);
            panelRows.addView(e);
            return;
        }
        int first = Math.max(0, Math.min(panelSel - PANEL_ROWS / 2, panelList.size() - PANEL_ROWS));
        long t = System.currentTimeMillis();
        for (int i = first; i < Math.min(panelList.size(), first + PANEL_ROWS); i++) {
            Catalog.Ch c = panelList.get(i);
            Epg.want(this, c.id);
            boolean s = i == panelSel, on = playing != null && playing.id == c.id;
            int fg = s ? 0xFF000000 : 0xFFFFFFFF;
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(px.p(15), px.p(13), px.p(15), px.p(13));
            r.setBackground(s ? Ui.round(0xFFFFFFFF, px.p(18)) : null);
            if (s) { r.setElevation(px.p(16)); r.setScaleX(1.04f); r.setScaleY(1.04f); }
            TextView num = px.t(19.5f, 500, s ? 0x8C000000 : Theme.white(0.55f));
            num.setText(String.valueOf(i + 1));
            r.addView(num, new LinearLayout.LayoutParams(px.p(54), -2));
            ImageView lg = new ImageView(this);
            lg.setScaleType(ImageView.ScaleType.FIT_CENTER);
            lg.setBackground(Ui.round(s ? 0xFF1C1C1F : 0xFF2A2A30, px.p(9)));
            lg.setPadding(px.p(4), px.p(4), px.p(4), px.p(4));
            Img.load(lg, c.logo, px.p(66));
            r.addView(lg, new LinearLayout.LayoutParams(px.p(66), px.p(45)));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView nm = px.t(21, 600, fg);
            nm.setText((on ? "▶ " : "") + c.name);
            col.addView(nm);
            Xtream.Prog n = Epg.now(c.id);
            TextView pr = px.t(18, 400, s ? 0x99000000 : Theme.white(0.6f));
            pr.setText(n != null ? n.title : " ");
            LinearLayout.LayoutParams prl = new LinearLayout.LayoutParams(-1, -2);
            prl.topMargin = px.p(4);
            col.addView(pr, prl);
            Ui.Bar bar = new Ui.Bar(this, 0x59808080, fg);
            bar.set(n != null ? n.progress(t) : 0);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, px.p(4.5f));
            bl.topMargin = px.p(8);
            col.addView(bar, bl);
            bar.setVisibility(n != null ? View.VISIBLE : View.INVISIBLE);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1);
            cl.leftMargin = px.p(18);
            r.addView(col, cl);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = px.p(4);
            panelRows.addView(r, rl);
        }
    }

    private void choosePanel() {
        if (panelList.isEmpty()) return;
        list = new ArrayList<>(panelList);
        cat = panelCat;
        h.removeCallbacks(doZap);
        playing = null;
        playIndex(panelSel);
        closePanel();
    }

    // ---- taster ----
    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) { digit(code - KeyEvent.KEYCODE_0); return true; }
        if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) { digit(code - KeyEvent.KEYCODE_NUMPAD_0); return true; }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (panelOpen()) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP: if (panelSel > 0) { panelSel--; bindPanel(); } return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: if (panelSel < panelList.size() - 1) { panelSel++; bindPanel(); } return true;
                case KeyEvent.KEYCODE_DPAD_LEFT: switchGroup(-1); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: switchGroup(1); return true;
                case KeyEvent.KEYCODE_BACK: closePanel(); return true;
                case KeyEvent.KEYCODE_MENU: if (!panelList.isEmpty()) { Catalog.toggleFav(this, panelList.get(panelSel).id); bindPanel(); } return true;
            }
            if (ok) { if (e.getRepeatCount() == 0) choosePanel(); return true; }
            return super.onKeyDown(code, e);
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_CHANNEL_DOWN: zap(-1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_CHANNEL_UP: zap(1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_INFO: showInfo(6_000); return true;
            case KeyEvent.KEYCODE_MENU: case KeyEvent.KEYCODE_BOOKMARK:
                if (playing != null) { Catalog.toggleFav(this, playing.id); bindInfo(playing); showInfo(4_000); } return true;
            case KeyEvent.KEYCODE_BACK:
                if (infoBar.getVisibility() == View.VISIBLE && infoBar.getAlpha() > 0.5f) { h.removeCallbacks(hideInfo); hideInfo.run(); return true; }
                break;
        }
        if (ok) {
            if (e.getRepeatCount() > 0) return true;
            if (infoMsg.getVisibility() == View.VISIBLE && playing != null) { Catalog.Ch c = playing; playing = null; playIndex(list.indexOf(c)); return true; }
            openPanel();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    private void digit(int d) {
        if (digits.length() >= 4) digits = "";
        digits += d;
        numEntry.setText(digits);
        numEntry.setVisibility(View.VISIBLE);
        h.removeCallbacks(doNumber);
        h.postDelayed(doNumber, 1500);
    }

    private void jumpNumber() {
        numEntry.setVisibility(View.GONE);
        int n;
        try { n = Integer.parseInt(digits); } catch (Exception e) { digits = ""; return; }
        digits = "";
        if (n >= 1 && n <= list.size()) { playIndex(n - 1); return; }
        Catalog.Ch c = Catalog.byNumber(n);
        if (c == null) { infoMsg.setText("Ingen kanal nummer " + n + "."); infoMsg.setVisibility(View.VISIBLE); showInfo(3_000); return; }
        list = Catalog.inCategory(c.cat);
        cat = c.cat;
        playing = null;
        playIndex(list.indexOf(c));
    }
}
