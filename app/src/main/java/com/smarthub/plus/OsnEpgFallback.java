package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Opt-in exact-channel OSN gap fill. This uses EPGShare SA1, NOT an OSN API.
 * OEM EPG Pro and DVB EIT must be checked first by BroadcastEpgProvider.
 * No channel search, no synthetic programme and no guessed guide alignment. */
final class OsnEpgFallback {
 static final String ENABLED="osn_epg_gapfill_enabled";
 static final String STATUS="osn_epg_gapfill_status";
 private static final String FEED="SA1";
 private static final long SIX_HOURS=6L*3600000L;
 private static final Object DOWNLOAD_LOCK=new Object();
 private OsnEpgFallback(){}

 private static final long FOUR_HOURS=14400000L;
 static boolean officialChannel(String physical,String name){
  return physical!=null&&physical.startsWith("DVB:")&&name!=null&&
    name.trim().matches("(?i)^OSN(?:\\s*TV)?\\s+.+");
 }

 static synchronized List<NativeEpgEvents.Event> official(Context c,String channel,long now){
  SharedPreferences prefs=c.getSharedPreferences("smarthub",0);
  try{
   String catalog=prefs.getString("osn_catalog","");
   long updated=prefs.getLong("osn_catalog_at",0);
   if(catalog.isEmpty()||now<updated||now-updated>=FOUR_HOURS){
    long attempt=prefs.getLong("osn_catalog_attempt",0);
    if(now<attempt||now-attempt>=300000L){
     prefs.edit().putLong("osn_catalog_attempt",now).apply();
     DirectHttp.Reply reply=DirectHttp.request("https://www.osn.com/apidata/channels-legacy",
       Collections.emptyMap(),null,256*1024,null);
     if(reply.code==200){
      String candidate=new String(reply.bytes,StandardCharsets.UTF_8);
      if(new JSONArray(candidate).length()>0){
       catalog=candidate;
       prefs.edit().putString("osn_catalog",catalog).putLong("osn_catalog_at",now).apply();
      }
     }
    }
   }
   if(catalog.isEmpty())return Collections.emptyList();
   String id=channelId(catalog,channel);
   if(id.isEmpty())return Collections.emptyList();
   String key="osn_guide:"+id;
   long saved=prefs.getLong(key+":at",0),attempt=prefs.getLong(key+":attempt",0);
   if((now<saved||now-saved>=FOUR_HOURS)&&(now<attempt||now-attempt>=300000L)){
    prefs.edit().putLong(key+":attempt",now).apply();
    String url="https://www.osn.com/apidata/channel-day-schedule?channelId="+id+"&lang=en&startTime="
      +(now-43200000L)+"&endTime="+(now+86400000L);
    DirectHttp.Reply reply=DirectHttp.request(url,Collections.emptyMap(),null,512*1024,null);
    if(reply.code==200){
     String data=new String(reply.bytes,StandardCharsets.UTF_8);
     if(!parseOfficial(data,id,channel).isEmpty())prefs.edit().putString(key,data)
       .putLong(key+":at",now).apply();
    }
   }
   saved=prefs.getLong(key+":at",0);
   if(saved>0&&saved<=now&&now-saved<=21600000L)
    return parseOfficial(prefs.getString(key,""),id,channel);
  }catch(Exception unavailable){prefs.edit().putString(STATUS,"OSN_OFFICIAL_UNAVAILABLE").apply();}
  return Collections.emptyList();
 }

 static String channelId(String json,String channel)throws Exception{
  JSONArray rows=new JSONArray(json);
  String match="";
  for(int i=0;i<Math.min(1000,rows.length());i++){
   JSONObject row=rows.optJSONObject(i);
   if(row==null||!FactoryProtocol.compatibleChannelName(channel,row.optString("title")))continue;
   String id=row.optString("guid");
   if(!id.matches("[0-9]{1,10}"))continue;
   if(!match.isEmpty()&&!match.equals(id))return "";
   match=id;
  }
  return match;
 }
 static List<NativeEpgEvents.Event> parseOfficial(String json,String id,String channel)throws Exception{
  JSONArray rows=new JSONObject(json).optJSONArray("programs");
  List<NativeEpgEvents.Event> events=new ArrayList<>();
  if(rows==null)return events;
  for(int i=0;i<Math.min(100,rows.length());i++){
   JSONObject row=rows.optJSONObject(i);
   if(row==null||!id.equals(row.optString("channelId"))||
      !FactoryProtocol.compatibleChannelName(row.optString("channelName"),channel))continue;
   long start=row.optLong("startTime"),end=row.optLong("endTime");
   String title=row.optString("programTitle").trim();
   if(start>0&&end>start&&end-start<=86400000L&&title.length()<=300&&ProgrammeTitle.available(title))
    events.add(new NativeEpgEvents.Event(title,start,end,PlaylistRepository.epgImage(row.optString("landscapeImageUrl"))));
  }
  events.sort(Comparator.comparingLong(e->e.start));
  return events;
 }

 static boolean eligible(String physical,String channel){
  return physical!=null&&physical.startsWith("DVB:")&&physical.length()<=180
    &&!OsnGuideReference.urlFor(channel).isEmpty();
 }
 static List<NativeEpgEvents.Event> read(Context c,String channel,String physical,long time){
  if(!eligible(physical,channel))return Collections.emptyList();
  SharedPreferences prefs=c.getSharedPreferences("smarthub",0);
  if(!prefs.getBoolean(ENABLED,false))return Collections.emptyList();
  OnlineEpgGuide guide=new OnlineEpgGuide(c);
  try{
   List<NativeEpgEvents.Event> cached=guide.current(channel,physical,time);
   if(!cached.isEmpty()){
    prefs.edit().putString(STATUS,"EPGSHARE_SA1_MATCHED").apply();
    return cached;
   }
   synchronized(DOWNLOAD_LOCK){
    // Never poll OSN website or blindly download after every zap.
    long previous=prefs.getLong("osn_epg_sa1_attempt_at",0);
    if(previous>0&&time-previous>=0&&time-previous<SIX_HOURS){
     prefs.edit().putString(STATUS,"EPGSHARE_SA1_NO_MATCH_OR_NOT_DUE").apply();
     return Collections.emptyList();
    }
    prefs.edit().putLong("osn_epg_sa1_attempt_at",time).apply();
    guide.download(FEED,false);
   }
   List<NativeEpgEvents.Event> fresh=guide.current(channel,physical,time);
   prefs.edit().putString(STATUS,fresh.isEmpty()?
    "EPGSHARE_SA1_NO_EXACT_MATCH":"EPGSHARE_SA1_MATCHED").apply();
   return fresh;
  }catch(Exception unavailable){
   prefs.edit().putString(STATUS,
     "EPGSHARE_SA1_UNAVAILABLE_"+unavailable.getClass().getSimpleName()).apply();
   return Collections.emptyList();
  }
 }
}
