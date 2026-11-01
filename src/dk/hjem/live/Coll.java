package dk.hjem.live;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Universer og sæsontemaer fra Vores TV-serveren (Marvel, Star Wars, Halloween …) – bedste udgave af hver titel, i rækkefølge. */
final class Coll {
    static final class Item { String kind, id, name, img, ext, sub, audio; int n; }
    static final class List1 { String key, name, group, order, note; boolean season; int missing; List<String> covers = new ArrayList<>(); List<Item> items = new ArrayList<>(); }

    static volatile List<List1> lists = new ArrayList<>();
    static volatile List<String> groups = new ArrayList<>();
    static volatile boolean jul, building;
    private static volatile long at;
    private static volatile boolean loading;

    /** Hentes i baggrunden (højst hvert 2. minut, mens serveren stadig bygger; ellers hvert 10.); Catalog.changed() tegner igen. */
    static void load(Context ctx) {
        if (loading || System.currentTimeMillis() - at < (building ? 120_000L : 10 * 60_000L)) return;
        loading = true;
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                String ip = Sync.webServer(c);
                if (ip == null) return;
                HttpURLConnection con = (HttpURLConnection) new URL("http://" + ip + ":" + Web.PORT + "/api/vod/collections").openConnection();
                con.setConnectTimeout(1500); con.setReadTimeout(8000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                    char[] buf = new char[8192]; int n;
                    while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                } finally { con.disconnect(); }
                JSONObject o = new JSONObject(sb.toString());
                if (!Search.acctOk(c, o.optString("acct"))) return; // andet IPTV-login: id'erne passer ikke
                jul = o.optBoolean("jul");
                building = o.optBoolean("building");
                List<String> gs = new ArrayList<>();
                JSONArray ga = o.optJSONArray("groups");
                for (int i = 0; ga != null && i < ga.length(); i++) gs.add(ga.optString(i));
                JSONArray ls = o.optJSONArray("lists");
                List<List1> out = new ArrayList<>();
                for (int i = 0; ls != null && i < ls.length(); i++) {
                    JSONObject l = ls.getJSONObject(i);
                    List1 L = new List1();
                    L.key = l.optString("key"); L.name = l.optString("name"); L.group = l.optString("group", "");
                    L.order = l.optString("order", ""); L.note = l.optString("note", ""); L.season = l.optBoolean("season");
                    JSONArray cv = l.optJSONArray("covers");
                    for (int j = 0; cv != null && j < cv.length(); j++) L.covers.add(cv.optString(j));
                    JSONArray it = l.optJSONArray("items");
                    for (int j = 0; it != null && j < it.length(); j++) {
                        JSONObject x = it.getJSONObject(j);
                        if (x.optBoolean("missing")) { L.missing++; continue; }
                        Item e = new Item();
                        e.n = j + 1; e.kind = x.optString("kind"); e.id = x.optString("id"); e.name = x.optString("name");
                        e.img = x.optString("img", null); e.ext = x.optString("ext", null); e.audio = x.optString("audio", null);
                        List<String> tags = new ArrayList<>();
                        String note = x.optString("note", "");
                        tags.add(note.isEmpty() ? String.valueOf(x.optInt("year")) : note);
                        if (Vod.SERIES.equals(e.kind)) tags.add("Serie");
                        if ("4K".equals(x.optString("quality"))) tags.add("4K");
                        if (x.optBoolean("surround")) tags.add("5.1");
                        String lang = x.optString("lang");
                        if ("dansk tale".equals(lang)) tags.add("Dansk tale");
                        else if (lang.startsWith("engelsk, danske")) tags.add("DK-tekst");
                        else if (!"engelsk".equals(lang)) tags.add("m. undertekster");
                        e.sub = android.text.TextUtils.join("  ·  ", tags);
                        L.items.add(e);
                    }
                    if (!L.items.isEmpty()) out.add(L);
                }
                groups = gs;
                lists = out;
                at = System.currentTimeMillis();
                Catalog.changed();
            } catch (Exception ignored) {
            } finally { loading = false; }
        }, "collections").start();
    }

    /** Husk filmens foretrukne lydspor (dansk for børnefilm), så afspilleren vælger det. */
    static void rememberAudio(Context c, Item x) {
        if (x.audio != null && !x.audio.isEmpty() && Vod.MOVIE.equals(x.kind))
            c.getSharedPreferences("audio", Context.MODE_PRIVATE).edit().putString(x.id, x.audio.equals("dan") ? "da" : x.audio).apply();
    }

    static List1 find(String key) {
        for (List1 l : lists) if (l.key.equals(key)) return l;
        return null;
    }
}
