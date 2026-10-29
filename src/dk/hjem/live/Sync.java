package dk.hjem.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deler indstillinger med Vores TV web på hjemmenettet (husstandens bruger): favoritter, viste grupper,
 * senest sete, film/serie-grupper, sport-favoritter og "fortsæt". Felt for felt vinder den nyeste ændring.
 * Uden en webserver på nettet sker der ingenting.
 */
final class Sync {
    private static final String[] FIELDS = {"favs", "groups", "history", "vodGroups", "sport"};
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean running, applying;
    private static volatile long lastRun, lastScan;
    private static Context app;

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("sync", Context.MODE_PRIVATE); }

    /** Kaldes når et felt ændres her på tv'et. */
    static void touch(Context c, String field) {
        if (applying) return;
        sp(c).edit().putLong("m_" + field, System.currentTimeMillis()).apply();
        later(c, 2000);
    }

    /** Fortsæt-listen ændres ofte (hvert 15. sekund under afspilning) – synk sjældnere. */
    static void resumeChanged(Context c) { if (!applying) later(c, 60_000); }

    private static final Runnable RUN = () -> now(app, false);
    private static void later(Context c, long ms) {
        app = c.getApplicationContext();
        MAIN.removeCallbacks(RUN);
        MAIN.postDelayed(RUN, ms);
    }

    /** Ved opstart og jævnligt: højst hvert 5. minut, medmindre force. */
    static void now(Context ctx, boolean force) {
        if (ctx == null || running || (!force && System.currentTimeMillis() - lastRun < 20_000)) return;
        final Context c = ctx.getApplicationContext();
        app = c;
        running = true;
        Bg.run("sync", () -> {
            try { run(c); } catch (Exception ignored) { } finally { running = false; lastRun = System.currentTimeMillis(); }
        });
    }

    static void periodic(Context c) { if (System.currentTimeMillis() - lastRun > 5 * 60_000L) now(c, false); }

    private static String server(Context c) {
        SharedPreferences p = sp(c);
        String ip = p.getString("server", null);
        if (ip != null && isWeb(ip)) return ip;
        if (System.currentTimeMillis() - lastScan < 30 * 60_000L) return null; // led ikke hele tiden
        lastScan = System.currentTimeMillis();
        for (Share.Peer peer : Share.scan()) if (peer.web) { p.edit().putString("server", peer.ip).apply(); return peer.ip; }
        return null;
    }

    private static boolean isWeb(String ip) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL("http://" + ip + ":" + Web.PORT + "/hello").openConnection();
            con.setConnectTimeout(1500); con.setReadTimeout(3000);
            if (con.getResponseCode() != 200) return false;
            byte[] b = new byte[2048];
            int n = con.getInputStream().read(b);
            return new JSONObject(new String(b, 0, Math.max(0, n), StandardCharsets.UTF_8)).optBoolean("web");
        } catch (Exception e) { return false; }
    }

    private static void run(Context c) throws Exception {
        String ip = server(c);
        if (ip == null) return;
        SharedPreferences s = sp(c);
        boolean first = !s.contains("synced");
        JSONObject state = local(c), mtime = new JSONObject();
        for (String f : FIELDS) {
            long m = s.getLong("m_" + f, 0);
            if (m == 0 && first) m = 1; // første gang: tv'ets indstillinger fylder serveren, men taber til alt der er ændret dér
            mtime.put(f, m);
        }
        JSONObject body = new JSONObject().put("state", state).put("mtime", mtime);
        HttpURLConnection con = (HttpURLConnection) new URL("http://" + ip + ":" + Web.PORT + "/api/sync").openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setConnectTimeout(3000); con.setReadTimeout(20000);
        con.setRequestProperty("Content-Type", "application/json");
        try (OutputStream o = con.getOutputStream()) { o.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        if (con.getResponseCode() != 200) { s.edit().remove("server").apply(); return; }
        String resp;
        try (java.io.InputStream in = con.getInputStream()) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            resp = new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }
        JSONObject r = new JSONObject(resp);
        apply(c, r.getJSONObject("state"), r.getJSONObject("mtime"));
        s.edit().putLong("synced", System.currentTimeMillis()).apply();
    }

    // ---- tv'ets indstillinger → JSON (samme form som webserveren) ----
    private static SharedPreferences pref(Context c, String n) { return c.getSharedPreferences(n, Context.MODE_PRIVATE); }

    private static JSONArray ints(String csv) {
        JSONArray a = new JSONArray();
        if (csv != null) for (String x : csv.split(",")) if (!x.isEmpty()) try { a.put(Integer.parseInt(x)); } catch (NumberFormatException ignored) { }
        return a;
    }
    private static JSONArray strs(String s, String sep) {
        JSONArray a = new JSONArray();
        if (s != null) for (String x : s.split(sep)) if (!x.trim().isEmpty()) a.put(x.trim());
        return a;
    }
    private static String join(JSONArray a, String sep) {
        StringBuilder b = new StringBuilder();
        if (a != null) for (int i = 0; i < a.length(); i++) { if (b.length() > 0) b.append(sep); b.append(a.opt(i)); }
        return b.toString();
    }

    private static JSONObject local(Context c) throws Exception {
        SharedPreferences live = pref(c, "live"), vod = pref(c, "vod"), sport = pref(c, "sport"), res = pref(c, "resume");
        JSONObject o = new JSONObject();
        o.put("favs", ints(live.getString("favs", "")));
        String g = live.getString("groups", null);
        o.put("groups", g == null ? JSONObject.NULL : strs(g, ","));
        o.put("history", ints(live.getString("history", "")));
        JSONObject vg = new JSONObject();
        for (String k : new String[]{Vod.MOVIE, Vod.SERIES}) { String v = vod.getString("groups_" + k, null); vg.put(k, v == null ? JSONObject.NULL : strs(v, ",")); }
        o.put("vodGroups", vg);
        o.put("sport", new JSONObject()
                .put("teams", strs(sport.getString("teams", Sports.DEFAULT_TEAMS), "[\\n,]"))
                .put("leagues", strs(sport.getString("leagues", Sports.DEFAULT_LEAGUES), "[\\n,]"))
                .put("sports", strs(sport.getString("sports", Sports.DEFAULT_SPORTS), "[\\n,]"))
                .put("spoiler", sport.getBoolean("spoiler", false)));
        JSONObject resume = new JSONObject(), done = new JSONObject();
        for (Map.Entry<String, ?> e : res.getAll().entrySet()) {
            if (e.getKey().startsWith("done:")) { done.put(e.getKey().substring(5), 1); continue; }
            try { resume.put(e.getKey(), new JSONObject(String.valueOf(e.getValue()))); } catch (Exception ignored) { }
        }
        o.put("resume", resume).put("done", done);
        return o;
    }

    // ---- serverens svar → tv'ets indstillinger (kun felter der er nyere dér) ----
    private static void apply(Context c, JSONObject st, JSONObject mt) throws Exception {
        SharedPreferences s = sp(c);
        SharedPreferences.Editor se = s.edit();
        SharedPreferences.Editor live = pref(c, "live").edit(), vod = pref(c, "vod").edit(), sport = pref(c, "sport").edit(), res = pref(c, "resume").edit();
        boolean changed = false;
        applying = true;
        try {
            for (String f : FIELDS) {
                long m = mt.optLong(f, 0);
                if (m <= s.getLong("m_" + f, 0) || !st.has(f)) continue;
                Object v = st.get(f);
                switch (f) {
                    case "favs": live.putString("favs", join(st.optJSONArray(f), ",")); break;
                    case "groups": if (v == JSONObject.NULL) live.remove("groups"); else live.putString("groups", join((JSONArray) v, ",")); break;
                    case "history": {
                        JSONArray h = st.optJSONArray(f);
                        live.putString("history", join(h, ","));
                        if (h != null && h.length() > 0) live.putInt("last", h.optInt(0));
                        break;
                    }
                    case "vodGroups": {
                        JSONObject vg = st.optJSONObject(f);
                        if (vg != null) for (String k : new String[]{Vod.MOVIE, Vod.SERIES}) {
                            if (!vg.has(k) || vg.isNull(k)) vod.remove("groups_" + k); else vod.putString("groups_" + k, join(vg.optJSONArray(k), ","));
                        }
                        break;
                    }
                    case "sport": {
                        JSONObject so = st.optJSONObject(f);
                        if (so == null) break;
                        if (so.has("teams")) sport.putString("teams", join(so.optJSONArray("teams"), "\n"));
                        if (so.has("leagues")) sport.putString("leagues", join(so.optJSONArray("leagues"), "\n"));
                        if (so.has("sports")) sport.putString("sports", join(so.optJSONArray("sports"), "\n"));
                        if (so.has("spoiler")) sport.putBoolean("spoiler", so.optBoolean("spoiler"));
                        break;
                    }
                }
                se.putLong("m_" + f, m);
                changed = true;
            }
            // fortsæt: serverens liste er allerede flettet med vores
            SharedPreferences rp = pref(c, "resume");
            JSONObject resume = st.optJSONObject("resume"), done = st.optJSONObject("done");
            if (done != null) for (java.util.Iterator<String> it = done.keys(); it.hasNext(); ) {
                String k = it.next();
                if (!rp.contains("done:" + k)) { res.putString("done:" + k, "1"); res.remove(k); changed = true; }
            }
            if (resume != null) for (java.util.Iterator<String> it = resume.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONObject r = resume.getJSONObject(k);
                String mine = rp.getString(k, null);
                long myWhen = 0;
                try { if (mine != null) myWhen = new JSONObject(mine).optLong("when"); } catch (Exception ignored) { }
                if (r.optLong("when") > myWhen) { res.putString(k, r.toString()); changed = true; }
            }
            live.apply(); vod.apply(); sport.apply(); res.apply(); se.apply();
        } finally { applying = false; }
        if (changed) { Sports.refresh(c); Catalog.changed(); }
    }

    static List<String> fields() { List<String> l = new ArrayList<>(); for (String f : FIELDS) l.add(f); return l; }
}
