package dk.hjem.live;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Film og serier fra udbyderen. Kategorier hentes samlet; titler hentes pr. kategori efter behov (udbyderen er enorm). */
final class Vod {
    static final String MOVIE = "movie", SERIES = "series";

    static final class Item {
        String kind, id, name, poster, cat, ext, year, rating;
        long added;
    }

    static final class Cat {
        String id, name, prefix, raw;
    }

    static final Map<String, Map<String, Cat>> cats = new ConcurrentHashMap<>();
    static final Map<String, List<Item>> items = new ConcurrentHashMap<>(); // kind|cat -> titler
    private static final java.util.Set<String> loading = Collections.synchronizedSet(new java.util.HashSet<>());
    private static final ExecutorService pool = Executors.newFixedThreadPool(3);
    private static final long MAX_AGE = 12 * 3600_000L;

    static Map<String, Cat> categories(String kind) {
        Map<String, Cat> m = cats.get(kind);
        return m == null ? new LinkedHashMap<>() : m;
    }

    static void loadCategories(Context ctx, String kind) {
        if (cats.containsKey(kind) || !loading.add("cats|" + kind)) return;
        final Context c = ctx.getApplicationContext();
        pool.execute(() -> {
            try {
                String js = cached(c, kind + "_categories.json", () -> new Xtream(Cred.load(c)).get(kind.equals(MOVIE) ? "get_vod_categories" : "get_series_categories", null));
                JSONArray a = new JSONArray(js);
                Map<String, Cat> m = new LinkedHashMap<>();
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    Cat k = new Cat();
                    k.id = o.optString("category_id");
                    String rn = o.optString("category_name");
                    k.prefix = Names.prefix(rn);
                    k.name = Names.group(rn);
                    if (k.prefix.isEmpty()) { String w = k.name.split(" ")[0].replaceAll("[^\\p{L}0-9+]", ""); k.prefix = w.isEmpty() ? "Andet" : Ui.cap(w.toLowerCase(Fmt.DA)); }
                    k.raw = rn;
                    m.put(k.id, k);
                }
                cats.put(kind, m);
                Catalog.changed();
            } catch (Exception ignored) {
            } finally { loading.remove("cats|" + kind); }
        });
    }

    static List<Item> inCategory(Context ctx, String kind, String catId) {
        String key = kind + "|" + catId;
        List<Item> l = items.get(key);
        if (l != null) return l;
        if (loading.add(key)) {
            final Context c = ctx.getApplicationContext();
            pool.execute(() -> {
                try {
                    String js = cached(c, kind + "_" + catId.replaceAll("[^0-9A-Za-z]", "_") + ".json", () -> new Xtream(Cred.load(c))
                            .get(kind.equals(MOVIE) ? "get_vod_streams" : "get_series", "&category_id=" + Xtream.enc(catId)));
                    JSONArray a = new JSONArray(js);
                    List<Item> out = new ArrayList<>(a.length());
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject o = a.getJSONObject(i);
                        Item it = new Item();
                        it.kind = kind;
                        it.cat = catId;
                        if (kind.equals(MOVIE)) {
                            it.id = o.optString("stream_id");
                            it.poster = o.optString("stream_icon");
                            it.ext = o.optString("container_extension");
                            it.added = o.optLong("added");
                        } else {
                            it.id = o.optString("series_id");
                            it.poster = o.optString("cover");
                            it.added = o.optLong("last_modified");
                            it.year = o.optString("releaseDate", o.optString("release_date", ""));
                            if (it.year.length() >= 4) it.year = it.year.substring(0, 4);
                        }
                        String[] yr = new String[6];
                        yr[4] = it.year;
                        it.name = cleanTitle(o.optString("name"), yr);
                        it.year = yr[4];
                        it.rating = o.optString("rating", "");
                        out.add(it);
                    }
                    Collections.sort(out, (x, y) -> Long.compare(y.added, x.added));
                    items.put(key, out);
                    Catalog.changed();
                } catch (Exception ignored) {
                } finally { loading.remove(key); }
            });
        }
        return new ArrayList<>();
    }

    /** "4K-DK - Sommerdahl (2020) (DK)" → "Sommerdahl"; årstal lægges i e[4] hvis det mangler. */
    static String cleanTitle(String raw, String[] e) {
        String nm = Names.channel(raw);
        nm = nm.replaceAll("^[A-Z0-9+]{1,6}(-[A-Z0-9+]{1,6})?\\s+-\\s+", "");
        for (int r = 0; r < 3; r++) nm = nm.replaceAll("(?i)\\s*[(\\[](DK|SE|NO|FI|EN|ENG|DANSK|NORDIC|MULTI[^)\\]]*|SUB[^)\\]]*|4K|HD|UHD)[)\\]]\\s*$", "");
        java.util.regex.Matcher ym = java.util.regex.Pattern.compile("\\s*[(\\[]?((19|20)\\d\\d)[)\\]]?\\s*$").matcher(nm);
        if (ym.find()) { if (e[4] == null || e[4].isEmpty()) e[4] = ym.group(1); nm = nm.substring(0, ym.start()).trim(); }
        return nm.replaceAll("\\s*-\\s*$", "");
    }

    interface Fetch { String get() throws Exception; }

    static String cached(Context c, String file, Fetch f) throws Exception {
        File fl = new File(c.getFilesDir(), file);
        if (fl.exists() && System.currentTimeMillis() - fl.lastModified() < MAX_AGE) return Catalog.read(fl);
        try {
            String s = f.get();
            Catalog.write(fl, s);
            return s;
        } catch (Exception e) {
            if (fl.exists()) return Catalog.read(fl);
            throw e;
        }
    }

    // ---- Viste grupper (standard: danske) ----
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("vod", Context.MODE_PRIVATE); }

    static List<String> shownGroups(Context c, String kind) {
        String v = prefs(c).getString("groups_" + kind, null);
        List<String> out = new ArrayList<>();
        Map<String, Cat> m = categories(kind);
        if (v == null) { // standard: danske/nordiske grupper + de store "top"-grupper uden 4K
            for (Cat k : m.values()) {
                String r = k.raw == null ? k.name : k.raw.toUpperCase(java.util.Locale.ROOT);
                boolean dk = "DK".equals(k.prefix) || r.matches(".*\\b(DANSK|DANSKE|DANISH|NORDIC|SCANDINAVIAN|SCANDI|NORDISK)\\b.*") && !r.matches(".*\\b(MUSIC|SPORT|BECK)\\b.*");
                boolean top = r.matches("^(NETFLIX (MOVIES|SERIES)|TOP (MOVIES|SERIES) BLURAY.*)$");
                if (dk || top) out.add(k.id);
            }
            // danske først, så nordiske, så resten
            java.util.Collections.sort(out, (a, b) -> rank(m.get(a)) - rank(m.get(b)));
            return out;
        }
        for (String s : v.split(",")) if (!s.isEmpty() && m.containsKey(s)) out.add(s);
        java.util.Collections.sort(out, (a, b) -> rank(m.get(a)) - rank(m.get(b)));
        return out;
    }

    private static int rank(Cat k) {
        String r = (k.raw == null ? k.name : k.raw).toUpperCase(java.util.Locale.ROOT);
        return r.contains("DANSK") || r.contains("DANISH") || "DK".equals(k.prefix) ? 0 : r.contains("NORDIC") || r.contains("NORDISK") || r.contains("SCANDI") ? 1 : 2;
    }

    static void toggleGroup(Context c, String kind, String id) {
        List<String> g = shownGroups(c, kind);
        if (g.contains(id)) g.remove(id); else g.add(id);
        StringBuilder b = new StringBuilder();
        for (String s : g) { if (b.length() > 0) b.append(','); b.append(s); }
        prefs(c).edit().putString("groups_" + kind, b.toString()).apply();
        Catalog.changed();
    }

    // ---- Fortsæt hvor I slap ----
    static final class Resume {
        String key, kind, id, title, sub, poster, ext, seriesId;
        long pos, dur, when;
        float progress() { return dur > 0 ? Math.min(1f, pos / (float) dur) : 0f; }
    }

    private static SharedPreferences rprefs(Context c) { return c.getSharedPreferences("resume", Context.MODE_PRIVATE); }

    static void saveResume(Context c, Resume r) {
        try {
            JSONObject o = new JSONObject();
            o.put("kind", r.kind).put("id", r.id).put("title", r.title).put("sub", r.sub).put("poster", r.poster)
             .put("ext", r.ext).put("seriesId", r.seriesId).put("pos", r.pos).put("dur", r.dur).put("when", System.currentTimeMillis());
            boolean done = r.dur > 0 && r.pos > r.dur - Math.min(r.dur * 0.07, 8 * 60_000L);
            SharedPreferences.Editor e = rprefs(c).edit();
            if (done) { e.remove(r.key); o.put("done", true); e.putString("done:" + r.key, "1"); }
            else e.putString(r.key, o.toString());
            e.apply();
        } catch (Exception ignored) { }
    }

    static Resume resume(Context c, String key) {
        String s = rprefs(c).getString(key, null);
        if (s == null) return null;
        try {
            JSONObject o = new JSONObject(s);
            Resume r = new Resume();
            r.key = key; r.kind = o.optString("kind"); r.id = o.optString("id"); r.title = o.optString("title");
            r.sub = o.optString("sub"); r.poster = o.optString("poster"); r.ext = o.optString("ext"); r.seriesId = o.optString("seriesId");
            r.pos = o.optLong("pos"); r.dur = o.optLong("dur"); r.when = o.optLong("when");
            return r;
        } catch (Exception e) { return null; }
    }

    static boolean watched(Context c, String key) { return rprefs(c).contains("done:" + key); }

    /** Nyeste først; for serier kun seneste afsnit pr. serie. */
    static List<Resume> continueList(Context c, String kindOrNull) {
        List<Resume> out = new ArrayList<>();
        java.util.Set<String> seenSeries = new java.util.HashSet<>();
        List<Resume> all = new ArrayList<>();
        for (String k : rprefs(c).getAll().keySet()) if (!k.startsWith("done:")) { Resume r = resume(c, k); if (r != null && r.pos > 60_000) all.add(r); }
        Collections.sort(all, (a, b) -> Long.compare(b.when, a.when));
        for (Resume r : all) {
            if (kindOrNull != null && !kindOrNull.equals(r.kind)) continue;
            if (SERIES.equals(r.kind) && !seenSeries.add(r.seriesId)) continue;
            out.add(r);
        }
        return out;
    }
}
