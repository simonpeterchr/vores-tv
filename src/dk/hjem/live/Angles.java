package dk.hjem.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Kameravinkler til F1 og MotoGP: hovedudsendelse, pitlane, driver tracker og onboard pr. kører. */
final class Angles {
    static final String F1 = "f1", MOTOGP = "motogp";

    static final class Angle {
        String label, sub, section;
        Catalog.Ch ch;
    }

    private static String raw(Catalog.Ch c) { return c.raw != null ? c.raw : c.name; }
    private static String cat(Catalog.Ch c) { Catalog.Cat k = Catalog.cats.get(c.cat); return k != null ? k.name : ""; }

    /** Hvilken familie af vinkler en kanal hører til (eller null). */
    static String family(Catalog.Ch c) {
        if (c == null) return null;
        String r = raw(c).toLowerCase(Locale.ROOT), k = cat(c).toLowerCase(Locale.ROOT);
        if (r.contains("motogp")) return MOTOGP;
        if (r.matches(".*\\b(f1|formula ?1|formel 1|formula one)\\b.*") || k.matches(".*\\bformula 1\\b.*")) return F1;
        return null;
    }

    private static int q(String raw) {
        switch (Match.quality(raw)) { case "FHD": return 5; case "HD": return 4; case "RAW": return 3; case "4K": return 2; case "SD": return 1; default: return 3; }
    }

    /** Alle vinkler i familien, ordnet: hovedudsendelse, ekstra feeds, onboard. */
    static List<Angle> list(String fam) {
        List<Angle> main = new ArrayList<>(), extra = new ArrayList<>(), onboard = new ArrayList<>();
        Map<String, Catalog.Ch> bestMain = new LinkedHashMap<>();
        Map<String, Angle> drivers = new LinkedHashMap<>();
        for (Catalog.Ch c : Catalog.channels) {
            String r = raw(c), u = r.toUpperCase(Locale.ROOT), k = cat(c).toUpperCase(Locale.ROOT);
            if (u.startsWith("#") || u.contains("NO EVENT")) continue;
            if (F1.equals(fam)) {
                // hovedudsendelser (én pr. navn, bedste kvalitet til et tv)
                String key = null;
                if (u.startsWith("SKY SPORTS F1")) key = "Sky Sports F1";
                else if (u.startsWith("F1 TV MAIN ENGLISH")) key = "F1 TV · engelsk";
                else if (u.startsWith("F1 TV MAIN FRANCE")) key = "F1 TV · fransk";
                else if (u.startsWith("F1 TV GERMANY")) key = "F1 TV · tysk";
                else if (u.startsWith("F1 TV SPANISH")) key = "F1 TV · spansk";
                else if (u.matches("^CANAL\\+ FORMULA ?1.*")) key = "Canal+ F1";
                if (key != null) { Catalog.Ch b = bestMain.get(key); if (b == null || q(r) > q(raw(b))) bestMain.put(key, c); continue; }
                if (u.startsWith("F1 TV PIT LANE")) { extra.add(a("Pitlane", "F1 TV", "Ekstra", c)); continue; }
                if (u.startsWith("F1 TV DRIVER TRACKER")) { extra.add(a("Driver tracker", "Kort over banen", "Ekstra", c)); continue; }
                if (u.startsWith("ONBOARD |") && u.contains("FORMEL 1")) { extra.add(a("Onboard (Viaplay)", Ev.nice(r.split("\\|").length > 1 ? r.split("\\|")[1].trim() : ""), "Ekstra", c)); continue; }
                // onboard pr. kører: "MAX VERSTAPPEN | RED BULL RACING | VER" eller "F1 CAR VERSTAPPEN"
                if (k.contains("FORMULA 1 PPV") && u.split("\\|").length >= 3) {
                    String[] p = r.split("\\|");
                    String name = Ev.nice(p[0].trim()), surname = name.substring(name.lastIndexOf(' ') + 1).toUpperCase(Locale.ROOT);
                    drivers.put(surname, a(name, Ev.nice(p[1].trim()) + " · " + p[2].trim(), "Onboard", c));
                    continue;
                }
                if (u.startsWith("F1 CAR ")) {
                    String surname = u.substring(7).trim();
                    if (!drivers.containsKey(surname)) drivers.put(surname, a(Ev.nice(surname), "Onboard", "Onboard", c));
                }
            } else if (MOTOGP.equals(fam) && k.contains("FORMULA 1 / MOTOGP") && u.startsWith("MOTOGP")) {
                String rest = Ev.nice(u.substring(6).trim().replace("BBOARD", "BOARD"));
                (rest.toLowerCase(Locale.ROOT).startsWith("on board") ? onboard : extra).add(a(rest, "MotoGP", rest.toLowerCase(Locale.ROOT).startsWith("on board") ? "Onboard" : "Ekstra", c));
            } else if (MOTOGP.equals(fam) && u.matches("^(SKY SPORT MOTOGP|DAZN MOTOGP).*")) {
                String key = u.startsWith("SKY") ? "Sky Sport MotoGP" : "DAZN MotoGP";
                Catalog.Ch b = bestMain.get(key);
                if (b == null || q(r) > q(raw(b))) bestMain.put(key, c);
            }
        }
        for (Map.Entry<String, Catalog.Ch> e : bestMain.entrySet()) main.add(a(e.getKey(), "Hovedudsendelse", "Hovedudsendelse", e.getValue()));
        List<Angle> d = new ArrayList<>(drivers.values());
        Collections.sort(d, (x, y) -> x.label.compareTo(y.label));
        onboard.addAll(d);
        List<Angle> out = new ArrayList<>(main);
        out.addAll(extra);
        out.addAll(onboard);
        return out;
    }

    private static Angle a(String label, String sub, String section, Catalog.Ch c) {
        Angle x = new Angle();
        x.label = label; x.sub = sub; x.section = section; x.ch = c;
        return x;
    }
}
