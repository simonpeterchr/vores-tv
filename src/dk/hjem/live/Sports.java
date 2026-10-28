package dk.hjem.live;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Kampprogram, resultater og tabeller fra ESPN's åbne sportsdata (ingen nøgle). */
final class Sports {
    static final class League { final String code, name; League(String c, String n) { code = c; name = n; } }

    /** Ligaer vi henter (jeres hold spiller også i pokal og Europa, så de er med for at finde deres kampe). */
    static final League[] LEAGUES = {
            new League("den.1", "Superligaen"), new League("eng.1", "Premier League"), new League("ger.1", "Bundesliga"),
            new League("esp.1", "La Liga"), new League("eng.2", "Championship"), new League("ger.2", "2. Bundesliga"),
            new League("uefa.champions", "Champions League"), new League("uefa.europa", "Europa League"),
            new League("uefa.europa.conf", "Conference League"), new League("ita.1", "Serie A"), new League("fra.1", "Ligue 1"),
            new League("den.cup", "Pokalen"), new League("eng.fa", "FA Cup"), new League("esp.copa_del_rey", "Copa del Rey"),
            new League("ger.dfb_pokal", "DFB-Pokal"), new League("eng.league_cup", "Carabao Cup"),
    };

    static final class Team { String name, shortName, abbr, logo; int score = -1; }

    static final class Game {
        String id, league, leagueName;
        long start;
        String state = "pre", detail = "", clock = ""; // pre | in | post
        Team home = new Team(), away = new Team();
        boolean live() { return "in".equals(state); }
        boolean done() { return "post".equals(state); }
    }

    static final Map<String, Game> games = new ConcurrentHashMap<>();
    private static final Map<String, Long> fetched = new ConcurrentHashMap<>();
    private static final ExecutorService pool = Executors.newFixedThreadPool(4);
    static final String API = "https://site.api.espn.com/apis/site/v2/sports/soccer/";

    static League league(String code) { for (League l : LEAGUES) if (l.code.equals(code)) return l; return null; }

