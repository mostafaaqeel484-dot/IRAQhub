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
    JSONObject auto=null;
    if(id.isEmpty()&&title.trim().length()>=5){
     // Strict TMDB provider rejects ambiguous or weak titles; do not guess.
     try{
      ProviderSecrets secrets=ProviderSecrets.read(context);
      if(!secrets.tmdb.isEmpty()){
       JSONObject candidate=new TmdbProvider(context,secrets.tmdb)
         .resolve(title,"","","",0);
       if("matched".equals(candidate.optString("status"))&&
         candidate.optString("id").matches("tmdb:[1-9][0-9]{0,10}")&&
         ("movie".equals(candidate.optString("type"))||
          "series".equals(candidate.optString("type")))){
        auto=candidate;
        id=candidate.optString("id");
        type=candidate.optString("type");
        PosterSelection.save(context,event,title,candidate);
       }
      }
     }catch(Exception unavailable){/* Do not assign uncertain titles. */}
    }
    if(!id.matches("tmdb:[1-9][0-9]{0,10}")||
       (!"movie".equals(type)&&!"series".equals(type))){
     // Key-free TVmaze is safe for explicitly numbered episodes only.
     BroadcastTitle episode=BroadcastTitle.parse(title);
     if(episode.season>0||episode.episode>0)try{
      JSONObject show=new TvmazeProvider(context).resolve(title,"");
      String image=show.optString("poster","");
      String key=PosterSelection.key(event,title);
      if("matched".equals(show.optString("status"))&&
         "series".equals(show.optString("type"))&&
         !key.isEmpty()&&imageAvailable(context,image)){
       long now=System.currentTimeMillis();
       prefs.edit().putString("verified_poster_url:"+key,image)
         .putString("verified_poster_source:"+key,"TVmaze · exact episodic title")
         .putString("verified_poster_id:"+key,"tvmaze:"+show.optString("id"))
         .putString("verified_poster_type:"+key,"series")
         .putLong("verified_poster_until:"+key,Math.min(end+60000,now+MAX_AGE))
         .putLong("verified_poster_at:"+key,now)
         .putLong("epg_art_changed_at",now).apply();
       done++;continue;
      }
     }catch(Exception unavailable){/* No guessed episode artwork. */}
     continue;
    }
    String key=PosterSelection.key(event,title);
    if(key.isEmpty())continue;
    long before=prefs.getLong("verified_poster_until:"+key,0);
    if(before>System.currentTimeMillis()&&!prefs.getString("verified_poster_url:"+key,"").isEmpty())continue;
    String address="",posterSource="",imdb="";
    // Verify chosen TMDB identity first. Different providers fail independently.
    try{
     ProviderSecrets secrets=ProviderSecrets.read(context);
     if(secrets!=null&&!secrets.tmdb.isEmpty()){
      JSONObject tmdb=auto!=null?auto:
        new TmdbProvider(context,secrets.tmdb).resolve(title,id,type,"",0);
      if("matched".equals(tmdb.optString("status"))&&id.equals(tmdb.optString("id"))
         &&type.equals(tmdb.optString("type"))){
       address=tmdb.optString("poster","");
       imdb=tmdb.optString("imdbId","");
       posterSource="TMDB";
      }
     }
    }catch(Exception unavailable){/* Preserve other providers. */}
    if(validUrl(address)&&!imageAvailable(context,address))address="";
    if(!validUrl(address))try{
     JSONObject better=provider.resolve(title,id,type,imdb,0);
     if("matched".equals(better.optString("status"))&&id.equals(better.optString("id"))
       &&type.equals(better.optString("type"))&&validUrl(better.optString("poster",""))){
      address=better.optString("poster","");
      posterSource="BetterPosters";
      if(imdb.isEmpty())imdb=better.optString("imdbId","");
     }
    }catch(Exception unavailable){/* TMDB remains usable. */}
    if(validUrl(address)&&!imageAvailable(context,address))address="";
    if(!validUrl(address)&&imdb.matches("tt[0-9]{7,10}")&&
       prefs.getBoolean("betterposters_direct_enabled",true)){
     try{
      String direct=BetterPostersDirect.poster(context,imdb);
      if(validUrl(direct)){address=direct;posterSource="BetterPosters · IMDb";}
     }catch(Exception unavailable){/* Fall back. */}
    }
    if(validUrl(address)&&!imageAvailable(context,address))address="";
    if(!validUrl(address)&&"series".equals(type)&&imdb.matches("tt[0-9]{7,10}"))
     try{
      JSONObject maze=new TvmazeProvider(context).resolve(title,imdb);
      if("matched".equals(maze.optString("status"))&&imdb.equals(maze.optString("imdbId"))
        &&validUrl(maze.optString("poster",""))){
       address=maze.optString("poster","");
       posterSource="TVmaze";
      }
     }catch(Exception unavailable){/* Keep verified images only. */}
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
     .putString("verified_poster_source:"+key,posterSource)
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
 private static boolean imageAvailable(Context context,String address){
  if(!validUrl(address))return false;
  try{
   byte[] bytes=ArtworkCache.fetch(context,new URL(address),5*1024*1024);
   if(bytes==null||bytes.length<200)return false;
   BitmapFactory.Options probe=new BitmapFactory.Options();
   probe.inJustDecodeBounds=true;
   BitmapFactory.decodeByteArray(bytes,0,bytes.length,probe);
   return probe.outWidth>=90&&probe.outHeight>=90&&
      probe.outWidth<=10000&&probe.outHeight<=10000;
  }catch(Exception unavailable){return false;}
 }
 private static boolean validUrl(String source){
  try{
   URI uri=new URI(source);
   if(!"https".equals(uri.getScheme())||uri.getRawUserInfo()!=null
      ||uri.getRawFragment()!=null||source.length()>2048)return false;
   String host=uri.getHost();
   return "btttr.cc".equals(host)||"image.tmdb.org".equals(host)||"static.tvmaze.com".equals(host)||"upload.wikimedia.org".equals(host);
  }catch(Exception invalid){return false;}
 }
}
