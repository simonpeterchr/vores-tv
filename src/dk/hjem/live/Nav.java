package dk.hjem.live;

import android.app.Activity;
import android.content.Intent;

/** Fanerne i top-bjælken: Hjem · Live-TV · Film · Serier. */
final class Nav {
    static final String[] TABS = {"Søg", "Hjem", "Live-TV", "Sport", "Film", "Serier"};
    static final int SEARCH = 0, HOME = 1, LIVE = 2, SPORT = 3, MOVIES = 4, SERIES = 5;

    static void wire(final Activity a, Ui.TopBar top, final int active, final Runnable soon) {
        for (int i = 0; i < top.items.length; i++) {
            final int t = i;
            top.items[i].setOnClickListener(v -> {
                if (t == active) return;
                Intent in = t == SEARCH ? new Intent(a, SearchActivity.class) : t == HOME ? new Intent(a, HomeActivity.class)
                        : t == LIVE ? new Intent(a, LiveActivity.class)
                        : t == SPORT ? new Intent(a, SportActivity.class)
                        : new Intent(a, MediaActivity.class).putExtra("kind", t == MOVIES ? Vod.MOVIE : Vod.SERIES);
                a.startActivity(in);
                a.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                if (!(a instanceof HomeActivity)) a.finish(); // Hjem er bunden; de andre faner lukkes ved fane-skift
            });
        }
    }
}
