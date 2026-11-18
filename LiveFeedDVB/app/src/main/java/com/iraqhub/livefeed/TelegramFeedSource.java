package com.iraqhub.livefeed;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort read-only parser for the PUBLIC Telegram preview.
 * No user token or channel credentials are required IF Telegram actually
 * exposes this particular channel's posts on the /s/ preview page.
 * It may be blocked, unavailable, or rate-limited; never treat posts as tuned ON AIR.
 */
public final class TelegramFeedSource {
    public static final String SOURCE_NAME = "Telegram • Ben 10 feeds";
    public static final String CHANNEL_URL = "https://t.me/livefeeds1";
    public static final String PUBLIC_PREVIEW = "https://t.me/s/livefeeds1";

    private static final Pattern FREQUENCY = Pattern.compile(
            "(?i)(?:Frequency\\s*:\\s*|📶\\s*)(\\d{4,5})\\s*([HVLR])\\s*(\\d{3,6})");
    private static final Pattern SATELLITE = Pattern.compile(
            "(?is)Satellite\\s*:\\s*(.+?)(?=\\s*(?:Frequency\\s*:|📶|FEC\\s*:|Category\\s*:|Transmission\\s*:|Event\\s*:|Posted\\s*:|Owner\\s*:|$))");
    private static final Pattern EVENT = Pattern.compile(
            "(?is)Event\\s*:\\s*(.+?)(?=\\s*(?:Posted\\s*:|Owner\\s*:|Frequency\\s*:|Satellite\\s*:|$))");
    private static final Pattern TRANSMISSION = Pattern.compile(
            "(?is)Transmission\\s*:\\s*(.+?)(?=\\s*(?:Event\\s*:|Posted\\s*:|Owner\\s*:|$))");
    private static final Pattern CATEGORY = Pattern.compile(
            "(?is)Category\\s*:\\s*(.+?)(?=\\s*(?:Transmission\\s*:|Event\\s*:|Posted\\s*:|$))");
    private static final Pattern PHOTO = Pattern.compile("(?is)url\\(\\s*['\\\"]?([^'\\\"\\)]+)");

    private TelegramFeedSource() {}

    public static List<FeedRepository.Feed> fetch() throws IOException {
        String html = Jsoup.connect(PUBLIC_PREVIEW)
                .userAgent("Mozilla/5.0 (Linux; Android 14; Android TV) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .timeout(9500).maxBodySize(5_000_000).get().outerHtml();
        return parse(html);
    }

    public static List<FeedRepository.Feed> parse(String html) {
        if (html == null || html.isEmpty()) return Collections.emptyList();
        Document page = Jsoup.parse(html, PUBLIC_PREVIEW);
        List<FeedRepository.Feed> result = new ArrayList<>();
        for (Element wrapper : page.select(".tgme_widget_message_wrap")) {
            Element msg = wrapper.selectFirst(".tgme_widget_message");
            if (msg == null) continue;
            Element body = msg.selectFirst(".tgme_widget_message_text");
            if (body == null) continue;
            String message = body.text().replace((char) 160, ' ')
                    .replace("🕒", " ").replace("🔗", " ").replace("🎬", " ")
                    .replace("📺", " ").replace("🏷", " ").replace("⚙", " ")
                    .replace("📶", " ").replace("🛰", " ")
                    .replaceAll("\\s+", " ").trim();
            if (!message.toLowerCase(Locale.ROOT).contains("satellite")
                    || !message.toLowerCase(Locale.ROOT).contains("frequency")) continue;
            Matcher tp = FREQUENCY.matcher(message);
            if (!tp.find()) continue;
            String satellite = capture(SATELLITE, message);
            if (satellite.isEmpty() || satellite.length() > 120) continue;
            String event = capture(EVENT, message);
            FeedRepository.Feed f = new FeedRepository.Feed();
            f.satellite = satellite;
            f.frequency = tp.group(1);
            f.polarization = tp.group(2).toUpperCase(Locale.ROOT);
            f.symbolRate = tp.group(3);
            f.name = event.isEmpty() ? "Ben 10 • " + f.frequencyText() : truncate(event, 100);
            f.category = truncate(capture(CATEGORY, message), 80);
            String transmission = truncate(capture(TRANSMISSION, message), 100);
            f.codec = transmission.isEmpty() ? "Unknown" : transmission;
            f.chroma = transmission.contains("4:2:2") ? "4:2:2"
                    : (transmission.contains("4:2:0") ? "4:2:0" : "—");
            f.bitrate = "";
            // Channel posts are sightings, NOT proof that the signal is currently live.
            f.status = "REPORTED";
            f.source = SOURCE_NAME;
            f.sources = SOURCE_NAME;
            String post = msg.attr("data-post");
            f.sourceUrl = post.startsWith("livefeeds1/") ? "https://t.me/" + post : CHANNEL_URL;
            Element photo = msg.selectFirst(".tgme_widget_message_photo_wrap");
            f.imageUrl = photo != null ? photoUrl(photo.attr("style")) : "";
            Element time = msg.selectFirst("time[datetime]");
            f.updated = time == null ? "" : time.attr("datetime");
            result.add(f);
            if (result.size() >= 60) break;
        }
        return result;
    }

    private static String capture(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String truncate(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit) : text;
    }

    private static String photoUrl(String style) {
        Matcher matcher = PHOTO.matcher(style);
        if (!matcher.find()) return "";
        String url = matcher.group(1).replace("&amp;", "&");
        return url.startsWith("https://") ? url : "";
    }
}
