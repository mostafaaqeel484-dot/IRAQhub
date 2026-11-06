package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

/** Background source refresh. EPG and poster failures are isolated; existing valid caches remain usable. */
public final class IraqHubSyncWorker extends Worker {
 public IraqHubSyncWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
 @NonNull @Override public Result doWork(){
  Context c=getApplicationContext();SharedPreferences p=c.getSharedPreferences("smarthub",0);
  // Give active MPEG-TS capture and software 4:2:2 decoder CPU/disk priority.
  // Preserve the last valid XMLTV and artwork cache, then let WorkManager retry.
  if(p.getBoolean("software_dvb_422_active",false)||p.getBoolean("dvb_tuning",false)){
   p.edit().putString("iraqhub_unified_sync_state","DEFERRED_VIDEO_PRIORITY")
      .putLong("iraqhub_unified_sync_deferred_at",System.currentTimeMillis()).apply();
   return Result.retry();
  }
  boolean epgOk=false,posterOk=false;StringBuilder errors=new StringBuilder();
  try{int count=OnlineEpgGuide.syncKnownFeeds(c);epgOk=count>0;}catch(Exception e){errors.append("EPG:").append(e.getClass().getSimpleName()).append(';');}
  try{JSONObject manifest=new BetterPostersProvider(c).refreshManifest();posterOk=manifest!=null;}catch(Exception e){errors.append("BetterPosters:").append(e.getClass().getSimpleName()).append(';');}
  p.edit().putLong("iraqhub_unified_sync_at",System.currentTimeMillis()).putBoolean("iraqhub_unified_epg_ok",epgOk).putBoolean("iraqhub_unified_poster_manifest_ok",posterOk).putString("iraqhub_unified_sync_errors",errors.toString()).apply();
  return epgOk&&posterOk?Result.success():Result.retry();
 }
}
