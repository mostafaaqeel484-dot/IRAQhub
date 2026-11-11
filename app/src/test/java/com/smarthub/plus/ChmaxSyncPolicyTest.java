package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChmaxSyncPolicyTest {
 @Test public void noMatchPackagesAreNotRedownloadedRepeatedly(){
  long now=1800000000000L;
  assertTrue(ChmaxSyncPolicy.skip(0,"zipA","zipA","channelsA","channelsA",
     now-60000,now));
  assertFalse(ChmaxSyncPolicy.skip(0,"zipA","zipA","channelsA","channelsA",
     now-13L*3600000,now));
 }
 @Test public void channelListChangeForcesRematch(){
  assertFalse(ChmaxSyncPolicy.skip(15,"a","a","old","new",1000,2000));
 }
 @Test public void positivePackagesRemainCachedForSevenDays(){
  long now=1800000000000L;
  assertTrue(ChmaxSyncPolicy.skip(200,"a","a","v","v",now-3L*86400000,now));
  assertFalse(ChmaxSyncPolicy.skip(200,"a","b","v","v",now-3L*86400000,now));
 }
 @Test public void stopBurstRequestsWhenServerThrottles(){
  assertTrue(ChmaxSyncPolicy.serverBackoff("FACTORY_HTTP_429"));
  assertTrue(ChmaxSyncPolicy.serverBackoff("FACTORY_AUTH_REQUIRED"));
  assertFalse(ChmaxSyncPolicy.serverBackoff("FACTORY_TIMEOUT"));
 }
}
