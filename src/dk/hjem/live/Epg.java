package dk.hjem.live;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Nu/næste pr. kanal (Xtream short EPG), hentet efter behov og gemt i hukommelsen. */
final class Epg {
    private static final Map<Integer, List<Xtream.Prog>> cache = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> fetched = new ConcurrentHashMap<>();
    private static final Set<Integer> inflight = java.util.Collections.synchronizedSet(new HashSet<>());
    private static final ExecutorService pool = Executors.newFixedThreadPool(4);

    static List<Xtream.Prog> get(int id) {
        List<Xtream.Prog> l = cache.get(id);
        return l == null ? new ArrayList<>() : l;
    }

    static Xtream.Prog now(int id) {
        long t = System.currentTimeMillis();
        for (Xtream.Prog p : get(id)) if (p.now(t)) return p;
        return null;
    }

    static Xtream.Prog next(int id) {
        long t = System.currentTimeMillis();
        for (Xtream.Prog p : get(id)) if (p.start >= t) return p;
        return null;
    }

    /** Henter i baggrunden hvis data mangler, er gamle, eller det aktuelle program er slut. */
    static void want(Context c, final int id) { want(c, id, null); }

    static void want(Context c, final int id, final java.util.concurrent.atomic.AtomicInteger pending) {
        Long f = fetched.get(id);
        long t = System.currentTimeMillis();
        Xtream.Prog n = now(id);
        boolean stale = f == null || t - f > 30 * 60_000L || (n == null && t - f > 2 * 60_000L);
        if (!stale || !inflight.add(id)) { if (pending != null) pending.decrementAndGet(); return; }
        final Cred cr = Cred.load(c);
        pool.execute(() -> {
            try {
                List<Xtream.Prog> l = new Xtream(cr).shortEpg(id, 12);
                cache.put(id, l);
                fetched.put(id, System.currentTimeMillis());
                Catalog.changed();
            } finally {
                inflight.remove(id);
                if (pending != null) pending.decrementAndGet();
            }
        });
    }
}
