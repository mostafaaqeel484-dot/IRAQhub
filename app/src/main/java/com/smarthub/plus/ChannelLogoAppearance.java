package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** RX6 channel art preferences. Per physical DVB service, without ever writing OEM storage. */
final class ChannelLogoAppearance {
 static final String GLOBAL="rx6_channel_logo_style_global";
 static final String PREFIX="rx6_channel_logo_style_";
 private ChannelLogoAppearance(){}

 static final class Config {
  final int size;
  final boolean removeWhite,removeBlack,crop,flag;
  final String background;
  Config(int percent,boolean white,boolean black,boolean cropTransparent,boolean showFlag,String background){
   this.size=clamp(percent);removeWhite=white;removeBlack=black;
   crop=cropTransparent;flag=showFlag;this.background=safeBackground(background);
  }
  String signature(){return size+"|"+removeWhite+"|"+removeBlack+"|"+crop+"|"+flag+"|"+background;}
  JSONObject json(){
   JSONObject data=new JSONObject();
   try{data.put("size",size);data.put("white",removeWhite);data.put("black",removeBlack);
    data.put("crop",crop);data.put("flag",flag);data.put("background",background);}
   catch(Exception ignored){}
   return data;
  }
 }
 static int clamp(int value){return Math.max(65,Math.min(125,value));}
 static String safeBackground(String value){
  return "black".equals(value)||"white".equals(value)?value:"transparent";
 }
 static boolean physical(String id){
  return id!=null&&id.startsWith("DVB:")&&id.length()<=180&&id.length()>8;
 }
 static String key(String channelId){
  if(!physical(channelId))return GLOBAL;
  try{
   byte[] hash=MessageDigest.getInstance("SHA-256").digest(channelId.getBytes(StandardCharsets.UTF_8));
   StringBuilder hex=new StringBuilder(PREFIX);
   for(int i=0;i<12;i++)hex.append(String.format(Locale.ROOT,"%02x",hash[i]&255));
   return hex.toString();
  }catch(Exception ignored){return GLOBAL;}
 }
 static Config read(Context c,String channelId){
  SharedPreferences p=c.getSharedPreferences("smarthub",0);
  String json=p.getString(key(channelId),"");
  if(json.isEmpty())json=p.getString(GLOBAL,"");
  int oldSize=p.getInt("osd_channel_logo_scale_percent",100);
  boolean oldWhite=p.getBoolean("osd_channel_logo_remove_white",true);
  boolean oldFlag=p.getBoolean("osd_show_channel_flag",true);
  JSONObject data;
  try{data=new JSONObject(json);}catch(Exception ignored){data=new JSONObject();}
  return new Config(data.optInt("size",oldSize),data.optBoolean("white",oldWhite),
   data.optBoolean("black",false),data.optBoolean("crop",true),
   data.optBoolean("flag",oldFlag),data.optString("background","transparent"));
 }
 static void save(Context c,String channelId,Config value){
  String k=key(channelId);
  if(!GLOBAL.equals(k)&&!physical(channelId))throw new IllegalArgumentException("INVALID_DVB_SERVICE");
  c.getSharedPreferences("smarthub",0).edit().putString(k,value.json().toString())
   .putLong("osd_changed_at",System.currentTimeMillis()).apply();
 }
 static void reset(Context c,String channelId){
  c.getSharedPreferences("smarthub",0).edit().remove(key(channelId))
   .putLong("osd_changed_at",System.currentTimeMillis()).apply();
 }
}
