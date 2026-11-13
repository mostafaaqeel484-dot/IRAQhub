package com.smarthub.plus;

import android.content.*;
import java.util.*;

/** Shared read model for launcher, infobar, companion and the CHMAX integration boundary. */
final class ReceiverInfoRepository {
    private static volatile ReceiverInfoRepository instance;
    private final Context context;
    private SatelliteGuideRepository guide;private BroadcastEpgProvider epg;
    private final java.util.concurrent.ExecutorService guideIo=
      java.util.concurrent.Executors.newSingleThreadExecutor();
    private String quickChannel="",manualChannel="";
    private long quickCheckedAt=-1,manualCheckedAt=-1;
    private volatile SatelliteGuideRepository.Event quickNow,quickNext;
    private volatile java.util.List<NativeEpgEvents.Event> manualEvents=
       java.util.Collections.emptyList();
    private java.util.concurrent.Future<?> quickJob,manualJob;
    private int quickGeneration,manualGeneration;
    private boolean quickLoading,manualLoading;
    // Screen reads are memory-only. Cold SQLite/manual XMLTV reads are async,
    // and stale channel requests are cancelled before touching view state.
    private synchronized void refreshQuick(String channel,long now){
        if(channel==null||!channel.startsWith("DVB:"))return;
        if(!channel.equals(quickChannel)){
            quickChannel=channel;quickCheckedAt=-1;
            quickNow=null;quickNext=null;quickLoading=false;
            ++quickGeneration;
            if(quickJob!=null)quickJob.cancel(true);
        }
        if(quickLoading||quickCheckedAt>=0&&now>=quickCheckedAt&&
              now-quickCheckedAt<1200)return;
        final int token=++quickGeneration;
        quickLoading=true;
        quickJob=guideIo.submit(()->{
            SatelliteGuideRepository.Event current=null,next=null;
            try{
                UnifiedEpgIndex index=UnifiedEpgIndex.get(context);
                current=index.receiverCurrent(channel,System.currentTimeMillis());
                next=index.receiverNext(channel,System.currentTimeMillis());
            }catch(Exception unavailable){/* Native tuner and factory EPG remain usable. */}
            synchronized(ReceiverInfoRepository.this){
                if(token!=quickGeneration||!channel.equals(quickChannel))return;
                quickNow=current;quickNext=next;
                quickCheckedAt=System.currentTimeMillis();
                quickLoading=false;
            }
        });
    }
    private synchronized java.util.List<NativeEpgEvents.Event> manualCached(
          SatelliteState observed,long now){
        String id=observed.channelId;
        if(!id.equals(manualChannel)){
            manualChannel=id;manualCheckedAt=-1;manualLoading=false;
            manualEvents=java.util.Collections.emptyList();
            ++manualGeneration;
            if(manualJob!=null)manualJob.cancel(true);
        }
        if(!manualLoading&&(manualCheckedAt<0||
            now-manualCheckedAt>=15000)){
            manualLoading=true;
            final int token=++manualGeneration;
            final String name=observed.channelName;
            manualJob=guideIo.submit(()->{
                java.util.List<NativeEpgEvents.Event> rows=java.util.Collections.emptyList();
                try{rows=ManualEpgStore.read(context,id,name,System.currentTimeMillis());}
                catch(Exception unavailable){}
                synchronized(ReceiverInfoRepository.this){
                    if(token!=manualGeneration||!id.equals(manualChannel))return;
                    manualEvents=rows;
                    manualCheckedAt=System.currentTimeMillis();
                    manualLoading=false;
                }
            });
        }
        return manualEvents;
    }
    static boolean timedCurrent(String title,long start,long end,long now){
        return title!=null&&!title.trim().isEmpty()&&start>0&&start<=now&&end>now;
    }
    static boolean timedNext(String title,long start,long end,long now){
        return title!=null&&!title.trim().isEmpty()&&start>now&&end>start;
    }
    synchronized void refreshEpg(SatelliteState state){if(epg==null)epg=new BroadcastEpgProvider(context);epg.refresh(state);}
    synchronized void epgFeedArrived(String feed){
        if(epg!=null)epg.feedArrived(feed,SatelliteState.read(context));
    }
    private DirectReceiverSignal signal;private int signalClients;
    synchronized DirectReceiverSignal acquireSignal(){if(signal==null)signal=new DirectReceiverSignal(context);signalClients++;return signal;}
    synchronized void releaseSignal(DirectReceiverSignal client){if(signal!=client)return;if(--signalClients<=0){signal.close();signal=null;signalClients=0;}}
    synchronized void invalidateSignal(){if(signal!=null)signal.pause();}
    private ReceiverInfoRepository(Context c){context=c.getApplicationContext();}
    private synchronized SatelliteGuideRepository guide(){if(guide==null)guide=new SatelliteGuideRepository(context);return guide;}
    static ReceiverInfoRepository get(Context c){ReceiverInfoRepository r=instance;if(r==null)synchronized(ReceiverInfoRepository.class){r=instance;if(r==null)instance=r=new ReceiverInfoRepository(c);}return r;}
    static String channelKey(SatelliteState s){return s.channelId+"|"+ChannelCatalogue.normalized(s.channelName);}
    SatelliteState current(){return enrich(SatelliteState.read(context));}
    SatelliteState enrich(SatelliteState observed){
        long began=android.os.SystemClock.elapsedRealtimeNanos();
        SatelliteState s=observed.copy();long now=System.currentTimeMillis();
        boolean oldExternal=s.source!=null&&(s.source.startsWith("Online EPG")||
          s.source.startsWith("XMLTV")||s.source.startsWith("Manual EPG")||
          (s.source.startsWith("EPGShare")&&
           !context.getSharedPreferences("smarthub",0)
             .getBoolean(OsnEpgFallback.ENABLED,false)));
        if(oldExternal){
            s.title="";s.startMs=0;s.endMs=0;s.image="";
            s.nextTitle="";s.nextStartMs=0;s.nextEndMs=0;s.nextImage="";
            s.source="";
        }
        // 1) Verified manufacturer event bound to the current DVB service.
        boolean factoryNow=context.getSharedPreferences("smarthub",0)
          .getBoolean("factory_enabled",true)&&FactoryProvider.applyPrepared(context,s,false);
        boolean factoryNext=context.getSharedPreferences("smarthub",0)
          .getBoolean("factory_enabled",true)&&FactoryProvider.applyPrepared(context,s,true);
        // 2) Native DVB EIT from the receiver; never promote old XMLTV cache.
        if(s.channelId.startsWith("DVB:"))refreshQuick(s.channelId,now);
        SatelliteGuideRepository.Event eNow=s.channelId.equals(quickChannel)?quickNow:null;
        SatelliteGuideRepository.Event eNext=s.channelId.equals(quickChannel)?quickNext:null;
        if(!factoryNow&&eNow!=null&&timedCurrent(eNow.title,eNow.startMs,eNow.endMs,now)){
            s.title=eNow.title;s.startMs=eNow.startMs;s.endMs=eNow.endMs;
            s.image=eNow.image;s.source=eNow.source;
        }
        if(!factoryNext&&eNext!=null&&timedNext(eNext.title,eNext.startMs,eNext.endMs,now)){
            s.nextTitle=eNext.title;s.nextStartMs=eNext.startMs;
            s.nextEndMs=eNext.endMs;s.nextImage=eNext.image;
        }
        boolean needNow=!timedCurrent(s.title,s.startMs,s.endMs,now);
        boolean needNext=!timedNext(s.nextTitle,s.nextStartMs,s.nextEndMs,now);
        // 3) Only an explicitly downloaded, exact-service manual guide.
        // No automatic external XMLTV/EPGShare requests or provider writes.
        if((needNow||needNext)&&s.channelId.startsWith("DVB:")){
            List<NativeEpgEvents.Event> local=manualCached(s,now);
            if(needNow){
                NativeEpgEvents.Event m=ManualEpgStore.now(local,now);
                if(m!=null){
                    s.title=m.title;s.startMs=m.start;s.endMs=m.end;s.image=m.image;
                    s.source="Manual EPG · "+ManualEpgStore.source(context,s.channelId);
                }
            }
            if(needNext){
                NativeEpgEvents.Event m=ManualEpgStore.next(local,now);
                if(m!=null){
                    s.nextTitle=m.title;s.nextStartMs=m.start;
                    s.nextEndMs=m.end;s.nextImage=m.image;
                }
            }
        }
        // Diagnostic budget is for the warmed in-memory binding path, NOT
        // for the cold OEM/network/tuner EPG fetch (which cannot be 50ms).
        long elapsed=(android.os.SystemClock.elapsedRealtimeNanos()-began)/1000000L;
        android.content.SharedPreferences prefs=context.getSharedPreferences("smarthub",0);
        long lastSample=prefs.getLong("epg_cached_metric_at",0);
        if(now-lastSample>=5000){
            prefs.edit().putLong("epg_cached_metric_at",now)
              .putLong("epg_cached_enrich_ms",elapsed)
              .putString("epg_cached_enrich_status",elapsed<=50?
                 "CACHED_WITHIN_50MS":"CACHED_SLOW_"+elapsed+"MS").apply();
        }
        return s;
    }
    Info info(SatelliteState s){
        SharedPreferences p=context.getSharedPreferences("smarthub",0);String key=channelKey(s);boolean ar=p.getBoolean("arabic",true);
        String encryption="";long age=System.currentTimeMillis()-p.getLong("broadcast_encryption_seen:"+key,0);
        if(age>=0&&age<120000){String v=p.getString("broadcast_encryption:"+key,"");if(v.equals("FTA"))encryption=ar?"مفتوحة · FTA":"Free-to-air · FTA";else if(v.equals("ENCRYPTED"))encryption=ar?"مشفرة":"Encrypted";}
        String sat=ReceptionData.value(context,s,"satellite"),rate=ReceptionData.value(context,s,"bitrate");
        ArrayList<String> broadcast=new ArrayList<>();if(!sat.isEmpty())broadcast.add(sat);String frequency=published(p,key,"channel_info_frequency_tv");if(!frequency.isEmpty())broadcast.add("INFO · "+frequency);String rf=DirectReceiverSignal.format(s);if(!rf.isEmpty())broadcast.add(rf);/* bitrate is rendered separately in the infobar rather than appended to RF. */
        ReceiverSignalReading frame=DirectReceiverSignal.reading(s);if(frame!=null&&frame.locked==1&&frame.tp==p.getInt("own_dvb_runtime_tp",-1)&&p.getInt("own_dvb_mapping_version",0)==3&&s.channelId.equals("DVB:"+p.getString("own_dvb_physical",""))&&p.getString("own_dvb_physical","").startsWith(p.getString("own_dvb_physical_tp","invalid")+":")){String metadata=p.getString("own_dvb_rf","");if(!metadata.isEmpty())broadcast.add(metadata);String audio=p.getString("own_dvb_audio","");if(!audio.isEmpty())broadcast.add(audio);if(p.getBoolean("own_dvb_fta",false))encryption="FTA · CHL";else encryption=ar?"مشفرة · Firmware":"Encrypted · Firmware";}
        if(s.channelId.equals(p.getString("decoder_channel",""))){int w=p.getInt("decoder_width",0),h=p.getInt("decoder_height",0);if(w>0&&h>0)broadcast.add(w+"×"+h);int fps=p.getInt("decoder_fps",0),bitrate=p.getInt("decoder_bitrate",0);if(fps>0)broadcast.add(fps+" FPS");/* codec bitrate rendered in Info.bitrate only */}
        long resolutionAge=System.currentTimeMillis()-p.getLong("broadcast_resolution_seen:"+key,0);if(resolutionAge>=0&&resolutionAge<120000){java.util.regex.Matcher fps=java.util.regex.Pattern.compile("([0-9]{1,3}(?:\\.[0-9]+)?) fps").matcher(p.getString("broadcast_resolution:"+key,""));if(fps.find())broadcast.add(fps.group());}
        String accurateCa=ReceiverTechnicalBadges.encryption(context,s);
        if(!accurateCa.isEmpty())encryption=accurateCa;
        String bitrate="";
        if(!rate.isEmpty())bitrate=rate;
        if(s.channelId.equals(p.getString("decoder_channel",""))){
          int decoderRate=p.getInt("decoder_bitrate",0);
          if(decoderRate>0&&decoderRate<=1000000000)
            bitrate=String.format(java.util.Locale.ROOT,"%.2f Mbps",decoderRate/1000000d);
        }
        long tsAge=System.currentTimeMillis()-p.getLong("software_ts_bitrate_at",0);
        if(s.channelId.equals(p.getString("software_ts_channel",""))&&tsAge>=0&&tsAge<12000){
          long bps=p.getLong("software_ts_bitrate",0);
          if(bps>0)bitrate=String.format(java.util.Locale.ROOT,"TS %.2f Mbps",bps/1000000d);
        }
        return new Info(LogoRepository.get(context).providerName(s.channelName),ResolutionBadge.label(context,s),String.join(" · ",broadcast),encryption,bitrate,
                ReceptionData.value(context,s,"strength"),ReceptionData.value(context,s,"quality"),ReceptionData.value(context,s,"snr"),ReceptionData.value(context,s,"ber"));
    }
    private static String published(SharedPreferences p,String channel,String field){long age=System.currentTimeMillis()-p.getLong("native_"+field+"_seen:"+channel,0);if(age<0||age>=120000)return "";String v=p.getString("native_"+field+":"+channel,"").trim();return v.length()<=100&&!v.chars().anyMatch(n->n<32)?v:"";}
    static final class Info {
        final String provider,badge,broadcast,encryption,bitrate,strength,quality,snr,ber;
        Info(String provider,String badge,String broadcast,String encryption,String strength,String quality,String snr,String ber){
            this(provider,badge,broadcast,encryption,"",strength,quality,snr,ber);
        }
        Info(String provider,String badge,String broadcast,String encryption,String bitrate,String strength,String quality,String snr,String ber){this.provider=provider;this.badge=badge;this.broadcast=broadcast;this.encryption=encryption;this.bitrate=bitrate;this.strength=strength;this.quality=quality;this.snr=snr;this.ber=ber;}
        String signalDetail(){return "SNR "+(snr.isEmpty()?"—":snr)+"  ·  BER "+(ber.isEmpty()?"—":ber);}
    }
}
