package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.Locale;

/**
 * Receiver-info badges. Render only observed video dimensions, published CAS
 * metadata or the channel's explicit CAID list; NEVER infer BISS from a name.
 */
final class ReceiverTechnicalBadges {
 private ReceiverTechnicalBadges(){}
 static final String[] RESOLUTIONS={"SD","HD","FHD","4K","8K"};

 static String tier(int width,int height){
  if(width<320||height<240||width>8192||height>4608)return "";
  if(width>=7680&&height>=4320)return "8K";
  if(width>=3840&&height>=2160)return "4K";
  if(height>=1080)return "FHD";
  if(height>=720)return "HD";
  return "SD";
 }
 static String caName(int system){
  if(system<0||system>65535)return "";
  int group=system&0xff00;
  switch(group){
   case 0x0100:return "SECA";
   case 0x0500:return "VI";
   case 0x0600:return "IR";
   case 0x0900:return "NDS";
   case 0x0B00:return "CONAX";
   case 0x1700:return "BETA";
   case 0x1800:return "NAGRA";
   case 0x2600:return "BISS";
   default:return "";
  }
 }
 static String caIds(JSONArray ids){
  if(ids==null)return "";
  StringBuilder out=new StringBuilder();
  for(int i=0;i<Math.min(ids.length(),64);i++){
   String type=caName(ids.optInt(i,-1));
   if(type.isEmpty()||out.toString().contains(type))continue;
   if(out.length()>0)out.append(" · ");
   out.append(type);
   if(out.length()>50)break;
  }
  return out.toString();
 }
 static String encryption(Context c,SatelliteState state){
  SharedPreferences p=c.getSharedPreferences("smarthub",Context.MODE_PRIVATE);
  String own=p.getString("own_ca_channel","");
  if(!own.isEmpty()&&own.equals(state.channelId)){
   int ca=p.getInt("own_ca_flag",-1);
   if(ca==0)return "FTA";
   if(ca>0){
    String systems=p.getString("own_ca_systems","");
    return systems.isEmpty()?"CA":"CA · "+systems;
   }
  }
  String id=ReceiverInfoRepository.channelKey(state);
  long age=System.currentTimeMillis()-p.getLong("broadcast_encryption_seen:"+id,0);
  if(age>=0&&age<120000){
   String value=p.getString("broadcast_encryption:"+id,"");
   if("FTA".equals(value))return "FTA";
   if("ENCRYPTED".equals(value)){
    String type=p.getString("native_ca_type:"+id,"");
    long typed=System.currentTimeMillis()-p.getLong("native_ca_type_seen:"+id,0);
    return typed>=0&&typed<120000&&!type.isEmpty()?"CA · "+type:"CA";
   }
  }
  return "";
 }

 static String publishedCa(String text){
  if(text==null)return "";
  String upper=text.toUpperCase(Locale.ROOT);
  if(upper.contains("BISS"))return "BISS";
  if(upper.contains("IRDETO"))return "IR";
  if(upper.contains("VIACCESS"))return "VI";
  if(upper.contains("NAGRAVISION")||upper.contains("NAGRA"))return "NAGRA";
  if(upper.contains("CONAX"))return "CONAX";
  if(upper.contains("VIDEOGUARD")||upper.contains("NDS"))return "NDS";
  if(upper.contains("BETACRYPT"))return "BETA";
  if(upper.contains("SECA")||upper.contains("MEDIAGUARD"))return "SECA";
  return "";
 }
}
