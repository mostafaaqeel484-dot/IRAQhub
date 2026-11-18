package com.iraqhub.livefeed;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class TelegramFeedSourceTest {
    @Test public void readsPublicBen10MessageIntoDvbFields() {
        String html = "<html><body><div class='tgme_widget_message_wrap'>" +
                "<div class='tgme_widget_message' data-post='livefeeds1/239'>" +
                "<div class='tgme_widget_message_text js-message_text'>" +
                "📡 Satellite Feed 🛰 Satellite: Eutelsat 16A (16.0°E) " +
                "📶 Frequency: 12737 H 2666 ⚙ FEC: - " +
                "🏷 Category: Other 📺 Transmission: MPEG-4 " +
                "🎬 Event: Religious by TRWAM 🕒 Posted: 2026-11-18 09:04 am " +
                "🔗 Owner: BEN 10</div>" +
                "<a class='tgme_widget_message_photo_wrap' " +
                "style='background-image:url(https://cdn1.telegram-cdn.org/file/test.jpg)'></a>" +
                "<a class='tgme_widget_message_date'><time datetime='2026-11-18T09:04:00+00:00'></time></a>" +
                "</div></div></body></html>";
        List<FeedRepository.Feed> feeds = TelegramFeedSource.parse(html);
        assertEquals(1, feeds.size());
        FeedRepository.Feed f = feeds.get(0);
        assertEquals("Eutelsat 16A (16.0°E)", f.satellite);
        assertEquals("12737 H 2666", f.frequencyText());
        assertEquals("Religious by TRWAM", f.name);
        assertEquals("MPEG-4", f.codec);
        assertTrue(f.isReported());
        assertFalse(f.isLive());
        assertEquals("https://t.me/livefeeds1/239", f.sourceUrl);
        assertEquals("https://cdn1.telegram-cdn.org/file/test.jpg", f.imageUrl);
        assertEquals("2026-11-18T09:04:00+00:00", f.updated);
    }

    @Test public void ignoresNonFeedTelegramPosts() {
        String html = "<div class='tgme_widget_message_wrap'>" +
                "<div class='tgme_widget_message' data-post='livefeeds1/240'>" +
                "<div class='tgme_widget_message_text'>Our bot for today's matches</div>" +
                "</div></div>";
        assertTrue(TelegramFeedSource.parse(html).isEmpty());
    }

    @Test public void mergeThreeSourcesOnlyOnce() {
        FeedRepository.Feed primary = make("Live-Feed.net", "ON", "Eutelsat 16A @ 16.0°E");
        FeedRepository.Feed website = make("Satelliweb", "REPORTED", "Eutelsat 16A (16.0°E)");
        FeedRepository.Feed telegram = make(TelegramFeedSource.SOURCE_NAME,
                "REPORTED", "Eutelsat 16A (16.0°E)");
        List<FeedRepository.Feed> merged = MultiSourceFeeds.merge(
                Arrays.asList(primary), Arrays.asList(website), Arrays.asList(telegram));
        assertEquals(1, merged.size());
        assertTrue(merged.get(0).isLive());
        assertTrue(merged.get(0).sources.contains("Satelliweb"));
        assertTrue(merged.get(0).sources.contains("Telegram"));
    }

    private static FeedRepository.Feed make(String source, String status, String sat) {
        FeedRepository.Feed feed = new FeedRepository.Feed();
        feed.satellite = sat; feed.frequency = "12737"; feed.polarization = "H";
        feed.symbolRate = "2666"; feed.name = "Religious by TRWAM";
        feed.source = source; feed.sources = source; feed.status = status;
        feed.imageUrl = ""; feed.codec = "MPEG-4"; feed.chroma = "—";
        return feed;
    }
}
