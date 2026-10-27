package dk.hjem.live;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;

/** Indholdet på Hjem: det I faktisk ser – live nu, sport nu og det nyeste på tværs af grupperne. */
final class Feed {
    private static boolean separator(Catalog.Ch c) { return c.name.matches("^[#=*~\\-_ ]{3,}.*"); }

    /** Favoritter; ellers senest sete kanaler; ellers de første kanaler i den første viste gruppe. */
    static List<Catalog.Ch> liveNow(Context c, int max) {
        List<Catalog.Ch> out = new ArrayList<>(Catalog.favorites(c));
        if (out.isEmpty()) for (Integer id : Catalog.history(c)) { Catalog.Ch ch = Catalog.byId.get(id); if (ch != null) out.add(ch); }
        if (out.isEmpty()) {
            List<String> g = Catalog.shownGroups(c);
            if (!g.isEmpty()) for (Catalog.Ch ch : Catalog.inCategory(g.get(0))) if (!separator(ch)) out.add(ch);
        }
        return out.size() > max ? out.subList(0, max) : out;
    }

    private static final String SPORT = "(?i).*\\b(sport|sports|soccer|football|fodbold|darts|eurosport|ppv|espn|dazn|premier|liga|champions|golf|tennis|handball|håndbold|f1|nfl|nhl|nba)\\b.*";

    /** Sport der kører nu eller senere i dag: PPV-kampe (tid i navnet) og sportskanalernes programguide. */
    static List<Search.Hit> sportNow(Context c, int max) {
        long now = System.currentTimeMillis(), until = endOfDay(), from = now - 3 * 3600_000L;
        List<Search.Hit> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int wanted = 0;
        for (String g : Catalog.shownGroups(c)) {
            Catalog.Cat k = Catalog.cats.get(g);
            boolean sportGroup = k != null && k.name.matches(SPORT);
            for (Catalog.Ch ch : Catalog.inCategory(g)) {
                if (separator(ch)) continue;
                Matcher m = Search.EVENT.matcher(ch.name);
                if (m.find()) { // PPV: kampen står i kanalnavnet
                    long t;
                    try { t = Search.EVENT_FMT.parse(m.group(1) + " " + m.group(2)).getTime(); } catch (Exception e) { continue; }
                    if (t + 2 * 3600_000L < now || t > until) continue; // slut (antaget 2 timer) eller ikke i dag
                    String name = ch.name.substring(0, m.start()).trim();
                    if (!seen.add(name.toLowerCase(Locale.ROOT))) continue;
                    Search.Hit h = new Search.Hit();
                    h.kind = "channel"; h.chId = ch.id; h.name = name; h.img = ch.logo; h.start = t; h.stop = t + 2 * 3600_000L;
                    h.sub = Search.when(t) + "  ·  " + (k != null ? k.name : "");
                    out.add(h);
                    continue;
                }
                if (!(sportGroup || ch.name.matches(SPORT))) continue;
                if (wanted++ < 40) Epg.want(c, ch.id);
                for (Xtream.Prog p : Epg.get(ch.id)) {
                    if (p.stop < now || p.start > until) continue;
                    String key = (p.title + "@" + p.start).toLowerCase(Locale.ROOT);
                    if (!seen.add(key)) continue;
                    Search.Hit h = new Search.Hit();
                    h.kind = "program"; h.chId = ch.id; h.name = p.title; h.img = ch.logo; h.start = p.start; h.stop = p.stop;
                    h.sub = ch.name + "  ·  " + Fmt.hm(p.start) + "–" + Fmt.hm(p.stop);
                    out.add(h);
                    break; // én udsendelse pr. kanal er nok på forsiden
                }
            }
        }
        // det der kører nu først, så det der starter snart
        Collections.sort(out, (a, b) -> {
            boolean an = a.start <= now, bn = b.start <= now;
            return an != bn ? (an ? -1 : 1) : Long.compare(a.start, b.start);
        });
        return out.size() > max ? out.subList(0, max) : out;
    }

    private static long endOfDay() {
        Calendar k = Calendar.getInstance();
        k.set(Calendar.HOUR_OF_DAY, 23); k.set(Calendar.MINUTE, 59);
        return k.getTimeInMillis();
    }

    private static int year(Vod.Item it) {
        try { return Integer.parseInt(it.year); } catch (Exception e) { return 0; }
    }

    private static double rating(Vod.Item it) {
        try { return Double.parseDouble(it.rating); } catch (Exception e) { return 0; }
    }

    /** Nyeste titler på tværs af de viste grupper (årstal, så tilføjet), uden dubletter. */
    static List<Vod.Item> newest(Context c, String kind, int max) {
        List<Vod.Item> all = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (String g : Vod.shownGroups(c, kind))
            for (Vod.Item it : Vod.inCategory(c, kind, g))
                if (it.poster != null && !it.poster.isEmpty() && names.add(it.name.toLowerCase(Locale.ROOT))) all.add(it);
        Collections.sort(all, (a, b) -> year(a) != year(b) ? year(b) - year(a) : Long.compare(b.added, a.added));
        return all.size() > max ? all.subList(0, max) : all;
    }

    /** Forsiden: en højt bedømt film fra i år eller sidste år; skifter én gang om dagen (ingen slider). */
    static Vod.Item hero(Context c) {
        List<Vod.Item> pool = new ArrayList<>();
        int y = Calendar.getInstance().get(Calendar.YEAR);
        for (Vod.Item it : newest(c, Vod.MOVIE, 200)) if (year(it) >= y - 1) pool.add(it);
        if (pool.isEmpty()) pool.addAll(newest(c, Vod.MOVIE, 20));
        if (pool.isEmpty()) return null;
        Collections.sort(pool, (a, b) -> Double.compare(rating(b), rating(a)));
        List<Vod.Item> top = pool.subList(0, Math.min(8, pool.size()));
        return top.get(Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % top.size());
    }
}
