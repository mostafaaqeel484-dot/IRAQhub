package com.gazal.audiosync;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

final class PlaylistStore {
    static final class Source {
        final String name;
        final String url;
        Source(String name, String url) { this.name = name; this.url = url; }
        public String toString() { return name + "  •  " + url; }
    }
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("gazal_sources", Context.MODE_PRIVATE);
    }
    static ArrayList<Source> load(Context c) {
        ArrayList<Source> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(c).getString("sources", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String url = item.optString("url");
                if (valid(url)) result.add(new Source(item.optString("name", "Audio " + (i + 1)), url));
            }
        } catch (Exception ignored) {}
        return result;
    }
    static void save(Context c, List<Source> sources) {
        JSONArray out = new JSONArray();
        for (Source s : sources) {
            JSONObject item = new JSONObject();
            try { item.put("name", s.name); item.put("url", s.url); out.put(item); }
            catch (Exception ignored) {}
        }
        prefs(c).edit().putString("sources", out.toString()).apply();
    }
    static boolean valid(String url) {
        if (url == null) return false;
        String l = url.trim().toLowerCase(java.util.Locale.ROOT);
        return l.startsWith("http://") || l.startsWith("https://");
    }
    static ArrayList<Source> parse(InputStream stream) throws Exception {
        ArrayList<Source> out = new ArrayList<>();
        BufferedReader in = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        String line, title = "";
        while ((line = in.readLine()) != null && out.size() < 500) {
            line = line.trim();
            if (line.startsWith("#EXTINF:")) {
                int comma = line.lastIndexOf(',');
                title = comma >= 0 ? line.substring(comma + 1).trim() : "";
            } else if (valid(line)) {
                if (title.isEmpty()) title = "Audio " + (out.size() + 1);
                out.add(new Source(title, line));
                title = "";
            } else if (line.startsWith("#") || line.isEmpty()) {
                // M3U metadata or comment.
            } else if (line.contains("=")) {
                // Simple text CFG/INI support for source URLs, no proprietary encrypted CFG support.
                int eq = line.indexOf('=');
                String candidate = line.substring(eq + 1).trim().replace("\"", "");
                if (valid(candidate)) {
                    out.add(new Source(title.isEmpty() ? "Audio " + (out.size()+1) : title, candidate));
                    title = "";
                }
            }
        }
        return out;
    }
}
