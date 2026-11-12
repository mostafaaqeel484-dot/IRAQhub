package com.smarthub.plus;

/** Pixel-equivalent TV dp geometry from the reference Tiger two-row InfoBar.
 * Metric methods stay pure so 720p and 1080p builds can regression-test
 * icon uniformity without an Android graphical device.
 */
final class TigerOsdMetrics {
 private TigerOsdMetrics(){}
 // Exact pre-4.0.8 Tiger bar geometry; enlargement is INSIDE the old bar.
 static int barHeightDp(boolean detailed){return detailed?226:185;}
 static boolean isCompact(boolean detailed){return !detailed;}
 static int headerHeightDp(){return 120;}
 // +18% inside the original 194dp brand column. The flag is half its width.
 static int channelLogoWidthDp(){return 194;}
 static int channelLogoHeightDp(){return 76;}
 static int channelFlagWidthDp(){return channelLogoWidthDp()/2;}
 static int channelFlagHeightDp(){return 25;}
 static int channelNameHeightDp(){return headerHeightDp()-channelLogoHeightDp()-channelFlagHeightDp();}
 static int iconWidthDp(){return 36;}
 static int iconHeightDp(){return 22;}
 static int posterBottomMarginDp(boolean detailed){
  return barHeightDp(detailed)+17;
 }
}