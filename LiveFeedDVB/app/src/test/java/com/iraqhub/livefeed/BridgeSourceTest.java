package com.iraqhub.livefeed;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class BridgeSourceTest {
    @Test public void parseAuthorizedDvbBridgeResponse() throws Exception {
        JSONObject feed = new JSONObject();
        feed.put("satellite", "Eutelsat 16A (16.0°E)");
        feed.put("frequency", "12737");
        feed.put("polarization", "H");
        feed.put("symbolRate", "2666");
        feed.put("name", "Religious by TRWAM");
        feed.put("sourceUrl", "https://t.me/livefeeds1/321");
        feed.put("status", "REPORTED");
        JSONArray array = new JSONArray().put(feed);
        List<FeedRepository.Feed> result = BridgeSource.parse(array);
        assertEquals(1, result.size());
        assertFalse(result.get(0).isLive());
        assertTrue(result.get(0).isReported());
        assertEquals("12737 H 2666", result.get(0).frequencyText());
        assertEquals(BridgeSource.SOURCE_NAME, result.get(0).source);
    }

    @Test public void refusesNonHttpsAndInlineCredentials() {
        try {
            BridgeSource.canonicalUrl("http://localhost:8000");
            fail("Cleartext forbidden");
        } catch (IllegalArgumentException expected) { }
        try {
            BridgeSource.canonicalUrl("https://user:password@example.com");
            fail("Embedded credentials forbidden");
        } catch (IllegalArgumentException expected) { }
        assertEquals("https://feeds.example", BridgeSource.canonicalUrl("https://feeds.example/v1/feeds"));
    }

    @Test public void malformedDvbDataIsDropped() throws Exception {
        JSONArray array = new JSONArray();
        array.put(new JSONObject().put("frequency", "abc").put("satellite", "X")
                .put("polarization", "V").put("symbolRate", "7200"));
        assertTrue(BridgeSource.parse(array).isEmpty());
    }
}
