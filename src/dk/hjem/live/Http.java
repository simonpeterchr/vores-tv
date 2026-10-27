package dk.hjem.live;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Minimal HTTP GET med manuel redirect-håndtering (også på tværs af værter). */
final class Http {
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
