package dk.hjem.live;

import android.content.Context;

import java.net.InetAddress;
import java.net.URL;

/** Svarer udbyderen? Tjekkes jævnligt fra Hjem, så man ikke først opdager det ved afspilning. */
final class Health {
    static volatile String problem; // null = alt ok
    private static volatile long last;
    private static volatile boolean running;

    static void check(Context ctx, boolean force) {
        if (running || (!force && System.currentTimeMillis() - last < 55_000)) return;
        final Context c = ctx.getApplicationContext();
        final Cred cr = Cred.load(c);
        if (!cr.ok()) return;
        running = true;
        new Thread(() -> {
            String p;
            try {
                p = diagnose(cr);
            } finally { running = false; last = System.currentTimeMillis(); }
            String before = problem;
            problem = p;
            if (before != null && p == null) Catalog.load(c, true); // tilbage igen: hent kanaler på ny
            if (before == null ? p != null : !before.equals(p)) Catalog.changed();
        }, "health").start();
    }

    private static String diagnose(Cred cr) {
        String host;
        try { host = new URL(cr.server).getHost(); } catch (Exception e) { return null; }
        try { InetAddress.getByName(host); }
        catch (Exception e) {
            return internet() ? "Udbyderens adresse findes ikke længere. De har sandsynligvis fået en ny server-adresse."
                    : "Ingen internetforbindelse. Tjek netværket på tv'et.";
        }
        String err = new Xtream(cr).check();
        if (err == null) return null;
        return internet() ? err : "Ingen internetforbindelse. Tjek netværket på tv'et.";
    }

    private static boolean internet() {
        try { InetAddress.getByName("github.com"); return true; } catch (Exception e) { return false; }
    }
}
