package dk.hjem.live;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Henter kanallogoer/plakater i baggrunden med et lille hukommelses-cache. */
final class Img {
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>((int) Math.min(48L << 20, Runtime.getRuntime().maxMemory() / 10)) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private static final ExecutorService pool = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void load(final ImageView v, final String url, final int maxW) {
        v.setTag(url);
        if (url == null || url.isEmpty()) { v.setImageDrawable(null); return; }
        Bitmap b = cache.get(url);
        if (b != null) { v.setImageBitmap(b); return; }
        v.setImageDrawable(null);
        pool.execute(() -> {
            Bitmap bm = null;
            try {
                byte[] d = Http.bytes(url);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(d, 0, d.length, o);
                int s = 1;
                while (o.outWidth / (s * 2) >= maxW) s *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = s;
                if (maxW >= 180) o.inPreferredConfig = Bitmap.Config.RGB_565; // plakater/baggrunde: halv hukommelse (logoer beholder gennemsigtighed)
                bm = BitmapFactory.decodeByteArray(d, 0, d.length, o);
            } catch (Throwable ignored) { }
            final Bitmap fb = bm;
            if (fb != null) cache.put(url, fb);
            MAIN.post(() -> { if (url.equals(v.getTag()) && fb != null) v.setImageBitmap(fb); });
        });
    }
}
