package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;

/** Independent 4-hour poster task; never blocks the XMLTV/DVB EPG schedule. */
public final class PosterRefreshWorker extends Worker {
 public PosterRefreshWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
 @NonNull @Override public Result doWork(){
  Context c=getApplicationContext();SharedPreferences p=c.getSharedPreferences("smarthub",0);
  if(p.getBoolean("software_dvb_422_active",false)||p.getBoolean("dvb_tuning",false)
     ||!p.getString("iraqhub_pvr_session","").isEmpty()){
   p.edit().putString("poster_sync_status","DEFERRED_VIDEO_PRIORITY").apply();
   // No repeated background retry loop while the tuner / 4:2:2 decoder needs CPU.
   return Result.success();
  }
  try{
   BetterPostersProvider provider=new BetterPostersProvider(c);
   boolean manifestOk=false;
   try{manifestOk=provider.refreshManifest()!=null;}catch(Exception ignored){
    // Failure of BetterPosters must not block TMDB, TVmaze or cached artwork.
   }
   int verified=UnifiedArtworkPrefetch.currentAndNext(c,provider);
   int sports=SportsPosterPrefetch.prefetch(c);
   // A valid addon Manifest is NOT proof that a poster was downloaded.
   String status=verified+sports>0?"POSTERS_VERIFIED_AND_CACHED":
     manifestOk?"POSTER_MANIFEST_OK_NO_VERIFIED_IMAGE":"NO_VERIFIED_POSTER_SOURCE";
   p.edit().putString("poster_sync_status",status)
    .putLong("poster_sync_success_at",System.currentTimeMillis())
    .putInt("poster_sync_verified_prefetch",verified)
    .putInt("sports_poster_prefetch_verified",sports)
    .putBoolean("iraqhub_unified_poster_manifest_ok",manifestOk).apply();
   return Result.success();
  }catch(Exception unavailable){
   p.edit().putString("poster_sync_status",
      "POSTER_SOURCE_UNAVAILABLE_"+unavailable.getClass().getSimpleName()).apply();
   return Result.retry();
  }
 }
}
