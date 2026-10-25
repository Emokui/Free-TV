package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Private, bounded-size wallpaper copy: the USB drive can be removed after import. */
final class Wallpaper {
    private Wallpaper() {}
    private static File file(Context c) { return new File(c.getFilesDir(), "wallpaper.jpg"); }
    static boolean exists(Context c) { return file(c).isFile(); }

    static Bitmap load(Context c) {
        Bitmap image = BitmapFactory.decodeFile(file(c).getPath());
        if (image != null) image.prepareToDraw();
        return image;
    }

    static int sampleSize(int width, int height) {
        int sample = 1;
        while (Math.max(width, height) / sample > 2560) sample *= 2;
        return sample;
    }

    static void importImage(Context c, Uri uri) throws IOException {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("无法读取图片");
            BitmapFactory.decodeStream(in, null, opts);
        }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) throw new IOException("无法识别此图片，请选择 JPG 或 PNG");
        opts.inSampleSize = sampleSize(opts.outWidth, opts.outHeight);
        opts.inJustDecodeBounds = false;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap source;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            source = BitmapFactory.decodeStream(in, null, opts);
        }
        if (source == null) throw new IOException("图片解码失败");
        Bitmap output = null;
        File temp = new File(c.getFilesDir(), "wallpaper.tmp");
        try {
            int orientation = ExifInterface.ORIENTATION_NORMAL;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                orientation = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            } catch (IOException | RuntimeException ignored) {}
            Matrix rotation = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: rotation.setScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_180: rotation.setRotate(180); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: rotation.setScale(1, -1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE: rotation.setRotate(90); rotation.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_90: rotation.setRotate(90); break;
                case ExifInterface.ORIENTATION_TRANSVERSE: rotation.setRotate(270); rotation.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_270: rotation.setRotate(270); break;
                default: break;
            }
            if (!rotation.isIdentity()) {
                Bitmap turned = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), rotation, true);
                if (turned != source) source.recycle();
                source = turned;
            }
            // Match the actual UI resolution, with a centered crop and no stretching.
            android.util.DisplayMetrics metrics = c.getResources().getDisplayMetrics();
            int longest = Math.max(metrics.widthPixels, metrics.heightPixels);
            float uiScale = longest > 0 ? Math.min(1f, longest / 1920f) : 1f;
            int width = Math.max(1, Math.round(1920 * uiScale)), height = Math.max(1, Math.round(1080 * uiScale));
            output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            canvas.drawColor(0xFF000000);
            float scale = Math.max((float) width / source.getWidth(), (float) height / source.getHeight());
            Matrix fit = new Matrix();
            fit.setScale(scale, scale);
            fit.postTranslate((width - source.getWidth() * scale) / 2, (height - source.getHeight() * scale) / 2);
            canvas.drawBitmap(source, fit, new Paint(Paint.FILTER_BITMAP_FLAG));
            try (FileOutputStream out = new FileOutputStream(temp)) {
                if (!output.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw new IOException("无法保存图片");
                out.getFD().sync();
            }
            if (!temp.renameTo(file(c))) throw new IOException("无法替换壁纸文件");
        } finally {
            source.recycle();
            if (output != null) output.recycle();
            temp.delete();
        }
    }

    static void clear(Context c) throws IOException {
        if (file(c).exists() && !file(c).delete()) throw new IOException("无法移除壁纸");
        Prefs.wallpaperChanged();
    }
}
