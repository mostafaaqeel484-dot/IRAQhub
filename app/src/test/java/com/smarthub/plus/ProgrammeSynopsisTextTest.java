package com.smarthub.plus;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ProgrammeSynopsisTextTest {
 @Test public void epgBeforeOnlineAndSports(){
  assertEquals("EPG summary",ProgrammeSynopsisText.choose(
     " EPG   summary ","Online synopsis","Sports",160));
  assertEquals("Online synopsis",ProgrammeSynopsisText.choose(
     "", "Online synopsis", "Sports", 160));
  assertEquals("Sports",ProgrammeSynopsisText.choose("",null,"Sports",160));
 }
 @Test public void noFabricationWhenDataMissing(){
  assertEquals("",ProgrammeSynopsisText.choose("",null,"",160));
 }
 @Test public void boundedReadableArabic(){
  String s=ProgrammeSynopsisText.clean("مباراة  نهائية\n في  كرة  القدم",160);
  assertEquals("مباراة نهائية في كرة القدم",s);
  String longText="فيلم جديد ".repeat(40);
  String cut=ProgrammeSynopsisText.clean(longText,160);
  assertTrue(cut.length()<=160);assertTrue(cut.endsWith("…"));
 }
 @Test public void stripMarkupAndControls(){
  assertEquals("فيلم ممتع",ProgrammeSynopsisText.clean(
    "<p>فيلم</p>  ممتع",100));
 }
}
