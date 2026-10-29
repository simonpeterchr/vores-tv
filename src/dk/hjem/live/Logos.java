package dk.hjem.live;

import android.content.Context;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rigtige kanallogoer fra github.com/tv-logo/tv-logos (udbyderens er klemt ned i 96×96).
 * Matcher på kanalnavn, i landets mappe først: "DK| TV 2 SPORT X ᴴᴰ" → countries/nordic/denmark/tv2-sport-x-dk.png.
 * Samme regler som webappens lib/logos.js.
 */
final class Logos {
    private static final String CDN = "https://cdn.jsdelivr.net/gh/tv-logo/tv-logos@main/";
    private static Map<String, Map<String, String>> maps; // mappe -> navn -> sti
    private static final Map<String, String> FOLDER = new HashMap<>();
    static {
        String[][] f = {{"DK", "denmark"}, {"SE", "sweden"}, {"NO", "norway"}, {"FI", "finland"}, {"UK", "united-kingdom"}, {"GB", "united-kingdom"},
                {"US", "united-states"}, {"DE", "germany"}, {"ES", "spain"}, {"IT", "italy"}, {"FR", "france"}, {"NL", "netherlands"}};
        for (String[] x : f) FOLDER.put(x[0], x[1]);
    }
    private static final String[] FALLBACK = {"denmark", "nordic", "sweden", "norway", "international", "united-kingdom"};

    private static final Pattern DROP = Pattern.compile("\\b(hd|fhd|uhd|4k|8k|raw|sd|hevc|h265|vip|ultra|syn|multi|multi-sub|multi-audio|overlay-sub|sub|50fps|60fps|backup|alt|dk|se|no|uk|danmark|europe|ukraine|tv guide)\\b");
    private static final String[][] ALIAS = {
            {"^viaplay-(?=(tv|v|dk|see|ramsang|film))", ""}, {"^v-(explore|history|nature)", "viasat-$1"}, {"^v-sport-ultra.*", "v-sport-ultrahd"}, {"^viaplay-film-", "v-film-"},
            {"premier(e)?$|premiume$", "premiere"}, {"^nat-geo", "national-geographic"}, {"^id-investigation", "investigation"},
            {"^discovery-channels$", "discovery-channel"}, {"ramsang", "dr-ramasjang"},
            {"^disney-junoir", "disney-jr"}, {"^disney-junior", "disney-jr"}, {"^sports-news", "sport-news"}, {"^viaplay-sports-news", "viaplay-sport-news"},
            {"^tv2-oj$", "tv2-ostjylland"}, {"ostjyl(and)?$", "ostjylland"}, {"^tv2-midt-vest$|^tv-2-midt-vest$", "tv-midtvest"}, {"^tv2-midtvest$", "tv-midtvest"}, {"^tv2-syd$", "tv-syd"},
            {"^dk1$", "dr1"}, {"^dk2$", "dr2"}, {"^film-", "v-film-"}, {"^v-film-series$", "v-series"}, {"^nicktoons$", "nick-toons"},
            {"^skyshowtime-?(\\d)$", "sky-showtime-$1"}, {"^folketingskanalen$", "folketinget"}, {"^dk-4$|^4$", "dk4"},
    };
    private static final Pattern[] ALIAS_RE = new Pattern[ALIAS.length];
    static { for (int i = 0; i < ALIAS.length; i++) ALIAS_RE[i] = Pattern.compile(ALIAS[i][0]); }

    /** Indlæser oversigten fra assets (én gang, før kanallisten behandles). */
    static synchronized void init(Context c) {
        if (maps != null) return;
        Map<String, Map<String, String>> m = new HashMap<>();
        try {
            java.io.InputStream in = c.getAssets().open("logos.json");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            JSONArray a = new JSONArray(new String(bo.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                String p = a.getString(i);
                String[] parts = p.split("/");
                String folder = parts[1].equals("nordic") && parts.length > 3 ? parts[2] : parts[1];
                String stem = parts[parts.length - 1].replaceAll("\\.png$", "").replaceAll("-[a-z]{2,3}$", "");
                boolean hz = stem.endsWith("-hz");
                if (hz) stem = stem.substring(0, stem.length() - 3);
                Map<String, String> fm = m.get(folder);
                if (fm == null) { fm = new HashMap<>(); m.put(folder, fm); }
                if (!fm.containsKey(stem) || !hz) fm.put(stem, p); // hellere det almindelige logo end den vandrette variant
            }
        } catch (Exception ignored) { }
        maps = m;
    }

    private static String glue(String s) { return s.replaceAll("\\b([a-z]{1,3})-(\\d)", "$1$2"); } // "tv-2-sport" → "tv2-sport"

    static List<String> keys(String name) {
        String n = Search.norm(name);
        Set<String> out = new LinkedHashSet<>();
        if (n.replaceAll("\\s+", " ").matches(".*\\bv[ -]sport ultra.*")) out.add("v-sport-ultrahd");
        String s = n.replace("æ", "ae").replace("ø", "o").replace("å", "a").replaceAll("[’'`*]", "").replaceAll("[()\\[\\]]", " ")
                .replace("&", " and ").replace("+", " plus ");
        s = DROP.matcher(s).replaceAll(" ").replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.isEmpty()) return new ArrayList<>(out);
        out.add(s); out.add(glue(s));
        String a = glue(s);
        for (int i = 0; i < ALIAS.length; i++) a = ALIAS_RE[i].matcher(a).replaceAll(ALIAS[i][1]);
        if (!a.isEmpty()) { out.add(a); out.add(glue(a)); }
        out.add(s.replace("-", ""));
        return new ArrayList<>(out);
    }

    /** Bedste logo for et kanalnavn, eller null. countries: landekoder at prøve først (fx kanalens og gruppens præfiks). */
    static String find(String name, String... countries) {
        if (maps == null || name == null) return null;
        Set<String> order = new LinkedHashSet<>();
        for (String c : countries) { String f = c == null ? null : FOLDER.get(c); if (f != null) order.add(f); }
        for (String f : FALLBACK) order.add(f);
        List<String> ks = keys(name);
        for (String f : order) {
            Map<String, String> m = maps.get(f);
            if (m == null) continue;
            for (String k : ks) { String p = m.get(k); if (p != null) return CDN + p; }
        }
        return null;
    }
}
