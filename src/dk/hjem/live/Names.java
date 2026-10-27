package dk.hjem.live;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rydder udbyderens navne op: "DK| DENMARK ᴴᴰ/ᴿᴬᵂ" → "Denmark HD/RAW", "DK: DR1 ᴿᴬᵂ" → "DR1 RAW". */
final class Names {
    private static final String SUP_FROM = "ᴬᴮᴰᴱᴳᴴᴵᴶᴷᴸᴹᴺᴼᴾᴿᵀᵁⱽᵂᵃᵇᶜᵈᵉᶠᵍʰⁱᶦʲᵏˡᵐⁿᵒᵖʳˢᵗᵘᵛʷˣʸᶻ⁰¹²³⁴⁵⁶⁷⁸⁹";
    private static final String SUP_TO   = "ABDEGHIJKLMNOPRTUVWabcdefghiijklmnoprstuvwxyz0123456789";
    private static final Pattern PREFIX = Pattern.compile("^\\s*([A-Za-z0-9+]{2,6})\\s*[|:]\\s*");
    private static final Set<String> KEEP = new HashSet<>(Arrays.asList(
            "PPV", "VIP", "RAW", "UHD", "FHD", "HD", "SD", "HEVC", "4K", "8K", "TV", "TV2", "TV3", "DR", "MAX", "HBO", "BBC", "ITV", "NBC", "CBS", "ABC", "ESPN", "NFL", "NBA", "NHL", "UFC", "USA"));

    static String prefix(String s) {
        Matcher m = PREFIX.matcher(s == null ? "" : s);
        return m.find() ? m.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private static String sup(String s) {
        StringBuilder b = new StringBuilder(s.length());
        boolean inSup = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int k = SUP_FROM.indexOf(c);
            if (k >= 0) {
                if (!inSup && b.length() > 0 && b.charAt(b.length() - 1) != ' ' && b.charAt(b.length() - 1) != '/') b.append(' ');
                b.append(Character.toUpperCase(SUP_TO.charAt(k)));
                inSup = true;
            } else { b.append(c); inSup = false; }
        }
        return b.toString();
    }

    static String channel(String s) {
        if (s == null) return "";
        return sup(PREFIX.matcher(s).replaceFirst("")).replaceAll("\\s+", " ").trim();
    }

    static String group(String s) {
        if (s == null) return "";
        String t = sup(PREFIX.matcher(s).replaceFirst("")).replaceAll("\\s+", " ").trim();
        StringBuilder b = new StringBuilder();
        for (String w : t.split(" ")) {
            if (b.length() > 0) b.append(' ');
            String bare = w.replaceAll("[^\\p{L}0-9+]", "");
            boolean caps = bare.length() > 3 && bare.equals(bare.toUpperCase(Locale.ROOT)) && !bare.matches(".*\\d.*");
            if (caps && !KEEP.contains(bare) && !w.contains("/")) b.append(w.charAt(0)).append(w.substring(1).toLowerCase(Locale.ROOT));
            else b.append(w);
        }
        return b.toString();
    }
}
