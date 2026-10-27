package dk.hjem.live;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Opdatering fra GitHub Releases: tjekker nyeste version og installerer den med Androids egen bekræftelse.
 * Data (login, favoritter, fortsæt) bevares, fordi opdateringen er signeret med samme nøgle.
 */
final class Updater {
    static final String API = "https://api.github.com/repos/simonpeterchr/vores-tv/releases/latest";
    static volatile String latestVersion, apkUrl;
    static volatile String state; // null | "henter" | "installerer" | fejltekst
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static String current(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception e) { return "0"; }
    }

    static boolean available(Context c) { return latestVersion != null && apkUrl != null && newer(latestVersion, current(c)); }

    /** "0.21" > "0.20", "1.0" > "0.99" */
    static boolean newer(String a, String b) {
        String[] x = a.replaceAll("[^0-9.]", "").split("\\."), y = b.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0, q = i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    static void check(Context ctx) {
        final Context c = ctx.getApplicationContext();
        android.content.SharedPreferences p = c.getSharedPreferences("update", Context.MODE_PRIVATE);
        long last = p.getLong("checked", 0);
        latestVersion = p.getString("latest", null);
        apkUrl = p.getString("url", null);
        if (System.currentTimeMillis() - last < 6 * 3600_000L) return;
        new Thread(() -> {
            try {
                JSONObject o = new JSONObject(Http.text(API));
                String tag = o.optString("tag_name", "");
                JSONArray as = o.optJSONArray("assets");
                String url = null;
                if (as != null) for (int i = 0; i < as.length(); i++) {
                    JSONObject a = as.getJSONObject(i);
                    if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); break; }
                }
                latestVersion = tag.replaceFirst("^v", "");
                apkUrl = url;
                p.edit().putLong("checked", System.currentTimeMillis()).putString("latest", latestVersion).putString("url", url).apply();
                Catalog.changed();
            } catch (Exception ignored) { }
        }, "update-check").start();
    }

    /** Henter og installerer. Første gang sendes brugeren til "Tillad fra denne kilde". */
    static void install(Activity a) {
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
            return;
        }
        if ("henter".equals(state) || "installerer".equals(state)) return;
        state = "henter";
        Catalog.changed();
        final Context c = a.getApplicationContext();
        final String url = apkUrl;
        new Thread(() -> {
            try {
                PackageInstaller pi = c.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                sp.setAppPackageName(c.getPackageName());
                int id = pi.createSession(sp);
                try (PackageInstaller.Session s = pi.openSession(id)) {
                    HttpURLConnection con = null;
                    URL u = new URL(url);
                    for (int hop = 0; hop < 6; hop++) { // GitHub sender videre til sit CDN
                        con = (HttpURLConnection) u.openConnection();
                        con.setInstanceFollowRedirects(false);
                        con.setRequestProperty("User-Agent", Http.UA);
                        int code = con.getResponseCode();
                        if (code >= 300 && code < 400) { u = new URL(u, con.getHeaderField("Location")); con.disconnect(); continue; }
                        if (code != 200) throw new java.io.IOException("HTTP " + code);
                        break;
                    }
                    try (InputStream in = con.getInputStream(); OutputStream out = s.openWrite("VoresTV.apk", 0, -1)) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        s.fsync(out);
                    }
                    state = "installerer";
                    Catalog.changed();
                    Intent cb = new Intent(c, UpdateReceiver.class);
                    PendingIntent pend = PendingIntent.getBroadcast(c, id, cb, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                    s.commit(pend.getIntentSender());
                }
            } catch (Exception e) {
                state = "Opdateringen kunne ikke hentes. Prøv igen senere.";
                Catalog.changed();
            }
        }, "update-install").start();
    }

    /** Svar fra Android: vis bekræftelsen, eller fortæl hvis det gik galt. */
    public static class UpdateReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent in) {
            int st = in.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = in.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(confirm); }
            } else if (st != PackageInstaller.STATUS_SUCCESS) {
                state = st == PackageInstaller.STATUS_FAILURE_ABORTED ? null : "Opdateringen blev ikke installeret.";
                MAIN.post(Catalog::changed);
            }
        }
    }
}
