package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

public final class VideoFrameRatePolicyTest {
 @Test public void measuredTwentyFiveFps(){
  assertEquals(2500,VideoFrameRatePolicy.fpsX100(100,165,2600));
  assertTrue(VideoFrameRatePolicy.advancing(100,165));
 }
 @Test public void firstSampleAndCounterResetRemainUnknown(){
  assertEquals(-1,VideoFrameRatePolicy.fpsX100(-1,65,2600));
  assertEquals(-1,VideoFrameRatePolicy.fpsX100(100,10,2600));
  assertEquals(-1,VideoFrameRatePolicy.fpsX100(50,70,400));
 }
 @Test public void stoppedDisplayCounterIsZeroFpsNotAHealthyVout(){
  assertEquals(0,VideoFrameRatePolicy.fpsX100(100,100,2600));
  assertFalse(VideoFrameRatePolicy.advancing(100,100));
  assertFalse(VideoFrameRatePolicy.advancing(100,101));
 }
}
