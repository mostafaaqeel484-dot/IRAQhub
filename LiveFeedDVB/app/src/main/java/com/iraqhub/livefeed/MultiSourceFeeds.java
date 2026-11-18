package com.iraqhub.livefeed;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LiveFeed, Satelliweb and (if publicly accessible) Ben 10 Telegram channel.
 * Human reports are labelled REPORTED, not confirmed current tuner locks.
 */
public final class MultiSourceFeeds {
    public static final String SATELLIWEB = "https://www.satelliweb.com/live-feeds-en.html";
    public static final String PRIMARY = "Live-Feed.net";
    public static final String SECONDARY = "Satelliweb";

    private static final long SECONDARY_REFRESH_MS = 60_000L;
    private static final long TELEGRAM_REFRESH_MS = 15_000L;
    private static long lastSecondaryAttempt = -1L;
    private static long lastTelegramAttempt = -1L;
    private static List<FeedRepository.Feed> cachedSecondary = Collections.emptyList();
    private static List<FeedRepository.Feed> cachedTelegram = Collections.emptyList();
    private static volatile String primaryStatus = "لم يُفحص";
    private static volatile String secondaryStatus = "لم يُفحص";
    public static String diagnostics() {
        return "Live-Feed.net: " + primaryStatus
                + "\n\nSatelliweb: " + secondaryStatus
                + "\n\nBen 10 feeds (Telegram): " + TelegramFeedSource.diagnostic()
                + "\n\nالبيانات المنشورة بتيليگرام بلاغات ولا تؤكد أن البث يعمل حالياً.";
    }
    private static final Pattern FREQUENCY = Pattern.compile(
            "Frequency\\s*:\\s*(\\d{4,5})\\s*-?\\s*Pol\\s*:\\s*([HVLR])\\s*-?\\s*SR\\s*:\\s*(\\d{3,6})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ORBIT = Pattern.compile(
            "(\\d{1,3}(?:\\.\\d+)?)\\s*°?\\s*([EW])", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODEC = Pattern.compile("Transmitted in\\s*:\\s*(.*?)(?=\\s*ℹ|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CATEGORY = Pattern.compile("Category\\s*:\\s*(.*?)(?=\\s*Transmitted in|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private MultiSourceFeeds() {}

    public static synchronized List<FeedRepository.Feed> fetch() throws IOException {
        return fetch(false);
    }

    public static synchronized List<FeedRepository.Feed> fetch(boolean force) throws IOException {
        IOException primaryFailure = null;
        List<FeedRepository.Feed> primary = Collections.emptyList();
        try {
            primary = FeedRepository.fetch(force);
            primaryStatus = "تمت القراءة: " + primary.size() + " فيد";
        } catch (IOException e) {
            primaryFailure = e;
            primaryStatus = "فشل الاتصال: " + e.getClass().getSimpleName();
        }

        long now = android.os.SystemClock.elapsedRealtime();
        boolean shouldFetchSecondary = force || lastSecondaryAttempt < 0
                || now - lastSecondaryAttempt >= SECONDARY_REFRESH_MS;
        if (shouldFetchSecondary) {
            // The secondary website publishes human-reported sightings. Respect its
            // lower fetch cadence while the main source runs on the 5s cycle.
            lastSecondaryAttempt = now;
            try {
                String page = Jsoup.connect(SATELLIWEB)
                        .userAgent("Mozilla/5.0 (Linux; Android 14; Android TV) AppleWebKit/537.36 Chrome/120.0 Safari/537.36")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .timeout(16000).maxBodySize(4_000_000)
                        .get().outerHtml();
                List<FeedRepository.Feed> parsed = parseSatelliweb(page);
                if (!parsed.isEmpty()) cachedSecondary = parsed;
                secondaryStatus = parsed.isEmpty() ?
                        "الصفحة متاحة لكن لم تُستخرج بلاغات" :
                        "تمت القراءة: " + parsed.size() + " بلاغ";
            } catch (Exception error) {
                secondaryStatus = "فشل الاتصال: " + error.getClass().getSimpleName();
                // Keep the last valid secondary response rather than erase its data.
            }
        }
        // Telegram's public preview is unofficial best-effort access, may return
        // no posts, and can be rate-limited. Never block the other two sources.
        if (force || lastTelegramAttempt < 0 || now - lastTelegramAttempt >= TELEGRAM_REFRESH_MS) {
            lastTelegramAttempt = now;
            try {
                List<FeedRepository.Feed> reported = TelegramFeedSource.fetch();
                if (!reported.isEmpty()) cachedTelegram = reported;
            } catch (Exception ignored) {
                // A private/restricted Telegram preview requires an authorized
                // official integration; we do not bypass its access controls.
            }
        }
        if (primary.isEmpty() && cachedSecondary.isEmpty()
                && cachedTelegram.isEmpty() && primaryFailure != null) throw primaryFailure;
        return merge(primary, cachedSecondary, cachedTelegram);
    }

    /**
     * Parse the real public Satelliweb "Live feeds" structure:
     * Reported by ...; Eutelsat 16A (16.0°E); Frequency: 11051 - Pol: H
     * - SR: 3200; Category: Newsfeed; Transmitted in: MPEG-4 4:2:2; ℹ event.
     * These are reports, not verified tuner lock states.
     */
    public static List<FeedRepository.Feed> parseSatelliweb(String html) {
        Document doc = Jsoup.parse(html, SATELLIWEB);
        doc.select("script, style, noscript, nav, footer").remove();
        if (doc.body() == null) return Collections.emptyList();
        String page = doc.body().text().replace('\u00a0', ' ');
        String[] reports = page.split("(?i)(?=Reported by\\s)");
        List<FeedRepository.Feed> out = new ArrayList<>();
        for (String report : reports) {
            if (out.size() >= 200) break;
            Matcher frequency = FREQUENCY.matcher(report);
            if (!frequency.find()) continue;
            String beforeFreq = report.substring(0, frequency.start());
            Matcher orbit = ORBIT.matcher(beforeFreq);
            String position = "";
            int orbitStart = -1;
            while (orbit.find()) {
                position = orbit.group(1) + "°" + orbit.group(2).toUpperCase(Locale.ROOT);
                orbitStart = orbit.start();
            }
            if (position.isEmpty() || orbitStart < 0) continue;
            String sat = beforeFreq.substring(0, orbitStart);
            // Remove the reporter, comment counter and image alt labels.
            sat = sat.replaceFirst("(?is)^.*?\\bcomment(?:s)?\\b", "");
            sat = sat.replaceAll("(?i)\\bImage\\b", "").replaceAll("(?i)\\bcomment\\b", "");
            sat = sat.replaceAll("\\s+", " ").trim();
            sat = sat.replaceAll("[(@\\s]+$", "").trim();
            if (sat.length() > 100) sat = sat.substring(sat.length() - 100);
            if (sat.isEmpty()) sat = "Satellite";
            String tail = report.substring(frequency.end());
            FeedRepository.Feed f = new FeedRepository.Feed();
            f.satellite = sat + " (" + position + ")";
            f.frequency = frequency.group(1);
            f.polarization = frequency.group(2).toUpperCase(Locale.ROOT);
            f.symbolRate = frequency.group(3);
            f.status = "REPORTED";
            f.source = SECONDARY;
            f.sources = SECONDARY;
            f.sourceUrl = SATELLIWEB;
            f.imageUrl = "";
            String codec = capture(CODEC, tail, "Unknown");
            f.codec = codec.length() > 80 ? codec.substring(0, 80) : codec;
            f.chroma = codec.contains("4:2:2") ? "4:2:2" :
                    codec.contains("4:2:0") ? "4:2:0" : "—";
            f.bitrate = "";
            String category = capture(CATEGORY, tail, "");
            f.category = category;
            int marker = tail.indexOf('ℹ');
            f.name = marker >= 0 ? tail.substring(marker + 1).trim() : category;
            if (f.name.isEmpty()) f.name = "Feed " + f.frequency;
            if (f.name.length() > 100) f.name = f.name.substring(0, 100);
            f.updated = "";
            out.add(f);
        }
        return out;
    }

    private static String capture(Pattern pattern, String text, String fallback) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : fallback;
    }

    /** Normalizes position (E/W), transponder frequency, polarization, and SR. */
    public static String transponderKey(FeedRepository.Feed f) {
        if (f == null) return "";
        String satellite = f.satellite == null ? "" : f.satellite;
        Matcher orbit = ORBIT.matcher(satellite);
        String orbital = null;
        while (orbit.find()) {
            try {
                int tenths = Math.round(Float.parseFloat(orbit.group(1)) * 10);
                orbital = (orbit.group(2).equalsIgnoreCase("W") ? "-" : "+") + tenths;
            } catch (NumberFormatException ignored) {}
        }
        if (orbital == null) {
            orbital = satellite.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        }
        String pol = f.polarization == null ? "?" : f.polarization.toUpperCase(Locale.ROOT);
        return orbital + "|" + number(f.frequency) + "|" + pol + "|" + number(f.symbolRate);
    }

    private static String number(String text) {
        try { return Integer.toString(Integer.parseInt(text.trim())); }
        catch (Exception error) { return text == null ? "" : text.trim(); }
    }

    /**
     * Merge cross-site duplicates, keeping the photo/video metadata from
     * Live-Feed.net and appending provenance from other providers. Never
     * discard a live record in favour of a human-reported-only record.
     */
    public static List<FeedRepository.Feed> merge(List<FeedRepository.Feed> primary,
                                                  List<FeedRepository.Feed> secondary) {
        return merge(primary, secondary, Collections.emptyList());
    }

    public static List<FeedRepository.Feed> merge(List<FeedRepository.Feed> primary,
                                                  List<FeedRepository.Feed> secondary,
                                                  List<FeedRepository.Feed> telegram) {
        Map<String, List<FeedRepository.Feed>> groups = new LinkedHashMap<>();
        if (primary != null) for (FeedRepository.Feed f : primary) accept(groups, f);
        if (secondary != null) for (FeedRepository.Feed f : secondary) accept(groups, f);
        if (telegram != null) for (FeedRepository.Feed f : telegram) accept(groups, f);
        List<FeedRepository.Feed> unique = new ArrayList<>();
        for (List<FeedRepository.Feed> group : groups.values()) unique.addAll(group);
        return unique;
    }

    private static String comparableName(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static void accept(Map<String, List<FeedRepository.Feed>> groups,
                               FeedRepository.Feed incoming) {
        if (incoming == null || incoming.frequency == null ||
                incoming.symbolRate == null || incoming.polarization == null) return;
        String key = transponderKey(incoming);
        List<FeedRepository.Feed> group = groups.get(key);
        if (group == null) {
            group = new ArrayList<>();
            groups.put(key, group);
        }
        if (group.isEmpty()) {
            group.add(incoming);
            return;
        }

        // A transponder can contain multiple independent video services.
        // Preserve distinct names from the same site rather than conflating them.
        String candidateName = comparableName(incoming.name);
        FeedRepository.Feed match = null;
        for (FeedRepository.Feed existing : group) {
            if (!candidateName.isEmpty() && candidateName.equals(comparableName(existing.name))) {
                match = existing;
                break;
            }
        }
        if (match == null && group.size() == 1 &&
                incoming.source != null && !incoming.source.equals(group.get(0).source)) {
            // The sources use different labels for the same frequency.
            match = group.get(0);
        }
        if (match == null) {
            // Avoid destructive guessing when two independent services share the
            // same transponder; keep both cards rather than lose a real feed.
            group.add(incoming);
            return;
        }
        mergeInto(match, incoming);
    }

    private static void mergeInto(FeedRepository.Feed existing,
                                  FeedRepository.Feed incoming) {
        String a = existing.sources == null ? "" : existing.sources;
        String b = incoming.sources == null ? incoming.source : incoming.sources;
        if (b != null && !b.isEmpty() && !a.contains(b)) {
            existing.sources = a.isEmpty() ? b : a + " + " + b;
        }
        if ((existing.imageUrl == null || existing.imageUrl.isEmpty())
                && incoming.imageUrl != null && !incoming.imageUrl.isEmpty())
            existing.imageUrl = incoming.imageUrl;
        if ((existing.name == null || existing.name.startsWith("FEED ") ||
                existing.name.startsWith("Feed ")) &&
                incoming.name != null && !incoming.name.isEmpty())
            existing.name = incoming.name;
        if ((existing.chroma == null || "—".equals(existing.chroma)) &&
                incoming.chroma != null && !"—".equals(incoming.chroma))
            existing.chroma = incoming.chroma;
        if ((existing.category == null || existing.category.isEmpty())
                && incoming.category != null) existing.category = incoming.category;
        if ((existing.codec == null || "Unknown".equals(existing.codec))
                && incoming.codec != null) existing.codec = incoming.codec;
        if (!existing.isLive() && incoming.isLive()) existing.status = "ON";
    }
}
