package dk.hjem.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Kanaler og kategorier fra udbyderen, cachet på disken, plus jeres favoritliste. */
final class Catalog {
    static final class Ch {
        int id, num, archive;
        String name, logo, cat, epgId;
        String raw;
    }

    static final class Cat {
        String id, name, prefix;
        int count;
    }

    static volatile List<Ch> channels = new ArrayList<>();
    static volatile Map<Integer, Ch> byId = new HashMap<>();
    static volatile Map<String, Cat> cats = new LinkedHashMap<>();
    static volatile boolean loaded;
    static volatile String error;
    static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long MAX_AGE = 12 * 3600_000L;
    private static boolean loading;

    static void changed() { MAIN.post(() -> { for (Runnable r : listeners) r.run(); }); }

    static synchronized void load(Context ctx, boolean force) {
        if (loading) return;
        loading = true;
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                Cred cr = Cred.load(c);
                if (!cr.ok()) { error = null; return; }
                File fc = new File(c.getFilesDir(), "live_categories.json"), fs = new File(c.getFilesDir(), "live_streams.json");
                boolean fresh = !force && fs.exists() && System.currentTimeMillis() - fs.lastModified() < MAX_AGE;
                if (fresh && !loaded) { parse(read(fc), read(fs)); changed(); return; }
                if (fresh) return;
                if (!loaded && fs.exists()) { parse(read(fc), read(fs)); changed(); }
                Xtream x = new Xtream(cr);
                String jc = x.get("get_live_categories", null), js = x.get("get_live_streams", null);
                parse(jc, js);
                write(fc, jc); write(fs, js);
                error = null;
                changed();
            } catch (Exception e) {
                error = "Kunne ikke hente kanaler: " + e.getMessage();
                changed();
            } finally {
                synchronized (Catalog.class) { loading = false; }
            }
        }, "catalog").start();
    }

    private static void parse(String jc, String js) throws Exception {
        Map<String, Cat> cm = new LinkedHashMap<>();
        JSONArray ac = new JSONArray(jc);
        for (int i = 0; i < ac.length(); i++) {
            JSONObject o = ac.getJSONObject(i);
            Cat k = new Cat();
            k.id = o.optString("category_id");
            String rn = o.optString("category_name");
            k.prefix = Names.prefix(rn);
            k.name = Names.group(rn);
            cm.put(k.id, k);
        }
        JSONArray as = new JSONArray(js);
        List<Ch> list = new ArrayList<>(as.length());
        Map<Integer, Ch> ids = new HashMap<>();
        for (int i = 0; i < as.length(); i++) {
            JSONObject o = as.getJSONObject(i);
            Ch ch = new Ch();
            ch.id = o.optInt("stream_id");
            ch.num = o.optInt("num", i + 1);
            ch.raw = o.optString("name").trim();
            ch.name = Names.channel(ch.raw);
            ch.logo = o.optString("stream_icon");
            ch.cat = o.optString("category_id");
            ch.epgId = o.optString("epg_channel_id");
            ch.archive = o.optInt("tv_archive", 0);
            list.add(ch);
            ids.put(ch.id, ch);
            Cat k = cm.get(ch.cat);
            if (k != null) k.count++;
        }
        channels = list; byId = ids; cats = cm; loaded = true;
    }

    static List<Ch> inCategory(String catId) {
        List<Ch> out = new ArrayList<>();
        for (Ch c : channels) if (catId.equals(c.cat)) out.add(c);
        return out;
    }

    static Ch byNumber(int num) {
        for (Ch c : channels) if (c.num == num) return c;
        return null;
    }

    // ---- Favoritter (rækkefølgen er jeres egen) ----
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("live", Context.MODE_PRIVATE); }

    static List<Integer> favIds(Context c) {
        List<Integer> out = new ArrayList<>();
        for (String s : prefs(c).getString("favs", "").split(","))
            if (!s.isEmpty()) try { out.add(Integer.parseInt(s)); } catch (NumberFormatException ignored) { }
        return out;
    }

    static void setFavIds(Context c, List<Integer> ids) {
        StringBuilder b = new StringBuilder();
        for (Integer i : ids) { if (b.length() > 0) b.append(','); b.append(i); }
        prefs(c).edit().putString("favs", b.toString()).apply();
        changed();
    }

    static boolean isFav(Context c, int id) { return favIds(c).contains(id); }

    static void toggleFav(Context c, int id) {
        List<Integer> f = favIds(c);
        if (f.contains(id)) f.remove(Integer.valueOf(id)); else f.add(id);
        setFavIds(c, f);
    }

    static void moveFav(Context c, int id, int delta) {
        List<Integer> f = favIds(c);
        int i = f.indexOf(id), j = i + delta;
        if (i < 0 || j < 0 || j >= f.size()) return;
        f.remove(i); f.add(j, id);
        setFavIds(c, f);
    }

    static List<Ch> favorites(Context c) {
        List<Ch> out = new ArrayList<>();
        for (Integer i : favIds(c)) { Ch ch = byId.get(i); if (ch != null) out.add(ch); }
        return out;
    }

    // ---- Grupper, der vises på tv'et (standard: de danske) ----
    static List<String> shownGroups(Context c) {
        String v = prefs(c).getString("groups", null);
        List<String> out = new ArrayList<>();
        if (v == null) {
            for (Cat k : cats.values()) if (k.count > 0 && "DK".equals(k.prefix)) out.add(k.id);
            return out;
        }
        for (String s : v.split(",")) if (!s.isEmpty() && cats.containsKey(s)) out.add(s);
        return out;
    }

    static boolean isShown(Context c, String id) { return shownGroups(c).contains(id); }

    static void toggleGroup(Context c, String id) {
        List<String> g = shownGroups(c);
        if (g.contains(id)) g.remove(id); else g.add(id);
        StringBuilder b = new StringBuilder();
        for (String s : g) { if (b.length() > 0) b.append(','); b.append(s); }
        prefs(c).edit().putString("groups", b.toString()).apply();
        changed();
    }

    /** Landepræfikser sorteret med de største først, DK øverst. */
    static List<String> prefixes() {
        Map<String, Integer> n = new LinkedHashMap<>();
        for (Cat k : cats.values()) if (k.count > 0) { String p = k.prefix.isEmpty() ? "Andet" : k.prefix; n.put(p, n.containsKey(p) ? n.get(p) + 1 : 1); }
        List<String> out = new ArrayList<>(n.keySet());
        java.util.Collections.sort(out, (a, b) -> a.equals("DK") ? -1 : b.equals("DK") ? 1 : n.get(b) - n.get(a));
        return out;
    }

    static List<Cat> groupsWithPrefix(String p) {
        List<Cat> out = new ArrayList<>();
        for (Cat k : cats.values()) if (k.count > 0 && (p.equals("Andet") ? k.prefix.isEmpty() : p.equals(k.prefix))) out.add(k);
        return out;
    }

    static int lastChannel(Context c) { return prefs(c).getInt("last", -1); }
    static void setLastChannel(Context c, int id) { prefs(c).edit().putInt("last", id).apply(); }

    static String read(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            return new String(b, 0, off, StandardCharsets.UTF_8);
        }
    }

    static void write(File f, String s) throws Exception {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(s.getBytes(StandardCharsets.UTF_8)); }
        tmp.renameTo(f);
    }
}
