package com.smarthub.plus;

import java.net.URI;
import java.util.Locale;

/** Only user-supplied network video URLs; no local private paths. */
final class Software422Source {
 private Software422Source(){}
 static boolean validNetwork(String source){
  if(source==null||source.length()>2048||source.indexOf('\n')>=0||source.indexOf('\r')>=0)return false;
  try{
   URI uri=new URI(source);
   String scheme=uri.getScheme();
   if(scheme==null||uri.getRawUserInfo()!=null)return false;
   scheme=scheme.toLowerCase(Locale.ROOT);
   if(!scheme.equals("http")&&!scheme.equals("https")&&!scheme.equals("udp")
     &&!scheme.equals("rtp")&&!scheme.equals("rtsp"))return false;
   return uri.getHost()!=null&&!uri.getHost().isEmpty();
  }catch(Exception bad){return false;}
 }
}
