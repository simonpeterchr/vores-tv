package dk.hjem.live;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Finder og rangerer de kanaler, der viser en kamp: sikkerhed, sprog (dansk først) og kvalitet. */
final class Match {
    static final int CERTAIN = 3, VERY_LIKELY = 2, LIKELY = 1;

    static final class Stream {
        Catalog.Ch ch;
        int confidence;
        String quality = "", country = "", why = "";
        int score() { return confidence * 1000 + lang() * 100 + qual(); }
        int lang() { return "DK".equals(country) ? 3 : "UK".equals(country) || "EN".equals(country) ? 2 : 1; }
        int qual() { switch (quality) { case "4K": return 40; case "FHD": return 30; case "RAW": return 28; case "HD": return 20; case "SD": return 5; default: return 10; } }
        String confidenceText() { return confidence == CERTAIN ? "Sikker" : confidence == VERY_LIKELY ? "Meget sandsynlig" : "Sandsynlig"; }
    }

    private static final String[] DROP = {"fc", "f.c.", "afc", "cf", "sv", "fodbold", "football", "club", "ac", "ssc", "as", "bk", "if", "fk", "sc", "vfb", "vfl", "tsg", "rcd", "cd", "ud", "real", "united", "city", "håndbold", "haandbold", "handball", "hb", "hk", "kif", "tsv", "hsg", "sg", "thw"};

    /** Navne et hold kan stå under i kanalnavne og programguide. */
    static List<String> aliases(Sports.Team t) {
        Set<String> a = new HashSet<>();
        for (String n : new String[]{t.name, t.shortName}) {
            if (n == null) continue;
            String x = Search.norm(n).replace(".", " ").replaceAll("\\s+", " ").trim();
            a.add(x);
            StringBuilder core = new StringBuilder();
            for (String w : x.split(" ")) {
                boolean drop = false;
                for (String d : DROP) if (w.equals(d.replace(".", ""))) drop = true;
                if (!drop) core.append(core.length() > 0 ? " " : "").append(w);
            }
            if (core.length() >= 4) a.add(core.toString());
        }
        String n = Search.norm(t.name);
        if (n.contains("københavn") || n.contains("kobenhavn")) { a.add("fck"); a.add("københavn"); }
        if (n.contains("barcelona")) { a.add("barca"); a.add("barça"); }
        if (n.contains("hamburg")) { a.add("hsv"); a.add("hamburger"); }
        if (n.contains("manchester united")) a.add("man utd");
        if (n.contains("manchester city")) a.add("man city");
        if (n.contains("tottenham")) a.add("spurs");
        if (n.contains("bayern")) a.add("bayern");
        if (n.contains("midtjylland")) a.add("fcm");
        if (n.contains("brøndby") || n.contains("brondby")) a.add("brøndby");
        List<String> out = new ArrayList<>();
        for (String s : a) if (s.length() >= 3) out.add(s);
        return out;
    }

    private static String clean(String text) {
        return " " + Search.norm(text).replace(".", " ").replaceAll("[^\\p{L}0-9]+", " ") + " ";
    }

    private static boolean mentions(String text, List<String> al) { return has(clean(text), al); }

    private static boolean has(String cleaned, List<String> al) {
        for (String a : al) if (cleaned.contains(" " + a + " ")) return true;
        return false;
    }

    // Normaliserede kanalnavne, beregnet én gang pr. kanalliste (der kan være titusinder)
    private static List<Catalog.Ch> normFor;
    private static String[] norm;

    private static synchronized String[] names() {
        List<Catalog.Ch> l = Catalog.channels;
        if (l != normFor) {
            String[] n = new String[l.size()];
            for (int i = 0; i < n.length; i++) { Catalog.Ch ch = l.get(i); n[i] = clean(ch.raw != null ? ch.raw : ch.name); }
            norm = n; normFor = l;
        }
        return norm;
    }

    static String quality(String raw) {
        String r = raw.toUpperCase(Locale.ROOT);
        if (r.matches(".*(4K|UHD|3840|2160).*")) return "4K";
        if (r.matches(".*(FHD|1080).*")) return "FHD";
        if (r.contains("RAW")) return "RAW";
        if (r.matches(".*\\bHD\\b.*") || r.contains("ᴴᴰ")) return "HD";
        if (r.matches(".*\\bSD\\b.*")) return "SD";
        return "";
    }

    private static final String SPORTY = "(?i).*\\b(sport|sports|soccer|football|fodbold|eurosport|ppv|espn|dazn|premier|liga|champions|bundesliga|laliga|serie a|ligue|viaplay|tv3\\+|tv 2 sport|canal\\+|sky|bein|arena|f1|darts|tennis|golf|ufc|handball|håndbold|motorsport)\\b.*";

    private static final java.util.Set<String> GENERIC = new java.util.HashSet<>(java.util.Arrays.asList(
            "grand", "prix", "open", "championship", "championships", "masters", "cup", "tour", "night", "fight", "event", "round", "series", "the", "and", "world"));

    /** Sportsord for en sportsgren uden hold. */
    private static List<String> sportWords(String sport) {
        switch (sport) {
            case Sports.F1: { List<String> l = Search.synonyms("formel 1"); l.add("formula one"); return l; }
            case Sports.DARTS: return Search.synonyms("dart");
            case Sports.TENNIS: return Search.synonyms("tennis");
            case Sports.GOLF: return Search.synonyms("golf");
            case Sports.MMA: return new ArrayList<>(java.util.Arrays.asList("ufc", "mma"));
            case Sports.HANDBALL: return Search.synonyms("håndbold");
            default: return new ArrayList<>();
        }
    }

