package com.smarthub.plus;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only interpretation of the OEM Aurum search result screen.
 * Extracted from the user's Android 14 ADB snapshot, not a scan engine or
 * a substitute for the protected firmware channel database.
 */
final class AurumScanSummary {
 static final class Field {
  final String id, text;
  Field(String id,String text){this.id=id==null?"":id;this.text=text==null?"":text.trim();}
 }
 static final class Result {
  final int tv,radio,free,encrypted,hd,displayedTotal;
  final boolean completed;
  final long observedAt;
  Result(int tv,int radio,int free,int encrypted,int hd,int total,boolean completed,long at){
   this.tv=tv;this.radio=radio;this.free=free;this.encrypted=encrypted;
   this.hd=hd;this.displayedTotal=total;this.completed=completed;this.observedAt=at;
  }
  String identity(){return tv+"|"+radio+"|"+free+"|"+encrypted+"|"+hd+"|"+displayedTotal+"|"+completed;}
  JSONObject json()throws Exception{
   return new JSONObject().put("tv",tv).put("radio",radio).put("free",free)
     .put("encrypted",encrypted).put("hd",hd).put("total",displayedTotal)
     .put("completed",completed).put("observedAt",observedAt)
     .put("source","Aurum visible results");
  }
  static Result fromJson(String raw){
   try{
    JSONObject j=new JSONObject(raw);
    if(!"Aurum visible results".equals(j.optString("source")))return null;
    return new Result(j.getInt("tv"),j.getInt("radio"),j.getInt("free"),
      j.getInt("encrypted"),j.getInt("hd"),j.getInt("total"),
      j.getBoolean("completed"),j.getLong("observedAt"));
   }catch(Exception e){return null;}
  }
 }

 /** The OEM binds tv_search_result_title/info as consecutive pairs inside TV/Radio panes. */
 static Result parse(List<Field> fields,long now){
  if(fields==null||fields.size()>600||now<=0)return null;
  boolean hasSearchHeader=false,finished=false,sawTv=false,sawRadio=false;
  int scope=0,slot=-1,displayedTotal=-1;
  int[] tv=new int[]{-1,-1,-1,-1},radio=new int[]{-1,-1,-1,-1};
  for(Field f:fields){
   if(f==null)continue;
   String id=f.id;
   if(id.startsWith("com.tosmart.aurum:id/"))id=id.substring(21);
   String value=f.text;
   if(value.length()>100)continue;
   if("header_title".equals(id)){
    String low=value.toLowerCase(Locale.ROOT);
    if(low.equals("بحث")||low.equals("search")||low.equals("scan"))hasSearchHeader=true;
   }else if("tv_detail_title".equals(id)){scope=1;slot=-1;sawTv=true;}
   else if("tv_radio_title".equals(id)){scope=2;slot=-1;sawRadio=true;}
   else if("tv_search_result_title".equals(id)){
    String low=value.toLowerCase(Locale.ROOT);
    if(low.equals("الكل")||low.equals("all")||low.equals("total"))slot=0;
    else if(low.equals("فرى")||low.equals("مجاني")||low.equals("free")||low.equals("fta"))slot=1;
    else if(low.equals("أتش دى")||low.equals("hd"))slot=2;
    else if(low.equals("مشفر")||low.equals("encrypted")||low.equals("scrambled"))slot=3;
    else slot=-1;
   }else if("tv_search_result_info".equals(id)&&scope!=0&&slot>=0){
    if(value.matches("[0-9]{1,5}")){
     int n=Integer.parseInt(value);
     if(n<=10000)(scope==1?tv:radio)[slot]=n;
    }
    slot=-1;
   }else if("channel_all_total_count".equals(id)){
    java.util.regex.Matcher number=java.util.regex.Pattern.compile("[0-9]{1,5}").matcher(value);
    if(number.find())displayedTotal=Integer.parseInt(number.group());
   }else if("tv_tips".equals(id)){
    String low=value.toLowerCase(Locale.ROOT);
    finished=low.contains("انتهاء المسح")||low.contains("scan finished")||
      low.contains("scan completed")||low.contains("search complete");
   }
  }
  if(!hasSearchHeader||!finished||!sawTv||!sawRadio||tv[0]<0||radio[0]<0)return null;
  int total=tv[0]+radio[0];
  if(total>10000||displayedTotal>=0&&displayedTotal!=total)return null;
  int free=(tv[1]<0?0:tv[1])+(radio[1]<0?0:radio[1]);
  int encrypted=(tv[3]<0?0:tv[3])+(radio[3]<0?0:radio[3]);
  int hd=(tv[2]<0?0:tv[2])+(radio[2]<0?0:radio[2]);
  if(free>total||encrypted>total||hd>total)return null;
  return new Result(tv[0],radio[0],free,encrypted,hd,total,true,now);
 }
 private AurumScanSummary(){}
}
