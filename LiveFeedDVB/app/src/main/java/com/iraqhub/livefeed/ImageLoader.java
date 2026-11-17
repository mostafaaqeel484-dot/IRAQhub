package com.iraqhub.livefeed;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageLoader {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private ImageLoader() {}
    public static void load(Context context, ImageView view, String source) {
        if (source == null || !source.startsWith("https://")) return;
        view.setTag(source);
        POOL.execute(() -> {
            try {
                File dir = new File(context.getCacheDir(), "feed_snapshots");
                if (!dir.exists() && !dir.mkdirs()) return;
                File image = new File(dir, sha(source) + ".img");
                if (!image.isFile() || image.length() == 0) {
                    HttpURLConnection c = (HttpURLConnection) new URL(source).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(12000);
                    c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("Referer", FeedRepository.SITE);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV)");
                    try {
                        c.connect();
                        if (c.getResponseCode() != 200 || c.getContentLengthLong() > 5_000_000) return;
                        File temp = new File(dir, sha(source) + ".part");
                        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(temp)) {
                            byte[] buf = new byte[8192];
                            int n, total = 0;
                            while ((n = in.read(buf)) != -1) {
                                total += n;
                                if (total > 5_000_000) return;
                                out.write(buf, 0, n);
                            }
                        } finally {
                            // Connection is not shared across image tasks.
                            c.disconnect();
                        }
                        if (!temp.renameTo(image)) return;
                    } finally {
                        c.disconnect();
                    }
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                Bitmap bitmap = BitmapFactory.decodeFile(image.getPath(), opts);
                if (bitmap == null) { image.delete(); return; }
                image.setLastModified(System.currentTimeMillis());
                trim(dir);
                MAIN.post(() -> {
                    if (source.equals(view.getTag())) view.setImageBitmap(bitmap);
                });
            } catch (Exception ignored) { }
        });
    }
    private static String sha(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 12; i++) b.append(String.format("%02x", d[i] & 255));
        return b.toString();
    }
    private static synchronized void trim(File dir) {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".img"));
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total < 45_000_000) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File f : files) {
            if (total < 28_000_000) break;
            long size = f.length();
            if (f.delete()) total -= size;
        }
    }
}