    /** Begivenheder uden to hold: F1-session, dartaften, turnering. */
    private static List<Stream> eventStreams(Context c, Sports.Game g) {
        List<String> sw = sportWords(g.sport), tw = new ArrayList<>();
        for (String w : clean(g.title).trim().split(" ")) if (w.length() >= 4 && !GENERIC.contains(w) && !w.matches("\\d+")) tw.add(w);
        List<Stream> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        String[] nn = names();
        List<Catalog.Ch> chs = normFor;
        for (int i = 0; i < nn.length; i++) { // kanalnavn med begivenheden (PPV) – mindst to særlige ord, eller sportsord + ét
            int hits = 0;
            for (String w : tw) if (nn[i].contains(" " + w + " ")) hits++;
            if (hits >= 2 || (hits >= 1 && has(nn[i], sw))) add(out, seen, chs.get(i), CERTAIN, "Begivenheden i kanalnavnet");
        }
        long k0 = g.allDay ? g.start : g.start - 20 * 60_000L, k1 = g.allDay ? g.start + 24 * 3600_000L : g.start + 30 * 60_000L;
        int wanted = 0;
        for (Catalog.Ch ch : sportsPool(c)) {
            if (seen.contains(ch.id)) continue;
            if (g.start - System.currentTimeMillis() < 26 * 3600_000L && wanted++ < 120) Epg.want(c, ch.id);
            for (Xtream.Prog p : Epg.get(ch.id)) {
                if (p.stop < k0 || p.start > k1) continue;
                if (p.clean == null) p.clean = clean(p.title + " " + (p.desc == null ? "" : p.desc));
                boolean sport = has(p.clean, sw);
                int hits = 0;
                for (String w : tw) if (p.clean.contains(" " + w + " ")) hits++;
                if (sport && hits > 0) { add(out, seen, ch, VERY_LIKELY, "Står i programguiden"); break; }
                if (sport) { add(out, seen, ch, LIKELY, Sports.sportName(g.sport) + " i programguiden"); break; }
            }
        }
        Collections.sort(out, (x, y) -> y.score() - x.score());
        return out;
    }

    private static List<Catalog.Ch> sportsPool(Context c) {
        List<Catalog.Ch> pool = new ArrayList<>(Catalog.favorites(c));
        for (String grp : Catalog.shownGroups(c)) {
            Catalog.Cat cat = Catalog.cats.get(grp);
            for (Catalog.Ch ch : Catalog.inCategory(grp))
                if ((cat != null && cat.name.matches(SPORTY)) || ch.name.matches(SPORTY)) pool.add(ch);
        }
        return pool;
    }

    /** Alle streams for kampen, bedst først. Henter programguide for relevante kanaler i baggrunden. */
    static List<Stream> streams(Context c, Sports.Game g) {
        if (g.title != null) return eventStreams(c, g);
        List<String> home = aliases(g.home), away = aliases(g.away);
        String league = g.leagueName == null ? "" : Search.norm(g.leagueName);
        List<Stream> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        int wanted = 0;
        String[] nn = names();
        List<Catalog.Ch> chs = normFor;
        for (int i = 0; i < nn.length; i++)
            if (has(nn[i], home) && has(nn[i], away)) add(out, seen, chs.get(i), CERTAIN, "Begge hold i kanalnavnet");
        // programguide: kun sportskanaler i jeres grupper og favoritter (ellers er der for mange)
        long k0 = g.start - 20 * 60_000L, k1 = g.start + 30 * 60_000L;
        for (Catalog.Ch ch : sportsPool(c)) {
            if (seen.contains(ch.id)) continue;
            if (g.start - System.currentTimeMillis() < 26 * 3600_000L && wanted++ < 120) Epg.want(c, ch.id);
            for (Xtream.Prog p : Epg.get(ch.id)) {
                if (p.stop < k0 || p.start > k1) continue;
                if (p.clean == null) p.clean = clean(p.title + " " + (p.desc == null ? "" : p.desc));
                boolean h = has(p.clean, home), a = has(p.clean, away);
                if (h && a) { add(out, seen, ch, VERY_LIKELY, "Kampen står i programguiden"); break; }
                if ((h || a) && !league.isEmpty() && p.clean.contains(" " + league.split(" ")[0] + " ")) { add(out, seen, ch, LIKELY, "Holdet og ligaen i programguiden"); break; }
            }
        }
        Collections.sort(out, (x, y) -> y.score() - x.score());
        return out;
    }

    private static void add(List<Stream> out, Set<Integer> seen, Catalog.Ch ch, int conf, String why) {
        if (!seen.add(ch.id)) return;
        Stream s = new Stream();
        s.ch = ch; s.confidence = conf; s.why = why;
        String raw = ch.raw != null ? ch.raw : ch.name;
        s.quality = quality(raw);
        String p = Names.prefix(raw);
        Catalog.Cat k = Catalog.cats.get(ch.cat);
        if (p.isEmpty() && k != null) p = k.prefix;
        s.country = p;
        out.add(s);
    }
}
