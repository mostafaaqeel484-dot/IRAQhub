package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public final class AppearancePaletteTest {
 @Test public void wallpaperModeKeepsSelectedWallpaperAccent(){
  assertEquals(0xff58d893,AppearancePalette.accent(0,0xff58d893));
  assertEquals(0xff09101c,AppearancePalette.background(0));
 }
 @Test public void advancedColourModesAreNotOverwrittenByBase(){
  assertEquals(0xff53d8b5,AppearancePalette.accent(1,0xff58d893));
  assertEquals(0xff14343b,AppearancePalette.panel(1));
  assertEquals(0xff70baff,AppearancePalette.accent(2,0xff58d893));
  assertEquals(0xff254564,AppearancePalette.tile(2));
 }
 @Test public void unexpectedModeSafelyKeepsWallpaper(){
  assertEquals(0xff7cf0a8,AppearancePalette.accent(-4,0xff7cf0a8));
 }
}
