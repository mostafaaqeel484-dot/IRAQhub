package com.smarthub.plus;
import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.*;

/** CPU and smoothness must not be traded for unconditional frame dropping. */
public class Software422ProfileTest {
 @Test public void smoothKeepsAllDecodedVideoFrames(){
  ArrayList<String> options=Software422Profile.buildOptions(Software422Profile.SMOOTH);
  assertTrue(options.contains("--avcodec-hw=none"));
  assertTrue(options.contains("--avcodec-skiploopfilter=1"));
  assertTrue(options.contains("--avcodec-skip-frame=0"));
  assertTrue(options.contains("--avcodec-skip-idct=0"));
  assertFalse(options.contains("--skip-frames"));
  assertTrue(options.contains("--network-caching=1050"));
  assertTrue(options.contains("--live-caching=850"));
  assertFalse(options.contains("--no-avcodec-hurry-up"));
 }
 @Test public void qualityRetainsFullFiltering(){
  ArrayList<String> options=Software422Profile.buildOptions(Software422Profile.QUALITY);
  assertTrue(options.contains("--avcodec-skiploopfilter=0"));
  assertTrue(options.contains("--avcodec-skip-frame=0"));
  assertFalse(options.contains("--skip-frames"));
 }
 @Test public void stableHasFullFilteringAndBuffer(){
  ArrayList<String> options=Software422Profile.buildOptions(Software422Profile.STABLE);
  assertTrue(options.contains("--avcodec-skiploopfilter=0"));
  assertTrue(options.contains("--no-drop-late-frames"));
  assertTrue(options.contains("--network-caching=1650"));
  assertTrue(options.contains("--live-caching=1400"));
  assertFalse(options.contains("--skip-frames"));
 }
  @Test public void invalidProfileRejected(){
  try{Software422Profile.buildOptions("heavy");fail("Accepted unsupported 4:2:2 profile");}
  catch(IllegalArgumentException expected){assertEquals("INVALID_422_PROFILE",expected.getMessage());}
 }
}
