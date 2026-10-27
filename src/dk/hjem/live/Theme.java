package dk.hjem.live;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.SparseArray;
import android.util.TypedValue;
import android.widget.TextView;

/** Designets skrift (Onest, variabel vægt) og tekst-hjælpere. */
final class Theme {
    static final int BG = 0xFF0A0A0C;
    private static final SparseArray<Typeface> fonts = new SparseArray<>();

    static Typeface font(Context c, int weight) {
        Typeface t = fonts.get(weight);
        if (t == null) {
            t = new Typeface.Builder(c.getAssets(), "fonts/Onest.ttf")
                    .setFontVariationSettings("'wght' " + weight).build();
            fonts.put(weight, t);
        }
        return t;
    }

    /** Hvid tekst med given gennemsigtighed (0–1), som designets rgba(255,255,255,x). */
    static int white(float alpha) { return (Math.round(alpha * 255) << 24) | 0xFFFFFF; }

    static TextView text(Context c, float sp, int weight, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(font(c, weight));
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setFontFeatureSettings("tnum"); // faste tal-bredder, så uret ikke hopper
        return t;
    }

    static GradientDrawable rounded(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusDp * c.getResources().getDisplayMetrics().density);
        return g;
    }

    /** Diagonal gradient som designets linear-gradient(140–150deg, a, b). */
    static GradientDrawable diagonal(int a, int b, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        g.setCornerRadius(radiusDp * c.getResources().getDisplayMetrics().density);
        return g;
    }
}
