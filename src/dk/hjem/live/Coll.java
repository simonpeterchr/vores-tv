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

/** Samlinger fra Vores TV-serveren: Marvel og Star Wars i historiens rækkefølge og Harry Potter (bedste engelske udgave). */
final class Coll {
    static final class Item { String kind, id, name, img, ext, sub; int n; }
    static final class List1 { String name; List<Item> items = new ArrayList<>(); }

    static volatile List<List1> lists = new ArrayList<>();
    private static volatile long at;
    private static volatile boolean loading;

    /** Hentes i baggrunden (højst hvert 10. minut); Catalog.changed() får skærmen til at tegne igen. */
    static void load(Context ctx) {
        if (loading || System.currentTimeMillis() - at < 10 * 60_000L) return;
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
                JSONArray ls = o.optJSONArray("lists");
                if (ls == null || ls.length() == 0) return; // bygges stadig på serveren – prøv igen senere
                List<List1> out = new ArrayList<>();
                for (int i = 0; i < ls.length(); i++) {
                    JSONObject l = ls.getJSONObject(i);
                    List1 L = new List1();
                    L.name = l.optString("name");
                    JSONArray it = l.optJSONArray("items");
                    for (int j = 0; it != null && j < it.length(); j++) {
                        JSONObject x = it.getJSONObject(j);
                        if (x.optBoolean("missing")) continue;
                        Item e = new Item();
                        e.n = j + 1; e.kind = x.optString("kind"); e.id = x.optString("id"); e.name = x.optString("name");
                        e.img = x.optString("img", null); e.ext = x.optString("ext", null);
                        List<String> tags = new ArrayList<>();
                        tags.add(String.valueOf(x.optInt("year")));
                        if (Vod.SERIES.equals(e.kind)) tags.add("Serie");
                        if ("4K".equals(x.optString("quality"))) tags.add("4K");
                        if (x.optBoolean("surround")) tags.add("5.1");
                        String lang = x.optString("lang");
                        if (lang.startsWith("engelsk, danske")) tags.add("DK-tekst");
                        else if (!"engelsk".equals(lang)) tags.add("m. undertekster");
                        e.sub = android.text.TextUtils.join("  ·  ", tags);
                        L.items.add(e);
                    }
                    if (!L.items.isEmpty()) out.add(L);
                }
                lists = out;
                at = System.currentTimeMillis();
                Catalog.changed();
            } catch (Exception ignored) {
            } finally { loading = false; }
        }, "collections").start();
    }
}
