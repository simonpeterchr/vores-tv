package dk.hjem.live;

import android.content.Context;
import android.util.JsonReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Søgning på tværs: kanaler, programmer (EPG), alle film og alle serier. */
final class Search {
    static final class Hit { String kind, id, name, img, sub, cat, ext; long start, stop; int chId; }

    // Hele film- og seriekataloget i kompakt form (id, navn, plakat, kategori, år, endelse)
    static volatile List<String[]> movies, series;
    private static volatile boolean indexing;
    private static final ExecutorService pool = Executors.newFixedThreadPool(6);
    static final AtomicInteger epgPending = new AtomicInteger();

    /** Sportsord: "fodbold" skal også finde Superliga-kampe osv. */
    private static final Map<String, String[]> SYN = new HashMap<>();
    static {
        SYN.put("fodbold", new String[]{"fodbold", "superliga", "champions league", "europa league", "conference league", "premier league", "la liga", "laliga", "bundesliga", "serie a", "ligue 1", "landskamp", "landsholdet", "pokal", "sydbank pokalen", "1. division", "soccer", "football", "vm-kval", "em-kval", "nations league", "uefa", "fifa"});
        SYN.put("håndbold", new String[]{"håndbold", "handball", "herreligaen", "kvindeligaen", "ehf"});
        SYN.put("tennis", new String[]{"tennis", "atp", "wta", "wimbledon", "roland garros", "us open", "australian open"});
        SYN.put("cykling", new String[]{"cykling", "tour de france", "giro", "vuelta", "cycling"});
        SYN.put("formel 1", new String[]{"formel 1", "formula 1", "f1", "grand prix"});
        SYN.put("ishockey", new String[]{"ishockey", "nhl", "metal ligaen", "hockey"});
        SYN.put("dart", new String[]{"darts", "pdc", "premier league darts", "world matchplay", "grand slam of darts", "players championship", "world grand prix", "european championship darts", "vm i dart", "masters darts", "littler", "van gerwen"});
        SYN.put("golf", new String[]{"golf", "pga", "dp world tour", "ryder cup"});
        SYN.put("nyheder", new String[]{"nyheder", "tv avisen", "tva", "news", "21 søndag", "deadline"});
    }

    static String norm(String s) {
        if (s == null) return "";
        s = s.toLowerCase(Locale.ROOT).replace('æ', '\u0001').replace('ø', '\u0002').replace('å', '\u0003');
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replace('\u0001', 'æ').replace('\u0002', 'ø').replace('\u0003', 'å');
    }

    /** En søgning = liste af alternativer; hvert alternativ er ord der alle skal findes. */
    static List<String[]> query(String q) {
        q = norm(q).trim();
        List<String[]> alts = new ArrayList<>();
        if (q.isEmpty()) return alts;
        for (Map.Entry<String, String[]> e : SYN.entrySet()) {
            if (q.equals(norm(e.getKey())) || q.equals(norm(e.getKey()) + "kamp") || q.equals(norm(e.getKey()) + "kampe")) {
                for (String s : e.getValue()) alts.add(new String[]{"=" + norm(s)}); // "=" = helt ord
                return alts;
            }
        }
        alts.add(q.split("\\s+"));
        return alts;
    }

    static boolean match(String text, List<String[]> q) {
        if (text == null || q.isEmpty()) return false;
        String t = norm(text);
        outer:
        for (String[] words : q) {
            for (String w : words) if (!has(t, w)) continue outer;
            return true;
        }
        return false;
    }

    /** Ordet skal starte et ord i teksten ("fodbold" ≠ "…fodbolden" er ok, men "dart" ≠ "Dartmouth" for "=dart"). */
    private static boolean has(String t, String w) {
        boolean whole = w.startsWith("=");
        if (whole) w = w.substring(1);
        for (int i = t.indexOf(w); i >= 0; i = t.indexOf(w, i + 1)) {
            boolean startOk = i == 0 || !Character.isLetterOrDigit(t.charAt(i - 1));
            int e = i + w.length();
            boolean endOk = !whole || e >= t.length() || !Character.isLetterOrDigit(t.charAt(e));
            if (startOk && endOk) return true;
        }
        return false;
    }

