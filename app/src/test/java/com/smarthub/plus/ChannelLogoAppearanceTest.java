package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;

/** Appearance and manual logo keys never overlap between physically distinct DVB services. */
public final class ChannelLogoAppearanceTest {
 @Test public void scaleAndBackgroundAreBounded(){
  assertEquals(65,ChannelLogoAppearance.clamp(-10));
  assertEquals(125,ChannelLogoAppearance.clamp(900));
  assertEquals(100,ChannelLogoAppearance.clamp(100));
  assertEquals("original",ChannelLogoAppearance.safeBackground("unknown"));
  assertEquals("black",ChannelLogoAppearance.safeBackground("black"));
 }
 @Test public void independentPerServiceKeysAreStable(){
  String a="DVB:130:H:11954:V:27500:100";
  String b="DVB:130:H:11954:V:27500:101";
  assertTrue(ChannelLogoAppearance.physical(a));
  assertNotEquals(ChannelLogoAppearance.key(a),ChannelLogoAppearance.key(b));
  assertEquals(ChannelLogoAppearance.key(a),ChannelLogoAppearance.key(a));
  assertEquals(ChannelLogoAppearance.GLOBAL,ChannelLogoAppearance.key(""));
 }
 @Test public void allAppearanceInputsArePreserved(){
  ChannelLogoAppearance.Config sample=new ChannelLogoAppearance.Config(
   120,true,false,true,true,"transparent");
  assertEquals(120,sample.size);
  assertTrue(sample.removeWhite);
  assertFalse(sample.removeBlack);
  assertTrue(sample.crop);
  assertTrue(sample.flag);
  assertEquals("original",sample.background);
  assertEquals(120,sample.json().optInt("size"));
  assertEquals(3,sample.json().optInt("layout"));
 }
}
