package dk.hjem.live;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Xtream Codes player_api: kanaler, film, serier og kort programguide. */
final class Xtream {
    final Cred c;

    Xtream(Cred c) { this.c = c; }

    static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    String api(String action, String extra) {
        String u = c.server + "/player_api.php?username=" + enc(c.user) + "&password=" + enc(c.pass);
        if (action != null) u += "&action=" + action;
        if (extra != null) u += extra;
        return u;
    }

    String get(String action, String extra) throws Exception { return Http.text(api(action, extra)); }

    /** Tjekker login; returnerer null hvis det er godkendt, ellers en fejltekst til brugeren. */
    String check() {
        try {
            JSONObject o = new JSONObject(get(null, null));
            JSONObject ui = o.optJSONObject("user_info");
            if (ui == null || ui.optInt("auth", 0) != 1) return "Udbyderen afviste brugernavn eller kode.";
            String st = ui.optString("status", "");
            if (!st.isEmpty() && !st.equalsIgnoreCase("Active")) return "Abonnementet står som \"" + st + "\".";
            return null;
        } catch (Exception e) {
            return "Kunne ikke kontakte serveren (" + e.getMessage() + ").";
        }
    }

    String liveUrl(int id, boolean hls) {
        return c.server + "/live/" + c.user + "/" + c.pass + "/" + id + (hls ? ".m3u8" : ".ts");
    }

    String vodUrl(int id, String ext) {
        return c.server + "/movie/" + c.user + "/" + c.pass + "/" + id + "." + (ext == null || ext.isEmpty() ? "mp4" : ext);
    }

    String episodeUrl(String id, String ext) {
        return c.server + "/series/" + c.user + "/" + c.pass + "/" + id + "." + (ext == null || ext.isEmpty() ? "mp4" : ext);
    }

    static final class Prog {
        String title, desc;
        long start, stop; // ms

        boolean now(long t) { return t >= start && t < stop; }
        float progress(long t) { return stop > start ? Math.max(0f, Math.min(1f, (t - start) / (float) (stop - start))) : 0f; }
    }

    List<Prog> shortEpg(int streamId, int limit) {
        List<Prog> out = new ArrayList<>();
        try {
            JSONObject o = new JSONObject(get("get_short_epg", "&stream_id=" + streamId + "&limit=" + limit));
            JSONArray a = o.optJSONArray("epg_listings");
            if (a == null) return out;
            for (int i = 0; i < a.length(); i++) {
                JSONObject e = a.getJSONObject(i);
                Prog p = new Prog();
                p.title = b64(e.optString("title"));
                p.desc = b64(e.optString("description"));
                // Udbyderens tidsstempler ligger en time forkert; tekst-tiderne ("2026-10-27 19:00:00") er dansk tid.
                p.start = local(e.optString("start"), e.optLong("start_timestamp") * 1000);
                p.stop = local(e.optString("end"), e.optLong("stop_timestamp") * 1000);
                if (p.start > 0 && p.stop > p.start) out.add(p);
            }
        } catch (Exception ignored) { }
        // Sortér og fjern overlap (udbyderen sender af og til dubletter med andre tider).
        java.util.Collections.sort(out, (a, b) -> Long.compare(a.start, b.start));
        List<Prog> clean = new ArrayList<>();
        for (Prog p : out) {
            if (!clean.isEmpty()) {
                Prog last = clean.get(clean.size() - 1);
                if (p.start < last.stop) {
                    if (p.title.equals(last.title) || p.stop <= last.stop) continue;
                    p.start = last.stop;
                }
            }
            if (p.stop > p.start) clean.add(p);
        }
        return clean;
    }

    private static final java.text.SimpleDateFormat LOCAL = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT);

    static synchronized long local(String s, long fallback) {
        if (s == null || s.length() < 19) return fallback;
        try { return LOCAL.parse(s.substring(0, 19)).getTime(); } catch (Exception e) { return fallback; }
    }

    static String b64(String s) {
        if (s == null || s.isEmpty()) return "";
        try { return new String(Base64.decode(s, Base64.DEFAULT), StandardCharsets.UTF_8).trim(); }
        catch (Exception e) { return s; }
    }
}
