package com.smarthub.plus;

/** Defaults to daily actions; diagnostics and API credentials stay one
 * deliberate "More" action away. No receiver capability is deleted. */
final class Rx7SimpleUiPolicy {
 private Rx7SimpleUiPolicy(){}
 static boolean isAdvancedCategory(String category) {
  return "API".equals(category)||"ADVANCED".equals(category);
 }
 static boolean showCategory(String category,boolean expanded) {
  return expanded||!isAdvancedCategory(category);
 }
 static int pinnedRows(boolean showExtras){return showExtras?2:1;}
}
