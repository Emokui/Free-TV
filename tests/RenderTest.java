package io.freemytv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import java.util.Arrays;

/** Runs through app_process on the TV, using real Android graphics rather than SDK stubs. */
public final class RenderTest {
    private static void check(boolean pass, String name) {
        if (!pass) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        Bitmap source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++)
            source.setPixel(x, y, ((x + y) & 1) == 0 ? Color.BLACK : Color.WHITE);
        Bitmap small = Ui.downscale(source, 1, 1);
        check(!source.isRecycled(), "downscale preserves caller bitmap");
        check(small.getWidth() == 1 && Math.abs(Color.red(small.getPixel(0, 0)) - 128) <= 2,
                "repeated reduction preserves high-frequency average");
        check(Ui.downscale(source, 64, 64) == source, "same-size image can be shared");
        Bitmap odd = Ui.downscale(source, 23, 17);
        check(odd.getWidth() == 23 && odd.getHeight() == 17, "fractional reductions have exact dimensions");
        Scene scene = new Scene();
        scene.setSize(320, 180);
        Bitmap image = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        android.graphics.RectF rect = new android.graphics.RectF(40, 40, 280, 140);
        int[] frost = new int[Scene.FW * Scene.FH], glass = new int[Scene.GW * Scene.GH];
        Arrays.fill(frost, Color.BLUE);
        Arrays.fill(glass, Color.RED);
        scene.setFrost(frost);
        scene.setGlassImage(glass);
        scene.glass(canvas, rect, 20, Color.BLACK, 0.35f, 0, 0, 0, 1);
        if (!scene.shaded()) {
            int blue = image.getPixel(160, 90);
            check(Color.blue(blue) > Color.red(blue) + 80, "fallback frost shows blurred photo");
            image.eraseColor(Color.TRANSPARENT);
            scene.liquid(canvas, rect, 20, Color.BLACK, 0.5f, 0, 1, 0, 1, 1);
            int red = image.getPixel(160, 90);
            check(Color.red(red) > Color.blue(red) + 80, "fallback liquid shows its photo");
            scene.clearGlass(); scene.clearFrost();
            image.eraseColor(Color.TRANSPARENT);
            scene.glass(canvas, rect, 20, Color.BLACK, 0.35f, 0, 0, 0, 1);
            check(Color.red(image.getPixel(160, 90)) == 0 && Color.blue(image.getPixel(160, 90)) == 0,
                    "cleared glass does not retain stale photo");
        }
        source.recycle(); small.recycle(); odd.recycle(); image.recycle();
        System.out.println("Android render tests passed: downscale ownership/quality and fallback glass.");
    }
}
