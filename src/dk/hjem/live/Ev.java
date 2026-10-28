package dk.hjem.live;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Læser kampen og tidspunktet ud af et PPV-kanalnavn. Udbyderen bruger flere formater, fx
 *  "BETANO POKALEN FC ROSKILDE - AGF | Wed 28 Oct 19:55 CET (DK) | 8K EXCLUSIVE | DK: VIAPLAY PPV 12"
 *  "FC Roskilde vs AGF @ Oct 28 7:55 PM :Viaplay DK 12"
 *  "Freiburg vs. Wolfsburg | DFB-Pokal | 2026-10-28 | 17:00 (GMT) | 8K EXCLUSIVE | CA: DAZN PPV 35"
 *  "Serie A: Sassuolo-Lazio @ 2026-10-27 18:15:00 :TV2PLAY DK 02"
 */
final class Ev {
    final String title;  // det før tidspunktet, uden løbenummer
    final String extra;  // fx "DFB-Pokal" når den står i navnet
    final long start;
    Ev(String t, String x, long s) { title = t; extra = x; start = s; }

    private static final String MON = "(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*";
    private static final Pattern A = Pattern.compile("\\|\\s*(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)[a-z]*\\s+(\\d{1,2})\\s+" + MON + "\\s+(\\d{1,2}):(\\d{2})\\s*(CET|CEST|GMT|BST|UTC|EDT|EST|ET)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern B = Pattern.compile("@\\s*" + MON + "\\s+(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})\\s*(AM|PM)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern C = Pattern.compile("\\|\\s*(\\d{4})-(\\d{2})-(\\d{2})\\s*\\|\\s*(\\d{1,2}):(\\d{2})\\s*\\((GMT|UTC|CET|ET)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern C2 = Pattern.compile("\\|\\s*(\\d{2})-(\\d{2})-(\\d{4})\\s*\\|\\s*(\\d{1,2}):(\\d{2})\\s*\\((GMT|UTC|CET|ET)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern D = Pattern.compile("[@|]\\s*(\\d{4})-(\\d{2})-(\\d{2})[ T]+(\\d{1,2}):(\\d{2})");
    private static final String MONTHS = "janfebmaraprmayjunjulaugsepoctnovdec";

    private static int month(String m) { return MONTHS.indexOf(m.substring(0, 3).toLowerCase(Locale.ROOT)) / 3; }

    private static TimeZone zone(String tz, String prefix) {
        if (tz != null) {
            switch (tz.toUpperCase(Locale.ROOT)) {
                case "GMT": case "UTC": return TimeZone.getTimeZone("UTC");
                case "BST": return TimeZone.getTimeZone("Europe/London");
                case "EDT": case "EST": case "ET": return TimeZone.getTimeZone("America/New_York");
                default: return TimeZone.getTimeZone("Europe/Copenhagen"); // CET/CEST
            }
        }
        if ("US".equals(prefix) || "CA".equals(prefix)) return TimeZone.getTimeZone("America/New_York");
        if ("UK".equals(prefix)) return TimeZone.getTimeZone("Europe/London");
        return TimeZone.getTimeZone("Europe/Copenhagen");
    }

    /** År for en dato uden år: det nærmeste (håndterer nytår). */
    private static int year(int month, int day) {
        Calendar now = Calendar.getInstance();
        int y = now.get(Calendar.YEAR), m = now.get(Calendar.MONTH);
        if (month - m > 6) return y - 1;
        if (m - month > 6) return y + 1;
        return y;
    }

    private static long at(TimeZone z, int y, int mo, int d, int h, int mi) {
        Calendar k = Calendar.getInstance(z);
        k.clear();
        k.set(y, mo, d, h, mi, 0);
        return k.getTimeInMillis();
    }

    private static String clean(String t) {
        return t.replaceAll("^\\s*\\d{1,3}\\s*-\\s+", "").replaceAll("\\s*:\\s*$", "").trim();
    }

    /** null hvis navnet ikke er en begivenhed med tidspunkt. */
    static Ev parse(String name, String prefix) {
        if (name == null || name.isEmpty()) return null;
        Matcher m;
        if ((m = A.matcher(name)).find()) {
            int d = Integer.parseInt(m.group(1)), mo = month(m.group(2));
            long t = at(zone(m.group(5), prefix), year(mo, d), mo, d, Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
            return new Ev(clean(name.substring(0, m.start())), null, t);
        }
        if ((m = C.matcher(name)).find()) {
            long t = at(zone(m.group(6), prefix), Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)));
            String before = name.substring(0, m.start());
            String[] parts = before.split("\\|");
            return new Ev(clean(parts[0]), parts.length > 1 ? parts[1].trim() : null, t);
        }
        if ((m = C2.matcher(name)).find()) {
            long t = at(zone(m.group(6), prefix), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)));
            String[] parts = name.substring(0, m.start()).split("\\|");
            return new Ev(clean(parts[0]), parts.length > 1 ? parts[1].trim() : null, t);
        }
        if ((m = D.matcher(name)).find()) {
            long t = at(zone(null, prefix), Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)));
            return new Ev(clean(name.substring(0, m.start())), null, t);
        }
        if ((m = B.matcher(name)).find()) {
            int mo = month(m.group(1)), d = Integer.parseInt(m.group(2)), h = Integer.parseInt(m.group(3)) % 12;
            if (m.group(5) != null && m.group(5).equalsIgnoreCase("PM")) h += 12;
            if (m.group(5) == null) h = Integer.parseInt(m.group(3));
            long t = at(zone(null, prefix), year(mo, d), mo, d, h, Integer.parseInt(m.group(4)));
            return new Ev(clean(name.substring(0, m.start())), null, t);
        }
        return null;
    }

    static Ev of(Catalog.Ch ch) {
        String raw = ch.raw != null ? ch.raw : ch.name;
        String p = Names.prefix(raw);
        if (p.isEmpty()) {
            Matcher m = Pattern.compile("\\b([A-Z]{2}):\\s*\\S").matcher(raw); // "… | DK: VIAPLAY PPV 12"
            if (m.find()) p = m.group(1);
        }
        if (p.isEmpty()) { Catalog.Cat k = Catalog.cats.get(ch.cat); if (k != null) p = k.prefix; }
        return parse(raw, p);
    }

    /** Kanalens korte navn: PPV-kanaler hedder fx "… | DK: VIAPLAY PPV 7" → "Viaplay PPV 7". */
    static String channelLabel(Catalog.Ch ch) {
        String raw = ch.raw != null ? ch.raw : ch.name;
        if (of(ch) == null) return ch.name;
        String tail = raw.contains("|") ? raw.substring(raw.lastIndexOf('|') + 1) : raw.contains(":") ? raw.substring(raw.lastIndexOf(':') + 1) : raw;
        tail = tail.replaceAll("^\\s*[A-Z]{2}:\\s*", "").trim();
        return tail.isEmpty() ? ch.name : nice(tail).replace("Ppv", "PPV");
    }

    /** Hvad der vises på en PPV-kanal (kampen), ellers null. */
    static String eventTitle(Catalog.Ch ch) {
        Ev e = of(ch);
        if (e == null || e.title.isEmpty()) return null;
        return nice(e.title.replaceAll("(?i)^(betano pokalen|oddset pokalen)\\s+", ""));
    }

    /** "FC ROSKILDE" → "FC Roskilde" (store bogstaver i PPV-navne gøres pæne). */
    static String nice(String s) {
        if (s == null || !s.equals(s.toUpperCase(Locale.ROOT))) return s;
        StringBuilder b = new StringBuilder();
        for (String w : s.toLowerCase(new Locale("da")).split(" ")) {
            if (b.length() > 0) b.append(' ');
            if (w.length() <= 3 && w.matches("[a-zæøå]+") && !w.matches("(i|og|af|de|la|el|di)")) b.append(w.toUpperCase(Locale.ROOT));
            else if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }
}
