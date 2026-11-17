package com.iraqhub.livefeed;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FeedRepository {
    public static final String SITE = "https://live-feed.net/";
    private static final String FEED_URL = SITE + "?show_off=1";
    public static final String CHANNEL = "new_satellite_feeds";
    private static final Pattern BLOCK = Pattern.compile("📡\\s*(.{2,135}?)\\s*📶\\s*(\\d{4,5})\\s*([HVLR])\\s*(\\d{3,6})", Pattern.DOTALL);
    private static final Pattern STATUS = Pattern.compile("(?:^|\\s)(ON|OFF)(?=\\s|🔓|🔐|Clear)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME = Pattern.compile("🆔\\s*(.{1,140}?)(?=\\s*(?:🔑|ON\\s|OFF\\s|📡|$))", Pattern.DOTALL);
    private static final Pattern CODEC = Pattern.compile("🎬\\s*(.{1,110}?)(?=\\s*(?:📊|🆔|🔑|$))", Pattern.DOTALL);
    private static final Pattern BITRATE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:Mb/s|Mbit/s)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}");
    private FeedRepository() {}

    public static final class Feed {
        public String satellite, frequency, polarization, symbolRate, name, codec, chroma, bitrate, imageUrl, updated, status;
        public String id() {
            return (satellite + "|" + frequency + "|" + polarization + "|" + symbolRate + "|" + name)
                    .toLowerCase(Locale.ROOT);
        }
        public String frequencyText() { return frequency + " " + polarization + " " + symbolRate; }
        public boolean isLive() { return "ON".equals(status); }
        public boolean is422() { return "4:2:2".equals(chroma); }
        public String summary() { return satellite + "  •  " + frequencyText(); }
    }

    private static final class Start {
        final int begin, end;
        final String satellite, frequency, polarization, symbolRate;
        Start(Matcher m) {
            begin = m.start(); end = m.end();
            satellite = m.group(1).replaceAll("\\s+", " ").trim();
            frequency = m.group(2); polarization = m.group(3); symbolRate = m.group(4);
        }
    }

    public static List<Feed> fetch() throws IOException {
        // The site server-renders cards. Fetch the complete list; fall back to the active-feed homepage.
        IOException last = null;
        for (String url : new String[]{FEED_URL, SITE}) {
            try {
                String html = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Linux; Android 14; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Accept-Language", "en-US,en;q=0.9,ar;q=0.8")
                        .header("Accept", "text/html,application/xhtml+xml")
                        .timeout(18000).maxBodySize(10_000_000).get().outerHtml();
                List<Feed> feeds = parse(html);
                if (!feeds.isEmpty()) return feeds;
                last = new IOException("موقع المصدر استجاب، لكن لم توجد حقول تردد DVB بصفحة HTML");
            } catch (IOException error) { last = error; }
        }
        throw last == null ? new IOException("تعذر جلب البيانات") : last;
    }

    public static List<Feed> parse(String html) {
        Document doc = Jsoup.parse(html, SITE);
        doc.select("script,style,noscript").remove();
        if (doc.body() == null) return Collections.emptyList();
        String text = doc.body().text();
        Matcher matcher = BLOCK.matcher(text);
        List<Start> starts = new ArrayList<>();
        while (matcher.find() && starts.size() < 600) starts.add(new Start(matcher));
        if (starts.isEmpty()) return Collections.emptyList();

        Map<String, String> snapshots = findSnapshots(doc);
        Map<String, Feed> unique = new LinkedHashMap<>();
        for (int i = 0; i < starts.size(); i++) {
            Start s = starts.get(i);
            int next = (i + 1 < starts.size()) ? starts.get(i + 1).begin : text.length();
            String tail = text.substring(s.end, Math.min(text.length(), next));
            String previous = text.substring(Math.max(0, s.begin - 130), s.begin);

            Feed f = new Feed();
            f.satellite = s.satellite;
            f.frequency = s.frequency;
            f.polarization = s.polarization;
            f.symbolRate = s.symbolRate;
            f.status = lastStatus(previous);
            f.name = first(NAME, tail, "").replaceAll("\\s+", " ").trim();
            if (f.name.isEmpty()) f.name = "FEED " + f.frequency;
            if (f.name.length() > 100) f.name = f.name.substring(0, 100);
            f.codec = first(CODEC, tail, "Unknown").replaceAll("\\s+", " ").trim();
            f.chroma = tail.contains("4:2:2") ? "4:2:2" : (tail.contains("4:2:0") ? "4:2:0" : "—");
            f.bitrate = first(BITRATE, tail, "");
            f.updated = first(TIME, previous, "");
            f.imageUrl = snapshots.getOrDefault(key(s.frequency, s.polarization, s.symbolRate), "");
            // Prefer live feed entries if duplicate frequency/title records exist.
            Feed prior = unique.get(f.id());
            if (prior == null || (!prior.isLive() && f.isLive()) || (prior.imageUrl.isEmpty() && !f.imageUrl.isEmpty())) {
                unique.put(f.id(), f);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static String first(Pattern p, String s, String fallback) {
        Matcher m = p.matcher(s);
        // TIME has no capturing groups; asking for group(1) used to crash the entire parser.
        return m.find() ? m.group(m.groupCount() > 0 ? 1 : 0) : fallback;
    }
    private static String lastStatus(String s) {
        Matcher m = STATUS.matcher(s);
        String result = "—";
        while (m.find()) result = m.group(1).toUpperCase(Locale.ROOT);
        return result;
    }
    private static String key(String f, String p, String sr) { return f + "|" + p + "|" + sr; }

    private static Map<String, String> findSnapshots(Document doc) {
        Map<String, String> result = new HashMap<>();
        for (Element img : doc.select("img")) {
            String src = imageUrl(img);
            if (src.isEmpty()) continue;
            Element parent = img;
            for (int j = 0; parent != null && j < 9; parent = parent.parent(), j++) {
                String text = parent.text();
                if (text.length() > 1650) break;
                Matcher m = BLOCK.matcher(text);
                if (m.find()) {
                    String k = key(m.group(2), m.group(3), m.group(4));
                    if (!m.find()) {
                        result.putIfAbsent(k, src);
                        break;
                    }
                }
                // Some cards omit the satellite label and only show a bare transponder.
                Matcher bare = Pattern.compile("(\\d{4,5})\\s+([HVLR])\\s+(\\d{3,6})").matcher(text);
                if (bare.find()) {
                    String k = key(bare.group(1), bare.group(2), bare.group(3));
                    if (!bare.find() && text.length() < 600) {
                        result.putIfAbsent(k, src);
                        break;
                    }
                }
            }
        }
        return result;
    }
    private static String imageUrl(Element img) {
        String[] attrs = {"data-src", "data-original", "data-lazy-src", "src"};
        for (String a : attrs) {
            String value = img.attr(a);
            if (value == null || value.isEmpty() || value.startsWith("data:") || value.startsWith("blob:")) continue;
            try {
                URI uri = URI.create(SITE).resolve(value);
                if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null) return uri.toString();
            } catch (IllegalArgumentException ignored) { }
        }
        return "";
    }

    public static void createChannel(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "الفيدات الجديدة", NotificationManager.IMPORTANCE_DEFAULT));
    }

    public static synchronized void processNew(Context ctx, List<Feed> feeds) {
        SharedPreferences p = ctx.getSharedPreferences("livefeed_settings", Context.MODE_PRIVATE);
        Set<String> old = p.getStringSet("seen_live_feeds", null);
        Set<String> seen = old == null ? new HashSet<>() : new HashSet<>(old);
        boolean initial = old == null;
        int announced = 0;
        for (Feed f : feeds) {
            if (!f.isLive()) continue;
            if (!seen.contains(f.id()) && !initial && announced < 5) {
                if (p.getBoolean("notify", true)) notifyNew(ctx, f);
                // Show one overlay per poll rather than rapidly replacing five banners.
                if (announced == 0 && p.getBoolean("overlay", false)) FloatingAlert.show(ctx, f);
                announced++;
            }
            seen.add(f.id());
        }
        // Preserve the newest entries without growing the preference indefinitely.
        if (seen.size() > 1500) {
            Set<String> current = new HashSet<>();
            for (Feed f : feeds) if (f.isLive()) current.add(f.id());
            seen = current;
        }
        p.edit().putStringSet("seen_live_feeds", seen).apply();
    }

    private static void notifyNew(Context ctx, Feed f) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        createChannel(ctx);
        Intent open = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra("selected_feed", f.id());
        PendingIntent pi = PendingIntent.getActivity(ctx, f.id().hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_satellite)
                .setContentTitle("📡 فيد جديد: " + f.name)
                .setContentText(f.summary())
                .setStyle(new Notification.BigTextStyle().bigText(f.summary() + " • " + f.chroma))
                .setContentIntent(pi).setAutoCancel(true).build();
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(f.id().hashCode(), n);
    }
}
