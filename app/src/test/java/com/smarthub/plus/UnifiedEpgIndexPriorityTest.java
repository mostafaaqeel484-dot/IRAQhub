package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure scoring only; all-channel EPG completeness requires receiver testing. */
public class UnifiedEpgIndexPriorityTest {
 @Test public void verifiedBroadcastOutranksMappings(){
  assertTrue(UnifiedEpgIndex.sourceRank("DVB EIT observed") >
    UnifiedEpgIndex.sourceRank("XMLTV · mapped guide"));
  assertTrue(UnifiedEpgIndex.sourceRank("Android TV published epg") >
    UnifiedEpgIndex.sourceRank("XMLTV"));
  assertTrue(UnifiedEpgIndex.sourceRank("XMLTV") >
    UnifiedEpgIndex.sourceRank("unclassified"));
 }
 @Test public void unknownIsNotNativeEvidence(){
  assertEquals(0,UnifiedEpgIndex.sourceRank(""));
  assertEquals(EpgSourcePriority.DVB,UnifiedEpgIndex.sourceRank("DVB EIT"));
 }

 @Test public void nativeEventKeyIsNeverUsedAsPhysicalChannel(){
  SatelliteGuideRepository.Event event=new SatelliteGuideRepository.Event();
  event.channelId="DVB:19:10723:1:210";
  event.startMs=1794171600000L;
  assertEquals("DVB:19:10723:1:210",UnifiedEpgIndex.eventChannel(event));
  assertNotEquals(event.channelId+"|"+event.startMs,UnifiedEpgIndex.eventChannel(event));
  assertEquals("",UnifiedEpgIndex.eventChannel(null));
 }
}
