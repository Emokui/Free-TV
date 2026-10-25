package io.freemytv;

import java.util.Arrays;
import java.util.Collections;

/** Run with the SDK stub jar on the classpath; no Android stub methods are invoked. */
public class LiteTest {
    private static void check(boolean pass, String name) {
        if (!pass) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        cachePolicy();
        // A Settings focus animation must not step the clock/tabs group, but must keep its own frames running.
        Spring[] top = {new Spring(0.26f,0.85f,0), new Spring(0.26f,0.85f,0),
                new Spring(0.26f,0.85f,0), new Spring(0.26f,0.85f,0)};
        top[3].target = 1;
        check(!Spring.stepRange(top,0,3,1/60f), "stationary tabs do not request a clock redraw");
        check(Spring.stepRange(top,3,4,1/60f), "settings animation still schedules frames");
        check(top[3].value > 0 && top[0].value == 0, "focus groups stay isolated");
        for (int i=0;i<180;i++) Spring.stepRange(top,3,4,1/60f);
        check(!Spring.stepRange(top,3,4,1/60f) && top[3].value == 1, "animation settles and stops scheduling");
        int wp = Prefs.wallpaperVersion, all = Prefs.version;
        Prefs.wallpaperChanged();
        check(Prefs.wallpaperVersion == wp + 1 && Prefs.version == all + 1, "wallpaper changes invalidate both generations");
        // Large and panoramic images must be sampled before decoding to bound TV memory use.
        for (int[] size : new int[][] {{1920,1080}, {8000,6000}, {100000,100}, {Integer.MAX_VALUE,1}}) {
            int sample = Wallpaper.sampleSize(size[0], size[1]);
            check(sample > 0 && (sample & (sample - 1)) == 0, "power of two sample");
            check(Math.max(size[0],size[1]) / sample <= 2560, "bounded image decode");
        }
        check(Wallpaper.sampleSize(1280,720) == 1, "do not reduce small images");
        String ours = "io.freemytv/io.freemytv.RemoteKeyService";
        String talkback = "com.google.android.marvin.talkback/.TalkBackService";
        check(talkback.equals(LegacyCleanup.withoutService(ours + ":" + talkback, ours)), "preserve TalkBack");
        check("".equals(LegacyCleanup.withoutService(ours, ours)), "remove only our service");
        check(LegacyCleanup.withoutService(talkback, ours) == null, "leave unrelated services untouched");
        check(LegacyCleanup.withoutService("null", ours) == null, "unset service list");
        check(LegacyCleanup.ownsDream("io.freemytv/.Screensaver", "io.freemytv"), "old relative dream");
        check(LegacyCleanup.ownsDream("io.freemytv/io.freemytv.ScreensaverAlone", "io.freemytv"), "old Android 11 dream");
        check("other.app/.Dream".equals(LegacyCleanup.otherDreams("io.freemytv/.Screensaver,other.app/.Dream", "io.freemytv")), "preserve other registered dreams");
        check(!LegacyCleanup.ownsDream("other.app/.Screensaver", "io.freemytv"), "preserve another dream");
        check(LegacyCleanup.restoreDreamApp("dream", "home", Arrays.asList("dream"), Collections.emptyList()), "restore what we disabled");
        check(!LegacyCleanup.restoreDreamApp("dream", "home", Arrays.asList("dream"), Arrays.asList("dream")), "preserve previously disabled dream");
        check(!LegacyCleanup.restoreDreamApp("home", "home", Arrays.asList("home"), Collections.emptyList()), "do not reenable replaced launcher");
        check(!LegacyCleanup.restoreDreamApp("dream", "home", Collections.emptyList(), Collections.emptyList()), "do not touch unowned app");
        try {
            LegacyCleanup.withoutService(ours + ":untrusted'entry", ours);
            throw new AssertionError("unsafe component must not be written");
        } catch (IllegalArgumentException expected) {}
        System.out.println("Lite tests passed: app cache, image sampling, upgrade preservation, legacy service cleanup.");
    }

    private static void cachePolicy() {
        AppListCache cache = new AppListCache();
        check(cache.needsReload(100, 0, "zh"), "cold cache loads");
        AppListCache.Request first = cache.begin(100, 0, "zh");
        check(cache.begin(110, 0, "zh") == null, "overlapping resumes coalesce");
        check(cache.complete(first, 200, 0, "zh"), "first result accepted");
        check(!cache.needsReload(201, 0, "zh"), "normal return reuses apps");
        check(!cache.needsReload(200 + AppListCache.MAX_AGE_MS - 1, 0, "zh"), "cache valid before expiry");
        check(cache.needsReload(200 + AppListCache.MAX_AGE_MS, 0, "zh"), "expiry refreshes even without callbacks");
        check(cache.needsReload(201, 1, "zh"), "shelf editing refreshes");
        check(cache.needsReload(201, 0, "en"), "language editing refreshes");
        cache.invalidate();
        AppListCache.Request changed = cache.begin(300, 0, "zh");
        cache.invalidate(); // Package changed again while the background query was in flight.
        check(!cache.complete(changed, 310, 0, "zh"), "stale package results rejected");
        AppListCache.Request retry = cache.begin(311, 0, "zh");
        check(retry != null, "changed package is queried again");
        check(!cache.complete(retry, 320, 1, "zh"), "stale shelf order rejected");
        AppListCache.Request shelf = cache.begin(321, 1, "zh");
        check(!cache.complete(shelf, 330, 1, "en"), "stale language rejected");
        AppListCache.Request language = cache.begin(331, 1, "en");
        cache.fail(language);
        check(cache.needsReload(332, 1, "en"), "failed query can retry");
        AppListCache.Request good = cache.begin(333, 1, "en");
        check(cache.complete(good, 340, 1, "en"), "latest query accepted");
        check(!cache.complete(first, 341, 0, "zh"), "old completion cannot replace current cache");
        check(!cache.needsReload(342, 1, "en"), "accepted state stays current");
        check(AppListCache.withinBurst(100, 1599) && !AppListCache.withinBurst(100, 1600)
                && !AppListCache.withinBurst(0, 100), "Home burst boundary and cold start");
    }
}
