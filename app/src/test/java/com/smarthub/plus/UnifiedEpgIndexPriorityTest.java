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
  assertEquals(100,UnifiedEpgIndex.sourceRank(""));
  assertEquals(300,UnifiedEpgIndex.sourceRank("DVB EIT"));
 }
}
