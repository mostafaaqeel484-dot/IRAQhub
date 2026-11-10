package com.smarthub.plus;
import static org.junit.Assert.*;
import org.junit.Test;
public final class ChannelInitialFilterTest {
 @Test public void latinAndArabicFirstLetterExact(){
  assertTrue(ChannelInitialFilter.matches("BBC World","B"));
  assertTrue(ChannelInitialFilter.matches("bein SPORTS 1","B"));
  assertFalse(ChannelInitialFilter.matches("Al BBC","B"));
  assertEquals("ا",ChannelInitialFilter.initial("إمام علي HD"));
  assertEquals("ا",ChannelInitialFilter.initial("أبو ظبي"));
  assertEquals("ي",ChannelInitialFilter.initial("ىمن"));
  assertTrue(ChannelInitialFilter.matches("2M MONDE","#"));
  assertTrue(ChannelInitialFilter.matches("  BBC","B"));
  assertTrue(ChannelInitialFilter.matches("أي قناة",""));
  assertFalse(ChannelInitialFilter.matches("BBC","C"));
 }
 @Test public void choicesContainClearAndLatinAndArabic(){
  String[] all=ChannelInitialFilter.choices();
  assertEquals("",all[0]);assertEquals("#",all[1]);
  assertEquals("A",all[2]);assertEquals("Z",all[27]);
  assertTrue(all.length>=55);
 }
}
