package dk.hjem.live;

import android.content.Context;

import androidx.media3.common.MediaItem;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

/** ExoPlayer sat op til hurtig kanalskift: lille startbuffer, udbyder-venlig HTTP. */
final class Play {
    static ExoPlayer create(Context c) {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(Http.UA).setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10_000).setReadTimeoutMs(15_000);
        DefaultLoadControl lc = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(4_000, 30_000, 700, 1_500).build();
        return new ExoPlayer.Builder(c)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(c).setDataSourceFactory(http))
                .setLoadControl(lc)
                .build();
    }

    static void live(ExoPlayer p, Cred cr, Catalog.Ch ch, boolean hls) {
        String url = new Xtream(cr).liveUrl(ch.id, hls);
        MediaItem.Builder b = new MediaItem.Builder().setUri(url);
        if (hls) b.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8);
        p.setMediaItem(b.build());
        p.prepare();
        p.setPlayWhenReady(true);
    }
}
