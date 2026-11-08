package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

public final class Native422OutputProfileTest {
 @Test public void allSizesAndChromaHaveEvenFrameDimensions(){
  int[] h={1080,720,540,360,260};
  for(int height:h){
   for(String format:new String[]{"422_","420_"}){
    Native422OutputProfile.Target t=Native422OutputProfile.target(format+height);
    assertTrue(t.selected,t.valid());
    assertEquals(height,t.height);
    assertEquals(format.startsWith("420"),t.convert420);
   }
  }
  assertEquals(12,Native422OutputProfile.modes().length);
 }
 @Test public void gpuDoesNotForceCpuResize(){
  Native422OutputProfile.Target t=Native422OutputProfile.target("gpu_native");
  assertTrue(t.gpuFast);
  assertEquals(0,t.width);
  assertEquals(0,t.height);
 }
 @Test public void autoStartsAt720AndFallsBackGradually(){
  Native422OutputProfile.Target p=Native422OutputProfile.target("auto_720");
  assertEquals(720,p.height);assertTrue(p.convert420);
  assertEquals("420_540",Native422OutputProfile.nextAuto("auto_720",7,2));
  assertEquals("420_360",Native422OutputProfile.nextAuto("420_540",9,3));
  assertEquals("420_260",Native422OutputProfile.nextAuto("420_360",9,4));
  assertEquals("420_260",Native422OutputProfile.nextAuto("420_260",9,5));
  assertEquals("auto_720",Native422OutputProfile.nextAuto("auto_720",2,5));
 }
 @Test public void unknownModeCannotCrossNativeBoundary(){
  assertEquals(Native422OutputProfile.AUTO,
    Native422OutputProfile.normalize("420_720;rm -rf"));
  assertFalse(Native422OutputProfile.canUseNative(false,true,true));
  assertFalse(Native422OutputProfile.canUseNative(true,false,true));
  assertFalse(Native422OutputProfile.canUseNative(true,true,false));
  assertTrue(Native422OutputProfile.canUseNative(true,true,true));
 }
}