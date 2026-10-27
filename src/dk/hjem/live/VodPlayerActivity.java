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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.ArrayList;
import java.util.List;

/** Film/afsnit: OK pause, ◂▸ spol (hold for hurtigere), Menu for lyd og undertekster, næste afsnit af sig selv. */
public class VodPlayerActivity extends Activity {
    private Px px;
    private FrameLayout root;
    private SurfaceView surface;
    private View shade;
    private LinearLayout bar, tracks, nextBox;
    private TextView title, sub, posT, durT, state, cues, nextText;
    private Ui.Bar prog;
    private ExoPlayer player;
    private final Handler h = new Handler(Looper.getMainLooper());
    private String kind, seriesId, poster;
    private String[] ids, titles, subs, exts;
    private int idx;
    private boolean resume;
    private long seekTarget = -1;
    private int nextCount;
    private final Runnable hide = () -> { if (player != null && player.isPlaying()) { fade(bar, false); fade(shade, false); cues.animate().translationY(0).setDuration(250).start(); } };
    private final Runnable tick = new Runnable() {
        @Override public void run() { update(); h.postDelayed(this, 500); }
    };
    private final Runnable save = new Runnable() {
        @Override public void run() { saveResume(); h.postDelayed(this, 15_000); }
    };
    private final Runnable applySeek = () -> { if (player != null && seekTarget >= 0) { player.seekTo(seekTarget); seekTarget = -1; } };
    private final Runnable countdown = new Runnable() {
        @Override public void run() {
            if (nextCount <= 0) { playNext(); return; }
            nextText.setText("Næste afsnit om " + nextCount + " …   OK = nu · Tilbage = stop");
            nextCount--;
            h.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        px = new Px(this);
        kind = getIntent().getStringExtra("kind");
        seriesId = getIntent().getStringExtra("seriesId");
        poster = getIntent().getStringExtra("poster");
        ids = getIntent().getStringArrayExtra("ids");
        titles = getIntent().getStringArrayExtra("titles");
        subs = getIntent().getStringArrayExtra("subs");
        exts = getIntent().getStringArrayExtra("exts");
        idx = getIntent().getIntExtra("i", 0);
        resume = getIntent().getBooleanExtra("resume", false);
        build();
    }

    private void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        setContentView(root);
        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        cues = px.t(40, 600, 0xFFFFFFFF);
        cues.setSingleLine(false);
        cues.setGravity(Gravity.CENTER);
        cues.setShadowLayer(px.p(8), 0, px.p(2), 0xFF000000);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(px.p(1500), -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cl.bottomMargin = px.p(90);
        root.addView(cues, cl);
        shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xB3000000, 0x00000000}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, px.p(460), Gravity.BOTTOM));

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        title = px.t(42, 700, 0xFFFFFFFF);
        title.setLetterSpacing(-0.02f);
        bar.addView(title);
        sub = px.t(22.5f, 400, Theme.white(0.7f));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = px.p(8);
        bar.addView(sub, sl);
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        posT = px.t(21, 500, Theme.white(0.85f));
        r.addView(posT);
        prog = new Ui.Bar(this, Theme.white(0.22f), 0xFFFFFFFF);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, px.p(9), 1);
        pl.leftMargin = px.p(24); pl.rightMargin = px.p(24);
        r.addView(prog, pl);
        durT = px.t(21, 500, Theme.white(0.85f));
        r.addView(durT);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = px.p(30);
        bar.addView(r, rl);
        LinearLayout r2 = new LinearLayout(this);
        state = px.t(20, 500, 0xFFFFFFFF);
        r2.addView(state, new LinearLayout.LayoutParams(0, -2, 1));
        TextView hint = px.t(20, 400, Theme.white(0.55f));
        hint.setText(Vod.SERIES.equals(kind) ? "OK pause    ◂▸ spol    ▴ lyd og tekster    ▾ næste afsnit" : "OK pause    ◂▸ spol    ▴ lyd og tekster");
        r2.addView(hint);
        LinearLayout.LayoutParams r2l = new LinearLayout.LayoutParams(-1, -2);
        r2l.topMargin = px.p(18);
        bar.addView(r2, r2l);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bl.leftMargin = px.p(96); bl.rightMargin = px.p(96); bl.bottomMargin = px.p(64);
        root.addView(bar, bl);

        nextBox = new LinearLayout(this);
        nextBox.setBackground(Ui.round(Ui.GLASS, px.p(24)));
        nextBox.setPadding(px.p(30), px.p(22), px.p(30), px.p(22));
        nextText = px.t(24, 600, 0xFFFFFFFF);
        nextBox.addView(nextText);
        nextBox.setVisibility(View.GONE);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        nl.topMargin = px.p(64); nl.rightMargin = px.p(96);
        root.addView(nextBox, nl);

        tracks = new LinearLayout(this);
        tracks.setOrientation(LinearLayout.VERTICAL);
        tracks.setBackground(Ui.round(Ui.GLASS, px.p(30)));
        tracks.setPadding(px.p(24), px.p(24), px.p(24), px.p(24));
        tracks.setVisibility(View.GONE);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(px.p(620), -2, Gravity.END | Gravity.CENTER_VERTICAL);
        tl.rightMargin = px.p(64);
        root.addView(tracks, tl);
    }

    @Override protected void onResume() {
        super.onResume();
        player = Play.create(this);
        player.setVideoSurfaceView(surface);
        player.addListener(new Player.Listener() {
            @Override public void onVideoSizeChanged(VideoSize v) { fit(v); }
            @Override public void onCues(CueGroup g) {
                StringBuilder s = new StringBuilder();
                for (Cue c : g.cues) if (c.text != null) { if (s.length() > 0) s.append('\n'); s.append(c.text); }
                cues.setText(s);
            }
            @Override public void onPlaybackStateChanged(int st) {
                if (st == Player.STATE_ENDED) ended();
                update();
            }
            @Override public void onIsPlayingChanged(boolean p) { update(); if (!p) show(0); else show(3_500); }
            @Override public void onPlayerError(PlaybackException e) {
                state.setText("Kan ikke afspille: " + e.getErrorCodeName());
                show(0);
            }
        });
        start(idx, resume);
        h.post(tick);
        h.postDelayed(save, 15_000);
    }

    @Override protected void onPause() {
        super.onPause();
        saveResume();
        h.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
    }

    private String key(int i) { return (Vod.MOVIE.equals(kind) ? "m:" : "e:") + ids[i]; }

    private void start(int i, boolean useResume) {
        idx = i;
        Cred cr = Cred.load(this);
        Xtream x = new Xtream(cr);
        String url = Vod.MOVIE.equals(kind) ? x.vodUrl(Integer.parseInt(ids[i]), exts[i]) : x.episodeUrl(ids[i], exts[i]);
        player.setMediaItem(MediaItem.fromUri(url));
        Vod.Resume r = useResume ? Vod.resume(this, key(i)) : null;
        if (r != null && r.pos > 60_000) player.seekTo(r.pos);
        player.prepare();
        player.setPlayWhenReady(true);
        title.setText(titles[i]);
        sub.setText(subs != null && subs[i] != null ? subs[i] : "");
        cues.setText("");
        nextBox.setVisibility(View.GONE);
        show(5_000);
    }

    private void saveResume() {
        if (player == null || ids == null) return;
        long pos = player.getCurrentPosition(), dur = player.getDuration();
        if (dur <= 0 || pos < 30_000) return;
        Vod.Resume r = new Vod.Resume();
        r.key = key(idx); r.kind = kind; r.id = ids[idx]; r.title = titles[idx]; r.sub = subs != null ? subs[idx] : "";
        r.poster = poster; r.ext = exts[idx]; r.seriesId = seriesId; r.pos = pos; r.dur = dur;
        Vod.saveResume(this, r);
    }

    private void ended() {
        if (player != null) { Vod.Resume r = new Vod.Resume(); r.key = key(idx); r.kind = kind; r.id = ids[idx]; r.pos = 1; r.dur = 1; Vod.saveResume(this, r); }
        if (Vod.SERIES.equals(kind) && idx < ids.length - 1) {
            nextBox.setVisibility(View.VISIBLE);
            nextCount = 8;
            h.post(countdown);
        } else finish();
    }

    private void playNext() {
        h.removeCallbacks(countdown);
        nextBox.setVisibility(View.GONE);
        if (idx < ids.length - 1) { saveResume(); start(idx + 1, true); }
    }

    private void fit(VideoSize v) {
        if (v.width == 0 || v.height == 0) return;
        float ar = v.width * v.pixelWidthHeightRatio / v.height;
        int W = root.getWidth(), H = root.getHeight();
        int w = W, hh = Math.round(W / ar);
        if (hh > H) { hh = H; w = Math.round(H * ar); }
        surface.setLayoutParams(new FrameLayout.LayoutParams(w, hh, Gravity.CENTER));
    }

    private static String t(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600 ? String.format(Fmt.DA, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format(Fmt.DA, "%d:%02d", s / 60, s % 60);
    }

    private void update() {
        if (player == null) return;
        long dur = player.getDuration(), pos = seekTarget >= 0 ? seekTarget : player.getCurrentPosition();
        posT.setText(t(pos));
        durT.setText(dur > 0 ? "-" + t(dur - pos) : "");
        prog.set(dur > 0 ? pos / (float) dur : 0);
        int st = player.getPlaybackState();
        state.setText(st == Player.STATE_BUFFERING ? "Indlæser …" : !player.getPlayWhenReady() ? "❚❚  Pause" : seekTarget >= 0 ? "Spoler …" : "");
    }

    private void show(long ms) {
        fade(bar, true); fade(shade, true);
        cues.animate().translationY(-px.p(230)).setDuration(180).start();
        h.removeCallbacks(hide);
        if (ms > 0) h.postDelayed(hide, ms);
    }

    private void fade(View v, boolean s) {
        if (s) { if (v.getVisibility() != View.VISIBLE) { v.setAlpha(0f); v.setVisibility(View.VISIBLE); } v.animate().alpha(1f).setDuration(180).start(); }
        else v.animate().alpha(0f).setDuration(250).withEndAction(() -> v.setVisibility(View.GONE)).start();
    }

    private void seek(long delta) {
        if (player == null) return;
        long base = seekTarget >= 0 ? seekTarget : player.getCurrentPosition();
        long dur = player.getDuration();
        seekTarget = Math.max(0, dur > 0 ? Math.min(dur - 2000, base + delta) : base + delta);
        update();
        show(3_000);
        h.removeCallbacks(applySeek);
        h.postDelayed(applySeek, 450);
    }

    // ---- lyd og undertekster ----
    private final List<Runnable> trackActions = new ArrayList<>();
    private int trackSel;

    private void openTracks() {
        trackActions.clear();
        tracks.removeAllViews();
        TextView hd = px.t(27, 700, 0xFFFFFFFF);
        hd.setText("Lyd og undertekster");
        hd.setPadding(px.p(12), 0, 0, px.p(12));
        tracks.addView(hd);
        Tracks tr = player.getCurrentTracks();
        addSection("Lyd", tr, C.TRACK_TYPE_AUDIO, false);
        addSection("Undertekster", tr, C.TRACK_TYPE_TEXT, true);
        trackSel = 0;
        tracks.setVisibility(View.VISIBLE);
        renderTrackSel();
    }

    private void addSection(String name, Tracks tr, final int type, boolean offOption) {
        TextView s = px.t(18, 600, Theme.white(0.5f));
        s.setLetterSpacing(0.08f);
        s.setText(name.toUpperCase(Fmt.DA));
        s.setPadding(px.p(12), px.p(14), 0, px.p(6));
        tracks.addView(s);
        boolean anySelected = false;
        for (Tracks.Group g : tr.getGroups()) if (g.getType() == type && g.isSelected()) anySelected = true;
        if (offOption) {
            final boolean on = !anySelected;
            addTrackRow((on ? "✓  " : "     ") + "Fra", () -> player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()));
        }
        for (final Tracks.Group g : tr.getGroups()) {
            if (g.getType() != type) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                final int ti = i;
                Format f = g.getTrackFormat(i);
                String lang = f.language != null && !f.language.equals("und") ? new java.util.Locale(f.language).getDisplayLanguage(Fmt.DA) : "";
                String label = (f.label != null ? f.label : lang.isEmpty() ? (g.length > 1 ? "Spor " + (i + 1) : "Standard") : Ui.cap(lang))
                        + (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0 ? "  ·  " + (f.channelCount >= 6 ? "5.1" : f.channelCount == 2 ? "stereo" : f.channelCount + " kanaler") : "");
                boolean sel = g.isTrackSelected(i);
                addTrackRow((sel ? "✓  " : "     ") + label, () -> player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), ti)).build()));
            }
        }
    }

    private void addTrackRow(String text, Runnable action) {
        TextView t = px.t(22.5f, 500, 0xFFFFFFFF);
        t.setText(text);
        t.setPadding(px.p(16), px.p(12), px.p(16), px.p(12));
        tracks.addView(t);
        trackActions.add(action);
    }

    private void renderTrackSel() {
        int n = 0;
        for (int i = 0; i < tracks.getChildCount(); i++) {
            View v = tracks.getChildAt(i);
            if (i == 0 || !(v instanceof TextView) || ((TextView) v).getLetterSpacing() > 0.05f) continue;
            boolean s = n == trackSel;
            v.setBackground(s ? Ui.round(0xFFFFFFFF, px.p(14)) : null);
            ((TextView) v).setTextColor(s ? 0xFF000000 : 0xFFFFFFFF);
            n++;
        }
    }

    // ---- taster ----
    @Override public boolean onKeyDown(int code, KeyEvent e) {
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (tracks.getVisibility() == View.VISIBLE) {
            if (code == KeyEvent.KEYCODE_DPAD_UP && trackSel > 0) { trackSel--; renderTrackSel(); }
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN && trackSel < trackActions.size() - 1) { trackSel++; renderTrackSel(); }
            else if (ok && trackSel < trackActions.size()) { trackActions.get(trackSel).run(); tracks.setVisibility(View.GONE); }
            else if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_DPAD_LEFT) tracks.setVisibility(View.GONE);
            return true;
        }
        if (nextBox.getVisibility() == View.VISIBLE) {
            if (ok) { playNext(); return true; }
            if (code == KeyEvent.KEYCODE_BACK) { h.removeCallbacks(countdown); finish(); return true; }
        }
        long step = e.getRepeatCount() > 20 ? 120_000 : e.getRepeatCount() > 6 ? 60_000 : 0;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_MEDIA_REWIND: seek(-(step > 0 ? step : 10_000)); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: seek(step > 0 ? step : 30_000); return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (bar.getVisibility() == View.VISIBLE && bar.getAlpha() > 0.5f) openTracks(); else show(4_000);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (Vod.SERIES.equals(kind) && idx < ids.length - 1 && bar.getVisibility() == View.VISIBLE) { saveResume(); start(idx + 1, true); }
                else show(4_000);
                return true;
            case KeyEvent.KEYCODE_MENU: case KeyEvent.KEYCODE_CAPTIONS: openTracks(); return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_MEDIA_PAUSE: case KeyEvent.KEYCODE_MEDIA_PLAY:
                player.setPlayWhenReady(!player.getPlayWhenReady()); return true;
            case KeyEvent.KEYCODE_BACK:
                if (bar.getVisibility() == View.VISIBLE && player.isPlaying()) { h.removeCallbacks(hide); hide.run(); return true; }
                break;
        }
        if (ok) { if (e.getRepeatCount() == 0) player.setPlayWhenReady(!player.getPlayWhenReady()); return true; }
        return super.onKeyDown(code, e);
    }
}
