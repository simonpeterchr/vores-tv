package dk.hjem.live;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Baggrundstråde med lav prioritet, så skærm og fjernbetjening altid kommer først (vigtigt på svage tv'er). */
final class Bg {
    static ExecutorService pool(int n, final String name) {
        return Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(() -> {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                r.run();
            }, name);
            t.setDaemon(true);
            return t;
        });
    }

    static void run(final String name, final Runnable r) {
        new Thread(() -> { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); r.run(); }, name).start();
    }
}
