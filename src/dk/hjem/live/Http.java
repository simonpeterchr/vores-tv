package dk.hjem.live;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Minimal HTTP GET med manuel redirect-håndtering (også på tværs af værter). */
final class Http {
    /** Brugervenlig fejltekst uden serveradresser eller IP'er. */
    /** Afspilningsfejl i klar tale (logger server og svar uden loginoplysninger). */
    static String playback(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException r = (androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) c;
                java.util.List<String> sv = r.headerFields.get("Server");
                android.util.Log.w("VoresTV", "afspilning " + r.responseCode + " fra " + r.dataSpec.uri.getHost() + " server=" + sv + " cf=" + r.headerFields.containsKey("CF-RAY"));
                if (r.responseCode == 403 || r.responseCode == 458 || r.responseCode == 509)
                    return "Udbyderen afviste afspilningen (" + r.responseCode + "). Det sker typisk, når jeres login allerede bruges på et andet tv eller i TiviMate. Stop det, og prøv igen.";
                if (r.responseCode == 404) return "Udbyderen har ikke filen lige nu (404). Prøv en anden version af titlen.";
                return "Udbyderen svarede med fejl " + r.responseCode + ".";
            }
            if (c instanceof java.net.SocketTimeoutException || c instanceof java.net.ConnectException) return "Udbyderen svarer ikke lige nu – prøv igen om lidt.";
        }
        return "Kan ikke afspille lige nu.";
    }

    static String friendly(Exception e) {
        if (e instanceof java.net.SocketTimeoutException) return "Udbyderen svarer ikke lige nu – prøv igen om lidt.";
        if (e instanceof java.net.UnknownHostException) return "Ingen forbindelse til internettet eller udbyderen.";
        if (e instanceof java.net.ConnectException) return "Kan ikke få forbindelse til udbyderen lige nu.";
        String m = e.getMessage();
        if (m != null && m.startsWith("HTTP ")) return "Udbyderen svarede med fejl " + m.substring(5).split(" ")[0] + ".";
        return "Noget gik galt med forbindelsen til udbyderen.";
    }

    // Google Fotos' korte links sender desktop-browsere til en Firebase-side; en Android-UA får et rent 302.
    static final String UA = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36";

    static byte[] bytes(String address) throws IOException {
        URL url = new URL(address.replaceFirst("^webcal://", "https://"));
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept-Language", "da,en;q=0.8");
            c.setRequestProperty("Cookie", "SOCS=CAI; CONSENT=YES+");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("Redirect uden Location");
                url = new URL(url, loc);
                continue;
            }
            if (code != 200) {
                c.disconnect();
                throw new IOException("HTTP " + code + " fra " + url.getHost());
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            } finally {
                c.disconnect();
            }
        }
        throw new IOException("For mange redirects");
    }

    static String text(String address) throws IOException {
        return new String(bytes(address), StandardCharsets.UTF_8);
    }
}