    static final java.util.regex.Pattern EVENT = java.util.regex.Pattern.compile("\\s*[@|]\\s*(\\d{4}-\\d{2}-\\d{2})[ T]+(\\d{1,2}:\\d{2})");
    static final java.text.SimpleDateFormat EVENT_FMT = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);

    static String when(long t) {
        java.util.Calendar a = java.util.Calendar.getInstance(), b = java.util.Calendar.getInstance();
        b.setTimeInMillis(t);
        int d = b.get(java.util.Calendar.DAY_OF_YEAR) - a.get(java.util.Calendar.DAY_OF_YEAR) + 365 * (b.get(java.util.Calendar.YEAR) - a.get(java.util.Calendar.YEAR));
        String day = d == 0 ? "I dag" : d == 1 ? "I morgen" : d == -1 ? "I går" : new java.text.SimpleDateFormat("EEE d. MMM", Fmt.DA).format(b.getTime());
        return day + " " + Fmt.hm(t);
    }

    // ---- live ----
    static List<Hit> channels(Context c, List<String[]> q, int max) {
        List<Hit> out = new ArrayList<>(), later = new ArrayList<>();
        java.util.Set<String> shown = new java.util.HashSet<>(Catalog.shownGroups(c));
        for (Catalog.Ch ch : Catalog.channels) {
            if (ch.name.matches("^[#=*~\\-_ ]{3,}.*")) continue; // skillelinjer som "##### SOCCER PPV #####"
            Catalog.Cat k = Catalog.cats.get(ch.cat);
            boolean m = match(ch.name, q) || (k != null && match(k.name, q) && q.size() > 1);
            if (!m) continue;
            Hit h = new Hit();
            h.kind = "channel"; h.chId = ch.id; h.name = ch.name; h.img = ch.logo; h.sub = k != null ? k.name : "";
            // PPV-kanaler bærer kampen i navnet: "Serie A: Sassuolo-Lazio @ 2026-10-27 20:45"
            java.util.regex.Matcher ev = EVENT.matcher(ch.name);
            if (ev.find()) {
                h.name = ch.name.substring(0, ev.start()).trim();
                try { h.start = EVENT_FMT.parse(ev.group(1) + " " + ev.group(2)).getTime(); h.sub = when(h.start) + "  ·  " + h.sub; } catch (Exception ignored) { }
            }
            (shown.contains(ch.cat) || Catalog.isFav(c, ch.id) ? out : later).add(h);
            if (out.size() >= max) break;
        }
        for (Hit h : later) { if (out.size() >= max) break; out.add(h); }
        return out;
    }

    /** Programmer der kører nu eller senere i dag på kanaler i jeres grupper og favoritter. */
    static List<Hit> programs(Context c, List<String[]> q, int max) {
        List<Hit> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Catalog.Ch ch : epgChannels(c)) {
            for (Xtream.Prog p : Epg.get(ch.id)) {
                if (p.stop < now || !match(p.title, q)) continue;
                Hit h = new Hit();
                h.kind = "program"; h.chId = ch.id; h.name = p.title; h.img = ch.logo; h.start = p.start; h.stop = p.stop;
                h.sub = ch.name;
                out.add(h);
            }
        }
        java.util.Collections.sort(out, (a, b) -> Long.compare(a.start, b.start));
        return out.size() > max ? out.subList(0, max) : out;
    }

    static List<Catalog.Ch> epgChannels(Context c) {
        List<Catalog.Ch> l = new ArrayList<>(Catalog.favorites(c));
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Catalog.Ch ch : l) seen.add(ch.id);
        for (String g : Catalog.shownGroups(c)) for (Catalog.Ch ch : Catalog.inCategory(g)) if (seen.add(ch.id)) l.add(ch);
        return l;
    }

    /** Henter programguide for alle kanaler i jeres grupper, så programsøgning virker. */
    static void warmEpg(Context c) {
        for (Catalog.Ch ch : epgChannels(c)) {
            if (!Epg.get(ch.id).isEmpty()) continue;
            epgPending.incrementAndGet();
            Epg.want(c, ch.id, epgPending);
        }
    }

    // ---- film og serier (hele kataloget, streamet så hukommelsen holder) ----
    static void index(final Context ctx) {
        if (indexing || (movies != null && series != null)) return;
        indexing = true;
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                if (movies == null) movies = load(c, Vod.MOVIE);
                Catalog.changed();
                if (series == null) series = load(c, Vod.SERIES);
                Catalog.changed();
            } catch (Exception ignored) {
            } finally { indexing = false; }
        }, "search-index").start();
    }

    static boolean indexing() { return indexing; }

    private static List<String[]> load(Context c, String kind) throws Exception {
        File f = new File(c.getFilesDir(), "index_" + kind + ".tsv");
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 24 * 3600_000L) return readTsv(f);
        Xtream x = new Xtream(Cred.load(c));
        HttpURLConnection con = (HttpURLConnection) new URL(x.api(Vod.MOVIE.equals(kind) ? "get_vod_streams" : "get_series", null)).openConnection();
        con.setRequestProperty("User-Agent", Http.UA);
        con.setConnectTimeout(15000);
        con.setReadTimeout(60000);
        List<String[]> out = new ArrayList<>();
        try (JsonReader r = new JsonReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            r.beginArray();
            while (r.hasNext()) {
                String[] e = new String[6]; // id, navn, billede, kategori, år, endelse
                r.beginObject();
                while (r.hasNext()) {
                    String n = r.nextName();
                    if (r.peek() == android.util.JsonToken.NULL) { r.nextNull(); continue; }
                    if (r.peek() == android.util.JsonToken.BEGIN_ARRAY || r.peek() == android.util.JsonToken.BEGIN_OBJECT) { r.skipValue(); continue; }
                    String v = r.nextString();
                    switch (n) {
                        case "stream_id": case "series_id": e[0] = v; break;
                        case "name": e[1] = v; break;
                        case "stream_icon": case "cover": e[2] = v; break;
                        case "category_id": e[3] = v; break;
                        case "releaseDate": case "release_date": case "year": if (e[4] == null && v.length() >= 4) e[4] = v.substring(0, 4); break;
                        case "container_extension": e[5] = v; break;
                    }
                }
                r.endObject();
                if (e[0] != null && e[1] != null) { e[1] = Vod.cleanTitle(e[1], e); out.add(e); }
            }
            r.endArray();
        } finally { con.disconnect(); }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            for (String[] e : out) {
                for (int i = 0; i < e.length; i++) { if (i > 0) w.write('\t'); w.write(e[i] == null ? "" : e[i].replace('\t', ' ').replace('\n', ' ')); }
                w.write('\n');
            }
        }
        return out;
    }

    private static List<String[]> readTsv(File f) throws Exception {
        List<String[]> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) out.add(Arrays.copyOf(line.split("\t", -1), 6));
        }
        return out;
    }

    static List<Hit> vod(Context c, String kind, List<String[]> q, int max) {
        List<String[]> src = Vod.MOVIE.equals(kind) ? movies : series;
        List<Hit> first = new ArrayList<>(), rest = new ArrayList<>();
        if (src == null) return first;
        java.util.Set<String> shown = new java.util.HashSet<>(Vod.shownGroups(c, kind));
        for (String[] e : src) {
            if (!match(e[1], q)) continue;
            Hit h = new Hit();
            h.kind = kind; h.id = e[0]; h.name = e[1]; h.img = e[2]; h.cat = e[3]; h.sub = e[4]; h.ext = e[5];
            (shown.contains(e[3]) ? first : rest).add(h);
            if (first.size() >= max) break;
        }
        for (Hit h : rest) { if (first.size() >= max) break; first.add(h); }
        return first;
    }
}
