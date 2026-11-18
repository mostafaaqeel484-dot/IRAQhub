package com.iraqhub.livefeed;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class FeedRepositoryTest {
    @Test public void parsesCurrentLiveFeedLayoutWithoutCrashingOnTimestamp() {
        String html = "<html><body>" +
                "<article><span>11059 H 7200</span>" +
                "<img src='https://live-feed.net/assets/capture.jpg'/>" +
                "<p>ON 🔓 KEY FOUND 2026-11-17 14:27:22</p>" +
                "<p>📡Eutelsat Hotbird 13B/13C/13G @ 13.0°E</p>" +
                "<p>📶11059 H 7200</p>" +
                "<p>🎬1920×1080 | H.264/AVC</p>" +
                "<p>📊4:2:2 | 14.48 Mb/s</p>" +
                "<p>🆔IMG 1 EU</p>" +
                "</article></body></html>";
        List<FeedRepository.Feed> feeds = FeedRepository.parse(html);
        assertEquals(1, feeds.size());
        FeedRepository.Feed f = feeds.get(0);
        assertTrue(f.isLive());
        assertTrue(f.is422());
        assertEquals("11059 H 7200", f.frequencyText());
        assertEquals("IMG 1 EU", f.name);
        assertEquals("14.48", f.bitrate);
        assertEquals("2026-11-17 14:27:22", f.updated);
        assertEquals("https://live-feed.net/assets/capture.jpg", f.imageUrl);
    }
    @Test public void parsesOfflineAndClearFeed() {
        String html = "<body><div>OFF Clear 2026-11-16 18:01:34 " +
                "📡Eutelsat 16A @ 16.0°E 📶12630 V 7200 " +
                "🎬1920×1080 | H.265/HEVC 📊4:2:0 | 7.40 Mb/s 🆔TEST FEED</div></body>";
        List<FeedRepository.Feed> feeds = FeedRepository.parse(html);
        assertEquals(1, feeds.size());
        assertFalse(feeds.get(0).isLive());
        assertFalse(feeds.get(0).is422());
        assertEquals("TEST FEED", feeds.get(0).name);
    }
}
