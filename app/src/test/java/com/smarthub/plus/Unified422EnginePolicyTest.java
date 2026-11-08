package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public class Unified422EnginePolicyTest {
 @Test public void oneAutoPlayerUsesLibVlcFfmpegFirst(){
  assertEquals("LIBVLC_FFMPEG",Unified422EnginePolicy.backendName("unified",
     Mpv422FluidPolicy.AUTO));
  assertFalse(Unified422EnginePolicy.preferMpv("vlc",Mpv422FluidPolicy.P260));
 }
 @Test public void fixedOutputUsesMpvFfmpegAndGpuFastWithoutDualDecode(){
  for(String mode:Mpv422FluidPolicy.publicModes()){
   if(Mpv422FluidPolicy.AUTO.equals(mode))continue;
   assertTrue(mode,Unified422EnginePolicy.preferMpv("unified",mode));
  }
  assertEquals("MPV_FFMPEG",Unified422EnginePolicy.backendName("unified",
      Mpv422FluidPolicy.GPU_NATIVE));
 }
 @Test public void untrustedModeFallsBackToAutoPolicy(){
  assertFalse(Unified422EnginePolicy.preferMpv("unified","bad;file"));
 }
}
