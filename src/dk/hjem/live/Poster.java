package dk.hjem.live;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Plakat (2:3) eller afsnits-/fortsæt-kort (16:9) med løft ved fokus og evt. fremdrift. */
final class Poster {
    static LinearLayout card(Context c, Px px, int w, int h, String img, String title, String sub, float progress, Runnable onFocus) {
        final LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        final FrameLayout box = new FrameLayout(c);
        box.setBackground(Ui.round(0xFF1F1F24, px.p(15)));
        box.setClipToOutline(true);
        box.setFocusable(true);
        {
            TextView ph = px.t(22, 600, Theme.white(0.6f));
            ph.setSingleLine(false);
            ph.setMaxLines(3);
            ph.setGravity(Gravity.CENTER);
            ph.setPadding(px.p(14), 0, px.p(14), 0);
            ph.setText(title);
            box.addView(ph, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        }
        ImageView iv = new ImageView(c); // dækker titel-pladsholderen, når billedet er hentet
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Img.load(iv, img, w);
        box.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        if (progress > 0) {
            Ui.Bar bar = new Ui.Bar(c, Theme.white(0.3f), 0xFFFFFFFF);
            bar.set(progress);
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, px.p(6), Gravity.BOTTOM);
            bl.leftMargin = px.p(18); bl.rightMargin = px.p(18); bl.bottomMargin = px.p(16);
            box.addView(bar, bl);
        }
        root.addView(box, new LinearLayout.LayoutParams(w, h));
        final TextView t = px.t(19.5f, 600, 0xFFFFFFFF);
        t.setText(title);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(w, -2);
        tl.topMargin = px.p(14);
        root.addView(t, tl);
        final TextView s = px.t(17, 400, Theme.white(0.5f));
        s.setText(sub == null ? "" : sub);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(w, -2);
        sl.topMargin = px.p(4);
        root.addView(s, sl);
        box.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).translationZ(has ? px.p(30) : 0).setDuration(180).start();
            t.animate().translationY(has ? px.p(14) : 0).setDuration(180).start();
            s.animate().translationY(has ? px.p(14) : 0).setDuration(180).start();
            if (has && onFocus != null) onFocus.run();
        });
        return root;
    }

    static View box(LinearLayout card) { return card.getChildAt(0); }

    /** Live-kort (16:9): kanallogo på mørkt kort, nu-program og fremdrift. */
    static LinearLayout logoCard(Context c, Px px, int w, int h, String logo, String title, String sub, float progress, Runnable onFocus) {
        LinearLayout card = card(c, px, w, h, null, title, sub, progress, onFocus);
        FrameLayout box = (FrameLayout) card.getChildAt(0);
        box.setBackground(Ui.round(0xFF232328, px.p(15)));
        ImageView iv = (ImageView) box.getChildAt(1);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setPadding(w / 4, h / 4, w / 4, h / 3);
        Img.load(iv, logo, w / 2);
        box.getChildAt(0).setVisibility(View.GONE);
        return card;
    }
}
