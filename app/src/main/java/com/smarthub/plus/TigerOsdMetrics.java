package com.smarthub.plus;

/** Pixel-equivalent TV dp geometry from the reference Tiger two-row InfoBar.
 * Metric methods stay pure so 720p and 1080p builds can regression-test
 * icon uniformity without an Android graphical device.
 */
final class TigerOsdMetrics {
 private TigerOsdMetrics(){}
 static int barHeightDp(boolean detailed){return detailed?218:172;}
 static boolean isCompact(boolean detailed){return !detailed;}
 static int headerHeightDp(){return 81;}
 static int iconWidthDp(){return 36;}
 static int iconHeightDp(){return 22;}
 static int posterBottomMarginDp(boolean detailed){
  return barHeightDp(detailed)+17;
 }
}