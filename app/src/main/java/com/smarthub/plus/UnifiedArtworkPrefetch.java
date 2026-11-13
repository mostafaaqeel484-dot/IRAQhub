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
  for(int index=0;index<2;index++){
   boolean upcoming=index==1;
   String title=upcoming?event.nextTitle:event.title;
   if(title==null||title.trim().isEmpty())continue;
   long start=upcoming?event.nextStartMs:event.startMs;
   long end=upcoming?event.nextEndMs:event.endMs;
   if(start<=0||end<=start||end<System.currentTimeMillis())continue;
   try{
    String existing=upcoming?event.nextImage:event.image;
    String key=PosterSelection.key(event,title,upcoming);
    if(key.isEmpty())continue;
    long now=System.currentTimeMillis();
    long sourceRevision=prefs.getLong("poster_sources_changed_at",0);
    // BetterPosters is the FIRST automatic movie/series source, including
    // channels carrying a generic DVB/XMLTV guide image. An explicitly
    // selected Commons artwork is still the owner's override.
    if(PosterArtworkPriority.attemptBetterFirst(
      PosterSourceSwitches.enabled(context,PosterSourceSwitches.BETTER),
      CommonsArtworkSelection.event(context,event,title,upcoming)!=null)){
     String priorSource=prefs.getString("verified_poster_source:"+key,"");
     boolean freshBetter=PosterArtworkPriority.betterSource(priorSource)&&
       prefs.getLong("verified_poster_until:"+key,0)>now&&
       prefs.getLong("verified_poster_at:"+key,0)>=sourceRevision&&
       BetterPostersProvider.manifestRevision().equals(prefs.getString("verified_poster_addon:"+key,""))&&
       validUrl(prefs.getString("verified_poster_url:"+key,""));
     if(freshBetter)continue;
     try{
      double minutes=(end-start)/60000.0;
      JSONObject better=provider.resolve(title,"","","",minutes);
      String image=better.optString("poster","");
      String type=better.optString("type","");
      String id=better.optString("id","");
      if("matched".equals(better.optString("status"))&&
         ("movie".equals(type)||"series".equals(type))&&
         (id.matches("tt[0-9]{7,10}")||id.matches("tmdb:[1-9][0-9]{0,10}"))&&
         validUrl(image)&&imageAvailable(context,image)){
       prefs.edit()
         .putString("verified_poster_url:"+key,image)
         .putString("verified_poster_id:"+key,id)
         .putString("verified_poster_type:"+key,type)
         .putString("verified_poster_source:"+key,"BetterPosters")
         .putString("verified_poster_addon:"+key,BetterPostersProvider.manifestRevision())
         .putLong("verified_poster_until:"+key,Math.min(end+60000,now+MAX_AGE))
         .putLong("verified_poster_at:"+key,now)
         .putLong("epg_art_changed_at",now).apply();
       done++;continue;
      }
     }catch(Exception unavailable){
      // The addon can be unavailable, misconfigured or return no exact title.
      // All verified TMDB/TVmaze/EPG poster fallbacks remain usable.
     }
    }
    // Native guide image wins only if the BetterPosters first pass had no
    // exact verified result. Preserve fast browsing and established EPG.
    if(existing!=null&&!existing.isEmpty())continue;
    JSONObject chosen=PosterSelection.read(context,event,title,upcoming);
    String id=chosen.optString("id"),type=chosen.optString("type");
    if(!PosterArtworkPriority.pinnedTmdbId(id)){id="";type="";}
    JSONObject auto=null;
    if(id.isEmpty()&&title.trim().length()>=5&&PosterSourceSwitches.enabled(context,PosterSourceSwitches.TMDB)){
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
        PosterSelection.save(context,event,title,upcoming,candidate);
       }
      }
     }catch(Exception unavailable){/* Do not assign uncertain titles. */}
    }
    if(!id.matches("tmdb:[1-9][0-9]{0,10}")||
       (!"movie".equals(type)&&!"series".equals(type))){
     // Key-free TVmaze is safe for explicitly numbered episodes only.
     BroadcastTitle episode=BroadcastTitle.parse(title);
     if((episode.season>0||episode.episode>0)&&prefs.getBoolean("art_source_tvmaze_enabled",true))try{
      JSONObject show=new TvmazeProvider(context).resolve(title,"");
      String image=show.optString("poster","");
      String key=PosterSelection.key(event,title,upcoming);
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
    long before=prefs.getLong("verified_poster_until:"+key,0);
    sourceRevision=Math.max(sourceRevision,prefs.getLong("providers_changed_at",0));
    boolean upgradeToBetter=PosterSourceSwitches.enabled(context,PosterSourceSwitches.BETTER)&&
      !PosterArtworkPriority.betterSource(prefs.getString("verified_poster_source:"+key,""));
    if(!upgradeToBetter&&before>System.currentTimeMillis()&&
       prefs.getLong("verified_poster_at:"+key,0)>=sourceRevision&&
       !prefs.getString("verified_poster_url:"+key,"").isEmpty())continue;
    String address="",posterSource="",imdb="";
    JSONObject matchedTmdb=null;
    // Metadata identification is separate from image priority. TMDB may
    // supply a verified IMDb identity when BetterPosters has no search catalog.
    try{
     ProviderSecrets secrets=ProviderSecrets.read(context);
     if(secrets!=null&&!secrets.tmdb.isEmpty()){
      JSONObject tmdb=auto!=null?auto:
        new TmdbProvider(context,secrets.tmdb).resolve(title,id,type,"",0);
      if("matched".equals(tmdb.optString("status"))&&id.equals(tmdb.optString("id"))
         &&type.equals(tmdb.optString("type"))){
       matchedTmdb=tmdb;
       imdb=tmdb.optString("imdbId","");
      }
     }
    }catch(Exception unavailable){/* Fallback sources remain independent. */}
    // Second BetterPosters chance uses verified IMDb identity (or typed TMDB
    // identity), before ever choosing TMDB's poster. This supports addons
    // advertising only metadata, without the optional bp-search catalog.
    if(PosterSourceSwitches.enabled(context,PosterSourceSwitches.BETTER))try{
     String preferredId=PosterArtworkPriority.exactImdb(imdb)?imdb:id;
     JSONObject better=provider.resolve(title,preferredId,type,imdb,0);
     String image=better.optString("poster","");
     if("matched".equals(better.optString("status"))&&
        type.equals(better.optString("type"))&&
        preferredId.equals(better.optString("id"))&&
        validUrl(image)&&imageAvailable(context,image)){
      address=image;posterSource="BetterPosters";
     }
    }catch(Exception unavailable){/* TMDB and TVmaze stay usable. */}
    if(!validUrl(address)&&PosterArtworkPriority.exactImdb(imdb)&&
       PosterSourceSwitches.enabled(context,PosterSourceSwitches.BETTER)&&
       prefs.getBoolean("betterposters_direct_enabled",true)){
     try{
      String direct=BetterPostersDirect.poster(context,imdb);
      if(validUrl(direct)&&imageAvailable(context,direct)){
       address=direct;posterSource="BetterPosters · IMDb";
      }
     }catch(Exception unavailable){/* Continue to TMDB fallback. */}
    }
    if(!validUrl(address)&&matchedTmdb!=null){
     String tmdbPoster=matchedTmdb.optString("poster","");
     if(validUrl(tmdbPoster)&&imageAvailable(context,tmdbPoster)){
      address=tmdbPoster;posterSource="TMDB";
     }
    }
    if(!validUrl(address)&&"series".equals(type)&&PosterArtworkPriority.exactImdb(imdb))
     try{
      JSONObject maze=new TvmazeProvider(context).resolve(title,imdb);
      String image=maze.optString("poster","");
      if("matched".equals(maze.optString("status"))&&imdb.equals(maze.optString("imdbId"))
        &&validUrl(image)&&imageAvailable(context,image)){
       address=image;posterSource="TVmaze";
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
     .putString("verified_poster_addon:"+key,PosterArtworkPriority.betterSource(posterSource)?
        BetterPostersProvider.manifestRevision():"")
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
 /** A verified BetterPosters result wins over automatic broadcast/TMDB images.
  * Manual per-event artwork selected by the owner is checked separately. */
 static String preferredBetterUrl(Context context,SatelliteState event,String title,
                                 boolean upcoming){
  if(!PosterSourceSwitches.enabled(context,PosterSourceSwitches.BETTER))return "";
  String key=PosterSelection.key(event,title,upcoming);
  if(key.isEmpty())return "";
  SharedPreferences p=context.getSharedPreferences("smarthub",0);
  if(!PosterArtworkPriority.betterSource(p.getString("verified_poster_source:"+key,"")))return "";
  return cachedUrl(context,event,title,upcoming);
 }
 static String cachedUrl(Context context,SatelliteState event,String title){
  return cachedUrl(context,event,title,event!=null&&title!=null&&!title.equals(event.title)&&title.equals(event.nextTitle));
 }
 static String cachedUrl(Context context,SatelliteState event,String title,boolean upcoming){
  if(event==null||title==null||title.isEmpty())return "";
  String key=PosterSelection.key(event,title,upcoming);
  if(key.isEmpty())return "";
  SharedPreferences p=context.getSharedPreferences("smarthub",0);
  if(p.getLong("verified_poster_until:"+key,0)<=System.currentTimeMillis())return "";
  String label=p.getString("verified_poster_source:"+key,"");
  if(PosterArtworkPriority.betterSource(label)&&
     (!PosterSourceSwitches.enabled(context,PosterSourceSwitches.BETTER)||
      !BetterPostersProvider.manifestRevision().equals(p.getString("verified_poster_addon:"+key,""))))return "";
  if("TMDB".equals(label)&&!PosterSourceSwitches.enabled(context,PosterSourceSwitches.TMDB))return "";
  if("TVmaze".equals(label)&&!PosterSourceSwitches.enabled(context,PosterSourceSwitches.TVMAZE))return "";
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
