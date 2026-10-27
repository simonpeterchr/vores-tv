package dk.hjem.live;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Telefon-siden (port 8766): login til udbyderen og valg/rækkefølge af kanaler.
 * Svarer kun enheder på hjemmenettet. Koden vises aldrig igen, når den er gemt.
 */
final class Web {
    static final int PORT = 8766;
    private static Thread thread;
    private static String flash;

    static synchronized void start(Context ctx) {
        if (thread != null && thread.isAlive()) return;
        final Context app = ctx.getApplicationContext();
        thread = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(PORT)) {
                while (true) {
                    Socket s = server.accept();
                    new Thread(() -> handle(app, s), "live-web-req").start();
                }
            } catch (Exception e) {
                Log.w("HjemTV", "Webside stoppet: " + e.getMessage());
            }
        }, "live-web");
        thread.start();
    }

    static String address() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return "http://" + a.getHostAddress() + ":" + PORT;
            }
        } catch (Exception ignored) { }
        return "http://<tv'ets ip>:" + PORT;
    }

    private static void handle(Context c, Socket s) {
        try (Socket sock = s) {
            InetAddress peer = sock.getInetAddress();
            if (!(peer.isSiteLocalAddress() || peer.isLoopbackAddress() || peer.isLinkLocalAddress())) return;
            sock.setSoTimeout(15000);
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
            String line = in.readLine();
            if (line == null) return;
            String[] req = line.split(" ");
            int len = 0;
            while ((line = in.readLine()) != null && !line.isEmpty())
                if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) len = Integer.parseInt(line.substring(15).trim());
            char[] body = new char[Math.min(len, 16384)];
            int got = 0;
            while (got < body.length) { int r = in.read(body, got, body.length - got); if (r < 0) break; got += r; }
            Map<String, String> form = parse(new String(body, 0, got));
            String full = req.length > 1 ? req[1] : "/";
            String path = full.contains("?") ? full.substring(0, full.indexOf('?')) : full;
            Map<String, String> query = parse(full.contains("?") ? full.substring(full.indexOf('?') + 1) : "");
            OutputStream out = sock.getOutputStream();
            if ("GET".equals(req[0]) && path.equals("/hello")) { json(out, 200, Share.hello(c)); return; }
            if ("POST".equals(req[0]) && path.equals("/receive")) {
                // Et andet tv i huset sender sit login. Tages kun imod, hvis dette tv ikke er logget ind.
                if (Cred.load(c).ok()) { json(out, 409, "Tv'et er allerede logget ind."); return; }
                Cred cr = Cred.parse(form.get("server"), form.get("user"), form.get("pass"));
                cr.alts = Cred.parseAlts(form.get("alts"));
                cr.alts.remove(cr.server);
                String err = cr.ok() ? new Xtream(cr).check() : "Mangler oplysninger.";
                if (err != null) outer: for (String alt : new java.util.ArrayList<>(cr.alts)) for (String cand : Health.candidates(alt, cr.server))
                    if (new Xtream(cr.withServer(cand)).check() == null) { cr.alts.remove(alt); cr.alts.add(cr.server); cr.server = cand; err = null; break outer; }
                if (err != null) { json(out, 502, err); return; }
                cr.save(c);
                Catalog.load(c, true);
                Catalog.changed();
                json(out, 200, "Logget ind med login fra " + (form.get("from") != null ? form.get("from") : "et andet tv") + ".");
                return;
            }
            if ("POST".equals(req[0])) {
                String back = form.containsKey("back") && form.get("back").startsWith("/") ? form.get("back") : "/";
                if (path.equals("/login")) {
                    Cred cr = Cred.parse(form.get("server"), form.get("user"), form.get("pass"));
                    String err = cr.ok() ? new Xtream(cr).check() : "Udfyld server, brugernavn og kode – eller indsæt ét M3U-link.";
                    cr.alts = Cred.parseAlts(form.get("alts"));
                    cr.alts.remove(cr.server);
                    if (err != null) outer: for (String alt : new java.util.ArrayList<>(cr.alts)) for (String cand : Health.candidates(alt, cr.server)) { // hovedadressen svarer ikke: prøv reserverne
                        if (new Xtream(cr.withServer(cand)).check() == null) { cr.alts.remove(alt); cr.alts.add(cr.server); cr.server = cand; err = null; break outer; }
                    }
                    if (err == null) {
                        cr.save(c);
                        Catalog.load(c, true);
                        flash = "✅ Logget ind. Vælg nu jeres kanaler herunder.";
                    } else flash = "⚠️ " + err;
                } else if (path.equals("/share")) {
                    String ip = form.get("ip"), name = form.get("name");
                    String res = ip == null ? "Intet tv valgt." : Share.send(c, ip);
                    flash = (res.startsWith("Logget ind") ? "✅ " + (name != null ? name + ": " : "") : "⚠️ " + (name != null ? name + ": " : "")) + res;
                } else if (path.equals("/alts")) {
                    Cred cr = Cred.load(c);
                    cr.alts = Cred.parseAlts(form.get("alts"));
                    cr.alts.remove(cr.server);
                    cr.save(c);
                    flash = cr.alts.isEmpty() ? "Reserve-adresser fjernet." : "✅ " + cr.alts.size() + " reserve-adresse(r) gemt. Appen skifter selv, hvis hovedadressen ikke svarer.";
                    Health.check(c, true);
                } else if (path.equals("/logout")) {
                    Cred.clear(c);
                    Catalog.changed();
                    flash = "Login er fjernet fra tv'et.";
                } else if (path.equals("/fav")) {
                    Catalog.toggleFav(c, num(form.get("id")));
                } else if (path.equals("/group")) {
                    Catalog.toggleGroup(c, form.get("g"));
                } else if (path.equals("/move")) {
                    Catalog.moveFav(c, num(form.get("id")), num(form.get("d")));
                }
                redirect(out, back);
                return;
            }
            String html = Cred.load(c).ok() ? channels(c, query) : login();
            byte[] b = html.getBytes(StandardCharsets.UTF_8);
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: "
                    + b.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(b);
        } catch (Exception e) {
            Log.w("HjemTV", "Webside-fejl: " + e.getMessage());
        }
    }

    private static void json(OutputStream out, int code, String body) throws Exception {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 " + code + " X\r\nContent-Type: " + (body.startsWith("{") ? "application/json" : "text/plain") + "; charset=utf-8\r\nContent-Length: "
                + b.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(b);
    }

    private static void redirect(OutputStream out, String to) throws Exception {
        out.write(("HTTP/1.1 303 See Other\r\nLocation: " + to + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
    }

    private static int num(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }

    private static Map<String, String> parse(String body) {
        Map<String, String> m = new HashMap<>();
        for (String kv : body.split("&")) {
            int eq = kv.indexOf('=');
            if (eq <= 0) continue;
            try { m.put(kv.substring(0, eq), URLDecoder.decode(kv.substring(eq + 1), "UTF-8")); } catch (Exception ignored) { }
        }
        return m;
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String head(String title) {
        String msg = flash;
        flash = null;
        return "<!doctype html><html lang=da><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
            + "<title>" + esc(title) + "</title><style>"
            + ":root{color-scheme:dark}body{margin:0;background:#0a0a0c;color:#fff;font:16px/1.4 -apple-system,system-ui,sans-serif}"
            + "main{max-width:600px;margin:0 auto;padding:28px 16px 64px}h1{font-size:28px;margin:0 0 4px}h2{font-size:18px;margin:28px 0 10px}"
            + "p{color:#aaa;margin:0 0 18px}.msg{padding:12px 14px;border-radius:12px;background:#1d2a20;margin-bottom:18px}"
            + "label{display:block;font-weight:600;margin:14px 0 6px}input,select,textarea{width:100%;box-sizing:border-box;font:inherit;padding:12px;border-radius:10px;"
            + "border:1px solid #3a3b40;background:#17181c;color:#fff}button{font:inherit;border:0;border-radius:10px;padding:10px 14px;min-height:44px;cursor:pointer}"
            + ".main{margin-top:18px;width:100%;background:#fff;color:#000;font-weight:600}.or{text-align:center;color:#777;margin:18px 0 0}"
            + "ul{list-style:none;padding:0;margin:0;border-radius:16px;overflow:hidden;background:#17181c}"
            + "li{display:flex;align-items:center;gap:10px;padding:8px 12px;border-top:1px solid #26272c}li:first-child{border-top:0}"
            + "li img{width:44px;height:30px;object-fit:contain;background:#000;border-radius:6px}li .n{flex:1;min-width:0}li .n b{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}"
            + "li .n small{color:#888}form{margin:0}.star{background:#2c2c30;color:#fff;min-width:44px}.star.on{background:#ffd60a;color:#000}"
            + ".ic{background:#2c2c30;color:#ddd;min-width:40px;padding:8px}.empty{color:#888;padding:14px}.bar{display:flex;gap:8px}.bar>*{flex:1}"
            + ".out{background:transparent;color:#ff6b6b;margin-top:30px}</style></head><body><main>"
            + (msg != null ? "<div class=msg role=status>" + esc(msg) + "</div>" : "");
    }

    private static String login() {
        return head("Vores TV – login")
            + "<h1>Vores TV</h1><p>Log ind med jeres IPTV-abonnement. Oplysningerne gemmes kun på tv'et.</p>"
            + "<form method=post action=/login autocomplete=off>"
            + "<label for=server>Server (eller hele M3U-linket)</label><input id=server name=server inputmode=url placeholder='http://server.dk:8080' required>"
            + "<label for=user>Brugernavn</label><input id=user name=user autocapitalize=off>"
            + "<label for=pass>Kode</label><input id=pass name=pass type=password autocapitalize=off>"
            + "<label for=alts>Reserve-adresser (valgfrit)</label><textarea id=alts name=alts rows=3 placeholder='Indsæt udbyderens tekst, fx Alternative URL 1 = http://…'></textarea>"
            + "<button class=main>Log ind</button></form>"
            + "<p class=or>Indsætter du et M3U-link (…get.php?username=…&amp;password=…), kan brugernavn og kode stå tomme.</p>"
            + "</main></body></html>";
    }

    private static String row(Catalog.Ch ch, boolean fav, String back, boolean ordering, String sub) {
        StringBuilder b = new StringBuilder("<li>");
        b.append("<img alt='' loading=lazy src=\"").append(esc(ch.logo)).append("\" onerror=\"this.style.visibility='hidden'\">")
         .append("<div class=n><b>").append(esc(ch.name)).append("</b>");
        if (sub != null) b.append("<small>").append(esc(sub)).append("</small>");
        b.append("</div>");
        if (ordering) {
            b.append(btn("/move", ch.id, "&d=-1", back, "ic", "↑", "Flyt op"))
             .append(btn("/move", ch.id, "&d=1", back, "ic", "↓", "Flyt ned"));
        }
        b.append(btn("/fav", ch.id, "", back, fav ? "star on" : "star", fav ? "★" : "☆", fav ? "Fjern fra jeres kanaler" : "Tilføj til jeres kanaler"));
        return b.append("</li>").toString();
    }

    private static String btn(String action, int id, String extra, String back, String cls, String label, String aria) {
        StringBuilder f = new StringBuilder("<form method=post action=").append(action).append("><input type=hidden name=id value=").append(id).append(">");
        if (!extra.isEmpty()) for (String kv : extra.substring(1).split("&")) {
            String[] p = kv.split("=");
            f.append("<input type=hidden name=").append(p[0]).append(" value=").append(p[1]).append(">");
        }
        return f.append("<input type=hidden name=back value=\"").append(esc(back)).append("\"><button class='").append(cls)
                .append("' aria-label='").append(aria).append("'>").append(label).append("</button></form>").toString();
    }

    private static String channels(Context c, Map<String, String> q) {
        String query = q.containsKey("q") ? q.get("q").trim() : "", cat = q.containsKey("cat") ? q.get("cat") : "";
        String back = "/?q=" + Xtream.enc(query) + "&cat=" + Xtream.enc(cat);
        StringBuilder h = new StringBuilder(head("Vores TV – kanaler"));
        h.append("<h1>Jeres kanaler</h1><p>Det er kun disse, der vises på tv'et. Rækkefølgen her er rækkefølgen ved zapping.</p>");
        if (!Catalog.loaded) {
            h.append("<p>").append(Catalog.error != null ? esc(Catalog.error) : "Henter kanaler fra udbyderen … opdatér siden om lidt.").append("</p>");
        }
        List<Catalog.Ch> favs = Catalog.favorites(c);
        h.append("<ul>");
        for (Catalog.Ch ch : favs) h.append(row(ch, true, back, true, null));
        if (favs.isEmpty()) h.append("<li class=empty>Ingen endnu – find dem herunder og tryk ☆.</li>");
        h.append("</ul><h2>Find kanaler</h2><form method=get action=/ class=bar><input name=q type=search placeholder='Søg, fx DR1 eller TV 2' value=\"")
         .append(esc(query)).append("\"><select name=cat onchange='this.form.submit()'><option value=''>Alle grupper</option>");
        for (Catalog.Cat k : Catalog.cats.values()) {
            if (k.count == 0) continue;
            h.append("<option value=\"").append(esc(k.id)).append("\"").append(k.id.equals(cat) ? " selected" : "").append(">")
             .append(esc(k.prefix.isEmpty() ? "" : k.prefix + " · ")).append(esc(k.name)).append(" (").append(k.count).append(")</option>");
        }
        h.append("</select></form>");
        if (!cat.isEmpty() && Catalog.cats.containsKey(cat)) {
            boolean on = Catalog.isShown(c, cat);
            h.append("<form method=post action=/group style='margin-top:10px'><input type=hidden name=g value=\"").append(esc(cat))
             .append("\"><input type=hidden name=back value=\"").append(esc(back)).append("\"><button class='").append(on ? "star on" : "star")
             .append("' style='width:100%'>").append(on ? "✓ Gruppen vises på tv'et – tryk for at skjule" : "Vis gruppen på tv'et").append("</button></form>");
        }
        List<String> shownG = Catalog.shownGroups(c);
        h.append("<p style='margin-top:14px'>Grupper på tv'et: ");
        for (int i = 0; i < shownG.size(); i++) { Catalog.Cat k = Catalog.cats.get(shownG.get(i)); if (k != null) h.append(i > 0 ? ", " : "").append(esc(k.name)); }
        if (shownG.isEmpty()) h.append("ingen");
        h.append("</p><ul style='margin-top:12px'>");
        int shown = 0;
        if (!query.isEmpty() || !cat.isEmpty()) {
            String ql = query.toLowerCase(Locale.ROOT);
            for (Catalog.Ch ch : Catalog.channels) {
                if (!cat.isEmpty() && !cat.equals(ch.cat)) continue;
                if (!ql.isEmpty() && !ch.name.toLowerCase(Locale.ROOT).contains(ql) && !ch.raw.toLowerCase(Locale.ROOT).contains(ql)) continue;
                Catalog.Cat k = Catalog.cats.get(ch.cat);
                h.append(row(ch, Catalog.isFav(c, ch.id), back, false, k != null ? (k.prefix.isEmpty() ? "" : k.prefix + " · ") + k.name : null));
                if (++shown >= 300) break;
            }
            if (shown == 0) h.append("<li class=empty>Ingen kanaler fundet.</li>");
        } else h.append("<li class=empty>Søg efter et navn eller vælg en gruppe.</li>");
        Cred cur = Cred.load(c);
        h.append("<h2>Del login med andre tv'er</h2><p>Andre tv'er i huset med Vores TV, som ikke er logget ind, kan få dette tv's login med ét tryk.</p>");
        if (q.containsKey("find")) {
            List<Share.Peer> peers = Share.scan();
            h.append("<ul>");
            for (Share.Peer p : peers) {
                h.append("<li><div class=n><b>").append(esc(p.name)).append("</b><small>").append(p.loggedIn ? "Allerede logget ind" : "Mangler login").append("</small></div>");
                if (!p.loggedIn) h.append("<form method=post action=/share><input type=hidden name=ip value=\"").append(esc(p.ip)).append("\"><input type=hidden name=name value=\"")
                        .append(esc(p.name)).append("\"><input type=hidden name=back value='/'><button class='star on'>Send login</button></form>");
                h.append("</li>");
            }
            if (peers.isEmpty()) h.append("<li class=empty>Fandt ingen andre tv'er med Vores TV åben. Åbn Vores TV på det andet tv, og prøv igen.</li>");
            h.append("</ul>");
        }
        h.append("<form method=get action=/ style='margin-top:12px'><input type=hidden name=find value=1><button class=main>Find tv'er i huset</button></form>");
        h.append("</ul><h2>Reserve-adresser</h2><p>Svarer udbyderen ikke, prøver tv'et selv disse med samme login. Indsæt bare udbyderens tekst.</p>")
         .append("<form method=post action=/alts><textarea id=alts name=alts rows=3 style='width:100%;box-sizing:border-box;font:inherit;padding:12px;border-radius:10px;border:1px solid #3a3b40;background:#17181c;color:#fff'>")
         .append(esc(android.text.TextUtils.join("\n", cur.alts))).append("</textarea><button class=main>Gem reserve-adresser</button></form>");
        h.append("<form method=post action=/logout onsubmit=\"return confirm('Fjern login fra tv\\'et?')\"><button class=out>Log ud af udbyderen</button></form>");
        return h.append("</main></body></html>").toString();
    }
}
