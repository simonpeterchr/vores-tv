package dk.hjem.live;

import android.content.Context;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Del login mellem tv'er i huset: find andre Vores TV på hjemmenettet og send login til dem, der mangler. */
final class Share {
    static final class Peer { String ip, name; boolean loggedIn, web; }

    static String deviceName(Context c) {
        String n = Settings.Global.getString(c.getContentResolver(), "device_name");
        return n == null || n.isEmpty() ? android.os.Build.MODEL : n;
    }

    /** Svar på /hello: navn og om tv'et er logget ind (aldrig selve loginet). */
    static String hello(Context c) {
        try { return new JSONObject().put("app", "vorestv").put("name", deviceName(c)).put("in", Cred.load(c).ok()).toString(); }
        catch (Exception e) { return "{}"; }
    }

    private static String myIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Spørger alle adresser i hjemmenettet (/24) på port 8766 – tager et par sekunder. */
    static List<Peer> scan() {
        final List<Peer> out = Collections.synchronizedList(new ArrayList<>());
        String me = myIp();
        if (me == null) return out;
        String base = me.substring(0, me.lastIndexOf('.') + 1);
        ExecutorService pool = Executors.newFixedThreadPool(32);
        for (int i = 1; i < 255; i++) {
            final String ip = base + i;
            if (ip.equals(me)) continue;
            pool.execute(() -> {
                try {
                    HttpURLConnection con = (HttpURLConnection) new URL("http://" + ip + ":" + Web.PORT + "/hello").openConnection();
                    con.setConnectTimeout(600);
                    con.setReadTimeout(1500);
                    if (con.getResponseCode() != 200) return;
                    byte[] b = new byte[2048];
                    int n = con.getInputStream().read(b);
                    JSONObject o = new JSONObject(new String(b, 0, Math.max(0, n), StandardCharsets.UTF_8));
                    if (!"vorestv".equals(o.optString("app"))) return;
                    Peer p = new Peer();
                    p.ip = ip; p.name = o.optString("name", ip); p.loggedIn = o.optBoolean("in"); p.web = o.optBoolean("web");
                    out.add(p);
                } catch (Exception ignored) { }
            });
        }
        pool.shutdown();
        try { pool.awaitTermination(8, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        List<Peer> l = new ArrayList<>(out);
        Collections.sort(l, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return l;
    }

    /** Sender dette tv's login (og reserve-adresser) til et andet tv i huset. Returnerer svaret som tekst. */
    static String send(Context c, String ip) {
        Cred cr = Cred.load(c);
        if (!cr.ok()) return "Dette tv er ikke logget ind.";
        if (!ip.matches("^(10|192\\.168|172\\.(1[6-9]|2\\d|3[01]))\\.[0-9.]+$")) return "Kun tv'er på hjemmenettet.";
        try {
            String body = "server=" + enc(cr.server) + "&user=" + enc(cr.user) + "&pass=" + enc(cr.pass)
                    + "&alts=" + enc(android.text.TextUtils.join("\n", cr.alts)) + "&from=" + enc(deviceName(c));
            HttpURLConnection con = (HttpURLConnection) new URL("http://" + ip + ":" + Web.PORT + "/receive").openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setConnectTimeout(3000);
            con.setReadTimeout(30000);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream o = con.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
            int code = con.getResponseCode();
            byte[] b = new byte[1024];
            int n = (code < 400 ? con.getInputStream() : con.getErrorStream()).read(b);
            return new String(b, 0, Math.max(0, n), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Kunne ikke nå tv'et.";
        }
    }

    private static String enc(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return ""; } }
}
