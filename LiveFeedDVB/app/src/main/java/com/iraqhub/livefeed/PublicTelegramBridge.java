package com.iraqhub.livefeed;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * End-user auto-discovery for a public, READ-ONLY satellite feed proxy.
 * There are no Telegram logins, session strings, API hashes or tokens in APKs.
 * Developer publishes the HTTPS proxy URL in a repository-controlled JSON config.
 * Until it is deployed, Telegram source is deliberately marked unconfigured.
 */
public final class PublicTelegramBridge {
    private static final String CONFIG_URL =
            "https://raw.githubusercontent.com/mostafaaqeel484-dot/IRAQhub/"
                    + "feature/livefeed-dvb-android-tv-20261117/"
                    + "LiveFeedDVB/telegram-bridge/public-config.json";
    private static final long CONFIG_TTL = 5 * 60_000L;
    private static final long FEED_TTL = 10_000L;
    private static long lastConfigCheck = -1L;
    private static long lastFeedCheck = -1L;
    private static String endpoint = "";
    private static String diagnostic = "تيليگرام التلقائي: لم يتم فحص إعدادات الخادم";
    private static List<FeedRepository.Feed> cached = new ArrayList<>();
    private PublicTelegramBridge() {}

    public static String diagnostic() { return diagnostic; }

    public static synchronized List<FeedRepository.Feed> fetch(Context ignored) throws IOException {
        long now = SystemClock.elapsedRealtime();
        if (lastConfigCheck < 0 || now - lastConfigCheck >= CONFIG_TTL) {
            lastConfigCheck = now;
            try {
                JSONObject config = getJson(CONFIG_URL, 5000, 70_000);
                String candidate = config.optBoolean("enabled", false)
                        ? config.optString("url", "") : "";
                if (!candidate.isEmpty()) {
                    candidate = BridgeSource.canonicalUrl(candidate);
                    if (candidate.contains("/v1/") || candidate.contains("@")) {
                        throw new IOException("عنوان خدمة تيليگرام غير صالح");
                    }
                }
                if (!candidate.equals(endpoint)) {
                    endpoint = candidate;
                    cached = new ArrayList<>();
                    lastFeedCheck = -1L;
                }
                if (endpoint.isEmpty())
                    diagnostic = "المصدر مهيّأ بالتطبيق لكن خادم Telegram Client API العام لم يُفعّل بعد";
            } catch (Exception error) {
                diagnostic = "تعذر قراءة إعدادات المصدر العامة: "
                        + error.getClass().getSimpleName();
                // Keep last known endpoint if remote configuration is temporarily offline.
            }
        }
        if (endpoint.isEmpty()) return new ArrayList<>();
        if (lastFeedCheck >= 0 && now - lastFeedCheck < FEED_TTL)
            return new ArrayList<>(cached);
        lastFeedCheck = now;
        try {
            JSONObject response = getJson(endpoint + "/v1/public-feeds", 8000, 600_000);
            JSONArray feeds = response.optJSONArray("feeds");
            List<FeedRepository.Feed> parsed = BridgeSource.parse(feeds);
            if (!response.optBoolean("ok", false)) {
                diagnostic = "خادم تيليگرام متصل لكن قراءة القناة فشلت: تحقق من جلسة الخادم";
                // Cache previously verified results rather than publish a false healthy status.
                return new ArrayList<>(cached);
            }
            cached = parsed;
            diagnostic = "Telegram Client API متصل ✅ — " + cached.size()
                    + " منشور DVB خلال آخر "
                    + response.optInt("maxAgeHours", 24) + " ساعة";
            return new ArrayList<>(cached);
        } catch (IOException error) {
            diagnostic = "خادم Telegram Client API غير متاح: "
                    + error.getClass().getSimpleName();
            return new ArrayList<>(cached);
        } catch (Exception error) {
            diagnostic = "تنسيق بيانات تيليگرام غير صالح";
            return new ArrayList<>(cached);
        }
    }

    private static JSONObject getJson(String url, int timeout, int maxBytes)
            throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "LiveFeedDVB/1.3");
            if (connection.getResponseCode() != 200)
                throw new IOException("HTTP " + connection.getResponseCode());
            if (connection.getContentLengthLong() > maxBytes)
                throw new IOException("Response too large");
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                char[] chars = new char[4096];
                int count;
                while ((count = reader.read(chars)) >= 0) {
                    body.append(chars, 0, count);
                    if (body.length() > maxBytes) throw new IOException("Response too large");
                }
            }
            return new JSONObject(body.toString());
        } finally {
            connection.disconnect();
        }
    }
}
