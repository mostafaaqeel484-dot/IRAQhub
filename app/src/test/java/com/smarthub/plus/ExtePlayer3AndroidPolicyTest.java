package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public final class ExtePlayer3AndroidPolicyTest {
 @Test public void actualNativeIsMainOnlyForExplicit422Mode(){
  assertTrue(ExtePlayer3AndroidPolicy.prefer(true,true,false));
  assertFalse(ExtePlayer3AndroidPolicy.prefer(true,true,true));
  assertFalse(ExtePlayer3AndroidPolicy.prefer(false,true,false));
  assertFalse(ExtePlayer3AndroidPolicy.prefer(true,false,false));
  assertTrue(ExtePlayer3AndroidPolicy.isNativeRoute("exteplayer3_android"));
  assertTrue(ExtePlayer3AndroidPolicy.isNativeRoute("jni_ffmpeg"));
  assertFalse(ExtePlayer3AndroidPolicy.isNativeRoute("unified"));
 }
 @Test public void skyBlueOriginalAnd450SwitchBackAndForth(){
  assertEquals("gpu_native",ExtePlayer3AndroidPolicy.toggleOutput("420_450"));
  assertEquals("420_450",ExtePlayer3AndroidPolicy.toggleOutput("gpu_native"));
  assertTrue(ExtePlayer3AndroidPolicy.display("420_450").contains("800×450"));
 }
}
