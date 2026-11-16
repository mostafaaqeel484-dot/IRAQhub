package com.smarthub.plus;

/** Pure source gate: an exact stored DVB image is visible only when its
 * originating source is enabled. Empty marker is a legacy OEM capture, never
 * an excuse to read another app's private data. */
final class SavedLogoSourcePolicy {
 private SavedLogoSourcePolicy(){}
 static boolean allow(String source,boolean chmax,boolean external){
  if("MANUAL_CHANNEL_IMAGE".equals(source))return external;
  if(!chmax)return false;
  return source==null||source.isEmpty()||
   "CHMAX_AUTHORISED".equals(source)||
   "receiver visible EPG / CHMAX branding".equals(source);
 }
}
