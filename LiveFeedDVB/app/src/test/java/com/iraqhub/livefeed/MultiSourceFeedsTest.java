package com.iraqhub.livefeed;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public final class MultiSourceFeedsTest {
    @Test public void parsesPublicSatelliwebLiveFeedReports() {
        String html = "<html><body><main>" +
            "Reported by stanley_cassidy on 2026-11-18 05:02 am 0 comment " +
            "Eutelsat 16A (16.0°E) Frequency: 11051 - Pol: H - SR: 3200 - FEC: - " +
            "Category: Newsfeed Transmitted in: MPEG-4 4:2:2 ℹ Czech News " +
            "Reported by stanley_cassidy on 2026-11-18 05:01 am 0 comment " +
            "Eutelsat 21B (21.5°E) Frequency: 11490 - Pol: V - SR: 1800 - FEC: - " +
            "Category: Newsfeed Transmitted in: MPEG-4 ℹ Beirut skyline" +
            "</main></body></html>";
        List<FeedRepository.Feed> feeds = MultiSourceFeeds.parseSatelliweb(html);
        assertEquals(2, feeds.size());
        assertEquals("Satelliweb", feeds.get(0).source);
        assertEquals("11051 H 3200", feeds.get(0).frequencyText());
        assertEquals("4:2:2", feeds.get(0).chroma);
        assertTrue(feeds.get(0).isReported());
        assertFalse(feeds.get(0).isLive());
        assertEquals("Czech News", feeds.get(0).name);
        assertEquals("Beirut skyline", feeds.get(1).name);
    }

    @Test public void mergesSameTransponderEvenWhenSatelliteLabelsDiffer() {
        FeedRepository.Feed primary = feed("Eutelsat 16A @ 16.0E",
                "11051", "H", "3200", "CZE 0441", "Live-Feed.net", "ON");
        primary.imageUrl = "https://live-feed.net/picture.png";
        FeedRepository.Feed other = feed("Eutelsat 16A (16.0°E)",
                "11051", "H", "03200", "Czech News", "Satelliweb", "REPORTED");
        List<FeedRepository.Feed> merged = MultiSourceFeeds.merge(
                Arrays.asList(primary), Arrays.asList(other));
        assertEquals(1, merged.size());
        assertEquals("Live-Feed.net + Satelliweb", merged.get(0).sources);
        assertEquals("https://live-feed.net/picture.png", merged.get(0).imageUrl);
        assertTrue(merged.get(0).isLive());
    }

    @Test public void differentPolarizationOrSymbolRateMustNotMerge() {
        FeedRepository.Feed a = feed("13.0°E", "10840", "H", "7200", "A", "Live-Feed.net", "ON");
        FeedRepository.Feed b = feed("13.0°E", "10840", "V", "7200", "B", "Satelliweb", "REPORTED");
        FeedRepository.Feed c = feed("13.0°E", "10840", "H", "7300", "C", "Satelliweb", "REPORTED");
        assertEquals(3, MultiSourceFeeds.merge(Arrays.asList(a), Arrays.asList(b,c)).size());
    }

    @Test public void sameNameOnDifferentSatelliteIsNotSameFeed() {
        FeedRepository.Feed a = feed("Eutelsat 16A @ 16.0E", "11051", "H", "3200",
                "Football Feed", "Live-Feed.net", "ON");
        FeedRepository.Feed b = feed("Hotbird @ 13.0E", "11051", "H", "3200",
                "Football Feed", "Satelliweb", "REPORTED");
        assertEquals(2, MultiSourceFeeds.merge(Arrays.asList(a), Arrays.asList(b)).size());
    }

    private static FeedRepository.Feed feed(String sat, String freq, String pol,
                                            String sr, String name, String source, String status) {
        FeedRepository.Feed f = new FeedRepository.Feed();
        f.satellite = sat; f.frequency = freq; f.polarization = pol;
        f.symbolRate = sr; f.name = name;
        f.source = source; f.sources = source; f.status = status;
        f.imageUrl = ""; f.codec = "MPEG-4"; f.chroma = "—"; f.category = "";
        return f;
    }
}
