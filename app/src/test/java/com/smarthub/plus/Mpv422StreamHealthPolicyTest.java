package com.smarthub.plus;
import static org.junit.Assert.*;
import org.junit.Test;

public final class Mpv422StreamHealthPolicyTest {
 @Test public void mpvClockAndFileLoadedWithoutFrameAreNotHealthyVideo(){
  int count=0;
  count=Mpv422StreamHealthPolicy.missingFrameSamples(count,"unknown",-1);
  count=Mpv422StreamHealthPolicy.missingFrameSamples(count,"unknown",-1);
  count=Mpv422StreamHealthPolicy.missingFrameSamples(count,"unknown",-1);
  assertTrue(Mpv422StreamHealthPolicy.shouldRecover(true,false,true,22000,count));
  assertFalse(Mpv422StreamHealthPolicy.shouldRecover(true,false,true,14000,count));
  assertFalse(Mpv422StreamHealthPolicy.shouldRecover(true,true,true,22000,count));
  assertFalse(Mpv422StreamHealthPolicy.shouldRecover(true,false,false,22000,count));
  assertFalse(Mpv422StreamHealthPolicy.shouldRecover(false,false,true,22000,count));
 }
 @Test public void aDecodedFormatOrPositiveFpsResetsTheBlackFrameCounter(){
  assertEquals(0,Mpv422StreamHealthPolicy.missingFrameSamples(6,"yuv422p10le",0));
  assertEquals(0,Mpv422StreamHealthPolicy.missingFrameSamples(6,"unknown",25));
  assertEquals(1,Mpv422StreamHealthPolicy.missingFrameSamples(0,null,-1));
 }
}
