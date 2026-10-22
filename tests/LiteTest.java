package io.freemytv;

import java.util.Arrays;
import java.util.Collections;

/** Run with the SDK stub jar on the classpath; no Android stub methods are invoked. */
public class LiteTest {
    private static void check(boolean pass, String name) {
        if (!pass) throw new AssertionError(name);
    }
    public static void main(String[] args) {
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
        System.out.println("Lite tests passed: image sampling, upgrade preservation, legacy service cleanup.");
    }
}
