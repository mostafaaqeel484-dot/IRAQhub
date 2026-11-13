package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.List;

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

 static boolean eligible(String physical,String channel){
  return physical!=null&&physical.startsWith("DVB:")&&physical.length()<=180
    &&!OsnGuideReference.urlFor(channel).isEmpty();
 }
 static List<NativeEpgEvents.Event> read(Context c,String channel,String physical,long time){
  if(!eligible(physical,channel))return Collections.emptyList();
  SharedPreferences prefs=c.getSharedPreferences("smarthub",0);
  if(!prefs.getBoolean(ENABLED,true))return Collections.emptyList();
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