    // ---- favoritter ----
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("sport", Context.MODE_PRIVATE); }
    static final String DEFAULT_TEAMS = "Sønderjyske\nBarcelona\nWrexham\nHamburg", DEFAULT_LEAGUES = "den.1\neng.1\nger.1";

    static List<String> favTeams(Context c) { return lines(prefs(c).getString("teams", DEFAULT_TEAMS)); }
    static List<String> favLeagues(Context c) { return lines(prefs(c).getString("leagues", DEFAULT_LEAGUES)); }
    static void setFavTeams(Context c, String v) { prefs(c).edit().putString("teams", v).apply(); Catalog.changed(); }
    static void setFavLeagues(Context c, List<String> v) { prefs(c).edit().putString("leagues", android.text.TextUtils.join("\n", v)).apply(); Catalog.changed(); }
    static boolean spoilerFree(Context c) { return prefs(c).getBoolean("spoiler", false); }
    static void setSpoilerFree(Context c, boolean v) { prefs(c).edit().putBoolean("spoiler", v).apply(); Catalog.changed(); }

    private static List<String> lines(String s) {
        List<String> out = new ArrayList<>();
        for (String x : s.split("[\\n,]")) if (!x.trim().isEmpty()) out.add(x.trim());
        return out;
    }

    static boolean isFavTeam(Context c, Team t) {
        String n = Search.norm(t.name + " " + t.shortName);
        for (String f : favTeams(c)) if (n.contains(Search.norm(f))) return true;
        return false;
    }

    static boolean mine(Context c, Game g) { return isFavTeam(c, g.home) || isFavTeam(c, g.away) || favLeagues(c).contains(g.league); }

    // ---- hentning: én dag ad gangen pr. liga ----
    private static String day(int offset) {
        Calendar k = Calendar.getInstance();
        k.add(Calendar.DAY_OF_YEAR, offset);
        return new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(k.getTime());
    }

    /** Henter i dag (oftere) og de næste 6 dage (sjældnere). */
    static void refresh() {
        for (League l : LEAGUES) for (int i = -1; i <= 6; i++) {
            final String key = l.code + "|" + day(i);
            Long f = fetched.get(key);
            long maxAge = i == 0 ? 60_000L : i < 0 ? 6 * 3600_000L : 3 * 3600_000L;
            if (f != null && System.currentTimeMillis() - f < maxAge) continue;
            fetched.put(key, System.currentTimeMillis());
            final League lg = l;
            final String d = day(i);
            pool.execute(() -> {
                try { parse(lg, new JSONObject(Http.text(API + lg.code + "/scoreboard?dates=" + d))); Catalog.changed(); }
                catch (Exception e) { fetched.remove(key); }
            });
        }
    }

    private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT);
    static { ISO.setTimeZone(TimeZone.getTimeZone("UTC")); }

    private static synchronized long iso(String s) {
        try { return ISO.parse(s.substring(0, 16)).getTime(); } catch (Exception e) { return 0; }
    }

    private static void parse(League lg, JSONObject o) throws Exception {
        JSONArray ev = o.optJSONArray("events");
        if (ev == null) return;
        for (int i = 0; i < ev.length(); i++) {
            JSONObject e = ev.getJSONObject(i);
            Game g = new Game();
            g.id = e.optString("id");
            g.league = lg.code;
            g.leagueName = lg.name;
            g.start = iso(e.optString("date"));
            JSONObject comp = e.optJSONArray("competitions").optJSONObject(0);
            JSONObject st = (comp != null && comp.has("status") ? comp : e).optJSONObject("status");
            if (st != null) {
                JSONObject t = st.optJSONObject("type");
                if (t != null) { g.state = t.optString("state", "pre"); g.detail = t.optString("shortDetail", ""); }
                g.clock = st.optString("displayClock", "");
            }
            JSONArray cs = comp != null ? comp.optJSONArray("competitors") : null;
            if (cs != null) for (int k = 0; k < cs.length(); k++) {
                JSONObject c = cs.getJSONObject(k);
                JSONObject tm = c.optJSONObject("team");
                Team t = "home".equals(c.optString("homeAway")) ? g.home : g.away;
                if (tm != null) {
                    t.name = tm.optString("displayName"); t.shortName = tm.optString("shortDisplayName", t.name);
                    t.abbr = tm.optString("abbreviation"); t.logo = tm.optString("logo");
                }
                try { t.score = Integer.parseInt(c.optString("score", "")); } catch (Exception ignored) { }
            }
            games.put(g.id + "|" + g.league, g);
        }
    }

    /** Kampe sorteret: i gang først, så kommende efter tid, så afsluttede (seneste først). */
    static List<Game> list(Context c, boolean onlyMine) {
        List<Game> out = new ArrayList<>();
        long from = System.currentTimeMillis() - 24 * 3600_000L;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Game g : games.values()) {
            if (g.start < from) continue;
            if (onlyMine && !mine(c, g)) continue;
            if (!seen.add(g.home.name + "-" + g.away.name + "@" + g.start)) continue; // samme kamp i to "ligaer"
            out.add(g);
        }
        Collections.sort(out, (a, b) -> {
            int ra = a.live() ? 0 : a.done() ? 2 : 1, rb = b.live() ? 0 : b.done() ? 2 : 1;
            if (ra != rb) return ra - rb;
            return ra == 2 ? Long.compare(b.start, a.start) : Long.compare(a.start, b.start);
        });
        return out;
    }

    /** Andre kampe i samme liga samme dag ("resten af runden"). */
    static List<Game> round(Game g) {
        List<Game> out = new ArrayList<>();
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        a.setTimeInMillis(g.start);
        for (Game x : games.values()) {
            if (!x.league.equals(g.league) || x.id.equals(g.id)) continue;
            b.setTimeInMillis(x.start);
            if (Math.abs(x.start - g.start) < 3 * 24 * 3600_000L) out.add(x);
        }
        Collections.sort(out, (p, q) -> Long.compare(p.start, q.start));
        return out;
    }

    // ---- tabel ----
    static final class Row { int rank, played, points; String team, logo, diff; }
    static final Map<String, List<Row>> tables = new ConcurrentHashMap<>();
    private static final Map<String, Long> tableAt = new ConcurrentHashMap<>();

    static List<Row> table(String code) {
        Long t = tableAt.get(code);
        if (t == null || System.currentTimeMillis() - t > 10 * 60_000L) {
            tableAt.put(code, System.currentTimeMillis());
            pool.execute(() -> {
                try {
                    JSONObject o = new JSONObject(Http.text("https://site.api.espn.com/apis/v2/sports/soccer/" + code + "/standings"));
                    JSONArray ch = o.optJSONArray("children");
                    if (ch == null || ch.length() == 0) return;
                    JSONArray en = ch.getJSONObject(0).getJSONObject("standings").getJSONArray("entries");
                    List<Row> rows = new ArrayList<>();
                    for (int i = 0; i < en.length(); i++) {
                        JSONObject e = en.getJSONObject(i);
                        Row r = new Row();
                        JSONObject tm = e.getJSONObject("team");
                        r.team = tm.optString("shortDisplayName", tm.optString("displayName"));
                        JSONArray lg = tm.optJSONArray("logos");
                        r.logo = lg != null && lg.length() > 0 ? lg.getJSONObject(0).optString("href") : "";
                        JSONArray st = e.getJSONArray("stats");
                        for (int k = 0; k < st.length(); k++) {
                            JSONObject s = st.getJSONObject(k);
                            switch (s.optString("name")) {
                                case "rank": r.rank = s.optInt("value"); break;
                                case "gamesPlayed": r.played = s.optInt("value"); break;
                                case "points": r.points = s.optInt("value"); break;
                                case "pointDifferential": r.diff = s.optString("displayValue"); break;
                            }
                        }
                        rows.add(r);
                    }
                    Collections.sort(rows, (p, q) -> p.rank - q.rank);
                    tables.put(code, rows);
                    Catalog.changed();
                } catch (Exception e) { tableAt.remove(code); }
            });
        }
        List<Row> l = tables.get(code);
        return l == null ? new ArrayList<>() : l;
    }

    /** "62'", "Pause", "20.00", "I morgen 18.00", "Slut". */
    static String status(Game g) {
        if (g.live()) return g.detail.isEmpty() ? g.clock : g.detail.replace("Halftime", "Pause").replace("HT", "Pause");
        if (g.done()) return "Slut";
        return Search.when(g.start).replace("I dag ", "");
    }
}
