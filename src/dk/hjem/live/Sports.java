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

    static final String FOOTBALL = "football", F1 = "f1", DARTS = "darts", HANDBALL = "handball", TENNIS = "tennis", MMA = "mma", GOLF = "golf";

    /** Sportsgrene med visningsnavn (rækkefølgen på Sport-fanen). */
    static final String[][] SPORTS = {{FOOTBALL, "Fodbold"}, {F1, "Formel 1"}, {DARTS, "Dart"}, {HANDBALL, "Håndbold"}, {TENNIS, "Tennis"}, {MMA, "UFC"}, {GOLF, "Golf"}};

    static String sportName(String s) { for (String[] x : SPORTS) if (x[0].equals(s)) return x[1]; return s; }

    static final class Game {
        String sport = FOOTBALL; // football | f1 | darts | handball | tennis | mma | golf
        String title, sub; // begivenheder uden to hold (F1-session, dartaften, turnering)
        boolean allDay;
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
    static final String DEFAULT_TEAMS = "Sønderjyske\nBarcelona\nWrexham\nHamburg", DEFAULT_LEAGUES = "den.1\nden.pokal\neng.1\nger.1", DEFAULT_SPORTS = "f1\ndarts\nhandball";

    static List<String> favSports(Context c) { return lines(prefs(c).getString("sports", DEFAULT_SPORTS)); }
    static void setFavSports(Context c, List<String> v) { prefs(c).edit().putString("sports", android.text.TextUtils.join("\n", v)).apply(); Catalog.changed(); }

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
        if (t.name == null) return false;
        String n = Search.norm(t.name + " " + t.shortName);
        for (String f : favTeams(c)) if (n.contains(Search.norm(f))) return true;
        return false;
    }

    static boolean mine(Context c, Game g) {
        if (!FOOTBALL.equals(g.sport)) return favSports(c).contains(g.sport) || (g.home.name != null && (isFavTeam(c, g.home) || isFavTeam(c, g.away)));
        return isFavTeam(c, g.home) || isFavTeam(c, g.away) || favLeagues(c).contains(g.league);
    }

    // ---- hentning: én dag ad gangen pr. liga ----
    private static String day(int offset) {
        Calendar k = Calendar.getInstance();
        k.add(Calendar.DAY_OF_YEAR, offset);
        return new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(k.getTime());
    }

    /** ESPN-kilder ud over fodbold (dag for dag): {sti, sport, visningsnavn}. */
    static final String[][] ESPN_OTHER = {
            {"racing/f1", F1, "Formel 1"}, {"tennis/atp", TENNIS, "ATP"}, {"tennis/wta", TENNIS, "WTA"},
            {"mma/ufc", MMA, "UFC"}, {"golf/pga", GOLF, "PGA Tour"},
    };

    /** TheSportsDB (gratis nøgle): dart og håndbold, som ESPN ikke har. */
    static final String[][] TSDB = {{"Darts", DARTS}, {"Handball", HANDBALL}};
    private static final ExecutorService tsdb = Executors.newSingleThreadExecutor(); // én ad gangen (begrænset antal opslag)

    /** Henter i dag (oftere) og de næste 6 dage (sjældnere). */
    static void refresh() {
        for (String[] o : ESPN_OTHER) for (int i = -1; i <= 6; i++) {
            final String key = o[0] + "|" + day(i);
            Long f = fetched.get(key);
            long maxAge = i == 0 ? 120_000L : 6 * 3600_000L;
            if (f != null && System.currentTimeMillis() - f < maxAge) continue;
            fetched.put(key, System.currentTimeMillis());
            final String[] src = o;
            final String d = day(i);
            pool.execute(() -> {
                try { parseOther(src, new JSONObject(Http.text("https://site.api.espn.com/apis/site/v2/sports/" + src[0] + "/scoreboard?dates=" + d))); Catalog.changed(); }
                catch (Exception e) { fetched.remove(key); }
            });
        }
        for (String[] t : TSDB) for (int i = 0; i <= 6; i++) {
            final String key = "tsdb|" + t[0] + "|" + day(i);
            Long f = fetched.get(key);
            long maxAge = i == 0 ? 15 * 60_000L : 12 * 3600_000L;
            if (f != null && System.currentTimeMillis() - f < maxAge) continue;
            fetched.put(key, System.currentTimeMillis());
            final String[] src = t;
            Calendar k = Calendar.getInstance();
            k.add(Calendar.DAY_OF_YEAR, i);
            final String d = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(k.getTime());
            tsdb.execute(() -> {
                try {
                    Thread.sleep(2500);
                    parseTsdb(src[1], new JSONObject(Http.text("https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d=" + d + "&s=" + src[0])));
                    Catalog.changed();
                } catch (Exception e) { fetched.remove(key); }
            });
        }
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

    private static String session(String abbr) {
        if (abbr == null) return "";
        switch (abbr) {
            case "FP1": return "1. træning"; case "FP2": return "2. træning"; case "FP3": return "3. træning";
            case "Qual": return "Kvalifikation"; case "Race": return "Løb"; case "Sprint": return "Sprint";
            case "SS": case "SQ": return "Sprint-kvalifikation";
            default: return abbr;
        }
    }

    private static void parseOther(String[] src, JSONObject o) throws Exception {
        JSONArray ev = o.optJSONArray("events");
        if (ev == null) return;
        for (int i = 0; i < ev.length(); i++) {
            JSONObject e = ev.getJSONObject(i);
            String name = e.optString("name");
            JSONArray comps = e.optJSONArray("competitions");
            if (F1.equals(src[1]) && comps != null) { // én kort pr. session: træning, kvalifikation, løb
                for (int k = 0; k < comps.length(); k++) {
                    JSONObject c = comps.getJSONObject(k);
                    Game g = other(src, e.optString("id") + "-" + k, name.replaceFirst("^(MSC Cruises|Heineken|Qatar Airways|Pirelli|Aramco|Lenovo|Crypto\\.com|AWS|Louis Vuitton|Gulf Air|STC|Etihad Airways|Rolex|Formula 1)\\s+", ""),
                            session(c.optJSONObject("type") != null ? c.optJSONObject("type").optString("abbreviation") : ""), c.optString("date"), c.optJSONObject("status"));
                    if (g != null) games.put(g.id + "|" + g.league, g);
                }
                continue;
            }
            Game g = other(src, e.optString("id"), name, src[2], e.optString("date"), e.optJSONObject("status"));
            if (g != null) { if (!MMA.equals(src[1])) g.allDay = true; games.put(g.id + "|" + g.league, g); }
        }
    }

    private static Game other(String[] src, String id, String title, String sub, String date, JSONObject st) {
        Game g = new Game();
        g.sport = src[1]; g.id = id; g.league = src[0]; g.leagueName = src[2];
        g.title = title; g.sub = sub;
        g.start = iso(date);
        if (g.start == 0) return null;
        if (st != null) {
            JSONObject t = st.optJSONObject("type");
            if (t != null) { g.state = t.optString("state", "pre"); g.detail = t.optString("shortDetail", ""); }
        }
        return g;
    }

    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    static { TS.setTimeZone(TimeZone.getTimeZone("UTC")); }

    private static void parseTsdb(String sport, JSONObject o) throws Exception {
        JSONArray ev = o.optJSONArray("events");
        if (ev == null) return;
        for (int i = 0; i < ev.length(); i++) {
            JSONObject e = ev.getJSONObject(i);
            Game g = new Game();
            g.sport = sport;
            g.id = "t" + e.optString("idEvent");
            g.league = "tsdb." + e.optString("idLeague");
            g.leagueName = e.optString("strLeague").replace("German Handball-Bundesliga", "Handball-Bundesliga").replace("Danish Mens Handball League", "Herreligaen").replace("Danish Womens Handball League", "Kvindeligaen").replace("PDC Darts", "PDC");
            String time = e.optString("strTime", "00:00:00");
            if (time.isEmpty() || "null".equals(time)) time = "00:00:00";
            g.allDay = time.startsWith("00:00");
            synchronized (TS) { try { g.start = TS.parse(e.optString("dateEvent") + " " + time.substring(0, Math.min(8, time.length()))).getTime(); } catch (Exception x) { continue; } }
            String h = e.optString("strHomeTeam", ""), a = e.optString("strAwayTeam", "");
            if (!h.isEmpty() && !"null".equals(h) && !a.isEmpty() && !"null".equals(a)) {
                g.home.name = h; g.home.shortName = h.replace(" Håndbold", "").replace(" Handball", "").replace("Handball Sport Verein ", "HSV ");
                g.away.name = a; g.away.shortName = a.replace(" Håndbold", "").replace(" Handball", "").replace("Handball Sport Verein ", "HSV ");
                g.home.logo = e.optString("strHomeTeamBadge", ""); g.away.logo = e.optString("strAwayTeamBadge", "");
                try { g.home.score = Integer.parseInt(e.optString("intHomeScore")); g.away.score = Integer.parseInt(e.optString("intAwayScore")); } catch (Exception ignored) { }
            } else {
                g.title = e.optString("strEvent"); g.sub = g.leagueName;
            }
            String status = e.optString("strStatus", "");
            g.state = status.matches("(?i)(FT|AOT|AET|Match Finished|Finished)") ? "post" : status.matches("(?i)(1H|2H|HT|Live|In Progress)") ? "in" : "pre";
            if ("pre".equals(g.state) && !g.allDay && System.currentTimeMillis() > g.start && System.currentTimeMillis() < g.start + 2 * 3600_000L) g.state = "in";
            games.put(g.id + "|" + g.league, g);
        }
    }

    // ---- Turneringer ingen datakilde har (fx Oddset Pokalen): find kampene i jeres egne kanaler ----
    static final String[][] GUIDE = { // {kode, navn, søgeord (regex)}
            {"den.pokal", "Pokalen", "(?i).*\\b(oddset pokalen|sydbank ?pokalen|landspokal\\w*|dbu pokal\\w*|pokalfodbold|pokalen)\\b.*"},
    };
    private static final java.util.regex.Pattern VS = java.util.regex.Pattern.compile("(.+?)\\s+(?:-|–|—|vs\\.?|v)\\s+(.+)");

    /** Laver kampe ud fra PPV-kanalnavne og sportskanalernes programguide. */
    static void guide(Context c) {
        long now = System.currentTimeMillis();
        for (String[] gd : GUIDE) {
            for (Catalog.Ch ch : Catalog.channels) { // PPV: "Pokalen: AGF - Brøndby @ 2026-10-29 19:00"
                String raw = ch.raw != null ? ch.raw : ch.name;
                Catalog.Cat cat = Catalog.cats.get(ch.cat);
                if (!(raw.matches(gd[2]) || (cat != null && cat.name.matches(gd[2])))) continue;
                java.util.regex.Matcher m = Search.EVENT.matcher(ch.name);
                if (!m.find()) continue;
                long t;
                try { t = Search.EVENT_FMT.parse(m.group(1) + " " + m.group(2)).getTime(); } catch (Exception e) { continue; }
                if (t < now - 3 * 3600_000L) continue;
                addGuideGame(gd, ch.name.substring(0, m.start()), t, t + 2 * 3600_000L);
            }
            for (Catalog.Ch ch : Match.sportsPool(c)) { // programguide
                for (Xtream.Prog p : Epg.get(ch.id)) {
                    if (p.stop < now - 3600_000L || !(p.title.matches(gd[2]))) continue;
                    addGuideGame(gd, p.title, p.start, p.stop);
                }
            }
        }
    }

    private static void addGuideGame(String[] gd, String title, long start, long stop) {
        String t = title.replaceAll("(?i)^.*?(oddset pokalen|sydbank ?pokalen|landspokal\\w*|dbu pokal\\w*|pokalfodbold|pokalen)\\s*[:\\-–|]*\\s*", "")
                .replaceAll("(?i)^(live|direkte)\\s*[:\\-–]\\s*", "").replaceAll("\\s*[(\\[].*$", "").trim();
        java.util.regex.Matcher m = VS.matcher(t);
        Game g = new Game();
        g.league = gd[0]; g.leagueName = gd[1]; g.start = start;
        if (m.matches()) {
            g.home.name = g.home.shortName = m.group(1).trim();
            g.away.name = g.away.shortName = m.group(2).trim();
        } else { g.title = t.isEmpty() ? gd[1] : t; g.sub = gd[1]; }
        long now = System.currentTimeMillis();
        g.state = now >= start && now < stop ? "in" : now >= stop ? "post" : "pre";
        g.id = "g" + Integer.toHexString((Search.norm(t) + (start / 600_000L)).hashCode());
        if (!games.containsKey(g.id + "|" + g.league)) { games.put(g.id + "|" + g.league, g); Catalog.changed(); }
    }

    /** Kampe sorteret: i gang først, så kommende efter tid, så afsluttede (seneste først). */
    static List<Game> list(Context c, boolean onlyMine) {
        List<Game> out = new ArrayList<>();
        long from = System.currentTimeMillis() - 24 * 3600_000L;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Game g : games.values()) {
            if (g.start < from) continue;
            if (onlyMine && !mine(c, g)) continue;
            String dk = g.title != null ? g.sport + g.title + g.sub + g.start : g.home.name + "-" + g.away.name + "@" + g.start;
            if (!seen.add(dk)) continue; // samme kamp i to "ligaer"
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
        if (g.title != null && !F1.equals(g.sport)) return new ArrayList<>();
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
        if (g.allDay) return Search.when(g.start).replaceAll(" \\d{2}\\.\\d{2}$", "");
        return Search.when(g.start).replace("I dag ", "");
    }
}
