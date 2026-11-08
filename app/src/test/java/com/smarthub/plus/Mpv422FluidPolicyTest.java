package com.smarthub.plus;
import static org.junit.Assert.*;
import org.junit.Test;

public final class Mpv422FluidPolicyTest {
 @Test public void exposesBothPixelFormatsAcrossResolutions(){
  for(int height:new int[]{1080,720,540,480,360,260}){
   for(String prefix:new String[]{"420_","422_"}){
    String mode=prefix+height;
    assertEquals(mode,Mpv422FluidPolicy.normalized(mode));
    assertEquals(height,Mpv422FluidPolicy.targetHeight(mode));
    String filter=Mpv422FluidPolicy.videoFilter(mode);
    assertTrue(filter,filter.contains("h="+height));
    assertTrue(filter,filter.contains(prefix.equals("422_")?
      "pix_fmts=yuv422p10le":"pix_fmts=yuv420p"));
   }
  }
  assertTrue(Mpv422FluidPolicy.videoFilter(Mpv422FluidPolicy.P360).contains("w=640"));
  assertTrue(Mpv422FluidPolicy.videoFilter(Mpv422FluidPolicy.P260).contains("w=462"));
  assertEquals(12,Mpv422FluidPolicy.publicModes().length);
  assertEquals("",Mpv422FluidPolicy.videoFilter(Mpv422FluidPolicy.GPU_NATIVE));
  assertEquals(Mpv422FluidPolicy.P720,Mpv422FluidPolicy.initialRenderMode(null));
  assertEquals(Mpv422FluidPolicy.AUTO,Mpv422FluidPolicy.normalized("dangerous vf injection"));
 }
 @Test public void softwareDefaultAndExplicitHardwareExperimental(){
  assertEquals(Mpv422FluidPolicy.SW,Mpv422FluidPolicy.decoderMode(null));
  assertEquals("no",Mpv422FluidPolicy.hwdecOption("unknown"));
  assertEquals("mediacodec-copy",Mpv422FluidPolicy.hwdecOption(Mpv422FluidPolicy.HW));
 }
 @Test public void autoStepsAreFiniteAndNeverChangeSource(){
  assertEquals(Mpv422FluidPolicy.P540,
    Mpv422FluidPolicy.nextAutoMode(Mpv422FluidPolicy.P720));
  assertEquals(Mpv422FluidPolicy.P360,
    Mpv422FluidPolicy.nextAutoMode(Mpv422FluidPolicy.P540));
  assertEquals(Mpv422FluidPolicy.P260,
    Mpv422FluidPolicy.nextAutoMode(Mpv422FluidPolicy.P360));
  assertEquals(Mpv422FluidPolicy.P260,
    Mpv422FluidPolicy.nextAutoMode(Mpv422FluidPolicy.P260));
  int a=Mpv422FluidPolicy.poorDropSamples(30,40,4800,0);
  int b=Mpv422FluidPolicy.poorDropSamples(40,51,4800,a);
  assertEquals(1,a);assertEquals(2,b);
  assertTrue(Mpv422FluidPolicy.shouldDownshift(Mpv422FluidPolicy.AUTO,b,15100,11,4800));
  assertFalse(Mpv422FluidPolicy.shouldDownshift(Mpv422FluidPolicy.AUTO,b,7000,11,4800));
  assertFalse(Mpv422FluidPolicy.shouldDownshift(Mpv422FluidPolicy.P720,b,15100,11,4800));
  assertFalse(Mpv422FluidPolicy.shouldDownshift(Mpv422FluidPolicy.AUTO,b,15100,3,4800));
  assertEquals(0,Mpv422FluidPolicy.poorDropSamples(99,3,4800,2));
 }
 @Test public void noInfiniteRetryForAnyFilteredMode(){
  for(String mode:Mpv422FluidPolicy.modes()){
   if(Mpv422FluidPolicy.GPU_NATIVE.equals(mode))continue;
   assertTrue(mode,Mpv422FluidPolicy.recoverWithNative(
     mode,false,"MPV_NO_FILE_LOADED_22S"));
  }
  assertFalse(Mpv422FluidPolicy.recoverWithNative(
    Mpv422FluidPolicy.GPU_NATIVE,false,"MPV_NO_FILE_LOADED_22S"));
  assertFalse(Mpv422FluidPolicy.recoverWithNative(
    Mpv422FluidPolicy.Y422_360,true,"MPV_NO_FILE_LOADED_22S"));
  assertFalse(Mpv422FluidPolicy.recoverWithNative(
    Mpv422FluidPolicy.P720,false,"MPV_NATIVE_DECODER_FATAL"));
 }
}
