package com.smarthub.plus;
import org.junit.Test;
import static org.junit.Assert.*;

public final class TigerOsdMetricsTest {
 @Test public void iconsAreSameSizeAcrossBothRows(){
  assertEquals(36,TigerOsdMetrics.iconWidthDp());
  assertEquals(22,TigerOsdMetrics.iconHeightDp());
  assertEquals(57,TigerOsdMetrics.headerHeightDp());
 }
 @Test public void postersStayAboveBothInfoBarSizes(){
  for(boolean expanded:new boolean[]{false,true}){
   int bar=TigerOsdMetrics.barHeightDp(expanded);
   assertTrue(bar>=TigerOsdMetrics.headerHeightDp()+70);
   assertEquals(bar+17,TigerOsdMetrics.posterBottomMarginDp(expanded));
  }
 }
}