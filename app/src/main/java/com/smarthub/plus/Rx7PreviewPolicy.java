package com.smarthub.plus;

import java.net.URI;
import java.util.Locale;

/** RX7 preview never assumes a DVB video plane is shareable. User-approved
 * HTTP(S) streams can be previewed for their exact bound current channel.
 * The firmware tuner is never retuned or opened by the Home tile. */
final class Rx7PreviewPolicy {
 static final String ENABLED="rx7_home_preview_enabled";
 static final String STREAM="rx7_home_preview_url";
 static final String BOUND="rx7_home_preview_channel";
 private Rx7PreviewPolicy(){}
 static boolean validStream(String value){
  if(value==null||value.length()<12||value.length()>2048||!value.equals(value.trim()))return false;
  try{
   URI uri=new URI(value);
   String scheme=uri.getScheme(),host=uri.getHost();
   return ("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme))
      &&host!=null&&!host.isEmpty()&&uri.getRawUserInfo()==null&&
      uri.getRawFragment()==null&&!value.contains("\\")&&
      !value.contains("\r")&&!value.contains("\n");
  }catch(Exception invalid){return false;}
 }
 static boolean allowed(boolean enabled,boolean focused,boolean foreground,
     boolean videoBusy,String expected,String actual,String uri){
  return enabled&&focused&&foreground&&!videoBusy&&expected!=null
     &&!expected.isEmpty()&&expected.equals(actual)&&validStream(uri);
 }
}
