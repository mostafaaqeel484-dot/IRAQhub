package com.smarthub.plus;
import android.content.*;import android.os.SystemClock;import org.json.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.*;
/** Shared EPG provider. Native EIT first, public guide fallback, correlated to the current channel. */
final class BroadcastEpgProvider {
 private final Context context;private final ExecutorService io=Executors.newSingleThreadExecutor();private final OnlineEpgGuide online;private volatile boolean busy;private String key="";private long retry;private volatile int generation;private Future<?> task;
 BroadcastEpgProvider(Context c){context=c.getApplicationContext();online=new OnlineEpgGuide(context);}
 synchronized void feedArrived(String feed,SatelliteState active){
  if(active==null||active.channelName.isEmpty()||
     !feed.equals(OnlineEpgGuide.feed(active.channelName)))return;
  retry=0;
  if(!busy)refresh(active);
 }
 synchronized void refresh(SatelliteState observed){if(observed.channelName.isEmpty()||observed.channelId.startsWith("IPTV:")||observed.source.startsWith("IPTV"))return;SharedPreferences prefs=context.getSharedPreferences("smarthub",0);if(prefs.getBoolean("dvb_tuning",false)||PlaybackResourceMode.active())return;String identity=ReceiverInfoRepository.channelKey(observed);long now=SystemClock.elapsedRealtime();if(busy&&identity.equals(key)||identity.equals(key)&&now<retry)return;if(busy){generation++;if(task!=null)task.cancel(true);busy=false;}if(!observed.channelId.startsWith("DVB:")&&!observed.title.isEmpty()&&!observed.source.startsWith("DVB")&&!observed.source.startsWith("Online EPG"))return;ReceiverSignalReading signal=DirectReceiverSignal.reading(observed);boolean own=observed.channelId.startsWith("DVB:");boolean nativeReady=own&&signal!=null&&signal.locked==1&&signal.tp==prefs.getInt("own_dvb_runtime_tp",-1)&&observed.channelId.equals("DVB:"+prefs.getString("own_dvb_physical",""))&&prefs.getString("own_dvb_physical","").startsWith(prefs.getString("own_dvb_physical_tp","invalid")+":");busy=true;key=identity;retry=now+30000;int ticket=++generation;long started=now;prefs.edit().putString("broadcast_epg_status","DVB_EPG_SEARCHING").putString("broadcast_epg_channel",identity).apply();SatelliteState snapshot=observed.copy();task=io.submit(()->{try{List<NativeEpgEvents.Event> events=new ArrayList<>();String source="DVB EIT · IRAQhub";if(nativeReady){ReceiverCatalog.Channel channel=ReceiverCatalog.get(context).channel(prefs.getLong("own_dvb_channel",-1));if(channel!=null&&snapshot.channelId.equals("DVB:"+channel.physical()))try{events=read(channel.programToken(),signal.tp,channel.sid);}catch(java.io.IOException unavailable){/* Continue with factory/native fallback. */}}if(Thread.currentThread().isInterrupted()||ticket!=generation)return;long time=System.currentTimeMillis();
 // EPG Pro, DVB EIT and EPG ALL are independently verified DVB sources.
 // EPG ALL is this app's indexed union, NOT an invented OEM API.
 if(Thread.currentThread().isInterrupted()||ticket!=generation)return;
 SatelliteState active=SatelliteState.read(context);
 SatelliteState original=active.copy();
 if(!identity.equals(ReceiverInfoRepository.channelKey(active)))return;
 if(prefs.getBoolean("dvb_tuning",false)||PlaybackResourceMode.lite(context))return;
 if(nativeReady){
  ReceiverSignalReading latest=DirectReceiverSignal.reading(active);
  if(latest==null||latest.locked!=1||latest.tp!=signal.tp||
      prefs.getBoolean("dvb_tuning",false))return;
 }
 long observedAt=System.currentTimeMillis();
 UnifiedEpgIndex index=UnifiedEpgIndex.get(context);
 if(own&&!events.isEmpty())index.recordNative(active.channelId,events,observedAt);
 // Prepared EPG Pro may contain NOW only or NEXT only; do not discard the
 // other valid programme source just because one manufacturer field exists.
 boolean factoryNow=FactoryProvider.applyPrepared(context,active,false);
 boolean factoryNext=FactoryProvider.applyPrepared(context,active,true);
 SatelliteGuideRepository.Event allNow=own?index.receiverCurrent(active.channelId,observedAt):null;
 SatelliteGuideRepository.Event allNext=own?index.receiverNext(active.channelId,observedAt):null;
 if(!factoryNow&&allNow!=null&&
    DvbEpgAllPolicy.current(allNow.startMs,allNow.endMs,observedAt,allNow.title)){
  active.title=allNow.title;active.startMs=allNow.startMs;
  active.endMs=allNow.endMs;active.image=allNow.image;
  active.source=allNow.source;factoryNow=true;
 }
 if(!factoryNext&&allNext!=null&&
    DvbEpgAllPolicy.next(allNext.startMs,allNext.endMs,observedAt,allNext.title)){
  active.nextTitle=allNext.title;active.nextStartMs=allNext.startMs;
  active.nextEndMs=allNext.endMs;active.nextImage=allNext.image;factoryNext=true;
 }
 NativeEpgEvents.Event eitNow=current(events,observedAt),eitNext=next(events,observedAt);
 if(!factoryNow&&eitNow!=null){
  active.title=eitNow.title;active.startMs=eitNow.start;
  active.endMs=eitNow.end;active.image=eitNow.image;
  active.source="DVB EIT · native receiver";factoryNow=true;
 }
 if(!factoryNext&&eitNext!=null){
  active.nextTitle=eitNext.title;active.nextStartMs=eitNext.start;
  active.nextEndMs=eitNext.end;active.nextImage=eitNext.image;factoryNext=true;
 }
 // For OSN, never confuse a cached EPGShare event with the DVB source.
 // A manually enabled internet bridge is a last resort, never a DVB default.
 if((!factoryNow||!factoryNext)&&OsnEpgFallback.eligible(
       active.channelId,active.channelName)&&
    prefs.getBoolean(OsnEpgFallback.ENABLED,false)){
  List<NativeEpgEvents.Event> extra=OsnEpgFallback.read(
      context,active.channelName,active.channelId,observedAt);
  if(!factoryNow){
   NativeEpgEvents.Event e=current(extra,observedAt);
   if(e!=null){active.title=e.title;active.startMs=e.start;active.endMs=e.end;
    active.image=e.image;active.source="EPGShare SA1 · manual OSN fallback";
    factoryNow=true;}
  }
  if(!factoryNext){
   NativeEpgEvents.Event e=next(extra,observedAt);
   if(e!=null){active.nextTitle=e.title;active.nextStartMs=e.start;
    active.nextEndMs=e.end;active.nextImage=e.image;factoryNext=true;}
  }
 }
 if(!factoryNow){
  // No guessed programme. Keep a genuinely timed receiver programme if
  // already present, otherwise clear stale/internet-only channel values.
  if(!DvbEpgAllPolicy.receiverGrade(active.source)||
     !DvbEpgAllPolicy.current(active.startMs,active.endMs,observedAt,active.title)){
   active.title="";active.startMs=0;active.endMs=0;active.image="";
   active.source="";
  }
 }
 if(!factoryNext&&!DvbEpgAllPolicy.next(active.nextStartMs,active.nextEndMs,
     observedAt,active.nextTitle)){
  active.nextTitle="";active.nextStartMs=0;active.nextEndMs=0;active.nextImage="";
 }
 if(!DvbEpgAllPolicy.current(active.startMs,active.endMs,observedAt,active.title)){
  retry=SystemClock.elapsedRealtime()+12000;
  prefs.edit().putString("broadcast_epg_status",
      "DVB_EPG_ALL_NO_VALID_CURRENT").putString("epg_all_channel",active.channelId)
      .putString("epg_all_osn_status",
        active.channelName.toUpperCase(java.util.Locale.ROOT).contains("OSN")?
         "OSN_FACTORY_EIT_ALL_UNAVAILABLE":"NOT_OSN").apply();
  return;
 }
 if(!identity.equals(ReceiverInfoRepository.channelKey(SatelliteState.read(context))))return;
 source=active.source;
 boolean artChanged=!original.title.equals(active.title)||
    original.startMs!=active.startMs||!original.image.equals(active.image)||
    !original.nextTitle.equals(active.nextTitle)||
    original.nextStartMs!=active.nextStartMs||
    !original.nextImage.equals(active.nextImage);
 active.observedAt=time;active.save(context);
 new SatelliteGuideRepository(context).record(active);
 SharedPreferences.Editor update=prefs.edit().putString("broadcast_epg_status",source).putString("epg_all_status","DVB_EPG_ALL_VERIFIED").putString("epg_all_channel",active.channelId).putLong("broadcast_epg_updated_at",time).putLong("broadcast_epg_elapsed_ms",SystemClock.elapsedRealtime()-started);if(artChanged)update.putLong("epg_art_changed_at",time);update.apply();if(artChanged)IraqHubSyncScheduler.programmeChanged(context);}catch(Exception unavailable){if(Thread.currentThread().isInterrupted()||ticket!=generation)return;retry=SystemClock.elapsedRealtime()+12000;prefs.edit().putString("broadcast_epg_status","EPG_UNAVAILABLE_"+unavailable.getClass().getSimpleName()).apply();}finally{synchronized(this){if(ticket==generation)busy=false;}}});}
 static NativeEpgEvents.Event current(List<NativeEpgEvents.Event> events,long now){NativeEpgEvents.Event found=null;for(NativeEpgEvents.Event event:events)if(event.start<=now&&event.end>now&&(found==null||event.start>found.start))found=event;return found;}
 static NativeEpgEvents.Event next(List<NativeEpgEvents.Event> events,long now){NativeEpgEvents.Event found=null;for(NativeEpgEvents.Event event:events)if(event.start>now&&(found==null||event.start<found.start))found=event;return found;}
 static List<NativeEpgEvents.Event> read(int programme,int tp,int sid)throws Exception{List<NativeEpgEvents.Event> events=new ArrayList<>();try(DatagramSocket socket=new DatagramSocket()){socket.setSoTimeout(120);byte[] query=("EPG_TDT\n0004\n"+NativeEpgEvents.request(programme,tp,sid,System.currentTimeMillis())).getBytes(StandardCharsets.UTF_8);socket.send(new DatagramPacket(query,query.length,InetAddress.getByName("127.0.0.1"),51021));long began=SystemClock.elapsedRealtime(),end=began+1000;while(SystemClock.elapsedRealtime()<end&&!Thread.currentThread().isInterrupted()){byte[] b=new byte[65507];DatagramPacket packet=new DatagramPacket(b,b.length);try{socket.receive(packet);}catch(SocketTimeoutException idle){continue;}if(packet.getAddress().isLoopbackAddress()){
 events.addAll(NativeEpgEvents.packet(new String(b,0,packet.getLength(),StandardCharsets.UTF_8),programme));
 long now=System.currentTimeMillis();
 if(SystemClock.elapsedRealtime()-began>=180&&current(events,now)!=null&&next(events,now)!=null)break;
}}}events.sort(Comparator.comparingLong(e->e.start));return events;}
}
