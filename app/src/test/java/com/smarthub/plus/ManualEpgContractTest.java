package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class ManualEpgContractTest {
 @Test public void manualGuideRequiresExactDvbIdentity(){
  assertTrue(ManualEpgStore.validChannel("DVB:13:12234:1:112","OSN Movies Premiere"));
  assertFalse(ManualEpgStore.validChannel("IPTV:123","OSN Movies Premiere"));
  assertFalse(ManualEpgStore.validChannel("DVB:1",""));
 }
 @Test public void perChannelStorageKeysNeverOverlap(){
  assertNotEquals(ManualEpgStore.key("DVB:12:1"),ManualEpgStore.key("DVB:12:2"));
 }
}
