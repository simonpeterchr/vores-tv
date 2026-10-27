package dk.hjem.live;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Fmt {
    static final Locale DA = new Locale("da", "DK");
    static String hm(long t) { return new SimpleDateFormat("HH.mm", DA).format(new Date(t)); }
    static String clock() { return hm(System.currentTimeMillis()); }
    static String date() {
        String s = new SimpleDateFormat("EEEE d. MMMM", DA).format(new Date());
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    static String mins(long ms) { long m = Math.max(0, ms / 60000); return m + " min"; }
}
