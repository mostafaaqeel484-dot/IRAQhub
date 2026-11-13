package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

public final class DvbEpgAllPolicyTest {
 @Test public void manufacturerEpgProBeforeDvbEitThenExternal(){
  assertEquals(400,DvbEpgAllPolicy.rank("Factory EPG Pro · verified"));
  assertTrue(DvbEpgAllPolicy.rank("Factory EPG Pro · verified")>
             DvbEpgAllPolicy.rank("DVB EIT · native receiver"));
  assertTrue(DvbEpgAllPolicy.rank("DVB EIT · native receiver")>
             DvbEpgAllPolicy.rank("Android TV published epg"));
  assertTrue(DvbEpgAllPolicy.rank("Android TV published epg")>
             DvbEpgAllPolicy.rank("EPGShare SA1"));
  assertFalse(DvbEpgAllPolicy.receiverGrade("EPGShare SA1"));
  assertTrue(DvbEpgAllPolicy.receiverGrade("Factory EPG Pro · verified"));
  assertTrue(DvbEpgAllPolicy.receiverGrade("DVB EIT · native receiver"));
 }
 @Test public void allowOnlyDvbPhysicalServiceIds(){
  assertTrue(DvbEpgAllPolicy.physical("DVB:130:Ku:11954:H:27500:100"));
  assertFalse(DvbEpgAllPolicy.physical("IPTV:osn"));
  assertFalse(DvbEpgAllPolicy.physical("OSN Movies"));
  assertFalse(DvbEpgAllPolicy.physical(null));
 }
 @Test public void nowNextAreIndependentAndTimeBound(){
  long now=1800000000000L;
  assertTrue(DvbEpgAllPolicy.current(now-60000,now+120000,now,"OSN movie"));
  assertTrue(DvbEpgAllPolicy.next(now+120000,now+3600000,now,"OSN next film"));
  assertFalse(DvbEpgAllPolicy.current(now+1000,now+3600000,now,"Future"));
  assertFalse(DvbEpgAllPolicy.next(now-1000,now+3600000,now,"Current"));
  assertFalse(DvbEpgAllPolicy.current(now-120000,now-1,now,"Expired"));
  assertFalse(DvbEpgAllPolicy.current(now-1000,now+3600000,now,"No Information"));
 }
}
