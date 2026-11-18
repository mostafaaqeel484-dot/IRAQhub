package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;
public final class Rx7SimpleUiPolicyTest {
 @Test public void defaultFocusShowsDailySettingsOnly(){
  assertTrue(Rx7SimpleUiPolicy.showCategory("VIDEO",false));
  assertTrue(Rx7SimpleUiPolicy.showCategory("CHANNELS",false));
  assertTrue(Rx7SimpleUiPolicy.showCategory("IMPORT",false));
  assertTrue(Rx7SimpleUiPolicy.showCategory("INFOBAR",false));
  assertTrue(Rx7SimpleUiPolicy.showCategory("ANTENNA",false));
  assertFalse(Rx7SimpleUiPolicy.showCategory("API",false));
  assertFalse(Rx7SimpleUiPolicy.showCategory("ADVANCED",false));
  assertTrue(Rx7SimpleUiPolicy.showCategory("API",true));
  assertTrue(Rx7SimpleUiPolicy.showCategory("ADVANCED",true));
 }
 @Test public void optionalHomeModulesNeverTakeOverDefaultHome(){
  assertEquals(1,Rx7SimpleUiPolicy.pinnedRows(false));
  assertEquals(2,Rx7SimpleUiPolicy.pinnedRows(true));
 }
}
