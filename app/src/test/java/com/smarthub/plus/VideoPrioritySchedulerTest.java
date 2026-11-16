package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;

public final class VideoPrioritySchedulerTest {
 @Test public void onlyAppOwnedNonessentialRefreshJobsAreSuspended(){
  java.util.Set<String> entries=new java.util.HashSet<>(
    java.util.Arrays.asList(IraqHubSyncScheduler.optionalNames()));
  // 4.0.18 adds both provider-catalogue refresh jobs to the original 13.\n  // Every optional work name must remain unique and app-owned.\n  assertEquals(15,entries.size());\n  assertEquals(IraqHubSyncScheduler.optionalNames().length,entries.size());\n  assertTrue(entries.contains("iraqhub-provider-catalogue"));\n  assertTrue(entries.contains("iraqhub-provider-catalogue-initial"));
  for(String name:entries){
   assertTrue(name.startsWith("iraqhub-"));
   assertFalse(name.toLowerCase().contains("tuner"));
   assertFalse(name.toLowerCase().contains("player"));
   assertFalse(name.toLowerCase().contains("capture"));
  }
 }
}
