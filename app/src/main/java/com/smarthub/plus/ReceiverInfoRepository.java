package com.smarthub.plus;

import android.content.*;
import java.util.*;

/** Shared read model for launcher, infobar, companion and the CHMAX integration boundary. */
final class ReceiverInfoRepository {
    private static volatile ReceiverInfoRepository instance;
    private final Context context;
    private SatelliteGuideRepository guide;private BroadcastEpgProvider epg;
    synchronized void refreshEpg(SatelliteState state){if(epg==null)epg=new BroadcastEpgProvider(context);epg.refresh(state);}
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
        SatelliteState s=observed.copy();long now=System.currentTimeMillis();
        if(s.title.isEmpty()){PlaylistRepository.Programme p=guide().currentFor(observed,now);if(p!=null&&p.startMs<=now&&p.endMs>now){s.title=p.title;s.image=p.image;s.startMs=p.startMs;s.endMs=p.endMs;s.source="XMLTV · mapped guide";}}
        if(s.nextTitle.isEmpty()){PlaylistRepository.Programme p=guide().nextFor(observed,now);if(p!=null){s.nextTitle=p.title;s.nextImage=p.image;s.nextStartMs=p.startMs;s.nextEndMs=p.endMs;}}
        return s;
    }
    Info info(SatelliteState s){
        SharedPreferences p=context.getSharedPreferences("smarthub",0);String key=channelKey(s);boolean ar=p.getBoolean("arabic",true);
        String encryption="";long age=System.currentTimeMillis()-p.getLong("broadcast_encryption_seen:"+key,0);
        if(age>=0&&age<120000){String v=p.getString("broadcast_encryption:"+key,"");if(v.equals("FTA"))encryption=ar?"مفتوحة · FTA":"Free-to-air · FTA";else if(v.equals("ENCRYPTED"))encryption=ar?"مشفرة":"Encrypted";}
        String sat=ReceptionData.value(context,s,"satellite"),rate=ReceptionData.value(context,s,"bitrate");
        ArrayList<String> broadcast=new ArrayList<>();if(!sat.isEmpty())broadcast.add(sat);String frequency=published(p,key,"channel_info_frequency_tv");if(!frequency.isEmpty())broadcast.add("INFO · "+frequency);String rf=DirectReceiverSignal.format(s);if(!rf.isEmpty())broadcast.add(rf);if(!rate.isEmpty())broadcast.add(rate);
        ReceiverSignalReading frame=DirectReceiverSignal.reading(s);if(frame!=null&&frame.locked==1&&frame.tp==p.getInt("own_dvb_runtime_tp",-1)&&p.getInt("own_dvb_mapping_version",0)==3&&s.channelId.equals("DVB:"+p.getString("own_dvb_physical",""))&&p.getString("own_dvb_physical","").startsWith(p.getString("own_dvb_physical_tp","invalid")+":")){String metadata=p.getString("own_dvb_rf","");if(!metadata.isEmpty())broadcast.add(metadata);String audio=p.getString("own_dvb_audio","");if(!audio.isEmpty())broadcast.add(audio);if(p.getBoolean("own_dvb_fta",false))encryption="FTA · CHL";else encryption=ar?"مشفرة · Firmware":"Encrypted · Firmware";}
        if(s.channelId.equals(p.getString("decoder_channel",""))){int w=p.getInt("decoder_width",0),h=p.getInt("decoder_height",0);if(w>0&&h>0)broadcast.add(w+"×"+h);int fps=p.getInt("decoder_fps",0),bitrate=p.getInt("decoder_bitrate",0);if(fps>0)broadcast.add(fps+" FPS");if(bitrate>0)broadcast.add(String.format(java.util.Locale.ROOT,"%.2f Mbps",bitrate/1000000.0));}
        long resolutionAge=System.currentTimeMillis()-p.getLong("broadcast_resolution_seen:"+key,0);if(resolutionAge>=0&&resolutionAge<120000){java.util.regex.Matcher fps=java.util.regex.Pattern.compile("([0-9]{1,3}(?:\\.[0-9]+)?) fps").matcher(p.getString("broadcast_resolution:"+key,""));if(fps.find())broadcast.add(fps.group());}
        return new Info(LogoRepository.get(context).providerName(s.channelName),ResolutionBadge.label(context,s),String.join(" · ",broadcast),encryption,
                ReceptionData.value(context,s,"strength"),ReceptionData.value(context,s,"quality"),ReceptionData.value(context,s,"snr"),ReceptionData.value(context,s,"ber"));
    }
    private static String published(SharedPreferences p,String channel,String field){long age=System.currentTimeMillis()-p.getLong("native_"+field+"_seen:"+channel,0);if(age<0||age>=120000)return "";String v=p.getString("native_"+field+":"+channel,"").trim();return v.length()<=100&&!v.chars().anyMatch(n->n<32)?v:"";}
    static final class Info {
        final String provider,badge,broadcast,encryption,strength,quality,snr,ber;
        Info(String provider,String badge,String broadcast,String encryption,String strength,String quality,String snr,String ber){this.provider=provider;this.badge=badge;this.broadcast=broadcast;this.encryption=encryption;this.strength=strength;this.quality=quality;this.snr=snr;this.ber=ber;}
        String signalDetail(){return "SNR "+(snr.isEmpty()?"—":snr)+"  ·  BER "+(ber.isEmpty()?"—":ber);}
    }
}
