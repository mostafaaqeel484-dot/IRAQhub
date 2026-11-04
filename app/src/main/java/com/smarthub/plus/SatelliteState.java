package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;

/** Last observed satellite context. Empty fields mean unavailable, never sample data. */
public final class SatelliteState {
    public String channelId="",channelName="",title="",nextTitle="",source="",image="",nextImage="";
    public long startMs,endMs,nextStartMs,nextEndMs,observedAt;

    public SatelliteState copy(){SatelliteState s=new SatelliteState();s.image=image;s.nextImage=nextImage;s.channelId=channelId;s.channelName=channelName;s.title=title;s.startMs=startMs;s.endMs=endMs;s.nextTitle=nextTitle;s.nextStartMs=nextStartMs;s.nextEndMs=nextEndMs;s.observedAt=observedAt;s.source=source;return s;}
    public void expire(long now){
        if(!ProgrammeTitle.available(title)){title="";image="";startMs=0;endMs=0;}if(!ProgrammeTitle.available(nextTitle)){nextTitle="";nextImage="";nextStartMs=0;nextEndMs=0;}
        if((title.isEmpty()||endMs>0&&endMs<=now)&&!nextTitle.isEmpty()&&nextStartMs>0&&nextStartMs<=now&&nextEndMs>now){title=nextTitle;image=nextImage;nextImage="";startMs=nextStartMs;endMs=nextEndMs;nextTitle="";nextStartMs=0;nextEndMs=0;}
        else if(endMs>0&&endMs<=now){image="";title="";startMs=0;endMs=0;}
        if(nextEndMs>0&&nextEndMs<=now){nextImage="";nextTitle="";nextStartMs=0;nextEndMs=0;}
    }
    public static synchronized SatelliteState read(Context context){
        SharedPreferences p=context.getSharedPreferences("smarthub",Context.MODE_PRIVATE);SatelliteState s=new SatelliteState();
        s.image=p.getString("satellite_epg_image","");s.nextImage=p.getString("satellite_next_epg_image","");s.channelId=p.getString("satellite_channel_id","");s.channelName=p.getString("satellite_channel_name","");s.title=p.getString("satellite_title","");
        s.startMs=p.getLong("satellite_start_ms",p.getLong("satellite_event_start_ms",0));s.endMs=p.getLong("satellite_end_ms",p.getLong("satellite_event_end_ms",0));
        s.nextTitle=p.getString("satellite_next_title","");s.nextStartMs=p.getLong("satellite_next_start_ms",0);s.nextEndMs=p.getLong("satellite_next_end_ms",0);
        s.source=p.getString("satellite_source","");s.observedAt=p.getLong("satellite_observed_at",0);
        String title=s.title,next=s.nextTitle;s.expire(System.currentTimeMillis());
        // Consumers that read the common preference aliases must not receive expired EPG.
        if(!title.equals(s.title)||!next.equals(s.nextTitle)){
            SharedPreferences.Editor e=p.edit();writeProgramme(e,s);if(!title.equals(s.title))clearBindings(e);e.apply();
        }
        return s;
    }
    public void save(Context context){save(context,this);}
    public static synchronized void save(Context context,SatelliteState state){
        if(state==null)return;SatelliteState s=state.copy();s.expire(System.currentTimeMillis());
        SharedPreferences p=context.getSharedPreferences("smarthub",Context.MODE_PRIVATE);SharedPreferences.Editor e=p.edit();
        if(!p.getString("satellite_channel_id","").equals(s.channelId)||!p.getString("satellite_channel_name","").equals(s.channelName)||!p.getString("satellite_title","").equals(s.title)||p.getLong("satellite_start_ms",0)!=s.startMs)clearBindings(e);
        e.putString("satellite_channel_id",safe(s.channelId)).putString("satellite_channel_name",safe(s.channelName)).putString("satellite_source",safe(s.source)).putLong("satellite_observed_at",s.observedAt);
        writeProgramme(e,s);e.apply();
    }
    private static void writeProgramme(SharedPreferences.Editor e,SatelliteState s){
        e.putString("satellite_epg_image",safe(s.image)).putString("satellite_next_epg_image",safe(s.nextImage));e.putString("satellite_title",safe(s.title)).putLong("satellite_start_ms",s.startMs).putLong("satellite_event_start_ms",s.startMs).putLong("satellite_end_ms",s.endMs).putLong("satellite_event_end_ms",s.endMs)
            .putString("satellite_next_title",safe(s.nextTitle)).putLong("satellite_next_start_ms",s.nextStartMs).putLong("satellite_next_end_ms",s.nextEndMs);
    }
    private static String safe(String text){return text==null?"":text;}
    private static void clearBindings(SharedPreferences.Editor e){e.remove("community_current_work").remove("satellite_imdb_id").remove("satellite_imdb_title").remove("satellite_wikidata_id").remove("satellite_wikidata_title");}
}
