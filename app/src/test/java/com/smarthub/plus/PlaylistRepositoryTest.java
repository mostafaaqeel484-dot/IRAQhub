package com.smarthub.plus;

import org.junit.Test;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import static org.junit.Assert.*;

public final class PlaylistRepositoryTest {
    @Test public void programmeIconIsPreservedWithoutReusingChannelLogo()throws Exception {
        String xml="<tv><channel id='x'><icon src='https://example.org/logo.png'/></channel><programme channel='x' start='20261031000000 +0000' stop='20261031010000 +0000'><title>Film</title><icon src='https://example.org/film.jpg'/></programme><programme channel='x' start='20261031010000 +0000' stop='20261031020000 +0000'><title>Next</title></programme></tv>";
        List<PlaylistRepository.Programme> p=PlaylistRepository.parseXmlTv(xml);assertEquals("https://example.org/film.jpg",p.get(0).image);assertEquals("",p.get(1).image);
    }
    @Test public void epgArtworkRejectsExecutableAndCredentialUris(){assertEquals("",PlaylistRepository.epgImage("javascript:alert(1)"));assertEquals("",PlaylistRepository.epgImage("https://user:password@example.org/film.jpg"));assertEquals("",PlaylistRepository.epgImage("file:///private.jpg"));}
    @Test public void m3uPreservesQuotedCommasMetadataAndStableIdentity()throws Exception {
        String text="#EXTM3U\n#EXTINF:-1 tvg-id=\"news.ar\" tvg-logo=\"https://example.org/logo.png\" group-title=\"News, Arabic\",قناة الأخبار\nhttps://example.org/live.m3u8?token=private\n";
        PlaylistRepository.Channel channel=PlaylistRepository.parseM3u(text,"").get(0);
        assertEquals("قناة الأخبار",channel.name);assertEquals("news.ar",channel.tvgId);assertEquals("News, Arabic",channel.group);assertEquals("https://example.org/logo.png",channel.logo);assertEquals("",channel.catchup);
        String renamed=text.replace("قناة الأخبار","New name");assertEquals(channel.id,PlaylistRepository.parseM3u(renamed,"").get(0).id);
    }
    @Test public void m3uResolvesRelativeStreamsAndSkipsDuplicateAndUnsafeSchemes()throws Exception {
        String text="#EXTM3U\n#EXTINF:-1,A\n/live/one.m3u8\n#EXTINF:-1,Duplicate\nhttps://example.org/live/one.m3u8\n#EXTINF:-1,Bad\njavascript:alert(1)\n";
        List<PlaylistRepository.Channel> channels=PlaylistRepository.parseM3u(text,"https://example.org/user/list.m3u");assertEquals(1,channels.size());assertEquals("https://example.org/live/one.m3u8",channels.get(0).uri);
    }
    @Test public void hlsMediaManifestRemainsOneUserSuppliedStream()throws Exception {
        String hls="#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:6.0,\nsegment1.ts\n";
        List<PlaylistRepository.Channel> channels=PlaylistRepository.parseM3u(hls,"https://example.org/live/index.m3u8");assertEquals(1,channels.size());assertEquals("https://example.org/live/index.m3u8",channels.get(0).uri);
    }
    @Test(expected=IOException.class) public void localHlsWithoutOriginalUrlIsRejected()throws Exception {PlaylistRepository.parseM3u("#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:6,\nsegment.ts","content://docs/tree/playlist");}
    @Test public void streamValidationRejectsHostlessAndExecutableUris(){assertFalse(PlaylistRepository.validStream("http:///missing-host"));assertFalse(PlaylistRepository.validStream("file:///etc/passwd"));assertFalse(PlaylistRepository.validStream("javascript:alert(1)"));assertTrue(PlaylistRepository.validStream("rtsp://example.org/channel"));}
    @Test public void xmlTvKeepsTimesCategoriesAndExplicitRepeat()throws Exception {
        String xml="<tv><programme channel=\"news.ar\" start=\"20261031000000 +0300\" stop=\"20261031010000 +0300\"><title>News &amp; weather</title><desc>Headlines</desc><category>News</category><category>Weather</category><previously-shown/></programme></tv>";
        List<PlaylistRepository.Programme> programmes=PlaylistRepository.parseXmlTv(xml);assertEquals(1,programmes.size());PlaylistRepository.Programme p=programmes.get(0);assertEquals(Instant.parse("2026-10-30T21:00:00Z").toEpochMilli(),p.startMs);assertEquals("News & weather",p.title);assertEquals("News, Weather",p.category);assertTrue(p.repeat);
    }
    @Test public void xmlTvIgnoresEntriesWithMissingStopOrBackwardsTime()throws Exception {assertTrue(PlaylistRepository.parseXmlTv("<tv><programme channel=\"x\" start=\"20261031000000 +0000\"><title>No stop</title></programme></tv>").isEmpty());}
    @Test(expected=IOException.class) public void xmlTvRejectsExternalEntityDeclarations()throws Exception {PlaylistRepository.parseXmlTv("<!DOCTYPE tv [<!ENTITY x SYSTEM 'file:///private'>]><tv><programme channel='x' start='20261031000000 +0000' stop='20261031010000 +0000'><title>&x;</title></programme></tv>");}
    @Test public void invalidCalendarDateDoesNotNormalizeIntoNextMonth(){assertEquals(0,PlaylistRepository.parseXmlTime("20260230010000 +0000"));}
    @Test public void englishAndArabicT9AndArabicDigitsMatch(){assertTrue(PlaylistRepository.matches("BBC World News","222"));assertEquals("5828",PlaylistRepository.t9("سلام"));assertTrue(PlaylistRepository.matches("سلام","٥٨٢٨"));assertTrue(PlaylistRepository.matches("الإخبارية","الاخباريه"));assertFalse(PlaylistRepository.matches("BBC","999"));}
}
