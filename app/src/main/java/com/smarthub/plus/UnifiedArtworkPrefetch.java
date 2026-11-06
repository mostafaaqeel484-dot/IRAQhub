package com.smarthub.plus;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import java.net.URI;
import java.net.URL;
import org.json.JSONObject;

/**
 * Safe current/next EPG poster prefetch. An explicitly verified content ID is
 * required; a programme title alone never triggers a BetterPosters association.
 * Stores only event-scoped artwork references, not copies of raw XMLTV data.
 */
final class UnifiedArtworkPrefetch {
 private static final long MAX_AGE=3L*24*3600000;
 private UnifiedArtworkPrefetch(){}

 static int currentAndNext(Context context,BetterPostersProvider provider){
  SharedPreferences prefs=context.getSharedPreferences("smarthub",0);
  if(prefs.getBoolean("software_dvb_422_active",false)||prefs.getBoolean("dvb_tuning",false))return 0;
  SatelliteState event=ReceiverInfoRepository.get(context).enrich(SatelliteState.read(context));
  if(event.channelId.isEmpty())return 0;
  int done=0;
  for(String title:new String[]{event.title,event.nextTitle}){
   if(title==null||title.trim().isEmpty())continue;
   long start=title.equals(event.title)?event.startMs:event.nextStartMs;
   long end=title.equals(event.title)?event.endMs:event.nextEndMs;
   if(start<=0||end<=start||end<System.currentTimeMillis())continue;
   try{
    String existing=title.equals(event.title)?event.image:event.nextImage;
    if(existing!=null&&!existing.isEmpty())continue;
    JSONObject chosen=PosterSelection.read(context,event,title);
    String id=chosen.optString("id"),type=chosen.optString("type");
    if(!id.matches("tmdb:[1-9][0-9]{0,10}")||(!"movie".equals(type)&&!"series".equals(type)))continue;
    String key=PosterSelection.key(event,title);
    if(key.isEmpty())continue;
    long before=prefs.getLong("verified_poster_until:"+key,0);
    if(before>System.currentTimeMillis()&&!prefs.getString("verified_poster_url:"+key,"").isEmpty())continue;
    JSONObject match=provider.resolve(title,id,type,"",0);
    if(!"matched".equals(match.optString("status"))||!id.equals(match.optString("id"))
       ||!type.equals(match.optString("type")))continue;
    String address=match.optString("poster","");
    if(!validUrl(address))continue;
    byte[] bytes=ArtworkCache.fetch(context,new URL(address),5*1024*1024);
    if(bytes==null||bytes.length<200)continue;
    BitmapFactory.Options image=new BitmapFactory.Options();
    image.inJustDecodeBounds=true;
    BitmapFactory.decodeByteArray(bytes,0,bytes.length,image);
    if(image.outWidth<90||image.outHeight<90||image.outWidth>10000||image.outHeight>10000)continue;
    SharedPreferences.Editor edit=prefs.edit()
     .putString("verified_poster_url:"+key,address).putString("verified_poster_id:"+key,id)
     .putString("verified_poster_type:"+key,type)
     .putLong("verified_poster_until:"+key,Math.min(end+60000,System.currentTimeMillis()+MAX_AGE))
     .putLong("verified_poster_at:"+key,System.currentTimeMillis())
     .putLong("epg_art_changed_at",System.currentTimeMillis());
    edit.apply();done++;
   }catch(Exception unavailable){/* Leave prior valid poster and EPG untouched. */}
  }
  prefs.edit().putLong("verified_poster_prefetch_at",System.currentTimeMillis())
    .putInt("verified_poster_prefetch_count",done).apply();
  return done;
 }
 static String cachedUrl(Context context,SatelliteState event,String title){
  if(event==null||title==null||title.isEmpty())return "";
  long start=title.equals(event.title)?event.startMs:event.nextStartMs;
  if(start<=0)return "";
  SharedPreferences p=context.getSharedPreferences("smarthub",0);
  String key=PosterSelection.key(event,title);
  if(p.getLong("verified_poster_until:"+key,0)<=System.currentTimeMillis())return "";
  String source=p.getString("verified_poster_url:"+key,"");
  return validUrl(source)?source:"";
 }
 private static boolean validUrl(String source){
  try{
   URI uri=new URI(source);
   if(!"https".equals(uri.getScheme())||uri.getRawUserInfo()!=null
      ||uri.getRawFragment()!=null||source.length()>2048)return false;
   String host=uri.getHost();
   return "btttr.cc".equals(host)||"image.tmdb.org".equals(host);
  }catch(Exception invalid){return false;}
 }
}
