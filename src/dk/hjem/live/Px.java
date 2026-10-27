package dk.hjem.live;

import android.content.Context;
import android.util.TypedValue;
import android.widget.TextView;

/** Designet er tegnet i 1920×1080 px; Px omregner designets tal til skærmens pixels. */
final class Px {
    final Context c;
    final float s;

    Px(Context c) {
        this.c = c;
        this.s = c.getResources().getDisplayMetrics().widthPixels / 1920f;
    }

    int p(float v) { return Math.round(v * s); }

    TextView t(float size, int weight, int color) {
        TextView t = Theme.text(c, 1, weight, color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * s);
        return t;
    }
}
