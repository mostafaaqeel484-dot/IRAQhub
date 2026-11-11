package com.smarthub.plus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only fallback artwork matched against the supplied DVB catalogues. */
final class ReceiverLogoAssets {
    private static final String DIRECTORY = "receiver-logos/";
    private static volatile JSONObject index;

    private ReceiverLogoAssets() {}

    private static synchronized JSONObject index(Context context) {
        if (index != null) return index;
        try (InputStream input = context.getAssets().open(DIRECTORY + "index.json")) {
            index = new JSONObject(new String(AdbLogoBundleImporter.limited(input, 4 * 1024 * 1024),
                StandardCharsets.UTF_8));
        } catch (Exception unavailable) {
            index = new JSONObject();
        }
        return index;
    }

    private static JSONArray entry(Context context, String name, String physical) {
        boolean exact = physical != null && physical.startsWith("DVB:");
        JSONObject rows = index(context).optJSONObject(exact ? "byDvb" : "byName");
        if (rows == null) return null;
        Object value = rows.opt(exact ? physical : normalizedName(name));
        if (value instanceof JSONArray) return (JSONArray) value;
        // Version 1 contained only a local file id.
        if (value instanceof String) return new JSONArray().put("").put(value);
        return null;
    }

    private static String logoId(JSONArray entry) {
        String id = entry == null ? "" : entry.optString(1);
        return id.matches("[A-Za-z0-9]+\\.[A-Za-z]{2}") ? id : "";
    }

    static String normalizedName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
            .replaceFirst("(?i)\\s+(?:FHD|HD|SD|UHD|4K)$", "")
            .toUpperCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    static Bitmap load(Context context, String name, String physical) {
        String id = logoId(entry(context, name, physical));
        if (id.isEmpty()) return null;
        String asset = DIRECTORY + id + ".png";
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = context.getAssets().open(asset)) {
            BitmapFactory.decodeStream(input, null, options);
        } catch (Exception unavailable) {
            return null;
        }
        if (options.outWidth < 1 || options.outHeight < 1) return null;
        options.inSampleSize = LogoImageSizing.sample(options.outWidth, options.outHeight, 512);
        options.inJustDecodeBounds = false;
        try (InputStream input = context.getAssets().open(asset)) {
            return BitmapFactory.decodeStream(input, null, options);
        } catch (Exception unavailable) {
            return null;
        }
    }

    /** Published HTTPS artwork for an exact catalogue match. Downloaded only
     * on the repository IO pool and then retained in the permanent disk cache. */
    static String remoteUrl(Context context, String name, String physical) {
        String id = logoId(entry(context, name, physical));
        if (id.isEmpty()) return "";
        String url = index(context).optJSONObject("urls") == null ? "" :
            index(context).optJSONObject("urls").optString(id);
        return url.startsWith("https://") && url.length() <= 1000 ? url : "";
    }

    /** A distinct local identity for every DVB service without published art.
     * This is deliberately rendered from the real channel name, never guessed. */
    static Bitmap identity(Context context, String name, String physical) {
        JSONArray row = entry(context, name, physical);
        String label = row == null ? name : row.optString(0, name);
        if (label == null || label.trim().isEmpty()) return null;
        label = label.trim();
        Bitmap bitmap = Bitmap.createBitmap(512, 288, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int hash = normalizedName(label).hashCode();
        float hue = (float) Math.floorMod(hash, 360);
        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(Color.HSVToColor(235, new float[]{hue, .72f, .48f}));
        canvas.drawRoundRect(new RectF(8, 8, 504, 280), 38, 38, background);
        Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
        accent.setColor(Color.HSVToColor(new float[]{(hue + 38f) % 360f, .78f, .98f}));
        canvas.drawRoundRect(new RectF(34, 32, 478, 43), 6, 6, accent);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        float size = label.length() <= 12 ? 66f : label.length() <= 22 ? 52f : 40f;
        text.setTextSize(size);
        String first = label, second = "";
        if (label.length() > 18) {
            int split = label.lastIndexOf(' ', Math.min(label.length() - 1, label.length() / 2 + 4));
            if (split < 5) split = label.indexOf(' ', label.length() / 2);
            if (split > 4) { first = label.substring(0, split).trim(); second = label.substring(split + 1).trim(); }
        }
        canvas.drawText(first, 256, second.isEmpty() ? 170 : 140, text);
        if (!second.isEmpty()) canvas.drawText(second, 256, 205, text);
        return bitmap;
    }
}
