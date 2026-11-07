package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.List;

/**
 * Offline-capable, read-only native DVB EIT refresh. It never tunes, scans or
 * edits firmware services. Only a physically verified active DVB service is
 * eligible; no XMLTV name-only matching or invented event counts.
 */
public final class DvbEpgRefreshWorker extends Worker {
 public DvbEpgRefreshWorker(@NonNull Context context,@NonNull WorkerParameters params){
  super(context,params);
 }
 @NonNull @Override public Result doWork(){
  Context c=getApplicationContext();SharedPreferences prefs=c.getSharedPreferences("smarthub",0);
  if(PlaybackResourceMode.active()||prefs.getBoolean("dvb_tuning",false)||prefs.getBoolean("software_dvb_422_active",false)
    ||!prefs.getString("iraqhub_pvr_session","").isEmpty()){
   prefs.edit().putString("native_epg_sync_status","DEFERRED_VIDEO_PRIORITY").apply();
   return Result.success();
  }
  try{
   SatelliteState state=SatelliteState.read(c);
   if(!state.channelId.startsWith("DVB:")){
    prefs.edit().putString("native_epg_sync_status","NO_ACTIVE_DVB_CHANNEL").apply();
    return Result.success();
   }
   ReceiverCatalog db=ReceiverCatalog.get(c);
   ReceiverCatalog.Channel ch=db.channel(prefs.getLong("own_dvb_channel",-1));
   if(ch==null||!state.channelId.equals("DVB:"+ch.physical())
      ||!ch.transponder().equals(prefs.getString("own_dvb_physical_tp",""))){
    prefs.edit().putString("native_epg_sync_status","UNVERIFIED_CHANNEL_IDENTITY").apply();
    return Result.success();
   }
   ReceiverSignalReading signal=DvbTuner.read();
   if(signal.locked!=1||signal.tp!=prefs.getInt("own_dvb_runtime_tp",-1)){
    prefs.edit().putString("native_epg_sync_status","TUNER_NOT_LOCKED").apply();
    return Result.success();
   }
   if(isStopped()||PlaybackResourceMode.active()||
     prefs.getBoolean("software_dvb_422_active",false))return Result.success();
   List<NativeEpgEvents.Event> events=BroadcastEpgProvider.read(ch.programToken(),signal.tp,ch.sid);
   if(isStopped()||PlaybackResourceMode.active()||
     prefs.getBoolean("software_dvb_422_active",false))return Result.success();
   long now=System.currentTimeMillis();
   NativeEpgEvents.Event current=BroadcastEpgProvider.current(events,now);
   NativeEpgEvents.Event next=BroadcastEpgProvider.next(events,now);
   if(current==null&&next==null){
    prefs.edit().putString("native_epg_sync_status","DVB_EIT_NO_VALID_EVENTS")
      .putInt("native_epg_sync_events",events.size()).apply();
    return Result.success();
   }
   SatelliteState latest=SatelliteState.read(c);
   if(!state.channelId.equals(latest.channelId)||prefs.getBoolean("dvb_tuning",false)){
    prefs.edit().putString("native_epg_sync_status","CHANNEL_CHANGED").apply();
    return Result.success();
   }
   SatelliteState timed=latest.copy();
   timed.source="DVB EIT · native receiver";
   timed.observedAt=now;
   timed.title=current==null?"":current.title;
   timed.startMs=current==null?0:current.start;
   timed.endMs=current==null?0:current.end;
   timed.image=current==null?"":current.image;
   timed.nextTitle=next==null?"":next.title;
   timed.nextStartMs=next==null?0:next.start;
   timed.nextEndMs=next==null?0:next.end;
   timed.nextImage=next==null?"":next.image;
   new SatelliteGuideRepository(c).record(timed);
   prefs.edit().putString("native_epg_sync_status","DVB_EIT_OBSERVED")
     .putInt("native_epg_sync_events",events.size())
     .putLong("native_epg_sync_success_at",now).apply();
   return Result.success();
  }catch(Exception unavailable){
   prefs.edit().putString("native_epg_sync_status",
     "DVB_EIT_UNAVAILABLE_"+unavailable.getClass().getSimpleName()).apply();
   return Result.success(); // Revisit at next 4h tick, without blocking other sources.
  }
 }
}
