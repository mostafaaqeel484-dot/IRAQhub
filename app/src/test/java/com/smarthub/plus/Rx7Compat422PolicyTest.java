package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;

public final class Rx7Compat422PolicyTest {
 @Test public void directDecoderOnlyForExplicitSafeSelectionAndActualLibrary(){
  assertTrue(Rx7Compat422Policy.direct(true,true,false));
  assertFalse(Rx7Compat422Policy.direct(false,true,false));
  assertFalse(Rx7Compat422Policy.direct(true,false,false));
  assertFalse(Rx7Compat422Policy.direct(true,true,true));
  assertEquals("420_450",Rx7Compat422Policy.DEFAULT_OUTPUT);
 }
 @Test public void noTsIsSeparateFromVideoDecoderFailure(){
  assertTrue(Rx7Compat422Policy.isCaptureMissing("CAPTURE_NO_MPEG_TS"));
  assertFalse(Rx7Compat422Policy.isCaptureMissing("LIBVLC_ENCOUNTERED_ERROR"));
 }
 @Test public void onlyMeasuredFrameProgressCountsAsVideo(){
  assertEquals(2500,Rx7Compat422Policy.fpsX100(20,45,1000));
  assertEquals(0,Rx7Compat422Policy.fpsX100(25,25,1000));
  assertTrue(Rx7Compat422Policy.moving(20,45,1000));
  assertFalse(Rx7Compat422Policy.moving(45,45,1000));
  assertFalse(Rx7Compat422Policy.moving(10,20,100));
 }
}
