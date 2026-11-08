package com.smarthub.plus;
import static org.junit.Assert.*;
import org.junit.Test;
public final class Native422V2ProfileTest {
 @Test public void experimentalNativeSessionPrioritizesFrames(){
  assertTrue(Native422V2Profile.enabled("native_v2"));
  assertFalse(Native422V2Profile.enabled("isolated"));
  assertEquals(Mpv422FluidPolicy.GPU_NATIVE,Native422V2Profile.renderMode());
  assertEquals(Mpv422FluidPolicy.SW,Native422V2Profile.decoderMode());
  assertTrue(Native422V2Profile.videoOnly());
  assertFalse(Native422V2Profile.allowsHardware(false));
 }
}
