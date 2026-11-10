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
  if(PlaybackResourceMode.active()||p.getBoolean("software_dvb_422_active",false)||p.getBoolean("dvb_tuning",false)
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
   if(isStopped()||PlaybackResourceMode.active()||
      p.getBoolean("software_dvb_422_active",false))
    return Result.success();
   // Isolate sources: a failed movie/TV lookup must not block sports.
   // A missing sports event must not erase film/series posters either.
   int verified=0,sports=0;
   // Sports must not depend on the user opening Sports Center first.
   // Network refresh runs on MatchRepository's bounded background executor;
   // when it completes, it prefetches source-verified images independently.
   try{
    MatchRepository.get(c).refresh(
      java.time.LocalDate.now(java.time.ZoneId.of("Asia/Baghdad")),"day",false);
   }catch(Exception unavailable){
    p.edit().putString("sports_match_refresh_error",unavailable.getClass().getSimpleName()).apply();
   }
   if(isStopped()||PlaybackResourceMode.active()||
      p.getBoolean("software_dvb_422_active",false))
    return Result.success();
   try{verified=UnifiedArtworkPrefetch.currentAndNext(c,provider);}
   catch(Exception unavailable){
    p.edit().putString("film_poster_prefetch_error",unavailable.getClass().getSimpleName()).apply();
   }
   if(isStopped()||PlaybackResourceMode.active()||
      p.getBoolean("software_dvb_422_active",false))
    return Result.success();
   try{sports=SportsPosterPrefetch.prefetch(c);}
   catch(Exception unavailable){
    p.edit().putString("sports_poster_prefetch_error",unavailable.getClass().getSimpleName()).apply();
   }
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
