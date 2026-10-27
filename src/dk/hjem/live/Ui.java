package dk.hjem.live;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Fælles byggeklodser fra designet (1280-artboards ×1.5 = 1920 px). */
final class Ui {
    static final int BG = 0xFF0B0B0D, RED = 0xFFE5484D, GLASS = 0x9E1E1E22;

    static GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r);
        return g;
    }

    static TextView badge(Context c, Px px, String text, boolean live) {
        TextView t = px.t(16.5f, 650, 0xFFFFFFFF);
        t.setText(text);
        t.setPadding(px.p(12), px.p(6), px.p(12), px.p(6));
        GradientDrawable g = round(live ? RED : 0, px.p(7.5f));
        if (!live) g.setStroke(px.p(1.5f), Theme.white(0.4f));
        t.setBackground(g);
        return t;
    }

    /** Fremdriftsbjælke: grå spor, hvid fyld. */
    static final class Bar extends View {
        float p;
        final int track, fill;
        Bar(Context c, int track, int fill) { super(c); this.track = track; this.fill = fill; }
        void set(float v) { p = Math.max(0, Math.min(1, v)); invalidate(); }
        @Override protected void onDraw(android.graphics.Canvas cv) {
            android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            float h = getHeight(), r = h / 2;
            pt.setColor(track);
            cv.drawRoundRect(0, 0, getWidth(), h, r, r, pt);
            pt.setColor(fill);
            if (p > 0) cv.drawRoundRect(0, 0, Math.max(h, getWidth() * p), h, r, r, pt);
        }
    }

    /** Top-bjælke: logo, pille-menu (aktivt punkt hvidt) og ur. */
    static final class TopBar {
        LinearLayout root;
        TextView clock;
        TextView[] items;
    }

    static TopBar topBar(Context c, Px px, String[] names, int active) {
        TopBar b = new TopBar();
        b.root = new LinearLayout(c);
        b.root.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = px.t(33, 750, 0xFFFFFFFF);
        logo.setText("vores tv");
        logo.setLetterSpacing(-0.03f);
        b.root.addView(logo, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout pill = new LinearLayout(c);
        pill.setPadding(px.p(7.5f), px.p(7.5f), px.p(7.5f), px.p(7.5f));
        pill.setBackground(round(0xBF303036, 999));
        b.items = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            final TextView t = px.t(22.5f, 500, Theme.white(0.75f));
            t.setText(names[i]);
            t.setPadding(px.p(27), px.p(12), px.p(27), px.p(12));
            t.setFocusable(true);
            final boolean on = i == active;
            style(t, on, false);
            t.setOnFocusChangeListener((v, has) -> style(t, on, has));
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) l.leftMargin = px.p(3);
            pill.addView(t, l);
            b.items[i] = t;
        }
        b.root.addView(pill);
        b.clock = px.t(24, 500, Theme.white(0.85f));
        b.clock.setGravity(Gravity.END);
        b.root.addView(b.clock, new LinearLayout.LayoutParams(0, -2, 1));
        return b;
    }

    private static void style(TextView t, boolean active, boolean focus) {
        t.setBackground(active || focus ? round(focus ? 0xFFFFFFFF : Theme.white(0.22f), 999) : null);
        t.setTextColor(focus ? 0xFF000000 : active ? 0xFFFFFFFF : Theme.white(0.75f));
    }

    static String cap(String s) { return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    /** Pille-knap: glas, hvid ved fokus. */
    static TextView pill(Context c, Px px, String text) {
        final TextView t = px.t(22.5f, 600, 0xFFFFFFFF);
        t.setText(text);
        t.setPadding(px.p(36), px.p(18), px.p(36), px.p(18));
        t.setFocusable(true);
        t.setBackground(round(Theme.white(0.14f), 999));
        t.setOnFocusChangeListener((v, has) -> {
            t.setBackground(round(has ? 0xFFFFFFFF : Theme.white(0.14f), 999));
            t.setTextColor(has ? 0xFF000000 : 0xFFFFFFFF);
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(160).start();
        });
        return t;
    }

    static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(w, h);
        l.leftMargin = x; l.topMargin = y;
        return l;
    }
}
