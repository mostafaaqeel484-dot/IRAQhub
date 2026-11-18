package com.iraqhub.livefeed;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTPS + bearer token client for a separately hosted, authorized Telethon bridge.
 * Telegram API ID/hash, login codes, and Telethon sessions NEVER go into the APK.
 */
public final class BridgeSource {
    public static final String SOURCE_NAME = "Telegram • Ben 10 feeds (Client API)";
    private static final String PREFS = "livefeed_bridge";
    private static final String KEY_ALIAS = "LiveFeedDvbTelegramBridgeTokenV1";
    private static volatile String status = "لم يتم ربط Telegram Client API";
    private BridgeSource() {}

    public static String diagnostic() { return status; }
    public static String url(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("url", "");
    }
    public static boolean isConfigured(Context context) {
        return !url(context).isEmpty() && !readToken(context).isEmpty();
    }
    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        status = "تم حذف إعدادات Telegram Client API من التطبيق";
    }

    public static String canonicalUrl(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("أدخل عنوان HTTPS للخادم");
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("عنوان الخادم غير صالح");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("المطلوب رابط HTTPS آمن بدون بيانات دخول بالرابط");
        }
        String output = uri.toString();
        while (output.endsWith("/")) output = output.substring(0, output.length() - 1);
        if (output.endsWith("/v1/feeds")) output = output.substring(0, output.length() - 9);
        return output;
    }

    public static void save(Context context, String enteredUrl, String newToken)
            throws GeneralSecurityException {
        String base = canonicalUrl(enteredUrl);
        String token = newToken == null || newToken.isEmpty() ? readToken(context) : newToken.trim();
        if (token == null || token.length() < 32)
            throw new IllegalArgumentException("رمز الخادم يجب أن يكون عشوائياً وطوله 32 حرفاً على الأقل");
        String encrypted = encrypt(token);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("url", base).putString("bearer", encrypted).apply();
        status = "تم حفظ عنوان الخادم؛ اضغط فحص الآن للتحقق من اتصال Client API";
        // Preserve existing non-Telegram feed baselines and prevent replaying historical channel messages.
        context.getSharedPreferences("livefeed_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("bridge_baseline_done", false).apply();
    }

    private static SecretKey key() throws GeneralSecurityException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        try {
            store.load(null);
        } catch (IOException error) {
            throw new GeneralSecurityException("Keystore unavailable", error);
        }
        KeyStore.Entry entry = store.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry)
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }

    private static String encrypt(String plain) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
                Base64.encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }
    private static String readToken(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = p.getString("bearer", "");
        if (stored == null || stored.isEmpty()) return "";
        try {
            String[] parts = stored.split(":", 2);
            if (parts.length != 2) return "";
            byte[] iv = Base64.decode(parts[0], Base64.DEFAULT);
            byte[] payload = Base64.decode(parts[1], Base64.DEFAULT);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        } catch (Exception error) {
            status = "تعذر فك تشفير رمز الخادم؛ امسح الربط وأعد إدخال الرمز";
            return "";
        }
    }

    public static List<FeedRepository.Feed> fetch(Context context) throws IOException {
        String base = url(context);
        String token = readToken(context);
        if (base.isEmpty() || token.isEmpty()) {
            status = "لم يتم إعداد رابط HTTPS ورمز Client API";
            return new ArrayList<>();
        }
        HttpsURLConnection c = null;
        try {
            c = (HttpsURLConnection) new URL(base + "/v1/feeds").openConnection();
            c.setConnectTimeout(7000);
            c.setReadTimeout(7000);
            c.setInstanceFollowRedirects(false); // Never forward bearer credentials to another host.
            c.setRequestMethod("GET");
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setRequestProperty("Accept", "application/json");
            int http = c.getResponseCode();
            if (http != 200) {
                status = "Client API: HTTP " + http +
                        (http == 401 ? " — رمز الخادم غير صحيح" : " — راجع إعدادات الخادم");
                throw new IOException(status);
            }
            if (c.getContentLengthLong() > 600_000L) throw new IOException("استجابة كبيرة غير متوقعة");
            StringBuilder text = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                char[] chars = new char[4096];
                int n;
                while ((n = reader.read(chars)) >= 0) {
                    text.append(chars, 0, n);
                    if (text.length() > 600_000) throw new IOException("تجاوز حجم استجابة الخادم");
                }
            }
            JSONObject root = new JSONObject(text.toString());
            JSONArray rows = root.optJSONArray("feeds");
            List<FeedRepository.Feed> feeds = parse(rows);
            boolean ok = root.optBoolean("ok", false);
            status = (ok ? "متصل ✅ " : "متصل لكن هناك خطأ قراءة ⚠️ ") +
                    "— " + feeds.size() + " منشور DVB خلال آخر "
                    + root.optInt("maxAgeHours", 24) + " ساعة";
            return feeds;
        } catch (IOException error) {
            if (!status.contains("HTTP")) status = "فشل اتصال Client API: " + error.getClass().getSimpleName();
            throw error;
        } catch (Exception error) {
            status = "استجابة Client API غير مفهومة: " + error.getClass().getSimpleName();
            throw new IOException(status, error);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static List<FeedRepository.Feed> parse(JSONArray rows) {
        List<FeedRepository.Feed> result = new ArrayList<>();
        if (rows == null) return result;
        for (int i = 0; i < Math.min(rows.length(), 80); i++) {
            JSONObject x = rows.optJSONObject(i);
            if (x == null) continue;
            String sat = x.optString("satellite", "");
            String freq = x.optString("frequency", "");
            String pol = x.optString("polarization", "");
            String sr = x.optString("symbolRate", "");
            if (sat.isEmpty() || !freq.matches("\\d{4,5}") ||
                    !pol.matches("[HVLR]") || !sr.matches("\\d{3,6}")) continue;
            FeedRepository.Feed f = new FeedRepository.Feed();
            f.satellite = sat;
            f.frequency = freq;
            f.polarization = pol;
            f.symbolRate = sr;
            f.name = x.optString("name", "Ben 10 • " + freq);
            f.codec = x.optString("codec", "Unknown");
            f.chroma = x.optString("chroma", "—");
            f.category = x.optString("category", "");
            f.updated = x.optString("updated", "");
            f.status = "REPORTED";
            f.source = SOURCE_NAME;
            f.sources = SOURCE_NAME;
            f.sourceUrl = x.optString("sourceUrl", TelegramFeedSource.CHANNEL_URL);
            f.imageUrl = x.optString("imageUrl", "");
            f.bitrate = "";
            result.add(f);
        }
        return result;
    }
}
