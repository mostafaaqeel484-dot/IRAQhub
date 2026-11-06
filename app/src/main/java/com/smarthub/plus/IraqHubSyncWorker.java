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
  // Live DVB / 4:2:2 decoding takes priority over XMLTV decompression and image fetching.
  // Keep the last valid cache and let WorkManager retry after playback ends.
  if(PlaybackLoadGuard.shouldDeferSync(c)){
   p.edit().putLong("iraqhub_unified_sync_deferred_at",System.currentTimeMillis())
     .putString("iraqhub_unified_sync_state","DEFERRED_ACTIVE_PLAYBACK").apply();
   return Result.retry();
  }
  boolean epgOk=false,posterOk=false;StringBuilder errors=new StringBuilder();
  try{int count=OnlineEpgGuide.syncKnownFeeds(c);epgOk=count>0;}catch(Exception e){errors.append("EPG:").append(e.getClass().getSimpleName()).append(';');}
  try{JSONObject manifest=new BetterPostersProvider(c).refreshManifest();posterOk=manifest!=null;}catch(Exception e){errors.append("BetterPosters:").append(e.getClass().getSimpleName()).append(';');}
  p.edit().putString("iraqhub_unified_sync_state",epgOk&&posterOk?"SUCCESS":"PARTIAL_OR_FAILED").putLong("iraqhub_unified_sync_at",System.currentTimeMillis()).putBoolean("iraqhub_unified_epg_ok",epgOk).putBoolean("iraqhub_unified_poster_manifest_ok",posterOk).putString("iraqhub_unified_sync_errors",errors.toString()).apply();
  return epgOk&&posterOk?Result.success():Result.retry();
 }
}
