package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Independent XMLTV feed updater. Poster and offline DVB EIT jobs cannot block it. */
public final class IraqHubSyncWorker extends Worker {
 public IraqHubSyncWorker(@NonNull Context c,@NonNull WorkerParameters params){super(c,params);}
 @NonNull @Override public Result doWork(){
  Context c=getApplicationContext();SharedPreferences p=c.getSharedPreferences("smarthub",0);
  if(PlaybackResourceMode.active()||p.getBoolean("software_dvb_422_active",false)||p.getBoolean("dvb_tuning",false)
    ||!p.getString("iraqhub_pvr_session","").isEmpty()){
   p.edit().putString("iraqhub_unified_sync_state","DEFERRED_VIDEO_PRIORITY")
    .putLong("iraqhub_unified_sync_deferred_at",System.currentTimeMillis()).apply();
   return Result.retry();
  }
  try{
   int feeds=OnlineEpgGuide.syncKnownFeeds(c);
   if(isStopped()||PlaybackResourceMode.active()||
     p.getBoolean("software_dvb_422_active",false))
    return Result.success();
   p.edit().putLong("iraqhub_unified_sync_at",System.currentTimeMillis())
     .putBoolean("iraqhub_unified_epg_ok",feeds>0)
     .putString("iraqhub_unified_sync_state",feeds>0?"XMLTV_SYNC_OK":"XMLTV_NO_FEEDS")
     .putString("iraqhub_unified_sync_errors","").apply();
   return feeds>0?Result.success():Result.retry();
  }catch(Exception error){
   p.edit().putBoolean("iraqhub_unified_epg_ok",false)
     .putString("iraqhub_unified_sync_errors",
       "XMLTV:"+error.getClass().getSimpleName())
     .putString("iraqhub_unified_sync_state","XMLTV_UPDATE_FAILED").apply();
   return Result.retry();
  }
 }
}
