package dk.hjem.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/** Login til IPTV-udbyderen (Xtream Codes). Gemmes kun lokalt på enheden. */
final class Cred {
    String server = "", user = "", pass = "";
    /** Reserve-adresser fra udbyderen (samme login), prøves når hovedadressen ikke svarer. */
    java.util.List<String> alts = new java.util.ArrayList<>();

    boolean ok() { return !server.isEmpty() && !user.isEmpty() && !pass.isEmpty(); }

    static Cred load(Context c) {
        SharedPreferences p = c.getSharedPreferences("cred", Context.MODE_PRIVATE);
        Cred r = new Cred();
        r.server = p.getString("server", "");
        r.user = p.getString("user", "");
        r.pass = p.getString("pass", "");
        for (String s : p.getString("alts", "").split("\n")) if (!s.trim().isEmpty()) r.alts.add(s.trim());
        return r;
    }

    void save(Context c) {
        c.getSharedPreferences("cred", Context.MODE_PRIVATE).edit()
                .putString("server", server).putString("user", user).putString("pass", pass)
                .putString("alts", android.text.TextUtils.join("\n", alts)).apply();
    }

    static void clear(Context c) {
        c.getSharedPreferences("cred", Context.MODE_PRIVATE).edit().clear().apply();
    }

    /** Tager enten tre felter eller ét M3U-link (…/get.php?username=…&password=…) og finder server, bruger og kode. */
    static Cred parse(String server, String user, String pass) {
        Cred r = new Cred();
        server = server == null ? "" : server.trim();
        if (server.contains("username=") && server.contains("password=")) {
            Uri u = Uri.parse(server.startsWith("http") ? server : "http://" + server);
            r.server = u.getScheme() + "://" + u.getAuthority();
            r.user = nz(u.getQueryParameter("username"));
            r.pass = nz(u.getQueryParameter("password"));
            return r;
        }
        if (!server.isEmpty() && !server.startsWith("http")) server = "http://" + server;
        while (server.endsWith("/")) server = server.substring(0, server.length() - 1);
        server = server.replaceAll("/(player_api|get)\\.php.*$", "");
        r.server = server;
        r.user = user == null ? "" : user.trim();
        r.pass = pass == null ? "" : pass.trim();
        return r;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /** Finder alle adresser i fri tekst, fx "Alternative URL 1 = http://a.xyz || Alternative URL 2 = http://b.xyz". */
    static java.util.List<String> parseAlts(String text) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null) return out;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)\\bhttps?://[a-z0-9.-]+(:\\d+)?").matcher(text);
        while (m.find()) { String u = m.group().toLowerCase(java.util.Locale.ROOT); if (!out.contains(u)) out.add(u); }
        return out;
    }

    /** Samme login på en anden adresse. */
    Cred withServer(String s) {
        Cred c = new Cred();
        c.server = s; c.user = user; c.pass = pass;
        return c;
    }
}
