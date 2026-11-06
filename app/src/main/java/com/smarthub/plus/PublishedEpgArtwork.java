package com.smarthub.plus;

import android.content.*;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import java.util.*;

/** Read-only Android TV EPG. Never opens receiver menus or probes private databases. */
final class PublishedEpgArtwork {
 private final Context context;private long retry;private String channel="";
 PublishedEpgArtwork(Context c){context=c.getApplicationContext();}
 void refresh(SatelliteState state,long now){
  String identity=CHMAXIntegration.identity(state);if(identity.equals(channel)&&now<retry)return;channel=identity;retry=now+60000;
  if(state.channelName.isEmpty())return;
  SharedPreferences prefs=context.getSharedPreferences("smarthub",0);
  try{
   long id=-1;boolean duplicate=false;int count=0;
   try(Cursor rows=context.getContentResolver().query(TvContract.Channels.CONTENT_URI,new String[]{"_id","display_name"},null,null,null)){
    if(rows!=null)while(rows.moveToNext()&&count++<5000){if(matchesChannel(state.channelName,rows.getString(1))){if(id>=0)duplicate=true;id=rows.getLong(0);}}
   }
   if(id<0||duplicate){status(id<0?"No matching channel published through Android TV EPG":"Ambiguous Android TV channel; artwork ignored");return;}
   int found=0;
   try(Cursor rows=context.getContentResolver().query(TvContract.buildProgramsUriForChannel(id,now,now+24*3600000L),new String[]{"title","start_time_utc_millis","end_time_utc_millis","poster_art_uri","thumbnail_uri"},null,null,"start_time_utc_millis ASC")){
    int visited=0;if(rows!=null)while(rows.moveToNext()&&visited++<200){String title=rows.getString(0);long start=rows.getLong(1),end=rows.getLong(2);String url=rows.getString(3);if(url==null||url.isEmpty())url=rows.getString(4);url=PlaylistRepository.epgImage(url);boolean current=matchesEvent(state.title,state.startMs,state.endMs,title,start,end),next=matchesEvent(state.nextTitle,state.nextStartMs,state.nextEndMs,title,start,end);if(url.isEmpty()||!current&&!next||end<=now)continue;String key=PosterSelection.key(state,current?state.title:state.nextTitle,!current);if(key.isEmpty())continue;prefs.edit().putString("published_epg_image:"+key,url).putLong("published_epg_until:"+key,Math.min(end+60000,now+24*3600000L)).apply();found++;}
   }
   if(found>0)prefs.edit().putLong("epg_art_changed_at",now).apply();status(found>0?"Android TV EPG · "+found+" programme image(s) · automatic, no menu":"Android TV EPG has no image matching this programme");
  }catch(SecurityException denied){status("Android TV EPG access denied; XMLTV and exposed receiver images remain available");}catch(Exception failure){status("Android TV EPG unavailable · "+failure.getClass().getSimpleName());}
 }
 private void status(String value){context.getSharedPreferences("smarthub",0).edit().putString("published_epg_status",value).apply();}
 static boolean matchesChannel(String a,String b){return a!=null&&b!=null&&!a.trim().isEmpty()&&ChannelCatalogue.normalized(a).equals(ChannelCatalogue.normalized(b));}
 static boolean matchesEvent(String a,long aStart,long aEnd,String b,long bStart,long bEnd){return a!=null&&b!=null&&!a.trim().isEmpty()&&PlaylistRepository.normalize(a).equals(PlaylistRepository.normalize(b))&&aStart>0&&aEnd>aStart&&Math.abs(aStart-bStart)<=60000&&Math.abs(aEnd-bEnd)<=60000;}
 static String image(Context c,SatelliteState state,String title){
  return image(c,state,title,state!=null&&title!=null&&!title.equals(state.title)&&title.equals(state.nextTitle));
 }
 static String image(Context c,SatelliteState state,String title,boolean upcoming){
  String key=PosterSelection.key(state,title,upcoming);
  if(key.isEmpty())return "";
  SharedPreferences p=c.getSharedPreferences("smarthub",0);
  return p.getLong("published_epg_until:"+key,0)>System.currentTimeMillis()
    ?p.getString("published_epg_image:"+key,""):"";
 }
}
