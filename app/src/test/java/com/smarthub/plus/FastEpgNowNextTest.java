package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

/** Prevent stale/unknown event metadata from appearing as live EPG. */
public final class FastEpgNowNextTest {
 @Test public void onlyCurrentTimedEventIsAccepted(){
  long now=1_800_000_000_000L;
  assertTrue(ReceiverInfoRepository.timedCurrent("WWE",now-1000,now+60000,now));
  assertFalse(ReceiverInfoRepository.timedCurrent("WWE",now-70000,now-1,now));
  assertFalse(ReceiverInfoRepository.timedCurrent("WWE",now+1000,now+60000,now));
  assertFalse(ReceiverInfoRepository.timedCurrent("",now-1000,now+1000,now));
  assertFalse(ReceiverInfoRepository.timedCurrent("WWE",0,now+60000,now));
 }
 @Test public void upcomingMustHaveFutureStartAndNonzeroDuration(){
  long now=1_800_000_000_000L;
  assertTrue(ReceiverInfoRepository.timedNext("Next Show",now+60000,now+160000,now));
  assertFalse(ReceiverInfoRepository.timedNext("Next Show",now-60000,now+160000,now));
  assertFalse(ReceiverInfoRepository.timedNext("Next Show",now+160000,now+160000,now));
  assertFalse(ReceiverInfoRepository.timedNext(null,now+1000,now+3000,now));
 }
 @Test public void nativeEitHasHigherPriorityThanNameBasedXmltv(){
  assertTrue(UnifiedEpgIndex.sourceRank("DVB EIT · native receiver")>
    UnifiedEpgIndex.sourceRank("XMLTV · mapped guide"));
 }
}
