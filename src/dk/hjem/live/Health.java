package dk.hjem.live;

import android.content.Context;

import java.net.InetAddress;
import java.net.URL;

/** Svarer udbyderen? Tjekkes jævnligt fra Hjem, så man ikke først opdager det ved afspilning. */
final class Health {
    static volatile String problem; // null = alt ok
    static volatile String switched; // besked når der er skiftet til en reserve-adresse
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
                if (p != null && internet()) { // hovedadressen svarer ikke: prøv udbyderens reserve-adresser
                    outer:
                    for (String alt : new java.util.ArrayList<>(cr.alts)) for (String cand : candidates(alt, cr.server)) {
                        String res = new Xtream(cr.withServer(cand)).check();
                        android.util.Log.i("VoresTV", "reserve " + cand + " -> " + (res == null ? "OK" : res));
                        if (res == null) {
                            String old = cr.server;
                            cr.alts.remove(alt);
                            if (!cr.alts.contains(old)) cr.alts.add(old);
                            cr.server = cand;
                            cr.save(c);
                            switched = "Skiftede til udbyderens reserve-adresse, fordi hovedadressen ikke svarede.";
                            p = null;
                            Catalog.load(c, true);
                            break outer;
                        }
                    }
                }
            } finally { running = false; last = System.currentTimeMillis(); }
            String before = problem;
            problem = p;
            if (before != null && p == null) Catalog.load(c, true); // tilbage igen: hent kanaler på ny
            if (before == null ? p != null : !before.equals(p)) Catalog.changed();
        }, "health").start();
    }

    /** Reserve-adressen som den er, og med hovedadressens port hvis den mangler (fx :8080). */
    static java.util.List<String> candidates(String alt, String main) {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add(alt);
        try {
            URL m = new URL(main), a = new URL(alt);
            if (a.getPort() < 0 && m.getPort() > 0) out.add(0, a.getProtocol() + "://" + a.getHost() + ":" + m.getPort());
        } catch (Exception ignored) { }
        return out;
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
