package com.smarthub.plus;
import java.net.URI;
import java.util.Locale;

/** Only explicit, user-selected URLs or Storage Access Framework documents. */
final class Software422Source {
 private Software422Source(){}
 static boolean validNetwork(String input){
  if(input==null||input.isEmpty()||input.length()>2048||input.indexOf('\n')>=0||input.indexOf('\r')>=0)return false;
  try{
   URI uri=new URI(input.trim());
   String scheme=uri.getScheme();
   if(scheme==null||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null)return false;
   scheme=scheme.toLowerCase(Locale.ROOT);
   if(!scheme.equals("http")&&!scheme.equals("https")&&!scheme.equals("rtsp")
       &&!scheme.equals("rtp")&&!scheme.equals("udp"))return false;
   // Never accept an arbitrary file:/content: path typed by the user:
   // local files are opened only via Android's document picker.
   return uri.getHost()!=null&&!uri.getHost().isEmpty();
  }catch(Exception e){return false;}
 }
}
