package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public final class LiveFeedTextParserTest {
 @Test public void readsPublishedMetadataAndMergesRepeatedFrequencyWithoutKeys(){
  String published="Eutelsat Hotbird 13B/13C/13G @ 13.0°E\n"+
   "11083 V 14400\nImage\nON Clear 2026-11-16 21:02:00\n"+
   "📡Eutelsat Hotbird 13B/13C/13G @ 13.0°E\n"+
   "📶11083 V 14400\n🎬1920×1080 | H.264/AVC\n"+
   "📊4:2:2 | 25.70 Mb/s\n🆔KAZ008M\n🔑 00 00 00 00\n"+
   "12698 V 6000\nON Clear 2026-11-16 21:23:09\n"+
   "📡Eutelsat 16A @ 16.0°E\n📶12698 V 6000\n"+
   "🎬1920×1080 | H.264/AVC\n📊4:2:0 | 13 Mb/s\n"+
   "🆔UKI 1940 p1\n";
  List<LiveFeedTextParser.Entry> list=LiveFeedTextParser.parse(published);
  assertEquals(2,list.size());
  LiveFeedTextParser.Entry first=list.get(0);
  assertEquals("11083 V 14400",first.frequencyLabel());
  assertEquals("13.0E",first.orbital);
  assertTrue(first.is422());
  assertTrue(first.isLive());
  assertEquals("KAZ008M",first.name);
  for(LiveFeedTextParser.Entry e:list){
   assertFalse(e.name.contains("00 00"));
   assertFalse(e.codec.contains("🔑"));
  }
 }
 @Test public void emptyHtmlNeverGeneratesInventedFrequencies(){
  assertTrue(LiveFeedTextParser.parse("").isEmpty());
  assertTrue(LiveFeedTextParser.parse("scan is offline; no published feeds").isEmpty());
 }
 @Test public void feedWithStatusButNoVideoIsStillListed(){
  String s="Eutelsat 7 @ 7.0°E\n11778 H 30000\nON Clear\nFEED 07\n";
  List<LiveFeedTextParser.Entry> list=LiveFeedTextParser.parse(s);
  assertEquals(1,list.size());
  assertEquals("FEED 07",list.get(0).name);
  assertEquals("7.0E",list.get(0).orbital);
 }
}
