package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

public final class TigerOsdMetricsTest {
 @Test public void iconsAreSameSizeAcrossBothRows(){
  assertEquals(36,TigerOsdMetrics.iconWidthDp());
  assertEquals(22,TigerOsdMetrics.iconHeightDp());
  assertEquals(120,TigerOsdMetrics.headerHeightDp());
  // Flag is exactly half the available channel-logo width, stacked below its name.
  assertEquals(194,TigerOsdMetrics.channelLogoWidthDp());
  assertEquals(97,TigerOsdMetrics.channelFlagWidthDp());
  assertEquals(TigerOsdMetrics.channelLogoWidthDp()/2,TigerOsdMetrics.channelFlagWidthDp());
  assertEquals(120,TigerOsdMetrics.channelLogoHeightDp()+
   TigerOsdMetrics.channelNameHeightDp()+TigerOsdMetrics.channelFlagHeightDp());
 }
 @Test public void compactModeIsNotBasedOnImpossibleHeightThreshold(){
  assertTrue(TigerOsdMetrics.isCompact(false));
  assertFalse(TigerOsdMetrics.isCompact(true));
 }
 @Test public void postersStayAboveBothInfoBarSizes(){
  for(boolean expanded:new boolean[]{false,true}){
   int bar=TigerOsdMetrics.barHeightDp(expanded);
   assertEquals(expanded?226:185,bar);
   assertTrue(bar>=TigerOsdMetrics.headerHeightDp()+50);
   assertEquals(bar+17,TigerOsdMetrics.posterBottomMarginDp(expanded));
  }
 }
}