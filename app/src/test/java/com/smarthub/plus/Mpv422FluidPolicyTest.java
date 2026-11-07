package com.smarthub.plus;
import static org.junit.Assert.*;
import org.junit.Test;

public final class Mpv422FluidPolicyTest {
 @Test public void autoStartsAtScaled420ButKeepsNativeGpuFallback(){
  assertEquals(Mpv422FluidPolicy.P720,Mpv422FluidPolicy.initialRenderMode(null));
  assertEquals(Mpv422FluidPolicy.P540,Mpv422FluidPolicy.initialRenderMode(Mpv422FluidPolicy.P540));
  assertEquals("",Mpv422FluidPolicy.videoFilter(Mpv422FluidPolicy.GPU_NATIVE));
  assertTrue(Mpv422FluidPolicy.videoFilter(Mpv422FluidPolicy.P720).contains("1280:h=720"));
  assertTrue(Mpv422FluidPolicy.videoFilter(Mpv422FluidPolicy.P540).contains("960:h=540"));
  assertTrue(Mpv422FluidPolicy.videoFilter(Mpv422FluidPolicy.P720).contains("pix_fmts=yuv420p"));
 }
 @Test public void downshiftOnlyAfterMeasuredSustainedVoDrops(){
  int a=Mpv422FluidPolicy.poorDropSamples(30,40,4800,0);
  int b=Mpv422FluidPolicy.poorDropSamples(40,51,4800,a);
  assertEquals(1,a);assertEquals(2,b);
  assertTrue(Mpv422FluidPolicy.shouldDownshift(Mpv422FluidPolicy.AUTO,b,15100,11,4800));
  assertFalse(Mpv422FluidPolicy.shouldDownshift(Mpv422FluidPolicy.AUTO,b,7000,11,4800));
  assertFalse(Mpv422FluidPolicy.shouldDownshift(Mpv422FluidPolicy.P720,b,15100,11,4800));
  assertFalse(Mpv422FluidPolicy.shouldDownshift(Mpv422FluidPolicy.AUTO,b,15100,3,4800));
 }
 @Test public void noInfiniteRetryForFilterTimeout(){
  assertTrue(Mpv422FluidPolicy.recoverWithNative(
    Mpv422FluidPolicy.P720,false,"MPV_NO_FILE_LOADED_22S"));
  assertTrue(Mpv422FluidPolicy.recoverWithNative(
    Mpv422FluidPolicy.P540,false,"MPV_END_BEFORE_FILE_LOADED_event=7"));
  assertTrue(Mpv422FluidPolicy.recoverWithNative(
    Mpv422FluidPolicy.P720,false,"MPV_SURFACE_NOT_READY_10S"));
  assertTrue(Mpv422FluidPolicy.recoverWithNative(
    Mpv422FluidPolicy.P720,false,"MPV_START_IllegalArgumentException: MPV_VF_REJECTED_-4"));
  assertFalse(Mpv422FluidPolicy.recoverWithNative(
    Mpv422FluidPolicy.GPU_NATIVE,false,"MPV_NO_FILE_LOADED_22S"));
  assertFalse(Mpv422FluidPolicy.recoverWithNative(
    Mpv422FluidPolicy.P720,true,"MPV_NO_FILE_LOADED_22S"));
  assertFalse(Mpv422FluidPolicy.recoverWithNative(
    Mpv422FluidPolicy.P720,false,"MPV_NATIVE_DECODER_FATAL"));
 }
 @Test public void counterResetAndHealthyIntervalResetPressure(){
  assertEquals(0,Mpv422FluidPolicy.poorDropSamples(99,3,4800,2));
  assertEquals(0,Mpv422FluidPolicy.poorDropSamples(40,42,4800,2));
  assertEquals(0,Mpv422FluidPolicy.poorDropSamples(40,100,1200,2));
 }
}
