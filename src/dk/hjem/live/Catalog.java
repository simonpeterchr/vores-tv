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
        Bg.run("catalog", () -> {
            try {
                Cred cr = Cred.load(c);
                if (!cr.ok()) { error = null; return; }
                Logos.init(c);
                File fc = new File(c.getFilesDir(), "live_categories.json"), fs = new File(c.getFilesDir(), "live_streams.json");
                boolean fresh = !force && fs.exists() && System.currentTimeMillis() - fs.lastModified() < MAX_AGE;
                if (fresh && !loaded) { parse(fc, fs); changed(); return; }
                if (fresh) return;
                if (!loaded && fs.exists()) { parse(fc, fs); changed(); }
                Xtream x = new Xtream(cr);
                Http.download(x.api("get_live_categories", null), fc);
                Http.download(x.api("get_live_streams", null), fs);
                parse(fc, fs);
                error = null;
                changed();
            } catch (Exception e) {
                error = Http.friendly(e);
                changed();
            } finally {
                synchronized (Catalog.class) { loading = false; }
            }
        });
    }

    /** Læser kanallisten bid for bid fra fil (der kan være titusinder af kanaler – må ikke fylde hukommelsen). */
    /** Hurtig opstart: læs den færdigbehandlede kanalliste, hvis den er nyere end udbyderens filer. */
    private static boolean readIndex(File fc, File fs) {
        File fi = new File(fs.getParentFile(), "live_index2.tsv");
        if (!fi.exists() || fi.lastModified() < fs.lastModified()) return false;
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(fi), StandardCharsets.UTF_8), 64 * 1024)) {
            Map<String, Cat> cm = new LinkedHashMap<>();
            List<Ch> list = new ArrayList<>();
            Map<Integer, Ch> ids = new HashMap<>();
            Map<String, List<Ch>> byCat = new HashMap<>();
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (f[0].equals("C") && f.length >= 4) { Cat k = new Cat(); k.id = f[1].intern(); k.name = f[2]; k.prefix = f[3]; cm.put(k.id, k); continue; }
                if (!f[0].equals("K") || f.length < 9) continue;
                Ch ch = new Ch();
                ch.id = Integer.parseInt(f[1]); ch.num = Integer.parseInt(f[2]); ch.raw = f[3]; ch.name = f[4]; ch.logo = f[5];
                ch.cat = f[6].intern(); ch.epgId = f[7]; ch.archive = f[8].isEmpty() ? 0 : Integer.parseInt(f[8]);
                list.add(ch); ids.put(ch.id, ch);
                List<Ch> l = byCat.get(ch.cat);
                if (l == null) { l = new ArrayList<>(); byCat.put(ch.cat, l); }
                l.add(ch);
                Cat k = cm.get(ch.cat);
                if (k != null) k.count++;
            }
            if (list.isEmpty()) return false;
            channels = list; byId = ids; cats = cm; catIndex = byCat; loaded = true;
            return true;
        } catch (Exception e) { return false; }
    }

    private static void writeIndex(File fs) {
        File fi = new File(fs.getParentFile(), "live_index2.tsv"), tmp = new File(fi.getPath() + ".tmp");
        try (java.io.Writer w = new java.io.BufferedWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(tmp), StandardCharsets.UTF_8), 64 * 1024)) {
            for (Cat k : cats.values()) w.write("C\t" + t(k.id) + "\t" + t(k.name) + "\t" + t(k.prefix) + "\n");
            for (Ch c : channels) w.write("K\t" + c.id + "\t" + c.num + "\t" + t(c.raw) + "\t" + t(c.name) + "\t" + t(c.logo) + "\t" + t(c.cat) + "\t" + t(c.epgId) + "\t" + c.archive + "\n");
        } catch (Exception e) { tmp.delete(); return; }
        tmp.renameTo(fi);
    }

    private static String t(String s) { return s == null ? "" : s.replace('\t', ' ').replace('\n', ' '); }

    private static void parse(File fc, File fs) throws Exception {
        if (readIndex(fc, fs)) return;
        Map<String, Cat> cm = new LinkedHashMap<>();
        try (android.util.JsonReader r = Http.reader(fc)) {
            r.beginArray();
            while (r.hasNext()) {
                Cat k = new Cat();
                String rn = "";
                r.beginObject();
                while (r.hasNext()) {
                    String n = r.nextName();
                    if (n.equals("category_id")) k.id = Http.str(r);
                    else if (n.equals("category_name")) rn = Http.str(r);
                    else r.skipValue();
                }
                r.endObject();
                k.prefix = Names.prefix(rn);
                k.name = Names.group(rn);
                if (k.id != null) cm.put(k.id, k);
            }
            r.endArray();
        }
        List<Ch> list = new ArrayList<>();
        Map<Integer, Ch> ids = new HashMap<>();
        Map<String, List<Ch>> byCat = new HashMap<>();
        try (android.util.JsonReader r = Http.reader(fs)) {
            r.beginArray();
            int i = 0;
            while (r.hasNext()) {
                Ch ch = new Ch();
                ch.num = ++i;
                r.beginObject();
                while (r.hasNext()) {
                    switch (r.nextName()) {
                        case "stream_id": try { ch.id = Integer.parseInt(Http.str(r)); } catch (NumberFormatException e) { ch.id = 0; } break;
                        case "num": try { ch.num = Integer.parseInt(Http.str(r)); } catch (NumberFormatException ignored) { } break;
                        case "name": ch.raw = Http.str(r).trim(); break;
                        case "stream_icon": ch.logo = Http.str(r); break;
                        case "category_id": ch.cat = Http.str(r).intern(); break;
                        case "epg_channel_id": ch.epgId = Http.str(r); break;
                        case "tv_archive": try { ch.archive = Integer.parseInt(Http.str(r)); } catch (NumberFormatException ignored) { } break;
                        default: r.skipValue();
                    }
                }
                r.endObject();
                if (ch.raw == null) ch.raw = "";
                if (ch.cat == null) ch.cat = "";
                ch.name = Names.channel(ch.raw);
                Cat k0 = cm.get(ch.cat);
                String better = Logos.find(ch.name, Names.prefix(ch.raw), k0 != null ? k0.prefix : null); // rigtige logoer i stedet for udbyderens klemte
                if (better != null) ch.logo = better;
                list.add(ch);
                ids.put(ch.id, ch);
                List<Ch> l = byCat.get(ch.cat);
                if (l == null) { l = new ArrayList<>(); byCat.put(ch.cat, l); }
                l.add(ch);
                Cat k = cm.get(ch.cat);
                if (k != null) k.count++;
            }
            r.endArray();
        }
        channels = list; byId = ids; cats = cm; catIndex = byCat; loaded = true;
        writeIndex(fs);
    }

    private static volatile Map<String, List<Ch>> catIndex = new HashMap<>();

    static List<Ch> inCategory(String catId) {
        List<Ch> l = catIndex.get(catId);
        return l == null ? new ArrayList<>() : new ArrayList<>(l);
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
    static void setLastChannel(Context c, int id) {
        // seneste 20 kanaler, nyeste først (bruges til "Live nu" uden favoritter)
        List<Integer> h = history(c);
        h.remove(Integer.valueOf(id));
        h.add(0, id);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(20, h.size()); i++) { if (i > 0) b.append(','); b.append(h.get(i)); }
        prefs(c).edit().putInt("last", id).putString("history", b.toString()).apply();
    }

    static List<Integer> history(Context c) {
        List<Integer> out = new ArrayList<>();
        for (String s : prefs(c).getString("history", "").split(","))
            if (!s.isEmpty()) try { out.add(Integer.parseInt(s)); } catch (NumberFormatException ignored) { }
        return out;
    }

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
