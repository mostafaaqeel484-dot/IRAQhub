package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;

public final class VideoPrioritySchedulerTest {
 @Test public void onlyAppOwnedNonessentialRefreshJobsAreSuspended(){
  java.util.Set<String> entries=new java.util.HashSet<>(
    java.util.Arrays.asList(IraqHubSyncScheduler.optionalNames()));
  assertEquals(10,entries.size());
  for(String name:entries){
   assertTrue(name.startsWith("iraqhub-"));
   assertFalse(name.toLowerCase().contains("tuner"));
   assertFalse(name.toLowerCase().contains("player"));
   assertFalse(name.toLowerCase().contains("capture"));
  }
 }
}