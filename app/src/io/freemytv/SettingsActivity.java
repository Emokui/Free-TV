package io.freemytv;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** TV-friendly settings for the simplified Chinese launcher. No background widgets or key remapping. */
public class SettingsActivity extends Activity {
    private static final int IMAGE = 10, STORAGE = 11;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private Button mode, wallpaper, sounds, theme, opacity;
    private boolean importing, destroyed;
    private int browserGeneration;
    private final Runnable poll = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 1000); }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        Ui.init(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(40), dp(32), dp(40), dp(24));
        root.setBackgroundColor(0xFF15171C);
        LinearLayout intro = new LinearLayout(this);
        intro.setOrientation(LinearLayout.VERTICAL);
        root.addView(intro, new LinearLayout.LayoutParams(0, -1, 0.85f));
        intro.addView(text("Free TV", 32));
        intro.addView(text("中文精简版", 22));
        intro.addView(text("Android 8.0+ · 支持 ARMv7", 16));
        TextView hint = text("壁纸支持电视存储和 U 盘图片。\n导入后可移除 U 盘。\n\n主页常用应用支持长按确定键调整顺序。", 17);
        hint.setPadding(0, dp(25), dp(20), 0);
        intro.addView(hint);
        status = text("", 16);
        status.setPadding(0, dp(24), dp(20), 0);
        intro.addView(status);
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1.15f));
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(menu);
        mode = button(menu, "桌面", this::chooseHome);
        wallpaper = button(menu, "选择本地壁纸", this::chooseWallpaper);
        opacity = button(menu, "壁纸遮罩", this::chooseOpacity);
        button(menu, "恢复默认背景", () -> {
            if (importing) return;
            try { Wallpaper.clear(this); refresh(); toast("已恢复默认背景"); }
            catch (Exception e) { toast("操作失败：" + e.getMessage()); }
        });
        button(menu, "常用应用", this::chooseShelf);
        theme = button(menu, "主题", () -> new AlertDialog.Builder(this).setTitle("桌面主题")
                .setSingleChoiceItems(new String[] {"自动（18:00–06:00 深色）", "浅色", "深色"},
                        Arrays.asList("auto", "light", "dark").indexOf(Prefs.theme()), (d, which) -> {
                            Prefs.setTheme(new String[] {"auto", "light", "dark"}[which]); refresh(); d.dismiss();
                        }).setNegativeButton("取消", null).show());
        sounds = button(menu, "按键音效", () -> { Prefs.setSounds(!Prefs.sounds()); refresh(); });
        button(menu, "电视系统设置", () -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (ActivityNotFoundException e) { toast("此电视未提供系统设置入口"); }
        });
        setContentView(root);
        mode.requestFocus();
        refresh();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(0xFFE6E8EC); return t;
    }
    private Button button(LinearLayout parent, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label); b.setTextSize(19); b.setAllCaps(false); b.setGravity(android.view.Gravity.CENTER_VERTICAL);
        b.setPadding(dp(18), dp(8), dp(18), dp(8));
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable focus = new GradientDrawable(); focus.setColor(0xFFFFFFFF); focus.setCornerRadius(dp(10));
        GradientDrawable normal = new GradientDrawable(); normal.setColor(0xFF292D35); normal.setCornerRadius(dp(10));
        bg.addState(new int[] {android.R.attr.state_focused}, focus);
        bg.addState(new int[] {}, normal); b.setBackground(bg);
        b.setTextColor(new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}}, new int[] {Color.BLACK, Color.WHITE}));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58)); lp.bottomMargin = dp(10);
        parent.addView(b, lp); b.setOnClickListener(v -> action.run()); return b;
    }
    private boolean switching(JSONObject s) {
        return !s.optString("busy").isEmpty() && SystemClock.elapsedRealtime() - s.optLong("busyAt") < 180_000;
    }
    private void refresh() {
        if (mode == null) return;
        JSONObject s = HomeMode.state(this);
        boolean busy = switching(s);
        mode.setEnabled(!busy);
        updateText(mode, "桌面：" + (HomeMode.FREE.equals(s.optString("mode")) && !s.optBoolean("homeReverted") ? "Free TV" : "原厂桌面"));
        wallpaper.setEnabled(!importing);
        updateText(wallpaper, importing ? "正在导入壁纸…" : Wallpaper.exists(this) ? "更换本地壁纸" : "选择本地壁纸");
        updateText(opacity, "壁纸遮罩：" + Prefs.wallpaperOpacity() + "%");
        updateText(sounds, "按键音效：" + (Prefs.sounds() ? "开启" : "关闭"));
        updateText(theme, "桌面主题：" + ("light".equals(Prefs.theme()) ? "浅色" : "dark".equals(Prefs.theme()) ? "深色" : "自动"));
        String error = s.optString("error");
        updateText(status, busy ? Chinese.text(s.optString("busy")) : !error.isEmpty() ? Chinese.text(error) : "1.4.3 中文精简优化版");
    }
    private static void updateText(TextView view, String text) {
        if (!text.contentEquals(view.getText())) view.setText(text);
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    private void chooseOpacity() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(12), dp(24), dp(12));
        TextView value = text("", 20);
        content.addView(value);
        TextView hint = text("左右键调整，每次 1%；上下键选择按钮。\n浅色为白色遮罩，深色为黑色遮罩。\n0% 无遮罩，100% 完全遮住壁纸。", 16);
        content.addView(hint);
        SeekBar slider = new SeekBar(this);
        slider.setMax(100);
        slider.setKeyProgressIncrement(1);
        slider.setProgress(Prefs.wallpaperOpacity());
        value.setText("遮罩不透明度：" + slider.getProgress() + "%");
        slider.setContentDescription(value.getText());
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                value.setText("遮罩不透明度：" + progress + "%");
                bar.setContentDescription(value.getText());
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        content.addView(slider, new LinearLayout.LayoutParams(-1, dp(60)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("壁纸遮罩")
                .setView(content).setPositiveButton("保存", (d, which) -> {
                    Prefs.setWallpaperOpacity(slider.getProgress()); refresh();
                }).setNeutralButton("恢复 15%", (d, which) -> {
                    Prefs.setWallpaperOpacity(15); refresh();
                }).setNegativeButton("取消", null).create();
        dialog.setOnShowListener(d -> slider.requestFocus());
        dialog.show();
    }

    private void chooseHome() {
        new AlertDialog.Builder(this).setTitle("选择桌面").setItems(new String[] {"启用 Free TV", "恢复原厂桌面"}, (d, which) -> {
            String message = which == 0 ? "将设为默认桌面，并停用原项目列出的推广应用。可在此恢复原厂桌面和应用。电视可能提示允许 USB 调试。"
                    : "将恢复原厂桌面，并重新启用此前由本应用停用的应用。";
            new AlertDialog.Builder(this).setTitle("切换桌面").setMessage(message).setPositiveButton("继续", (confirm, ok) -> {
                HomeMode.request(this, which == 0 ? HomeMode.FREE : HomeMode.ORIGINAL);
                status.setText("正在切换桌面…"); mode.setEnabled(false);
            }).setNegativeButton("取消", null).show();
        }).setNegativeButton("取消", null).show();
    }

    private void chooseWallpaper() {
        if (importing) return;
        new AlertDialog.Builder(this).setTitle("选择本地壁纸")
                .setItems(new String[] {"使用系统图片选择器", "浏览电视存储 / U 盘"}, (d, which) -> {
                    if (which == 1) { browseStorage(); return; }
                    Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                            .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivityForResult(picker, IMAGE); }
                    catch (ActivityNotFoundException e) {
                        try { startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), IMAGE); }
                        catch (ActivityNotFoundException missing) { browseStorage(); }
                    }
                }).setNegativeButton("取消", null).show();
    }
    private void browseStorage() {
        if (Build.VERSION.SDK_INT <= 32 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE); return;
        }
        if (Build.VERSION.SDK_INT >= 33) { toast("请使用系统图片选择器选择图片"); return; }
        browse(new File("/storage"));
    }
    private boolean image(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }
    private void browse(File dir) {
        final int generation = ++browserGeneration;
        new Thread(() -> {
            List<File> entries = new ArrayList<>();
            if ("/storage".equals(dir.getPath())) entries.add(new File("/sdcard"));
            File[] found = dir.listFiles(f -> !f.getName().startsWith(".") && (f.isDirectory() || image(f)));
            if (found != null) {
                Arrays.sort(found, Comparator.comparing((File f) -> !f.isDirectory()).thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                for (File f : found) if (!f.getName().equals("emulated") && !f.getName().equals("self")) entries.add(f);
            }
            handler.post(() -> {
                if (destroyed || generation != browserGeneration) return;
                List<String> labels = new ArrayList<>();
                labels.add("↑ 返回上一级");
                for (File f : entries) labels.add(f.isDirectory() ? "[文件夹] " + (f.getPath().equals("/sdcard") ? "电视内部存储" : f.getName()) : f.getName());
                if (entries.isEmpty()) toast("此文件夹没有可读取的图片或子文件夹");
                new AlertDialog.Builder(this).setTitle(dir.getPath()).setItems(labels.toArray(new String[0]), (d, at) -> {
                    if (at == 0) { if (!dir.getPath().equals("/storage")) browse(dir.getParentFile() == null ? new File("/storage") : dir.getParentFile()); return; }
                    File f = entries.get(at - 1);
                    if (f.isDirectory()) browse(f); else importWallpaper(Uri.fromFile(f));
                }).setNegativeButton("取消", null).show();
            });
        }, "wallpaper-browser").start();
    }
    private void importWallpaper(Uri uri) {
        if (importing) return;
        importing = true; refresh();
        new Thread(() -> {
            String failure = null;
            try { Wallpaper.importImage(this, uri); Prefs.wallpaperChanged(); }
            catch (Exception | OutOfMemoryError e) { failure = "壁纸导入失败：" + (e.getMessage() == null ? "图片过大或格式不受支持" : e.getMessage()); }
            final String error = failure;
            handler.post(() -> {
                importing = false;
                if (destroyed) return;
                refresh(); toast(error == null ? "壁纸已保存，返回桌面即可查看" : error);
            });
        }, "wallpaper-import").start();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == IMAGE && result == RESULT_OK && data != null && data.getData() != null) importWallpaper(data.getData());
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == STORAGE && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) browse(new File("/storage"));
        else if (request == STORAGE) toast("未授予存储权限，可改用系统图片选择器");
    }

    private void chooseShelf() {
        List<String[]> candidates = HomeApps.shelfCandidates(this);
        List<String> selected = new ArrayList<>(HomeApps.shelfPackages(this));
        String[] labels = new String[candidates.size()]; boolean[] checked = new boolean[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) { labels[i] = candidates.get(i)[1]; checked[i] = selected.contains(candidates.get(i)[0]); }
        new AlertDialog.Builder(this).setTitle("常用应用（最多 " + HomeApps.MAX_SHELF + " 个）")
                .setMultiChoiceItems(labels, checked, (dialog, which, on) -> {
                    String pkg = candidates.get(which)[0];
                    if (on && selected.size() >= HomeApps.MAX_SHELF) {
                        ((AlertDialog) dialog).getListView().setItemChecked(which, false); toast("常用应用已达到上限"); return;
                    }
                    if (on) selected.add(pkg); else selected.remove(pkg);
                }).setPositiveButton("保存", (d, which) -> { Prefs.setShelf(selected); toast("已保存；主页长按确定键可调整顺序"); })
                .setNegativeButton("取消", null).show();
    }
    @Override protected void onResume() { super.onResume(); handler.post(poll); }
    @Override protected void onPause() { handler.removeCallbacks(poll); super.onPause(); }
    @Override protected void onDestroy() { destroyed = true; handler.removeCallbacks(poll); super.onDestroy(); }
}
