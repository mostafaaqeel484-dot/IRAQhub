package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class VideoSurfaceStallPolicyTest {
 @Test public void onlyIdenticalSuccessfulPixelsAccumulate(){
  assertEquals(1,VideoSurfaceStallPolicy.identicalSamples(0,123,123,true));
  assertEquals(0,VideoSurfaceStallPolicy.identicalSamples(2,123,124,true));
  assertEquals(0,VideoSurfaceStallPolicy.identicalSamples(3,123,123,false));
  assertEquals(12,VideoSurfaceStallPolicy.identicalSamples(12,123,123,true));
 }
 @Test public void oneRecoveryRequiresMovingClockAndFreshTransport(){
  assertTrue(VideoSurfaceStallPolicy.probableStall(4,25000,1000,true,false));
  assertFalse(VideoSurfaceStallPolicy.probableStall(3,25000,1000,true,false));
  assertFalse(VideoSurfaceStallPolicy.probableStall(4,12000,1000,true,false));
  assertFalse(VideoSurfaceStallPolicy.probableStall(4,25000,12000,true,false));
  assertFalse(VideoSurfaceStallPolicy.probableStall(4,25000,1000,false,false));
  assertFalse(VideoSurfaceStallPolicy.probableStall(4,25000,1000,true,true));
 }
}