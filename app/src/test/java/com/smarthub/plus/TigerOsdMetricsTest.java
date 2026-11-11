package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

public final class TigerOsdMetricsTest {
 @Test public void iconsAreSameSizeAcrossBothRows(){
  assertEquals(36,TigerOsdMetrics.iconWidthDp());
  assertEquals(22,TigerOsdMetrics.iconHeightDp());
  assertEquals(93,TigerOsdMetrics.headerHeightDp());
  // 2026 reference: year + local date/time + two fixed-height badge rows.
  assertTrue(TigerOsdMetrics.headerHeightDp()>=2*TigerOsdMetrics.iconHeightDp()+24);
 }
 @Test public void compactModeIsNotBasedOnImpossibleHeightThreshold(){
  assertTrue(TigerOsdMetrics.isCompact(false));
  assertFalse(TigerOsdMetrics.isCompact(true));
 }
 @Test public void postersStayAboveBothInfoBarSizes(){
  for(boolean expanded:new boolean[]{false,true}){
   int bar=TigerOsdMetrics.barHeightDp(expanded);
   assertTrue(bar>=TigerOsdMetrics.headerHeightDp()+70);
   assertEquals(bar+17,TigerOsdMetrics.posterBottomMarginDp(expanded));
  }
 }
}